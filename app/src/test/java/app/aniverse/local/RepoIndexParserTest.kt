package app.aniverse.local

import app.aniverse.local.data.network.RepoIndexParser
import app.aniverse.local.data.network.RepoUrlResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RepoIndexParserTest {

    private val parser = RepoIndexParser()
    private val indexUrl = "https://raw.githubusercontent.com/example/repo/main/index.min.json"

    @Test
    fun parsesABareArray() {
        val body = """[{"pkg":"eu.example.anime","name":"Example","version":"1.2.3","code":12,"lang":"en","apk":"apk/example.apk"}]"""
        val index = parser.parse(body, indexUrl)
        assertNotNull(index)
        val extension = index!!.extensions.single()
        assertEquals("eu.example.anime", extension.pkg)
        assertEquals("Example", extension.name)
        assertEquals("1.2.3", extension.versionName)
        assertEquals(12, extension.versionCode)
        assertEquals("https://raw.githubusercontent.com/example/repo/main/apk/example.apk", extension.apkUrl)
        assertFalse(extension.nsfw)
    }

    @Test
    fun parsesAWrappedObject() {
        val body = """{"extensions":[{"pkg":"a.b","name":"A","version":"1.0","code":1,"apk":"a.apk","nsfw":1}]}"""
        val extension = parser.parse(body, indexUrl)!!.extensions.single()
        assertTrue(extension.nsfw)
        // Bare file names live in the repository's `apk/` folder, as every Aniyomi repo publishes them.
        assertEquals("https://raw.githubusercontent.com/example/repo/main/apk/a.apk", extension.apkUrl)
        // Missing `icon` entries fall back to the conventional `icon/<pkg>.png`.
        assertEquals("https://raw.githubusercontent.com/example/repo/main/icon/a.b.png", extension.iconUrl)
    }

    @Test
    fun explicitIconPathsAreResolvedAgainstTheIndexFolder() {
        val body = """[{"pkg":"a.b","name":"A","version":"1.0","code":1,"apk":"apk/a.apk","icon":"icon/custom.png"}]"""
        val extension = parser.parse(body, indexUrl)!!.extensions.single()
        assertEquals("https://raw.githubusercontent.com/example/repo/main/apk/a.apk", extension.apkUrl)
        assertEquals("https://raw.githubusercontent.com/example/repo/main/icon/custom.png", extension.iconUrl)
    }

    @Test
    fun absoluteApkUrlsAreKept() {
        val body = """[{"pkg":"a.b","name":"A","version":"1.0","code":1,"apk":"https://cdn.example.com/a.apk"}]"""
        assertEquals("https://cdn.example.com/a.apk", parser.parse(body, indexUrl)!!.extensions.single().apkUrl)
    }

    @Test
    fun malformedEntriesAreSkippedNotFatal() {
        val body = """[
            {"pkg":"ok.one","name":"One","version":"1.0","code":1,"apk":"one.apk"},
            {"name":"No package","version":"1.0","apk":"two.apk"},
            {"pkg":"no.apk","name":"No apk","version":"1.0"},
            {"pkg":"bad.json","name":"Wrong types","version":"1.0","code":"not-a-number","apk":"x.apk"}
        ]"""
        val index = parser.parse(body, indexUrl)!!
        assertEquals(1, index.extensions.size)
        assertEquals("ok.one", index.extensions.single().pkg)
    }

    @Test
    fun duplicatePackagesKeepTheFirstEntry() {
        val body = """[
            {"pkg":"dup.pkg","name":"First","version":"2.0","code":2,"apk":"first.apk"},
            {"pkg":"dup.pkg","name":"Second","version":"1.0","code":1,"apk":"second.apk"}
        ]"""
        val index = parser.parse(body, indexUrl)!!
        assertEquals(1, index.extensions.size)
        assertEquals("First", index.extensions.single().name)
    }

    @Test
    fun alternateLayoutSwapsTheApkFolder() {
        assertEquals(
            "https://raw.githubusercontent.com/example/repo/main/a.apk",
            RepoUrlResolver.alternateFileLayout("https://raw.githubusercontent.com/example/repo/main/apk/a.apk"),
        )
        assertEquals(
            "https://raw.githubusercontent.com/example/repo/main/apk/a.apk",
            RepoUrlResolver.alternateFileLayout("https://raw.githubusercontent.com/example/repo/main/a.apk"),
        )
        assertNull(RepoUrlResolver.alternateFileLayout("https://example.com"))
    }

    @Test
    fun nonIndexPayloadsReturnNull() {
        assertNull(parser.parse("", indexUrl))
        assertNull(parser.parse("not json at all", indexUrl))
        assertNull(parser.parse("{\"something\":[]}", indexUrl))
        assertNull(parser.parse("[1,2,3]", indexUrl))
    }
}
