package com.darkssh.client.util

/**
 * Sanitizes free-text fields (hostname, nickname, username, ...) that come from
 * on-screen keyboards, autocomplete/autocorrect, or paste.
 *
 * Some mobile keyboards (Gboard, MIUI keyboard, etc.) and clipboard sources can
 * silently insert invisible Unicode characters around or inside otherwise
 * innocent-looking text - a non-breaking space, a zero-width space, a leftover
 * BOM from a copy-pasted value, a word joiner. These are visually indistinguishable
 * from the "clean" text in a text field or a log line, but they break exact/strict
 * parsers.
 *
 * The concrete bug this fixes (bug-012): [java.net.InetAddress.getAllByName] only
 * treats a string as an IPv4 literal (skipping DNS entirely) when it exactly matches
 * digits and dots, nothing else. A hostname field containing "192.168.50.45" plus one
 * invisible character fails that strict check, so Android falls through to a real DNS
 * lookup for what is actually a literal IP - which then fails with a confusing
 * `UnknownHostException: No address associated with hostname` / `EAI_NODATA`, even
 * though the IP itself is perfectly valid and reachable.
 */
object TextSanitizer {
    /**
     * Zero-width / non-visible Unicode characters known to get inserted by mobile
     * keyboards, autocomplete, or paste, without producing any visible gap.
     */
    private val INVISIBLE_CHARS =
        charArrayOf(
            '\uFEFF', // BOM / zero-width no-break space
            '\u200B', // zero-width space
            '\u200C', // zero-width non-joiner
            '\u200D', // zero-width joiner
            '\u2060', // word joiner
            '\u00A0', // non-breaking space (renders as a normal-looking space)
        )

    /**
     * Trims regular leading/trailing whitespace, then strips embedded invisible
     * characters anywhere in the string (not just at the edges - e.g. a stray
     * zero-width space between two octets of an IP would otherwise survive a
     * plain [String.trim]).
     */
    fun sanitize(value: String): String {
        val trimmed = value.trim()
        if (trimmed.none { it in INVISIBLE_CHARS }) return trimmed
        return trimmed.filterNot { it in INVISIBLE_CHARS }
    }
}
