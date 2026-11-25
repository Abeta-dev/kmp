# SDUI Wire Protocol (`v1`)

`abeta-dev/kmp` uses a **versioned JSON wire protocol (`v1`)** shared **1:1** between `kmp/sdui-contracts` + `kmp/action-engine` (`kotlinx.serialization`, `SduiJson`) and `bff/pkg/sdui/{actions,atoms,snippets,theming}` (Go 1.26.0, `encoding/json`).

All HTTP API responses from the Go BFF are wrapped in `ginmw.Envelope` (`BffEnvelope<T>` on KMP, `WatchBffEnvelope<T>` in `watch-companion`). Every schema change must preserve `snake_case` field names and pass golden parity tests against `bff/testdata/golden/`.

---

## Protocol Hierarchy

```mermaid
flowchart LR
    Env["ginmw.Envelope / BffEnvelope (success, data, error)"] --> Page["PageResponse"]
    Page --> Toolbar["ToolbarSpec"]
    Page --> Snippets["snippets: List of SnippetEnvelope"]
    Page --> BottomBar["BottomBarSpec"]
    Page --> FloatingCart["FloatingCartSpec (label, eta_badge, cta, motion, free_delivery_threshold_cents, nudge_prefix, unlocked_label)"]
    Page --> Pagination["PaginationSpec (next_cursor, has_more, load_more_action)"]
    Page --> Adaptive["AdaptivePageSpec (layout_mode, max_width_dp, split_pane_enabled, secondary_snippet_ids)"]
    Page --> PullRefresh["pull_to_refresh: ActionRef"]
    Page --> PageActions["page_actions: List of ActionRef"]
    Page --> Tracking["TrackingContract"]
    Page --> Versioning["page_version, app_version, etag, stream_spec (6 SnippetDeltaOps)"]
    Snippets --> SnippetData["23 Canonical SnippetTypes (incl. composed_block w/ 36 ComposedNodeKinds, custom_slot, graph_canvas)"]
    SnippetData --> Atoms["29 Wire Atoms + LayoutConfig + MotionSpec (7 MotionLoopEffects)"]
    Atoms --> Tokens["ColorTokenRef / TypographyTokenRef"]
    Atoms --> ActionRef["ActionRef / ActionTrigger (18 Built-in ActionTypes)"]
    ActionRef --> Validations["validations: List of ValidationRuleContract"]
    ActionRef --> Secondary["secondary_actions: List of ActionRef"]
```

---

## 1. HTTP Envelope (`ginmw.Envelope` / `BffEnvelope<T>`)

Every `/api/v1/*` endpoint returns:

