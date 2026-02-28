package mysh.dev.gemcap.data
import org.junit.Assert.assertEquals
import org.junit.Test
class SearchEngineTest {
    @Test
    fun gemcapBuildsExpectedUrl() {
        val actual = SearchEngine.GEMCAP.buildSearchUrl("hello world")
        assertEquals("gemini://gemini-search.mysh.dev/?hello+world", actual)
    }
    @Test
    fun kennedyBuildsExpectedUrl() {
        val actual = SearchEngine.KENNEDY.buildSearchUrl("capsule test")
        assertEquals("gemini://kennedy.gemi.dev/search?capsule+test", actual)
    }
    @Test
    fun tlgsBuildsExpectedUrl() {
        val actual = SearchEngine.TLGS.buildSearchUrl("with/slash")
        assertEquals("gemini://tlgs.one/search?with%2Fslash", actual)
    }
    @Test
    fun invalidOrdinalFallsBackToGemcap() {
        val actual = SearchEngine.fromOrdinal(-1)
        assertEquals(SearchEngine.GEMCAP, actual)
    }
}