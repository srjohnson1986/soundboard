package com.example.soundboard.data

/**
 * A small backup zip made outside the app (Python's zipfile, deflate-compressed, with a
 * directory entry), which every platform's [ZipCodec] must read the same way: this is what
 * keeps a backup from Android importable on the web and the other way round.
 */
object GoldenBackup {
    const val BOARD_JSON = "{\"name\":\"Golden\",\"pages\":[{\"id\":\"p\",\"name\":\"Page 1\",\"rows\":1,\"columns\":1,\"tiles\":[{\"id\":\"t\",\"label\":\"Hi\",\"fileName\":\"hi.m4a\"}]}]}"
    val SOUND_BYTES: ByteArray = ByteArray(1024) { it.toByte() }

    val zip: ByteArray = (
    "504b03041400000008008e463b5d4ec9cbca65000000810000000a000000626f6172642e6a736f6e4d8c310ac2401000bf22" +
    "532f42c0ea3ea095d84b8ad5ac7161ef2e988845b8bf5b04c16e8a9959299a8dc4b1c6600561d2d166d275c5071213f2532e" +
    "3adaae4378d5cf4cea847b8d772e1b2f1effdd82107ab32071728487879db7d1d3f7f9a0b4bef5ed0b504b03041400000008" +
    "008e463b5d00000000020000000000000007000000736f756e64732f0300504b03041400000008008e463b5d264c0bb71901" +
    "0000000400000d000000736f756e64732f68692e6d34616360646266616563e7e0e4e2e6e1e5e31710141216111513979094" +
    "92969195935750545256515553d7d0d4d2d6d1d5d33730343236313533b7b0b4b2b6b1b5b37770747276717573f7f0f4f2f6" +
    "f1f5f30f080c0a0e090d0b8f888c8a8e898d8b4f484c4a4e494d4bcfc8cccacec9cdcb2f282c2a2e292d2bafa8acaaaea9ad" +
    "ab6f686c6a6e696d6befe8eceaeee9edeb9f3071d2e42953a74d9f3173d6ec3973e7cd5fb070d1e2254b972d5fb172d5ea35" +
    "6bd7addfb071d3e62d5bb76ddfb173d7ee3d7bf7ed3f70f0d0e123478f1d3f71f2d4e93367cf9dbf70f1d2e52b57af5dbf71" +
    "f3d6ed3b77efdd7ff0f0d1e3274f9f3d7ff1f2d5eb376fdfbdfff0f1d3e72f5fbf7dfff1f3d7ef3f7ffffd6718f5ffa8ff59" +
    "47aeff01504b010214001400000008008e463b5d4ec9cbca65000000810000000a0000000000000000000000800100000000" +
    "626f6172642e6a736f6e504b010214001400000008008e463b5d000000000200000000000000070000000000000000001000" +
    "fd418d000000736f756e64732f504b010214001400000008008e463b5d264c0bb719010000000400000d0000000000000000" +
    "0000008001b4000000736f756e64732f68692e6d3461504b05060000000003000300a8000000f80100000000"
    ).chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /** Checks [codec] reads [zip] as exactly board.json and sounds/hi.m4a, directory left out. */
    fun assertReadBy(codec: ZipCodec) {
        val entries = codec.read(zip).associate { it.name to it.bytes }
        kotlin.test.assertEquals(setOf("board.json", "sounds/hi.m4a"), entries.keys)
        kotlin.test.assertEquals(BOARD_JSON, entries.getValue("board.json").decodeToString())
        kotlin.test.assertContentEquals(SOUND_BYTES, entries.getValue("sounds/hi.m4a"))
    }
}
