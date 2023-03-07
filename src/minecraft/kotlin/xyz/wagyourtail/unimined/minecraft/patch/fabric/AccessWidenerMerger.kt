package xyz.wagyourtail.unimined.minecraft.patch.fabric

import net.fabricmc.accesswidener.AccessWidenerReader
import net.fabricmc.accesswidener.AccessWidenerVisitor
import net.fabricmc.accesswidener.AccessWidenerWriter
import net.fabricmc.tinyremapper.TinyRemapper
import org.objectweb.asm.Opcodes
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import xyz.wagyourtail.unimined.api.mappings.MappingsProvider
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.MinecraftProvider

class AccessWidenerMerger(private val namespace: String) : AccessWidenerVisitor {
    // Contains the actual transforms. Class names are as class-file internal binary names (forward slash is used
    // instead of period as the package separator).
    private val classAccess: HashMap<String, Access> = HashMap()
    private val methodAccess: HashMap<EntryTriple, Access> = HashMap()
    private val fieldAccess: HashMap<EntryTriple, Access> = HashMap()

    fun writeToAccessWidenerWriter() : AccessWidenerWriter {
        val writer = AccessWidenerWriter()

        writer.visitHeader(namespace)

        classAccess.forEach { (name, access) ->
            access as ClassAccess

            when (access) {
                ClassAccess.DEFAULT -> {}
                ClassAccess.ACCESSIBLE -> {
                    writer.visitClass(name, AccessWidenerReader.AccessType.ACCESSIBLE, false)
                }
                ClassAccess.EXTENDABLE -> {
                    writer.visitClass(name, AccessWidenerReader.AccessType.EXTENDABLE, false)
                }
                ClassAccess.ACCESSIBLE_EXTENDABLE -> {
                    writer.visitClass(name, AccessWidenerReader.AccessType.ACCESSIBLE, false)
                    writer.visitClass(name, AccessWidenerReader.AccessType.EXTENDABLE, false)
                }
            }
        }

        methodAccess.forEach { (triple, access) ->
            access as MethodAccess

            when (access) {
                MethodAccess.DEFAULT -> {}
                MethodAccess.ACCESSIBLE -> {
                    writer.visitMethod(triple.owner, triple.name, triple.desc, AccessWidenerReader.AccessType.ACCESSIBLE, false)
                }
                MethodAccess.EXTENDABLE -> {
                    writer.visitMethod(triple.owner, triple.name, triple.desc, AccessWidenerReader.AccessType.EXTENDABLE, false)
                }
                MethodAccess.ACCESSIBLE_EXTENDABLE -> {
                    writer.visitMethod(triple.owner, triple.name, triple.desc, AccessWidenerReader.AccessType.ACCESSIBLE, false)
                    writer.visitMethod(triple.owner, triple.name, triple.desc, AccessWidenerReader.AccessType.EXTENDABLE, false)
                }
            }
        }

        fieldAccess.forEach { (triple, access) ->
            access as FieldAccess

            when (access) {
                FieldAccess.DEFAULT -> {}
                FieldAccess.ACCESSIBLE -> {
                    writer.visitField(triple.owner, triple.name, triple.desc, AccessWidenerReader.AccessType.ACCESSIBLE, false)
                }
                FieldAccess.MUTABLE -> {
                    writer.visitField(triple.owner, triple.name, triple.desc, AccessWidenerReader.AccessType.MUTABLE, false)
                }
                FieldAccess.ACCESSIBLE_MUTABLE -> {
                    writer.visitField(triple.owner, triple.name, triple.desc, AccessWidenerReader.AccessType.ACCESSIBLE, false)
                    writer.visitField(triple.owner, triple.name, triple.desc, AccessWidenerReader.AccessType.MUTABLE, false)
                }
            }
        }

        return writer
    }

    override fun visitHeader(namespace: String) {
        if (this.namespace != null && this.namespace != namespace) {
            throw RuntimeException(String.format("Namespace mismatch, expected %s got %s", this.namespace, namespace))
        }
    }

