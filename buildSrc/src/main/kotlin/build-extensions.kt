import org.gradle.api.Project
import org.gradle.kotlin.dsl.expand
import org.gradle.language.jvm.tasks.ProcessResources

val Project.mod: ModData get() = ModData(this)

fun Project.prop(key: String): String? = findProperty(key)?.toString()

fun ProcessResources.properties(files: Iterable<String>, vararg properties: Pair<String, Any>) {
    for ((name, value) in properties) inputs.property(name, value)
    filesMatching(files) { expand(properties.toMap()) }
}

/**
 * Typed access to the `mod.*` / `deps.*` properties.
 *
 * Every lookup is required. A per-version override that is missing would otherwise fall through to
 * the `[VERSIONED]` placeholder in the root gradle.properties and build against a nonsense
 * dependency coordinate, which surfaces as a confusing resolution error on one node rather than as
 * "you forgot to set this".
 */
@JvmInline
value class ModData(private val project: Project) {
    val id: String get() = req("mod.id")
    val name: String get() = req("mod.name")
    val version: String get() = req("mod.version")
    val group: String get() = req("mod.group")

    fun prop(key: String) = req("mod.$key")
    fun dep(key: String) = req("deps.$key")

    /** True when this node targets unobfuscated Minecraft (26.1+), which takes no mappings at all. */
    val unobfuscated: Boolean get() = prop("unobfuscated").toBoolean()

    private fun req(key: String): String {
        val v = project.prop(key)
        require(!v.isNullOrBlank() && v != "[VERSIONED]") {
            "Missing '$key' for ${project.path} — add it to versions/<mc>/gradle.properties"
        }
        return v
    }
}
