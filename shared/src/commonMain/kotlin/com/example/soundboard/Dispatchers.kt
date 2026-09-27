package com.example.soundboard

import kotlinx.coroutines.CoroutineDispatcher

/** Where [BoardViewModel] runs storage work by default: Dispatchers.IO where there is one. */
internal expect val defaultIoDispatcher: CoroutineDispatcher