    override fun visitClass(name: String, access: AccessWidenerReader.AccessType, transitive: Boolean) {
        classAccess[name] = applyAccess(access, classAccess.getOrDefault(name, ClassAccess.DEFAULT), null)
    }

    override fun visitMethod(
        owner: String,
        name: String,
        descriptor: String,
        access: AccessWidenerReader.AccessType,
        transitive: Boolean
    ) {
        addOrMerge(
            methodAccess,
            EntryTriple(owner, name, descriptor),
            access,
            MethodAccess.DEFAULT
        )
    }

    override fun visitField(
        owner: String,
        name: String,
        descriptor: String,
        access: AccessWidenerReader.AccessType,
        transitive: Boolean
    ) {
        addOrMerge(
            fieldAccess,
            EntryTriple(owner, name, descriptor),
            access,
            FieldAccess.DEFAULT
        )
    }

    private fun addOrMerge(
        map: MutableMap<EntryTriple, Access>,
        entry: EntryTriple,
        access: AccessWidenerReader.AccessType,
        defaultAccess: Access
    ) {
        map[entry] = applyAccess(access, map.getOrDefault(entry, defaultAccess), entry)
    }

    fun applyAccess(
        input: AccessWidenerReader.AccessType,
        access: Access,
        entryTriple: EntryTriple?
    ): Access {
        return when (input) {
            AccessWidenerReader.AccessType.ACCESSIBLE -> {
                makeClassAccessible(entryTriple)
                access.makeAccessible()
            }

            AccessWidenerReader.AccessType.EXTENDABLE -> {
                makeClassExtendable(entryTriple)
                access.makeExtendable()
            }

            AccessWidenerReader.AccessType.MUTABLE -> access.makeMutable()
            else -> throw java.lang.UnsupportedOperationException("Unknown access type:$input")
        }
    }

    private fun makeClassAccessible(entryTriple: EntryTriple?) {
        if (entryTriple == null) return
        classAccess.put(
            entryTriple.owner,
            applyAccess(
                AccessWidenerReader.AccessType.ACCESSIBLE,
                classAccess.getOrDefault(entryTriple.owner, ClassAccess.DEFAULT),
                null
            )
        )
    }

    private fun makeClassExtendable(entryTriple: EntryTriple?) {
        if (entryTriple == null) return
        classAccess.put(
            entryTriple.owner,
            applyAccess(
                AccessWidenerReader.AccessType.EXTENDABLE,
                classAccess.getOrDefault(entryTriple.owner, ClassAccess.DEFAULT),
                null
            )
        )
    }

    fun getClassAccess(className: String): Access {
        return classAccess.getOrDefault(className, ClassAccess.DEFAULT)
    }

    fun getFieldAccess(entryTriple: EntryTriple): Access {
        return fieldAccess.getOrDefault(entryTriple, FieldAccess.DEFAULT)
    }

    fun getMethodAccess(entryTriple: EntryTriple): Access {
        return methodAccess.getOrDefault(entryTriple, MethodAccess.DEFAULT)
    }
}

