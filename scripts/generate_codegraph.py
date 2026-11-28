#!/usr/bin/env python3
"""Deterministic, zero-dependency CodeGraph symbol & dependency indexer for abeta-dev/kmp.

Powers Step 5 of the repository's Mandatory Grounding Order:
  AGENTS.md -> architecture docs -> flow docs -> pattern docs ->
  CodeGraph symbols (docs/codegraph/) -> source reads only for verification.

Usage:
  ./scripts/generate_codegraph.py                  # Regenerate docs/codegraph/{symbols.json,INDEX.md}
  ./scripts/generate_codegraph.py --check          # Verify committed artifacts match byte-for-byte
  ./scripts/generate_codegraph.py --query <substr> # Fast symbol lookup from docs/codegraph/symbols.json
"""

from __future__ import annotations

import argparse
from collections import Counter
import json
import os
from pathlib import Path
import re
import sys
from typing import Any


REPO_ROOT = Path(__file__).resolve().parent.parent
CODEGRAPH_DIR = REPO_ROOT / "docs" / "codegraph"
SYMBOLS_JSON_PATH = CODEGRAPH_DIR / "symbols.json"
INDEX_MD_PATH = CODEGRAPH_DIR / "INDEX.md"

SCHEMA_VERSION = "1.0"
GO_MODULE_PREFIX = "github.com/abeta-dev/kmp/bff/"

EXCLUDED_DIRS = frozenset({
    ".git",
    ".gradle",
    ".kotlin",
    ".build.nosync",
    "build",
    "generated",
    "kotlin-js-store",
    "node_modules",
})

KMP_MODULE_LAYERS: dict[str, str] = {
    "core": "0",
    "state": "0",
    "analytics": "0",
    "database": "0",
    "design-tokens": "1",
    "sdui-contracts": "1",
    "ui-atoms": "1",
    "action-engine": "2",
    "network": "2",
    "sdui-layout": "3",
    "watch-companion": "3",
    "storybook": "3",
    "sample-app": "3",
    "kmp-bom": "bom",
    "dependencyManagement": "bom",
}

LAYER_ORDER: dict[str, int] = {
    "0": 0,
    "1": 1,
    "2": 2,
    "3": 3,
    "bom": 4,
    "unknown": 5,
}

GO_LAYER_ORDER: dict[str, int] = {
    "cmd": 0,
    "handler": 1,
    "usecase": 2,
    "domain": 3,
    "infra": 4,
    "pkg": 5,
}

KMP_TARGET_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    (
        "androidLibrary",
        re.compile(
            r"\bandroidLibrary\b|\bandroidKotlinMultiplatformLibrary\b|\bandroid\s*\{|\bandroidTarget\s*(?:\{|\()"
        ),
    ),
    ("jvm", re.compile(r"\bjvm\s*\(|libs\.plugins\.kotlinJvm|\bkotlin\s*\(\s*\"jvm\"\s*\)")),
    ("iosArm64", re.compile(r"\biosArm64\s*\(")),
    ("iosSimulatorArm64", re.compile(r"\biosSimulatorArm64\s*\(")),
    ("macosArm64", re.compile(r"\bmacosArm64\s*\(")),
    ("watchosArm64", re.compile(r"\bwatchosArm64\s*\(")),
    ("watchosSimulatorArm64", re.compile(r"\bwatchosSimulatorArm64\s*\(")),
    ("watchosDeviceArm64", re.compile(r"\bwatchosDeviceArm64\s*\(")),
    ("wasmJs", re.compile(r"\bwasmJs\s*(?:\{|\()")),
]

KOTLIN_VISIBILITY_MODS = frozenset({"public", "private", "internal", "protected"})
KOTLIN_DECL_MODS = frozenset({
    "abstract",
    "open",
    "final",
    "sealed",
    "enum",
    "annotation",
    "data",
    "inner",
    "value",
    "inline",
    "noinline",
    "crossinline",
    "reified",
    "expect",
    "actual",
    "suspend",
    "operator",
    "infix",
    "tailrec",
    "external",
    "const",
    "override",
    "lateinit",
    "vararg",
})
KOTLIN_USE_SITE_TARGETS = frozenset({
    "file",
    "field",
    "property",
    "get",
    "set",
    "receiver",
    "param",
    "setparam",
    "delegate",
})

SWIFT_MODIFIERS = frozenset({
    "public",
    "open",
    "internal",
    "fileprivate",
    "private",
    "final",
    "static",
    "nonisolated",
    "mutating",
    "nonmutating",
    "override",
    "convenience",
    "required",
    "indirect",
    "lazy",
    "weak",
    "unowned",
    "dynamic",
    "prefix",
    "postfix",
    "infix",
    "distributed",
    "consuming",
    "borrowing",
})

# Curated key architectural entry-point symbols rendered in INDEX.md with live file:line resolution.
# Tuple shape: (lang, symbol_name, preferred_scope_or_none)
KEY_ENTRYPOINTS: list[tuple[str, list[tuple[str, str, str | None]]]] = [
    (
        "Layer 0 — Core, State, Analytics & Database",
        [
            ("kotlin", "AppResult", "core"),
            ("kotlin", "AppError", "core"),
            ("kotlin", "PlatformCapabilities", "core"),
            ("kotlin", "SecureKeyValueStore", "core"),
            ("kotlin", "BiometricAuthenticator", "core"),
            ("kotlin", "DeepLinkParser", "core"),
            ("kotlin", "SessionStore", "state"),
            ("kotlin", "OfflineMutationQueue", "state"),
            ("kotlin", "AnalyticsTracker", "analytics"),
            ("kotlin", "AbetaDatabase", "database"),
            ("kotlin", "AbetaLocalDatabase", "database"),
        ],
    ),
    (
        "Layer 1 — Design Tokens, Wire Contracts & Stateless UI Atoms",
        [
            ("kotlin", "DesignTheme", "design-tokens"),
            ("kotlin", "ThemeTokens", "design-tokens"),
            ("kotlin", "AdaptiveLayoutMetrics", "design-tokens"),
            ("kotlin", "PageResponse", "sdui-contracts"),
            ("kotlin", "SnippetEnvelope", "sdui-contracts"),
            ("kotlin", "SnippetTypes", "sdui-contracts"),
            ("kotlin", "ComposedNodeKind", "sdui-contracts"),
            ("kotlin", "SnippetDeltaOp", "sdui-contracts"),
            ("kotlin", "ActionRef", "sdui-contracts"),
            ("kotlin", "MotionSpec", "sdui-contracts"),
            ("kotlin", "UiButton", "ui-atoms"),
            ("kotlin", "UiQrCode", "ui-atoms"),
            ("kotlin", "UiMediaPager", "ui-atoms"),
            ("kotlin", "UiCelebrationOverlay", "ui-atoms"),
        ],
    ),
    (
        "Layer 2 — Action Engine & Network Client",
        [
            ("kotlin", "ActionTypes", "action-engine"),
            ("kotlin", "ActionDecoderRegistry", "action-engine"),
            ("kotlin", "ActionRegistry", "action-engine"),
            ("kotlin", "DefaultActionDispatcher", "action-engine"),
            ("kotlin", "ActionOutcome", "action-engine"),
            ("kotlin", "NetworkConfig", "network"),
            ("kotlin", "HttpClientFactory", "network"),
            ("kotlin", "KtorHttpTransport", "network"),
            ("kotlin", "KtorSseTransport", "network"),
        ],
    ),
    (
        "Layer 3 — SDUI Layout, Watch Companion, Storybook & Sample App",
        [
            ("kotlin", "LayoutController", "sdui-layout"),
            ("kotlin", "SduiLayoutViewModel", "sdui-layout"),
            ("kotlin", "CachedScreenRepository", "sdui-layout"),
            ("kotlin", "BuiltInSnippetModule", "sdui-layout"),
            ("kotlin", "SnippetRegistry", "sdui-layout"),
            ("kotlin", "CustomSlotRegistry", "sdui-layout"),
            ("kotlin", "PlatformBridgeBundle", "sdui-layout"),
            ("kotlin", "SduiHostedRoot", "sdui-layout"),
            ("kotlin", "createSduiKoinModule", "sdui-layout"),
            ("kotlin", "WatchCompanionController", "watch-companion"),
            ("kotlin", "WatchScreenMapper", "watch-companion"),
            ("kotlin", "StorybookApp", "storybook"),
        ],
    ),
    (
        "Platform Reference Hosts & SwiftPM Targets",
        [
            ("kotlin", "AbetaAndroidHostActivity", "platforms/android"),
            ("kotlin", "AndroidPlatformBridgeFactory", "platforms/android"),
            ("kotlin", "AndroidDatabaseFactory", "platforms/android"),
            ("kotlin", "WearCompanionHostActivity", "platforms/wearos"),
            ("swift", "AbetaIosSduiScreenView", "AbetaSduiSwiftUI"),
            ("swift", "AbetaIosRootAppView", "AbetaSduiSwiftUI"),
            ("swift", "AbetaWatchViewModel", "AbetaWatchSwiftUI"),
            ("swift", "AbetaWatchSduiView", "AbetaWatchSwiftUI"),
        ],
    ),
    (
        "Go BFF — Clean Architecture & Public `pkg/sdui/*` SDK",
        [
            ("go", "NewRouter", "internal/handler"),
            ("go", "ScreenService", "internal/usecase"),
            ("go", "ActionService", "internal/usecase"),
            ("go", "ScreenRepository", "internal/domain"),
            ("go", "InMemoryScreenRepository", "internal/infra"),
            ("go", "DefaultActionExecutor", "internal/infra"),
            ("go", "UpstreamCachedScreenRepository", "pkg/sdui/cache"),
            ("go", "CORSMiddleware", "pkg/sdui/server"),
            ("go", "ETagMiddleware", "pkg/sdui/server"),
            ("go", "CompressionMiddleware", "pkg/sdui/server"),
            ("go", "JWTAuthMiddleware", "pkg/sdui/server"),
            ("go", "PageResponse", "pkg/sdui/snippets"),
            ("go", "ActionTrigger", "pkg/sdui/actions"),
        ],
    ),
]


