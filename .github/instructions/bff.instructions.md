---
applyTo: "bff/**/*.go"
---

# Go 1.26.0 SDUI BFF Guidelines (`abeta-dev/kmp`)

- Load `agents/owners/bff-owner.md` and `skills/abeta-kmp-bff/SKILL.md`.
- Maintain strict Clean Architecture: `internal/handler -> internal/usecase -> internal/domain <- internal/infra`; `internal/domain` must never import Gin or `net/http` (`arch_test.go`).
- Keep 1:1 wire parity with `kmp/sdui-contracts` and `kmp/action-engine` (`29` wire atoms, `23` snippets incl. `composed_block` w/ `36` `ComposedNodeKind`s, `custom_slot`, & `graph_canvas`, `6` `SnippetDeltaOp`s, `18` actions, `7` motion loops, `FloatingCartSpec`, `AdaptivePageSpec`) across the 8 exportable `pkg/sdui/*` packages (`bff@v0.9.0`), and update all **11** fixtures in `bff/testdata/golden/*.json` + `bff/internal/handler/openapi.go` on schema changes.
- Keep screen builders separated in `bff/internal/infra/screen_<name>.go` and snippet builders split by domain in `bff/pkg/sdui/snippets/`.
- Run `(cd bff && test -z "$(gofmt -l .)" && go vet ./... && go test -count=1 -race -cover ./...)`.
