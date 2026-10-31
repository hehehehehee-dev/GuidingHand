package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class GoalRoutingTest {
    private val apps = listOf(SpokenApp("YouTube", "com.google.android.youtube"), SpokenApp("Zalo", "com.zing.zalo"), SpokenApp("Instagram", "com.instagram.android"))
    @Test fun explicitAppWorksInsideAnySentenceWithoutRequiringAnOpenCommand() {
        assertEquals("com.instagram.android", GoalRouting.explicit("Tôi đang đọc báo nhưng muốn nhắn cháu trên Instagram", apps)?.app?.packageName)
        assertEquals("com.zing.zalo", GoalRouting.explicit("Message my grandson on Zalo", apps)?.app?.packageName)
    }
    @Test fun clearYoutubeSearchAvoidsACloudRoutingCallAndKeepsTheActualQuery() {
        val vi = GoalRouting.explicit("Tôi muốn xem video nấu ăn trên YouTube", apps)!!
        assertEquals("search_app", vi.action); assertEquals("video nấu ăn", vi.query)
        val en = GoalRouting.explicit("Find cooking videos on YouTube", apps)!!
        assertEquals("cooking videos", en.query)
    }
    @Test fun ambiguityMissingAppsAndMalformedIdsNeverLaunchAnInventedPackage() {
        assertNull(GoalRouting.explicit("Use Zalo or Instagram", apps))
        assertNull(GoalRouting.explicit("Open TikTok", apps))
        assertNull(GoalRouting.resolve(apps, "a99")); assertNull(GoalRouting.resolve(apps, "a01"))
        assertNull(GoalRouting.resolve(apps, "com.zing.zalo"))
        assertEquals(apps[1], GoalRouting.resolve(apps, "a1"))
    }
    @Test fun familyAliasOnlyAppliesToCommunicationGoals() {
        assertTrue(GoalRouting.usesFamily("Nhắn tin cho cháu trên Zalo"))
        assertTrue(GoalRouting.usesFamily("Message my grandchild"))
        assertFalse(GoalRouting.usesFamily("Watch cooking videos with my grandson"))
    }
    @Test fun commonCapabilitiesUseRealInstalledAppsWithoutCloudRouting() {
        val available = apps + SpokenApp("Chrome", "com.android.chrome")
        assertEquals("search_web", GoalRouting.fast("Tôi muốn đọc báo", available, "other.app", "com.android.chrome")?.action)
        assertEquals("com.google.android.youtube", GoalRouting.fast("Xem video nấu ăn", available, "other.app", null)?.app?.packageName)
        assertEquals("app", GoalRouting.fast("Nhắn tin cho cháu", available, "other.app", null)?.question)
        assertEquals("com.zing.zalo", GoalRouting.fast("Nhắn tin cho cháu", available, "com.zing.zalo", null)?.app?.packageName)
        assertNull(GoalRouting.fast("Read news", apps, "other.app", "com.android.chrome"))
    }
    @Test fun currentAppMentionAndNegationDoNotOverrideTheRequestedDestination() {
        val available = apps + SpokenApp("Chrome", "com.android.chrome")
        assertEquals("com.android.chrome", GoalRouting.fast("Tôi đang ở YouTube nhưng muốn đọc báo", available, "com.google.android.youtube", "com.android.chrome")?.app?.packageName)
        assertEquals("com.instagram.android", GoalRouting.fast("I am in YouTube but want Instagram", available, "com.google.android.youtube", null)?.app?.packageName)
        assertNull(GoalRouting.explicit("I don't want YouTube", available))
        assertFalse(GoalRouting.usesFamily("Tôi đang nhắn tin cho cháu nhưng muốn đọc báo"))
        assertFalse(GoalRouting.fast("Tôi đang ở YouTube nhưng muốn đọc báo", available, "com.google.android.youtube", "com.android.chrome")!!.query.contains("YouTube"))
    }
}
