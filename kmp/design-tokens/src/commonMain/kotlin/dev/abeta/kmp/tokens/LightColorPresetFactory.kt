package dev.abeta.kmp.tokens

import androidx.compose.ui.graphics.Color

private const val LIGHT_SCRIM_ALPHA = 0.45f

internal fun createIndigoLightColorTokens(): ColorTokens =
    createLightPresetColorTokens(
        brandPrimary = DesignPalette.Indigo600,
        brandOnPrimary = DesignPalette.White,
        brandSecondary = DesignPalette.Emerald700,
        brandOnSecondary = DesignPalette.White,
        brandContainer = DesignPalette.Indigo50,
        brandOnContainer = DesignPalette.Indigo700,
        brandTextAndFocus = DesignPalette.Indigo600,
        accent = DesignPalette.Emerald700,
    )

@Suppress("LongParameterList")
internal fun createLightPresetColorTokens(
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandSecondary: Color,
    brandOnSecondary: Color,
    brandContainer: Color,
    brandOnContainer: Color,
    brandTextAndFocus: Color,
    accent: Color,
): ColorTokens {
    val brand =
        BrandColors(
            primary = brandPrimary,
            onPrimary = brandOnPrimary,
            secondary = brandSecondary,
            onSecondary = brandOnSecondary,
            container = brandContainer,
            onContainer = brandOnContainer,
            accent = accent,
            onAccent = DesignPalette.White,
        )
    return buildLightTokens(brand = brand, focus = brandTextAndFocus)
}

private fun buildLightTokens(
    brand: BrandColors,
    focus: Color,
): ColorTokens =
    ColorTokens(
        isDark = false,
        background =
            BackgroundColors(
                primary = DesignPalette.Slate50,
                secondary = DesignPalette.Slate100,
                tertiary = DesignPalette.Slate200,
                inverse = DesignPalette.Slate900,
            ),
        surface =
            SurfaceColors(
                primary = DesignPalette.White,
                secondary = DesignPalette.Slate50,
                elevated = DesignPalette.White,
                inverse = DesignPalette.Slate900,
                brand = brand.primary,
                brandSubtle = brand.container,
            ),
        brand = brand,
        border = lightBorders(focus),
        text = lightTexts(brand, focus),
        icon = lightIcons(brand, focus),
        button = lightButtons(brand, focus),
        input = lightInputs(focus),
        tag = lightTags(brand),
        status = lightStatuses(),
        overlay =
            OverlayColors(
                scrim = DesignPalette.Black.copy(alpha = LIGHT_SCRIM_ALPHA),
                tooltipBackground = DesignPalette.Slate900,
                tooltipForeground = DesignPalette.White,
            ),
        chart =
            ChartColors(
                series1 = DesignPalette.ChartCobaltLight,
                series2 = DesignPalette.ChartAmberLight,
                series3 = DesignPalette.ChartTealLight,
                series4 = DesignPalette.ChartVermillionLight,
                series5 = DesignPalette.ChartSkyLight,
            ),
    )

private fun lightBorders(focus: Color): BorderColors =
    BorderColors(
        subtle = DesignPalette.Slate200,
        default = DesignPalette.Slate300,
        strong = DesignPalette.Slate400,
        focus = focus,
        error = DesignPalette.Error600,
        divider = DesignPalette.Slate200,
    )

private fun lightTexts(
    brand: BrandColors,
    focus: Color,
): TextColors =
    TextColors(
        primary = DesignPalette.Slate900,
        secondary = DesignPalette.Slate600,
        tertiary = DesignPalette.Slate500,
        disabled = DesignPalette.Slate400,
        inverse = DesignPalette.White,
        brand = focus,
        onBrand = brand.onPrimary,
        success = DesignPalette.Success700,
        warning = DesignPalette.Warning700,
        error = DesignPalette.Error700,
        info = DesignPalette.Info700,
    )

private fun lightIcons(
    brand: BrandColors,
    focus: Color,
): IconColors =
    IconColors(
        primary = DesignPalette.Slate900,
        secondary = DesignPalette.Slate600,
        tertiary = DesignPalette.Slate400,
        disabled = DesignPalette.Slate300,
        inverse = DesignPalette.White,
        brand = focus,
        onBrand = brand.onPrimary,
        success = DesignPalette.Success600,
        warning = DesignPalette.Warning600,
        error = DesignPalette.Error600,
        info = DesignPalette.Info600,
    )

private fun lightButtons(
    brand: BrandColors,
    focus: Color,
): ButtonColors =
    ButtonColors(
        primaryBackground = brand.primary,
        primaryForeground = brand.onPrimary,
        secondaryBackground = brand.secondary,
        secondaryForeground = brand.onSecondary,
        outlineBorder = DesignPalette.Slate300,
        ghostForeground = focus,
        destructiveBackground = DesignPalette.Error600,
        destructiveForeground = DesignPalette.White,
        disabledBackground = DesignPalette.Slate200,
        disabledForeground = DesignPalette.Slate500,
    )

private fun lightInputs(focus: Color): InputColors =
    InputColors(
        background = DesignPalette.White,
        foreground = DesignPalette.Slate900,
        placeholder = DesignPalette.Slate500,
        border = DesignPalette.Slate300,
        focusBorder = focus,
        errorBorder = DesignPalette.Error600,
        disabledBackground = DesignPalette.Slate100,
    )

private fun lightTags(brand: BrandColors): TagColors =
    TagColors(
        neutralBackground = DesignPalette.Slate100,
        neutralForeground = DesignPalette.Slate700,
        brandBackground = brand.container,
        brandForeground = brand.onContainer,
        successBackground = DesignPalette.Success50,
        successForeground = DesignPalette.Success700,
        warningBackground = DesignPalette.Warning50,
        warningForeground = DesignPalette.Warning700,
        errorBackground = DesignPalette.Error50,
        errorForeground = DesignPalette.Error700,
        infoBackground = DesignPalette.Info50,
        infoForeground = DesignPalette.Info700,
    )

private fun lightStatuses(): StatusColors =
    StatusColors(
        success = DesignPalette.Success600,
        successSubtle = DesignPalette.Success50,
        warning = DesignPalette.Warning600,
        warningSubtle = DesignPalette.Warning50,
        error = DesignPalette.Error600,
        errorSubtle = DesignPalette.Error50,
        info = DesignPalette.Info600,
        infoSubtle = DesignPalette.Info50,
    )
