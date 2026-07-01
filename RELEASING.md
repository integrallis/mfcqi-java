# Releasing & CI

Maintainer guide for cutting releases and the automation behind them. End-user install
instructions live in the [README](README.md#installation).

## TL;DR — cut a release

1. Bump `version` in `gradle.properties` (the single source of truth), update `CHANGELOG.md`,
   commit, and push to `main`.
2. **Actions → Release → Run workflow**, leave **`dry_run` checked** → validates signing, POM
   rules, and bundle assembly without publishing anything.
3. Run **Release** again with **`dry_run` unchecked**.

That run publishes every automated artifact:

```
Release (dry_run off)
  → JReleaser deploy            → Maven Central (the 10 library modules)
  → gh release create           → GitHub release + JVM zip/tar (mfcqi-<version>.zip/.tar)
  → dispatch native.yml         → linux-x64 / macos-arm64 / windows-x64 native binaries → attached
       → publish-packages       → Homebrew tap + Scoop bucket bumped to the new version
```

The version flows automatically into Maven coordinates, `--version`, the SARIF report, the JVM
dist filenames, the native-binary release assets, the auto-created `v<version>` tag, and the
Homebrew/Scoop manifests.

After `native.yml` finishes, run `scripts/build-macos-intel.sh v<version>` on an Intel Mac. It
builds and uploads the Intel binary, then adds its verified checksum to the Homebrew formula.

## What gets published where

| Artifact | Destination | Built by |
|---|---|---|
| 11 library modules (`mfcqi-core`, …, `mfcqi-kotlin`) | Maven Central (`com.integrallis`) | `release.yml` (JReleaser) |
| CLI JVM distribution (`mfcqi-<v>.zip`/`.tar`, needs a JRE) | GitHub release | `release.yml` (`gh`) |
| CLI native binaries (linux-x64, macos-arm64, windows-x64) | GitHub release | `native.yml` (GraalVM) |
| CLI native binary (macos-x64) | GitHub release | `scripts/build-macos-intel.sh` (GraalVM) |
| Homebrew formula | `integrallis/homebrew-tap` | `native.yml` → `publish-packages` |
| Scoop manifest | `integrallis/scoop-bucket` | `native.yml` → `publish-packages` |

The CLI is **not** published to Maven Central — only the libraries are. `mfcqi-kotlin` is a standard
library publication and declares PMD's Maven Central-hosted `pmd-kotlin` artifact as a runtime
dependency. It stages into `build/staging-deploy` with the other modules. Kotlin support also ships
inside every CLI distribution and is verified by the GraalVM native build.

> **Build note:** `mfcqi-kotlin` compiles on a **JDK 21 toolchain** (`jvmToolchain(21)`). CI runners
> that build on JDK 25 (`build.yml`, `early-access.yml`) also install 21; the
> `foojay-resolver-convention` settings plugin auto-provisions it anywhere it's missing. The release
> runners (`release.yml`, `native.yml`) already use JDK/GraalVM 21.

## Workflows

| Workflow | Trigger | Purpose |
|---|---|---|
| `build.yml` | pull requests | `spotlessCheck` + `gradle build` (tests + 80% coverage gate + SpotBugs) |
| `early-access.yml` | push to `main` | `gradle build test` |
| `badge.yml` | push to `main` (src changes) / dispatch | regenerate `.github/badges/mfcqi.json` from the library score and commit it |
| `release.yml` | manual (`workflow_dispatch`) | deploy to Central, create the GitHub release, dispatch `native.yml` |
| `native.yml` | manual + `release: published` | build native binaries per platform, attach them, publish Homebrew/Scoop |

`release.yml` inputs: `dry_run` (default **true** — validate only) and `skip_deploy` (re-create only
the GitHub release without re-deploying to Central). `native.yml` inputs: `tag` (release to upload
to) and `ref` (git ref to build from; defaults to the tag).

## Required secrets

Set on the `integrallis/mfcqi-java` repo (Settings → Secrets and variables → Actions):

| Secret | Used by | What |
|---|---|---|
| `MAVENCENTRAL_USERNAME` / `MAVENCENTRAL_PASSWORD` | `release.yml` | Central Portal **user token** (not your login) |
| `GPG_PUBLIC_KEY` / `GPG_SECRET_KEY` | `release.yml` | ASCII-armored GPG keypair for signing Central artifacts |
| `GPG_PASSPHRASE` | `release.yml` | passphrase for the private key |
| `PACKAGES_PUBLISH_TOKEN` | `release.yml`, `native.yml` | PAT — see below |
| `GITHUB_TOKEN` | all | provided automatically |

`PACKAGES_PUBLISH_TOKEN` needs:
- **Contents: write** on `integrallis/homebrew-tap` and `integrallis/scoop-bucket` (push the
  formula/manifest), and
- **Actions: write** on `integrallis/mfcqi-java` (so `release.yml` can dispatch `native.yml` — a
  `GITHUB_TOKEN` cannot trigger another workflow).

Fine-grained PAT: set **Resource owner = integrallis** (org must allow fine-grained PATs first) and
select those three repos. A classic PAT with `repo` + `workflow` scopes also works.

## One-time setup

1. **Maven Central namespace** — register `com.integrallis` on `central.sonatype.com`, verify
   domain ownership via a DNS `TXT` record on `integrallis.com`, and generate a publishing user
   token (→ `MAVENCENTRAL_*`).
2. **GPG signing key** — `gpg --gen-key` (use a passphrase), publish the public key
   (`gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>`), and export the armored public +
   secret keys into the `GPG_*` secrets.
3. **Package repos** — create public `integrallis/homebrew-tap` and `integrallis/scoop-bucket`.
4. **PAT** — create `PACKAGES_PUBLISH_TOKEN` with the permissions above.

## Native binaries (GraalVM)

The CLI is compiled to a native executable via the `org.graalvm.buildtools.native` Gradle plugin
on `mfcqi-cli`. Reflection/resource config is committed under
`mfcqi-cli/src/main/resources/META-INF/native-image/` (Picocli's portion is generated by
`picocli-codegen` at compile time).

Build locally (needs a GraalVM JDK, e.g. `sdk install java 21.0.2-graalce`):

```bash
JAVA_HOME=<graalvm> ./gradlew :mfcqi-cli:nativeCompile
# -> mfcqi-cli/build/native/nativeCompile/mfcqi
```

Before uploading or publishing a native binary, run the real-repository smoke test. It clones a few
public Java/Kotlin repositories into a temporary directory and fails if the native CLI crashes or
does not emit a numeric score:

```bash
scripts/smoke-real-repos.sh
MFCQI_PARALLELISM=4 scripts/smoke-real-repos.sh
```

GraalVM native-image **cannot cross-compile**, so each binary is built on its own platform. CI
covers linux-x64 (ubuntu), macos-arm64 (macos-14), and windows-x64. **macOS Intel** is omitted
(GitHub Intel-mac runners are scarce/unreliable); after `native.yml` finishes, a maintainer builds
it on Intel hardware with [`scripts/build-macos-intel.sh`](scripts/build-macos-intel.sh). The script
uploads the binary and updates the Homebrew tap, after which Homebrew, `install.sh`, and direct
download all serve it. Intel-mac users can otherwise use the JVM zip.

### Regenerating the native-image config

If a dependency or code path changes what reflection/resources are needed, re-capture the config
with the tracing agent, then commit the updated files:

```bash
./gradlew :mfcqi-cli:installDist
CP=$(echo mfcqi-cli/build/install/mfcqi/lib/*.jar | tr ' ' ':')
CONF=mfcqi-cli/src/main/resources/META-INF/native-image/com.integrallis/mfcqi-cli
# Exercise every path so the agent records all of it (metrics, json/sarif, badge, quality-gate,
# and the LLM path):
<graalvm>/bin/java -agentlib:native-image-agent=config-merge-dir=$CONF -cp "$CP" \
  com.integrallis.mfcqi.cli.Main analyze <some-project> --skip-llm --format sarif
# ...repeat for the other commands, then rebuild with `nativeCompile` and verify.
```

## Versioning

`gradle.properties` `version` is the only place the version is set. At build time
`mfcqi-core` generates `mfcqi-version.properties`, read at runtime by `Version.current()`; the
Picocli `--version` provider and the SARIF report both use it. Bumping that one line is the whole
version change.

The **docs landing page** also tracks it: `pages.yml` rewrites the Maven install snippet's version
from `gradle.properties` at deploy time, and re-publishes on `release: published` — so the site's
`com.integrallis:mfcqi-core:<version>` always matches the released version with no manual edit.

## Troubleshooting

- **Always dry-run first.** It validates the entire deploy path (signing, Central POM rules,
  bundle) without touching anything immutable. Maven Central releases are immutable once published.
- **GitHub release failed after a successful deploy?** Re-run Release with `skip_deploy=true` to
  recreate only the GitHub release (Central is already done; re-deploying would collide).
- **`native.yml` didn't auto-fire?** Check `PACKAGES_PUBLISH_TOKEN` has **Actions: write** on this
  repo; otherwise dispatch `native.yml` manually with the tag.
- **Logs:** `release.yml` uploads `jreleaser-logs` (with `out/jreleaser/trace.log`) as a run
  artifact on failure.
