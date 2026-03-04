package mysh.dev.gemcap.ui.theme

import androidx.compose.ui.graphics.Color
import mysh.dev.gemcap.domain.CapsuleIdentity
import kotlin.math.abs

data class CapsuleStyle(
    val backgroundColor: Color,
    val bodyColor: Color,
    val heading1Color: Color,
    val heading2Color: Color,
    val heading3Color: Color,
    val quoteTextColor: Color,
    val quoteIndicatorColor: Color,
    val preformattedTextColor: Color,
    val preformattedAltColor: Color,
    val preformattedBackgroundColor: Color,
    val linkTextColor: Color,
    val linkIconColor: Color,
    val bulletColor: Color,
    val chromeAccentColor: Color,
    val chromeTextColor: Color
)

// Color theme generation ported from Lagrange's setThemeSeed_GmDocument().
// Lagrange source: src/gmdocument.c:1546
// Core color utilities: src/ui/color.c, src/ui/color.h
object CapsuleStyleGenerator {
    // Lagrange: src/ui/color.h:249 (minSat_HSLColor)
    private const val minSatHsl = 0.013f
    private const val saturationPreference = 1f

    private enum class Theme {
        COLORFUL_DARK,
        COLORFUL_LIGHT
    }

    // Lagrange: src/ui/color.h:128-178 (tmFirst_ColorId .. max_ColorId)
    private enum class ColorId {
        BACKGROUND,
        PARAGRAPH,
        FIRST_PARAGRAPH,
        QUOTE,
        QUOTE_ICON,
        PREFORMATTED,
        HEADING1,
        HEADING2,
        HEADING3,
        BANNER_BACKGROUND,
        BANNER_TITLE,
        BANNER_ICON,
        BANNER_SIDE_TITLE,
        INLINE_CONTENT_METADATA,
        BACKGROUND_ALT_TEXT,
        FRAME_ALT_TEXT,
        BACKGROUND_OPEN_LINK,
        LINK_FEED_ENTRY_DATE,
        LINK_CUSTOM_ICON_VISITED,
        BAD_LINK,
        LINK_ICON,
        LINK_ICON_VISITED,
        LINK_TEXT,
        LINK_TEXT_HOVER,
        HYPERTEXT_LINK_ICON,
        HYPERTEXT_LINK_ICON_VISITED,
        HYPERTEXT_LINK_TEXT,
        HYPERTEXT_LINK_TEXT_HOVER,
        GOPHER_LINK_ICON,
        GOPHER_LINK_ICON_VISITED,
        GOPHER_LINK_TEXT,
        GOPHER_LINK_TEXT_HOVER,
        BANNER_ITEM_BACKGROUND,
        BANNER_ITEM_FRAME,
        BANNER_ITEM_TITLE,
        BANNER_ITEM_TEXT
    }

    private val allColorIds = ColorId.entries.toTypedArray()

    private data class Rgba8(
        val r: Int,
        val g: Int,
        val b: Int,
        val a: Int = 255
    ) {
        fun asComposeColor(): Color {
            return Color(
                red = r / 255f,
                green = g / 255f,
                blue = b / 255f,
                alpha = a / 255f
            )
        }
    }

    // Lagrange: src/ui/color.h:245-247 (Impl_HSLColor)
    private data class HslColor(
        val hue: Float,
        val sat: Float,
        val lum: Float,
        val alpha: Float = 1f
    )

    private data class CorePalette(
        val black: Rgba8,
        val gray25: Rgba8,
        val gray50: Rgba8,
        val gray75: Rgba8,
        val white: Rgba8,
        val brown: Rgba8,
        val orange: Rgba8,
        val teal: Rgba8,
        val cyan: Rgba8,
        val maroon: Rgba8,
        val red: Rgba8,
        val darkGreen: Rgba8,
        val green: Rgba8,
        val indigo: Rgba8,
        val blue: Rgba8
    )

    private class ThemePalette {
        private val colors = Array(allColorIds.size) { Rgba8(0, 0, 0, 255) }

