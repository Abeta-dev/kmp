package dev.abeta.kmp.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Horizontal text or content alignment token.
 */
@Serializable
enum class AlignmentToken {
    @SerialName("start")
    START,

    @SerialName("center")
    CENTER,

    @SerialName("end")
    END,
}

/**
 * Visual style variant for [ButtonAtom].
 */
@Serializable
enum class ButtonVariant {
    @SerialName("primary")
    PRIMARY,

    @SerialName("secondary")
    SECONDARY,

    @SerialName("outline")
    OUTLINE,

    @SerialName("ghost")
    GHOST,

    @SerialName("destructive")
    DESTRUCTIVE,
}

/**
 * Size preset for [ButtonAtom].
 */
@Serializable
enum class ButtonSize {
    @SerialName("sm")
    SMALL,

    @SerialName("md")
    MEDIUM,

    @SerialName("lg")
    LARGE,
}

/**
 * Content scaling mode for [ImageAtom].
 */
@Serializable
enum class ImageScaleMode {
    @SerialName("fit")
    FIT,

    @SerialName("fill_bounds")
    FILL_BOUNDS,

    @SerialName("crop")
    CROP,

    @SerialName("inside")
    INSIDE,
}

/**
 * Visual presentation mode for [ProgressAtom].
 */
@Serializable
enum class ProgressKind {
    @SerialName("linear")
    LINEAR,

    @SerialName("circular")
    CIRCULAR,
}

/**
 * Soft keyboard input type hint for [TextFieldAtom].
 */
@Serializable
enum class KeyboardKind {
    @SerialName("text")
    TEXT,

    @SerialName("number")
    NUMBER,

    @SerialName("email")
    EMAIL,

    @SerialName("phone")
    PHONE,

    @SerialName("password")
    PASSWORD,

    @SerialName("decimal")
    DECIMAL,

    @SerialName("uri")
    URI,
}

/**
 * Semantic style variant for [TagAtom].
 */
@Serializable
enum class TagVariant {
    @SerialName("neutral")
    NEUTRAL,

    @SerialName("brand")
    BRAND,

    @SerialName("success")
    SUCCESS,

    @SerialName("warning")
    WARNING,

    @SerialName("error")
    ERROR,

    @SerialName("info")
    INFO,
}

/**
 * Visual placeholder shape variant for [SkeletonAtom].
 */
@Serializable
enum class SkeletonKind {
    @SerialName("text")
    TEXT,

    @SerialName("rectangle")
    RECTANGLE,

    @SerialName("circle")
    CIRCLE,
}

/**
 * Presentation style for modal overlays triggered by SDUI actions.
 */
@Serializable
enum class OverlayPresentation {
    @SerialName("dialog")
    DIALOG,

    @SerialName("bottom_sheet")
    BOTTOM_SHEET,
}

/**
 * Enter and exit transition presets for [MotionSpec].
 */
@Serializable
enum class MotionTransition {
    @SerialName("none")
    NONE,

    @SerialName("fade_in")
    FADE_IN,

    @SerialName("slide_up")
    SLIDE_UP,

    @SerialName("slide_down")
    SLIDE_DOWN,

    @SerialName("scale_in")
    SCALE_IN,

    @SerialName("spring_bounce")
    SPRING_BOUNCE,
}

/**
 * Semantic animation duration tokens for [MotionSpec].
 */
@Serializable
enum class MotionDurationToken {
    @SerialName("instant")
    INSTANT,

    @SerialName("fast")
    FAST,

    @SerialName("normal")
    NORMAL,

    @SerialName("slow")
    SLOW,
}

/**
 * Continuous ambient loop animation effects for [MotionSpec].
 */
@Serializable
enum class MotionLoopEffect {
    @SerialName("none")
    NONE,

    @SerialName("pulse")
    PULSE,

    @SerialName("shimmer")
    SHIMMER,
}

/**
 * Visual and tactile feedback effect kinds triggered by SDUI actions.
 */
@Serializable
enum class FeedbackEffect {
    @SerialName("celebration")
    CELEBRATION,

    @SerialName("cart_bounce")
    CART_BOUNCE,

    @SerialName("shake_error")
    SHAKE_ERROR,

    @SerialName("haptic_tick")
    HAPTIC_TICK,
}

/**
 * Runtime permission kind identifiers transmitted over SDUI actions.
 */
@Serializable
enum class OsPermissionKind {
    @SerialName("location")
    LOCATION,

    @SerialName("notifications")
    NOTIFICATIONS,

    @SerialName("camera")
    CAMERA,

    @SerialName("storage")
    STORAGE,
}

/**
 * Responsive page presentation mode across phones, tablets, iPads, desktops, and foldables.
 */
@Serializable
enum class ScreenLayoutMode {
    @SerialName("single_column")
    SINGLE_COLUMN,

    @SerialName("centered")
    CENTERED,

    @SerialName("split_pane")
    SPLIT_PANE,
}