class AccessWidenerBetterRemapper
/**
 * @param delegate      The visitor to forward the remapped information to.
 * @param toNamespace   The namespace that the access widener will be remapped to.
 */(
    private val delegate: AccessWidenerVisitor,
    private val mappingsProvider: MappingsProvider,
    private val toNamespace: String,
    private val mcProvider: MinecraftProvider<*, *>
) : AccessWidenerVisitor {
    private var remapper: TinyRemapper

    init {
        remapper = TinyRemapper.newRemapper()
            .withMappings(
                mappingsProvider.getMappingsProvider(
                    EnvType.COMBINED,
                    MappingNamespace.OFFICIAL to MappingNamespace.getNamespace(toNamespace),
                    false
                )
            ).build()

        remapper.readClassPathAsync(*mcProvider.mcLibraries.resolve().map { it.toPath() }.toTypedArray())
        remapper.readClassPathAsync(mcProvider.getMinecraftWithMapping(EnvType.COMBINED, MappingNamespace.OFFICIAL, MappingNamespace.getNamespace(toNamespace)))
    }
    override fun visitHeader(namespace: String) {
        remapper = TinyRemapper.newRemapper()
            .withMappings(
                mappingsProvider.getMappingsProvider(
                    EnvType.COMBINED,
                    MappingNamespace.getNamespace(namespace) to MappingNamespace.getNamespace(toNamespace),
                    false
                )
            ).build()

        remapper.readClassPathAsync(*mcProvider.mcLibraries.resolve().map { it.toPath() }.toTypedArray())
        remapper.readClassPathAsync(mcProvider.getMinecraftWithMapping(EnvType.COMBINED, MappingNamespace.getNamespace(namespace), MappingNamespace.getNamespace(toNamespace)))

        delegate.visitHeader(toNamespace)
    }

    override fun visitClass(name: String, access: AccessWidenerReader.AccessType, transitive: Boolean) {
        delegate.visitClass(remapper.environment.remapper.map(name), access, transitive)
    }

    override fun visitMethod(
        owner: String,
        name: String,
        descriptor: String,
        access: AccessWidenerReader.AccessType,
        transitive: Boolean
    ) {
        delegate.visitMethod(
            remapper.environment.remapper.map(owner),
            remapper.environment.remapper.mapMethodName(owner, name, descriptor),
            remapper.environment.remapper.mapDesc(descriptor),
            access,
            transitive
        )
    }

    override fun visitField(
        owner: String,
        name: String,
        descriptor: String,
        access: AccessWidenerReader.AccessType,
        transitive: Boolean
    ) {
        delegate.visitField(
            remapper.environment.remapper.map(owner),
            remapper.environment.remapper.mapFieldName(owner, name, descriptor),
            remapper.environment.remapper.mapDesc(descriptor),
            access,
            transitive
        )
    }
}

fun makePublic(i: Int): Int {
    return i and (Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED).inv() or Opcodes.ACC_PUBLIC
}

fun makeProtected(i: Int): Int {
    return if (i and Opcodes.ACC_PUBLIC != 0) {
        //Return i if public
        i
    } else i and Opcodes.ACC_PRIVATE.inv() or Opcodes.ACC_PROTECTED
}

fun makeFinalIfPrivate(access: Int, name: String, ownerAccess: Int): Int {
    // Dont make constructors final
    if (name == "<init>") {
        return access
    }

    // Skip interface and static methods
    if (ownerAccess and Opcodes.ACC_INTERFACE != 0 || access and Opcodes.ACC_STATIC != 0) {
        return access
    }
    return if (access and Opcodes.ACC_PRIVATE != 0) {
        access or Opcodes.ACC_FINAL
    } else access
}

fun removeFinal(i: Int): Int {
    return i and Opcodes.ACC_FINAL.inv()
}

class EntryTriple(val owner: String, val name: String, val desc: String) {
    override fun toString(): String {
        return "EntryTriple{owner=$owner,name=$name,desc=$desc}"
    }

    override fun hashCode(): Int {
        return owner.hashCode() * 37 + name.hashCode() * 19 + desc.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EntryTriple

        if (owner != other.owner) return false
        if (name != other.name) return false
        if (desc != other.desc) return false

        return true
    }
}

interface Access : AccessOperator {
    fun makeAccessible(): Access
    fun makeExtendable(): Access
    fun makeMutable(): Access
}

enum class ClassAccess(private val operator: AccessOperator) : Access {
    DEFAULT(AccessOperator { access: Int, name: String, ownerAccess: Int -> access }), ACCESSIBLE(
        AccessOperator { access: Int, name: String, ownerAccess: Int ->
            makePublic(
                access
            )
        }),
    EXTENDABLE(
        AccessOperator { access: Int, name: String, ownerAccess: Int ->
            makePublic(
                removeFinal(access)
            )
        }),
    ACCESSIBLE_EXTENDABLE(
        AccessOperator { access: Int, name: String, ownerAccess: Int ->
            makePublic(
                removeFinal(access)
            )
        });