        operator fun get(id: ColorId): Rgba8 = colors[id.ordinal]

        operator fun set(id: ColorId, color: Rgba8) {
            colors[id.ordinal] = color
        }
    }

    // Lagrange: src/ui/color.c:33-53 (darkPalette_)
    private val darkCorePalette = CorePalette(
        black = Rgba8(0, 0, 0),
        gray25 = Rgba8(40, 40, 40),
        gray50 = Rgba8(80, 80, 80),
        gray75 = Rgba8(160, 160, 160),
        white = Rgba8(255, 255, 255),
        brown = Rgba8(98, 67, 7),
        orange = Rgba8(255, 170, 32),
        teal = Rgba8(0, 96, 128),
        cyan = Rgba8(0, 192, 255),
        maroon = Rgba8(140, 32, 32),
        red = Rgba8(255, 80, 80),
        darkGreen = Rgba8(24, 80, 24),
        green = Rgba8(48, 200, 48),
        indigo = Rgba8(0, 70, 128),
        blue = Rgba8(40, 132, 255)
    )

    // Lagrange: src/ui/color.c:55-75 (lightPalette_)
    private val lightCorePalette = CorePalette(
        black = Rgba8(0, 0, 0),
        gray25 = Rgba8(75, 75, 75),
        gray50 = Rgba8(150, 150, 150),
        gray75 = Rgba8(235, 235, 235),
        white = Rgba8(255, 255, 255),
        brown = Rgba8(210, 120, 10),
        orange = Rgba8(235, 215, 200),
        teal = Rgba8(10, 110, 130),
        cyan = Rgba8(170, 215, 220),
        maroon = Rgba8(150, 60, 55),
        red = Rgba8(240, 180, 170),
        darkGreen = Rgba8(50, 100, 50),
        green = Rgba8(128, 200, 128),
        indigo = Rgba8(50, 120, 190),
        blue = Rgba8(150, 211, 255)
    )

    // Lagrange: src/gmdocument.c:1793
    private val hues = floatArrayOf(
        5f, 25f, 40f, 56f, 95f, 120f, 160f, 180f, 208f, 231f, 270f, 334f
    )

    // Lagrange: src/gmdocument.c:1794-1809 (altHues)
    private val altHueIndices = arrayOf(
        intArrayOf(2, 3),   // red
        intArrayOf(8, 3),   // reddish orange
        intArrayOf(7, 6),   // yellowish orange
        intArrayOf(5, 7),   // yellow
        intArrayOf(8, 2),   // greenish yellow
        intArrayOf(2, 3),   // green
        intArrayOf(2, 8),   // bluish green
        intArrayOf(2, 5),   // cyan
        intArrayOf(6, 10),  // sky blue
        intArrayOf(3, 11),  // blue
        intArrayOf(8, 9),   // violet
        intArrayOf(7, 8)    // pink
    )

    // Lagrange: src/gmdocument.c:1918-1919
    private val normSat = floatArrayOf(
        0.85f, 0.90f, 1.00f, 0.65f, 0.65f, 0.65f,
        0.90f, 0.90f, 1.00f, 0.90f, 1.00f, 0.75f
    )

