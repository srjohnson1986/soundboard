package com.example.soundboard

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

// iOS has threads, so file work gets its own pool, as on Android.
internal actual val defaultIoDispatcher: CoroutineDispatcher = Dispatchers.IO
