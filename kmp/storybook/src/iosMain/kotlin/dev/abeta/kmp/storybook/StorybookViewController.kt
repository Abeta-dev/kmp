package dev.abeta.kmp.storybook

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Creates a self-contained iOS [UIViewController] hosting the Abeta KMP [StorybookApp].
 */
fun createStorybookViewController(): UIViewController = ComposeUIViewController { StorybookApp() }
