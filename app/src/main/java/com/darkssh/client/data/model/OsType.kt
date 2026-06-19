package com.darkssh.client.data.model

/**
 * Operating system types detected from remote SSH servers.
 * Based on Termius implementation.
 */
enum class OsType(
    val displayName: String,
    vararg val keywords: String,
) {
    ARCH("Arch Linux", "arch", "arch linux"),
    UBUNTU("Ubuntu", "ubuntu"),
    DEBIAN("Debian GNU/Linux", "debian", "debian gnu/linux"),
    FEDORA("Fedora", "fedora"),
    CENTOS("CentOS Linux", "centos", "centos linux"),
    REDHAT("Red Hat", "redhat", "red hat"),
    SUSE("SUSE Linux", "suse", "suse linux", "opensuse"),
    ALPINE("Alpine", "alpine"),
    GENTOO("Gentoo Linux", "gentoo", "gentoo linux"),
    RASPBIAN("Raspbian GNU/Linux", "raspbian", "raspbian gnu/linux"),
    ALMA("AlmaLinux", "almalinux", "alma"),
    ROCKY("Rocky Linux", "rocky"),
    AMAZON("Amazon Linux", "amazon"),
    ANDROID("Android", "android"),
    FREEBSD("FreeBSD", "freebsd"),
    OPENBSD("OpenBSD", "openbsd"),
    NETBSD("NetBSD", "netbsd"),
    OSX("macOS", "darwin", "macos", "osx"),
    LINUX("Linux", "linux"),
    WINDOWS("Windows", "msys_nt", "win", "windows"),
    MIKROTIK("RouterOS", "mikrotik"),
    UNKNOWN("Unknown"),
    ;

    companion object {
        // Parse OS type from command output (e.g., uname or /etc/*release).
        // Matches keywords case-insensitively.
        fun parse(output: String): OsType {
            if (output.isBlank()) return UNKNOWN

            val normalizedOutput = output.trim().lowercase()

            // Try exact keyword matches first
            for (osType in values()) {
                if (osType == UNKNOWN) continue
                for (keyword in osType.keywords) {
                    if (normalizedOutput.contains(keyword.lowercase())) {
                        return osType
                    }
                }
            }

            // Fallback: try to extract NAME= field from /etc/*release format
            val nameMatch = Regex("NAME=\"?([^\"\\n]+)\"?", RegexOption.IGNORE_CASE).find(normalizedOutput)
            if (nameMatch != null) {
                val name = nameMatch.groupValues[1].trim()
                for (osType in values()) {
                    if (osType == UNKNOWN) continue
                    for (keyword in osType.keywords) {
                        if (name.contains(keyword.lowercase())) {
                            return osType
                        }
                    }
                }
            }

            return UNKNOWN
        }
    }
}
