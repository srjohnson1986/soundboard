@file:JsModule("fflate")

package com.example.soundboard.data

// fflate (https://github.com/101arrowz/fflate), a small, dependency-free zip library.

/** Zips an object mapping each entry's path to its `Uint8Array`; returns the archive. */
internal external fun zipSync(data: JsAny): JsAny

/** Unzips an archive into an object mapping each entry's path to its `Uint8Array`; throws if it isn't one. */
internal external fun unzipSync(data: JsAny): JsAny
