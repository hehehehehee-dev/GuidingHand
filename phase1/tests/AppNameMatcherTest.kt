package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class AppNameMatcherTest {
    private val zalo = SpokenApp("Zalo", "com.zing.zalo")
    private val instagram = SpokenApp("Instagram", "com.instagram.android")
    private val facebook = SpokenApp("Facebook", "com.facebook.katana")
    private val youtube = SpokenApp("YouTube", "com.google.android.youtube")
    private val messenger = SpokenApp("Messenger", "com.facebook.orca")
    private val installed = listOf(zalo, instagram, facebook, youtube, messenger)
    private fun suggest(vararg text: String) = AppNameMatcher.suggest(text.toList(), installed)

    @Test fun bilingualMispronunciationsAreSuggestionsForTheInstalledApp() {
        mapOf("mở gia lô" to zalo, "tìm za lô" to zalo, "open ja lo" to zalo, "da lo" to zalo,
            "mở in sta" to instagram, "find in sta gram" to instagram, "phây búc" to facebook,
            "face book" to facebook, "fay book" to facebook, "du tub" to youtube,
            "you tube" to youtube, "mét sen giơ" to messenger, "mess en ger" to messenger).forEach { (text, app) ->
            val result = suggest(text).single()
            assertEquals(text, app, result.app)
            assertFalse(text, result.exact)
        }
    }
    @Test fun canonicalLabelsIgnorePoliteCommandWrappers() {
        for (text in listOf("Zalo", "Please open Zalo", "can you find the app Zalo please", "tôi muốn mở ứng dụng Zalo nhé")) {
            assertEquals(text, AppNameMatch(zalo, true), suggest(text).single())
        }
    }
    @Test fun aliasesNeverInventAnUninstalledApp() {
        assertTrue(AppNameMatcher.suggest(listOf("mở gia lô"), listOf(instagram)).isEmpty())
        assertTrue(AppNameMatcher.suggest(listOf("grandchild chat"), emptyList(), mapOf(zalo.packageName to listOf("grandchild chat"))).isEmpty())
    }
    @Test fun closeMisspellingsNeedConfirmation() {
        assertEquals(AppNameMatch(instagram, false), suggest("open Instagran").single())
        assertEquals(AppNameMatch(zalo, false), suggest("open Zall").single())
        assertTrue(suggest("open unrelatedthing").isEmpty())
    }
    @Test fun peopleAndTaskWordsAreNotFuzzilyInterpretedAsApps() {
        val apps = listOf(SpokenApp("Photos", "photos"), SpokenApp("Alice", "alice"), SpokenApp("Gmail", "gmail"))
        for (text in listOf("send a photo", "photos of grandma", "message Alice", "call Gmail", "photo", "find photographic")) {
            assertTrue(text, AppNameMatcher.suggest(listOf(text), apps).isEmpty())
        }
    }
    @Test fun ambiguousNamesAndAliasesKeepAllChoices() {
        val lite = SpokenApp("Zalo Lite", "zalo.lite")
        val aliases = mapOf(zalo.packageName to listOf("family chat"), lite.packageName to listOf("family chat"))
        val results = AppNameMatcher.suggest(listOf("family chat"), listOf(zalo, lite), aliases)
        assertEquals(setOf(zalo, lite), results.map { it.app }.toSet())
        assertTrue(results.none { it.exact })
        assertEquals(setOf(zalo, facebook), suggest("open Zalo or Facebook").map { it.app }.toSet())
        assertTrue(suggest("open Zalo or Facebook").none { it.exact })
    }
    @Test fun alternateHypothesesStayTentativeAndAreDeduplicated() {
        val results = suggest("something unclear", "Zalo", "gia lo", "Instagram")
        assertEquals(listOf(zalo, instagram), results.map { it.app })
        assertTrue(results.none { it.exact })
        assertEquals(1, suggest("Zalo", "gia lo", "Zalo").size)
    }
    @Test fun namedAppInAMessageGoalDoesNotMatchTheRecipient() {
        assertEquals(AppNameMatch(instagram, true), suggest("message Alice in Instagram").single())
        assertEquals(AppNameMatch(zalo, false), suggest("nhắn cho mẹ bằng gia lô").single())
    }
    @Test fun packageScopedFamilyAliasesCanUseNaturalNicknames() {
        val results = AppNameMatcher.suggest(listOf("mở app nói chuyện với cháu"), installed,
            mapOf(zalo.packageName to listOf("nói chuyện với cháu")))
        assertEquals(AppNameMatch(zalo, false), results.single())
    }
    @Test fun boundariesDoNotMatchAnAppInsideAnotherWord() {
        assertTrue(suggest("zalophone").isEmpty())
        assertTrue(suggest("facebooking").isEmpty())
        assertEquals(3, AppNameMatcher.suggest(listOf("shared"), installed,
            installed.associate { it.packageName to listOf("shared") }).size)
    }
    @Test fun installedNamesThatStartWithCommandWordsAreStillRecognized() {
        val find = SpokenApp("Find My Device", "find.device")
        val camera = SpokenApp("Open Camera", "open.camera")
        assertEquals(AppNameMatch(find, true), AppNameMatcher.suggest(listOf(find.label), listOf(find, camera)).single())
        assertEquals(AppNameMatch(camera, true), AppNameMatcher.suggest(listOf(camera.label), listOf(find, camera)).single())
    }
    @Test fun exactFamilyDefinedPurposeAliasesRemainTentative() {
        assertEquals(AppNameMatch(zalo, false), AppNameMatcher.suggest(listOf("gọi cho cháu"), installed,
            mapOf(zalo.packageName to listOf("gọi cho cháu"))).single())
    }
}
