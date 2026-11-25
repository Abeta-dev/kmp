# Antigravity Workspace Rules — `abeta-dev/kmp`

1. Always consult [AGENTS.md](../../AGENTS.md) and load the matching skill under `skills/` plus the responsible module owner persona under `agents/owners/` before modifying code.
2. Enforce the **Uncle Bob Dependency Rule** strictly:
   - Layer 0 (`core`, `state`, `analytics`, `database`) has zero dependencies on Layers 1–3.
   - Layer 1 (`design-tokens`, `sdui-contracts`, `ui-atoms`) NEVER imports `core`, `action-engine`, `network`, or `sdui-layout`.
   - Layer 2 (`action-engine`, `network`) NEVER imports `ui-atoms`, `sdui-layout`, `watch-companion`, or each other.
   - `watch-companion` stays 100% headless (zero Compose UI).
   - Go BFF `bff/internal/domain` stays pure Go (zero Gin/HTTP/infra imports).
3. Enforce **SOLID, Clean Code & KISS**: avoid monolithic files (> 750 LOC), duplicate logic, or `when (actionType)` switches outside OCP registries (`ActionRegistry`, `SnippetRegistry`).
4. Enforce **Native UI Craft (`/impeccable`)**: minimum 48dp / 44pt touch targets on all clickable controls, semantic `ThemeTokens` colors/typography (`sp`), `reducedMotion`, safe-area/IME insets, and polite `liveRegion` semantics on banners/toasts.
5. Never commit legacy brand references or machine-specific absolute paths. Run `./scripts/check_brand_hygiene.sh`, `./scripts/check_architecture_and_owners.sh`, and `./scripts/verify_all.sh` before reporting work complete.
