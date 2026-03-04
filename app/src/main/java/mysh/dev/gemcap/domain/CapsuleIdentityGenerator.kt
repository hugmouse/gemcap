package mysh.dev.gemcap.domain

import android.annotation.SuppressLint
import android.net.Uri
import java.util.Locale

// Identity generation ported from Lagrange:
// - src/gmdocument.c:setThemeSeed_GmDocument()
// - src/gmutil.c:urlThemeSeed_String() / urlPaletteSeed_String()
object CapsuleIdentityGenerator {

    // Lagrange: src/gmutil.c:282-287 (urlUser_String() regex patterns)
    private val userPathPattern = Regex("^/~([^/?#]+)")
    private val usersPathPattern = Regex("^/users/([^/?#]+)", RegexOption.IGNORE_CASE)

    // Lagrange: src/gmdocument.c:1549-1556 (siteIcons)
    // TODO: not all of them are being rendered correctly out of the box lmao,
    // I guess I have to now figure out how fontpacks work?
    private val siteIcons = intArrayOf(
        0x203B, 0x2042, 0x205C, 0x2182, 0x25ED, 0x2600, 0x2601, 0x2604, 0x2605, 0x2606,
        0x265C, 0x265E, 0x2690, 0x2691, 0x2693, 0x2698, 0x2699, 0x26F0, 0x270E, 0x2728,
        0x272A, 0x272F, 0x2731, 0x2738, 0x273A, 0x273E, 0x2740, 0x2742, 0x2744, 0x2748,
        0x274A, 0x2318, 0x2756, 0x2766, 0x27BD, 0x27C1, 0x27D0, 0x2B19, 0x1F300, 0x1F303,
        0x1F306, 0x1F308, 0x1F30A, 0x1F319, 0x1F31F, 0x1F320, 0x1F340, 0x1F4CD, 0x1F4E1,
        0x1F531, 0x1F533, 0x1F657, 0x1F659, 0x1F665, 0x1F668, 0x1F66B, 0x1F78B, 0x1F796,
        0x1F79C
    )

    fun fromUrl(url: String): CapsuleIdentity {
        val normalizedUrl = url.trim()
        @SuppressLint("UseKtx")
        val parsedUri = Uri.parse(normalizedUrl)
        // Lagrange seed-source flow:
        //   setThemeSeed_GmDocument(..., urlPaletteSeed_String(url), urlThemeSeed_String(url))
        // See src/gmdocument.c:2348 and src/gmutil.c:324-345.
        val themeSeedSource = themeSeedSource(parsedUri)
        val paletteSeedSource = paletteSeedSource(parsedUri, themeSeedSource)
        val themeSeed = themeHash(themeSeedSource)
        val paletteSeed = themeHash(paletteSeedSource)

        var iconCodePoint = 0
        if (paletteSeedSource.isNotEmpty()) {
            // Lagrange: src/gmdocument.c:1561-1563
            val index = (paletteSeed.toInt() ushr 7) % siteIcons.size
            iconCodePoint = siteIcons[index]
        }
        // Lagrange special cases (no explanation though upon why):
        // src/gmdocument.c:2124-2129
        when (paletteSeedSource.lowercase(Locale.US)) {
            "geminiprotocol.net" -> iconCodePoint = 0x264A
            "spartan.mozz.us" -> iconCodePoint = 0x1F4AA
        }

        val icon = if (iconCodePoint != 0) String(Character.toChars(iconCodePoint)) else null

        return CapsuleIdentity(
            icon = icon,
            themeSeed = themeSeed,
            paletteSeed = paletteSeed,
            themeSeedSource = themeSeedSource,
            paletteSeedSource = paletteSeedSource
        )
    }

    private fun themeSeedSource(uri: Uri): String {
        if (uri.scheme?.equals("file", ignoreCase = true) == true) return ""
        val path = uri.encodedPath.orEmpty()
        val userMatch = userPathPattern.find(path)?.groupValues?.getOrNull(1)
            ?: usersPathPattern.find(path)?.groupValues?.getOrNull(1)
        return userMatch?.takeIf { it.isNotBlank() } ?: uri.host.orEmpty().lowercase(Locale.US)
    }


    // Partial port of src/gmutil.c:urlPaletteSeed_String() (335-345).
    private fun paletteSeedSource(uri: Uri, defaultSource: String): String {
        // Lagrange also checks a site-specific override (valueString_SiteSpec + paletteSeed key),
        // which is not implemented in Gemcap.
        //
        // The file-scheme check is redundant with themeSeedSource but kept for parity with
        // Lagrange's urlPaletteSeed_String() and to support future site-specific overrides
        // that might never arrive.
        if (uri.scheme.equals("file", ignoreCase = true)) {
            return ""
        }
        return defaultSource
    }

    // Lagrange: src/gmdocument.c:88-152 (themeHash_ CRC-32 table)
    @OptIn(ExperimentalUnsignedTypes::class)
    private val crc32Table: UIntArray by lazy {
        UIntArray(256) { i ->
            var crc = i.toUInt()
            repeat(8) {
                crc = if ((crc and 1u) != 0u) {
                    0xEDB88320u xor (crc shr 1)
                } else {
                    crc shr 1
                }
            }
            crc
        }
    }

    // Lagrange: src/gmdocument.c:88 (themeHash_)
    @OptIn(ExperimentalUnsignedTypes::class)
    private fun themeHash(seedSource: String): UInt {
        if (seedSource.isEmpty()) return 0u
        var crc = 0u
        for (b in seedSource.toByteArray(Charsets.UTF_8)) {
            val idx = ((crc xor (b.toInt() and 0xFF).toUInt()) and 0xFFu).toInt()
            crc = crc32Table[idx] xor (crc shr 8)
        }
        return crc
    }
}