def camel_to_kebab(name: str) -> str:
    """Convert Gradle camelCase project accessor (e.g. sduiContracts) to kebab-case."""
    return re.sub(r"([a-z0-9])([A-Z])", r"\1-\2", name).lower()


def rel_posix(path: Path) -> str:
    """Return strictly repo-relative POSIX path."""
    return path.relative_to(REPO_ROOT).as_posix()


def is_excluded_path(path: Path) -> bool:
    """Return True if path traverses any build/generated/VCS directory."""
    return any(part in EXCLUDED_DIRS for part in path.parts)


def strip_kotlin_comments_only(text: str) -> str:
    """Strip Kotlin // and nested /* */ comments while preserving string literals and newlines."""
    out: list[str] = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if ch == "/" and nxt == "/":
            i += 2
            while i < n and text[i] != "\n":
                out.append(" ")
                i += 1
            continue
        if ch == "/" and nxt == "*":
            depth = 1
            out.append("  ")
            i += 2
            while i < n and depth > 0:
                c = text[i]
                c2 = text[i + 1] if i + 1 < n else ""
                if c == "/" and c2 == "*":
                    depth += 1
                    out.append("  ")
                    i += 2
                elif c == "*" and c2 == "/":
                    depth -= 1
                    out.append("  ")
                    i += 2
                else:
                    out.append("\n" if c == "\n" else " ")
                    i += 1
            continue
        if ch == '"' and text[i : i + 3] == '"""':
            out.append('"""')
            i += 3
            while i < n:
                if text[i : i + 3] == '"""':
                    out.append('"""')
                    i += 3
                    while i < n and text[i] == '"':
                        out.append('"')
                        i += 1
                    break
                out.append(text[i])
                i += 1
            continue
        if ch == '"':
            out.append(ch)
            i += 1
            while i < n:
                c = text[i]
                out.append(c)
                i += 1
                if c == "\\" and i < n:
                    out.append(text[i])
                    i += 1
                elif c == '"':
                    break
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def strip_kotlin_comments_and_strings(text: str) -> str:
    """Replace Kotlin comments and string/char literals with spaces while preserving line numbers."""
    out: list[str] = []
    i = 0
    n = len(text)

    def skip_block_comment(pos: int) -> int:
        depth = 1
        out.append("  ")
        pos += 2
        while pos < n and depth > 0:
            c = text[pos]
            c2 = text[pos + 1] if pos + 1 < n else ""
            if c == "/" and c2 == "*":
                depth += 1
                out.append("  ")
                pos += 2
            elif c == "*" and c2 == "/":
                depth -= 1
                out.append("  ")
                pos += 2
            else:
                out.append("\n" if c == "\n" else " ")
                pos += 1
        return pos

    def skip_raw_string(pos: int) -> int:
        out.append("   ")
        pos += 3
        while pos < n:
            if text[pos : pos + 3] == '"""':
                out.append("   ")
                pos += 3
                while pos < n and text[pos] == '"':
                    out.append(" ")
                    pos += 1
                break
            out.append("\n" if text[pos] == "\n" else " ")
            pos += 1
        return pos

    def skip_quoted_string(pos: int) -> int:
        out.append(" ")
        pos += 1
        while pos < n:
            c = text[pos]
            if c == "\\":
                out.append(" ")
                pos += 1
                if pos < n:
                    out.append("\n" if text[pos] == "\n" else " ")
                    pos += 1
            elif c == "$" and pos + 1 < n and text[pos + 1] == "{":
                out.append("  ")
                pos += 2
                tpl_depth = 1
                while pos < n and tpl_depth > 0:
                    tc = text[pos]
                    tc2 = text[pos + 1] if pos + 1 < n else ""
                    if tc == "/" and tc2 == "/":
                        out.append("  ")
                        pos += 2
                        while pos < n and text[pos] != "\n":
                            out.append(" ")
                            pos += 1
                    elif tc == "/" and tc2 == "*":
                        pos = skip_block_comment(pos)
                    elif tc == '"' and text[pos : pos + 3] == '"""':
                        pos = skip_raw_string(pos)
                    elif tc == '"':
                        pos = skip_quoted_string(pos)
                    elif tc == "'":
                        out.append(" ")
                        pos += 1
                        while pos < n:
                            cc = text[pos]
                            out.append("\n" if cc == "\n" else " ")
                            pos += 1
                            if cc == "\\" and pos < n:
                                out.append("\n" if text[pos] == "\n" else " ")
                                pos += 1
                            elif cc == "'":
                                break
                    elif tc == "{":
                        tpl_depth += 1
                        out.append(" ")
                        pos += 1
                    elif tc == "}":
                        tpl_depth -= 1
                        out.append(" ")
                        pos += 1
                    else:
                        out.append("\n" if tc == "\n" else " ")
                        pos += 1
            elif c == '"':
                out.append(" ")
                pos += 1
                break
            else:
                out.append("\n" if c == "\n" else " ")
                pos += 1
        return pos

    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if ch == "/" and nxt == "/":
            out.append("  ")
            i += 2
            while i < n and text[i] != "\n":
                out.append(" ")
                i += 1
        elif ch == "/" and nxt == "*":
            i = skip_block_comment(i)
        elif ch == '"' and text[i : i + 3] == '"""':
            i = skip_raw_string(i)
        elif ch == '"':
            i = skip_quoted_string(i)
        elif ch == "'":
            out.append(" ")
            i += 1
            while i < n:
                c = text[i]
                out.append("\n" if c == "\n" else " ")
                i += 1
                if c == "\\" and i < n:
                    out.append("\n" if text[i] == "\n" else " ")
                    i += 1
                elif c == "'":
                    break
        else:
            out.append(ch)
            i += 1

    return "".join(out)


