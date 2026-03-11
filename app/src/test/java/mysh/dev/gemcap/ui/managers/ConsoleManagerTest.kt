package mysh.dev.gemcap.ui.managers

import mysh.dev.gemcap.domain.ConsoleCategory
import mysh.dev.gemcap.domain.ConsoleLevel
import mysh.dev.gemcap.ui.model.PanelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConsoleManagerTest {

    private lateinit var manager: ConsoleManager
    private var panelState = PanelState()

    @Before
    fun setup() {
        manager = ConsoleManager(
            getPanelState = { panelState },
            updatePanelState = { panelState = it }
        )
    }

    @Test
    fun `log adds entry to entries list`() {
        manager.log(ConsoleCategory.NETWORK, ConsoleLevel.INFO, "GET gemini://example.com")
        assertEquals(1, manager.entries.size)
        assertEquals("GET gemini://example.com", manager.entries[0].title)
    }

    @Test
    fun `log assigns incrementing IDs`() {
        manager.log(ConsoleCategory.NETWORK, ConsoleLevel.INFO, "first")
        manager.log(ConsoleCategory.NETWORK, ConsoleLevel.INFO, "second")
        assertTrue(manager.entries[1].id > manager.entries[0].id)
    }

    @Test
    fun `buffer caps at max size`() {
        repeat(1100) { i ->
            manager.log(ConsoleCategory.NETWORK, ConsoleLevel.INFO, "entry $i")
        }
        assertEquals(1000, manager.entries.size)
        assertEquals("entry 100", manager.entries[0].title)
        assertEquals("entry 1099", manager.entries[999].title)
    }

    @Test
    fun `clear removes all entries`() {
        manager.log(ConsoleCategory.NETWORK, ConsoleLevel.INFO, "test")
        manager.log(ConsoleCategory.ERROR, ConsoleLevel.ERROR, "error")
        manager.clear()
        assertTrue(manager.entries.isEmpty())
    }

    @Test
    fun `errorCount counts only ERROR level entries in ERROR category`() {
        manager.log(ConsoleCategory.NETWORK, ConsoleLevel.INFO, "ok")
        manager.log(ConsoleCategory.ERROR, ConsoleLevel.ERROR, "fail1")
        manager.log(ConsoleCategory.ERROR, ConsoleLevel.WARNING, "warn")
        manager.log(ConsoleCategory.ERROR, ConsoleLevel.ERROR, "fail2")
        assertEquals(2, manager.errorCount)
    }

    @Test
    fun `entriesForCategory filters correctly`() {
        manager.log(ConsoleCategory.NETWORK, ConsoleLevel.INFO, "net1")
        manager.log(ConsoleCategory.ERROR, ConsoleLevel.ERROR, "err1")
        manager.log(ConsoleCategory.SECURITY, ConsoleLevel.INFO, "sec1")
        manager.log(ConsoleCategory.NETWORK, ConsoleLevel.INFO, "net2")

        val networkEntries = manager.entriesForCategory(ConsoleCategory.NETWORK)
        assertEquals(2, networkEntries.size)
        assertEquals("net1", networkEntries[0].title)
        assertEquals("net2", networkEntries[1].title)
    }

    @Test
    fun `showConsole and dismissConsole update panel state`() {
        manager.showConsole()
        assertTrue(panelState.showConsole)
        manager.dismissConsole()
        assertTrue(!panelState.showConsole)
    }
}
