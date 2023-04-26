package xyz.wagyourtail.unimined.output

import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.minecraft
import xyz.wagyourtail.unimined.api.output.Output

abstract class OutputImpl<T: Jar, U: Jar>(
    val project: Project,
    val unimined: UniminedExtension,
    val outputProviderImpl: OutputProviderImpl,
    val baseTaskName: String
) : Output<T> {

    val prev
        get() = outputProviderImpl.getBefore(this)

    override var disable: Boolean = false
        // disable if prev is disabled
        get() = field || prev?.disable == true
        set(value) {
            field = value
        }

    protected val taskApplyMap = mutableMapOf<String, T.() -> Unit>()
    protected val taskApplyFirstMap = mutableMapOf<String, T.() -> Unit>()

    protected lateinit var resolved: Map<String, T>

    fun isResolved() = ::resolved.isInitialized

    init {
        @Suppress("USELESS_CAST")
        unimined.events.register (register@{
            if (disable) return@register
            afterEvaluate()
        } as () -> Unit)
    }

    open fun afterEvaluate() {
        if (project.minecraft.combinedSourceSets.isNotEmpty() && !project.minecraft.disableCombined.get()) {
            configFirst("combined") {
                applyEnvConfig(EnvType.COMBINED, this)
            }
        }
        if (project.minecraft.clientSourceSets.isNotEmpty()) {
            configFirst("client") {
                applyEnvConfig(EnvType.CLIENT, this)
            }
        }
        if (project.minecraft.serverSourceSets.isNotEmpty()) {
            configFirst("server") {
                applyEnvConfig(EnvType.SERVER, this)
            }
        }
    }

    abstract fun applyEnvConfig(env: EnvType, task: T)

    protected abstract fun create(name: String): T

    override fun config(named: String, apply: T.() -> Unit) {
        val prev = taskApplyMap[named]
        if (prev != null) {
            taskApplyMap[named] = {
                prev()
                apply()
            }
        } else {
            taskApplyMap[named] = apply
        }
    }

    override fun configFirst(named: String, apply: T.() -> Unit) {
        val prev = taskApplyFirstMap[named]
        if (prev != null) {
            taskApplyFirstMap[named] = {
                prev()
                apply()
            }
        } else {
            taskApplyFirstMap[named] = apply
        }
    }

    override fun configAll(apply: T.(name: String) -> Unit) {
        for (name in (taskApplyFirstMap.keys + taskApplyMap.keys).toSet()) {
            config(name) {
                apply(this, name)
            }
        }
    }

    override fun configAllFirst(apply: T.(name: String) -> Unit) {
        for (name in (taskApplyFirstMap.keys + taskApplyMap.keys).toSet()) {
            configFirst(name) {
                apply(this, name)
            }
        }
    }

    open fun beforeResolve(prev: Map<String, U>?) {
        // do nothing
    }

    open fun beforeResolvePrev() {
        // do nothing
    }

    fun resolve(): Map<String, T> {
        if (this::resolved.isInitialized) return resolved
        // ensure prev has all of this one's tasks
        val currentKeys = taskApplyFirstMap.keys + taskApplyMap.keys
        for (name in currentKeys) {
            prev?.config(name) {}
        }
        beforeResolvePrev()
        val prev = prev?.resolve()
        beforeResolve(prev as Map<String, U>?)
        val res = mutableMapOf<String, Pair<T, T.() -> Unit>>()
        for (name in (currentKeys + (prev?.keys ?: emptySet())).toSet()) {
            res[name] = create("$name${baseTaskName.capitalized()}") to {
                if (taskApplyFirstMap.containsKey(name)) {
                    taskApplyFirstMap[name]!!.invoke(this)
                }
                if (taskApplyMap.containsKey(name)) {
                    taskApplyMap[name]!!.invoke(this)
                }
            }
        }
        resolved = res.mapValues { v ->
            v.value.first.also { entry ->
                prev?.get(v.key)?.let {v.value.first.dependsOn(it)}
                v.value.second.invoke(entry)
            }
        }
        return resolved
    }

}