#!/bin/sh
# MFCQI CLI installer — downloads the prebuilt GraalVM native binary for your platform.
# No Java required. Usage:
#   curl -fsSL https://raw.githubusercontent.com/integrallis/mfcqi-java/main/install.sh | sh
# Environment overrides:
#   MFCQI_VERSION       version to install (default: latest), e.g. 0.2.0
#   MFCQI_INSTALL_DIR   target directory (default: $HOME/.local/bin)
set -eu

REPO="integrallis/mfcqi-java"
INSTALL_DIR="${MFCQI_INSTALL_DIR:-$HOME/.local/bin}"
VERSION="${MFCQI_VERSION:-latest}"

os="$(uname -s)"
arch="$(uname -m)"

case "$os" in
  Linux) os_name="linux" ;;
  Darwin) os_name="macos" ;;
  *)
    echo "Unsupported OS: $os. On Windows, use Scoop or download the .exe from the releases page." >&2
    exit 1
    ;;
esac

case "$arch" in
  x86_64 | amd64) arch_name="x86_64" ;;
  arm64 | aarch64) arch_name="aarch64" ;;
  *)
    echo "Unsupported architecture: $arch" >&2
    exit 1
    ;;
esac

if [ "$os_name" = "linux" ] && [ "$arch_name" = "aarch64" ]; then
  echo "No prebuilt linux-aarch64 binary yet — build from source (see the README)." >&2
  exit 1
fi

asset="mfcqi-${os_name}-${arch_name}"
if [ "$VERSION" = "latest" ]; then
  url="https://github.com/${REPO}/releases/latest/download/${asset}"
else
  url="https://github.com/${REPO}/releases/download/v${VERSION}/${asset}"
fi

echo "Installing mfcqi (${os_name}-${arch_name}, version: ${VERSION})..."
mkdir -p "$INSTALL_DIR"
tmp="$(mktemp)"
if ! curl -fsSL "$url" -o "$tmp"; then
  echo "Download failed: $url" >&2
  rm -f "$tmp"
  exit 1
fi
chmod +x "$tmp"
mv "$tmp" "$INSTALL_DIR/mfcqi"
echo "Installed: $INSTALL_DIR/mfcqi"

case ":$PATH:" in
  *":$INSTALL_DIR:"*) ;;
  *) echo "Note: $INSTALL_DIR is not on your PATH. Add it, e.g.:  export PATH=\"$INSTALL_DIR:\$PATH\"" ;;
esac

"$INSTALL_DIR/mfcqi" --version || true
