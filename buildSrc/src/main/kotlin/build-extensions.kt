import org.gradle.api.Project
import org.gradle.kotlin.dsl.expand
import org.gradle.language.jvm.tasks.ProcessResources

val Project.mod: ModData get() = ModData(this)

fun Project.prop(key: String): String? = findProperty(key)?.toString()

/**
 * Reads `versions/<mc>/gradle.properties` from the root explicitly.
 *
 * Gradle only auto-loads a project's own projectDir/gradle.properties. Stonecutter points a version
 * node at `versions/<mc>/`, so `:1.20.1` picks its file up for free — but a loader branch node is
 * `<loader>/versions/<mc>/`, which does not exist, so those projects silently fall through to the
 * root's `[VERSIONED]` placeholders instead. Reading the canonical file keeps one copy of the data
 * rather than duplicating 23 files per loader.
 */
fun Project.versionProps(mc: String): Map<String, String> {
    val file = rootProject.file("versions/$mc/gradle.properties")
    require(file.isFile) { "No versions/$mc/gradle.properties — regenerate with gen_versions.py" }
    return java.util.Properties()
        .apply { file.inputStream().use { load(it) } }
        .entries.associate { it.key.toString() to it.value.toString() }
}

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
