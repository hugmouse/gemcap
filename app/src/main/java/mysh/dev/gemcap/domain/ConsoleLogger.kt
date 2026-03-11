package mysh.dev.gemcap.domain

interface ConsoleLogger {
    fun log(category: ConsoleCategory, level: ConsoleLevel, title: String, detail: String? = null)

    companion object {
        val NoOp: ConsoleLogger = object : ConsoleLogger {
            override fun log(category: ConsoleCategory, level: ConsoleLevel, title: String, detail: String?) {}
        }
    }
}
