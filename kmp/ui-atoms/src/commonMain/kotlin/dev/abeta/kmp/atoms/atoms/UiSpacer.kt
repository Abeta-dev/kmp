package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.contracts.SpacerAtom

/**
 * Stateless spacer UI atom rendered from a [SpacerAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiSpacer(
    atom: SpacerAtom,
    modifier: Modifier = Modifier,
) {
    var sizedModifier = modifier
    atom.widthDp?.takeIf { it > 0 }?.let { w ->
        sizedModifier = sizedModifier.width(w.dp)
    }
    atom.heightDp?.takeIf { it > 0 }?.let { h ->
        sizedModifier = sizedModifier.height(h.dp)
    }
    Spacer(modifier = sizedModifier)
}
