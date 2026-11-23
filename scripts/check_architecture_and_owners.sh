#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PR_DIFF_BASE=""
REPORT_OUT=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --pr-diff)
      PR_DIFF_BASE="${2:-}"
      shift 2
      ;;
    --report-out)
      REPORT_OUT="${2:-}"
      shift 2
      ;;
    *)
      echo "Usage: $0 [--pr-diff <git-base-ref>] [--report-out <markdown-path>]" >&2
      exit 2
      ;;
  esac
done

python3 - "$ROOT_DIR" "$PR_DIFF_BASE" "$REPORT_OUT" <<'PY'
import glob
import os
import re
import subprocess
import sys

root = sys.argv[1]
pr_diff_base = sys.argv[2]
report_out = sys.argv[3]

violations = []
warnings = []


def read_text(rel_path: str) -> str:
    full = os.path.join(root, rel_path)
    with open(full, "r", encoding="utf-8") as fh:
        return fh.read()


# ---------------------------------------------------------------------------
# 1. Uncle Bob Dependency Rule (Gradle project deps + source import guards)
# ---------------------------------------------------------------------------
ALLOWED_PROJECT_DEPS = {
    "core": set(),
    "state": {"core"},
    "analytics": {"core"},
    "database": {"core"},
    "design-tokens": set(),
    "sdui-contracts": set(),
    "ui-atoms": {"designTokens", "sduiContracts"},
    "action-engine": {"core", "sduiContracts"},
    "network": {"core"},
    "sdui-layout": {
        "core", "state", "analytics", "database",
        "designTokens", "sduiContracts", "uiAtoms",
        "actionEngine", "network",
    },
    "watch-companion": {
        "core", "state", "analytics", "database",
        "sduiContracts", "actionEngine", "network",
    },
    "storybook": {
        "core", "state", "analytics", "database",
        "designTokens", "sduiContracts", "uiAtoms",
        "actionEngine", "network", "sduiLayout", "kmpBom",
    },
    "sample-app": {
        "core", "state", "analytics", "database",
        "designTokens", "sduiContracts", "uiAtoms",
        "actionEngine", "network", "sduiLayout", "kmpBom",
    },
}

PUBLISHABLE_LIBS = [
    "core",
    "state",
    "analytics",
    "database",
    "design-tokens",
    "sdui-contracts",
    "ui-atoms",
    "action-engine",
    "network",
    "sdui-layout",
    "watch-companion",
]

for mod, allowed in ALLOWED_PROJECT_DEPS.items():
    gradle_rel = f"kmp/{mod}/build.gradle.kts"
    content = read_text(gradle_rel)
    found_deps = set(re.findall(r"projects\.([A-Za-z0-9_]+)", content))
    disallowed = found_deps - allowed
    if disallowed:
        violations.append(
            f"[DependencyRule] {gradle_rel} has disallowed internal project deps: {sorted(disallowed)}"
        )

    # Zero hardcoded external dependency versions in module build.gradle.kts
    hardcoded = re.findall(
        r'(?:implementation|api|testImplementation)\s*\(\s*"[^":\s]+:[^":\s]+:[0-9][^"\s]*"\s*\)',
        content,
    )
    if hardcoded:
        violations.append(
            f"[BOM/Catalog] {gradle_rel} contains hardcoded dependency versions: {hardcoded}"
        )

# Layer 0 / Layer 1 / headless Layer 3 import isolation checks
LAYER1_FORBIDDEN_IMPORTS = {
    "database": (
        "dev.abeta.kmp.state.",
        "dev.abeta.kmp.analytics.",
        "dev.abeta.kmp.tokens.",
        "dev.abeta.kmp.contracts.",
        "dev.abeta.kmp.atoms.",
        "dev.abeta.kmp.actions.",
        "dev.abeta.kmp.network.",
        "dev.abeta.kmp.layout.",
        "androidx.compose.",
        "io.ktor.",
    ),
    "design-tokens": (
        "dev.abeta.kmp.core.",
        "dev.abeta.kmp.state.",
        "dev.abeta.kmp.analytics.",
        "dev.abeta.kmp.database.",
        "dev.abeta.kmp.contracts.",
        "dev.abeta.kmp.atoms.",
        "dev.abeta.kmp.actions.",
        "dev.abeta.kmp.network.",
        "dev.abeta.kmp.layout.",
        "io.ktor.",
    ),
    "sdui-contracts": (
        "dev.abeta.kmp.core.",
        "dev.abeta.kmp.database.",
        "dev.abeta.kmp.tokens.",
        "dev.abeta.kmp.atoms.",
        "dev.abeta.kmp.actions.",
        "dev.abeta.kmp.network.",
        "dev.abeta.kmp.layout.",
        "androidx.compose.",
        "io.ktor.",
    ),
    "ui-atoms": (
        "dev.abeta.kmp.core.",
        "dev.abeta.kmp.state.",
        "dev.abeta.kmp.analytics.",
        "dev.abeta.kmp.database.",
        "dev.abeta.kmp.actions.",
        "dev.abeta.kmp.network.",
        "dev.abeta.kmp.layout.",
        "io.ktor.",
    ),
    "watch-companion": (
        "androidx.compose.",
        "dev.abeta.kmp.tokens.",
        "dev.abeta.kmp.atoms.",
        "dev.abeta.kmp.layout.",
    ),
}

