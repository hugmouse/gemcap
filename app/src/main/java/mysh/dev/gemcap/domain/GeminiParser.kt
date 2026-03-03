package mysh.dev.gemcap.domain

import java.net.URI
import java.util.Locale

object GeminiParser {

    private val mediaExtensionToMime = mapOf(
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "bmp" to "image/bmp",
        "webp" to "image/webp",
        "svg" to "image/svg+xml",
        "ico" to "image/x-icon",
        "avif" to "image/avif",
        "mp3" to "audio/mpeg",
        "ogg" to "audio/ogg",
        "wav" to "audio/wav",
        "flac" to "audio/flac",
        "aac" to "audio/aac",
        "m4a" to "audio/mp4",
        "opus" to "audio/opus",
        "webm" to "video/webm",
        "mp4" to "video/mp4",
        "ogv" to "video/ogg",
        "avi" to "video/x-msvideo",
        "mov" to "video/quicktime",
        "mkv" to "video/x-matroska",
        "flv" to "video/x-flv",
        "wmv" to "video/x-ms-wmv"
    )

    private data class LabelIconResult(
        val displayText: String,
        val icon: String?,
        val fromLabel: Boolean
    )

    fun parse(text: String, baseUrl: String? = null): List<GeminiContent> {
        val lines = text.lines()
        val content = mutableListOf<GeminiContent>()
        var idCounter = 0
        var inPreformatted = false
        var preformattedAlt = ""
        val preformattedBlock = StringBuilder()

        for (line in lines) {
            if (inPreformatted) {
                if (line.startsWith("```")) {
                    // End of preformatted block
                    content.add(
                        GeminiContent.Preformatted(
                            id = idCounter++,
                            text = preformattedBlock.toString(),
                            alt = preformattedAlt
                        )
                    )
                    inPreformatted = false
                    preformattedBlock.clear()
                    preformattedAlt = ""
                } else {
                    if (preformattedBlock.isNotEmpty()) {
                        preformattedBlock.append("\n")
                    }
                    preformattedBlock.append(line)
                }
                continue
            }

            when {
                line.startsWith("```") -> {
                    inPreformatted = true
                    preformattedAlt = line.removePrefix("```").trim()
                }

                line.startsWith("=>") -> {
                    val parts = line.removePrefix("=>").trim().split(Regex("\\s+"), 2)
                    val url = parts.getOrNull(0)?.trim().orEmpty()
                    val rawLinkText = parts.getOrNull(1)?.trim().orEmpty().ifEmpty { url }
                    val id = idCounter++
                    val resolvedUrl = resolveUrl(url, baseUrl)
                    val scheme = detectLinkScheme(url, resolvedUrl, baseUrl)
                    val flags = mutableSetOf<LinkFlag>()

                    val isRemote = isRemoteLink(baseUrl, resolvedUrl)
                    if (isRemote) {
                        flags += LinkFlag.REMOTE
                    }
                    if (parts.size > 1 && parts[1].isNotBlank()) {
                        flags += LinkFlag.HUMAN_READABLE
                    }

                    val extensionFlags = extensionFlags(url, resolvedUrl)
                    flags += extensionFlags

                    if (isQueryLink(scheme, url, resolvedUrl)) {
                        flags += LinkFlag.QUERY
                    }

                    val iconResult = extractLabelIcon(
                        label = rawLinkText,
                        scheme = scheme,
                        isRemote = isRemote
                    )
                    if (iconResult.fromLabel) {
                        flags += LinkFlag.ICON_FROM_LABEL
                    }

                    val mediaMimeType = detectMediaMimeType(url, resolvedUrl)

                    content.add(
                        if (mediaMimeType != null) {
                            GeminiContent.EmbeddedMedia(
                                id = id,
                                url = url,
                                mimeType = mediaMimeType,
                                linkText = iconResult.displayText,
                                state = GeminiContent.EmbeddedMediaState.COLLAPSED
                            )
                        } else {
                            GeminiContent.Link(
                                id = id,
                                url = url,
                                text = iconResult.displayText,
                                resolvedUrl = resolvedUrl,
                                scheme = scheme,
                                flags = flags.toSet(),
                                labelIcon = iconResult.icon
                            )
                        }
                    )
                }

                line.startsWith("###") -> content.add(
                    GeminiContent.Heading(
                        id = idCounter++,
                        level = 3,
                        text = line.removePrefix("###").trimStart()
                    )
                )

                line.startsWith("##") -> content.add(
                    GeminiContent.Heading(
                        id = idCounter++,
                        level = 2,
                        text = line.removePrefix("##").trimStart()
                    )
                )

                line.startsWith("#") -> content.add(
                    GeminiContent.Heading(
                        id = idCounter++,
                        level = 1,
                        text = line.removePrefix("#").trimStart()
                    )
                )

                line.startsWith("* ") -> content.add(
                    GeminiContent.ListItem(
                        id = idCounter++,
                        text = line.removePrefix("* ")
                    )
                )

                line.length > 1 && line[0] == '*' && line[1].isWhitespace() -> content.add(
                    GeminiContent.ListItem(
                        id = idCounter++,
                        text = line.substring(1).trimStart()
                    )
                )

                line.startsWith(">") -> content.add(
                    GeminiContent.Quote(
                        id = idCounter++,
                        text = line.removePrefix(">").trimStart()
                    )
                )

                else -> content.add(GeminiContent.Text(id = idCounter++, text = line))
            }
        }

        // If we end while still in preformatted, add what we have
        if (inPreformatted && preformattedBlock.isNotEmpty()) {
            content.add(
                GeminiContent.Preformatted(
                    id = idCounter,
                    text = preformattedBlock.toString(),
                    alt = preformattedAlt
                )
            )
        }

        return content
    }

