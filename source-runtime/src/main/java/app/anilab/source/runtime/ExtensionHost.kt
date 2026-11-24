package app.anilab.source.runtime

import android.content.Context
import android.content.pm.PackageManager
import app.anilab.source.api.EXTENSION_API_VERSION
import app.anilab.source.api.EXTENSION_META_PATH
import app.anilab.source.api.ExtensionApiMismatchException
import app.anilab.source.api.FACTORY_FALLBACK_PATH
import app.anilab.source.api.FACTORY_SERVICE_PATH
import app.anilab.source.api.Source
import app.anilab.source.api.SourceFactory
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipFile

/**
 * Lives in the `:extension` process. Loads installed extension APKs into a child class loader and
 * keeps the instantiated sources. Any throwable from third-party code stays inside this process.
 */
object ExtensionHost {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val loaded = LinkedHashMap<String, LoadedExtension>()

    private class LoadedExtension(val meta: ExtensionMeta, val entries: List<Entry>)

    private class Entry(val descriptor: SourceDescriptor, val source: Source)

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

        val meta = readMeta(apk)
        if (meta.apiVersion != EXTENSION_API_VERSION) {
            throw ExtensionApiMismatchException(EXTENSION_API_VERSION, meta.apiVersion)
        }

        val parent = SourceFactory::class.java.classLoader ?: ExtensionHost::class.java.classLoader
        val loader = PathClassLoader(apk.absolutePath, parent)
        val factories = readFactoryNames(apk, meta).map { instantiateFactory(it, loader) }

        val entries = factories.map { it.create() }.map { source ->
            Entry(
                descriptor = SourceDescriptor(
                    pkg = pkg,
                    sourceId = source.sourceId,
                    name = source.name,
                    lang = source.lang,
                    versionId = source.versionId,
                ),
                source = source,
            )
        }
        if (entries.isEmpty()) throw ExtensionLoadException("No sources were found in $pkg")

        val result = LoadedExtension(meta, entries)
        loaded[pkg] = result
        return result
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
