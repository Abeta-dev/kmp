package dev.abeta.kmp.storybook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.abeta.kmp.atoms.atoms.UiDivider
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.DividerAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.tokens.ThemeTokens

/**
 * Foundations overview story summarizing layers, target platforms, and usage principles.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun IntroductionStory() {
    val spacing = ThemeTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
        UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            UiText(
                TextAtom(
                    text = "Welcome to Abeta KMP Storybook",
                    typography = TypographyTokenRef("headline.medium"),
                    color = ColorTokenRef("text.primary"),
                ),
            )
            UiText(
                TextAtom(
                    text =
                        "An interactive multiplatform component catalog built strictly on " +
                            "design-tokens, sdui-contracts, and stateless ui-atoms.",
                    typography = TypographyTokenRef("body.medium"),
                    color = ColorTokenRef("text.secondary"),
                ),
            )
            Row(
                modifier = Modifier.padding(top = spacing.space8),
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                UiTag(TagAtom(label = "Kotlin 2.4.21", variant = TagVariant.BRAND))
                UiTag(TagAtom(label = "Compose 1.12.2", variant = TagVariant.SUCCESS))
                UiTag(TagAtom(label = "Desktop + Wasm + Mobile", variant = TagVariant.INFO))
            }
        }

        ArchitectureLayersCard()
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun ArchitectureLayersCard() {
    val spacing = ThemeTokens.spacing
    val layers =
        listOf(
            "Layer 0 · Foundation" to
                "core, state, analytics, database — zero UI dependencies, Room 3 SQLite, offline queue",
            "Layer 1 · Design & Contracts" to "design-tokens, sdui-contracts, ui-atoms — stateless atoms & tokens",
            "Layer 2 · Capabilities" to "action-engine, network — OCP action handlers, OkHttp HTTP/2 + Brotli",
            "Layer 3 · Orchestration" to
                "sdui-layout, watch-companion, storybook, sample-app — SDUI engine, wearables & live catalogs",
        )
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(
            TextAtom(
                text = "Strict Dependency Rule",
                typography = TypographyTokenRef("title.large"),
            ),
        )
        UiDivider(DividerAtom(), modifier = Modifier.padding(vertical = spacing.space8))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
            layers.forEach { (title, desc) ->
                Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                    UiText(
                        TextAtom(
                            text = title,
                            typography = TypographyTokenRef("label.large"),
                            color = ColorTokenRef("text.brand"),
                        ),
                    )
                    UiText(
                        TextAtom(
                            text = desc,
                            typography = TypographyTokenRef("body.small"),
                            color = ColorTokenRef("text.secondary"),
                        ),
                    )
                }
            }
        }
    }
}
