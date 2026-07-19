package com.darkssh.client.util

/**
 * Sanitizes free-text fields (hostname, nickname, username, ...) that come from
 * on-screen keyboards, autocomplete/autocorrect, or paste.
 *
 * Some mobile keyboards (Gboard, MIUI keyboard, etc.) and clipboard sources can
 * silently insert invisible Unicode characters around or inside otherwise
 * innocent-looking text - a non-breaking space, a zero-width space, a directional
 * mark, a leftover BOM from a copy-pasted value, a soft hyphen, a variation
 * selector. These are visually indistinguishable from "clean" text in a text
 * field or a log line, but they break exact/strict parsers.
 *
 * The concrete bug this fixes (bug-012): [java.net.InetAddress.getAllByName] only
 * treats a string as an IPv4/IPv6 literal (skipping DNS entirely) when it exactly
 * matches its strict numeric-format parser. A hostname field containing
 * "192.168.50.45" plus ONE extra character of basically any kind - not just a
 * specific "known" invisible character - fails that strict check, so Android falls
 * through to a real DNS lookup for what is actually a literal, reachable IP. That
 * lookup then fails with a confusing `UnknownHostException: No address associated
 * with hostname` / `EAI_NODATA`.
 *
 * First iteration of this fix (see git history) filtered a small hand-picked list
 * of "known" invisible characters (BOM, ZWSP, ZWNJ, ZWJ, word joiner, NBSP). That
 * was NOT enough: verified empirically (see bug-012 evidence in feature_list.json)
 * that at least 10 more character kinds an Android keyboard/autocorrect/paste flow
 * can realistically insert - LRM/RLM directional marks, soft hyphen, line/paragraph
 * separators, ideographic space, emoji variation selectors, embedded literal
 * tab/CR/LF - ALL reproduce the exact same failure, and a real user hit one of them
 * in the wild (a hand-picked blacklist can never be complete). This version instead
 * classifies by Unicode *general category*, which covers the whole class of
 * "invisible junk" characters regardless of which one shows up, plus a strict
 * whitelist pass for fields (hostname) that can never legitimately contain
 * anything but the small, well-known network-address charset.
 */
object TextSanitizer {
    /** Anything outside this charset can never be part of a real hostname/IPv4/IPv6 literal. */
    private val HOSTNAME_DISALLOWED = Regex("[^A-Za-z0-9.\\-:_\\[\\]]")

    /**
     * Light cleanup for free-text fields that may legitimately contain regular
     * spaces (e.g. a host nickname like "My Home Server"): trims the edges, then
     * removes/normalizes characters whose Unicode general category marks them as
     * invisible junk that no keyboard should be inserting on purpose:
     * - CONTROL / FORMAT / LINE_SEPARATOR / PARAGRAPH_SEPARATOR: always dropped,
     *   never meaningful in a single-line text field (zero-width space/joiner,
     *   BOM, directional marks, soft hyphen, embedded tab/CR/LF, ...).
     * - SPACE_SEPARATOR other than a genuine ' ' (U+0020): normalized to a plain
     *   space instead of dropped, so a non-breaking or ideographic space a
     *   keyboard meant as a word separator doesn't silently glue two words
     *   together.
     */
    fun sanitize(value: String): String {
        val trimmed = value.trim()
        val cleaned =
            buildString(trimmed.length) {
                for (c in trimmed) {
                    if (c == ' ') {
                        append(c)
                        continue
                    }
                    when (Character.getType(c)) {
                        Character.SPACE_SEPARATOR.toInt() -> {
                            append(' ')
                        }

                        Character.CONTROL.toInt(),
                        Character.FORMAT.toInt(),
                        Character.LINE_SEPARATOR.toInt(),
                        Character.PARAGRAPH_SEPARATOR.toInt(),
                        -> {
                            // dropped: never legitimate in a single-line field
                        }

                        else -> {
                            append(c)
                        }
                    }
                }
            }
        // Normalizing an edge NBSP/ideographic-space to ' ' can re-expose a
        // trailing/leading space; trim once more.
        return cleaned.trim()
    }

    /**
     * Strict cleanup for fields that must be a plain network hostname or
     * IPv4/IPv6 literal (never contains spaces, punctuation beyond `.`/`-`/`:`,
     * or non-ASCII text once IDN names are punycode-encoded, which is the only
     * form this app or the SSH stack ever sends over the wire). Applies
     * [sanitize] first, then strips ANY remaining character outside the
     * hostname/IP charset - a whitelist, so it's correct even for invisible
     * characters nobody has specifically hit yet, not just the ones tested so
     * far.
     */
    fun sanitizeStrict(value: String): String = HOSTNAME_DISALLOWED.replace(sanitize(value), "")
}
