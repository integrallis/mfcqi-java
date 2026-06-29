#!/usr/bin/env bash
# Native CLI smoke test against real OSS repositories.
#
# This is intentionally opt-in: it clones public repositories and exercises the
# native binary on code that is much richer than the synthetic unit fixtures.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BIN="${MFCQI_BIN:-$ROOT/mfcqi-cli/build/native/nativeCompile/mfcqi}"
WORKDIR="${MFCQI_SMOKE_DIR:-${TMPDIR:-/tmp}/mfcqi-real-repos-smoke}"
REPORTS="$WORKDIR/reports"
TIMEOUT_SECONDS="${MFCQI_SMOKE_TIMEOUT:-180}"
PARALLELISM="${MFCQI_PARALLELISM:-1}"

if [ ! -x "$BIN" ]; then
  echo "Native binary not found: $BIN" >&2
  echo "Build it first with: ./gradlew :mfcqi-cli:nativeCompile" >&2
  exit 2
fi

mkdir -p "$WORKDIR/repos" "$REPORTS"

clone_or_update() {
  local name=$1
  local url=$2
  local dest="$WORKDIR/repos/$name"
  if [ -d "$dest/.git" ]; then
    git -C "$dest" fetch --depth 1 origin
    git -C "$dest" reset --hard FETCH_HEAD
  else
    git clone --depth 1 "$url" "$dest"
  fi
}

run_with_timeout() {
  perl -e '
    my $timeout = shift @ARGV;
    exec @ARGV unless $timeout && $timeout > 0;
    my $pid = fork();
    die "fork failed: $!" unless defined $pid;
    if ($pid == 0) {
      exec @ARGV or die "exec failed: $!";
    }
    local $SIG{ALRM} = sub {
      kill "TERM", $pid;
      sleep 2;
      kill "KILL", $pid;
      die "timed out after ${timeout}s\n";
    };
    alarm $timeout;
    waitpid($pid, 0);
    alarm 0;
    exit($? >> 8);
  ' "$TIMEOUT_SECONDS" "$@"
}

run_analysis() {
  local name=$1
  local language=$2
  local target=$3
  local report="$REPORTS/$name.json"
  local log="$REPORTS/$name.log"

  echo "Analyzing $name ($language): $target" >&2
  if [ "$language" = "kotlin" ]; then
    run_with_timeout "$BIN" analyze "$target" --language kotlin --parallelism "$PARALLELISM" --format json --output "$report" >"$log" 2>&1
  else
    run_with_timeout "$BIN" analyze "$target" --parallelism "$PARALLELISM" --format json --output "$report" >"$log" 2>&1
  fi

  local score
  score="$(jq -r '.mfcqi_score // empty' "$report")"
  case "$score" in
    ''|*[!0-9.]*)
      echo "No numeric mfcqi_score for $name; see $report and $log" >&2
      exit 1
      ;;
  esac
  printf "%s\t%s\t%s\n" "$name" "$language" "$score"
}

clone_or_update gson https://github.com/google/gson.git
clone_or_update picocli https://github.com/remkop/picocli.git
clone_or_update okio https://github.com/square/okio.git
clone_or_update turbine https://github.com/cashapp/turbine.git

printf "repo\tlanguage\tscore\n" | tee "$REPORTS/summary.tsv"
run_analysis gson java "$WORKDIR/repos/gson/gson/src/main/java" | tee -a "$REPORTS/summary.tsv"
run_analysis picocli java "$WORKDIR/repos/picocli/src/main/java" | tee -a "$REPORTS/summary.tsv"
run_analysis okio-kotlin kotlin "$WORKDIR/repos/okio/okio/src/commonMain/kotlin" | tee -a "$REPORTS/summary.tsv"
run_analysis turbine-kotlin kotlin "$WORKDIR/repos/turbine/src/commonMain/kotlin" | tee -a "$REPORTS/summary.tsv"

echo "Reports written to $REPORTS"
