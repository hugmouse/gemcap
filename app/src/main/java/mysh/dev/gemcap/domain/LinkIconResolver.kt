package mysh.dev.gemcap.domain

object LinkIconResolver {
    private const val ARROW = "\u27A4"              // ➤
    private const val ENVELOPE = "\uD83D\uDCE7"     // 📧
    private const val MAGNIFYING_GLASS = "\uD83D\uDD0D" // 🔍
    private const val POINTING_FINGER = "\uD83D\uDC49"  // 👉
    private const val UPLOAD_ARROW = "\u2BA5"        // ⮥
    private const val IMAGE = "\uD83D\uDDBC"         // 🖼
    private const val FILE = "\uD83D\uDCCE"          // 📎
    private const val GLOBE = "\uD83C\uDF10"         // 🌐
    private const val NEX = "\uD83D\uDE87"           // 🚇
    private const val GUPPY = "\uD83D\uDC1F"        // 🐟
    private const val SPARTAN = "\uD83D\uDCAA"       // 💪
    private const val FONTPACK = "\uD83D\uDD20"      // 🔠

    fun iconFor(link: GeminiContent.Link): String {
        if (LinkFlag.ICON_FROM_LABEL in link.flags && !link.labelIcon.isNullOrBlank()) {
            return link.labelIcon
        }
        if (LinkFlag.QUERY in link.flags) {
            return if (link.scheme == LinkScheme.SPARTAN) UPLOAD_ARROW else MAGNIFYING_GLASS
        }
        return when (link.scheme) {
            LinkScheme.TITAN -> UPLOAD_ARROW
            LinkScheme.FINGER -> POINTING_FINGER
            LinkScheme.NEX -> NEX
            LinkScheme.GUPPY -> GUPPY
            LinkScheme.SPARTAN -> SPARTAN
            LinkScheme.MAILTO, LinkScheme.MISFIN -> ENVELOPE
            LinkScheme.DATA -> FILE
            LinkScheme.FILE -> FILE
            else -> when {
                LinkFlag.REMOTE in link.flags -> GLOBE
                LinkFlag.IMAGE_EXT in link.flags -> IMAGE
                LinkFlag.FONTPACK_EXT in link.flags -> FONTPACK
                else -> ARROW
            }
        }
    }

    fun descriptionFor(link: GeminiContent.Link): String {
        return when (link.scheme) {
            LinkScheme.HTTP -> "HTTP link"
            LinkScheme.GEMINI -> "Gemini link"
            LinkScheme.GOPHER -> "Gopher link"
            LinkScheme.FINGER -> "Finger link"
            LinkScheme.TITAN -> "Titan link"
            LinkScheme.SPARTAN -> "Spartan link"
            LinkScheme.NEX -> "Nex link"
            LinkScheme.GUPPY -> "Guppy link"
            LinkScheme.MAILTO -> "Email link"
            LinkScheme.MISFIN -> "Misfin link"
            LinkScheme.FILE -> "File link"
            LinkScheme.DATA -> "Data link"
            LinkScheme.ABOUT -> "Internal link"
            LinkScheme.UNKNOWN -> "Link"
        }
    }
}
