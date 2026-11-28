#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Auto-detect ANDROID_HOME when unset (respecting ANDROID_SDK_ROOT fallback as well).
if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "${ANDROID_SDK_ROOT}" ]; then
    ANDROID_HOME="${ANDROID_SDK_ROOT}"
  else
    for candidate in \
      "${HOME}/Library/Android/sdk" \
      "${HOME}/Android/Sdk" \
      "/opt/homebrew/share/android-commandlinetools"; do
      if [ -d "${candidate}/platforms/android-37.0" ] || [ -d "${candidate}/platforms/android-37" ]; then
        ANDROID_HOME="${candidate}"
        break
      fi
    done
    if [ -z "${ANDROID_HOME:-}" ]; then
      for candidate in \
        "${HOME}/Library/Android/sdk" \
        "${HOME}/Android/Sdk" \
        "/opt/homebrew/share/android-commandlinetools"; do
        if [ -d "${candidate}" ]; then
          ANDROID_HOME="${candidate}"
          break
        fi
      done
    fi
  fi
fi

if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "${ANDROID_HOME}" ]; then
  cat >&2 <<'EOF'
ERROR: ANDROID_HOME is not set and no Android SDK was found in standard locations
  ($HOME/Library/Android/sdk, $HOME/Android/Sdk, /opt/homebrew/share/android-commandlinetools).

Action required:
  1. Install the Android SDK with platform API 37 (`platforms;android-37.0`), e.g.:
       sdkmanager "platforms;android-37.0"
  2. Export ANDROID_HOME before re-running ./scripts/verify_all.sh:
       export ANDROID_HOME="$HOME/Library/Android/sdk"   # macOS
       export ANDROID_HOME="$HOME/Android/Sdk"           # Linux
EOF
  exit 1
fi

export ANDROID_HOME
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

if [ ! -d "${ANDROID_HOME}/platforms/android-37.0" ] && [ ! -d "${ANDROID_HOME}/platforms/android-37" ]; then
  cat >&2 <<EOF
ERROR: Android SDK platform API 37 is missing at: ${ANDROID_HOME}/platforms/android-37.0

Action required:
  Install API 37 via Android Studio SDK Manager or sdkmanager:
    sdkmanager --sdk_root="${ANDROID_HOME}" "platforms;android-37.0"
EOF
  exit 1
fi

echo "==> Using Android SDK: ${ANDROID_HOME} (platforms/android-37.0 verified)"

echo "==> [1/5] Running brand & machine-path hygiene check..."
"$ROOT_DIR/scripts/check_brand_hygiene.sh"

echo "==> [2/5] Running architecture, BOM, wire-parity, CodeGraph & module-owner gate..."
"$ROOT_DIR/scripts/check_architecture_and_owners.sh"

echo "==> [3/5] Running KMP Gradle check & HMPP metadata jars..."
(
  cd "$ROOT_DIR/kmp"
  ./gradlew check allMetadataJar --no-daemon
)

echo "==> [4/5] Running Sample App headless smoke verification..."
(
  cd "$ROOT_DIR/kmp"
  ./gradlew :sample-app:run --args="--headless" --no-daemon
)

echo "==> [5/5] Running Go BFF vet & race tests..."
(
  cd "$ROOT_DIR/bff"
  go vet ./...
  go test -race -cover ./...
)

echo "PASSED: All abeta-dev/kmp verification gates succeeded."