def strip_go_comments_and_strings(text: str, *, preserve_strings: bool = False) -> str:
    """Strip Go comments (and optionally strings/runes) while preserving line numbers."""
    out: list[str] = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if ch == "/" and nxt == "/":
            out.append("  ")
            i += 2
            while i < n and text[i] != "\n":
                out.append(" ")
                i += 1
        elif ch == "/" and nxt == "*":
            out.append("  ")
            i += 2
            while i < n:
                if text[i] == "*" and i + 1 < n and text[i + 1] == "/":
                    out.append("  ")
                    i += 2
                    break
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
        elif ch == "`":
            out.append(ch if preserve_strings else " ")
            i += 1
            while i < n:
                c = text[i]
                out.append(c if preserve_strings else ("\n" if c == "\n" else " "))
                i += 1
                if c == "`":
                    break
        elif ch == '"':
            out.append(ch if preserve_strings else " ")
            i += 1
            while i < n:
                c = text[i]
                out.append(c if preserve_strings else ("\n" if c == "\n" else " "))
                i += 1
                if c == "\\" and i < n:
                    c2 = text[i]
                    out.append(c2 if preserve_strings else ("\n" if c2 == "\n" else " "))
                    i += 1
                elif c == '"':
                    break
        elif ch == "'":
            out.append(ch if preserve_strings else " ")
            i += 1
            while i < n:
                c = text[i]
                out.append(c if preserve_strings else ("\n" if c == "\n" else " "))
                i += 1
                if c == "\\" and i < n:
                    c2 = text[i]
                    out.append(c2 if preserve_strings else ("\n" if c2 == "\n" else " "))
                    i += 1
                elif c == "'":
                    break
        else:
            out.append(ch)
            i += 1
    return "".join(out)


def strip_swift_comments_and_strings(text: str) -> str:
    """Replace Swift comments and string literals with spaces while preserving line numbers."""
    out: list[str] = []
    i = 0
    n = len(text)

    def skip_block_comment(pos: int) -> int:
        depth = 1
        out.append("  ")
        pos += 2
        while pos < n and depth > 0:
            c = text[pos]
            c2 = text[pos + 1] if pos + 1 < n else ""
            if c == "/" and c2 == "*":
                depth += 1
                out.append("  ")
                pos += 2
            elif c == "*" and c2 == "/":
                depth -= 1
                out.append("  ")
                pos += 2
            else:
                out.append("\n" if c == "\n" else " ")
                pos += 1
        return pos

    def skip_multiline_string(pos: int) -> int:
        out.append("   ")
        pos += 3
        while pos < n:
            if text[pos : pos + 3] == '"""':
                out.append("   ")
                pos += 3
                break
            if text[pos] == "\\" and pos + 1 < n:
                out.append(" ")
                pos += 1
                out.append("\n" if text[pos] == "\n" else " ")
                pos += 1
            else:
                out.append("\n" if text[pos] == "\n" else " ")
                pos += 1
        return pos

    def skip_quoted_string(pos: int) -> int:
        out.append(" ")
        pos += 1
        while pos < n:
            c = text[pos]
            if c == "\\" and pos + 1 < n and text[pos + 1] == "(":
                out.append("  ")
                pos += 2
                interp_depth = 1
                while pos < n and interp_depth > 0:
                    tc = text[pos]
                    if tc == '"':
                        pos = skip_quoted_string(pos)
                    elif tc == "(":
                        interp_depth += 1
                        out.append(" ")
                        pos += 1
                    elif tc == ")":
                        interp_depth -= 1
                        out.append(" ")
                        pos += 1
                    else:
                        out.append("\n" if tc == "\n" else " ")
                        pos += 1
            elif c == "\\":
                out.append(" ")
                pos += 1
                if pos < n:
                    out.append("\n" if text[pos] == "\n" else " ")
                    pos += 1
            elif c == '"':
                out.append(" ")
                pos += 1
                break
            else:
                out.append("\n" if c == "\n" else " ")
                pos += 1
        return pos

    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if ch == "/" and nxt == "/":
            out.append("  ")
            i += 2
            while i < n and text[i] != "\n":
                out.append(" ")
                i += 1
        elif ch == "/" and nxt == "*":
            i = skip_block_comment(i)
        elif ch == '"' and text[i : i + 3] == '"""':
            i = skip_multiline_string(i)
        elif ch == '"':
            i = skip_quoted_string(i)
        else:
            out.append(ch)
            i += 1
    return "".join(out)


# ---------------------------------------------------------------------------
# Module & Package Graph Indexers
# ---------------------------------------------------------------------------

def index_kmp_modules() -> dict[str, dict[str, Any]]:
    """Index KMP modules, layers, targets, and internal dependencies from kmp/*/build.gradle.kts."""
    kmp_dir = REPO_ROOT / "kmp"
    known_modules: set[str] = set()
    gradle_files: list[tuple[str, Path]] = []

    for child in sorted(kmp_dir.iterdir(), key=lambda p: p.name):
        if not child.is_dir() or child.name in EXCLUDED_DIRS:
            continue
        gradle_path = child / "build.gradle.kts"
        if gradle_path.is_file():
            known_modules.add(child.name)
            gradle_files.append((child.name, gradle_path))

    artifact_to_module = {mod: mod for mod in known_modules}
    artifact_to_module["bom"] = "kmp-bom"
    artifact_to_module["dependencies"] = "dependencyManagement"

    modules: dict[str, dict[str, Any]] = {}
    for mod, gradle_path in gradle_files:
        raw = gradle_path.read_text(encoding="utf-8")
        cleaned = strip_kotlin_comments_only(raw)

        targets = [
            target_name
            for target_name, pattern in KMP_TARGET_PATTERNS
            if pattern.search(cleaned)
        ]

        deps: set[str] = set()
        for accessor in re.findall(r"\bprojects\.([A-Za-z0-9_]+)\b", cleaned):
            dep_mod = camel_to_kebab(accessor)
            if dep_mod in known_modules and dep_mod != mod:
                deps.add(dep_mod)

        # Resolve BOM-managed internal coordinates (e.g., sample-app consuming io.github.abeta-dev:*).
        for artifact in re.findall(
            r'\b(?:api|implementation|compileOnly|runtimeOnly)\s*\(\s*"io\.github\.abeta-dev:([A-Za-z0-9_-]+)"\s*\)',
            cleaned,
        ):
            dep_mod = artifact_to_module.get(artifact)
            if dep_mod and dep_mod != mod:
                deps.add(dep_mod)

        entry: dict[str, Any] = {
            "dependencies": sorted(deps),
            "gradle_file": rel_posix(gradle_path),
            "layer": KMP_MODULE_LAYERS.get(mod, "unknown"),
            "symbol_count": 0,
            "targets": targets,
        }

        if mod == "kmp-bom":
            constraints: set[str] = set()
            for alias in re.findall(r"\binternal\.kmp\.([A-Za-z0-9_]+)\b", cleaned):
                mapped = artifact_to_module.get(camel_to_kebab(alias))
                if mapped:
                    constraints.add(mapped)
            entry["bom_constraints"] = sorted(constraints)

        modules[mod] = entry

    return modules


def classify_go_layer(pkg_rel: str) -> str:
    if pkg_rel.startswith("cmd/"):
        return "cmd"
    if pkg_rel.startswith("internal/handler"):
        return "handler"
    if pkg_rel.startswith("internal/usecase"):
        return "usecase"
    if pkg_rel.startswith("internal/domain"):
        return "domain"
    if pkg_rel.startswith("internal/infra"):
        return "infra"
    if pkg_rel.startswith("pkg/"):
        return "pkg"
    return "other"


def extract_go_imports(text: str) -> list[str]:
    """Extract all imported package paths from a Go source file."""
    cleaned = strip_go_comments_and_strings(text, preserve_strings=True)
    imports: set[str] = set()
    for block in re.findall(r"\bimport\s*\((.*?)\)", cleaned, flags=re.S):
        for imp in re.findall(r'"([^"]+)"', block):
            imports.add(imp)
    for single in re.findall(r'\bimport\s+(?:[A-Za-z0-9_.]+\s+)?"([^"]+)"', cleaned):
        imports.add(single)
    return sorted(imports)