    fun fromIdentity(identity: CapsuleIdentity?, isDarkMode: Boolean): CapsuleStyle? {
        if (identity == null) return null
        if (identity.paletteSeed == 0u) return null

        val theme = if (isDarkMode) Theme.COLORFUL_DARK else Theme.COLORFUL_LIGHT
        val core = if (isDarkMode) darkCorePalette else lightCorePalette
        val palette = ThemePalette()

        setDefaultLinkColors(palette, core, theme)
        setDefaultNonLinkColors(palette, core, theme)
        applySaturationPreference(palette, saturationPreference)
        applySeededThemeColors(palette, core, theme, isDarkMode, identity.paletteSeed)
        setDerivedThemeColors(palette, core, theme)

        return CapsuleStyle(
            backgroundColor = palette[ColorId.BACKGROUND].asComposeColor(),
            bodyColor = palette[ColorId.PARAGRAPH].asComposeColor(),
            heading1Color = palette[ColorId.HEADING1].asComposeColor(),
            heading2Color = palette[ColorId.HEADING2].asComposeColor(),
            heading3Color = palette[ColorId.HEADING3].asComposeColor(),
            quoteTextColor = palette[ColorId.QUOTE].asComposeColor(),
            quoteIndicatorColor = palette[ColorId.QUOTE_ICON].asComposeColor(),
            preformattedTextColor = palette[ColorId.PREFORMATTED].asComposeColor(),
            preformattedAltColor = palette[ColorId.INLINE_CONTENT_METADATA].asComposeColor(),
            preformattedBackgroundColor = palette[ColorId.BACKGROUND_ALT_TEXT].asComposeColor(),
            linkTextColor = palette[ColorId.LINK_TEXT].asComposeColor(),
            linkIconColor = palette[ColorId.LINK_ICON].asComposeColor(),
            bulletColor = palette[ColorId.PARAGRAPH].asComposeColor(),
            chromeAccentColor = palette[ColorId.BANNER_ICON].asComposeColor(),
            chromeTextColor = palette[ColorId.BANNER_TITLE].asComposeColor()
        )
    }

    private fun setDefaultLinkColors(palette: ThemePalette, core: CorePalette, theme: Theme) {
        palette[ColorId.BAD_LINK] = core.red
        if (isDarkTheme(theme)) {
            palette[ColorId.INLINE_CONTENT_METADATA] = core.cyan
            palette[ColorId.LINK_TEXT] = core.white
            palette[ColorId.LINK_ICON] = core.cyan
            palette[ColorId.LINK_TEXT_HOVER] = core.cyan
            palette[ColorId.LINK_ICON_VISITED] = core.teal
            palette[ColorId.HYPERTEXT_LINK_TEXT] = core.white
            palette[ColorId.HYPERTEXT_LINK_ICON] = core.orange
            palette[ColorId.HYPERTEXT_LINK_TEXT_HOVER] = core.orange
            palette[ColorId.HYPERTEXT_LINK_ICON_VISITED] = core.brown
            palette[ColorId.GOPHER_LINK_TEXT] = core.white
            palette[ColorId.GOPHER_LINK_ICON] = core.green
            palette[ColorId.GOPHER_LINK_ICON_VISITED] = core.darkGreen
            palette[ColorId.GOPHER_LINK_TEXT_HOVER] = core.green
        } else {
            palette[ColorId.INLINE_CONTENT_METADATA] = core.brown
            palette[ColorId.LINK_TEXT] = core.black
            palette[ColorId.LINK_ICON] = core.teal
            palette[ColorId.LINK_TEXT_HOVER] = core.teal
            palette[ColorId.LINK_ICON_VISITED] = core.cyan
            palette[ColorId.HYPERTEXT_LINK_TEXT] = core.black
            palette[ColorId.HYPERTEXT_LINK_TEXT_HOVER] = core.brown
            palette[ColorId.HYPERTEXT_LINK_ICON] = core.brown
            palette[ColorId.HYPERTEXT_LINK_ICON_VISITED] = core.orange
            palette[ColorId.GOPHER_LINK_TEXT] = core.black
            palette[ColorId.GOPHER_LINK_TEXT_HOVER] = core.darkGreen
            palette[ColorId.GOPHER_LINK_ICON] = core.darkGreen
            palette[ColorId.GOPHER_LINK_ICON_VISITED] = core.green
        }
    }

