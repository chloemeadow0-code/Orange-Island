#!/usr/bin/env python3
"""Render original notices from the verified, version-pinned source cache.

Run pack_sandbox_sources.py --collect-only first. This command prints the
result, or --check checks the checked-in asset without changing files.
"""
import argparse
from pathlib import Path
import re
import sys
import tarfile

from pack_sandbox_sources import ROOT, archive_text, read_packages, sha


def render():
    with tarfile.open(ROOT / "app/src/fdroid/assets/alpine-rootfs.bundle") as archive:
        packages = read_packages(archive_text(archive, "lib/apk/db/installed"))
    origins = {item["origin"]: item["commit"] for item in packages}
    cache = ROOT / "release/.sandbox-source-cache"
    sections = ["THIRD-PARTY COPYRIGHT AND LICENSE NOTICES\n"
                "Generated from the source inputs pinned by the bundled Alpine package database.\n"
                "Original notices below are retained in addition to the separate GPL/LGPL texts.\n"]

    def add(label, text):
        sections.append(label + "\n" + "=" * len(label) + "\n\n" + text.rstrip() + "\n")

    def source_archive(origin, filename):
        directory = cache / origins[origin] / origin
        recipe = (directory / "recipe/APKBUILD").read_text(encoding="utf-8")
        match = re.search(rf"^([a-f0-9]{{128}})\s+{re.escape(filename)}\s*$", recipe, re.MULTILINE)
        path = directory / "distfiles" / filename
        if not match or sha(path) != match.group(1):
            raise ValueError(f"Unverified source archive: {filename}")
        return tarfile.open(path)

    def leading_comment(text):
        match = re.search(r"/\*.*?\*/", text, re.DOTALL)
        return match.group(0) if match else ""

    add("PRoot contributors", (ROOT / "thirdparty/proot/COPYING").read_text(encoding="utf-8").split("GNU GENERAL PUBLIC LICENSE")[0])
    for name in ("talloc.c", "talloc.h"):
        add("talloc / " + name, leading_comment((ROOT / "thirdparty/talloc" / name).read_text(encoding="utf-8")))
    with source_archive("musl", "musl-1.2.5.tar.gz") as archive:
        add("musl / COPYRIGHT", archive_text(archive, "musl-1.2.5/COPYRIGHT"))
        # musl's COPYRIGHT explicitly points to additional permissive notices in
        # individual sources. Preserve distinct copyright/license header blocks.
        comments = {}
        for member in sorted(archive.getmembers(), key=lambda item: item.name):
            if not member.isfile() or Path(member.name).suffix not in (".c", ".h", ".S", ".s"):
                continue
            text = archive.extractfile(member).read().decode("utf-8", errors="replace")
            for block in re.findall(r"/\*.*?\*/", text[:20000], re.DOTALL):
                if re.search(r"copyright|permission|public domain", block, re.IGNORECASE):
                    comments.setdefault(block, []).append(member.name)
        for block, paths in comments.items():
            add("musl source notice: " + ", ".join(paths), block)
    for name in ("getconf.c", "getent.c", "iconv.c"):
        path = cache / origins["musl"] / "musl/recipe" / name
        add("Alpine musl-utils / " + name, leading_comment(path.read_text(encoding="utf-8")))
    with source_archive("openssl", "openssl-3.3.2.tar.gz") as archive:
        add("OpenSSL / crypto/init.c copyright notice", leading_comment(archive_text(archive, "openssl-3.3.2/crypto/init.c")))
        add("OpenSSL / LICENSE.txt", archive_text(archive, "openssl-3.3.2/LICENSE.txt"))
    with source_archive("zlib", "zlib-1.3.1.tar.gz") as archive:
        add("zlib / zlib.h", leading_comment(archive_text(archive, "zlib-1.3.1/zlib.h")))
    with source_archive("ca-certificates", "ca-certificates-20241010.tar.bz2") as archive:
        certificate_text = archive_text(archive, "ca-certificates-20241010/certdata.txt")
        add("Mozilla certificate data / certdata.txt", certificate_text.split("# certdata.txt")[0])
        perl_text = archive_text(archive, "ca-certificates-20241010/mk-ca-bundle.pl")
        # The source script retains its full initial copyright/license comment.
        header = []
        for line in perl_text.splitlines():
            if line.startswith("#") or not line.strip():
                header.append(line)
            else:
                break
        add("ca-certificates / mk-ca-bundle.pl", "\n".join(header))
    add("Mozilla Public License 2.0", (ROOT / "app/src/fdroid/assets/licenses/mpl-2.0.txt").read_text(encoding="utf-8"))
    add("Alpine metadata and signing keys", "The alpine-release and alpine-keys packages declare MIT in their pinned APKBUILD files.\n"
        "Maintainer recorded there: Natanael Copa. These packages contain release metadata and public signing keys.\n"
        "Their original recipe files are included in the source archive; no replacement copyright holder is asserted here.\n\n"
        + "Permission is hereby granted" + (ROOT / "LICENSE").read_text(encoding="utf-8").split("Permission is hereby granted", 1)[1])
    return "\n\n".join(sections).rstrip() + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    content = render()
    if args.check:
        saved = (ROOT / "app/src/fdroid/assets/licenses/third-party-notices.txt").read_text(encoding="utf-8")
        if content != saved:
            raise SystemExit("Bundled notices are stale; regenerate from the pinned source cache")
        print("Bundled copyright and license notices match the pinned sources")
    else:
        sys.stdout.reconfigure(encoding="utf-8")
        print(content, end="")