def iter_go_prod_files() -> list[Path]:
    bff_dir = REPO_ROOT / "bff"
    files: list[Path] = []
    for subdir in ("cmd", "internal", "pkg"):
        base = bff_dir / subdir
        if not base.is_dir():
            continue
        for root, dirs, filenames in os.walk(base):
            dirs[:] = sorted(d for d in dirs if d not in EXCLUDED_DIRS)
            for fname in sorted(filenames):
                if not fname.endswith(".go") or fname.endswith("_test.go"):
                    continue
                path = Path(root) / fname
                if not is_excluded_path(path):
                    files.append(path)
    return sorted(files, key=rel_posix)


def index_go_packages(go_files: list[Path]) -> dict[str, dict[str, Any]]:
    bff_dir = REPO_ROOT / "bff"
    pkg_data: dict[str, dict[str, Any]] = {}

    for path in go_files:
        pkg_rel = path.parent.relative_to(bff_dir).as_posix()
        text = path.read_text(encoding="utf-8")
        pkg_match = re.search(
            r"^\s*package\s+([A-Za-z_][A-Za-z0-9_]*)\b",
            strip_go_comments_and_strings(text, preserve_strings=False),
            flags=re.M,
        )
        pkg_name = pkg_match.group(1) if pkg_match else path.parent.name

        if pkg_rel not in pkg_data:
            pkg_data[pkg_rel] = {
                "files": 0,
                "import_path": f"{GO_MODULE_PREFIX}{pkg_rel}",
                "internal_imports": set(),
                "layer": classify_go_layer(pkg_rel),
                "package_name": pkg_name,
                "symbol_count": 0,
            }
        pkg_data[pkg_rel]["files"] += 1

        for imp in extract_go_imports(text):
            if imp.startswith(GO_MODULE_PREFIX):
                target_rel = imp[len(GO_MODULE_PREFIX) :]
                if target_rel != pkg_rel:
                    pkg_data[pkg_rel]["internal_imports"].add(target_rel)

    result: dict[str, dict[str, Any]] = {}
    for pkg_rel in sorted(pkg_data):
        info = pkg_data[pkg_rel]
        result[pkg_rel] = {
            "files": info["files"],
            "import_path": info["import_path"],
            "internal_imports": sorted(info["internal_imports"]),
            "layer": info["layer"],
            "package_name": info["package_name"],
            "symbol_count": 0,
        }
    return result


# ---------------------------------------------------------------------------
# Kotlin Public API Symbol Indexer
# ---------------------------------------------------------------------------

KOTLIN_TOKEN_RE = re.compile(
    r"`[^`\n]+`|[A-Za-z_][A-Za-z0-9_]*|[{}()\[\]<>\.,:;=!?@*]"
)


def tokenize_with_lines(cleaned_text: str, token_re: re.Pattern[str]) -> list[tuple[str, int]]:
    """Tokenize stripped source code while tracking exact 1-based line numbers."""
    tokens: list[tuple[str, int]] = []
    line = 1
    last_pos = 0
    for match in token_re.finditer(cleaned_text):
        start = match.start()
        line += cleaned_text.count("\n", last_pos, start)
        last_pos = start
        tokens.append((match.group(0), line))
    return tokens


def is_kotlin_ident(tok: str) -> bool:
    if not tok:
        return False
    if tok.startswith("`") and tok.endswith("`") and len(tok) > 2:
        return True
    return tok[0].isalpha() or tok[0] == "_"


def skip_balanced(tokens: list[tuple[str, int]], idx: int, open_ch: str, close_ch: str) -> int:
    """Given tokens[idx][0] == open_ch, return index right after the matching close_ch."""
    depth = 0
    n = len(tokens)
    while idx < n:
        t = tokens[idx][0]
        if t == open_ch:
            depth += 1
        elif t == close_ch:
            depth -= 1
            if depth == 0:
                return idx + 1
        idx += 1
    return n


def index_kotlin_file(path: Path, module: str, source_set: str) -> list[dict[str, Any]]:
    """Extract top-level public Kotlin symbols from a production .kt file."""
    text = path.read_text(encoding="utf-8")
    cleaned = strip_kotlin_comments_and_strings(text)
    tokens = tokenize_with_lines(cleaned, KOTLIN_TOKEN_RE)
    file_rel = rel_posix(path)

    symbols: list[dict[str, Any]] = []
    n = len(tokens)
    i = 0
    brace_depth = 0
    paren_depth = 0
    bracket_depth = 0
    pending_mods: set[str] = set()
    pending_composable = False

    def reset_pending() -> None:
        nonlocal pending_composable
        pending_mods.clear()
        pending_composable = False

    while i < n:
        tok, line = tokens[i]

        if brace_depth > 0:
            if tok == "{":
                brace_depth += 1
            elif tok == "}":
                brace_depth -= 1
                if brace_depth == 0:
                    reset_pending()
            i += 1
            continue

        if tok == "{":
            brace_depth += 1
            reset_pending()
            i += 1
            continue
        if tok == "}":
            reset_pending()
            i += 1
            continue
        if tok == "(":
            paren_depth += 1
            i += 1
            continue
        if tok == ")":
            if paren_depth > 0:
                paren_depth -= 1
            i += 1
            continue
        if tok == "[":
            bracket_depth += 1
            i += 1
            continue
        if tok == "]":
            if bracket_depth > 0:
                bracket_depth -= 1
            i += 1
            continue

        if paren_depth > 0 or bracket_depth > 0:
            i += 1
            continue

        # At top level (brace_depth == 0, paren_depth == 0, bracket_depth == 0)
        if tok == "@":
            # Parse annotation: @[useSite:]Name[.SubName][<...>][(...)]
            j = i + 1
            is_file_target = False
            if (
                j + 1 < n
                and tokens[j][0] in KOTLIN_USE_SITE_TARGETS
                and tokens[j + 1][0] == ":"
            ):
                is_file_target = tokens[j][0] == "file"
                j += 2
            if j < n and tokens[j][0] == "[":
                # Grouped annotations @[A B]
                j = skip_balanced(tokens, j, "[", "]")
                i = j
                continue
            ann_parts: list[str] = []
            while j < n and is_kotlin_ident(tokens[j][0]):
                ann_parts.append(tokens[j][0])
                j += 1
                if j < n and tokens[j][0] == ".":
                    j += 1
                else:
                    break
            if j < n and tokens[j][0] == "<":
                j = skip_balanced(tokens, j, "<", ">")
            if j < n and tokens[j][0] == "(":
                j = skip_balanced(tokens, j, "(", ")")
            if not is_file_target and ann_parts and ann_parts[-1] == "Composable":
                pending_composable = True
            i = j
            continue

        if tok in KOTLIN_VISIBILITY_MODS or tok in KOTLIN_DECL_MODS:
            pending_mods.add(tok)
            i += 1
            continue

        if tok in ("package", "import", "val", "var"):
            reset_pending()
            i += 1
            continue

        prev_tok = tokens[i - 1][0] if i > 0 else ""

        if tok == "fun" and i + 1 < n and tokens[i + 1][0] == "interface":
            pending_mods.add("fun")
            i += 1
            continue

        if tok in ("class", "interface", "object", "typealias"):
            # Reject ::class literals and anonymous `object : ...` or `object { ... }`
            if prev_tok == ":" or i + 1 >= n or not is_kotlin_ident(tokens[i + 1][0]):
                reset_pending()
                i += 1
                continue

            name_tok = tokens[i + 1][0].strip("`")
            is_non_public = bool(pending_mods & {"private", "internal", "protected"})
            if not is_non_public:
                if tok == "typealias":
                    kind = "typealias"
                elif tok == "object":
                    kind = "data object" if "data" in pending_mods else "object"
                elif tok == "interface":
                    if "sealed" in pending_mods:
                        kind = "sealed interface"
                    elif "fun" in pending_mods:
                        kind = "fun interface"
                    else:
                        kind = "interface"
                else:
                    if "data" in pending_mods:
                        kind = "data class"
                    elif "value" in pending_mods:
                        kind = "value class"
                    elif "enum" in pending_mods:
                        kind = "enum class"
                    elif "sealed" in pending_mods:
                        kind = "sealed class"
                    elif "annotation" in pending_mods:
                        kind = "annotation class"
                    elif "abstract" in pending_mods:
                        kind = "abstract class"
                    else:
                        kind = "class"
                symbols.append({
                    "file": file_rel,
                    "kind": kind,
                    "line": line,
                    "module": module,
                    "name": name_tok,
                    "source_set": source_set,
                })
            reset_pending()
            i += 2
            continue

        if tok == "fun":
            is_non_public = bool(pending_mods & {"private", "internal", "protected"})
            is_composable = pending_composable
            j = i + 1
            if j < n and tokens[j][0] == "<":
                j = skip_balanced(tokens, j, "<", ">")

            # Collect receiver.funcName segments up to '('
            segments: list[str] = []
            while j < n:
                t = tokens[j][0]
                if t == "(":
                    break
                if t in ("{", "}", ";", "="):
                    segments.clear()
                    break
                if t == "<":
                    j = skip_balanced(tokens, j, "<", ">")
                    continue
                if t in (".", "?", "*"):
                    j += 1
                    continue
                if is_kotlin_ident(t):
                    segments.append(t.strip("`"))
                    j += 1
                    continue
                segments.clear()
                break

            if segments and j < n and tokens[j][0] == "(" and not is_non_public:
                if len(segments) >= 2:
                    # Keep concise Receiver.funcName for extension functions (strip package qualifiers)
                    func_name = f"{segments[-2]}.{segments[-1]}"
                else:
                    func_name = segments[0]
                kind = "composable fun" if is_composable else "fun"
                symbols.append({
                    "file": file_rel,
                    "kind": kind,
                    "line": line,
                    "module": module,
                    "name": func_name,
                    "source_set": source_set,
                })
            reset_pending()
            i = j if j > i else i + 1
            continue

        # Any other top-level token cannot sit between modifiers and a declaration keyword.
        reset_pending()
        i += 1

    return symbols


