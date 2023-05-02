package xyz.wagyourtail.unimined.util

class OnceTask<T>(val taskName: String, dependencies: () -> List<OnceTask<*>>, val task: () -> T) {

    var ran = false
        private set

    val dependencies by lazy(dependencies)

    val value by lazy(::run)

    private fun run(from: List<OnceTask<*>> = emptyList()): T {
        if (!ran) {
            if (from.contains(this)) {
                throw IllegalStateException(
                    "Circular dependency detected! ${
                        from.subList(from.indexOf(this), from.size)
                            .joinToString(" -> ") { it.taskName }
                    } -> $taskName"
                )
            }
            dependencies.forEach { it.run(from + this) }
            synchronized(this) {
                if (!ran) {
                    val t = task()
                    ran = true
                    return t
                }
            }
        }
        return value
    }
}