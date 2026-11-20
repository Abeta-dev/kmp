package dev.abeta.kmp.tokens

/**
 * Curated multi-brand visual themes supported out-of-the-box by [DesignTheme].
 */
enum class ThemePreset(
    val displayName: String,
) {
    INDIGO("Indigo Core"),
    EMERALD("Emerald"),
    AMBER("Warm Amber"),
}

/**
 * Resolves the WCAG-calibrated [ColorTokens] palette for a given [preset] and polarity ([darkTheme]).
 */
fun resolveColorTokens(
    preset: ThemePreset = ThemePreset.INDIGO,
    darkTheme: Boolean = false,
): ColorTokens =
    when (preset) {
        ThemePreset.INDIGO -> if (darkTheme) DarkColorTokens else LightColorTokens
        ThemePreset.EMERALD -> if (darkTheme) EmeraldDarkColorTokens else EmeraldLightColorTokens
        ThemePreset.AMBER -> if (darkTheme) AmberDarkColorTokens else AmberLightColorTokens
    }

val EmeraldLightColorTokens: ColorTokens =
    createLightPresetColorTokens(
        brandPrimary = DesignPalette.Emerald700,
        brandOnPrimary = DesignPalette.White,
        brandSecondary = DesignPalette.Indigo600,
        brandOnSecondary = DesignPalette.White,
        brandContainer = DesignPalette.Emerald50,
        brandOnContainer = DesignPalette.Emerald800,
        brandTextAndFocus = DesignPalette.Emerald700,
        accent = DesignPalette.Indigo600,
    )

val EmeraldDarkColorTokens: ColorTokens =
    createDarkPresetColorTokens(
        brandPrimary = DesignPalette.Emerald400,
        brandOnPrimary = DesignPalette.Slate950,
        brandSecondary = DesignPalette.Indigo400,
        brandOnSecondary = DesignPalette.Slate950,
        brandContainer = DesignPalette.Emerald950,
        brandOnContainer = DesignPalette.Emerald200,
        brandTextAndFocus = DesignPalette.Emerald300,
        accent = DesignPalette.Indigo400,
    )

val AmberLightColorTokens: ColorTokens =
    createLightPresetColorTokens(
        brandPrimary = DesignPalette.Orange700,
        brandOnPrimary = DesignPalette.White,
        brandSecondary = DesignPalette.Slate800,
        brandOnSecondary = DesignPalette.White,
        brandContainer = DesignPalette.Orange50,
        brandOnContainer = DesignPalette.Orange700,
        brandTextAndFocus = DesignPalette.Orange700,
        accent = DesignPalette.Emerald700,
    )

val AmberDarkColorTokens: ColorTokens =
    createDarkPresetColorTokens(
        brandPrimary = DesignPalette.Orange400,
        brandOnPrimary = DesignPalette.Slate950,
        brandSecondary = DesignPalette.Emerald400,
        brandOnSecondary = DesignPalette.Slate950,
        brandContainer = DesignPalette.Orange950,
        brandOnContainer = DesignPalette.Orange300,
        brandTextAndFocus = DesignPalette.Orange300,
        accent = DesignPalette.Emerald400,
    )