def iter_kotlin_prod_files() -> list[tuple[Path, str, str]]:
    """Return sorted list of (path, module, source_set) for all production Kotlin files."""
    kmp_dir = REPO_ROOT / "kmp"
    results: list[tuple[Path, str, str]] = []

    # 1. KMP module source sets: kmp/<module>/src/*Main/**/*.kt and kmp/<module>/src/main/**/*.kt
    for mod_dir in sorted(kmp_dir.iterdir(), key=lambda p: p.name):
        if not mod_dir.is_dir() or mod_dir.name in EXCLUDED_DIRS or mod_dir.name == "platforms":
            continue
        src_dir = mod_dir / "src"
        if not src_dir.is_dir():
            continue
        for ss_dir in sorted(src_dir.iterdir(), key=lambda p: p.name):
            if not ss_dir.is_dir():
                continue
            ss_name = ss_dir.name
            if not (ss_name == "main" or ss_name.endswith("Main")):
                continue
            for root, dirs, filenames in os.walk(ss_dir):
                dirs[:] = sorted(d for d in dirs if d not in EXCLUDED_DIRS)
                for fname in sorted(filenames):
                    if not fname.endswith(".kt") or fname.endswith("Test.kt"):
                        continue
                    path = Path(root) / fname
                    if not is_excluded_path(path):
                        results.append((path, mod_dir.name, ss_name))

    # 2. Platform reference host Kotlin files: kmp/platforms/{android,wearos}/*.kt
    for host in ("android", "wearos"):
        host_dir = kmp_dir / "platforms" / host
        if not host_dir.is_dir():
            continue
        for path in sorted(host_dir.glob("*.kt"), key=lambda p: p.name):
            if path.name.endswith("Test.kt") or is_excluded_path(path):
                continue
            results.append((path, f"platforms/{host}", "reference"))

    return sorted(results, key=lambda item: rel_posix(item[0]))


# ---------------------------------------------------------------------------
# Go Exported Symbol Indexer
# ---------------------------------------------------------------------------

GO_TOKEN_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*|[{}()\[\]\.,;=*]")


def is_go_exported(name: str) -> bool:
    return bool(name) and name[0].isupper()


def index_go_file(path: Path, pkg_rel: str) -> list[dict[str, Any]]:
    """Extract exported Go types, top-level funcs, and methods on exported receivers."""
    text = path.read_text(encoding="utf-8")
    cleaned = strip_go_comments_and_strings(text, preserve_strings=False)
    tokens = tokenize_with_lines(cleaned, GO_TOKEN_RE)
    file_rel = rel_posix(path)

    symbols: list[dict[str, Any]] = []
    n = len(tokens)
    i = 0
    brace_depth = 0

    def parse_type_spec(pos: int) -> int:
        if pos >= n:
            return pos
        name, name_line = tokens[pos]
        if not (name[0].isalpha() or name[0] == "_"):
            return pos + 1
        j = pos + 1
        # Optional generic type parameters: type Foo[T any] ...
        if j < n and tokens[j][0] == "[":
            j = skip_balanced(tokens, j, "[", "]")
        # Optional alias `=`
        if j < n and tokens[j][0] == "=":
            j += 1
        underlying = tokens[j][0] if j < n else ""
        if is_go_exported(name):
            if underlying == "struct":
                kind = "struct"
            elif underlying == "interface":
                kind = "interface"
            else:
                kind = "type"
            symbols.append({
                "file": file_rel,
                "kind": kind,
                "line": name_line,
                "name": name,
                "package": pkg_rel,
            })
        return j

    while i < n:
        tok, _line = tokens[i]

        if brace_depth > 0:
            if tok == "{":
                brace_depth += 1
            elif tok == "}":
                brace_depth -= 1
            i += 1
            continue

        if tok == "{":
            brace_depth += 1
            i += 1
            continue

        if tok == "type":
            if i + 1 < n and tokens[i + 1][0] == "(":
                # Grouped type ( ... ) block
                j = i + 2
                inner_brace = 0
                prev_line = tokens[i + 1][1]
                expect_spec = True
                while j < n:
                    t, tline = tokens[j]
                    if inner_brace > 0:
                        if t == "{":
                            inner_brace += 1
                        elif t == "}":
                            inner_brace -= 1
                        prev_line = tline
                        j += 1
                        continue
                    if t == ")":
                        j += 1
                        break
                    if t == "{":
                        inner_brace += 1
                        prev_line = tline
                        j += 1
                        continue
                    if t == ";":
                        expect_spec = True
                        prev_line = tline
                        j += 1
                        continue
                    if tline > prev_line:
                        expect_spec = True
                    if expect_spec and (t[0].isalpha() or t[0] == "_"):
                        j = parse_type_spec(j)
                        expect_spec = False
                        prev_line = tokens[j - 1][1] if j - 1 < n else tline
                        continue
                    prev_line = tline
                    j += 1
                i = j
                continue
            i = parse_type_spec(i + 1)
            continue

        if tok == "func":
            # In Go, a top-level func declaration starts a statement (first token on line,
            # or preceded by ';' or '}'), never embedded inside `type Foo func(...)` or `var x = func(...)`.
            at_stmt_start = (
                i == 0
                or tokens[i - 1][1] < _line
                or tokens[i - 1][0] in (";", "}")
            )
            if not at_stmt_start:
                i += 1
                continue

            if i + 1 < n and tokens[i + 1][0] == "(":
                # Method: func (r *Recv[T]) Name(...)
                recv_end = skip_balanced(tokens, i + 1, "(", ")")
                # Extract receiver type identifier ignoring * and [...] type params
                recv_idents: list[str] = []
                k = i + 2
                while k < recv_end - 1:
                    rt = tokens[k][0]
                    if rt == "[":
                        k = skip_balanced(tokens, k, "[", "]")
                        continue
                    if rt[0].isalpha() or rt[0] == "_":
                        recv_idents.append(rt)
                    k += 1
                recv_type = recv_idents[-1] if recv_idents else ""
                if recv_end < n and tokens[recv_end][1] == tokens[recv_end - 1][1]:
                    method_name, method_line = tokens[recv_end]
                    if (
                        is_go_exported(recv_type)
                        and is_go_exported(method_name)
                        and (method_name[0].isalpha() or method_name[0] == "_")
                    ):
                        symbols.append({
                            "file": file_rel,
                            "kind": "method",
                            "line": method_line,
                            "name": f"{recv_type}.{method_name}",
                            "package": pkg_rel,
                        })
                    i = recv_end + 1
                    continue
                i = recv_end
                continue

            if i + 1 < n:
                func_name, func_line = tokens[i + 1]
                if is_go_exported(func_name) and (func_name[0].isalpha() or func_name[0] == "_"):
                    symbols.append({
                        "file": file_rel,
                        "kind": "func",
                        "line": func_line,
                        "name": func_name,
                        "package": pkg_rel,
                    })
                i += 2
                continue

        i += 1

    return symbols


