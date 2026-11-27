package app.aniverse.source.runtime

import dalvik.system.PathClassLoader

import android.content.Context
import android.content.pm.PackageManager
import app.aniverse.source.api.EXTENSION_API_VERSION
import app.aniverse.source.api.EXTENSION_META_PATH
import app.aniverse.source.api.ExtensionApiMismatchException
import app.aniverse.source.api.FACTORY_FALLBACK_PATH
import app.aniverse.source.api.FACTORY_SERVICE_PATH
import app.aniverse.source.api.Source
import app.aniverse.source.api.SourceFactory
import eu.kanade.tachiyomi.AniyomiHost
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipFile

/**
 * Lives in the `:extension` process. Loads installed extension APKs into a child class loader and
 * keeps the instantiated sources. Any throwable from third-party code stays inside this process.
 *
 * Two extension contracts are supported: AniVerse's own `source-api` factories and Aniyomi
 * extensions, which are wrapped by [AniyomiSourceAdapter].
 */
object ExtensionHost {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val loaded = LinkedHashMap<String, LoadedExtension>()

    private class LoadedExtension(val meta: ExtensionMeta, val entries: List<Entry>)

    private class Entry(val descriptor: SourceDescriptor, val source: Source)

    /** The Aniyomi services entry older extensions use instead of manifest metadata. */
    private const val ANIYOMI_FACTORY_SERVICE =
        "META-INF/services/eu.kanade.tachiyomi.animesource.AnimeSourceFactory"

    fun apiVersion(context: Context, pkg: String): Int = synchronized(this) {
        load(context, pkg).meta.apiVersion
    }

    fun descriptors(context: Context, pkg: String): List<SourceDescriptor> = synchronized(this) {
        load(context, pkg).entries.map { it.descriptor }
    }

    fun source(context: Context, pkg: String, sourceId: Long): Source = synchronized(this) {
        load(context, pkg).entries.firstOrNull { it.descriptor.sourceId == sourceId }?.source
            ?: throw ExtensionLoadException("Source $sourceId is not provided by $pkg")
    }

    fun forget(pkg: String) {
        synchronized(this) { loaded.remove(pkg) }
    }

    private fun load(context: Context, pkg: String): LoadedExtension {
        loaded[pkg]?.let { return it }

        val info = try {
            context.packageManager.getApplicationInfo(pkg, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            throw ExtensionLoadException("Extension $pkg is not installed")
        }
        val apk = File(info.sourceDir)
        if (!apk.isFile) throw ExtensionLoadException("Extension files are missing for $pkg")

        // Aniyomi extensions resolve Application/Json/network through the host at load time.
        AniyomiHost.init(context)

        val result = when (val format = ExtensionFormats.inspect(context, apk)) {
            is ExtensionFormat.AniVerse -> loadAniVerse(pkg, apk)
            is ExtensionFormat.Aniyomi -> loadAniyomi(pkg, apk, format)
            is ExtensionFormat.Unsupported -> throw ExtensionLoadException(format.reason)
        }
        loaded[pkg] = result
        return result
    }

    private fun loadAniVerse(pkg: String, apk: File): LoadedExtension {
        val meta = readMeta(apk)
        if (meta.apiVersion != EXTENSION_API_VERSION) {
            throw ExtensionApiMismatchException(EXTENSION_API_VERSION, meta.apiVersion)
        }

        val parent = SourceFactory::class.java.classLoader ?: ExtensionHost::class.java.classLoader
        val loader = PathClassLoader(apk.absolutePath, parent)
        val factories = readFactoryNames(apk, meta).map { instantiateFactory(it, loader) }

        val entries = factories.map { it.create() }.map { source -> entryOf(pkg, source) }
        if (entries.isEmpty()) throw ExtensionLoadException("No sources were found in $pkg")

        return LoadedExtension(meta, entries)
    }

    private fun loadAniyomi(pkg: String, apk: File, format: ExtensionFormat.Aniyomi): LoadedExtension {
        if (ExtensionFormats.isNewerThanSupported(format.libVersion)) {
            throw ExtensionLoadException(
                "This extension needs a newer Aniyomi extension library (${format.libVersion}) " +
                    "than this build of AniVerse provides",
            )
        }

        val className = format.factoryClass?.takeIf { it.isNotBlank() } ?: readAniyomiService(apk)
        if (className.isNullOrBlank()) {
            throw ExtensionLoadException("This Aniyomi extension does not name a source class")
        }

        val parent = AnimeSource::class.java.classLoader ?: ExtensionHost::class.java.classLoader
        val loader = PathClassLoader(apk.absolutePath, parent)
        val sources = instantiateAniyomi(className, loader)
        if (sources.isEmpty()) throw ExtensionLoadException("No sources were found in $pkg")

        val entries = sources.map { source -> entryOf(pkg, AniyomiSourceAdapter(source)) }
        val meta = ExtensionMeta(apiVersion = EXTENSION_API_VERSION, name = null, factory = className)
        return LoadedExtension(meta, entries)
    }

    private fun entryOf(pkg: String, source: Source): Entry = Entry(
        descriptor = SourceDescriptor(
            pkg = pkg,
            sourceId = source.sourceId,
            name = source.name,
            lang = source.lang,
            versionId = source.versionId,
        ),
        source = source,
    )

    private fun instantiateAniyomi(className: String, loader: ClassLoader): List<AnimeSource> {
        val type = Class.forName(className, true, loader)
        val instance = try {
            type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        } catch (e: NoSuchMethodException) {
            throw ExtensionLoadException("$className has no no-argument constructor", e)
        }
        return when (instance) {
            is AnimeSourceFactory -> instance.createSources()
            is AnimeSource -> listOf(instance)
            else -> throw ExtensionLoadException(
                "$className is neither an AnimeSource nor an AnimeSourceFactory",
            )
        }
    }

    private fun readAniyomiService(apk: File): String? = ZipFile(apk).use { zip ->
        zip.getEntry(ANIYOMI_FACTORY_SERVICE)?.let { entry ->
            zip.getInputStream(entry).bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                    .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            }
        }
    }

    private fun instantiateFactory(className: String, loader: ClassLoader): SourceFactory {
        val type = Class.forName(className, true, loader)
        val instance = type.getDeclaredConstructor().newInstance()
        return instance as? SourceFactory
            ?: throw ExtensionLoadException("$className does not implement SourceFactory")
    }

    private fun readMeta(apk: File): ExtensionMeta = ZipFile(apk).use { zip ->
        val entry = zip.getEntry(EXTENSION_META_PATH) ?: return@use ExtensionMeta()
        val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
        try {
            json.decodeFromString(ExtensionMeta.serializer(), text)
        } catch (e: Exception) {
            throw ExtensionLoadException("The extension descriptor is not valid JSON", e)
        }
    }

    private fun readFactoryNames(apk: File, meta: ExtensionMeta): List<String> = ZipFile(apk).use { zip ->
        val names = mutableListOf<String>()
        zip.getEntry(FACTORY_SERVICE_PATH)?.let { entry ->
            zip.getInputStream(entry).bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .forEach { names += it }
            }
        }
        if (names.isEmpty()) {
            zip.getEntry(FACTORY_FALLBACK_PATH)?.let { entry ->
                val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }.trim()
                if (text.isNotEmpty()) names += text
            }
        }
        if (names.isEmpty() && !meta.factory.isNullOrBlank()) names += meta.factory
        if (names.isEmpty()) throw ExtensionLoadException("This extension does not declare a SourceFactory")
        names
    }
}
