# Sandbox source distribution

The application-level MIT license does not replace the licenses of bundled
components. PRoot is GPL-2.0-or-later, talloc is LGPL-3.0-or-later, and Alpine
packages use several licenses, including GPL, LGPL, MIT, BSD, MPL, Apache and
Zlib. Preserve their applicable copyright notices and license texts.

## Generate the source artifact

From the repository root, with Python 3.11 or newer and Git installed:

```sh
python scripts/pack_sandbox_sources.py
```

On Linux, `./pack-gpl-sources.sh` invokes the same collector. Network access is
needed on the first run. Downloads are cached under `release/.sandbox-source-cache/`.

The collector reads the actual bundled rootfs package database. For every
package origin it uses that package's exact aports commit, verifies the recipe
version, includes the complete recipe directory, downloads all SHA-512-listed
sources, and verifies their checksums. It does not execute downloaded recipes.
Missing inputs or mismatches stop packaging. It also includes the current
PRoot/talloc trees, their notices, and the Android build script with its patches.

Output: `release/gpl-sources-v<version>-corrected.tar.gz`, with a manifest and
file checksums inside. Existing output is not overwritten. Historical archives
without this verification must not be assumed complete or reused for releases.

## Verify and distribute with a release

1. Build the sandbox binaries from the same source trees captured in the
   archive. Confirm the APK's rootfs checksum matches `manifest.json` and that
   the final APK includes the third-party notices and full license texts.
2. Rebuild PRoot/talloc using the included `build-proot.sh` and the Android NDK
   version from the release's build configuration. The archive preserves the
   script's expected `thirdparty/proot` and `thirdparty/talloc` paths.
3. Alpine recipes and inputs are under `alpine/aports/main/<origin>/`. Use an
   Alpine aarch64 build environment matching the rootfs branch, install the
   dependencies named by APKBUILD, and use abuild with SRCDEST pointing to that
   origin directory. The archive includes source inputs, not a compiler image.
4. Attach the verified source archive beside the matching APK in the
   [release repository](https://github.com/chloemeadow0-code/Orange-Island-Releases/releases)
   and clearly link it in that release's description. Verify both are publicly
   downloadable. Generating a local file does not publish it.
5. Keep the source available for distributed versions. If relying on a written
   source offer instead of accompanying source, establish and fulfill the
   applicable license's offer requirements; a generic upstream link is not a
   substitute for the matching source distribution.

This workflow does not certify older APKs or artifacts built from a different
checkout. A corrected source archive cannot by itself change notices already
embedded in an APK. Rebuild the APK when bundled notices change.

## 用户获取源码

请在安装版本对应的发布页查找 `gpl-sources-v<版本>-corrected.tar.gz`，
其中包含组件版本清单、源码、构建配方、补丁和校验值。若对应文件缺失或无法下载，
请向[发布仓库](https://github.com/chloemeadow0-code/Orange-Island-Releases/issues)
反馈版本号。维护者发布 APK 前应实际上传并验证源码附件，不能仅以本文作为“已提供源码”的证明。