    override fun makeAccessible(): Access {
        return if (this == EXTENDABLE || this == ACCESSIBLE_EXTENDABLE) {
            ACCESSIBLE_EXTENDABLE
        } else ACCESSIBLE
    }

    override fun makeExtendable(): Access {
        return if (this == ACCESSIBLE || this == ACCESSIBLE_EXTENDABLE) {
            ACCESSIBLE_EXTENDABLE
        } else EXTENDABLE
    }

    override fun makeMutable(): Access {
        throw UnsupportedOperationException("Classes cannot be made mutable")
    }

    override fun apply(access: Int, targetName: String, ownerAccess: Int): Int {
        return operator.apply(access, targetName, ownerAccess)
    }
}

enum class MethodAccess(private val operator: AccessOperator) : Access {
    DEFAULT(AccessOperator { access: Int, name: String, ownerAccess: Int -> access }), ACCESSIBLE(
        AccessOperator { access: Int, name: String, ownerAccess: Int ->
            makePublic(
                makeFinalIfPrivate(access, name, ownerAccess)
            )
        }),
    EXTENDABLE(
        AccessOperator { access: Int, name: String, ownerAccess: Int ->
            makeProtected(
                removeFinal(access)
            )
        }),
    ACCESSIBLE_EXTENDABLE(
        AccessOperator { access: Int, name: String, owner: Int ->
            makePublic(
                removeFinal(access)
            )
        });

    override fun makeAccessible(): Access {
        return if (this == EXTENDABLE || this == ACCESSIBLE_EXTENDABLE) {
            ACCESSIBLE_EXTENDABLE
        } else ACCESSIBLE
    }

    override fun makeExtendable(): Access {
        return if (this == ACCESSIBLE || this == ACCESSIBLE_EXTENDABLE) {
            ACCESSIBLE_EXTENDABLE
        } else EXTENDABLE
    }

    override fun makeMutable(): Access {
        throw UnsupportedOperationException("Methods cannot be made mutable")
    }

    override fun apply(access: Int, targetName: String, ownerAccess: Int): Int {
        return operator.apply(access, targetName, ownerAccess)
    }
}

enum class FieldAccess(private val operator: AccessOperator) : Access {
    DEFAULT(AccessOperator { access: Int, name: String, ownerAccess: Int -> access }), ACCESSIBLE(
        AccessOperator { access: Int, name: String, ownerAccess: Int ->
            makePublic(
                access
            )
        }),
    MUTABLE(
        AccessOperator { access: Int, name: String, ownerAccess: Int ->
            if (ownerAccess and Opcodes.ACC_INTERFACE != 0 && access and Opcodes.ACC_STATIC != 0) {
                // Don't make static interface fields mutable.
                return@AccessOperator access
            }
            removeFinal(access)
        }),
    ACCESSIBLE_MUTABLE(AccessOperator { access: Int, name: String, ownerAccess: Int ->
        if (ownerAccess and Opcodes.ACC_INTERFACE != 0 && access and Opcodes.ACC_STATIC != 0) {
            // Don't make static interface fields mutable.
            return@AccessOperator makePublic(access)
        }
        makePublic(removeFinal(access))
    });

    override fun makeAccessible(): Access {
        return if (this == MUTABLE || this == ACCESSIBLE_MUTABLE) {
            ACCESSIBLE_MUTABLE
        } else ACCESSIBLE
    }

    override fun makeExtendable(): Access {
        throw UnsupportedOperationException("Fields cannot be made extendable")
    }

    override fun makeMutable(): Access {
        return if (this == ACCESSIBLE || this == ACCESSIBLE_MUTABLE) {
            ACCESSIBLE_MUTABLE
        } else MUTABLE
    }

    override fun apply(access: Int, targetName: String, ownerAccess: Int): Int {
        return operator.apply(access, targetName, ownerAccess)
    }
}

fun interface AccessOperator {
    fun apply(access: Int, targetName: String, ownerAccess: Int): Int
}