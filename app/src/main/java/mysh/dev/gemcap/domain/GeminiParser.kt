package mysh.dev.gemcap.domain

import java.net.URI

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

    fun parse(text: String): List<GeminiContent> {
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
                    val url = parts.getOrNull(0) ?: ""
                    val linkText = parts.getOrNull(1) ?: url
                    val id = idCounter++
                    val mediaMimeType = detectMediaMimeType(url)

                    content.add(
                        if (mediaMimeType != null) {
                            GeminiContent.EmbeddedMedia(
                                id = id,
                                url = url,
                                mimeType = mediaMimeType,
                                linkText = linkText,
                                state = GeminiContent.EmbeddedMediaState.COLLAPSED
                            )
                        } else {
                            GeminiContent.Link(id = id, url = url, text = linkText)
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

                line.startsWith("*") && line.length > 1 -> content.add(
                    GeminiContent.ListItem(
                        id = idCounter++,
                        text = line.removePrefix("*").trimStart()
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

    fun detectMediaMimeType(url: String): String? {
        if (!isGeminiOrRelativeUrl(url)) {
            return null
        }
        val pathWithoutQuery = url.substringBefore("?").substringBefore("#")
        val extension = pathWithoutQuery.substringAfterLast(".", "").lowercase()
        return mediaExtensionToMime[extension]
    }

    private fun isGeminiOrRelativeUrl(url: String): Boolean {
        return try {
            val scheme = URI(url).scheme?.lowercase()
            scheme == null || scheme == "gemini"
        } catch (_: Exception) {
            !url.contains("://")
        }
    }
}
