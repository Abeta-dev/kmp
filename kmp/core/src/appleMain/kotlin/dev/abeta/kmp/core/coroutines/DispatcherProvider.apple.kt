package dev.abeta.kmp.core.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Suppress("InjectDispatcher")
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