for mod, forbidden_prefixes in LAYER1_FORBIDDEN_IMPORTS.items():
    for kt_file in glob.glob(os.path.join(root, f"kmp/{mod}/src/**/*.kt"), recursive=True):
        if "/commonTest/" in kt_file or "/jvmTest/" in kt_file:
            continue
        with open(kt_file, "r", encoding="utf-8") as fh:
            for line_no, line in enumerate(fh, 1):
                stripped = line.strip()
                if stripped.startswith("import "):
                    imp = stripped[len("import "):].strip()
                    for prefix in forbidden_prefixes:
                        if imp.startswith(prefix):
                            rel = os.path.relpath(kt_file, root)
                            violations.append(
                                f"[ImportIsolation] {rel}:{line_no} imports forbidden `{imp}`"
                            )

# Go BFF domain purity check (mirrors internal/domain/arch_test.go)
for go_file in glob.glob(os.path.join(root, "bff/internal/domain/**/*.go"), recursive=True):
    if go_file.endswith("_test.go"):
        continue
    with open(go_file, "r", encoding="utf-8") as fh:
        text = fh.read()
    for bad in ('"github.com/gin-gonic/gin"', '"net/http"', "bff/internal/infra", "bff/internal/handler"):
        if bad in text:
            rel = os.path.relpath(go_file, root)
            violations.append(f"[GoCleanArch] {rel} imports forbidden package {bad}")

# ---------------------------------------------------------------------------
# 2. Gradle BOM, Version Catalog, settings.gradle.kts & ABI .api Registration
# ---------------------------------------------------------------------------
bom_text = read_text("kmp/kmp-bom/build.gradle.kts")
root_gradle = read_text("kmp/build.gradle.kts")
settings_gradle = read_text("kmp/settings.gradle.kts")
internal_toml = read_text("kmp/dependencyManagement/gradle/internal.versions.toml")

for mod in PUBLISHABLE_LIBS:
    if f'"{mod}"' not in root_gradle:
        violations.append(f"[BOM/Publish] Module `{mod}` missing from kmp/build.gradle.kts publishableModules")
    if f'":{mod}"' not in settings_gradle:
        violations.append(f"[BOM/Settings] Module `:{mod}` missing from kmp/settings.gradle.kts")
    if f'group = "io.github.abeta-dev", name = "{mod}"' not in internal_toml:
        violations.append(f"[Catalog] Coordinate `io.github.abeta-dev:{mod}` missing from internal.versions.toml")
    api_file = os.path.join(root, f"kmp/{mod}/api/{mod}.api")
    if not os.path.isfile(api_file):
        violations.append(f"[ABI] Missing Binary Compatibility Validator descriptor: kmp/{mod}/api/{mod}.api")

# Ensure all 11 publishable libraries are referenced in kmp-bom
bom_entries = re.findall(r"api\(internal\.kmp\.([A-Za-z0-9_]+)\)", bom_text)
if len(bom_entries) < len(PUBLISHABLE_LIBS):
    violations.append(
        f"[BOM] kmp/kmp-bom/build.gradle.kts declares {len(bom_entries)} kmp modules; expected >= {len(PUBLISHABLE_LIBS)}"
    )

# ---------------------------------------------------------------------------
# 3. 1:1 KMP <-> Go BFF Wire Protocol Parity (ActionTypes & SnippetTypes)
# ---------------------------------------------------------------------------
def extract_kotlin_const_strings(rel_path: str, object_name: str) -> set[str]:
    text = read_text(rel_path)
    match = re.search(rf"object\s+{object_name}\s*\{{(.*?)\}}", text, re.S)
    if not match:
        violations.append(f"[WireParity] Could not locate `object {object_name}` in {rel_path}")
        return set()
    return set(re.findall(r'const\s+val\s+[A-Z0-9_]+\s*=\s*"([^"]+)"', match.group(1)))


