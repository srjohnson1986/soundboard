package com.example.soundboard.data

import kotlinx.serialization.json.Json

/**
 * The one JSON setup every on-disk file shares (board.json, saved presets, recent presets).
 * `ignoreUnknownKeys` lets an older build read a file a newer one wrote; `encodeDefaults`
 * writes every field out so the file reads the same whatever the defaults later become.
 */
internal val BoardJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}