    private fun setDefaultNonLinkColors(
        palette: ThemePalette,
        core: CorePalette,
        theme: Theme
    ) {
        if (theme == Theme.COLORFUL_DARK) {
            val base = HslColor(200f, 0f, 0.15f, 1f)
            setHsl(palette, ColorId.BACKGROUND, base)
            palette[ColorId.PARAGRAPH] = core.gray75
            setHsl(palette, ColorId.FIRST_PARAGRAPH, addSatLum(base, 0f, 0.75f))
            palette[ColorId.QUOTE] = core.cyan
            palette[ColorId.PREFORMATTED] = core.cyan
            palette[ColorId.HEADING1] = core.white
            setHsl(palette, ColorId.HEADING2, addSatLum(base, 0.5f, 0.5f))
            setHsl(palette, ColorId.HEADING3, addSatLum(base, 1f, 0.4f))
            setHsl(palette, ColorId.BANNER_BACKGROUND, addSatLum(base, 0f, -0.05f))
            palette[ColorId.BANNER_TITLE] = core.white
            palette[ColorId.BANNER_ICON] = core.orange
        } else {
            val base = addSatLum(hsl(core.teal), -0.3f, 0.5f)
            setHsl(palette, ColorId.BACKGROUND, base)
            palette[ColorId.PARAGRAPH] = core.black
            palette[ColorId.FIRST_PARAGRAPH] = core.black
            setHsl(palette, ColorId.QUOTE, addSatLum(base, 0f, -0.25f))
            setHsl(palette, ColorId.PREFORMATTED, addSatLum(base, 0f, -0.3f))
            setHsl(palette, ColorId.HEADING1, addSatLum(base, 1f, -0.37f))
            palette[ColorId.HEADING2] = mix(palette[ColorId.HEADING1], core.black, 0.5f)
            palette[ColorId.HEADING3] = mix(palette[ColorId.BACKGROUND], core.black, 0.4f)
            setHsl(palette, ColorId.BANNER_BACKGROUND, addSatLum(base, 0f, -0.1f))
            setHsl(palette, ColorId.BANNER_ICON, addSatLum(base, 0f, -0.4f))
            setHsl(palette, ColorId.BANNER_TITLE, addSatLum(base, 0f, -0.4f))
            setHsl(palette, ColorId.LINK_ICON, addSatLum(hsl(core.teal), 0f, 0f))
            palette[ColorId.LINK_ICON_VISITED] = mix(palette[ColorId.BACKGROUND], core.teal, 0.35f)
            setHsl(palette, ColorId.HYPERTEXT_LINK_ICON, hsl(core.white))
            palette[ColorId.HYPERTEXT_LINK_ICON_VISITED] = mix(palette[ColorId.BACKGROUND], core.white, 0.5f)
            setHsl(
                palette,
                ColorId.GOPHER_LINK_ICON,
                addSatLum(hsl(palette[ColorId.GOPHER_LINK_ICON]), 0f, -0.25f)
            )
            setHsl(
                palette,
                ColorId.GOPHER_LINK_TEXT_HOVER,
                addSatLum(hsl(palette[ColorId.GOPHER_LINK_TEXT_HOVER]), 0f, -0.3f)
            )
        }
    }

    private fun applySaturationPreference(palette: ThemePalette, saturation: Float) {
        for (id in allColorIds) {
            if (!isLinkColor(id)) {
                val color = hsl(palette[id])
                setHsl(palette, id, color.copy(sat = color.sat * saturation))
            }
        }
    }

