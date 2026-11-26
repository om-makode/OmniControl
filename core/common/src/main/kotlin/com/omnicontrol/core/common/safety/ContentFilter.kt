package com.omnicontrol.core.common.safety

/**
 * Detects adult / explicit / abusive words in commands, search text and URLs.
 * Used for two things:
 *  - voice: a transcription containing these words is treated as "misheard" and never executed;
 *  - actions: OmniControl will not search for, type, or open explicit content.
 */
object ContentFilter {

    // Whole words only (so "Essex", "classic", "cocktail", "Scunthorpe" are fine).
    private val EXPLICIT_WORDS = listOf(
        "sex", "sexy", "sexual", "porn", "porno", "pornhub", "xxx", "xvideos", "xnxx", "xhamster", "redtube",
        "nude", "nudes", "naked", "nsfw", "hentai", "onlyfans", "boobs", "boob", "tits", "titties", "nipple",
        "nipples", "pussy", "dick", "cock", "penis", "vagina", "blowjob", "handjob", "orgasm", "masturbate",
        "masturbation", "horny", "milf", "escort", "escorts", "stripper", "camgirl", "erotic", "fetish",
        "bdsm", "anal", "cum", "cumshot",
        // abuse
        "fuck", "fucking", "fucker", "motherfucker", "bitch", "bastard", "slut", "whore", "asshole",
        // common transliterated abuse
        "chutiya", "chutiye", "bhenchod", "behenchod", "madarchod", "bhosdi", "bhosdike", "randi", "gandu", "lund"
    )

    private val PATTERN = Regex(
        "(?<![a-z])(" + EXPLICIT_WORDS.joinToString("|") { Regex.escape(it) } + ")(?![a-z])",
        RegexOption.IGNORE_CASE
    )

    /** Domains / URL parts that are adult by name even without a listed word. */
    private val URL_PARTS = listOf("porn", "xxx", "xvideos", "xnxx", "xhamster", "redtube", "onlyfans", "hentai", "nsfw", "sex")

    fun containsExplicit(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return PATTERN.containsMatchIn(text.replace('_', ' ').replace('-', ' '))
    }

    fun isExplicitUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val u = url.lowercase()
        return URL_PARTS.any { u.contains(it) } || containsExplicit(u.replace('.', ' ').replace('/', ' '))
    }
}
