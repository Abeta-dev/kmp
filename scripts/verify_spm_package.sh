#!/usr/bin/env bash
set -euo pipefail

PACKAGE_SWIFT=""
EXPECTED_TAG=""

usage() {
  echo "Usage: $0 <path/to/Package.swift> [--expected-tag <tag>]" >&2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --expected-tag)
      if [[ $# -lt 2 || -z "${2:-}" ]]; then
        echo "ERROR: --expected-tag requires a non-empty value." >&2
        usage
        exit 2
      fi
      EXPECTED_TAG="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    -*)
      echo "ERROR: Unknown option: $1" >&2
      usage
      exit 2
      ;;
    *)
      if [[ -n "$PACKAGE_SWIFT" ]]; then
        echo "ERROR: Unexpected extra argument: $1" >&2
        usage
        exit 2
      fi
      PACKAGE_SWIFT="$1"
      shift
      ;;
  esac
done

if [[ -z "$PACKAGE_SWIFT" ]]; then
  echo "ERROR: Missing required path to Package.swift." >&2
  usage
  exit 2
fi

if [[ ! -f "$PACKAGE_SWIFT" ]]; then
  echo "ERROR: Package.swift not found: ${PACKAGE_SWIFT}" >&2
  exit 1
fi

echo "==> Verifying SwiftPM manifest: ${PACKAGE_SWIFT}"

python3 - "$PACKAGE_SWIFT" "$EXPECTED_TAG" <<'PY'
import re
import sys

manifest_path = sys.argv[1]
expected_tag = sys.argv[2]

with open(manifest_path, "r", encoding="utf-8") as fh:
    content = fh.read()

lines = content.splitlines()
errors = []

if not lines or not re.match(r"^//\s*swift-tools-version:\s*[0-9]+\.[0-9]+(\.[0-9]+)?\s*$", lines[0]):
    first = lines[0] if lines else "<empty file>"
    errors.append(f"First line must start with '// swift-tools-version: <X.Y>'; found: {first!r}")

placeholder_patterns = [
    r"REPLACE_WITH",
    r"PLACEHOLDER",
    r"<[A-Z0-9_]+>",
    r"\bTODO\b",
    r"\bFIXME\b",
]
for pat in placeholder_patterns:
    if re.search(pat, content):
        errors.append(f"Manifest contains forbidden placeholder matching /{pat}/")

required_targets = ("AbetaSduiKit", "AbetaWatchKit")

for name in required_targets:
    lib_pat = rf'\.library\s*\([^)]*name:\s*"{name}"[^)]*targets:\s*\[\s*"{name}"\s*\]'
    if not re.search(lib_pat, content, re.S):
        errors.append(f"Missing .library product declaration for '{name}'")

binary_blocks = re.findall(r"\.binaryTarget\s*\((.*?)\)", content, re.S)
parsed_targets = {}
for block in binary_blocks:
    name_m = re.search(r'name:\s*"([^"]+)"', block)
    url_m = re.search(r'url:\s*"([^"]+)"', block)
    sha_m = re.search(r'checksum:\s*"([^"]+)"', block)
    if name_m:
        parsed_targets[name_m.group(1)] = (
            url_m.group(1) if url_m else "",
            sha_m.group(1) if sha_m else "",
        )

sha256_re = re.compile(r"^[0-9a-f]{64}$")

for name in required_targets:
    if name not in parsed_targets:
        errors.append(f"Missing .binaryTarget declaration for '{name}'")
        continue

    url, checksum = parsed_targets[name]
    if not url.startswith("https://github.com/"):
        errors.append(f"Target '{name}' URL must start with 'https://github.com/'; found: {url!r}")
    if not url.endswith(".xcframework.zip"):
        errors.append(f"Target '{name}' URL must end with '.xcframework.zip'; found: {url!r}")
    if expected_tag:
        expected_suffix = f"/releases/download/{expected_tag}/{name}-{expected_tag}.xcframework.zip"
        if not url.endswith(expected_suffix):
            errors.append(
                f"Target '{name}' URL must end with '{expected_suffix}'; found: {url!r}"
            )

    if not sha256_re.match(checksum):
        errors.append(
            f"Target '{name}' checksum must be a 64-character lowercase hex SHA-256; found: {checksum!r}"
        )
    elif len(set(checksum)) == 1:
        errors.append(f"Target '{name}' checksum appears to be a dummy repeated-character placeholder: {checksum!r}")

if errors:
    for err in errors:
        print(f"ERROR: {err}", file=sys.stderr)
    sys.exit(1)

print("  [ok] Static Package.swift checks passed (swift-tools-version, GitHub URLs, SHA-256 checksums).")
PY

if command -v swift >/dev/null 2>&1; then
  TMP_SPM_DIR="$(mktemp -d)"
  trap 'rm -rf "${TMP_SPM_DIR:-}"' EXIT

  cp "$PACKAGE_SWIFT" "${TMP_SPM_DIR}/Package.swift"

  if ! (cd "$TMP_SPM_DIR" && swift package dump-package > "${TMP_SPM_DIR}/dump.json" 2> "${TMP_SPM_DIR}/dump.err"); then
    echo "ERROR: 'swift package dump-package' failed:" >&2
    cat "${TMP_SPM_DIR}/dump.err" >&2
    exit 1
  fi

  python3 - "${TMP_SPM_DIR}/dump.json" "$EXPECTED_TAG" <<'PY'
import json
import re
import sys

dump_path = sys.argv[1]
expected_tag = sys.argv[2]

with open(dump_path, "r", encoding="utf-8") as fh:
    data = json.load(fh)

errors = []
if not isinstance(data, dict):
    errors.append("swift package dump-package output is not a JSON object")
else:
    required = ("AbetaSduiKit", "AbetaWatchKit")
    products = {p.get("name"): p for p in data.get("products", []) if isinstance(p, dict)}
    targets = {t.get("name"): t for t in data.get("targets", []) if isinstance(t, dict)}
    platforms = {p.get("platformName"): p.get("version") for p in data.get("platforms", []) if isinstance(p, dict)}

    if "ios" not in platforms:
        errors.append("Manifest missing iOS platform declaration")
    if "watchos" not in platforms:
        errors.append("Manifest missing watchOS platform declaration")

    sha256_re = re.compile(r"^[0-9a-f]{64}$")

    for name in required:
        prod = products.get(name)
        if not prod:
            errors.append(f"dump-package missing product '{name}'")
        elif name not in prod.get("targets", []):
            errors.append(f"dump-package product '{name}' does not expose target '{name}'")

        tgt = targets.get(name)
        if not tgt:
            errors.append(f"dump-package missing target '{name}'")
            continue
        if tgt.get("type") != "binary":
            errors.append(f"dump-package target '{name}' has type {tgt.get('type')!r}; expected 'binary'")

        url = tgt.get("url") or ""
        checksum = tgt.get("checksum") or ""

        if not url.startswith("https://github.com/"):
            errors.append(f"dump-package target '{name}' URL must start with 'https://github.com/'; got {url!r}")
        if expected_tag:
            expected_suffix = f"/releases/download/{expected_tag}/{name}-{expected_tag}.xcframework.zip"
            if not url.endswith(expected_suffix):
                errors.append(f"dump-package target '{name}' URL must end with '{expected_suffix}'; got {url!r}")

        if not sha256_re.match(checksum) or len(set(checksum)) == 1:
            errors.append(f"dump-package target '{name}' has invalid SHA-256 checksum: {checksum!r}")

if errors:
    for err in errors:
        print(f"ERROR: {err}", file=sys.stderr)
    sys.exit(1)

print("  [ok] 'swift package dump-package' JSON parsed cleanly with AbetaSduiKit & AbetaWatchKit products/binary targets.")
PY
else
  echo "  [skip] 'swift' CLI not installed on this host; skipped 'swift package dump-package' dynamic check."
fi

echo "PASSED: SwiftPM manifest verification succeeded (${PACKAGE_SWIFT})."
