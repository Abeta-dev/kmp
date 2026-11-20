---
description: Run an Impeccable native iOS, Android, Wear OS & adaptive UI craft audit across design-tokens, ui-atoms, sdui-layout, platforms, and storybook
---

Review UI & native host surfaces (`kmp/design-tokens`, `kmp/ui-atoms`, `kmp/sdui-layout`, `kmp/platforms`, `kmp/storybook`, `kmp/sample-app`) across 5 dimensions (0–4 each, /20 total):
1. **Accessibility**: VoiceOver/TalkBack labels, roles, `LiveRegionMode.Polite` announcements, `48dp`/`44pt` touch targets, `sp` typography scaling, `reducedMotion`, WCAG AA contrast.
2. **Performance**: `LazyColumn`/`ScalingLazyColumn` stable keys, recomposition discipline, image caching.
3. **Appearance & Theming**: Semantic `ThemeTokens` (zero raw hex), Light + Dark first-class parity, Material 3 / Apple semantics.
4. **Platform Conformance**: Safe-area & edge-to-edge window insets (`safeDrawing`, `statusBarsPadding`, `navigationBarsPadding`, `imePadding`), predictive Back / edge-swipe preservation, native Android/iOS/Wear controls.
5. **Adaptivity**: Phone, Tablet/iPad, Foldable, Desktop, Watch size classes (`AdaptiveLayoutMetrics`, `SplitPaneBody`, `columnsByWidth`, `itemWidthByWidth`).
