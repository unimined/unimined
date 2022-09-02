package xyz.wagyourtail.unimined


fun main(args: Array<String>) {
    System.out.println(SemVerUtils.matches("11.1", "^10\\."))
}

@Suppress("UNUSED")
object SemVerUtils {
    private val semverPattern = Regex("^(\\d+)(\\.(\\d+)(\\.(\\d+))?)?(-(\\w+)(.(\\d+))?)?(\\+([0-9a-zA-Z-.]+))?$")
    fun isSemVer(s: String): Boolean {
        return semverPattern.matches(s)
    }

    fun matches(semVer: String, pattern: String): Boolean {
        return SemVerMatcher.matcher(pattern).matches(SemVer.create(semVer))
    }

    data class SemVer(val major: Int, val minor: Int, val patch: Int, val prerelease: String?, val build: String?) {
        /**
         * @return 1 if this is greater than other, -1 if other is greater than this, 0 if they are equal
         */
        operator fun compareTo(other: SemVer): Int {
            if (major > other.major) {
                return 1
            }
            if (major < other.major) {
                return -1
            }
            if (minor > other.minor) {
                return 1
            }
            if (minor < other.minor) {
                return -1
            }
            if (patch > other.patch) {
                return 1
            }
            return if (patch < other.patch) {
                -1
            } else preReleaseCompare(other)
        }

        fun preReleaseCompare(other: SemVer): Int {
            if (prerelease == null && other.prerelease == null) {
                return 0
            }
            if (prerelease == null) {
                return 1
            }
            if (other.prerelease == null) {
                return -1
            }
            if (prerelease == other.prerelease) {
                return 0
            }
            if (prerelease.startsWith("alpha") && !other.prerelease.startsWith("alpha")) {
                return -1
            }
            if (!prerelease.startsWith("alpha") && other.prerelease.startsWith("alpha")) {
                return 1
            }
            if (prerelease.startsWith("beta") && !other.prerelease.startsWith("beta")) {
                return -1
            }
            if (!prerelease.startsWith("beta") && other.prerelease.startsWith("beta")) {
                return 1
            }
            if (prerelease.startsWith("pre") && !other.prerelease.startsWith("pre")) {
                return -1
            }
            if (!prerelease.startsWith("pre") && other.prerelease.startsWith("pre")) {
                return 1
            }
            if (prerelease.startsWith("rc") && !other.prerelease.startsWith("rc")) {
                return -1
            }
            if (!prerelease.startsWith("rc") && other.prerelease.startsWith("rc")) {
                return 1
            }
            // compare using actual int values in prerelease string
            val pattern = Regex("(\\d+)")
            val matcher = pattern.findAll(prerelease).iterator()
            val otherMatcher = pattern.findAll(other.prerelease).iterator()
            while (matcher.hasNext() && otherMatcher.hasNext()) {
                val firstMatch = matcher.next()
                val secondMatch = otherMatcher.next()

                val thisValue: Int = firstMatch.groups[1]?.value?.toInt()!!
                val otherValue: Int = secondMatch.groups[1]?.value?.toInt()!!
                if (thisValue > otherValue) {
                    return 1
                } else if (thisValue < otherValue) {
                    return -1
                }
            }
            return 0
        }

        companion object {
            fun create(version: String): SemVer {
                val matcher = semverPattern.find(version) ?: throw IllegalArgumentException("Invalid semver string: $version")
                val major: Int = matcher.groups[1]?.value?.toInt()!!
                val minor = if (matcher.groups[3]?.value == null) 0 else matcher.groups[3]?.value?.toInt()!!
                val patch = if (matcher.groups[5]?.value == null) 0 else matcher.groups[5]?.value?.toInt()!!
                val prerelease = matcher.groups[7]?.value
                val build = matcher.groups[9]?.value
                return SemVer(major, minor, patch, prerelease, build)
            }
        }
    }

    interface SemVerMatcher {
        fun matches(version: SemVer): Boolean

        companion object {
            fun matcher(version: String): SemVerMatcher {
                if (version.matches(PrefixedRange.prefixedRangePattern)) {
                    return PrefixedRange.parse(version)
                } else if (version.matches(HyphenRange.hyphenRangePattern)) {
                    return HyphenRange.parse(version)
                } else if (version.matches(AndRange.andRangePattern)) {
                    return AndRange.parse(version)
                } else if (version.matches(OrRange.orRangePattern)) {
                    return OrRange.parse(version)
                }
                throw IllegalArgumentException("Invalid version matcher: $version")
            }
        }
    }

    data class PrefixedRange(val prefix: String, val major: String?, val minor: String?, val patch: String?) : SemVerMatcher {