def extract_go_const_strings(rel_path: str, prefix: str) -> set[str]:
    text = read_text(rel_path)
    return set(re.findall(rf"{prefix}[A-Za-z0-9_]+\s+(?:[A-Za-z0-9_]+\s+)?=\s+\"([^\"]+)\"", text))


kmp_actions = extract_kotlin_const_strings(
    "kmp/action-engine/src/commonMain/kotlin/dev/abeta/kmp/actions/model/ActionPayloads.kt",
    "ActionTypes",
)
go_actions = extract_go_const_strings("bff/pkg/sdui/actions/actions.go", "Action")
if kmp_actions != go_actions:
    violations.append(
        f"[WireParity] ActionTypes mismatch! KMP-only={sorted(kmp_actions - go_actions)}, Go-only={sorted(go_actions - kmp_actions)}"
    )

kmp_snippets = extract_kotlin_const_strings(
    "kmp/sdui-contracts/src/commonMain/kotlin/dev/abeta/kmp/contracts/SnippetContracts.kt",
    "SnippetTypes",
)
go_snippets = extract_go_const_strings("bff/pkg/sdui/snippets/snippets.go", "SnippetType")
if kmp_snippets != go_snippets:
    violations.append(
        f"[WireParity] SnippetTypes mismatch! KMP-only={sorted(kmp_snippets - go_snippets)}, Go-only={sorted(go_snippets - kmp_snippets)}"
    )

# ---------------------------------------------------------------------------
# 4. Module Code Owner Agents & .github/CODEOWNERS Coverage
# ---------------------------------------------------------------------------
OWNER_MAPPING = [
    ("kmp/core/", "agents/owners/core-owner.md", "core-owner"),
    ("kmp/state/", "agents/owners/state-owner.md", "state-owner"),
    ("kmp/analytics/", "agents/owners/analytics-owner.md", "analytics-owner"),
    ("kmp/database/", "agents/owners/database-owner.md", "database-owner"),
    ("kmp/design-tokens/", "agents/owners/design-tokens-owner.md", "design-tokens-owner"),
    ("kmp/sdui-contracts/", "agents/owners/sdui-contracts-owner.md", "sdui-contracts-owner"),
    ("kmp/ui-atoms/", "agents/owners/ui-atoms-owner.md", "ui-atoms-owner"),
    ("kmp/action-engine/", "agents/owners/action-engine-owner.md", "action-engine-owner"),
    ("kmp/network/", "agents/owners/network-owner.md", "network-owner"),
    ("kmp/sdui-layout/", "agents/owners/sdui-layout-owner.md", "sdui-layout-owner"),
    ("kmp/watch-companion/", "agents/owners/watch-companion-owner.md", "watch-companion-owner"),
    ("kmp/storybook/", "agents/owners/storybook-owner.md", "storybook-owner"),
    ("kmp/sample-app/", "agents/owners/sample-app-owner.md", "sample-app-owner"),
    ("kmp/platforms/", "agents/owners/platforms-owner.md", "platforms-owner"),
    ("kmp/kmp-bom/", "agents/owners/bom-catalog-owner.md", "bom-catalog-owner"),
    ("kmp/dependencyManagement/", "agents/owners/bom-catalog-owner.md", "bom-catalog-owner"),
    ("bff/", "agents/owners/bff-owner.md", "bff-owner"),
    (".github/", "agents/owners/cicd-platform-owner.md", "cicd-platform-owner"),
    ("scripts/", "agents/owners/cicd-platform-owner.md", "cicd-platform-owner"),
]

codeowners_path = os.path.join(root, ".github/CODEOWNERS")
if not os.path.isfile(codeowners_path):
    violations.append("[CodeOwners] Missing .github/CODEOWNERS")
    codeowners_text = ""
else:
    codeowners_text = read_text(".github/CODEOWNERS")

seen_owners = set()
for prefix, agent_rel, agent_name in OWNER_MAPPING:
    if f"/{prefix}" not in codeowners_text:
        violations.append(f"[CodeOwners] Path `/{prefix}` missing from .github/CODEOWNERS")
    if agent_rel not in seen_owners:
        seen_owners.add(agent_rel)
        full_agent = os.path.join(root, agent_rel)
        if not os.path.isfile(full_agent):
            violations.append(f"[CodeOwners] Missing module owner agent: {agent_rel}")
        else:
            agent_text = read_text(agent_rel)
            for required_key in (f"name: {agent_name}", "verification_command:", "## Non-Negotiable Architecture & Review Gates"):
                if required_key not in agent_text:
                    violations.append(f"[CodeOwners] {agent_rel} missing required field `{required_key}`")

