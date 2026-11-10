import java.io.File
import java.io.Serializable
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.w3c.dom.Element

/*
 * The notices for the libraries an app carries, made from the build: each library's license from
 * its Maven POM (or its parent's), the NOTICE files its jar passes on, and the license texts. The
 * phone and the desktop each make their own list (app/build.gradle.kts, desktop/build.gradle.kts),
 * which About shows and the desktop's installers carry beside the app.
 *
 * Headings are set off as "== Heading ==", which About shows as headings. A license the build
 * doesn't know stops the build, so a new library's license gets read before it
 * ships: add its name or address to [knownLicenses], with its text in resources/licenses (the
 * texts there are SPDX's, from github.com/spdx/license-list-data).
 */

/** One library, as its POM describes it. */
data class LibraryPom(
    val group: String,
    val module: String,
    val version: String,
    val name: String?,
    val url: String?,
    /** The licenses as the POM names them: (name, url). */
    val licenses: List<Pair<String, String>>,
) : Serializable

/**
 * A license the build knows, and its text in buildSrc/src/main/resources/licenses/<key>.txt; or,
 * for terms that aren't an open license (Google's, for ML Kit), where to read them.
 */
private data class KnownLicense(
    val key: String,
    val title: String,
    val termsAt: String? = null,
    val matches: (name: String, url: String) -> Boolean,
)

private fun String.has(vararg parts: String) = parts.all { contains(it, ignoreCase = true) }

private val knownLicenses = listOf(
    KnownLicense("Apache-2.0", "Apache License 2.0") { n, u ->
        (n.has("apache") && (n.contains("2") || n.has("asl"))) || n.equals("ASL2", true) || u.has("apache.org/licenses/LICENSE-2.0") || u.has("opensource.org/licenses/Apache-2.0")
    },
    KnownLicense("MIT", "MIT License") { n, u -> n.equals("MIT", true) || n.has("MIT License") || u.has("opensource.org/licenses/MIT") || u.has("/licenses/mit") },
    KnownLicense("BSD-3-Clause", "BSD 3-Clause License") { n, u ->
        n.has("BSD") && (n.contains("3") || n.has("new") || n.has("revised")) || u.has("BSD-3-Clause") || u.has("bsd-license.php")
    },
    KnownLicense("BSD-2-Clause", "BSD 2-Clause License") { n, u -> n.has("BSD") && n.contains("2") || u.has("BSD-2-Clause") },
    KnownLicense("LGPL-2.1", "GNU Lesser General Public License 2.1") { n, u ->
        (n.has("LGPL") || n.has("Lesser General Public")) && n.contains("2.1") || u.has("lgpl-2.1") || u.has("old-licenses/lgpl-2.1")
    },
    KnownLicense("GPL-2.0-with-classpath-exception", "GNU General Public License 2.0 with the Classpath Exception") { n, u ->
        (n.has("GPL") || n.has("General Public")) && (n.has("CE") || n.has("classpath") || n.has("CPE")) || u.has("gplv2+ce")
    },
    KnownLicense("EPL-2.0", "Eclipse Public License 2.0") { n, u -> n.has("Eclipse Public License") && n.contains("2.0") || u.has("epl-2.0") },
    KnownLicense("EPL-1.0", "Eclipse Public License 1.0") { n, u -> n.has("Eclipse Public License") && n.contains("1.0") || u.has("epl-v10") },
    // Google's libraries for the phone's sticker cutter (ML Kit) and what they bring.
    KnownLicense("ML-Kit-Terms", "ML Kit Terms of Service", termsAt = "https://developers.google.com/ml-kit/terms") { _, u -> u.has("developers.google.com/ml-kit/terms") },
    KnownLicense("Android-SDK-License", "Android Software Development Kit License", termsAt = "https://developer.android.com/studio/terms") { _, u -> u.has("developer.android.com/studio/terms") },
)

private fun knownLicense(name: String, url: String) = knownLicenses.firstOrNull { it.matches(name, url) }

/** Bits of a library's records worth reading in a jar: its license and notice files. */
private val noticeFile = Regex("""(?i)^(META-INF/)?(LICEN[CS]E|NOTICE|COPYING|COPYRIGHT|AL2\.0|LGPL2\.1)([._-][^/]*)?$""")

