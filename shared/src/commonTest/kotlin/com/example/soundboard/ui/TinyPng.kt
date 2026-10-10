package com.example.soundboard.ui

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** A valid 1x1 PNG, for tests that need a tile picture that really decodes. */
@OptIn(ExperimentalEncodingApi::class)
val TINY_PNG: ByteArray = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")