    // Lagrange: src/gmdocument.c:1779-2112 (seed extraction and theme color application)
    private fun applySeededThemeColors(
        palette: ThemePalette,
        core: CorePalette,
        theme: Theme,
        isDarkUi: Boolean,
        themeSeed: UInt
    ) {
        if (themeSeed == 0u) return

        val seedHues = hues.copyOf()
        if ((themeSeed and 0x00c00000u) != 0u) {
            val shift = if ((themeSeed and 0x00200000u) != 0u) 10f else -10f
            for (i in seedHues.indices) {
                seedHues[i] += shift
            }
        }

        var primIndex = (themeSeed and 0xffu).toInt() % seedHues.size
        if (primIndex == 11 && (themeSeed and 0x04000000u) != 0u) {
            primIndex = (((primIndex.toUInt() + themeSeed) and 0x0fu).toInt()) % 12
        }

        val altIndex = intArrayOf(
            if ((themeSeed and 0x4u) != 0u) 1 else 0,
            if ((themeSeed and 0x40u) != 0u) 1 else 0
        )
        val altHue = seedHues[altHueIndices[primIndex][altIndex[0]]]
        val altHue2 = seedHues[altHueIndices[primIndex][altIndex[1]]]

        val isBannerLighter = (themeSeed and 0x4000u) != 0u || !isDarkUi
        val isDarkBgSat = (themeSeed and 0x200000u) != 0u && (primIndex < 1 || primIndex > 4)
        val normLums = normLums(seedHues)

        // Lagrange: src/gmdocument.c:1860-1902
        if (theme == Theme.COLORFUL_DARK) {
            val base = HslColor(
                hue = seedHues[primIndex],
                sat = 0.8f * ((themeSeed shr 24) and 0xffu).toFloat() / 255f + minSatHsl,
                lum = 0.06f + 0.09f * (((themeSeed shr 5) and 0x7u).toFloat() / 7f),
                alpha = 1f
            )
            val altBase = HslColor(altHue, base.sat, base.lum, 1f)

            setHsl(palette, ColorId.BACKGROUND, base)
            setHsl(
                palette,
                ColorId.BANNER_BACKGROUND,
                addSatLum(base, 0.1f, 0.04f * (if (isBannerLighter) 1f else -1f))
            )
            setHsl(
                palette,
                ColorId.BANNER_TITLE,
                setLum(addSatLum(base, 0.1f, 0f), 0.55f)
            )
            setHsl(
                palette,
                ColorId.BANNER_ICON,
                setLum(addSatLum(base, 0.35f, 0f), 0.65f)
            )

            val titleLum = 0.2f * (((themeSeed shr 17) and 0x7u).toFloat() / 7f)
            setHsl(palette, ColorId.HEADING1, setLum(altBase, titleLum + 0.80f))
            setHsl(palette, ColorId.HEADING2, setLum(altBase, titleLum + 0.70f))
            setHsl(palette, ColorId.HEADING3, setLum(altBase, titleLum + 0.60f))
            setHsl(palette, ColorId.PARAGRAPH, addSatLum(base, 0.1f, 0.6f))

            // Lagrange: src/gmdocument.c:1892
            if (delta(palette[ColorId.HEADING3], palette[ColorId.PARAGRAPH]) <= 80) {
                setHsl(
                    palette,
                    ColorId.HEADING2,
                    addSatLum(hsl(palette[ColorId.HEADING2]), 0.4f, -0.12f)
                )
                setHsl(
                    palette,
                    ColorId.HEADING3,
                    addSatLum(hsl(palette[ColorId.HEADING3]), 0.4f, -0.2f)
                )
            }

            setHsl(palette, ColorId.FIRST_PARAGRAPH, addSatLum(base, 0.2f, 0.72f))
            setHsl(palette, ColorId.PREFORMATTED, HslColor(altHue2, 1f, 0.75f, 1f))
            palette[ColorId.QUOTE] = palette[ColorId.PREFORMATTED]
            palette[ColorId.INLINE_CONTENT_METADATA] = palette[ColorId.HEADING3]
        // Lagrange: src/gmdocument.c:1903-1944
        } else {
            val normLum = normLums[primIndex]
            var base = HslColor(seedHues[primIndex], 1f, normLum, 1f)
            val h1 = HslColor(seedHues[primIndex], 1f, normLum - 0.37f, 1f)
            base = base.copy(sat = base.sat * normSat[primIndex] * 0.8f)

            palette[ColorId.PARAGRAPH] = core.black
            palette[ColorId.FIRST_PARAGRAPH] = core.black

            setHsl(palette, ColorId.BACKGROUND, base)
            setHsl(palette, ColorId.QUOTE, addSatLum(base, 0f, -base.lum * 0.67f))
            setHsl(palette, ColorId.PREFORMATTED, addSatLum(base, 0f, -base.lum * 0.75f))
            setHsl(palette, ColorId.HEADING1, h1)
            setHsl(palette, ColorId.HEADING2, addSatLum(h1, 0f, -0.1f))
            palette[ColorId.HEADING3] = mix(palette[ColorId.HEADING1], core.black, 0.6f)
            setHsl(
                palette,
                ColorId.BANNER_BACKGROUND,
                addSatLum(base, 0f, if (isDarkUi) -0.2f * (1f - normLum) else 0.2f * (1f - normLum))
            )
            setHsl(palette, ColorId.BANNER_ICON, addSatLum(base, 0f, if (isDarkUi) -0.6f else -0.3f))
            setHsl(palette, ColorId.BANNER_TITLE, addSatLum(base, 0f, if (isDarkUi) -0.5f else -0.25f))
            palette[ColorId.LINK_ICON_VISITED] = mix(palette[ColorId.BACKGROUND], core.teal, 0.3f)
        }

        // Lagrange: src/gmdocument.c:2056
        if (isDarkTheme(theme)) {
            val base = HslColor(seedHues[primIndex], 1f, normLums[primIndex], 1f)
            palette[ColorId.LINK_TEXT] = mix(palette[ColorId.LINK_TEXT], rgb(base), 0.25f)
            palette[ColorId.HYPERTEXT_LINK_TEXT] = palette[ColorId.LINK_TEXT]
            palette[ColorId.GOPHER_LINK_TEXT] = palette[ColorId.LINK_TEXT]
        }

        // Lagrange: src/gmdocument.c:2069-2112 (isDarkBgSat saturation/luminosity adjustments)
        for (id in allColorIds) {
            var color = hsl(palette[id])
            if (theme == Theme.COLORFUL_DARK && !isLinkColor(id)) {
                if (isDarkBgSat) {
                    if (isBackgroundColor(id)) {
                        val newSat = when {
                            primIndex == 11 -> (4f * color.sat + 1f) / 5f
                            primIndex != 5 -> (color.sat + 1f) / 2f
                            else -> color.sat * 0.5f
                        }
                        color = color.copy(sat = newSat, lum = color.lum * 0.75f)
                    } else if (isTextColor(id)) {
                        color = color.copy(lum = (color.lum + 1f) / 2f)
                    }
                } else {
                    if (isBackgroundColor(id)) {
                        var newSat = color.sat * 0.333f
                        if (primIndex == 11) newSat *= 0.5f
                        if (primIndex == 4 || primIndex == 5) newSat *= 0.333f
                        color = color.copy(sat = newSat)
                    } else if (id == ColorId.PARAGRAPH && (primIndex == 5 || primIndex == 4)) {
                        color = color.copy(sat = color.sat * 0.4f, lum = color.lum + 0.1f)
                    } else if (isTextColor(id)) {
                        color = color.copy(
                            sat = (color.sat + 2f) / 3f,
                            lum = (2f * color.lum + 1f) / 3f
                        )
                    }
                }
            }
            if (!isLinkColor(id)) {
                color = color.copy(sat = color.sat * saturationPreference)
            }
            setHsl(palette, id, color)
        }
    }

