package com.example.soundboard

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// A browser has one thread and asynchronous storage, so there's no separate I/O pool.
internal actual val defaultIoDispatcher: CoroutineDispatcher = Dispatchers.Default