    fun detectMediaMimeType(url: String, resolvedUrl: String? = null): String? {
        if (!isGeminiOrRelativeUrl(url, resolvedUrl)) {
            return null
        }
        val pathWithoutQuery = (resolvedUrl ?: url).substringBefore("?").substringBefore("#")
        val extension = pathWithoutQuery.substringAfterLast(".", "").lowercase()
        return mediaExtensionToMime[extension]
    }

    private fun isGeminiOrRelativeUrl(url: String, resolvedUrl: String?): Boolean {
        return try {
            val scheme = URI(resolvedUrl ?: url).scheme?.lowercase(Locale.US)
            scheme == null || scheme == "gemini"
        } catch (_: Exception) {
            !url.contains("://")
        }
    }

    private fun resolveUrl(url: String, baseUrl: String?): String? {
        if (url.isBlank()) return null
        return try {
            if (baseUrl.isNullOrBlank()) {
                if (url.contains("://") || url.startsWith("about:", ignoreCase = true)) {
                    URI(url).toString()
                } else {
                    null
                }
            } else {
                URI(baseUrl).resolve(url).toString()
            }
        } catch (_: Exception) {
            if (url.contains("://") || url.startsWith("about:", ignoreCase = true)) url else null
        }
    }

    private fun detectLinkScheme(url: String, resolvedUrl: String?, baseUrl: String?): LinkScheme {
        val scheme = parseScheme(resolvedUrl ?: url) ?: parseScheme(baseUrl)

        return when (scheme) {
            "gemini", null -> LinkScheme.GEMINI
            "titan" -> LinkScheme.TITAN
            "gopher" -> LinkScheme.GOPHER
            "finger" -> LinkScheme.FINGER
            "http", "https" -> LinkScheme.HTTP
            "file" -> LinkScheme.FILE
            "data" -> LinkScheme.DATA
            "about" -> LinkScheme.ABOUT
            "mailto" -> LinkScheme.MAILTO
            "spartan" -> LinkScheme.SPARTAN
            "nex" -> LinkScheme.NEX
            "misfin" -> LinkScheme.MISFIN
            "guppy" -> LinkScheme.GUPPY
            else -> LinkScheme.UNKNOWN
        }
    }

