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
        if any(
            seg in kt_file
            for seg in ("/commonTest/", "/jvmTest/", "/androidHostTest/", "/androidDeviceTest/", "/androidUnitTest/")
        ):
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

# Go pkg/sdui/cache pure-Go check (zero Gin or net/http imports)
cache_go_files = [
    f for f in glob.glob(os.path.join(root, "bff/pkg/sdui/cache/**/*.go"), recursive=True)
    if not f.endswith("_test.go")
]
if not cache_go_files:
    violations.append("[GoCleanArch] Expected pure-Go source files under bff/pkg/sdui/cache/")
for go_file in cache_go_files:
    with open(go_file, "r", encoding="utf-8") as fh:
        text = fh.read()
    imported_pkgs = set(re.findall(r'"([^"]+)"', "\n".join(
        re.findall(r'import\s*\((.*?)\)', text, re.S) + re.findall(r'import\s+(?:[A-Za-z0-9_.]+\s+)?("[^"]+")', text)
    )))
    for imp in sorted(imported_pkgs):
        if imp == "net/http" or imp.startswith("net/http/") or imp == "github.com/gin-gonic/gin" or imp.startswith("github.com/gin-gonic/gin/"):
            rel = os.path.relpath(go_file, root)
            violations.append(f"[GoPureCache] {rel} imports forbidden HTTP/Gin package `{imp}`")

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
    mod_gradle = read_text(f"kmp/{mod}/build.gradle.kts")
    if "androidKotlinMultiplatformLibrary" not in mod_gradle or not re.search(r"\bandroid\s*\{", mod_gradle):
        violations.append(
            f"[AndroidTarget] Publishable module `:{mod}` missing `androidKotlinMultiplatformLibrary` plugin or `android {{ ... }}` target"
        )
    jvm_api_file = os.path.join(root, f"kmp/{mod}/api/jvm/{mod}.api")
    flat_api_file = os.path.join(root, f"kmp/{mod}/api/{mod}.api")
    if os.path.isfile(jvm_api_file):
        if os.path.isfile(flat_api_file):
            violations.append(
                f"[ABI] Stale duplicate flat ABI descriptor kmp/{mod}/api/{mod}.api exists alongside kmp/{mod}/api/jvm/{mod}.api; remove the flat file."
            )
    elif not os.path.isfile(flat_api_file):
        violations.append(
            f"[ABI] Missing Binary Compatibility Validator descriptor: kmp/{mod}/api/jvm/{mod}.api (or legacy kmp/{mod}/api/{mod}.api)"
        )

# Ensure kmp-bom constrains the exact set of 11 publishable libraries + dependencies catalog
catalog_kmp_aliases = {
    alias: artifact
    for alias, artifact in re.findall(
        r'^kmp-([A-Za-z0-9_]+)\s*=\s*\{\s*group\s*=\s*"io\.github\.abeta-dev"\s*,\s*name\s*=\s*"([^"]+)"',
        internal_toml,
        re.M,
    )
}
bom_entries = re.findall(r"api\(internal\.kmp\.([A-Za-z0-9_]+)\)", bom_text)
if len(bom_entries) != len(set(bom_entries)):
    dupes = sorted({e for e in bom_entries if bom_entries.count(e) > 1})
    violations.append(f"[BOM] kmp/kmp-bom/build.gradle.kts declares duplicate constraints: {dupes}")

unknown_bom_aliases = sorted(set(bom_entries) - set(catalog_kmp_aliases))
if unknown_bom_aliases:
    violations.append(
        f"[BOM] kmp/kmp-bom/build.gradle.kts references aliases missing from internal.versions.toml: {unknown_bom_aliases}"
    )

bom_artifacts = {catalog_kmp_aliases[e] for e in bom_entries if e in catalog_kmp_aliases}
expected_bom_artifacts = set(PUBLISHABLE_LIBS) | {"dependencies"}
if bom_artifacts != expected_bom_artifacts:
    missing_bom = sorted(expected_bom_artifacts - bom_artifacts)
    extra_bom = sorted(bom_artifacts - expected_bom_artifacts)
    violations.append(
        f"[BOM] Exact module set mismatch in kmp/kmp-bom/build.gradle.kts! Missing={missing_bom}, Extra={extra_bom}"
    )

