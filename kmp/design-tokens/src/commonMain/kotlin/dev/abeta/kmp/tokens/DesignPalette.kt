package dev.abeta.kmp.tokens

import androidx.compose.ui.graphics.Color

/**
 * Raw color palette for the Design System across neutral, brand, accent, status,
 * and Okabe-Ito colorblind-accessible data-visualization scales.
 *
 * Prefer semantic roles from [ColorTokens] via [ThemeTokens.colors] rather than referencing
 * raw palette colors directly in UI components.
 */
@Suppress("MagicNumber")
object DesignPalette {
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)
    val Transparent = Color.Transparent

    // Brand Indigo
    val Indigo50 = Color(0xFFEEF2FF)
    val Indigo100 = Color(0xFFE0E7FF)
    val Indigo200 = Color(0xFFC7D2FE)
    val Indigo300 = Color(0xFFA5B4FC)
    val Indigo400 = Color(0xFF818CF8)
    val Indigo500 = Color(0xFF6366F1)
    val Indigo600 = Color(0xFF4F46E5)
    val Indigo700 = Color(0xFF4338CA)
    val Indigo800 = Color(0xFF3730A3)
    val Indigo900 = Color(0xFF312E81)
    val Indigo950 = Color(0xFF1E1B4B)

    // Brand Emerald
    val Emerald50 = Color(0xFFECFDF5)
    val Emerald100 = Color(0xFFD1FAE5)
    val Emerald200 = Color(0xFFA7F3D0)
    val Emerald300 = Color(0xFF6EE7B7)
    val Emerald400 = Color(0xFF34D399)
    val Emerald500 = Color(0xFF10B981)
    val Emerald600 = Color(0xFF059669)
    val Emerald700 = Color(0xFF047857)
    val Emerald800 = Color(0xFF065F46)
    val Emerald900 = Color(0xFF064E3B)
    val Emerald950 = Color(0xFF022C22)

    // Brand Orange / Amber Accent
    val Orange50 = Color(0xFFFFF7ED)
    val Orange100 = Color(0xFFFFEDD5)
    val Orange300 = Color(0xFFFDBA74)
    val Orange400 = Color(0xFFFB923C)
    val Orange500 = Color(0xFFF97316)
    val Orange600 = Color(0xFFEA580C)
    val Orange700 = Color(0xFFC2410C)
    val Orange950 = Color(0xFF431407)

    // Slate Neutrals
    val Slate50 = Color(0xFFF8FAFC)
    val Slate100 = Color(0xFFF1F5F9)
    val Slate200 = Color(0xFFE2E8F0)
    val Slate300 = Color(0xFFCBD5E1)
    val Slate400 = Color(0xFF94A3B8)
    val Slate500 = Color(0xFF64748B)
    val Slate600 = Color(0xFF475569)
    val Slate700 = Color(0xFF334155)
    val Slate800 = Color(0xFF1E293B)
    val Slate900 = Color(0xFF0F172A)
    val Slate950 = Color(0xFF020617)

    // Status: Success
    val Success50 = Emerald50
    val Success100 = Emerald100
    val Success400 = Emerald400
    val Success500 = Emerald500
    val Success600 = Emerald600
    val Success700 = Emerald700
    val Success900 = Emerald900
    val Success950 = Emerald950

    // Status: Warning (Amber)
    val Warning50 = Color(0xFFFFFBEB)
    val Warning100 = Color(0xFFFEF3C7)
    val Warning400 = Color(0xFFFBBF24)
    val Warning500 = Color(0xFFF59E0B)
    val Warning600 = Color(0xFFD97706)
    val Warning700 = Color(0xFFB45309)
    val Warning900 = Color(0xFF78350F)
    val Warning950 = Color(0xFF451A03)

    // Status: Error (Rose/Red)
    val Error50 = Color(0xFFFEF2F2)
    val Error100 = Color(0xFFFEE2E2)
    val Error400 = Color(0xFFF87171)
    val Error500 = Color(0xFFEF4444)
    val Error600 = Color(0xFFDC2626)
    val Error700 = Color(0xFFB91C1C)
    val Error900 = Color(0xFF7F1D1D)
    val Error950 = Color(0xFF450A0A)

    // Status: Info (Sky/Blue)
    val Info50 = Color(0xFFF0F9FF)
    val Info100 = Color(0xFFE0F2FE)
    val Info400 = Color(0xFF38BDF8)
    val Info500 = Color(0xFF0EA5E9)
    val Info600 = Color(0xFF0284C7)
    val Info700 = Color(0xFF0369A1)
    val Info900 = Color(0xFF0C4A6E)
    val Info950 = Color(0xFF082F49)

    // Okabe-Ito Colorblind-Accessible Chart Palette
    val ChartCobaltLight = Color(0xFF0072B2)
    val ChartAmberLight = Color(0xFFE69F00)
    val ChartTealLight = Color(0xFF009E73)
    val ChartVermillionLight = Color(0xFFD55E00)
    val ChartSkyLight = Color(0xFF56B4E9)

    val ChartCobaltDark = Color(0xFF4DA6FF)
    val ChartAmberDark = Color(0xFFFFB81C)
    val ChartTealDark = Color(0xFF19E6AE)
    val ChartVermillionDark = Color(0xFFFF7A33)
    val ChartSkyDark = Color(0xFF7CC8F8)
}
