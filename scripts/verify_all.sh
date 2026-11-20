#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> [1/5] Running brand & machine-path hygiene check..."
"$ROOT_DIR/scripts/check_brand_hygiene.sh"

echo "==> [2/5] Running architecture, BOM, wire-parity & module-owner gate..."
"$ROOT_DIR/scripts/check_architecture_and_owners.sh"

echo "==> [3/5] Running KMP Gradle check..."
(
  cd "$ROOT_DIR/kmp"
  ./gradlew check --no-daemon
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