# ---------------------------------------------------------------------------
# 3. 1:1 KMP <-> Go BFF Wire Protocol Parity & Renderer Registration
# ---------------------------------------------------------------------------
def extract_kotlin_const_map(rel_path: str, object_name: str) -> dict[str, str]:
    text = read_text(rel_path)
    match = re.search(rf"object\s+{object_name}\s*\{{(.*?)\}}", text, re.S)
    if not match:
        violations.append(f"[WireParity] Could not locate `object {object_name}` in {rel_path}")
        return {}
    return dict(re.findall(r'const\s+val\s+([A-Z0-9_]+)\s*=\s*"([^"]+)"', match.group(1)))


def extract_kotlin_const_strings(rel_path: str, object_name: str) -> set[str]:
    return set(extract_kotlin_const_map(rel_path, object_name).values())


def extract_kotlin_enum_serial_names(rel_path: str, enum_name: str) -> set[str]:
    text = read_text(rel_path)
    match = re.search(rf"enum\s+class\s+{enum_name}\s*\{{(.*?)\}}", text, re.S)
    if not match:
        violations.append(f"[WireParity] Could not locate `enum class {enum_name}` in {rel_path}")
        return set()
    return set(re.findall(r'@SerialName\s*\(\s*"([^"]+)"\s*\)', match.group(1)))


def extract_go_const_strings(rel_path: str, prefix: str) -> set[str]:
    text = read_text(rel_path)
    return set(re.findall(rf"\b{prefix}[A-Za-z0-9_]+\s+(?:[A-Za-z0-9_]+\s+)?=\s+\"([^\"]+)\"", text))


kmp_actions = extract_kotlin_const_strings(
    "kmp/action-engine/src/commonMain/kotlin/dev/abeta/kmp/actions/model/ActionPayloads.kt",
    "ActionTypes",
)
go_actions = extract_go_const_strings("bff/pkg/sdui/actions/actions.go", "Action")
if kmp_actions != go_actions:
    violations.append(
        f"[WireParity] ActionTypes mismatch! KMP-only={sorted(kmp_actions - go_actions)}, Go-only={sorted(go_actions - kmp_actions)}"
    )

kmp_snippet_const_map = extract_kotlin_const_map(
    "kmp/sdui-contracts/src/commonMain/kotlin/dev/abeta/kmp/contracts/SnippetContracts.kt",
    "SnippetTypes",
)
kmp_snippets = set(kmp_snippet_const_map.values())
go_snippets = extract_go_const_strings("bff/pkg/sdui/snippets/snippets.go", "SnippetType")
if kmp_snippets != go_snippets:
    violations.append(
        f"[WireParity] SnippetTypes mismatch! KMP-only={sorted(kmp_snippets - go_snippets)}, Go-only={sorted(go_snippets - kmp_snippets)}"
    )

# MotionLoopEffect parity (8 values)
kmp_motion_loops = extract_kotlin_enum_serial_names(
    "kmp/sdui-contracts/src/commonMain/kotlin/dev/abeta/kmp/contracts/AtomEnums.kt",
    "MotionLoopEffect",
)
go_motion_loops = extract_go_const_strings("bff/pkg/sdui/atoms/atoms.go", "MotionLoop")
if not kmp_motion_loops or kmp_motion_loops != go_motion_loops:
    violations.append(
        f"[WireParity] MotionLoopEffect mismatch! KMP-only={sorted(kmp_motion_loops - go_motion_loops)}, Go-only={sorted(go_motion_loops - kmp_motion_loops)}"
    )

# SnippetDeltaOp parity (6 values)
kmp_delta_ops = extract_kotlin_enum_serial_names(
    "kmp/sdui-contracts/src/commonMain/kotlin/dev/abeta/kmp/contracts/StreamingAndManifestContracts.kt",
    "SnippetDeltaOp",
)
go_delta_ops = extract_go_const_strings("bff/pkg/sdui/snippets/streaming_and_manifest.go", "DeltaOp")
if not kmp_delta_ops or kmp_delta_ops != go_delta_ops:
    violations.append(
        f"[WireParity] SnippetDeltaOp mismatch! KMP-only={sorted(kmp_delta_ops - go_delta_ops)}, Go-only={sorted(go_delta_ops - kmp_delta_ops)}"
    )

