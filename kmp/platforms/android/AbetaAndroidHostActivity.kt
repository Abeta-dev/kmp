package dev.abeta.kmp.platforms.android

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import dev.abeta.kmp.atoms.atoms.LocalImageRenderer
import dev.abeta.kmp.atoms.atoms.UiImageRenderer
import dev.abeta.kmp.layout.di.createSduiKoinModule
import dev.abeta.kmp.layout.engine.LayoutController
import dev.abeta.kmp.layout.platform.PlatformBridgeBundle
import dev.abeta.kmp.layout.platform.asUiHapticCallback
import dev.abeta.kmp.layout.snippet.SnippetRegistry
import dev.abeta.kmp.layout.ui.SduiScreen
import dev.abeta.kmp.tokens.ColorTokens
import dev.abeta.kmp.tokens.DefaultMotionTokens
import dev.abeta.kmp.tokens.DesignTheme
import dev.abeta.kmp.tokens.DesignTokenBundle
import dev.abeta.kmp.tokens.ReducedMotionTokens
import dev.abeta.kmp.tokens.ThemePreset
import dev.abeta.kmp.tokens.resolveColorTokens
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.dsl.koinApplication

/**
 * Drop-in reference Android [FragmentActivity] host (`compileSdk >= 35`) wiring edge-to-edge
 * insets (`enableEdgeToEdge()`), predictive Back (`OnBackPressedCallback`), Material 3 dynamic
 * & dark theme + reduced-motion accessibility preferences, deep links, runtime permissions,
 * and [AndroidPlatformBridgeFactory] into [DesignTheme] + [SduiScreen].
 *
 * Copy into an Android Gradle application module targeting `compileSdk >= 35`.
 */
class AbetaAndroidHostActivity : FragmentActivity() {
    private val backStack = ArrayDeque<String>(listOf(DEFAULT_SCREEN_ID))
    private val pendingDeepLinkFlow = MutableStateFlow<String?>(null)
    private var activeController: LayoutController? = null
    private var pendingPermissionResult: CompletableDeferred<Boolean>? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingPermissionResult?.complete(granted)
            pendingPermissionResult = null
        }

    private val predictiveBackCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                popBackStack(steps = 1)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, predictiveBackCallback)

        val bffBaseUrl =
            intent?.getStringExtra(EXTRA_BFF_BASE_URL)?.trim()?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_ANDROID_BFF_URL
        val initialScreenId =
            intent?.getStringExtra(EXTRA_SCREEN_ID)?.trim()?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_SCREEN_ID
        backStack.clear()
        backStack.addLast(initialScreenId)
        pendingDeepLinkFlow.value = intent?.dataString

        val platformBridges =
            AndroidPlatformBridgeFactory.create(
                activity = this,
                permissionRequester = { androidPermission, _ ->
                    requestRuntimePermission(androidPermission)
                },
            )

        setContent {
            AndroidSduiRoot(
                bffBaseUrl = bffBaseUrl,
                initialScreenId = initialScreenId,
                platformBridges = platformBridges,
                deepLinkFlow = pendingDeepLinkFlow,
                onControllerReady = { controller -> activeController = controller },
                onNavigate = ::navigateToScreen,
                onBackRequested = ::popBackStack,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let { uri ->
            pendingDeepLinkFlow.value = uri
        }
    }

    private suspend fun requestRuntimePermission(permission: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pendingPermissionResult?.cancel()
        pendingPermissionResult = deferred
        permissionLauncher.launch(permission)
        return deferred.await()
    }

    private fun navigateToScreen(
        route: String,
        clearBackStack: Boolean,
    ) {
        val targetId =
            route
                .trim()
                .removePrefix("/screens/")
                .removePrefix("/")
                .ifEmpty { DEFAULT_SCREEN_ID }
        if (clearBackStack) {
            backStack.clear()
        }
        if (backStack.lastOrNull() != targetId) {
            backStack.addLast(targetId)
        }
        predictiveBackCallback.isEnabled = backStack.size > 1
        activeController?.loadScreen(targetId)
    }

    private fun popBackStack(steps: Int) {
        repeat(steps.coerceAtLeast(1)) {
            if (backStack.size > 1) {
                backStack.removeLast()
            }
        }
        predictiveBackCallback.isEnabled = backStack.size > 1
        val destination = backStack.lastOrNull() ?: DEFAULT_SCREEN_ID
        activeController?.loadScreen(destination)
    }

    companion object {
        const val EXTRA_BFF_BASE_URL: String = "bff_base_url"
        const val EXTRA_SCREEN_ID: String = "screen_id"
        private const val DEFAULT_ANDROID_BFF_URL: String = "http://10.0.2.2:8080"
        private const val DEFAULT_SCREEN_ID: String = "store-home"
    }
}