    // Lagrange: src/gmdocument.c:1495 (setDerivedThemeColors_)
    private fun setDerivedThemeColors(palette: ThemePalette, core: CorePalette, theme: Theme) {
        palette[ColorId.QUOTE_ICON] = mix(palette[ColorId.QUOTE], palette[ColorId.BACKGROUND], 0.55f)
        palette[ColorId.BANNER_SIDE_TITLE] = mix(
            palette[ColorId.BANNER_TITLE],
            palette[ColorId.BACKGROUND],
            if (theme == Theme.COLORFUL_DARK) 0.55f else 0f
        )

        val bannerItemFg = if (isDarkTheme(theme)) core.white else core.black
        palette[ColorId.BANNER_ITEM_BACKGROUND] = mix(
            palette[ColorId.BANNER_BACKGROUND],
            palette[ColorId.BANNER_TITLE],
            0.1f
        )
        palette[ColorId.BANNER_ITEM_FRAME] = mix(
            palette[ColorId.BANNER_BACKGROUND],
            palette[ColorId.BANNER_TITLE],
            0.4f
        )
        palette[ColorId.BANNER_ITEM_TEXT] = mix(
            palette[ColorId.BANNER_TITLE],
            bannerItemFg,
            0.5f
        )
        palette[ColorId.BANNER_ITEM_TITLE] = bannerItemFg

        palette[ColorId.BACKGROUND_ALT_TEXT] = mix(
            palette[ColorId.QUOTE_ICON],
            palette[ColorId.BACKGROUND],
            0.85f
        )
        palette[ColorId.FRAME_ALT_TEXT] = mix(
            palette[ColorId.QUOTE_ICON],
            palette[ColorId.BACKGROUND],
            0.4f
        )
        palette[ColorId.BACKGROUND_OPEN_LINK] = mix(
            palette[ColorId.LINK_TEXT],
            palette[ColorId.BACKGROUND],
            0.90f
        )
        palette[ColorId.LINK_FEED_ENTRY_DATE] = mix(
            palette[ColorId.LINK_TEXT],
            palette[ColorId.BACKGROUND],
            0.25f
        )
        if (theme == Theme.COLORFUL_DARK && delta(
                palette[ColorId.LINK_TEXT],
                palette[ColorId.PARAGRAPH]
            ) < 100
        ) {
            setHsl(
                palette,
                ColorId.PARAGRAPH,
                addSatLum(hsl(palette[ColorId.PARAGRAPH]), 0.3f, -0.025f)
            )
        }
        palette[ColorId.LINK_CUSTOM_ICON_VISITED] = mix(
            palette[ColorId.LINK_ICON_VISITED],
            palette[ColorId.LINK_ICON],
            0.2f
        )
    }