```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

On failure, `success` is `false` and `error` contains `{ "code": "...", "message": "...", "detail": "..." }` (`BffApiError` in `kmp/sdui-layout/.../BffModels.kt`).

---

## 2. `PageResponse`, `AdaptivePageSpec`, `FloatingCartSpec` & `PaginationSpec`

Returned inside `data` by `GET /api/v1/screens/:screenId`:

| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `page_id` | `string` | Yes | Stable screen identifier (12 built-in BFF screens: `"home"`, `"catalog"`, `"containers"`, `"profile"`, `"form-demo"`, `"fresh-home"`, `"store-home"`, `"watch-status"`, `"pay-home"`, `"pay-transfer"`, `"pay-cards"`, `"pay-receipt"`, plus additional 7-in-1 Super-App vertical screens in `sample-app`, `storybook`, and `Abeta-dev/sdui-bff`) |
| `title` | `string?` | No | Screen title |
| `toolbar` | `ToolbarSpec?` | No | Top app bar (`title`, `subtitle`, `navigation_icon`, `trailing_actions`, `bg_color`, `layout`) |
| `snippets` | `SnippetEnvelope[]` | No | Ordered list of screen snippets (defaults to `[]`) |
| `bottom_bar` | `BottomBarSpec?` | No | Persistent bottom action bar (`primary_button`, `secondary_button`, `helper_text`, `layout`, `motion`) |
| `floating_cart` | `FloatingCartSpec?` | No | Persistent floating cart summary bar (`label`, `eta_badge`, `cta`, `motion`, `free_delivery_threshold_cents`, `nudge_prefix`, `unlocked_label`) rendered by `FloatingCartDockHost` |
| `page_actions` | `ActionRef[]` | No | Lifecycle actions dispatched when the page loads (defaults to `[]`) |
| `tracking` | `TrackingContract?` | No | Page-level analytics event (`event_name`, `properties`) |
| `pagination` | `PaginationSpec?` | No | Cursor pagination metadata (`next_cursor`, `has_more`, `load_more_action`) |
| `pull_to_refresh` | `ActionRef?` | No | Optional action dispatched on pull-to-refresh (`PullToRefreshBox`) |
| `adaptive_layout` | `AdaptivePageSpec?` | No | Server-driven responsive viewport rules (`layout_mode`, `max_width_dp`, `split_pane_enabled`, `secondary_snippet_ids`) |
| `page_version` | `string?` | No | Deterministic SHA-256 content version used by `VERSION_GATED` caching & `/api/v1/screens/manifest` |
| `app_version` | `string?` | No | Server `APP_VERSION` stamp for cache invalidation across app/server upgrades |
| `etag` | `string?` | No | Optional page-level entity tag honored by `server.ETagMiddleware` & `CachedScreenRepository` |
| `stream_spec` | `SseStreamSpec?` | No | Optional SSE delta stream configuration (`stream_id`, `endpoint`, `reconnect_policy`) delivering `SnippetDeltaPatch` updates across **6 `SnippetDeltaOp`s** (`append_text`, `patch_snippet_data`, `replace_snippet`, `insert_snippet`, `remove_snippet`, `update_page_meta`) |

### `AdaptivePageSpec` & Responsive Breakpoint Specs (`AdaptiveContracts.kt` / `pkg/sdui/snippets`)

| Type | Wire Values / Fields (`snake_case`) |
| :--- | :--- |
| **`ScreenLayoutMode`** | `"single_column"` \| `"centered"` \| `"split_pane"` |
| **`AdaptivePageSpec`** | `layout_mode` (`ScreenLayoutMode`, default `"centered"`), `max_width_dp` (`int?`), `split_pane_enabled` (`boolean`, default `false`), `secondary_snippet_ids` (`string[]`) |
| **`ResponsiveColumnsSpec`** | `compact` (`int?`), `medium` (`int?`), `expanded` (`int?`) — attached via `GridSnippetData.columns_by_width` |
| **`ResponsiveIntSpec`** | `compact` (`int?`), `medium` (`int?`), `expanded` (`int?`) — attached via `CarouselSnippetData.item_width_by_width` |

### `FloatingCartSpec`

| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `label` | `string` | Yes | Fallback summary label (live subtotal & item count are computed dynamically by `PageCartCalculator` when `product_card` snippets exist) |
| `eta_badge` | `TagAtom?` | No | Optional express delivery ETA badge (e.g., `"8 MINS"`) |
| `cta` | `ButtonAtom` | Yes | Checkout call-to-action button |
| `motion` | `MotionSpec?` | No | Declarative enter/exit/loop motion applied to `FloatingCartDockHost` |
| `free_delivery_threshold_cents` | `int?` | No | Optional cart subtotal threshold in cents for live free-delivery progress bar & unlock bounce |
| `nudge_prefix` | `string?` | No | Prefix text shown while below threshold (e.g., `"Add"`) |
| `unlocked_label` | `string?` | No | Celebration text shown once subtotal meets/exceeds `free_delivery_threshold_cents` |

### `PaginationSpec`

| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `next_cursor` | `string?` | No | Opaque cursor token for fetching the subsequent page |
| `has_more` | `boolean` | No | `true` when additional pages are available (defaults to `false`) |
| `load_more_action` | `ActionRef?` | No | Action dispatched when incremental loading is requested (`LayoutController.loadMore()`) |

---

## 3. `SnippetEnvelope` & All 23 Canonical `SnippetTypes`

Every section in `PageResponse.snippets` is wrapped in `SnippetEnvelope`:

| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `snippet_id` | `string` | Yes | Unique instance ID within the page |
| `snippet_parent_id` | `string?` | No | Optional parent container ID (e.g., inside `carousel`, `grid`, `tabs`, or `accordion`) |
| `snippet_type` | `string` | Yes | Discriminator matching `SnippetTypes` |
| `sticky` | `boolean` | No | Pins the snippet via `LazyColumn` `stickyHeader` (e.g., pinned search/filter bars) |
| `data` | `JsonObject` | No | Typed snippet payload decoded by `SnippetRegistry` |
| `tracking` | `TrackingContract?` | No | Impression analytics (`event_name`, `properties` — **string values only**) |
| `motion` | `MotionSpec?` | No | Optional declarative enter/stagger/loop animation spec applied by `SnippetEntry` |

### All 23 Canonical Snippet Types (`SnippetTypes`)

| `snippet_type` | Kotlin Contract (`sdui-contracts`) | Purpose |
| :--- | :--- | :--- |
| `header_v1` | `HeaderSnippetData` | Section/page header with `title`, `subtitle`, `leading_icon`, `avatar` (`AvatarAtom?`), `trailing_tag`, `trailing_action`, `trailing_icon`, `skeletons` (`SkeletonAtom[]`), `on_click`, `layout` |
| `banner_v1` | `BannerSnippetData` | Highlighted banner with `title`, `body`, `icon`, `image`, `tag`, `cta`, `dismiss_action`, `on_click`, `layout` |
| `metric_card` | `MetricCardSnippetData` | KPI card with `label`, `value`, `delta`, `caption`, `icon`, `progress`, `on_click`, `layout` |
| `card_v1` | `CardSnippetData` | Content card with `title`, `subtitle`, `description`, `media`, `avatar` (`AvatarAtom?`), `skeletons` (`SkeletonAtom[]`), `badge`, `leading_icon`, `primary_button`, `secondary_button`, `on_click`, `layout` |
| `action_row` | `ActionRowSnippetData` | Clickable row with `title`, `subtitle`, `leading_icon`, `trailing_tag`, `trailing_icon`, `on_click`, `layout` |
| `form_v1` | `FormSnippetData` | Interactive form with `form_id`, `title`, `description`, `fields` (`TextFieldAtom[]`), `switches` (`SwitchAtom[]`), `checkboxes` (`CheckboxAtom[]`), `sliders` (`SliderAtom[]`), `submit_button`, `cancel_button`, `layout` |
| `empty_state` | `EmptyStateSnippetData` | Zero-data placeholder with `title`, `message`, `icon`, `image`, `action_button`, `layout` |
| `divider` | `DividerSnippetData` | Horizontal separator wrapping `divider` (`DividerAtom`) and `layout` |
| `spacer` | `SpacerSnippetData` | Spacing element wrapping `spacer` (`SpacerAtom`) |
| `product_card` | `ProductCardSnippetData` | Quick-commerce product card with `product_id`, `title`, `subtitle`, `price`, `original_price`, `unit_price_cents`, `badge`, `image`, `stepper` (`StepperAtom`), `rating`, `on_click`, `layout` |
| `carousel` | `CarouselSnippetData` | Horizontal scrolling container with `title`, `items` (`SnippetEnvelope[]`), `item_width_dp`, `item_width_by_width` (`ResponsiveIntSpec?`), `spacing_dp`, `layout` |
| `grid` | `GridSnippetData` | Multi-column container with `title`, `columns` (default `2`), `columns_by_width` (`ResponsiveColumnsSpec?`), `spacing_dp`, `items` (`SnippetEnvelope[]`), `layout` |
| `tabs` | `TabsSnippetData` | Tabbed container with `tabs` (`TabItemSpec[]`) and `selected_index`, `layout`. Each `TabItemSpec` holds `id` (`string`), `title` (`string`, matching Go BFF `TabItemSpec.Title`), `badge` (`TagAtom?`), and `snippets` (`SnippetEnvelope[]`) |
| `accordion` | `AccordionSnippetData` | Collapsible container with `title`, `subtitle`, `initially_expanded` (default `false`), `snippets` (`SnippetEnvelope[]`), `layout` |
| `swipe_deck` | `SwipeDeckSnippetData` | Stacked swipe card deck with `deck_id`, `cards` (`SwipeCardItem[]`), `like_label`, `nope_label`, `super_like_label`, `empty_title`, `empty_message`, `on_like`, `on_nope`, `on_super_like`, `on_undo`, `layout` |
| `search_bar` | `SearchBarSnippetData` | Query-bound search input with `search_id`, `query`, `placeholder`, `leading_icon`, `trailing_icon`, `submit_label`, `clearable`, `on_submit`, `on_clear`, `on_trailing_click`, `layout` |
| `chip_group` | `ChipGroupSnippetData` | Single- or multi-select filter strip with `group_id`, `title`, `multi_select`, `chips` (`FilterChipItem[]`), `layout` |
| `nudge_bar` | `NudgeBarSnippetData` | Urgency/contextual nudge bar with `nudge_id`, `title`, `subtitle`, `icon`, `badge`, `countdown`, `progress`, `cta`, `dismissible`, `pointer_edge` (`top`\|`bottom`), `on_dismiss`, `on_click`, `layout` |
| `marquee_strip` | `MarqueeStripSnippetData` | Continuous horizontal ticker with `strip_id`, `items` (`MarqueeItem[]`), `velocity_dp_per_sec` (default `48`), `separator` (default `"•"`), `layout` |
| `order_tracker` | `OrderTrackerSnippetData` | Animated multi-stage tracker with `tracker_id`, `order_code`, `headline`, `eta_countdown`, `eta_badge`, `rider_name`, `rider_caption`, `steps` (`OrderTrackerStep[]`), `primary_action`, `secondary_action`, `layout` |
| `composed_block` | `ComposedBlockSnippetData` | **Atomic Snippet Molding Engine**: recursive server-molded `root` (`ComposedNode`, max depth 6) supporting **36 `ComposedNodeKind`s** (`column`, `row`, `box`, `card`, `flow_row`, `scroll_row`, `grid`, `text`, `button`, `tag`, `icon`, `image`, `avatar`, `progress`, `divider`, `spacer`, `stepper`, `countdown`, `rolling_counter`, `text_field`, `switch`, `checkbox`, `slider`, `media_pager`, `radio_group`, `select`, `otp_input`, `chip`, `segmented_control`, `rating`, `price`, `badge`, `alert_banner`, `stat_tile`, `key_value_row`, `qr_code`) with per-child `box_alignment`, `block_id`, `recipe_name`, `layout` |
| `custom_slot` | `CustomSlotSnippetData` | Universal host extension bridge (`slot_id`, `slot_type`, `props`, optional `fallback` `SnippetEnvelope`) resolved via `CustomSlotRegistry` / `LocalCustomSlotRegistry` |
| `graph_canvas` | `GraphCanvasSnippetData` | Interactive spatial node-edge canvas (`canvas_id`, `title`, `nodes` (`GraphNodeSpec[]`), `edges` (`GraphEdgeSpec[]` w/ `bezier`\|`straight`\|`dashed`), `viewport_height_dp`, `interactive`, `on_node_tap`, `on_node_move`, `layout`) |

Unknown `snippet_type` entries are safely handled with an unknown-snippet fallback in `SnippetRegistry` without crashing the screen (`SduiJson` sets `ignoreUnknownKeys = true`).

---

## 4. Token References & `LayoutConfig`

### `ColorTokenRef`
```json
{
  "token": "text.primary",
  "alpha": 0.9,
  "fallback_hex": "#111318",
  "hex": null
}
```
Fields: `token` (`string?`), `alpha` (`float?`), `fallback_hex` (`string?`), `hex` (`string?`).

### `TypographyTokenRef`
```json
{
  "token": "title.large",
  "font_weight": "bold",
  "letter_spacing": 0.15
}
```
Fields: `token` (`string`), `font_weight` (`string?`), `letter_spacing` (`double?`).

### `LayoutConfig`
Shared box layout configuration on atoms, snippets, `ToolbarSpec`, and `BottomBarSpec`:

- `padding`: `SpacingValues` (`top`, `bottom`, `start`, `end`, `all`, `horizontal`, `vertical`)
- `margin`: `SpacingValues`
- `bg_color`: `ColorTokenRef`
- `corner_radius`: `CornerRadiusSpec` (`token`, `all`, `top_start`, `top_end`, `bottom_start`, `bottom_end`)
- `border`: `BorderSpec` (`width_dp`, `width_token`, `color`)
- `fill_max_width`: `boolean`

---

## 5. Motion & Feedback Wire Contracts (`MotionContracts.kt`, `AtomEnums.kt`, `pkg/sdui/atoms`)

`MotionSpec` attaches optional declarative entrance/exit transitions, stagger delays, and **7 ambient loop effects** to **`ButtonAtom`**, **`TagAtom`**, **`BadgeAtom`**, **`CountdownAtom`**, **`ComposedNode`**, **`SnippetEnvelope`**, **`BottomBarSpec`**, and **`FloatingCartSpec`**. In `kmp/ui-atoms`, `MotionModifier.kt` + `MotionLoopModifiers.kt` (`applyMotionSpec`, `Modifier.pressBounce`, `Modifier.shakeOnError`, `resolveMotionDurationMs`, `resolveMotionDelayMs`, `resolveEnterTransition`, `resolveExitTransition`) collapse all animations when `ThemeTokens.motion.reducedMotion` is `true`.

| Type | Wire Values / Fields (`snake_case`) |
| :--- | :--- |
| **`MotionSpec`** | `enter` (`MotionTransition`), `exit` (`MotionTransition`), `duration_token` (`MotionDurationToken?`), `duration_ms` (`int?`), `stagger_ms` (`int`, default `0`), `delay_ms` (`int`, default `0`), `loop_effect` (`MotionLoopEffect`) |
| **`MotionTransition`** | `"none"` \| `"fade_in"` \| `"slide_up"` \| `"slide_down"` \| `"scale_in"` \| `"spring_bounce"` |
| **`MotionDurationToken`** | `"instant"` \| `"fast"` \| `"normal"` \| `"slow"` |
| **`MotionLoopEffect`** | `"none"` + **7 ambient loops**: `"pulse"` \| `"shimmer"` \| `"wiggle"` \| `"float"` \| `"heartbeat"` \| `"glow_ring"` \| `"marquee"` |
| **`FeedbackEffect`** | `"celebration"` \| `"cart_bounce"` \| `"shake_error"` \| `"haptic_tick"` |

---

## 6. All 29 Wire Atoms (`Atoms.kt`, `ExtendedAtoms.kt` & `pkg/sdui/atoms`)

| Atom | Key Fields | Action Triggers |
| :--- | :--- | :--- |
| **`TextAtom`** | `id`, `text`, `color`, `typography`, `max_lines`, `alignment` (`start`\|`center`\|`end`), `markdown`, `strike_through`, `underline`, `prefix_icon`, `suffix_icon`, `layout` | `on_click` (`ActionRef`) |
| **`IconAtom`** | `id`, `name`, `size_dp`, `color`, `content_description`, `layout` | `on_click` (`ActionRef`) |
| **`TagAtom`** | `id`, `label`, `variant` (`neutral`\|`brand`\|`success`\|`warning`\|`error`\|`info`), `bg_color`, `text_color`, `border`, `icon`, `layout`, `motion` (`MotionSpec?`) | `on_click` (`ActionRef`) |
| **`ButtonAtom`** | `id`, `label`, `sub_label`, `variant` (`primary`\|`secondary`\|`outline`\|`ghost`\|`destructive`), `size` (`sm`\|`md`\|`lg`), `enabled`, `loading`, `full_width`, `leading_icon`, `trailing_icon`, `tracking`, `layout`, `motion` (`MotionSpec?`) | `on_click` (`ActionRef`) |
| **`ImageAtom`** | `id`, `url`, `content_description`, `width_dp`, `height_dp`, `aspect_ratio`, `scale_mode` (`fit`\|`fill_bounds`\|`crop`\|`inside`), `corner_radius`, `placeholder_color`, `layout` | `on_click` (`ActionRef`) |
| **`TextFieldAtom`** | `field_id`, `label`, `placeholder`, `initial_value`, `helper_text`, `error_text`, `enabled`, `read_only`, `single_line`, `max_lines`, `max_length`, `required`, `regex`, `keyboard_type` (`text`\|`number`\|`email`\|`phone`\|`password`\|`decimal`\|`uri`), `prefix_icon`, `suffix_icon`, `layout` | `on_value_change`, `on_submit` (`ActionRef`) |
| **`DividerAtom`** | `id`, `thickness_dp`, `color`, `inset_start_dp`, `layout` | None |
| **`SpacerAtom`** | `id`, `width_dp`, `height_dp`, `weight` | None |
| **`ProgressAtom`** | `id`, `kind` (`linear`\|`circular`), `progress`, `label`, `color`, `track_color`, `layout` | None |
| **`SwitchAtom`** | `field_id`, `label`, `subtitle`, `checked`, `enabled`, `layout` | `on_change` (`ActionRef`) |
| **`CheckboxAtom`** | `field_id`, `label`, `subtitle`, `checked`, `enabled`, `required`, `layout` | `on_change` (`ActionRef`) |
| **`SliderAtom`** | `field_id`, `label`, `value`, `min_value`, `max_value`, `steps`, `show_value`, `unit_label`, `enabled`, `layout` | `on_change` (`ActionRef`) |
| **`AvatarAtom`** | `id`, `url`, `initials`, `size_dp`, `bg_color`, `text_color`, `content_description`, `layout` | `on_click` (`ActionRef`) |
| **`SkeletonAtom`** | `id`, `kind` (`SkeletonKind`: `text`\|`rectangle`\|`circle`), `width_dp`, `height_dp`, `layout` | None |
| **`StepperAtom`** | `field_id`, `label`, `quantity`, `min_quantity` (default `0`), `max_quantity` (default `20`), `enabled`, `layout` | `on_increment`, `on_decrement` (`ActionRef`) |
| **`CountdownAtom`** | `countdown_id`, `remaining_seconds`, `expires_at_epoch_ms`, `label`, `urgent_threshold_seconds` (default `60`), `show_hours`, `expired_text` (default `"00:00"`), `motion` (`MotionSpec?`) | `on_expire` (`ActionRef`) |
| **`RadioGroupAtom`** | `field_id`, `label`, `options` (`OptionItem[]`), `selected_id`, `enabled`, `layout` | `on_change` (`ActionRef`) |
| **`SelectAtom`** | `field_id`, `label`, `placeholder`, `options` (`OptionItem[]`), `selected_id`, `helper_text`, `error_text`, `enabled`, `layout` | `on_change` (`ActionRef`) |
| **`OtpInputAtom`** | `field_id`, `label`, `length` (default `6`), `initial_value`, `masked`, `helper_text`, `error_text`, `enabled`, `layout` | `on_complete` (`ActionRef`) |
| **`ChipAtom`** | `id`, `label`, `selected`, `enabled`, `removable`, `leading_icon`, `layout` | `on_click`, `on_remove` (`ActionRef`) |
| **`SegmentedControlAtom`** | `field_id`, `options` (`OptionItem[]`), `selected_id`, `enabled`, `layout` | `on_change` (`ActionRef`) |
| **`RatingAtom`** | `id`, `field_id`, `value`, `max_stars` (default `5`), `interactive`, `review_count`, `label`, `star_size_dp` (default `20`), `layout` | `on_rate` (`ActionRef`) |
| **`PriceAtom`** | `id`, `amount_text`, `original_amount_text`, `discount_label`, `unit_suffix`, `emphasized`, `layout` | None |
| **`BadgeAtom`** | `id`, `count`, `max_count` (default `99`), `label`, `dot_only`, `variant` (`TagVariant`), `layout`, `motion` (`MotionSpec?`) | None |
| **`AlertBannerAtom`** | `id`, `title`, `message`, `variant` (`TagVariant`), `icon`, `action_label`, `dismissible`, `layout` | `on_action`, `on_dismiss` (`ActionRef`) |
| **`StatTileAtom`** | `id`, `label`, `value`, `delta_label`, `delta_positive`, `caption`, `icon`, `layout` | `on_click` (`ActionRef`) |
| **`KeyValueRowAtom`** | `id`, `label`, `value`, `sublabel`, `value_color`, `emphasized`, `show_divider`, `layout` | `on_click` (`ActionRef`) |
| **`QrCodeAtom`** | `id`, `payload`, `label`, `caption`, `size_dp` (default `160`), `layout` | `on_click` (`ActionRef`) |
| **`MediaPagerAtom`** | `id`, `images` (`ImageAtom[]`), `aspect_ratio`, `height_dp`, `show_indicators`, `auto_advance_ms`, `layout` | `on_page_click` (`ActionRef`) |

> Note: `kmp/ui-atoms` renders all **29 wire atoms** plus `UiSurfaceCard` and `UiRollingCounter`, giving **31 stateless Compose UI atoms** in total (incl. real ISO/IEC 18004 `UiQrCode`, plus `UiCelebrationOverlay`, `UiHapticFeedback.kt`, `MotionModifier.kt`, and `MotionLoopModifiers.kt`).

---

## 7. `ActionRef` / `ActionTrigger` & All 18 Action Types

In KMP (`sdui-contracts`) this contract is `ActionRef`; in Go (`pkg/sdui/actions`) it is `ActionTrigger`:

```json
{
  "id": "submit_workspace_form",
  "type": "call_api",
  "payload": {
    "endpoint": "/api/v1/actions/execute",
    "method": "POST",
    "body": { "form_id": "workspace_onboarding" }
  },
  "validations": [
    {
      "field_id": "work_email",
      "rule": "regex",
      "value": "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$",
      "message": "Enter a valid email address"
    }
  ],
  "secondary_actions": [
    {
      "id": "track_form_submit",
      "type": "track_event",
      "payload": { "event_name": "workspace_form_submitted" }
    }
  ]
}
```

### Fields on `ActionRef` / `ActionTrigger`
- `id`: `string?` (in Go `ActionTrigger.ID` is non-empty string)
- `type`: `string`
- `payload`: `JsonObject` / `json.RawMessage`
- `validations`: `ValidationRuleContract[]` — `{ "field_id", "rule", "value"?, "message" }`
- `secondary_actions`: `ActionRef[]` / `ActionTrigger[]`

### All 18 Built-In Action Types (`ActionTypes`)

| `type` | Payload Fields (`snake_case`) |
| :--- | :--- |
| `navigate` | `route`, `params` (`map<string,string>`), `clear_back_stack` (`bool`), `launch_mode` (`string?`) |
| `back_press` | `result` (`map<string,any>`), `steps` (`int`, default `1`) |
| `open_dialog` | `dialog_id`, `title`, `message`, `dismissible` (`bool`), `positive_action` (`ActionRef?`), `negative_action` (`ActionRef?`), `presentation` (`OverlayPresentation`: `"dialog"` \| `"bottom_sheet"`, default `"dialog"`) |
| `dismiss_overlay` | `overlay_id` (`string?`), `all` (`bool`) |
| `show_toast` | `message`, `duration_ms` (`long`, default `2500`), `variant` (`string`, default `"info"`) |
| `call_api` | `endpoint`, `method` (default `"POST"`), `headers`, `body` (`JsonObject?`), `on_success` (`ActionRef?`), `on_error` (`ActionRef?`) |
| `save_state` | `key`, `value` (`JsonElement`), `scope` (default `"session"`) |
| `delete_state` | `key`, `scope` (default `"session"`) |
| `track_event` | `event_name`, `properties` (`map<string,JsonElement>`) |
| `refresh_page` | `page_id` (`string?`), `show_loader` (`bool`) |
| `copy_to_clipboard` | `text`, `label` (`string?`) |
| `update_snippets` | `operation`, `target_snippet_id` (`string?`), `snippets` (`SnippetEnvelope[]`) |
| `batch` | `actions` (`ActionRef[]`), `stop_on_error` (`bool`), `parallel` (`bool`) |
| `trigger_feedback` | `effect` (`FeedbackEffect`: `"celebration"` \| `"cart_bounce"` \| `"shake_error"` \| `"haptic_tick"`), `title` (`string?`), `subtitle` (`string?`), `duration_ms` (`long`, default `1800`), `target_id` (`string?`) |
| `open_url` | `url` (`string`), `external_browser` (`bool`, default `true`), `fallback_route` (`string?`) |
| `share` | `title` (`string?`), `text` (`string`), `url` (`string?`), `chooser_title` (`string?`) |
| `request_permission` | `permission` (`OsPermissionKind`: `"location"` \| `"notifications"` \| `"camera"` \| `"storage"`), `rationale` (`string?`), `on_granted` (`ActionRef?`), `on_denied` (`ActionRef?`) |
| `biometric_auth` | `reason` (`string`), `title` (`string?`), `allow_device_credential` (`bool`, default `true`), `on_authenticated` (`ActionRef?`), `on_failed` (`ActionRef?`) |

---

## Golden Schema Parity Fixtures

Canonical `v1` wire fixtures (**11 fixtures**) live in `bff/testdata/golden/`:

- `bff/testdata/golden/home_screen.json`
- `bff/testdata/golden/form_screen.json`
- `bff/testdata/golden/action_batch.json`
- `bff/testdata/golden/containers_screen.json`
- `bff/testdata/golden/store_home.json`
- `bff/testdata/golden/interactive_screen.json`
- `bff/testdata/golden/nudges_and_composed_screen.json`
- `bff/testdata/golden/extended_atoms_screen.json`
- `bff/testdata/golden/express_molecules_and_analytics_screen.json`
- `bff/testdata/golden/streaming_and_versioned_screen.json`
- `bff/testdata/golden/spatial_custom_and_graph_screen.json`

Both `bff/internal/infra/golden*_test.go` and `kmp/sdui-contracts/src/jvmTest/kotlin/dev/abeta/kmp/contracts/GoldenFixturesParityTest.kt` (plus `SduiScreenComposeTest`) validate against these exact files to guarantee zero drift between Go and Kotlin.
