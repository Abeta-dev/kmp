---
applyTo: "bff/**/*.go"
---

# Go 1.26.0 SDUI BFF Guidelines (`abeta-dev/kmp`)

- Load `agents/owners/bff-owner.md` and `skills/abeta-kmp-bff/SKILL.md`.
- Maintain strict Clean Architecture: `internal/handler -> internal/usecase -> internal/domain <- internal/infra`; `internal/domain` must never import Gin or `net/http` (`arch_test.go`).
- Keep 1:1 wire parity with `kmp/sdui-contracts` and `kmp/action-engine` (`15` atoms, `14` snippets, `18` actions, `MotionSpec`, `FloatingCartSpec`, `AdaptivePageSpec`) and update `bff/testdata/golden/*.json` + `bff/internal/handler/openapi.go` on schema changes.
- Keep screen builders separated in `bff/internal/infra/screen_<name>.go` and snippet builders split by domain in `bff/pkg/sdui/snippets/`.
- Run `(cd bff && test -z "$(gofmt -l .)" && go vet ./... && go test -count=1 -race -cover ./...)`.
