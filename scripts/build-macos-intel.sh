#!/usr/bin/env bash
# Build the macOS Intel (x86_64) native binary on an Intel Mac and upload it to a release.
#
# GraalVM native-image cannot cross-compile, and GitHub no longer offers reliable Intel-mac
# runners, so the Intel-mac binary is built by a maintainer on actual Intel hardware and attached
# to the release. The script also adds the binary to the Homebrew formula after the automated
# native workflow publishes the Apple Silicon/Linux formula.
#
# Prerequisites on the Intel Mac:
#   - This repo, checked out at the release tag you are building for.
#   - A GraalVM JDK (e.g. `sdk install java 21.0.2-graalce`) on JAVA_HOME or PATH.
#   - GitHub CLI (`gh`) authenticated with write access to this repo and integrallis/homebrew-tap.
#   - The native workflow for the release has finished publishing the initial Homebrew formula.
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
TAP_DIR="$(mktemp -d)/homebrew-tap"
trap 'rm -rf "$(dirname "$TAP_DIR")"' EXIT

echo "Building $ASSET for $TAG (version $VERSION)..."
./gradlew :mfcqi-cli:nativeCompile --no-daemon

cp mfcqi-cli/build/native/nativeCompile/mfcqi "$ASSET"
strip "$ASSET" || true
echo "Built: $(./"$ASSET" --version)"

echo "Uploading $ASSET to release $TAG..."
gh release upload "$TAG" "$ASSET" --clobber

echo "Adding $ASSET to the Homebrew formula..."
SHA_MAC_INTEL="$(shasum -a 256 "$ASSET" | awk '{print $1}')"
gh repo clone integrallis/homebrew-tap "$TAP_DIR" -- --depth 1

FORMULA="$TAP_DIR/Formula/mfcqi.rb" VERSION="$VERSION" TAG="$TAG" \
  SHA_MAC_INTEL="$SHA_MAC_INTEL" ruby <<'RUBY_SCRIPT'
path = ENV.fetch("FORMULA")
version = ENV.fetch("VERSION")
tag = ENV.fetch("TAG")
sha = ENV.fetch("SHA_MAC_INTEL")
formula = File.read(path)
expected_version = %(version "#{version}")
abort "Homebrew formula is not at #{version}; wait for the native workflow to finish" unless formula.include?(expected_version)

marker = "    # Intel macOS: no native binary is published; use the JVM distribution or build from source."
intel = <<~FORMULA.chomp.lines(chomp: true).map { |line| "    #{line}" }.join("\n")
  on_intel do
    url "https://github.com/integrallis/mfcqi-java/releases/download/#{tag}/mfcqi-macos-x86_64"
    sha256 "#{sha}"
  end
FORMULA
abort "Homebrew formula does not contain the expected Intel placeholder" unless formula.sub!(marker, intel)
File.write(path, formula)
RUBY_SCRIPT

git -C "$TAP_DIR" add Formula/mfcqi.rb
git -C "$TAP_DIR" commit -m "mfcqi $VERSION"
git -C "$TAP_DIR" push

echo "Done. Intel-mac users can now install mfcqi with Homebrew, install.sh, or direct download."