    // Lagrange: src/gmdocument.c:1839-1846 (normLums_)
    private fun normLums(localHues: FloatArray): FloatArray {
        val result = FloatArray(localHues.size)
        for (i in localHues.indices) {
            result[i] = 1f - luma(HslColor(localHues[i], 0.75f, 0.5f, 1f)) / 2f
        }
        return result
    }

    private fun setHsl(palette: ThemePalette, id: ColorId, color: HslColor) {
        palette[id] = rgb(color)
    }

    // Lagrange: src/ui/color.c:396 (mix_Color)
    private fun mix(first: Rgba8, second: Rgba8, t: Float): Rgba8 {
        val clampedT = t.coerceIn(0f, 1f)
        return Rgba8(
            r = (first.r * (1f - clampedT) + second.r * clampedT).toInt(),
            g = (first.g * (1f - clampedT) + second.g * clampedT).toInt(),
            b = (first.b * (1f - clampedT) + second.b * clampedT).toInt(),
            a = (first.a * (1f - clampedT) + second.a * clampedT).toInt()
        )
    }

    // Lagrange: src/ui/color.c:404 (delta_Color)
    private fun delta(first: Rgba8, second: Rgba8): Int {
        return abs(first.r - second.r) + abs(first.g - second.g) + abs(first.b - second.b)
    }