@CacheableTask
abstract class LibraryNotices : DefaultTask() {
    /** Who the list is for: "the phone", "computers". */
    @get:Input abstract val audience: Property<String>

    @get:Input abstract val appVersion: Property<String>

    @get:Input abstract val libraries: ListProperty<LibraryPom>

    /** A library's jar, by "group:module:version", for the files inside it. */
    @get:Input abstract val jarsById: MapProperty<String, String>

    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) abstract val jars: ConfigurableFileCollection

    /** A note to print with a library, by "group:module" (how it's used, where its source is). */
    @get:Input abstract val notes: MapProperty<String, String>

    /**
     * A library's license by "group:module", where its POM is too vague to tell (jaudiotagger's
     * says only "LGPL"): a key of [knownLicenses], read from the library's own license file.
     */
    @get:Input abstract val licenseOf: MapProperty<String, String>

    /** Paragraphs after the list's introduction (the Java runtime, on a computer). */
    @get:Input abstract val extra: ListProperty<String>

    /** Gets `licenses/libraries.txt`. */
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun write() {
        // A multiplatform library's umbrella module (kotlinx-coroutines-core beside
        // kotlinx-coroutines-core-jvm) has no files of its own: the platform's module is listed.
        val withFiles = jarsById.get().keys
        val libs = libraries.get().filter { it.id in withFiles }.sortedWith(compareBy({ it.displayName.lowercase() }, { it.group }, { it.module }))
        val unknown = mutableListOf<String>()
        // A library under several licenses (JNA: Apache 2.0 or LGPL 2.1) is listed under the first
        // the build knows; its other licenses are named beside it.
        val byLicense = linkedMapOf<KnownLicense, MutableList<Pair<LibraryPom, List<String>>>>()
        for (lib in libs) {
            val known = licenseOf.get()["${lib.group}:${lib.module}"]
                ?.let { key -> listOf(knownLicenses.firstOrNull { it.key == key } ?: throw GradleException("licenseOf: no license $key")) }
                ?: lib.licenses.map { (n, u) -> knownLicense(n, u) }
            val first = known.filterNotNull().firstOrNull()
            if (first == null) {
                unknown += "${lib.id}: ${lib.licenses.ifEmpty { listOf("(none)" to "") }.joinToString { (n, u) -> "$n $u".trim() }}"
                continue
            }
            val others = known.filterNotNull().distinct().filter { it != first }.map { it.title }
            byLicense.getOrPut(first) { mutableListOf() } += lib to others
        }
        if (unknown.isNotEmpty()) {
            throw GradleException(
                "These libraries' licenses aren't known to LibraryNotices (buildSrc). Read them, then add them there:\n" +
                    unknown.joinToString("\n") { "  $it" },
            )
        }

        // The files inside each jar, the same text kept once.
        val jarFiles = jarsById.get()
        class Found(val text: String, val holders: MutableList<String>, val path: String)
        val notices = linkedMapOf<String, Found>()
        val licenseFiles = linkedMapOf<String, Found>()
        val canonical = byLicense.keys.associateWith { license -> license.termsAt?.let { "Google's terms, at $it" } ?: licenseText(license.key) }
        val canonicalLoose = canonical.filterKeys { it.termsAt == null }.values.map { loose(it) }
        for (lib in libs) {
            val jar = jarFiles[lib.id]?.let(::File)?.takeIf { it.isFile && it.name.endsWith(".jar") } ?: continue
            ZipFile(jar).use { zip ->
                for (entry in zip.entries().asSequence().filter { !it.isDirectory && noticeFile.matches(it.name) }.sortedBy { it.name }) {
                    val raw = zip.getInputStream(entry).readBytes().decodeToString().replace("\r\n", "\n").trim()
                    if (raw.isBlank()) continue
                    val isNotice = entry.name.substringAfterLast('/').startsWith("NOTICE", ignoreCase = true)
                    val text = if (isNotice) raw else beyondApache(raw) ?: continue
                    val key = normalize(text)
                    if (!isNotice && canonicalLoose.any { sameText(loose(text), it) }) continue
                    val into = if (isNotice) notices else licenseFiles
                    into.getOrPut(sha(key)) { Found(text, mutableListOf(), entry.name) }.holders += lib.id
                }
            }
        }

        val out = StringBuilder()
        val total = byLicense.values.sumOf { it.size }
        out.appendLine("== Libraries in Fumibako ${appVersion.get()} for ${audience.get()} ==")
        out.appendLine()
        out.appendLine(
            "Fumibako is free software under the GNU General Public License, version 3 or later. It's " +
                "built with these $total libraries, each under its own license. The build makes this list " +
                "from the libraries' own records: their Maven descriptions and the license and notice files inside them.",
        )
        for (paragraph in extra.get()) {
            out.appendLine()
            out.appendLine(paragraph)
        }
        val notesByModule = notes.get()
        for ((license, members) in byLicense.entries.sortedByDescending { it.value.size }) {
            out.appendLine()
            out.appendLine()
            out.appendLine("== ${license.title} (${members.size}) ==")
            for ((lib, others) in members) {
                out.appendLine()
                out.append(lib.displayName).append(' ').append(lib.version)
                out.append(" (").append(lib.group).append(':').append(lib.module).append(')')
                if (others.isNotEmpty()) out.append(", or ").append(others.joinToString(" or "))
                lib.url?.let { out.append("\n    ").append(it) }
                notesByModule["${lib.group}:${lib.module}"]?.let { out.append("\n    ").append(it) }
                out.appendLine()
            }
        }
        if (notices.isNotEmpty()) {
            out.appendLine()
            out.appendLine()
            out.appendLine("== Notices the libraries pass on ==")
            for (found in notices.values) {
                out.appendLine()
                out.appendLine("From ${found.holders.joinToString()} (${found.path}):")
                out.appendLine()
                out.appendLine(found.text)
            }
        }
        out.appendLine()
        out.appendLine()
        out.appendLine("== The licenses ==")
        for ((license, text) in canonical.entries.sortedByDescending { byLicense.getValue(it.key).size }) {
            out.appendLine()
            out.appendLine("${license.title}:")
            out.appendLine()
            out.appendLine(text.trim())
        }
        for (found in licenseFiles.values) {
            out.appendLine()
            out.appendLine("In ${found.holders.joinToString()} (${found.path}):")
            out.appendLine()
            out.appendLine(found.text)
        }

        val file = outputDir.get().file("licenses/libraries.txt").asFile
        file.parentFile.mkdirs()
        file.writeText(out.toString())
    }

    private fun licenseText(key: String): String =
        (LibraryNotices::class.java.getResourceAsStream("/licenses/$key.txt") ?: throw GradleException("buildSrc has no text for $key"))
            .use { it.readBytes().decodeToString().replace("\r\n", "\n") }

    /**
     * A license file that starts with the Apache License (PDFBox's adds the licenses of what it
     * includes after it): only what follows, or null when nothing does.
     */
    private fun beyondApache(text: String): String? {
        if (!text.has("Apache License") || !text.has("Version 2.0, January 2004")) return text
        val end = "limitations under the License."
        val at = text.lastIndexOf(end).takeIf { it >= 0 } ?: return null
        val rest = text.substring(at + end.length).trim()
        return rest.takeIf { it.length > 200 }
    }

    /** Only the words, for telling a re-wrapped copy of a license from the license. */
    private fun loose(text: String) = text.lowercase().replace(Regex("[^a-z0-9]+"), "")

    /** The same license, give or take a title, a copyright line or a closing "how to apply" section. */
    private fun sameText(a: String, b: String): Boolean {
        if (a.length < 500 || b.length < 500) return a == b
        val core = b.substring(b.length / 10, b.length / 10 + 400)
        return a.contains(core) && kotlin.math.abs(a.length - b.length) < b.length / 8
    }

    private fun normalize(text: String) = text.replace(Regex("\\s+"), " ").trim()

    private fun sha(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

private val LibraryPom.id get() = "$group:$module:$version"
private val LibraryPom.displayName get() = name?.takeIf { it.isNotBlank() && !it.contains("\${") } ?: module

/**
 * Registers a [LibraryNotices] task over the libraries of [configurationName] (every external
 * library it resolves to, not the project's own modules).
 */
fun Project.libraryNotices(taskName: String, configurationName: String, configure: LibraryNotices.() -> Unit = {}): TaskProvider<LibraryNotices> {
    val configuration = configurations.named(configurationName)
    val root = configuration.flatMap { it.incoming.resolutionResult.rootComponent }
    val artifacts = configuration.map { conf ->
        conf.incoming.artifactView {
            componentFilter { it is ModuleComponentIdentifier }
            // On Android, the jars inside the libraries' AARs.
            if (conf.attributes.keySet().any { it.name.startsWith("com.android.build") || it.name == "com.android.build.api.attributes.BuildTypeAttr" }) {
                attributes.attribute(Attribute.of("artifactType", String::class.java), "android-classes-jar")
            }
        }.artifacts
    }
    return tasks.register(taskName, LibraryNotices::class.java) {
        group = "documentation"
        description = "Writes the notices for the libraries in $configurationName."
        libraries.set(root.map { readPoms(it) })
        jars.from(artifacts.map { it.artifactFiles })
        jarsById.set(
            artifacts.flatMap { collection ->
                collection.resolvedArtifacts.map { list ->
                    list.associate { (it.id.componentIdentifier as ModuleComponentIdentifier).let { c -> "${c.group}:${c.module}:${c.version}" } to it.file.absolutePath }
                }
            },
        )
        notes.convention(emptyMap())
        licenseOf.convention(emptyMap())
        extra.convention(emptyList())
        outputDir.set(layout.buildDirectory.dir("generated/libraryNotices/$taskName"))
        configure()
    }
}

/** Every external library below [root], from its POM and its parents'. */
private fun Project.readPoms(root: ResolvedComponentResult): List<LibraryPom> {
    val modules = sortedSetOf<String>()
    val seen = mutableSetOf<ResolvedComponentResult>()
    fun walk(component: ResolvedComponentResult) {
        if (!seen.add(component)) return
        (component.id as? ModuleComponentIdentifier)?.let { modules += "${it.group}:${it.module}:${it.version}" }
        component.dependencies.filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>().forEach { walk(it.selected) }
    }
    walk(root)

    val poms = mutableMapOf<String, Pom?>()
    fun fetch(ids: Collection<String>) {
        val wanted = ids.filter { it !in poms }
        if (wanted.isEmpty()) return
        val query = dependencies.createArtifactResolutionQuery()
        wanted.forEach { val (g, m, v) = it.split(':'); query.forModule(g, m, v) }
        val result = query.withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java).execute()
        val found = result.resolvedComponents.associate { component ->
            val id = component.id as ModuleComponentIdentifier
            val file = component.getArtifacts(MavenPomArtifact::class.java).filterIsInstance<ResolvedArtifactResult>().firstOrNull()?.file
            "${id.group}:${id.module}:${id.version}" to file?.let(::parsePom)
        }
        wanted.forEach { poms[it] = found[it] }
        fetch(found.values.mapNotNull { it?.parent })
    }
    fetch(modules)

    fun inherited(id: String?, depth: Int = 0): Pom? = id?.let { poms[it] }?.let { pom ->
        if (depth > 10) return pom
        val parent = inherited(pom.parent, depth + 1) ?: return pom
        pom.copy(licenses = pom.licenses.ifEmpty { parent.licenses }, url = pom.url ?: parent.url)
    }
    return modules.map { id ->
        val (g, m, v) = id.split(':')
        val pom = inherited(id)
        LibraryPom(g, m, v, pom?.name, pom?.url, pom?.licenses.orEmpty())
    }
}

private data class Pom(val name: String?, val url: String?, val licenses: List<Pair<String, String>>, val parent: String?)

private fun parsePom(file: File): Pom? = runCatching {
    val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }.newDocumentBuilder().parse(file)
    val project = doc.documentElement
    fun Element.child(name: String): Element? = (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().firstOrNull { it.tagName == name }
    fun Element.children(name: String) = (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == name }
    fun Element.text(name: String) = child(name)?.textContent?.trim()?.takeIf { it.isNotEmpty() && !it.contains("\${") }
    val licenses = project.child("licenses")?.children("license").orEmpty().map { (it.text("name") ?: "") to (it.text("url") ?: "") }
    val parent = project.child("parent")?.let { p ->
        val g = p.text("groupId"); val a = p.text("artifactId"); val v = p.text("version")
        if (g != null && a != null && v != null) "$g:$a:$v" else null
    }
    Pom(project.text("name")?.replace(Regex("\\s+"), " "), project.text("url"), licenses, parent)
}.getOrNull()
