package dev.abeta.kmp.storybook

/**
 * Top-level Storybook navigation sections mirroring `@abeta.dev/react-libs`.
 */
enum class StoryCategory(
    val title: String,
) {
    FOUNDATIONS("Foundations & Docs"),
    ATOMS("UI Atoms"),
    SNIPPETS("SDUI Snippets"),
    PLAYGROUND("Interactive Playground"),
}

/**
 * Metadata descriptor for a single interactive Storybook page.
 */
data class StoryEntry(
    val id: String,
    val title: String,
    val category: StoryCategory,
    val subtitle: String,
    val description: String,
)

/**
 * Canonical registry of all stories rendered by [StorybookApp].
 */
object StoryCatalog {
    const val STORY_INTRODUCTION = "introduction"
    const val STORY_DESIGN_TOKENS = "design-tokens"
    const val STORY_BUTTON = "atom-button"
    const val STORY_TEXT_TAG = "atom-text-tag"
    const val STORY_TEXT_FIELD = "atom-text-field"
    const val STORY_MEDIA_FEEDBACK = "atom-media-feedback"
    const val STORY_CONTROLS_DATA = "atom-controls-data"
    const val STORY_SNIPPETS = "sdui-snippets"
    const val STORY_PLAYGROUND = "sdui-playground"
    const val STORY_MOTION_STORE = "motion-store"
    const val STORY_LIVE_BFF = "live-bff-explorer"
    const val STORY_RESPONSIVE_BRIDGES = "responsive-bridges"
    const val STORY_DISCOVERY_SEARCH = "discovery-search"

    val entries: List<StoryEntry> =
        listOf(
            StoryEntry(
                id = STORY_INTRODUCTION,
                title = "Introduction",
                category = StoryCategory.FOUNDATIONS,
                subtitle = "abeta-dev/kmp Design System & SDUI Kit",
                description = "Clean Architecture overview, module layers, and multiplatform targets.",
            ),
            StoryEntry(
                id = STORY_DESIGN_TOKENS,
                title = "Design Tokens",
                category = StoryCategory.FOUNDATIONS,
                subtitle = "Colors, WCAG Contrast, 17-Step Typography, Spacing & Scales",
                description = "Live specimen boards across Indigo, Emerald, and Warm Amber themes.",
            ),
            StoryEntry(
                id = STORY_BUTTON,
                title = "Button",
                category = StoryCategory.ATOMS,
                subtitle = "UiButton · Variants, Sizes, Loading & Disabled States",
                description = "Stateless ButtonAtom renderer with live prop inspector and JSON preview.",
            ),
            StoryEntry(
                id = STORY_TEXT_TAG,
                title = "Text & Tag",
                category = StoryCategory.ATOMS,
                subtitle = "UiText & UiTag · 17 Type Roles & Semantic Badges",
                description = "Typography hierarchy, text decorations, and status/brand pill tags.",
            ),
            StoryEntry(
                id = STORY_TEXT_FIELD,
                title = "Text Field",
                category = StoryCategory.ATOMS,
                subtitle = "UiTextField · Inputs, Validation & Masking",
                description = "Interactive text input with error states, max length, and keyboard kinds.",
            ),
            StoryEntry(
                id = STORY_MEDIA_FEEDBACK,
                title = "Icons, Progress & Media",
                category = StoryCategory.ATOMS,
                subtitle = "UiIcon, UiProgress, UiImage, UiDivider & UiSurfaceCard",
                description = "Built-in vector icon registry, progress indicators, and themed cards.",
            ),
            StoryEntry(
                id = STORY_CONTROLS_DATA,
                title = "Controls, Avatar & Skeleton",
                category = StoryCategory.ATOMS,
                subtitle = "UiSwitch, UiCheckbox, UiSlider, UiAvatar & UiSkeleton",
                description = "Interactive controls, monogram/URL avatars, and reduced-motion skeletons.",
            ),
            StoryEntry(
                id = STORY_SNIPPETS,
                title = "All 17 Snippets",
                category = StoryCategory.SNIPPETS,
                subtitle = "BuiltInSnippetModule · All 17 Canonical SDUI SnippetTypes",
                description = "Interactive gallery rendering all 17 SDUI snippets via SnippetRegistry.",
            ),
            StoryEntry(
                id = STORY_PLAYGROUND,
                title = "SDUI Contract Sandbox",
                category = StoryCategory.PLAYGROUND,
                subtitle = "Live Wire JSON + Atom Composition + ActionRef Console",
                description = "Explore server-driven atom contracts, trigger actions, and inspect JSON.",
            ),
            StoryEntry(
                id = STORY_MOTION_STORE,
                title = "Motion & Quick-Commerce",
                category = StoryCategory.PLAYGROUND,
                subtitle = "MotionSpec Presets · UiStepper · ProductCard · Celebration Overlay",
                description = "Interactive SDUI animation showcase with Abeta Fresh express cart and celebration.",
            ),
            StoryEntry(
                id = STORY_LIVE_BFF,
                title = "Live Go BFF Explorer",
                category = StoryCategory.PLAYGROUND,
                subtitle = "HttpClientFactory · KtorHttpTransport · RemoteScreenRepository · SduiScreen",
                description = "Stream live screens from a running Go BFF or preview modal overlays.",
            ),
            StoryEntry(
                id = STORY_RESPONSIVE_BRIDGES,
                title = "Responsive & OS Bridges",
                category = StoryCategory.PLAYGROUND,
                subtitle = "AdaptiveLayoutMetrics · Watch/Phone/iPad/Desktop · Core OS Ports",
                description = "Interactive breakpoint previewer and live OS bridge capabilities deck.",
            ),
            StoryEntry(
                id = STORY_DISCOVERY_SEARCH,
                title = "Discovery, Search & Swipe",
                category = StoryCategory.PLAYGROUND,
                subtitle = "SwipeDeck · SearchBar · ChipGroup · Live Match Celebration",
                description =
                    "Interactive swipe card stack with drag gestures, search bar query binding, and filter chips.",
            ),
        )

    /** Returns the [StoryEntry] matching [id], falling back to Introduction if missing. */
    fun findById(id: String): StoryEntry = entries.firstOrNull { it.id == id } ?: entries.first()
}