# ---------------------------------------------------------------------------
# 5. SOLID / KISS File-Size Hygiene Check (production source files)
# ---------------------------------------------------------------------------
MAX_PROD_LOC = 750
for pat in ("kmp/**/src/**/*.kt", "bff/**/*.go"):
    for path in glob.glob(os.path.join(root, pat), recursive=True):
        if any(seg in path for seg in ("/commonTest/", "/jvmTest/", "/src/test/", "_test.go", ".build.nosync", "/build/")):
            continue
        with open(path, "r", encoding="utf-8", errors="ignore") as fh:
            loc = sum(1 for _ in fh)
        if loc > MAX_PROD_LOC:
            rel = os.path.relpath(path, root)
            warnings.append(f"[SOLID/KISS] {rel} has {loc} LOC (> {MAX_PROD_LOC} recommended SRP ceiling)")

# ---------------------------------------------------------------------------
# 6. Optional PR Diff -> Module Owner Routing & Review Report
# ---------------------------------------------------------------------------
report_lines = [
    "## Abeta KMP Kit — Module Code Owner & Architecture Gate Report",
    "",
    f"- **Publishable KMP Modules Verified**: {len(PUBLISHABLE_LIBS)} (`io.github.abeta-dev:*`)",
    f"- **Wire Parity (`ActionTypes`)**: {len(kmp_actions)} KMP / {len(go_actions)} Go BFF",
    f"- **Wire Parity (`SnippetTypes`)**: {len(kmp_snippets)} KMP / {len(go_snippets)} Go BFF",
    f"- **Module Code Owner Agents Verified**: {len(seen_owners)} (`agents/owners/*` + `.github/CODEOWNERS`)",
    f"- **Hard Violations**: {len(violations)}",
    f"- **SOLID/KISS Advisories**: {len(warnings)}",
    "",
]

if pr_diff_base:
    diff_proc = subprocess.run(
        ["git", "-C", root, "diff", "--name-only", f"{pr_diff_base}...HEAD"],
        capture_output=True,
        text=True,
        check=False,
    )
    changed_files = [ln.strip() for ln in diff_proc.stdout.splitlines() if ln.strip()]
    matched_agents: dict[str, list[str]] = {}
    for ch in changed_files:
        matched = False
        for prefix, agent_rel, _ in OWNER_MAPPING:
            if ch.startswith(prefix):
                matched_agents.setdefault(agent_rel, []).append(ch)
                matched = True
                break
        if not matched and ch in ("kmp/settings.gradle.kts", "kmp/build.gradle.kts", "kmp/gradle.properties", "kmp/detekt.yml"):
            matched_agents.setdefault("agents/owners/bom-catalog-owner.md", []).append(ch)
        elif not matched:
            matched_agents.setdefault("agents/owners/cicd-platform-owner.md", []).append(ch)

    report_lines.append(f"### Touched Modules & Assigned Code Owner Agents (`{pr_diff_base}...HEAD`, {len(changed_files)} file(s))")
    report_lines.append("")
    if not matched_agents:
        report_lines.append("- No changed files detected against base ref.")
    else:
        report_lines.append("| Code Owner Agent | Changed Files | Module Verification Command |")
        report_lines.append("| :--- | :--- | :--- |")
        for agent_rel, files in sorted(matched_agents.items()):
            agent_text = read_text(agent_rel) if os.path.isfile(os.path.join(root, agent_rel)) else ""
            cmd_match = re.search(r'verification_command:\s*"([^"]+)"', agent_text)
            cmd = cmd_match.group(1) if cmd_match else "./scripts/verify_all.sh"
            sample = ", ".join(f"`{f}`" for f in files[:3])
            if len(files) > 3:
                sample += f" (+{len(files) - 3} more)"
            report_lines.append(f"| `{agent_rel}` | {sample} | `{cmd}` |")
    report_lines.append("")

if violations:
    report_lines.append("### Blocking Violations")
    for v in violations:
        report_lines.append(f"- ❌ {v}")
    report_lines.append("")

if warnings:
    report_lines.append("### SOLID / KISS Advisories")
    for w in warnings:
        report_lines.append(f"- ⚠️ {w}")
    report_lines.append("")

if not violations:
    report_lines.append("### Verdict")
    report_lines.append("✅ **PASSED** — Dependency Rule, BOM/Catalog/ABI parity, 1:1 KMP/Go wire parity, and Code Owner coverage all succeeded.")

report_md = "\n".join(report_lines) + "\n"
if report_out:
    with open(report_out, "w", encoding="utf-8") as fh:
        fh.write(report_md)

print(report_md)

if violations:
    sys.exit(1)
PY
