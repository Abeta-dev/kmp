# Canonical Engineering Pattern Index

This directory is Stage 4 of the repository's **Mandatory Grounding Order Before Generation**:

```text
AGENTS.md -> docs/architecture.md -> docs/flows/*.md -> docs/patterns/*.md -> docs/codegraph/symbols.json -> Source Reads Only for Verification
```

Follow these step-by-step recipes whenever extending SDUI contracts, adding actions or OS bridges, consuming `bff/pkg/sdui/*` from downstream Go services, or writing multiplatform KMP/Apple/Room 3 code.

## Quick Routing Table

| Implementation Task | Pattern Document | Modules & Packages Touched |
| :--- | :--- | :--- |
| Add a new wire atom, composed node kind, or SDUI snippet with 1:1 KMP + Go BFF parity | [sdui-atom-and-snippet-pattern.md](sdui-atom-and-snippet-pattern.md) | `kmp/sdui-contracts`, `kmp/ui-atoms`, `kmp/sdui-layout`, `kmp/watch-companion`, `kmp/storybook`, `bff/pkg/sdui/{atoms,snippets}`, `bff/internal/handler/openapi_schemas.go`, `bff/testdata/golden/` |
| Add a new `ActionTypes` wire contract, OCP decoder/handler, and native OS bridge port | [ocp-action-and-bridge-pattern.md](ocp-action-and-bridge-pattern.md) | `kmp/action-engine`, `kmp/core`, `kmp/platforms/android`, `kmp/sdui-layout`, `kmp/watch-companion`, `bff/pkg/sdui/actions`, `bff/internal/infra/action_executor.go` |
| Embed `bff/pkg/sdui/{cache,server,snippets,...}` in a downstream Go service (e.g. Aegis) | [go-bff-cache-and-server-pattern.md](go-bff-cache-and-server-pattern.md) | `bff/pkg/sdui/{cache,server,snippets,actions,atoms,theming}`, `bff/internal/domain/arch_test.go` |
| Write HMPP-safe Apple 32/64-bit code, Room 3 migrations, SwiftPM/XCFramework interop, or BCV ABI/BOM updates | [kmp-multiplatform-hmpp-and-abi-pattern.md](kmp-multiplatform-hmpp-and-abi-pattern.md) | `kmp/core`, `kmp/database`, `kmp/network`, `kmp/state`, `kmp/sdui-layout`, `kmp/watch-companion`, `kmp/platforms/apple`, `kmp/kmp-bom`, `kmp/dependencyManagement` |

## Mandatory Verification Before Closing Any PR

```bash
./scripts/check_brand_hygiene.sh
./scripts/check_architecture_and_owners.sh
(cd kmp && ./gradlew apiDump check)
(cd bff && go vet ./... && go test -race -cover ./...)
```

- See [../architecture.md](../architecture.md) for the Uncle Bob Dependency Rule and [../flows/README.md](../flows/README.md) for end-to-end runtime sequence diagrams.
