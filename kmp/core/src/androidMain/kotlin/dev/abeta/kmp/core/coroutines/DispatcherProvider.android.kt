@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Android I/O dispatcher backed by [Dispatchers.IO]. */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
