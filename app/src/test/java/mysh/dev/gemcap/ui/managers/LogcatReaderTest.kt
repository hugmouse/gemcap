package mysh.dev.gemcap.ui.managers

import mysh.dev.gemcap.domain.ConsoleLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LogcatReaderTest {

    @Test
    fun `parse threadtime format - debug level`() {
        val line = "03-11 12:01:03.456  1234  5678 D GeminiClient: Fetch start url=gemini://example.com"
        val parsed = LogcatReader.parseLine(line)
        assertNotNull(parsed)
        assertEquals("D", parsed!!.level)
        assertEquals("GeminiClient", parsed.tag)
        assertEquals("Fetch start url=gemini://example.com", parsed.message)
    }

    @Test
    fun `parse threadtime format - error level`() {
        val line = "03-11 12:01:03.456  1234  5678 E GeminiClient: Failed to fetch"
        val parsed = LogcatReader.parseLine(line)
        assertNotNull(parsed)
        assertEquals("E", parsed!!.level)
        assertEquals("GeminiClient", parsed.tag)
        assertEquals("Failed to fetch", parsed.message)
    }

    @Test
    fun `parse threadtime format - warning level`() {
        val line = "03-11 12:01:03.456  1234  5678 W TofuTrustManager: Domain mismatch"
        val parsed = LogcatReader.parseLine(line)
        assertNotNull(parsed)
        assertEquals("W", parsed!!.level)
    }

    @Test
    fun `parse returns null for non-matching lines`() {
        assertNull(LogcatReader.parseLine("--- beginning of main"))
        assertNull(LogcatReader.parseLine(""))
        assertNull(LogcatReader.parseLine("some random text"))
    }

    @Test
    fun `mapLevel maps logcat levels to ConsoleLevel`() {
        assertEquals(ConsoleLevel.INFO, LogcatReader.mapLevel("V"))
        assertEquals(ConsoleLevel.INFO, LogcatReader.mapLevel("D"))
        assertEquals(ConsoleLevel.INFO, LogcatReader.mapLevel("I"))
        assertEquals(ConsoleLevel.WARNING, LogcatReader.mapLevel("W"))
        assertEquals(ConsoleLevel.ERROR, LogcatReader.mapLevel("E"))
        assertEquals(ConsoleLevel.ERROR, LogcatReader.mapLevel("F"))
    }
}
