#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE_SWIFT=""
EXPECTED_TAG=""
SDUI_FRAMEWORK_DIR=""
WATCH_FRAMEWORK_DIR=""

usage() {
  cat >&2 <<EOF
Usage: $0 <path/to/Package.swift> [--expected-tag <tag>] [--sdui-framework <dir>] [--watch-framework <dir>]

Options:
  --expected-tag <tag>       Verify binary target URLs match releases/download/<tag>/<Target>-<tag>.xcframework.zip
  --sdui-framework <dir>     On macOS, swiftc -typecheck AbetaSduiSwiftUI.swift & AbetaAppleHosts.swift (Swift 5 & 6)
                             against <dir> (slice directory containing AbetaSduiKit.framework or .xcframework root)
  --watch-framework <dir>    On macOS, swiftc -typecheck AbetaWatchSwiftUI.swift & AbetaAppleHosts.swift (Swift 5 & 6)
                             against <dir> (slice directory containing AbetaWatchKit.framework or .xcframework root)
EOF
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
    --sdui-framework)
      if [[ $# -lt 2 || -z "${2:-}" ]]; then
        echo "ERROR: --sdui-framework requires a non-empty value." >&2
        usage
        exit 2
      fi
      SDUI_FRAMEWORK_DIR="$2"
      shift 2
      ;;
    --watch-framework)
      if [[ $# -lt 2 || -z "${2:-}" ]]; then
        echo "ERROR: --watch-framework requires a non-empty value." >&2
        usage
        exit 2
      fi
      WATCH_FRAMEWORK_DIR="$2"
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

MANIFEST_DIR="$(cd "$(dirname "$PACKAGE_SWIFT")" && pwd)"
SOURCES_DIR=""
if [[ -d "${MANIFEST_DIR}/Sources" ]]; then
  SOURCES_DIR="${MANIFEST_DIR}/Sources"
elif [[ -d "${ROOT_DIR}/kmp/platforms/apple/Sources" ]]; then
  SOURCES_DIR="${ROOT_DIR}/kmp/platforms/apple/Sources"
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

required_products = ("AbetaSduiKit", "AbetaWatchKit", "AbetaWatchSwiftUI", "AbetaSduiSwiftUI")
required_binary_targets = ("AbetaSduiKit", "AbetaWatchKit")
required_source_targets = {
    "AbetaWatchSwiftUI": ("AbetaWatchKit", "Sources/AbetaWatchSwiftUI"),
    "AbetaSduiSwiftUI": ("AbetaSduiKit", "Sources/AbetaSduiSwiftUI"),
}

for name in required_products:
    lib_pat = rf'\.library\s*\([^)]*name:\s*"{name}"[^)]*targets:\s*\[\s*"{name}"\s*\]'
    if not re.search(lib_pat, content, re.S):
        errors.append(f"Missing .library product declaration for '{name}'")

binary_blocks = re.findall(r"\.binaryTarget\s*\((.*?)\)", content, re.S)
parsed_binary_targets = {}
for block in binary_blocks:
    name_m = re.search(r'name:\s*"([^"]+)"', block)
    url_m = re.search(r'url:\s*"([^"]+)"', block)
    sha_m = re.search(r'checksum:\s*"([^"]+)"', block)
    if name_m:
        parsed_binary_targets[name_m.group(1)] = (
            url_m.group(1) if url_m else "",
            sha_m.group(1) if sha_m else "",
        )

sha256_re = re.compile(r"^[0-9a-f]{64}$")

for name in required_binary_targets:
    if name not in parsed_binary_targets:
        errors.append(f"Missing .binaryTarget declaration for '{name}'")
        continue

    url, checksum = parsed_binary_targets[name]
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

source_blocks = re.findall(r"\.target\s*\((.*?)\)", content, re.S)
parsed_source_targets = {}
for block in source_blocks:
    name_m = re.search(r'name:\s*"([^"]+)"', block)
    path_m = re.search(r'path:\s*"([^"]+)"', block)
    deps_m = re.search(r'dependencies:\s*\[(.*?)\]', block, re.S)
    if name_m:
        deps = re.findall(r'"([^"]+)"', deps_m.group(1)) if deps_m else []
        parsed_source_targets[name_m.group(1)] = (
            path_m.group(1) if path_m else "",
            deps,
        )

for name, (expected_dep, expected_path) in required_source_targets.items():
    if name not in parsed_source_targets:
        errors.append(f"Missing .target source declaration for '{name}'")
        continue
    path, deps = parsed_source_targets[name]
    if path != expected_path:
        errors.append(f"Source target '{name}' path must be {expected_path!r}; found: {path!r}")
    if expected_dep not in deps:
        errors.append(f"Source target '{name}' must depend on {expected_dep!r}; found dependencies: {deps!r}")

if errors:
    for err in errors:
        print(f"ERROR: {err}", file=sys.stderr)
    sys.exit(1)

print("  [ok] Static Package.swift checks passed (4 products, 2 binary targets, 2 SwiftUI source targets, GitHub URLs, SHA-256 checksums).")
PY

if [[ -z "$SOURCES_DIR" ]]; then
  echo "ERROR: Missing SwiftUI Sources/ directory (checked ${MANIFEST_DIR}/Sources and ${ROOT_DIR}/kmp/platforms/apple/Sources)." >&2
  exit 1
fi

for target_dir in AbetaWatchSwiftUI AbetaSduiSwiftUI; do
  if [[ ! -d "${SOURCES_DIR}/${target_dir}" ]]; then
    echo "ERROR: Missing required SwiftPM source target directory: ${SOURCES_DIR}/${target_dir}" >&2
    exit 1
  fi
  if ! find "${SOURCES_DIR}/${target_dir}" -maxdepth 2 -name '*.swift' | grep -q .; then
    echo "ERROR: No .swift source files found under ${SOURCES_DIR}/${target_dir}" >&2
    exit 1
  fi
done
echo "  [ok] Verified SwiftUI source directories under ${SOURCES_DIR}."

if command -v swift >/dev/null 2>&1; then
  TMP_SPM_DIR="$(mktemp -d)"
  trap 'rm -rf "${TMP_SPM_DIR:-}"' EXIT

  cp "$PACKAGE_SWIFT" "${TMP_SPM_DIR}/Package.swift"
  cp -R "$SOURCES_DIR" "${TMP_SPM_DIR}/Sources"

  if ! (cd "$TMP_SPM_DIR" && swift package dump-package > "${TMP_SPM_DIR}/dump.json" 2> "${TMP_SPM_DIR}/dump.err"); then
    echo "ERROR: 'swift package dump-package' failed:" >&2
    cat "${TMP_SPM_DIR}/dump.err" >&2
    exit 1
  fi

  python3 - "${TMP_SPM_DIR}/dump.json" "$EXPECTED_TAG" "$TMP_SPM_DIR" <<'PY'
import json
import os
import re
import sys

dump_path = sys.argv[1]
expected_tag = sys.argv[2]
pkg_root = sys.argv[3]

with open(dump_path, "r", encoding="utf-8") as fh:
    data = json.load(fh)

errors = []
if not isinstance(data, dict):
    errors.append("swift package dump-package output is not a JSON object")
else:
    required_products = ("AbetaSduiKit", "AbetaWatchKit", "AbetaWatchSwiftUI", "AbetaSduiSwiftUI")
    required_binary = ("AbetaSduiKit", "AbetaWatchKit")
    required_source = {
        "AbetaWatchSwiftUI": ("AbetaWatchKit", "Sources/AbetaWatchSwiftUI"),
        "AbetaSduiSwiftUI": ("AbetaSduiKit", "Sources/AbetaSduiSwiftUI"),
    }
    products = {p.get("name"): p for p in data.get("products", []) if isinstance(p, dict)}
    targets = {t.get("name"): t for t in data.get("targets", []) if isinstance(t, dict)}
    platforms = {p.get("platformName"): p.get("version") for p in data.get("platforms", []) if isinstance(p, dict)}

    if "ios" not in platforms:
        errors.append("Manifest missing iOS platform declaration")
    if "watchos" not in platforms:
        errors.append("Manifest missing watchOS platform declaration")

    sha256_re = re.compile(r"^[0-9a-f]{64}$")

    for name in required_products:
        prod = products.get(name)
        if not prod:
            errors.append(f"dump-package missing product '{name}'")
        elif name not in prod.get("targets", []):
            errors.append(f"dump-package product '{name}' does not expose target '{name}'")

    for name in required_binary:
        tgt = targets.get(name)
        if not tgt:
            errors.append(f"dump-package missing binary target '{name}'")
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

    for name, (expected_dep, expected_path) in required_source.items():
        tgt = targets.get(name)
        if not tgt:
            errors.append(f"dump-package missing source target '{name}'")
            continue
        if tgt.get("type") != "regular":
            errors.append(f"dump-package target '{name}' has type {tgt.get('type')!r}; expected 'regular'")
        actual_path = tgt.get("path") or ""
        if actual_path != expected_path:
            errors.append(f"dump-package target '{name}' path is {actual_path!r}; expected {expected_path!r}")
        elif not os.path.isdir(os.path.join(pkg_root, actual_path)):
            errors.append(f"dump-package target '{name}' path does not exist on disk: {actual_path!r}")

        dep_names = set()
        for dep in tgt.get("dependencies", []):
            if isinstance(dep, dict):
                for key in ("byName", "target", "product"):
                    val = dep.get(key)
                    if isinstance(val, list) and val and isinstance(val[0], str):
                        dep_names.add(val[0])
        if expected_dep not in dep_names:
            errors.append(
                f"dump-package source target '{name}' missing dependency '{expected_dep}'; found {sorted(dep_names)}"
            )

if errors:
    for err in errors:
        print(f"ERROR: {err}", file=sys.stderr)
    sys.exit(1)

print("  [ok] 'swift package dump-package' JSON parsed cleanly with all 4 products and binary/source targets.")
PY
else
  echo "  [skip] 'swift' CLI not installed on this host; skipped 'swift package dump-package' dynamic check."
fi

resolve_framework_dir() {
  local raw_dir="$1"
  local fw_name="$2"
  local slice_glob="$3"

  if [[ ! -d "$raw_dir" ]]; then
    echo "ERROR: Framework directory does not exist: ${raw_dir}" >&2
    return 1
  fi
  if [[ -d "${raw_dir}/${fw_name}.framework" ]]; then
    printf '%s\n' "$raw_dir"
    return 0
  fi

  local slice_dir
  slice_dir="$(find "$raw_dir" -maxdepth 2 -type d -name "${slice_glob}" | while IFS= read -r d; do
    if [[ -d "${d}/${fw_name}.framework" ]]; then
      printf '%s\n' "$d"
      break
    fi
  done)"
  if [[ -n "$slice_dir" ]]; then
    printf '%s\n' "$slice_dir"
    return 0
  fi

  echo "ERROR: Could not find ${fw_name}.framework inside ${raw_dir} (or ${slice_glob} slice)." >&2
  return 1
}

typecheck_swift_files() {
  local sdk="$1"
  local target="$2"
  local fw_dir="$3"
  shift 3

  local swift_ver src_file
  for swift_ver in 5 6; do
    for src_file in "$@"; do
      if [[ ! -f "$src_file" ]]; then
        echo "ERROR: Swift source file missing for typecheck: ${src_file}" >&2
        exit 1
      fi
      echo "  [typecheck] sdk=${sdk} target=${target} swift=${swift_ver} file=$(basename "$src_file")"
      xcrun --sdk "$sdk" swiftc \
        -target "$target" \
        -swift-version "$swift_ver" \
        -F "$fw_dir" \
        -typecheck "$src_file"
    done
  done
}

if [[ -n "$SDUI_FRAMEWORK_DIR" || -n "$WATCH_FRAMEWORK_DIR" ]]; then
  if [[ "$(uname -s)" != "Darwin" ]] || ! command -v xcrun >/dev/null 2>&1; then
    echo "  [skip] macOS/xcrun unavailable on this host; skipped swiftc -typecheck verification."
  else
    APPLE_HOSTS_SWIFT="${ROOT_DIR}/kmp/platforms/apple/AbetaAppleHosts.swift"

    if [[ -n "$SDUI_FRAMEWORK_DIR" ]]; then
      RESOLVED_SDUI_FW="$(resolve_framework_dir "$SDUI_FRAMEWORK_DIR" "AbetaSduiKit" "ios-*-simulator")"
      SDUI_SWIFTUI_FILE="${SOURCES_DIR}/AbetaSduiSwiftUI/AbetaSduiSwiftUI.swift"
      SDUI_FILES=("$SDUI_SWIFTUI_FILE")
      if [[ -f "$APPLE_HOSTS_SWIFT" ]]; then
        SDUI_FILES+=("$APPLE_HOSTS_SWIFT")
      fi
      typecheck_swift_files "iphonesimulator" "arm64-apple-ios15.0-simulator" "$RESOLVED_SDUI_FW" "${SDUI_FILES[@]}"
      echo "  [ok] AbetaSduiSwiftUI & Apple iOS host typechecked cleanly against AbetaSduiKit (Swift 5 & 6)."
    fi

    if [[ -n "$WATCH_FRAMEWORK_DIR" ]]; then
      RESOLVED_WATCH_FW="$(resolve_framework_dir "$WATCH_FRAMEWORK_DIR" "AbetaWatchKit" "watchos-*-simulator")"
      WATCH_SWIFTUI_FILE="${SOURCES_DIR}/AbetaWatchSwiftUI/AbetaWatchSwiftUI.swift"
      WATCH_FILES=("$WATCH_SWIFTUI_FILE")
      if [[ -f "$APPLE_HOSTS_SWIFT" ]]; then
        WATCH_FILES+=("$APPLE_HOSTS_SWIFT")
      fi
      typecheck_swift_files "watchsimulator" "arm64-apple-watchos9.0-simulator" "$RESOLVED_WATCH_FW" "${WATCH_FILES[@]}"
      echo "  [ok] AbetaWatchSwiftUI & Apple watchOS host typechecked cleanly against AbetaWatchKit (Swift 5 & 6)."
    fi
  fi
fi

echo "PASSED: SwiftPM manifest verification succeeded (${PACKAGE_SWIFT})."