# ComposedNodeKind parity (36 values)
kmp_composed_kinds = extract_kotlin_enum_serial_names(
    "kmp/sdui-contracts/src/commonMain/kotlin/dev/abeta/kmp/contracts/NudgeAndComposedContracts.kt",
    "ComposedNodeKind",
)
go_composed_kinds = extract_go_const_strings(
    "bff/pkg/sdui/snippets/nudge_and_composed_snippets.go",
    "ComposedNode",
)
if not kmp_composed_kinds or kmp_composed_kinds != go_composed_kinds:
    violations.append(
        f"[WireParity] ComposedNodeKind mismatch! KMP-only={sorted(kmp_composed_kinds - go_composed_kinds)}, Go-only={sorted(go_composed_kinds - kmp_composed_kinds)}"
    )

# BuiltInSnippetModule registers every single SnippetTypes constant
renderer_class_to_type: dict[str, str] = {}
snippet_src_dir = os.path.join(root, "kmp/sdui-layout/src/commonMain/kotlin/dev/abeta/kmp/layout/snippet")
for kt_file in glob.glob(os.path.join(snippet_src_dir, "*.kt")):
    text = open(kt_file, "r", encoding="utf-8").read()
    class_matches = list(re.finditer(r"\bclass\s+([A-Za-z0-9_]+)\b", text))
    for idx, cm in enumerate(class_matches):
        cls_name = cm.group(1)
        start = cm.end()
        end = class_matches[idx + 1].start() if idx + 1 < len(class_matches) else len(text)
        body = text[start:end]
        const_m = re.search(r"override\s+val\s+snippetType\s*:\s*String\s*=\s*SnippetTypes\.([A-Z0-9_]+)", body)
        if const_m:
            const_name = const_m.group(1)
            if const_name in kmp_snippet_const_map:
                renderer_class_to_type[cls_name] = kmp_snippet_const_map[const_name]
        else:
            lit_m = re.search(r'override\s+val\s+snippetType\s*:\s*String\s*=\s*"([^"]+)"', body)
            if lit_m:
                renderer_class_to_type[cls_name] = lit_m.group(1)

builtin_module_text = read_text(
    "kmp/sdui-layout/src/commonMain/kotlin/dev/abeta/kmp/layout/snippet/BuiltInSnippetModule.kt"
)
registered_classes = re.findall(r"register\s*\(\s*([A-Za-z0-9_]+)\s*\(", builtin_module_text)
registered_snippet_types: set[str] = set()
unresolved_renderers: list[str] = []
for cls_name in registered_classes:
    stype = renderer_class_to_type.get(cls_name)
    if stype is None:
        unresolved_renderers.append(cls_name)
    else:
        registered_snippet_types.add(stype)

if unresolved_renderers:
    violations.append(
        f"[SnippetRegistry] BuiltInSnippetModule registers renderers with unresolved snippetType: {sorted(unresolved_renderers)}"
    )
