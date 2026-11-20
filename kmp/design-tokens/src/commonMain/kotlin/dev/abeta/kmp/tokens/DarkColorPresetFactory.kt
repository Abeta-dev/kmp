package dev.abeta.kmp.tokens

import androidx.compose.ui.graphics.Color

private const val DARK_SCRIM_ALPHA = 0.65f

internal fun createIndigoDarkColorTokens(): ColorTokens =
    createDarkPresetColorTokens(
        brandPrimary = DesignPalette.Indigo600,
        brandOnPrimary = DesignPalette.White,
        brandSecondary = DesignPalette.Emerald400,
        brandOnSecondary = DesignPalette.Slate950,
        brandContainer = DesignPalette.Indigo950,
        brandOnContainer = DesignPalette.Indigo300,
        brandTextAndFocus = DesignPalette.Indigo400,
        accent = DesignPalette.Emerald400,
    )

@Suppress("LongParameterList")
internal fun createDarkPresetColorTokens(
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
            onAccent = DesignPalette.Slate950,
        )
    return buildDarkTokens(brand = brand, focus = brandTextAndFocus)
}

private fun buildDarkTokens(
    brand: BrandColors,
    focus: Color,
): ColorTokens =
    ColorTokens(
        isDark = true,
        background =
            BackgroundColors(
                primary = DesignPalette.Slate950,
                secondary = DesignPalette.Slate900,
                tertiary = DesignPalette.Slate800,
                inverse = DesignPalette.Slate50,
            ),
        surface =
            SurfaceColors(
                primary = DesignPalette.Slate900,
                secondary = DesignPalette.Slate800,
                elevated = DesignPalette.Slate800,
                inverse = DesignPalette.Slate50,
                brand = brand.primary,
                brandSubtle = brand.container,
            ),
        brand = brand,
        border = darkBorders(focus),
        text = darkTexts(brand, focus),
        icon = darkIcons(brand, focus),
        button = darkButtons(brand, focus),
        input = darkInputs(focus),
        tag = darkTags(brand),
        status = darkStatuses(),
        overlay =
            OverlayColors(
                scrim = DesignPalette.Black.copy(alpha = DARK_SCRIM_ALPHA),
                tooltipBackground = DesignPalette.Slate100,
                tooltipForeground = DesignPalette.Slate900,
            ),
        chart =
            ChartColors(
                series1 = DesignPalette.ChartCobaltDark,
                series2 = DesignPalette.ChartAmberDark,
                series3 = DesignPalette.ChartTealDark,
                series4 = DesignPalette.ChartVermillionDark,
                series5 = DesignPalette.ChartSkyDark,
            ),
    )

private fun darkBorders(focus: Color): BorderColors =
    BorderColors(
        subtle = DesignPalette.Slate800,
        default = DesignPalette.Slate700,
        strong = DesignPalette.Slate600,
        focus = focus,
        error = DesignPalette.Error400,
        divider = DesignPalette.Slate800,
    )

private fun darkTexts(
    brand: BrandColors,
    focus: Color,
): TextColors =
    TextColors(
        primary = DesignPalette.Slate50,
        secondary = DesignPalette.Slate300,
        tertiary = DesignPalette.Slate400,
        disabled = DesignPalette.Slate600,
        inverse = DesignPalette.Slate950,
        brand = focus,
        onBrand = brand.onPrimary,
        success = DesignPalette.Success400,
        warning = DesignPalette.Warning400,
        error = DesignPalette.Error400,
        info = DesignPalette.Info400,
    )

private fun darkIcons(
    brand: BrandColors,
    focus: Color,
): IconColors =
    IconColors(
        primary = DesignPalette.Slate50,
        secondary = DesignPalette.Slate300,
        tertiary = DesignPalette.Slate500,
        disabled = DesignPalette.Slate600,
        inverse = DesignPalette.Slate950,
        brand = focus,
        onBrand = brand.onPrimary,
        success = DesignPalette.Success400,
        warning = DesignPalette.Warning400,
        error = DesignPalette.Error400,
        info = DesignPalette.Info400,
    )

private fun darkButtons(
    brand: BrandColors,
    focus: Color,
): ButtonColors =
    ButtonColors(
        primaryBackground = brand.primary,
        primaryForeground = brand.onPrimary,
        secondaryBackground = brand.secondary,
        secondaryForeground = brand.onSecondary,
        outlineBorder = DesignPalette.Slate700,
        ghostForeground = focus,
        destructiveBackground = DesignPalette.Error600,
        destructiveForeground = DesignPalette.White,
        disabledBackground = DesignPalette.Slate800,
        disabledForeground = DesignPalette.Slate500,
    )

private fun darkInputs(focus: Color): InputColors =
    InputColors(
        background = DesignPalette.Slate900,
        foreground = DesignPalette.Slate50,
        placeholder = DesignPalette.Slate400,
        border = DesignPalette.Slate700,
        focusBorder = focus,
        errorBorder = DesignPalette.Error400,
        disabledBackground = DesignPalette.Slate800,
    )

private fun darkTags(brand: BrandColors): TagColors =
    TagColors(
        neutralBackground = DesignPalette.Slate800,
        neutralForeground = DesignPalette.Slate200,
        brandBackground = brand.container,
        brandForeground = brand.onContainer,
        successBackground = DesignPalette.Success950,
        successForeground = DesignPalette.Success400,
        warningBackground = DesignPalette.Warning950,
        warningForeground = DesignPalette.Warning400,
        errorBackground = DesignPalette.Error950,
        errorForeground = DesignPalette.Error400,
        infoBackground = DesignPalette.Info950,
        infoForeground = DesignPalette.Info400,
    )

private fun darkStatuses(): StatusColors =
    StatusColors(
        success = DesignPalette.Success400,
        successSubtle = DesignPalette.Success950,
        warning = DesignPalette.Warning400,
        warningSubtle = DesignPalette.Warning950,
        error = DesignPalette.Error400,
        errorSubtle = DesignPalette.Error950,
        info = DesignPalette.Info400,
        infoSubtle = DesignPalette.Info950,
    )
