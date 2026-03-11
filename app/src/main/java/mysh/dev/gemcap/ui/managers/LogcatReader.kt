package mysh.dev.gemcap.ui.managers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mysh.dev.gemcap.domain.ConsoleCategory
import mysh.dev.gemcap.domain.ConsoleLevel
import mysh.dev.gemcap.domain.ConsoleLogger
import java.io.BufferedReader

class LogcatReader(
    private val logger: ConsoleLogger,
    private val scope: CoroutineScope
) {

    data class ParsedLine(val level: String, val tag: String, val message: String)

    private var process: Process? = null
    private var readJob: Job? = null

    fun start() {
        if (readJob?.isActive == true) return
        val pid = android.os.Process.myPid()
        readJob = scope.launch(Dispatchers.IO) {
            try {
                val proc = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-v", "threadtime", "--pid=$pid")
                )
                process = proc
                val reader: BufferedReader = proc.inputStream.bufferedReader()
                while (isActive) {
                    val line = reader.readLine() ?: break
                    val parsed = parseLine(line) ?: continue
                    logger.log(
                        category = ConsoleCategory.LOGCAT,
                        level = mapLevel(parsed.level),
                        title = "[${parsed.level}/${parsed.tag}] ${parsed.message}"
                    )
                }
            } catch (_: Exception) {
                // Process was destroyed or IO error — expected on stop
            }
        }
    }

    fun stop() {
        readJob?.cancel()
        readJob = null
        process?.destroy()
        process = null
    }

    companion object {
        // Matches: "03-11 12:01:03.456  1234  5678 D TagName: message"
        private val LINE_REGEX = Regex(
            """^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+\d+\s+\d+\s+([VDIWEF])\s+(\S+?)\s*:\s*(.*)$"""
        )

        fun parseLine(line: String): ParsedLine? {
            val match = LINE_REGEX.matchEntire(line) ?: return null
            return ParsedLine(
                level = match.groupValues[1],
                tag = match.groupValues[2],
                message = match.groupValues[3]
            )
        }

        fun mapLevel(level: String): ConsoleLevel = when (level) {
            "W" -> ConsoleLevel.WARNING
            "E", "F" -> ConsoleLevel.ERROR
            else -> ConsoleLevel.INFO
        }
    }
}