# ---------------------------------------------------------------------------
# Swift Public Symbol Indexer
# ---------------------------------------------------------------------------

SWIFT_TOKEN_RE = re.compile(r"`[^`\n]+`|[A-Za-z_][A-Za-z0-9_]*|[{}()\[\]<>\.,:;=!?@]")


def iter_swift_prod_files() -> list[tuple[Path, str]]:
    sources_dir = REPO_ROOT / "kmp" / "platforms" / "apple" / "Sources"
    if not sources_dir.is_dir():
        return []
    files: list[tuple[Path, str]] = []
    for target_dir in sorted(sources_dir.iterdir(), key=lambda p: p.name):
        if not target_dir.is_dir() or target_dir.name in EXCLUDED_DIRS:
            continue
        for root, dirs, filenames in os.walk(target_dir):
            dirs[:] = sorted(d for d in dirs if d not in EXCLUDED_DIRS)
            for fname in sorted(filenames):
                if not fname.endswith(".swift"):
                    continue
                path = Path(root) / fname
                if not is_excluded_path(path):
                    files.append((path, target_dir.name))
    return sorted(files, key=lambda item: rel_posix(item[0]))


def index_swift_file(path: Path, target: str) -> list[dict[str, Any]]:
    """Extract public Swift types and public functions from a SwiftPM Sources file."""
    text = path.read_text(encoding="utf-8")
    cleaned = strip_swift_comments_and_strings(text)
    tokens = tokenize_with_lines(cleaned, SWIFT_TOKEN_RE)
    file_rel = rel_posix(path)

    symbols: list[dict[str, Any]] = []
    n = len(tokens)
    i = 0
    brace_depth = 0
    paren_depth = 0
    pending_mods: set[str] = set()
    # Stack of (type_name, is_public_extension, open_brace_depth)
    scope_stack: list[tuple[str, bool, int]] = []
    pending_scope: tuple[str, bool] | None = None

    def current_enclosing_name() -> str:
        return ".".join(name for name, _, _ in scope_stack)

    def inside_public_extension() -> bool:
        return bool(scope_stack) and scope_stack[-1][1] and brace_depth == scope_stack[-1][2]

    def at_type_member_depth() -> bool:
        if not scope_stack:
            return brace_depth == 0
        return brace_depth == scope_stack[-1][2]

    while i < n:
        tok, line = tokens[i]

        if tok == "(":
            paren_depth += 1
            i += 1
            continue
        if tok == ")":
            if paren_depth > 0:
                paren_depth -= 1
            i += 1
            continue
        if tok == "{":
            brace_depth += 1
            if pending_scope is not None:
                scope_stack.append((pending_scope[0], pending_scope[1], brace_depth))
                pending_scope = None
            pending_mods.clear()
            i += 1
            continue
        if tok == "}":
            while scope_stack and scope_stack[-1][2] == brace_depth:
                scope_stack.pop()
            if brace_depth > 0:
                brace_depth -= 1
            pending_mods.clear()
            i += 1
            continue

        if paren_depth > 0 or not at_type_member_depth():
            i += 1
            continue

        if tok == "@":
            j = i + 1
            if j < n and is_kotlin_ident(tokens[j][0]):
                j += 1
            if j < n and tokens[j][0] == "(":
                j = skip_balanced(tokens, j, "(", ")")
            i = j
            continue

        # Handle `class` modifier before `func` / `var` / `let`
        if tok == "class" and i + 1 < n and tokens[i + 1][0] in ("func", "var", "let"):
            pending_mods.add("class")
            i += 1
            continue

        if tok in SWIFT_MODIFIERS:
            pending_mods.add(tok)
            i += 1
            continue

        if tok in ("struct", "class", "enum", "protocol", "extension"):
            if i + 1 >= n or not is_kotlin_ident(tokens[i + 1][0]):
                pending_mods.clear()
                i += 1
                continue
            raw_name = tokens[i + 1][0].strip("`")
            is_public = bool(pending_mods & {"public", "open"})
            is_non_public = bool(pending_mods & {"private", "fileprivate", "internal"})
            is_final = "final" in pending_mods

            if tok == "extension":
                pending_scope = (raw_name, is_public and not is_non_public)
            else:
                enclosing = current_enclosing_name()
                full_name = f"{enclosing}.{raw_name}" if enclosing else raw_name
                pending_scope = (raw_name, False)
                if is_public and not is_non_public:
                    if tok == "class" and is_final:
                        kind = "public final class"
                    else:
                        kind = f"public {tok}"
                    symbols.append({
                        "file": file_rel,
                        "kind": kind,
                        "line": line,
                        "name": full_name,
                        "target": target,
                    })
            pending_mods.clear()
            i += 2
            continue

        if tok == "func":
            if i + 1 >= n or not is_kotlin_ident(tokens[i + 1][0]):
                pending_mods.clear()
                i += 1
                continue
            raw_name = tokens[i + 1][0].strip("`")
            is_non_public = bool(pending_mods & {"private", "fileprivate", "internal"})
            is_public = bool(pending_mods & {"public", "open"}) or (
                inside_public_extension() and not is_non_public
            )
            if is_public and not is_non_public:
                enclosing = current_enclosing_name()
                full_name = f"{enclosing}.{raw_name}" if enclosing else raw_name
                symbols.append({
                    "file": file_rel,
                    "kind": "public func",
                    "line": line,
                    "name": full_name,
                    "target": target,
                })
            pending_mods.clear()
            i += 2
            continue

        if tok in ("var", "let", "init", "deinit", "typealias", "associatedtype", "subscript", "case"):
            pending_mods.clear()

        i += 1

    return symbols


# ---------------------------------------------------------------------------
# CodeGraph Assembly & Formatting
# ---------------------------------------------------------------------------

