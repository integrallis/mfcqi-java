class Mfcqi < Formula
  desc "Multi-Factor Code Quality Index for Java codebases"
  homepage "https://github.com/integrallis/mfcqi-java"
  version "${VERSION}"
  license "MIT"

  on_macos do
    on_arm do
      url "${BASE}/mfcqi-macos-aarch64"
      sha256 "${SHA_MAC_ARM}"
    end
    on_intel do
      url "${BASE}/mfcqi-macos-x86_64"
      sha256 "${SHA_MAC_X64}"
    end
  end

  on_linux do
    on_intel do
      url "${BASE}/mfcqi-linux-x86_64"
      sha256 "${SHA_LINUX_X64}"
    end
  end

  def install
    bin.install Dir["mfcqi-*"].first => "mfcqi"
  end

  test do
    assert_match "mfcqi-java", shell_output("#{bin}/mfcqi --version")
  end
end