@Composable
@Suppress("LongParameterList")
private fun AndroidSduiRoot(
    bffBaseUrl: String,
    initialScreenId: String,
    platformBridges: PlatformBridgeBundle,
    deepLinkFlow: MutableStateFlow<String?>,
    onControllerReady: (LayoutController) -> Unit,
    onNavigate: (route: String, clearBackStack: Boolean) -> Unit,
    onBackRequested: (steps: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val koinApp =
        remember(bffBaseUrl, platformBridges) {
            koinApplication {
                modules(
                    createSduiKoinModule(
                        baseUrl = bffBaseUrl,
                        scope = scope,
                        platformBridges = platformBridges,
                        navigationListener = { nav -> onNavigate(nav.route, nav.clearBackStack) },
                        backListener = { back -> onBackRequested(back.steps) },
                    ),
                )
            }
        }
    DisposableEffect(koinApp) {
        onDispose { koinApp.close() }
    }

    val controller = remember(koinApp) { koinApp.koin.get<LayoutController>() }
    val snippetRegistry = remember(koinApp) { koinApp.koin.get<SnippetRegistry>() }
    val imageRenderer = remember(koinApp) { koinApp.koin.get<UiImageRenderer>() }
    val uiState by controller.uiState.collectAsState()
    val pendingDeepLink by deepLinkFlow.collectAsState()

    LaunchedEffect(controller) {
        onControllerReady(controller)
        controller.observeConnectivity()
    }
    LaunchedEffect(controller, initialScreenId) {
        if (pendingDeepLink == null) {
            controller.loadScreen(initialScreenId)
        }
    }
    LaunchedEffect(controller, pendingDeepLink) {
        val uri = pendingDeepLink ?: return@LaunchedEffect
        controller.handleDeepLink(uri)
        deepLinkFlow.value = null
    }

    val capabilities = platformBridges.capabilities
    val darkTheme = isSystemInDarkTheme() || capabilities.isDarkModePreferred
    val reducedMotion = capabilities.isReducedMotionPreferred
    val tokens =
        remember(context, darkTheme, reducedMotion) {
            DesignTokenBundle(
                colors = resolveAndroidAdaptiveColors(context = context, darkTheme = darkTheme),
                motion = if (reducedMotion) ReducedMotionTokens else DefaultMotionTokens,
            )
        }

    DesignTheme(
        darkTheme = darkTheme,
        reducedMotion = reducedMotion,
        tokens = tokens,
    ) {
        CompositionLocalProvider(LocalImageRenderer provides imageRenderer) {
            SduiScreen(
                state = uiState,
                snippetRegistry = snippetRegistry,
                onAction = controller::onAction,
                onFormValueChange = controller::onFormFieldChanged,
                onRetry = { controller.retry() },
                onDismissOverlay = controller::dismissOverlay,
                onDismissToast = controller::dismissToast,
                onDismissFeedback = controller::dismissFeedback,
                onSnippetImpression = controller::onSnippetImpression,
                onLoadMore = { controller.loadMore() },
                onHaptic = platformBridges.hapticEngine.asUiHapticCallback(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun resolveAndroidAdaptiveColors(
    context: Context,
    darkTheme: Boolean,
): ColorTokens {
    val base = resolveColorTokens(preset = ThemePreset.INDIGO, darkTheme = darkTheme)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return base
    }
    val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return base.copy(
        background = base.background.copy(primary = dynamic.background),
        surface =
            base.surface.copy(
                primary = dynamic.surface,
                secondary = dynamic.surfaceVariant,
                brand = dynamic.primary,
                brandSubtle = dynamic.primaryContainer,
            ),
        brand =
            base.brand.copy(
                primary = dynamic.primary,
                onPrimary = dynamic.onPrimary,
                secondary = dynamic.secondary,
                onSecondary = dynamic.onSecondary,
                container = dynamic.primaryContainer,
                onContainer = dynamic.onPrimaryContainer,
            ),
        button =
            base.button.copy(
                primaryBackground = dynamic.primary,
                primaryForeground = dynamic.onPrimary,
            ),
    )
}
