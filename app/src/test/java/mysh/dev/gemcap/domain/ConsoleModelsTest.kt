package mysh.dev.gemcap.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConsoleModelsTest {

    @Test
    fun `ConsoleEntry defaults have null detail`() {
        val entry = ConsoleEntry(
            id = 1,
            timestamp = 1000L,
            category = ConsoleCategory.NETWORK,
            level = ConsoleLevel.INFO,
            title = "test"
        )
        assertEquals(1L, entry.id)
        assertEquals(ConsoleCategory.NETWORK, entry.category)
        assertEquals(ConsoleLevel.INFO, entry.level)
        assertEquals("test", entry.title)
        assertNull(entry.detail)
    }

    @Test
    fun `ConsoleEntry with detail`() {
        val entry = ConsoleEntry(
            id = 2,
            timestamp = 2000L,
            category = ConsoleCategory.ERROR,
            level = ConsoleLevel.ERROR,
            title = "Connection failed",
            detail = "ECONNREFUSED at example.com:1965"
        )
        assertEquals("Connection failed", entry.title)
        assertEquals("ECONNREFUSED at example.com:1965", entry.detail)
    }

    @Test
    fun `all categories exist`() {
        val categories = ConsoleCategory.entries
        assertEquals(4, categories.size)
        assert(ConsoleCategory.NETWORK in categories)
        assert(ConsoleCategory.ERROR in categories)
        assert(ConsoleCategory.SECURITY in categories)
        assert(ConsoleCategory.LOGCAT in categories)
    }

    @Test
    fun `all levels exist`() {
        val levels = ConsoleLevel.entries
        assertEquals(3, levels.size)
        assert(ConsoleLevel.INFO in levels)
        assert(ConsoleLevel.WARNING in levels)
        assert(ConsoleLevel.ERROR in levels)
    }
}
