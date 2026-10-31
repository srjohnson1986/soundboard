package com.example.soundboard.translate

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where a [Translator] stands. Translation is only ever a *suggestion* that fills in a tile's
 * Spanish text for the user to read and edit (#332); what is spoken is that saved text, so
 * playback never depends on any of this. Every state is something the screen can explain, so a
 * translation control never just vanishes: see [explanation].
 */
sealed interface TranslatorStatus {
    /** Still finding out what this device can do. */
    data object Checking : TranslatorStatus

    /** Translating works now, offline. */
    data object Ready : TranslatorStatus

    /** Supported, but the language model has to be downloaded first ([approximateMegabytes] of it, if known). */
    data class NeedsDownload(val approximateMegabytes: Int? = null) : TranslatorStatus

    /** The model is downloading. [percent] is null where the platform can't say how far along it is. */
    data class Downloading(val percent: Int? = null) : TranslatorStatus

    /** This device or browser can't translate, for [reason] (said on screen). Nothing here will change that. */
    data class Unavailable(val reason: String) : TranslatorStatus

    /** The last download or translation failed with [message]; trying again is allowed. */
    data class Failed(val message: String) : TranslatorStatus

    /** Whether asking for a translation can work: not [Unavailable], and not still [Checking]. */
    val canTranslate: Boolean get() = this !is Unavailable && this !is Checking

    /** Whether translating needs a download first, or one is going on. */
    val needsModel: Boolean get() = this is NeedsDownload || this is Downloading

    /** A sentence for the screen about this state; null when there's nothing to say (it works). */
    val explanation: String?
        get() = when (this) {
            Checking -> "Checking whether this device can translate..."
            Ready -> null
            is NeedsDownload -> "The first translation downloads the Spanish language model" +
                (approximateMegabytes?.let { " (about $it MB)" } ?: "") + "; after that it works offline."
            is Downloading -> "Downloading the Spanish language model" + (percent?.let { " ($it%)" } ?: "...")
            is Unavailable -> "Translating isn't available here: $reason"
            is Failed -> "Couldn't translate: $message"
        }
}

/**
 * Translates text on the device, offline once its language model is downloaded (#332). Android
 * uses ML Kit, the web Chrome's built-in Translator API; where a platform can't, its stand-in
 * says why ([UnavailableTranslator]). It is a platform capability like [com.example.soundboard.audio.Speaker]:
 * the view model only ever uses this interface.
 */
interface Translator {
    /** Where translating stands; see [TranslatorStatus]. */
    val status: StateFlow<TranslatorStatus>

    /** Downloads the language model for [target] if it isn't here yet, reporting through [status]. A no-op once it is. */
    suspend fun prepare(target: String)

    /**
     * Translates [text] from [from] (English) to [target] ("es"), downloading the model first if
     * needed. Null if it couldn't, and [status] then says why ([TranslatorStatus.Failed] or
     * [TranslatorStatus.Unavailable]).
     */
    suspend fun translate(text: String, target: String, from: String = "en"): String?
}

/**
 * The honest stand-in for a platform that can't translate (iOS, for now; a browser without the
 * Translator API): it never translates, and its [status] says why, so the controls explain
 * themselves instead of disappearing.
 */
open class UnavailableTranslator(reason: String) : Translator {
    override val status: StateFlow<TranslatorStatus> = MutableStateFlow<TranslatorStatus>(TranslatorStatus.Unavailable(reason)).asStateFlow()

    override suspend fun prepare(target: String) = Unit

    override suspend fun translate(text: String, target: String, from: String): String? = null
}