    private fun parseScheme(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            URI(url).scheme?.lowercase(Locale.US)
        } catch (_: Exception) {
            null
        }
    }

    private fun isRemoteLink(baseUrl: String?, resolvedUrl: String?): Boolean {
        if (baseUrl.isNullOrBlank() || resolvedUrl.isNullOrBlank()) return false
        return try {
            val baseHost = URI(baseUrl).host?.lowercase(Locale.US).orEmpty()
            val targetHost = URI(resolvedUrl).host?.lowercase(Locale.US).orEmpty()
            baseHost.isNotEmpty() && targetHost.isNotEmpty() && baseHost != targetHost
        } catch (_: Exception) {
            false
        }
    }

    // Lagrange-compatible extension sets (includes formats not in mediaExtensionToMime)
    // Though I have to make my own decoders for at least psd and hdr I think?
    // TODO: actually check this
    private val imageExtensions = setOf(
        "gif", "jpg", "jpeg", "png", "tga", "psd", "hdr", "jxl", "webp", "pic"
    )
    private val audioExtensions = setOf("mp3", "wav", "ogg", "opus", "mid") // also midi will NOT work

    private fun extensionFlags(url: String, resolvedUrl: String?): Set<LinkFlag> {
        val path = (resolvedUrl ?: url).substringBefore("?").substringBefore("#").lowercase(Locale.US)
        if (path.isBlank()) return emptySet()
        val ext = path.substringAfterLast('.', "")
        if (ext.isEmpty()) return emptySet()

        val flags = mutableSetOf<LinkFlag>()
        if (ext in imageExtensions) flags += LinkFlag.IMAGE_EXT
        if (ext in audioExtensions) flags += LinkFlag.AUDIO_EXT
        if (ext == "fontpack") flags += LinkFlag.FONTPACK_EXT
        return flags
    }

    private fun isQueryLink(scheme: LinkScheme, url: String, resolvedUrl: String?): Boolean {
        if (url.endsWith("?") || url.contains("%s")) return true
        if (scheme == LinkScheme.GOPHER) {
            return try {
                URI(resolvedUrl ?: url).path?.startsWith("/7") == true
            } catch (_: Exception) {
                false
            }
        }
        return false
    }

    private fun extractLabelIcon(
        label: String,
        scheme: LinkScheme,
        isRemote: Boolean
    ): LabelIconResult {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) {
            return LabelIconResult(displayText = label, icon = null, fromLabel = false)
        }

        if (!customIconAllowedForScheme(scheme, isRemote)) {
            return LabelIconResult(displayText = trimmed, icon = null, fromLabel = false)
        }

        val firstIcon = readFirstIconToken(trimmed) ?: return LabelIconResult(
            displayText = trimmed,
            icon = null,
            fromLabel = false
        )

        val firstCodePoint = trimmed.codePointAt(0)
        val firstIsAllowed = when {
            scheme == LinkScheme.MAILTO || scheme == LinkScheme.MISFIN -> firstCodePoint == 0x1F4E7
            else -> isAllowedLinkIcon(firstCodePoint)
        }
        if (!firstIsAllowed) {
            return LabelIconResult(displayText = trimmed, icon = null, fromLabel = false)
        }

        val remaining = trimmed.substring(firstIcon.endIndex).trimStart()
        if (remaining.isEmpty()) {
            return LabelIconResult(displayText = trimmed, icon = null, fromLabel = false)
        }

        val nextCodePoint = remaining.codePointAt(0)
        if (isAllowedLinkIcon(nextCodePoint)) {
            return LabelIconResult(displayText = trimmed, icon = null, fromLabel = false)
        }

        return LabelIconResult(displayText = remaining, icon = firstIcon.icon, fromLabel = true)
    }

    private fun customIconAllowedForScheme(scheme: LinkScheme, isRemote: Boolean): Boolean {
        return (scheme == LinkScheme.GEMINI && !isRemote) ||
            scheme == LinkScheme.ABOUT ||
            scheme == LinkScheme.FILE ||
            scheme == LinkScheme.MAILTO ||
            scheme == LinkScheme.MISFIN ||
            scheme == LinkScheme.UNKNOWN
    }

    private data class IconToken(
        val icon: String,
        val endIndex: Int
    )

    private fun readFirstIconToken(text: String): IconToken? {
        if (text.isEmpty()) return null

        val first = text.codePointAt(0)
        var end = Character.charCount(first)

        if (isRegionalIndicatorLetter(first) && text.length > end) {
            val second = text.codePointAt(end)
            if (isRegionalIndicatorLetter(second)) {
                end += Character.charCount(second)
            }
        }

        if (text.length > end) {
            val variationSelector = text.codePointAt(end)
            if (variationSelector == 0xFE0F) {
                end += Character.charCount(variationSelector)
            }
        }

        return IconToken(
            icon = text.substring(0, end),
            endIndex = end
        )
    }

    private fun isRegionalIndicatorLetter(codePoint: Int): Boolean {
        return codePoint in 0x1F1E6..0x1F1FF
    }

    private fun isAllowedLinkIcon(codePoint: Int): Boolean {
        if (codePoint in 0x1F3FB..0x1F3FF) {
            return false
        }
        return codePoint in 0x1F300..0x1FAFF ||
            codePoint in 0x2600..0x27BF ||
            isRegionalIndicatorLetter(codePoint) ||
            codePoint == 0x2022 ||
            codePoint == 0x2139 ||
            (codePoint in 0x2190..0x21FF) ||
            codePoint == 0x29BF ||
            codePoint == 0x2A2F ||
            (codePoint in 0x2B00..0x2BFF) ||
            codePoint == 0x20BF ||
            (codePoint in 0x1F191..0x1F19A)
    }
}
