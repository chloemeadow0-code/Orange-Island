#!/usr/bin/env python3
"""Collect pinned sandbox sources without executing downloaded APKBUILD files.

Alpine's installed database identifies each package's origin and aports commit.
All files in that recipe directory and every SHA-512-listed source are included.
An unavailable source, version mismatch, or checksum mismatch aborts packaging.
"""
import argparse
import concurrent.futures
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import socket
import subprocess
import tarfile
import tempfile
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
RECIPE_HOST = "https://raw.githubusercontent.com/alpinelinux/aports"
API = "https://api.github.com/repos/alpinelinux/aports/contents"


def download(url, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(destination.name + ".download")
    for attempt in range(3):
        offset = temporary.stat().st_size if temporary.exists() else 0
        headers = {"User-Agent": "Orange-Island-source-packager"}
        if offset:
            headers["Range"] = f"bytes={offset}-"
        request = urllib.request.Request(url, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=25) as response:
                resumed = offset and response.status == 206
                if resumed and not response.headers.get("Content-Range", "").startswith(f"bytes {offset}-"):
                    raise ValueError("Unexpected partial download range")
                with temporary.open("ab" if resumed else "wb") as output:
                    shutil.copyfileobj(response, output)
            temporary.replace(destination)
            return
        except urllib.error.HTTPError:
            raise
        except (urllib.error.URLError, TimeoutError, socket.timeout):
            if attempt == 2:
                raise


def sha(path, algorithm="sha512"):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()


def archive_text(archive, filename):
    for candidate in (filename, "./" + filename):
        try:
            stream = archive.extractfile(candidate)
            if stream is not None:
                return stream.read().decode("utf-8")
        except KeyError:
            pass
    raise ValueError(f"Missing {filename} in rootfs")


def read_packages(database):
    database = database.replace("\r\n", "\n")
    packages = []
    for block in database.strip().split("\n\n"):
        fields = {}
        # Only the package header is relevant; filesystem entries reuse letters.
        for line in block.splitlines():
            if line.startswith("F:"):
                break
            if len(line) > 2 and line[1] == ":":
                fields[line[0]] = line[2:]
        if "P" not in fields:
            continue
        for required in ("P", "V", "L", "o", "c"):
            if not fields.get(required):
                raise ValueError(f"Package {fields['P']} lacks {required}")
        if not re.fullmatch(r"[0-9a-f]{40}", fields["c"]):
            raise ValueError("Invalid aports commit")
        if not re.fullmatch(r"[a-zA-Z0-9+_.-]+", fields["o"]):
            raise ValueError("Invalid package origin")
        packages.append(dict(name=fields["P"], version=fields["V"],
                             license=fields["L"], origin=fields["o"], commit=fields["c"]))
    if not packages:
        raise ValueError("Empty installed package database")
    return sorted(packages, key=lambda item: item["name"])


def recipe_files(origin, commit, cache):
    """GitHub contents calls are cached at immutable commits; raw files stay pinned."""
    index = cache / "directory.json"
    if not index.exists():
        download(f"{API}/main/{origin}?ref={commit}", index)
    entries = json.loads(index.read_text(encoding="utf-8"))
    if not isinstance(entries, list):
        raise ValueError(f"Unexpected recipe listing: {origin}")
    def fetch_entry(entry):
        name = entry["name"]
        if PurePosixPath(name).name != name or name in (".", ".."):
            raise ValueError("Unsafe recipe filename")
        if entry["type"] != "file":
            raise ValueError(f"Recipe {origin}/{name}: directory or symlink requires explicit handling")
        path = cache / "recipe" / name
        if not path.exists():
            download(f"{RECIPE_HOST}/{commit}/main/{origin}/{urllib.parse.quote(name)}", path)
        # Verify raw content against the pinned Git tree blob identifier.
        data = path.read_bytes()
        git_hash = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
        if git_hash != entry["sha"]:
            raise ValueError(f"Git blob checksum mismatch: {origin}/{name}")
        return path
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        return list(pool.map(fetch_entry, entries))


def collect_origin(origin, commit, packages, branch, cache_root, stage):
    cache = cache_root / commit / origin
    files = recipe_files(origin, commit, cache)
    recipe = (cache / "recipe" / "APKBUILD").read_text(encoding="utf-8")
    def assignment(name):
        match = re.search(rf"^{name}=['\"]?([\w.+-]+)['\"]?\s*$", recipe, re.MULTILINE)
        if not match:
            raise ValueError(f"Cannot statically read {origin} {name}")
        return match.group(1)
    version = assignment("pkgver") + "-r" + assignment("pkgrel")
    if any(package["version"] != version for package in packages):
        raise ValueError(f"Version mismatch: {origin} recipe {version}, installed {packages}")
    checksum_block = re.search(r'^sha512sums="(.*?)"', recipe, re.MULTILINE | re.DOTALL)
    # Some metadata packages generate every file in APKBUILD itself.
    recipe_only = checksum_block is None and not re.search(r'^source\s*=', recipe, re.MULTILINE)
    if not checksum_block and not recipe_only:
        raise ValueError(f"No SHA-512 source manifest: {origin}")
    checksums = []
    for line in (checksum_block.group(1).strip().splitlines() if checksum_block else []):
        match = re.fullmatch(r"([0-9a-f]{128})\s+([^/\s]+)", line.strip())
        if not match:
            raise ValueError(f"Unrecognized checksum entry in {origin}: {line}")
        checksums.append(match.groups())
    if not checksums and not recipe_only:
        raise ValueError(f"Empty source manifest: {origin}")
    destination = stage / "alpine" / "aports" / "main" / origin
    destination.mkdir(parents=True)
    for path in files:
        shutil.copy2(path, destination / path.name)
    sources = []
    for expected, name in checksums:
        local = cache / "recipe" / name
        url = f"{RECIPE_HOST}/{commit}/main/{origin}/{urllib.parse.quote(name)}"
        if not local.exists():
            local = cache / "distfiles" / name
            if not local.exists():
                for prefix in (f"v{branch}/", ""):
                    url = f"https://distfiles.alpinelinux.org/distfiles/{prefix}{urllib.parse.quote(name)}"
                    try:
                        download(url, local)
                        break
                    except urllib.error.HTTPError as error:
                        if error.code != 404:
                            raise
                else:
                    raise ValueError(f"Source not found: {origin}/{name}")
            else:
                url = f"https://distfiles.alpinelinux.org/distfiles/v{branch}/{urllib.parse.quote(name)}"
        if sha(local) != expected:
            raise ValueError(f"Source checksum mismatch: {origin}/{name}")
        shutil.copy2(local, destination / name)
        sources.append(dict(file=name, sha512=expected, url=url))
    print(f"Verified {origin} {version}: {len(files)} recipe files, {len(sources)} source inputs", flush=True)
    return dict(origin=origin, commit=commit, version=version, sources=sources)


def git(*args, cwd=ROOT):
    return subprocess.check_output(["git", *args], cwd=cwd).decode("utf-8").strip()


def copy_proot(stage):
    source = ROOT / "thirdparty" / "proot"
    target = stage / "thirdparty" / "proot"
    entries = git("ls-files", "--stage", "-z", cwd=source).split("\0")
    for entry in entries:
        if not entry:
            continue
        metadata, name = entry.split("\t", 1)
        mode = metadata.split()[0]
        if mode not in ("100644", "100755"):
            raise ValueError(f"Unsupported PRoot entry: {name}")
        destination = target / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source / name, destination)
        destination.chmod(0o755 if mode == "100755" else 0o644)
    return dict(commit=git("rev-parse", "HEAD", cwd=source),
                working_tree=git("status", "--porcelain", cwd=source),
                upstream="https://github.com/termux/proot")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version", nargs="?")
    # Retain compatibility: Alpine sources are now mandatory, never optional.
    parser.add_argument("--with-alpine", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--verify", type=Path, help="Verify a completed source archive offline")
    parser.add_argument("--collect-only", action="store_true", help="Populate and verify the source cache without creating an archive")
    args = parser.parse_args()
    if args.verify:
        verify_archive(args.verify)
        return
    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    version = args.version or re.search(r'versionName = "([^"]+)"', gradle).group(1)
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", version):
        raise ValueError("Invalid application version")
    release = ROOT / "release"
    release.mkdir(exist_ok=True)
    output = release / f"gpl-sources-v{version}-corrected.tar.gz"
    if output.exists() and not args.collect_only:
        raise FileExistsError(f"Refusing to overwrite {output}")
    if not args.collect_only and not (ROOT / "app/src/fdroid/assets/licenses/third-party-notices.txt").is_file():
        raise ValueError("Missing bundled third-party copyright and license notices")
    rootfs = ROOT / "app/src/fdroid/assets/alpine-rootfs.bundle"
    rootfs_hash = sha(rootfs, "sha256")
    manager = (ROOT / "app/src/fdroid/java/com/orangeisland/app/sandbox/ProotSandboxManager.kt").read_text(encoding="utf-8")
    expected = re.search(r'rootfsSha256 = "([0-9a-f]+)"', manager).group(1)
    if rootfs_hash != expected:
        raise ValueError("Bundled rootfs does not match the application integrity pin")
    with tarfile.open(rootfs) as archive:
        database = archive_text(archive, "lib/apk/db/installed")
        alpine_version = archive_text(archive, "etc/alpine-release").strip()
    packages = read_packages(database)
    branch = ".".join(alpine_version.split(".")[:2])
    groups = {}
    for package in packages:
        groups.setdefault((package["origin"], package["commit"]), []).append(package)
    cache = release / ".sandbox-source-cache"
    with tempfile.TemporaryDirectory(prefix="sandbox-sources-", dir=release) as work:
        stage = Path(work) / f"gpl-sources-v{version}"
        stage.mkdir()
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            futures = [pool.submit(collect_origin, origin, commit, members, branch, cache, stage)
                       for (origin, commit), members in groups.items()]
            origins = [future.result() for future in futures]
        if args.collect_only:
            print(f"Source cache verified: {len(packages)} packages, {len(origins)} origins", flush=True)
            return
        # Validate the APK notice asset against these same pinned sources.
        from render_sandbox_notices import render
        bundled_notices = (ROOT / "app/src/fdroid/assets/licenses/third-party-notices.txt").read_text(encoding="utf-8")
        if bundled_notices != render():
            raise ValueError("Bundled copyright notices do not match the pinned sources")
        proot = copy_proot(stage)
        shutil.copytree(ROOT / "thirdparty/talloc", stage / "thirdparty/talloc",
                        ignore=shutil.ignore_patterns("*.o", "*.so", ".git"))
        shutil.copy2(ROOT / "build-proot.sh", stage / "build-proot.sh")
        (stage / "build-proot.sh").chmod(0o755)
        shutil.copytree(ROOT / "app/src/fdroid/assets/licenses", stage / "licenses")
        shutil.copy2(ROOT / "LICENSE", stage / "LICENSE")
        shutil.copy2(ROOT / "NOTICE", stage / "NOTICE")
        shutil.copy2(ROOT / "SOURCE_DISTRIBUTION.md", stage / "SOURCE_DISTRIBUTION.md")
        (stage / "alpine/installed-packages.txt").write_text(database, encoding="utf-8")
        manifest = dict(app_version=version, rootfs_sha256=rootfs_hash,
                        alpine_version=alpine_version, packages=packages,
                        origins=sorted(origins, key=lambda item: item["origin"]), proot=proot)
        (stage / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        (stage / "README.md").write_text(
            f"# Orange Island {version}: sandbox source distribution\n\n"
            "This archive contains the current PRoot and talloc source trees, the Android build script, "
            "and pinned Alpine package recipes with all SHA-512-listed source inputs and local patches. "
            "It is a source artifact, not evidence that a particular published APK was built from it.\n\n"
            "See manifest.json for exact package versions, commits, origins, and rootfs checksum. "
            "See SOURCE_DISTRIBUTION.md for rebuild and release verification steps.\n\n"
            "For PRoot/talloc, install the Android NDK version configured in the application and run "
            "`ANDROID_NDK_HOME=/path/to/ndk bash build-proot.sh --force` from this directory. "
            "The thirdparty/ layout is preserved.\n\n"
            f"For Alpine packages, use an Alpine {branch} aarch64 build environment and abuild. "
            "Each alpine/aports/main/<origin>/ directory contains APKBUILD and its source inputs; "
            "set SRCDEST to that directory for abuild checksum verification/building. "
            "Install the recipe's build dependencies. The original toolchain environment is not bundled.\n\n"
            "Copyright and license texts in the source trees and source archives remain authoritative. "
            "The repository MIT license does not relicense third-party sources.\n", encoding="utf-8")
        files = sorted(path for path in stage.rglob("*") if path.is_file())
        (stage / "SHA256SUMS").write_text("".join(f"{sha(path, 'sha256')}  {path.relative_to(stage).as_posix()}\n" for path in files), encoding="utf-8")
        temporary_output = Path(work) / output.name
        with tarfile.open(temporary_output, "w:gz") as archive:
            archive.add(stage, arcname=stage.name)
        verify_archive(temporary_output)
        # Publication name is created only after every origin and checksum passes.
        temporary_output.replace(output)
    print(f"Created {output}\nSHA256 {sha(output, 'sha256')}", flush=True)


def verify_archive(path):
    with tarfile.open(path) as archive:
        files = {member.name: member for member in archive.getmembers() if member.isfile()}
        roots = {PurePosixPath(name).parts[0] for name in files}
        if len(roots) != 1:
            raise ValueError("Archive must contain one source root")
        prefix = next(iter(roots)) + "/"
        def read(name):
            return archive.extractfile(files[prefix + name]).read()
        checked = set()
        for line in read("SHA256SUMS").decode().splitlines():
            expected, name = line.split("  ", 1)
            if hashlib.sha256(read(name)).hexdigest() != expected:
                raise ValueError(f"Archive checksum mismatch: {name}")
            checked.add(prefix + name)
        if checked != set(files) - {prefix + "SHA256SUMS"}:
            raise ValueError("Unlisted or missing archive files")
        manifest = json.loads(read("manifest.json"))
        installed = read_packages(read("alpine/installed-packages.txt").decode())
        if installed != manifest["packages"]:
            raise ValueError("Installed package manifest mismatch")
        expected_origins = {(p["origin"], p["commit"], p["version"]) for p in installed}
        origins = manifest["origins"]
        if expected_origins != {(o["origin"], o["commit"], o["version"]) for o in origins}:
            raise ValueError("Not all installed origins are covered")
        for origin in origins:
            for source in origin["sources"]:
                name = f"alpine/aports/main/{origin['origin']}/{source['file']}"
                if hashlib.sha512(read(name)).hexdigest() != source["sha512"]:
                    raise ValueError(f"Alpine input checksum mismatch: {name}")
        print(f"Archive verified: {len(installed)} packages, {len(origins)} origins, {len(checked)} files", flush=True)


if __name__ == "__main__":
    main()
