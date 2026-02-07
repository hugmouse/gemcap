package mysh.dev.gemcap.domain

object GeminiParser {

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
                    val linkText = parts.getOrNull(1) ?: url // Fallback to URL if no text
                    content.add(GeminiContent.Link(id = idCounter++, url = url, text = linkText))
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
                    id = ++idCounter,
                    text = preformattedBlock.toString(),
                    alt = preformattedAlt
                )
            )
        }

        return content
    }
}
