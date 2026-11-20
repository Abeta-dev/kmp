package dev.abeta.kmp.atoms.atoms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.abeta.kmp.contracts.ImageAtom

/**
 * Extensible port for network/asset image rendering in [UiImage].
 *
 * Host applications provide an implementation (e.g. Coil/Kamel) via [LocalImageRenderer]
 * without coupling `ui-atoms` to any networking library.
 */
fun interface UiImageRenderer {
    @Composable
    fun render(
        atom: ImageAtom,
        modifier: Modifier,
    )
}

/**
 * [ProvidableCompositionLocal] providing an optional [UiImageRenderer].
 */
val LocalImageRenderer: ProvidableCompositionLocal<UiImageRenderer?> =
    staticCompositionLocalOf { null }
