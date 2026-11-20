package dev.abeta.kmp.core.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Suppress("InjectDispatcher")
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