def build_codegraph() -> dict[str, Any]:
    modules = index_kmp_modules()
    go_files = iter_go_prod_files()
    go_packages = index_go_packages(go_files)
    kotlin_files = iter_kotlin_prod_files()
    swift_files = iter_swift_prod_files()

    kotlin_symbols: list[dict[str, Any]] = []
    kotlin_counts: Counter[str] = Counter()
    for path, mod, ss in kotlin_files:
        file_syms = index_kotlin_file(path, mod, ss)
        kotlin_symbols.extend(file_syms)
        kotlin_counts[mod] += len(file_syms)

    for mod, info in modules.items():
        info["symbol_count"] = kotlin_counts.get(mod, 0)

    bff_dir = REPO_ROOT / "bff"
    go_symbols: list[dict[str, Any]] = []
    go_counts: Counter[str] = Counter()
    for path in go_files:
        pkg_rel = path.parent.relative_to(bff_dir).as_posix()
        file_syms = index_go_file(path, pkg_rel)
        go_symbols.extend(file_syms)
        go_counts[pkg_rel] += len(file_syms)

    for pkg_rel, info in go_packages.items():
        info["symbol_count"] = go_counts.get(pkg_rel, 0)

    swift_symbols: list[dict[str, Any]] = []
    swift_targets: set[str] = set()
    for path, target in swift_files:
        swift_targets.add(target)
        swift_symbols.extend(index_swift_file(path, target))

    # Deterministic sorting
    kotlin_symbols.sort(key=lambda s: (s["module"], s["file"], s["line"], s["name"], s["kind"]))
    go_symbols.sort(key=lambda s: (s["package"], s["file"], s["line"], s["name"], s["kind"]))
    swift_symbols.sort(key=lambda s: (s["target"], s["file"], s["line"], s["name"], s["kind"]))

    stats = {
        "go_files": len(go_files),
        "go_packages": len(go_packages),
        "go_symbols": len(go_symbols),
        "kmp_modules": len(modules),
        "kotlin_files": len(kotlin_files),
        "kotlin_symbols": len(kotlin_symbols),
        "swift_files": len(swift_files),
        "swift_symbols": len(swift_symbols),
        "swift_targets": len(swift_targets),
        "total_symbols": len(kotlin_symbols) + len(go_symbols) + len(swift_symbols),
    }

    return {
        "go_packages": go_packages,
        "modules": modules,
        "schema_version": SCHEMA_VERSION,
        "stats": stats,
        "symbols": {
            "go": go_symbols,
            "kotlin": kotlin_symbols,
            "swift": swift_symbols,
        },
    }


def render_symbols_json(graph: dict[str, Any]) -> str:
    """Render deterministic, compact, git-diff-friendly JSON (single-line symbol records)."""
    lines: list[str] = ["{"]

    def append_block(key: str, value: Any, trailing_comma: bool) -> None:
        dumped = json.dumps(value, indent=2, sort_keys=True, ensure_ascii=False)
        block_lines = dumped.splitlines()
        comma = "," if trailing_comma else ""
        if len(block_lines) == 1:
            lines.append(f'  "{key}": {block_lines[0]}{comma}')
        else:
            lines.append(f'  "{key}": {block_lines[0]}')
            for middle in block_lines[1:-1]:
                lines.append(f"  {middle}")
            lines.append(f"  {block_lines[-1]}{comma}")

    append_block("schema_version", graph["schema_version"], trailing_comma=True)
    append_block("stats", graph["stats"], trailing_comma=True)
    append_block("modules", graph["modules"], trailing_comma=True)
    append_block("go_packages", graph["go_packages"], trailing_comma=True)

    # Compact single-line entries inside `symbols` arrays for fast grep & minimal git diffs.
    lines.append('  "symbols": {')
    sym_keys = sorted(graph["symbols"].keys())
    for k_idx, lang in enumerate(sym_keys):
        items = graph["symbols"][lang]
        outer_comma = "," if k_idx < len(sym_keys) - 1 else ""
        if not items:
            lines.append(f'    "{lang}": []{outer_comma}')
            continue
        lines.append(f'    "{lang}": [')
        for idx, item in enumerate(items):
            inner_comma = "," if idx < len(items) - 1 else ""
            compact = json.dumps(item, sort_keys=True, ensure_ascii=False, separators=(", ", ": "))
            lines.append(f"      {compact}{inner_comma}")
        lines.append(f"    ]{outer_comma}")
    lines.append("  }")
    lines.append("}")
    return "\n".join(lines) + "\n"


def render_index_md(graph: dict[str, Any]) -> str:
    """Render deterministic, concise human & AI markdown summary for docs/codegraph/INDEX.md."""
    stats = graph["stats"]
    modules = graph["modules"]
    go_packages = graph["go_packages"]
    symbols = graph["symbols"]

    # Build quick lookup maps for key entry-point resolution (prefer commonMain / non-test).
    lookup: dict[tuple[str, str], list[dict[str, Any]]] = {}
    for lang in ("kotlin", "go", "swift"):
        for sym in symbols[lang]:
            lookup.setdefault((lang, sym["name"]), []).append(sym)

    def pick_primary_symbol(
        lang: str,
        name: str,
        preferred_scope: str | None = None,
    ) -> dict[str, Any] | None:
        matches = lookup.get((lang, name), [])
        if not matches:
            return None
        if preferred_scope:
            scope_key = "module" if lang == "kotlin" else ("package" if lang == "go" else "target")
            scoped = [m for m in matches if m[scope_key] == preferred_scope]
            if scoped:
                matches = scoped
        if lang == "kotlin":
            ranked = sorted(
                matches,
                key=lambda s: (
                    0 if s["source_set"] == "commonMain" else 1,
                    1 if s["module"].startswith("platforms/") else 0,
                    s["file"],
                    s["line"],
                ),
            )
            return ranked[0]
        return matches[0]

    lines: list[str] = [
        "# Abeta KMP Kit — CodeGraph Symbol & Dependency Index",
        "",
        "> **Auto-generated deterministically** by `./scripts/generate_codegraph.py` (schema `1.0`).",
        "> Do not hand-edit. Refresh with `./scripts/generate_codegraph.py` and verify in CI with `./scripts/generate_codegraph.py --check`.",
        "",
        "## Quick Symbol Query (Step 5 Grounding)",
        "",
        "Query any Kotlin, Go, or Swift public symbol in milliseconds without grepping the repository:",
        "",
        "```bash",
        "./scripts/generate_codegraph.py --query CachedScreenRepository",
        "./scripts/generate_codegraph.py --query LayoutController",
        "./scripts/generate_codegraph.py --query UpstreamCachedScreenRepository",
        "./scripts/generate_codegraph.py --query AbetaWatchViewModel",
        "```",
        "",
        "## Index Summary",
        "",
        "| Dimension | Count |",
        "| :--- | ---: |",
        f"| KMP Modules (`kmp/*/build.gradle.kts`) | {stats['kmp_modules']} |",
        f"| Go BFF Packages (`bff/{{cmd,internal,pkg}}`) | {stats['go_packages']} |",
        f"| SwiftPM Targets (`kmp/platforms/apple/Sources/*`) | {stats['swift_targets']} |",
        f"| Kotlin Public Symbols ({stats['kotlin_files']} prod files) | {stats['kotlin_symbols']} |",
        f"| Go Exported Symbols ({stats['go_files']} prod files) | {stats['go_symbols']} |",
        f"| Swift Public Symbols ({stats['swift_files']} prod files) | {stats['swift_symbols']} |",
        f"| **Total Indexed Symbols** | **{stats['total_symbols']}** |",
        "",
        "## KMP Module Dependency Graph",
        "",
        "| Layer | Module | Targets | Internal Dependencies | Public Symbols |",
        "| :--- | :--- | :--- | :--- | ---: |",
    ]

    sorted_modules = sorted(
        modules.items(),
        key=lambda kv: (LAYER_ORDER.get(kv[1]["layer"], 99), kv[0]),
    )
    for mod, info in sorted_modules:
        layer_label = f"Layer {info['layer']}" if info["layer"].isdigit() else info["layer"].upper()
        targets_str = ", ".join(f"`{t}`" for t in info["targets"]) if info["targets"] else "—"
        if mod == "kmp-bom" and info.get("bom_constraints"):
            deps_str = "constrains: " + ", ".join(f"`{d}`" for d in info["bom_constraints"])
        elif info["dependencies"]:
            deps_str = ", ".join(f"`{d}`" for d in info["dependencies"])
        else:
            deps_str = "—"
        lines.append(
            f"| {layer_label} | `{mod}` | {targets_str} | {deps_str} | {info['symbol_count']} |"
        )

    # Platform reference hosts & SwiftPM targets summary
    platform_kt_counts: Counter[str] = Counter(
        s["module"] for s in symbols["kotlin"] if s["module"].startswith("platforms/")
    )
    swift_target_counts: Counter[str] = Counter(s["target"] for s in symbols["swift"])

    lines.extend([
        "",
        "## Platform Reference Hosts & SwiftPM Targets",
        "",
        "| Host / Target | Language | Source Root | Public Symbols |",
        "| :--- | :--- | :--- | ---: |",
    ])
    for host in sorted(platform_kt_counts):
        lines.append(
            f"| `{host}` | Kotlin | `kmp/{host}/` | {platform_kt_counts[host]} |"
        )
    for target in sorted(swift_target_counts):
        lines.append(
            f"| `{target}` | Swift | `kmp/platforms/apple/Sources/{target}/` | {swift_target_counts[target]} |"
        )

    lines.extend([
        "",
        "## Go BFF Package Dependency Graph",
        "",
        "| Layer | Package (`bff/...`) | Internal Imports | Files | Exported Symbols |",
        "| :--- | :--- | :--- | ---: | ---: |",
    ])

    sorted_go_pkgs = sorted(
        go_packages.items(),
        key=lambda kv: (GO_LAYER_ORDER.get(kv[1]["layer"], 99), kv[0]),
    )
    for pkg_rel, info in sorted_go_pkgs:
        imps = ", ".join(f"`{imp}`" for imp in info["internal_imports"]) if info["internal_imports"] else "—"
        lines.append(
            f"| `{info['layer']}` | `{pkg_rel}` | {imps} | {info['files']} | {info['symbol_count']} |"
        )

    lines.extend([
        "",
        "## Key Entry-Point Symbols by Layer",
        "",
    ])

    for section_title, entries in KEY_ENTRYPOINTS:
        resolved_rows: list[str] = []
        for lang, sym_name, preferred_scope in entries:
            sym = pick_primary_symbol(lang, sym_name, preferred_scope)
            if sym is None:
                continue
            scope = (
                sym["module"]
                if lang == "kotlin"
                else (sym["package"] if lang == "go" else sym["target"])
            )
            resolved_rows.append(
                f"| `{scope}` | `{sym['kind']}` | `{sym['name']}` | `{sym['file']}:{sym['line']}` |"
            )
        if not resolved_rows:
            continue
        lines.extend([
            f"### {section_title}",
            "",
            "| Module / Package | Kind | Symbol | Location |",
            "| :--- | :--- | :--- | :--- |",
            *resolved_rows,
            "",
        ])

    return "\n".join(lines).rstrip() + "\n"