if registered_snippet_types != kmp_snippets:
    missing_reg = sorted(kmp_snippets - registered_snippet_types)
    extra_reg = sorted(registered_snippet_types - kmp_snippets)
    violations.append(
        f"[SnippetRegistry] BuiltInSnippetModule registration mismatch vs SnippetTypes! Missing={missing_reg}, Extra={extra_reg}"
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
# 5. Mandatory Grounding Docs & CodeGraph Freshness Gate
# ---------------------------------------------------------------------------
GROUNDING_REQUIRED_FILES = [
    "docs/architecture.md",
    "docs/flows/README.md",
    "docs/patterns/README.md",
    "docs/codegraph/INDEX.md",
    "docs/codegraph/symbols.json",
    "scripts/generate_codegraph.py",
]

missing_grounding_files = []
for rel_file in GROUNDING_REQUIRED_FILES:
    if not os.path.isfile(os.path.join(root, rel_file)):
        missing_grounding_files.append(rel_file)
        violations.append(f"[Grounding] Missing required grounding/CodeGraph artifact: {rel_file}")

codegraph_status = "skipped (missing required artifacts)"
if not missing_grounding_files:
    cg_proc = subprocess.run(
        [sys.executable, os.path.join(root, "scripts/generate_codegraph.py"), "--check"],
        capture_output=True,
        text=True,
        check=False,
    )
    if cg_proc.returncode != 0:
        cg_lines = [ln.strip() for ln in (cg_proc.stderr or cg_proc.stdout or "").splitlines() if ln.strip()]
        cg_summary = cg_lines[0] if cg_lines else f"exit code {cg_proc.returncode}"
        violations.append(
            f"[CodeGraph] docs/codegraph/ is out of sync ({cg_summary}). Run `./scripts/generate_codegraph.py` to refresh."
        )
        codegraph_status = f"stale (exit {cg_proc.returncode})"
    else:
        codegraph_status = "fresh (--check passed)"

# ---------------------------------------------------------------------------
# 6. SOLID / KISS File-Size Hygiene Check (production source files)
# ---------------------------------------------------------------------------
MAX_PROD_LOC = 750
for pat in ("kmp/**/src/**/*.kt", "bff/**/*.go"):
    for path in glob.glob(os.path.join(root, pat), recursive=True):
        if any(
            seg in path
            for seg in (
                "/commonTest/",
                "/jvmTest/",
                "/androidHostTest/",
                "/androidDeviceTest/",
                "/androidUnitTest/",
                "/src/test/",
                "_test.go",
                ".build.nosync",
                "/build/",
            )
        ):
            continue
        with open(path, "r", encoding="utf-8", errors="ignore") as fh:
            loc = sum(1 for _ in fh)
        if loc > MAX_PROD_LOC:
            rel = os.path.relpath(path, root)
            warnings.append(f"[SOLID/KISS] {rel} has {loc} LOC (> {MAX_PROD_LOC} recommended SRP ceiling)")

# ---------------------------------------------------------------------------
# 7. Optional PR Diff -> Module Owner Routing & Review Report
# ---------------------------------------------------------------------------
report_lines = [
    "## Abeta KMP Kit — Module Code Owner & Architecture Gate Report",
    "",
    f"- **Publishable KMP Modules Verified**: {len(PUBLISHABLE_LIBS)} (`io.github.abeta-dev:*`, exact BOM set={len(bom_artifacts)})",
    f"- **Wire Parity (`ActionTypes`)**: {len(kmp_actions)} KMP / {len(go_actions)} Go BFF",
    f"- **Wire Parity (`SnippetTypes` / `BuiltInSnippetModule`)**: {len(kmp_snippets)} KMP / {len(go_snippets)} Go BFF / {len(registered_snippet_types)} registered",
    f"- **Wire Parity (`MotionLoopEffect`)**: {len(kmp_motion_loops)} KMP / {len(go_motion_loops)} Go BFF",
    f"- **Wire Parity (`SnippetDeltaOp`)**: {len(kmp_delta_ops)} KMP / {len(go_delta_ops)} Go BFF",
    f"- **Wire Parity (`ComposedNodeKind`)**: {len(kmp_composed_kinds)} KMP / {len(go_composed_kinds)} Go BFF",
    f"- **Module Code Owner Agents Verified**: {len(seen_owners)} (`agents/owners/*` + `.github/CODEOWNERS`)",
    f"- **Mandatory Grounding & CodeGraph Gate**: {len(GROUNDING_REQUIRED_FILES) - len(missing_grounding_files)}/{len(GROUNDING_REQUIRED_FILES)} files present, CodeGraph {codegraph_status}",
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
    report_lines.append("✅ **PASSED** — Dependency Rule, BOM/Catalog/ABI parity, 1:1 KMP/Go wire parity, Code Owner coverage, and Grounding/CodeGraph freshness all succeeded.")

report_md = "\n".join(report_lines) + "\n"
if report_out:
    with open(report_out, "w", encoding="utf-8") as fh:
        fh.write(report_md)

print(report_md)

if violations:
    sys.exit(1)
PY
