---
description: Run the complete abeta-dev/kmp verification gate across brand hygiene, architecture/owners, KMP, headless demo, and Go BFF
---

Execute `./scripts/verify_all.sh` and report exact observed pass/fail output across all 5 gates:
1. Brand & machine-path hygiene (`./scripts/check_brand_hygiene.sh`)
2. Architecture, Dependency Rule, BOM/ABI, KMP<->BFF wire parity & Code Owners (`./scripts/check_architecture_and_owners.sh`)
3. KMP Gradle `check` (`spotlessCheck`, `detekt`, `apiCheck`, JVM/macOS tests, iOS/watchOS test linking)
4. Sample App `--headless` smoke verification
5. Go BFF `go vet ./...` and `go test -race -cover ./...`
