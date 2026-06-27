#!/usr/bin/env bash
# Build the macOS Intel (x86_64) native binary on an Intel Mac and upload it to a release.
#
# GraalVM native-image cannot cross-compile, and GitHub no longer offers reliable Intel-mac
# runners, so the Intel-mac binary (if wanted) is built by a maintainer on actual Intel hardware
# and attached to the release. Once uploaded, `install.sh` and direct download pick it up
# automatically (Homebrew remains Apple-Silicon-only for macOS).
#
# Prerequisites on the Intel Mac:
#   - This repo, checked out at the release tag you are building for.
#   - A GraalVM JDK (e.g. `sdk install java 21.0.2-graalce`) on JAVA_HOME or PATH.
#   - GitHub CLI (`gh`) authenticated with write access to the repo.
#
# Usage:
#   scripts/build-macos-intel.sh [tag]
#   # tag defaults to "v<version>" read from gradle.properties.
set -euo pipefail

cd "$(dirname "$0")/.."

# --- sanity checks -----------------------------------------------------------
if [ "$(uname -s)" != "Darwin" ] || [ "$(uname -m)" != "x86_64" ]; then
  echo "This must run on an Intel (x86_64) Mac. Detected: $(uname -s)/$(uname -m)." >&2
  echo "native-image builds for the host architecture only — it cannot cross-compile." >&2
  exit 1
fi

if ! command -v native-image >/dev/null 2>&1 && [ ! -x "${JAVA_HOME:-}/bin/native-image" ]; then
  echo "GraalVM native-image not found. Install GraalVM and set JAVA_HOME, e.g.:" >&2
  echo "  sdk install java 21.0.2-graalce && sdk use java 21.0.2-graalce" >&2
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) not found. Install it and run 'gh auth login'." >&2
  exit 1
fi

VERSION="$(sed -E 's/.*=[[:space:]]*//' gradle.properties)"
TAG="${1:-v$VERSION}"
ASSET="mfcqi-macos-x86_64"

echo "Building $ASSET for $TAG (version $VERSION)..."
./gradlew :mfcqi-cli:nativeCompile --no-daemon

cp mfcqi-cli/build/native/nativeCompile/mfcqi "$ASSET"
strip "$ASSET" || true
echo "Built: $(./"$ASSET" --version)"

echo "Uploading $ASSET to release $TAG..."
gh release upload "$TAG" "$ASSET" --clobber
echo "Done. Intel-mac users can now: curl -fsSL .../install.sh | sh  (or download $ASSET directly)."
