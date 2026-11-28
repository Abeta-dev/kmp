# Runtime & Cross-Stack Flow Index

This directory is Stage 3 of the repository's **Mandatory Grounding Order Before Generation**:

```text
AGENTS.md -> docs/architecture.md -> docs/flows/*.md -> docs/patterns/*.md -> docs/codegraph/symbols.json -> Source Reads Only for Verification
```

Use these four end-to-end runtime flow guides to trace how requests, state mutations, live patches, and wearable snapshots move across Kotlin Multiplatform (`kmp/`) and the Go 1.26.0 Clean Architecture BFF (`bff/`).

## Quick Routing Table

| Engineering Question | Flow Document | Key Symbols Covered |
| :--- | :--- | :--- |
| How does a screen load, validate its manifest, negotiate HTTP `304` ETags, cache locally, and fall back offline? | [screen-load-cache-and-manifest-flow.md](screen-load-cache-and-manifest-flow.md) | `LayoutController.loadScreen`, `CachedScreenRepository`, `ScreenCachePolicy`, `RemoteScreenManifestRepository`, `RemoteScreenRepository`, `server.ETagMiddleware`, `cache.UpstreamCachedScreenRepository`, `createDiskKeyValueStore`, `AbetaLocalDatabase`, `SduiScreen` |
| How do UI clicks validate form state, decode/dispatch OCP actions, run OS bridges or API calls, and queue/replay offline mutations? | [action-dispatch-and-offline-sync-flow.md](action-dispatch-and-offline-sync-flow.md) | `ActionRef`, `LayoutController.onAction`, `DefaultActionDispatcher`, `DefaultActionValidator`, `ActionDecoderRegistry`, `ActionRegistry`, `PlatformBridgeOutcomeExecutor`, `RemoteApiCallExecutor`, `CsrfTokenProvider`, `SnippetListMutator`, `OfflineActionCoordinator`, `OfflineMutationQueue` |
| How do live SSE streams connect, survive reconnects, patch nested snippets incrementally, and tear down cleanly? | [sse-streaming-and-delta-patch-flow.md](sse-streaming-and-delta-patch-flow.md) | `PageResponse.streamSpec`, `LayoutStreamController`, `KtorSseTransport`, `SseLineParser`, `bffHandler.streamDeltas`, `server.StreamProvider`, `applySnippetDeltaPatch`, `SnippetDeltaOp` |
| How do Apple (iOS/watchOS/macOS), Wear OS, and Android hosts embed SDUI screens, watch glances, and Tiles? | [wearable-and-apple-host-flow.md](wearable-and-apple-host-flow.md) | `AbetaIosRootAppView`, `AbetaIosSduiScreenView`, `createSduiViewController`, `AbetaWatchSduiView`, `WatchCompanionHostHolder`, `WearCompanionHostActivity`, `WearSduiScreen`, `WearTileSnapshotMapper`, `AbetaAndroidHostActivity`, `WatchCompanionController`, `RemoteWatchPageSource`, `WatchScreenMapper` |

## Cross-Flow Invariants

1. **Generation Guards Prevent Stale Writes**: Both `LayoutController` (`screenGeneration`) and `LayoutStreamController` (`generation`) drop in-flight screen loads, action outcomes, and SSE frames if navigation advances before completion.
2. **Layer Isolation Is Preserved End-to-End**: `ui-atoms` only emits `ActionRef` intents; `action-engine` only calls `ApiCallExecutor` ports; `watch-companion` stays 100% headless with zero Compose UI imports.
3. **1:1 Wire Parity**: Every KMP contract in `kmp/sdui-contracts` and `kmp/action-engine` has an exact Go mirror under `bff/pkg/sdui/*`, verified by `./scripts/check_architecture_and_owners.sh`.
4. **Related Guides**: See [../architecture.md](../architecture.md) for the Uncle Bob Dependency Rule and [../patterns/README.md](../patterns/README.md) for step-by-step implementation recipes.
