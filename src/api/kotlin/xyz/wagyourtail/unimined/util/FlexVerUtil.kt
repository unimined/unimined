package xyz.wagyourtail.unimined.util

import org.jetbrains.annotations.VisibleForTesting
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Parse the given strings as freeform version strings, and compare them according to FlexVer.
 * @param b the second version string
 * @return `0` if the two versions are equal, a negative number if `a < b`, or a positive number if `a > b`
 */
fun String.compareFlexVer(b: String): Int {
    return FlexVerComparator.compare(this, b)
}

/**
 * Implements FlexVer, a SemVer-compatible intuitive comparator for free-form versioning strings as
 * seen in the wild. It's designed to sort versions like people do, rather than attempting to force
 * conformance to a rigid and limited standard. As such, it imposes no restrictions. Comparing two
 * versions with differing formats will likely produce nonsensical results (garbage in, garbage out),
 * but best effort is made to correct for basic structural changes, and versions of differing length
 * will be parsed in a logical fashion.
 *
 * @author Unascribed
 */
object FlexVerComparator {
    /**
     * Parse the given strings as freeform version strings, and compare them according to FlexVer.
     *
     * @param a the first version string
     * @param b the second version string
     * @return `0` if the two versions are equal, a negative number if `a < b`, or a positive number if `a > b`
     */
    fun compare(a: String, b: String): Int {
        val ad = decompose(a)
        val bd = decompose(b)
        for (i in 0 until max(ad.size, bd.size)) {
            val c = get(ad, i).compareTo(get(bd, i))
            if (c != 0) return c
        }
        return 0
    }


    private val NULL: VersionComponent = object : VersionComponent(IntArray(0)) {
        override fun compareTo(that: VersionComponent): Int {
            return if (that === this) 0 else -that.compareTo(this)
        }
    }

    /**
	 * Break apart a string into intuitive version components,
	 * by splitting it where a run of characters changes from numeric to non-numeric.
     *
     * @param str the version String
	 */
    @VisibleForTesting
    fun decompose(str: String): List<VersionComponent> {
        if (str.isEmpty()) return emptyList()
        var lastWasNumber = isAsciiDigit(str.codePointAt(0))
        val totalCodepoints = str.codePointCount(0, str.length)
        val accum = IntArray(totalCodepoints)
        val out: MutableList<VersionComponent> = ArrayList()
        var j = 0
        var i = 0
        while (i < str.length) {
            val cp = str.codePointAt(i)
            if (Character.charCount(cp) == 2) i++
            if (cp == '+'.code) break // remove appendices

            val number = isAsciiDigit(cp)
            if (number != lastWasNumber || (cp == '-'.code && j > 0 && accum[0] != '-'.code)) {
                out.add(createComponent(lastWasNumber, accum, j))
                j = 0
                lastWasNumber = number
            }
            accum[j] = cp
            j++
            i++
        }
        out.add(createComponent(lastWasNumber, accum, j))
        return out
    }

    private fun isAsciiDigit(cp: Int): Boolean {
        return cp >= '0'.code && cp <= '9'.code
    }

    private fun createComponent(number: Boolean, s: IntArray, j: Int): VersionComponent {
        val arr = Arrays.copyOfRange(s, 0, j)
        return if (number) {
            NumericVersionComponent(arr)
        } else if (arr.size > 1 && arr[0] == '-'.code) {
            SemVerPrereleaseVersionComponent(arr)
        } else {
            VersionComponent(arr)
        }
    }

    private fun get(li: List<VersionComponent>, i: Int): VersionComponent {
        return if (i >= li.size) NULL else li[i]
    }

    @VisibleForTesting
    open class VersionComponent(private val codepoints: IntArray) {
        fun codepoints(): IntArray {
            return codepoints
        }

        open fun compareTo(that: VersionComponent): Int {
            if (that === NULL) return 1
            val a = this.codepoints()
            val b = that.codepoints()

            for (i in 0 until min(a.size, b.size)) {
                val c1 = a[i]
                val c2 = b[i]
                if (c1 != c2) return c1 - c2
            }

            return a.size - b.size
        }

        override fun toString(): String {
            return String(codepoints, 0, codepoints.size)
        }
    }

    @VisibleForTesting
    class SemVerPrereleaseVersionComponent(codepoints: IntArray) : VersionComponent(codepoints) {
        override fun compareTo(that: VersionComponent): Int {
            if (that === NULL) return -1 // opposite order

            return super.compareTo(that)
        }
    }

    @VisibleForTesting
    class NumericVersionComponent(codepoints: IntArray) : VersionComponent(codepoints) {
        override fun compareTo(that: VersionComponent): Int {
            if (that === NULL) return 1
            if (that is NumericVersionComponent) {
                val a = removeLeadingZeroes(this.codepoints())
                val b = removeLeadingZeroes(that.codepoints())
                if (a.size != b.size) return a.size - b.size
                for (i in a.indices) {
                    val ad = a[i]
                    val bd = b[i]
                    if (ad != bd) return ad - bd
                }
                return 0
            }
            return super.compareTo(that)
        }

        private fun removeLeadingZeroes(a: IntArray): IntArray {
            if (a.size == 1) return a
            var i = 0
            val stopIdx = a.size - 1
            while (i < stopIdx && a[i] == '0'.code) {
                i++
            }
            return Arrays.copyOfRange(a, i, a.size)
        }
    }
}