    // Lagrange: src/ui/color.c:465 (hsl_Colorf)
    private fun hsl(color: Rgba8): HslColor {
        val rgb = floatArrayOf(
            (color.r / 255f).coerceIn(0f, 1f),
            (color.g / 255f).coerceIn(0f, 1f),
            (color.b / 255f).coerceIn(0f, 1f),
            (color.a / 255f).coerceIn(0f, 1f)
        )
        val compMax = when {
            rgb[0] >= rgb[1] && rgb[0] >= rgb[2] -> 0
            rgb[1] >= rgb[0] && rgb[1] >= rgb[2] -> 1
            else -> 2
        }
        val compMin = when {
            rgb[0] <= rgb[1] && rgb[0] <= rgb[2] -> 0
            rgb[1] <= rgb[0] && rgb[1] <= rgb[2] -> 1
            else -> 2
        }
        val rgbMax = rgb[compMax]
        val rgbMin = rgb[compMin]
        val lum = (rgbMax + rgbMin) / 2f

        var hue = 0f
        var sat = 0f
        if (abs(rgbMax - rgbMin) > 0.00001f) {
            val chr = rgbMax - rgbMin
            sat = chr / (1f - abs(2f * lum - 1f))
            hue = when (compMax) {
                0 -> (rgb[1] - rgb[2]) / chr + if (rgb[1] < rgb[2]) 6f else 0f
                1 -> (rgb[2] - rgb[0]) / chr + 2f
                else -> (rgb[0] - rgb[1]) / chr + 4f
            }
        }
        return HslColor(hue * 60f, sat, lum, rgb[3])
    }

    // Lagrange: src/ui/color.c:519 (rgbf_HSLColor)
    private fun rgb(color: HslColor): Rgba8 {
        var hue = wrap01(color.hue / 360f)
        val sat = color.sat.coerceIn(0f, 1f)
        val lum = color.lum.coerceIn(0f, 1f)

        val red: Float
        val green: Float
        val blue: Float

        if (sat < 0.00001f) {
            red = lum
            green = lum
            blue = lum
        } else {
            val q = if (lum < 0.5f) lum * (1f + sat) else lum + sat - lum * sat
            val p = 2f * lum - q
            red = hueToRgb(p, q, hue + 1f / 3f)
            green = hueToRgb(p, q, hue)
            blue = hueToRgb(p, q, hue - 1f / 3f)
        }

        return Rgba8(
            r = quantize8(red),
            g = quantize8(green),
            b = quantize8(blue),
            a = quantize8(color.alpha)
        )
    }

    // Lagrange: src/ui/color.c:551 (luma_HSLColor)
    private fun luma(color: HslColor): Float {
        return luma(rgb(color))
    }

    // Lagrange: src/ui/color.c:543 (luma_Color)
    private fun luma(color: Rgba8): Float {
        return 0.299f * color.r / 255f + 0.587f * color.g / 255f + 0.114f * color.b / 255f
    }

    // Lagrange: src/ui/color.c:510 (hueToRgb_)
    private fun hueToRgb(p: Float, q: Float, inputT: Float): Float {
        var t = inputT
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        if (t < 1f / 6f) return p + (q - p) * 6f * t
        if (t < 1f / 2f) return q
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f
        return p
    }

    private fun quantize8(value: Float): Int {
        return (value.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    }

    private fun wrap01(value: Float): Float {
        var wrapped = value % 1f
        if (wrapped < 0f) wrapped += 1f
        return wrapped
    }

    // Lagrange: src/ui/color.c:613 (addSatLum_HSLColor)
    private fun addSatLum(color: HslColor, sat: Float, lum: Float): HslColor {
        return HslColor(
            hue = color.hue,
            sat = (color.sat + sat).coerceIn(minSatHsl, 1f),
            lum = (color.lum + lum).coerceIn(minSatHsl, 1f),
            alpha = color.alpha
        )
    }

    // Lagrange: src/ui/color.c:608 (setLum_HSLColor)
    private fun setLum(color: HslColor, lum: Float): HslColor {
        return HslColor(
            hue = color.hue,
            sat = color.sat,
            lum = lum.coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }

    private fun isDarkTheme(theme: Theme): Boolean {
        return theme == Theme.COLORFUL_DARK
    }

    private fun isLinkColor(id: ColorId): Boolean {
        return id.ordinal >= ColorId.BAD_LINK.ordinal
    }

    private fun isBackgroundColor(id: ColorId): Boolean {
        return id == ColorId.BACKGROUND || id == ColorId.BANNER_BACKGROUND
    }

    private fun isTextColor(id: ColorId): Boolean {
        return !isBackgroundColor(id)
    }
}