        override fun matches(version: SemVer): Boolean {
            if (prefix == "*" && major == null && minor == null && patch == null) {
                return true
            }
            if (prefix == "^") {
                if (major != null) {
                    if (version.major == 0 && version.minor.toString() != minor) {
                        return false
                    }
                    return if (version.major == 0 && version.minor == 0 && version.patch.toString() != patch) {
                        false
                    } else version.major.toString() == major
                }
            }
            if (prefix == "~") {
                return if (minor != null) {
                    version.minor.toString() == minor
                } else {
                    version.major.toString() == major
                }
            }
            if (version.prerelease != null) {
                return false
            }
            val major = if (this.major == null) -2 else if (this.major == "X" || this.major == "*") -1 else
                this.major.toInt()
            val minor = if (this.minor == null) -2 else if (this.minor == "X" || this.minor == "*") -1 else
                this.minor.toInt()
            val patch = if (this.patch == null) -2 else if (this.patch == "X" || this.patch == "*") -1 else
                this.patch.toInt()
            if (prefix == "=") {
                if (version.major == major || major == -1) {
                    if (version.minor == minor || minor == -1) {
                        if (version.patch == patch || patch == -1) {
                            return true
                        }
                    }
                }
                return false
            }
            if (prefix == ">") {
                if (version.major > major) {
                    return true
                } else if (version.major == major) {
                    if (version.minor > minor) {
                        return true
                    } else if (version.minor == minor) {
                        if (version.patch > patch) {
                            return true
                        }
                    }
                }
            }
            if (prefix == "<") {
                if (version.major < major) {
                    return true
                } else if (version.major == major) {
                    if (version.minor < minor) {
                        return true
                    } else if (version.minor == minor) {
                        if (version.patch < patch) {
                            return true
                        }
                    }
                }
            }
            if (prefix == ">=") {
                if (version.major > major) {
                    return true
                } else if (version.major == major) {
                    if (version.minor > minor) {
                        return true
                    } else if (version.minor == minor) {
                        if (version.patch >= patch) {
                            return true
                        }
                    }
                }
            }
            if (prefix == "<=") {
                if (version.major < major) {
                    return true
                } else if (version.major == major) {
                    if (version.minor < minor) {
                        return true
                    } else if (version.minor == minor) {
                        if (version.patch <= patch) {
                            return true
                        }
                    }
                }
            }
            return false
        }

        companion object {
            var prefixedRangePattern = Regex("^(\\^|<=|>=|<|>|~|=)?(X|\\d+)?(\\.(\\*|X|\\d+|$)?)?(\\.(\\*|X|\\d+|$)?)?$")


            fun parse(range: String): PrefixedRange {
                val m = prefixedRangePattern.find(range) ?: throw IllegalArgumentException("Invalid version range: $range")
                val prefix: String = m.groups[1]?.value!!
                val major: String? = m.groups[2]?.value
                val minor: String? = m.groups[4]?.value
                val patch: String? = m.groups[6]?.value
                return PrefixedRange(prefix, major, minor, patch)
            }
        }
    }

    object HyphenRange {

        var hyphenRangePattern = Regex(
            "^(\\d+|X)(\\.(\\d+|X))?(\\.(\\d+|X))?\\s*-\\s*(\\d+|X)(\\.(\\d+|X))?(\\.(\\d+|X))?$"
        )

        fun parse(range: String): SemVerMatcher {
            val m = hyphenRangePattern.find(range) ?: throw IllegalArgumentException("Invalid range $range")
            return AndRange(
                PrefixedRange(">=", m.groups[1]?.value, m.groups[3]?.value, m.groups[5]?.value),
                PrefixedRange("<", m.groups[7]?.value, m.groups[9]?.value, m.groups[11]?.value)
            )
        }
    }

    data class AndRange(val first: SemVerMatcher, val second: SemVerMatcher) : SemVerMatcher {
        override fun matches(version: SemVer): Boolean {
            return first.matches(version) && second.matches(version)
        }

        companion object {
            var andRangePattern = Regex("\\S*[^-] [^-]\\S*")
            fun parse(range: String): AndRange {
                if(!andRangePattern.matches(range)) {
                    throw IllegalArgumentException("Invalid range $range")
                }
                val parts = range.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                return AndRange(
                    SemVerMatcher.matcher(parts[0]), SemVerMatcher.matcher(
                        parts[1]
                    )
                )
            }
        }
    }

    data class OrRange(val first: SemVerMatcher, val second: SemVerMatcher) : SemVerMatcher {
        override fun matches(version: SemVer): Boolean {
            return first.matches(version) || second.matches(version)
        }

        companion object {
            var orRangePattern = Regex("\\S*[^-]\\|\\|[^-]\\S*")
            fun parse(range: String): OrRange {
                if(!orRangePattern.matches(range)) {
                    throw IllegalArgumentException("Invalid range $range")
                }
                val parts = range.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                return OrRange(
                    SemVerMatcher.matcher(parts[0]), SemVerMatcher.matcher(
                        parts[1]
                    )
                )
            }
        }
    }
}