# ---------------------------------------------------------------------------
# CLI Commands
# ---------------------------------------------------------------------------

def cmd_generate() -> int:
    graph = build_codegraph()
    symbols_json = render_symbols_json(graph)
    index_md = render_index_md(graph)

    CODEGRAPH_DIR.mkdir(parents=True, exist_ok=True)
    SYMBOLS_JSON_PATH.write_text(symbols_json, encoding="utf-8", newline="\n")
    INDEX_MD_PATH.write_text(index_md, encoding="utf-8", newline="\n")

    stats = graph["stats"]
    print(
        f"Generated docs/codegraph/symbols.json ({len(symbols_json.encode('utf-8')):,} bytes) "
        f"and docs/codegraph/INDEX.md ({len(index_md.encode('utf-8')):,} bytes): "
        f"{stats['total_symbols']} symbols "
        f"(kotlin={stats['kotlin_symbols']}, go={stats['go_symbols']}, swift={stats['swift_symbols']})."
    )
    return 0


def cmd_check() -> int:
    graph = build_codegraph()
    expected = {
        SYMBOLS_JSON_PATH: render_symbols_json(graph).encode("utf-8"),
        INDEX_MD_PATH: render_index_md(graph).encode("utf-8"),
    }

    stale: list[str] = []
    for path, expected_bytes in expected.items():
        rel = rel_posix(path)
        if not path.is_file():
            stale.append(f"{rel} (missing)")
            continue
        actual_bytes = path.read_bytes()
        if actual_bytes != expected_bytes:
            stale.append(f"{rel} (out of date)")

    if stale:
        print("ERROR: CodeGraph artifacts are stale or missing:", file=sys.stderr)
        for item in stale:
            print(f"  - {item}", file=sys.stderr)
        print("Run ./scripts/generate_codegraph.py to refresh docs/codegraph/", file=sys.stderr)
        return 1

    stats = graph["stats"]
    print(
        f"PASSED: docs/codegraph/ is up to date "
        f"({stats['total_symbols']} symbols: "
        f"kotlin={stats['kotlin_symbols']}, go={stats['go_symbols']}, swift={stats['swift_symbols']})."
    )
    return 0


def cmd_query(
    query: str,
    *,
    lang_filter: str | None = None,
    module_filter: str | None = None,
    kind_filter: str | None = None,
) -> int:
    if not SYMBOLS_JSON_PATH.is_file():
        print(
            "ERROR: docs/codegraph/symbols.json not found. "
            "Run ./scripts/generate_codegraph.py to refresh docs/codegraph/",
            file=sys.stderr,
        )
        return 1

    graph = json.loads(SYMBOLS_JSON_PATH.read_text(encoding="utf-8"))
    q = query.strip().lower()
    if not q:
        print("ERROR: --query requires a non-empty substring.", file=sys.stderr)
        return 2

    mod_q = module_filter.strip().lower() if module_filter else None
    kind_q = kind_filter.strip().lower() if kind_filter else None
    langs = [lang_filter] if lang_filter else ["kotlin", "go", "swift"]

    matches: list[tuple[int, str, str, str, str, int]] = []
    for lang in langs:
        for sym in graph.get("symbols", {}).get(lang, []):
            scope = (
                sym["module"]
                if lang == "kotlin"
                else (sym["package"] if lang == "go" else sym["target"])
            )
            name = sym["name"]
            kind = sym["kind"]
            file_rel = sym["file"]
            line = sym["line"]

            if mod_q and mod_q not in scope.lower():
                continue
            if kind_q and kind_q not in kind.lower():
                continue

            name_l = name.lower()
            hit = q in name_l or ("/" in q and (q in scope.lower() or q in file_rel.lower()))
            if not hit:
                continue

            # Rank exact matches first, then prefix matches, then substring matches.
            if name_l == q:
                rank = 0
            elif name_l.startswith(q) or name_l.endswith(f".{q}"):
                rank = 1
            else:
                rank = 2
            matches.append((rank, scope, kind, name, file_rel, line))

    matches.sort(key=lambda item: (item[0], item[1], item[4], item[5], item[3]))
    if not matches:
        print(f"No symbols matching '{query}'.")
        return 0

    for _rank, scope, kind, name, file_rel, line in matches:
        print(f"{scope} | {kind} | {name} | {file_rel}:{line}")
    return 0


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Deterministic CodeGraph symbol & dependency indexer for abeta-dev/kmp.",
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument(
        "--check",
        action="store_true",
        help="Verify committed docs/codegraph/{symbols.json,INDEX.md} match generated output byte-for-byte.",
    )
    mode.add_argument(
        "--query",
        metavar="SUBSTRING",
        help="Query matching symbols from docs/codegraph/symbols.json (`module | kind | name | file:line`).",
    )
    parser.add_argument(
        "--lang",
        choices=("kotlin", "go", "swift"),
        help="Optional language filter when used with --query.",
    )
    parser.add_argument(
        "--module",
        metavar="SUBSTRING",
        help="Optional module/package/target filter when used with --query.",
    )
    parser.add_argument(
        "--kind",
        metavar="SUBSTRING",
        help="Optional symbol kind filter when used with --query (e.g. 'interface', 'struct', 'composable').",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    if args.check:
        return cmd_check()
    if args.query is not None:
        return cmd_query(
            args.query,
            lang_filter=args.lang,
            module_filter=args.module,
            kind_filter=args.kind,
        )
    return cmd_generate()


if __name__ == "__main__":
    raise SystemExit(main())
