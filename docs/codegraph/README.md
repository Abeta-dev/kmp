# CodeGraph — Deterministic Symbol & Dependency Index (`docs/codegraph/`)

CodeGraph provides a fast, zero-external-dependency, byte-for-byte deterministic index of all KMP modules, Go BFF packages, and public API symbols across `abeta-dev/kmp` (Kotlin, Go, and Swift).

---

## Mandatory Grounding Order (Step 5)

CodeGraph powers **Step 5** of the repository's Mandatory Grounding Order so humans and AI coding agents locate exact module boundaries, dependencies, and `file:line` symbol anchors in milliseconds before opening source files:

1. `AGENTS.md` — architectural truth gates, invariants, and module owner routing
2. Architecture docs (`docs/architecture.md`, `docs/atomic-and-action-separation.md`, `docs/local-database-room3.md`)
3. Flow docs (SDUI screen lifecycle, action dispatch, offline sync & streaming flows)
4. Pattern docs (module owner personas under `agents/owners/` and skills under `skills/*/SKILL.md`)
5. **CodeGraph symbols (`docs/codegraph/INDEX.md` + `docs/codegraph/symbols.json`)** — resolve exact symbols, module graphs, and `file:line` locations
6. Source file reads — open targeted line ranges only for verification and editing

---

## Artifacts in This Directory

| File | Purpose | Ownership |
| :--- | :--- | :--- |
| `docs/codegraph/README.md` | Grounding guide, CLI usage, schema reference, and CI freshness policy | Documentation |
| `docs/codegraph/INDEX.md` | Human- & agent-readable module/package dependency graph, symbol counts, and key entry points | Auto-generated |
| `docs/codegraph/symbols.json` | Compact, sorted, machine-queryable symbol & dependency index (`schema_version: "1.0"`) | Auto-generated |

> Never hand-edit `INDEX.md` or `symbols.json`. Regenerate both deterministically via `./scripts/generate_codegraph.py`.

---

## CLI Usage (`scripts/generate_codegraph.py`)

Requires only Python 3 standard library (zero external packages).

### 1. Query Symbols in Milliseconds

Reads `docs/codegraph/symbols.json` directly without scanning the workspace and prints `module | kind | name | file:line`:

```bash
# Substring lookup (case-insensitive, exact matches ranked first)
./scripts/generate_codegraph.py --query CachedScreenRepository
./scripts/generate_codegraph.py --query LayoutController
./scripts/generate_codegraph.py --query UpstreamCachedScreenRepository
./scripts/generate_codegraph.py --query AbetaWatchViewModel

# Optional narrowing filters
./scripts/generate_codegraph.py --query Snippet --lang kotlin --module sdui-contracts
./scripts/generate_codegraph.py --query Middleware --lang go
./scripts/generate_codegraph.py --query View --lang swift --kind struct
```

### 2. Regenerate Artifacts

Re-scans production sources and rewrites `docs/codegraph/symbols.json` and `docs/codegraph/INDEX.md` deterministically (sorted keys, UTF-8, LF line endings, zero timestamps):

```bash
./scripts/generate_codegraph.py
```

### 3. CI Freshness Gate (`--check`)

Regenerates both artifacts in memory and compares byte-for-byte against the committed files. Exits `0` if up to date, or `1` with an actionable error message if any public symbol, module target, or internal dependency drifted:

```bash
./scripts/generate_codegraph.py --check
```

---

## Indexing Scope & Exclusions

**Indexed (production sources only):**
- **KMP Module Graph**: `kmp/*/build.gradle.kts` — architectural layer (`0`, `1`, `2`, `3`, `bom`), declared targets (`androidLibrary`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchos*`, `wasmJs`), internal `projects.*` / BOM dependencies, and symbol counts.
- **Go BFF Package Graph**: `bff/{cmd,internal,pkg}/**/*.go` — Clean Architecture layer (`cmd`, `handler`, `usecase`, `domain`, `infra`, `pkg`), internal `github.com/abeta-dev/kmp/bff/*` imports, file counts, and symbol counts.
- **Kotlin Public API Symbols**: `kmp/*/src/*Main/**/*.kt`, `kmp/sample-app/src/main/**/*.kt`, and `kmp/platforms/{android,wearos}/*.kt` — top-level public (non-`private`, non-`internal`) `class`, `data class`, `value class`, `enum class`, `sealed class`, `sealed interface`, `interface`, `fun interface`, `object`, `data object`, `typealias`, and top-level `fun` (including `@Composable`, `expect`, `actual`, `suspend`).
- **Go Exported Symbols**: `bff/{pkg,internal,cmd}/**/*.go` — exported types (`struct`, `interface`, `type`), exported top-level functions (`func`), and exported methods (`<Recv>.<Name>`) on exported receivers.
- **Swift Public Symbols**: `kmp/platforms/apple/Sources/**/*.swift` — `public struct`, `public final class`, `public class`, `public enum`, `public protocol`, and `public func` (including methods in `public extension` blocks).

**Excluded:**
- All test sources (`*Test.kt`, `src/*Test/`, `src/test/`, `*_test.go`)
- Build and generated outputs (`build/`, `.build.nosync/`, `generated/`, `.gradle/`, `.kotlin/`)
- Non-public (`private`, `internal`, `protected`, unexported Go, `fileprivate`/`private` Swift) declarations

---

## `symbols.json` Schema (`1.0`)

```json
{
  "schema_version": "1.0",
  "stats": {
    "go_files": 0,
    "go_packages": 0,
    "go_symbols": 0,
    "kmp_modules": 0,
    "kotlin_files": 0,
    "kotlin_symbols": 0,
    "swift_files": 0,
    "swift_symbols": 0,
    "swift_targets": 0,
    "total_symbols": 0
  },
  "modules": {
    "<kmp-module>": {
      "dependencies": ["..."],
      "gradle_file": "kmp/<kmp-module>/build.gradle.kts",
      "layer": "0 | 1 | 2 | 3 | bom",
      "symbol_count": 0,
      "targets": ["jvm", "..."]
    }
  },
  "go_packages": {
    "<pkg-rel-path>": {
      "files": 0,
      "import_path": "github.com/abeta-dev/kmp/bff/<pkg-rel-path>",
      "internal_imports": ["..."],
      "layer": "cmd | handler | usecase | domain | infra | pkg",
      "package_name": "...",
      "symbol_count": 0
    }
  },
  "symbols": {
    "go": [{"file": "...", "kind": "...", "line": 1, "name": "...", "package": "..."}],
    "kotlin": [{"file": "...", "kind": "...", "line": 1, "module": "...", "name": "...", "source_set": "..."}],
    "swift": [{"file": "...", "kind": "...", "line": 1, "name": "...", "target": "..."}]
  }
}
```
