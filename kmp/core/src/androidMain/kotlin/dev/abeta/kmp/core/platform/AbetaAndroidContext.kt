package dev.abeta.kmp.core.platform

import android.content.Context
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe application [Context] holder used by Android/Wear OS platform actuals across
 * Abeta KMP Kit (`core`, `state`, and `database`).
 *
 * Host applications should call [initialize] once in `Application.onCreate()` (or early in their
 * startup initializer). When running in JVM host unit tests without an Android `Context`,
 * [applicationContextOrNull] returns `null` so platform actuals degrade gracefully.
 */
object AbetaAndroidContext {
    private val contextRef = AtomicReference<Context?>(null)

    /** Stores [context]'s application context safely for subsequent KMP platform lookups. */
    fun initialize(context: Context) {
        val appContext = runCatching { context.applicationContext }.getOrNull() ?: context
        contextRef.set(appContext)
    }

    /** Returns the initialized application [Context], or `null` when not yet initialized. */
    fun applicationContextOrNull(): Context? = contextRef.get()
}
