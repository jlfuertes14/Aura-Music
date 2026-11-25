package com.auramusic.app.core.sponsorblock

import com.auramusic.app.domain.model.ReturnYouTubeDislikeData
import com.auramusic.app.domain.model.SponsorSegment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorBlockParsingTest {

    @Test
    fun defaultCategories_containsExpectedStandardCategories() {
        val expected = setOf("sponsor", "selfpromo", "interaction", "intro", "outro", "music_offtopic")
        assertEquals(expected, SponsorBlockRepository.DEFAULT_CATEGORIES)
    }

    @Test
    fun sponsorSegment_displayLabelMapsAllSupportedAndFallbackCategories() {
        val cases = listOf(
            "sponsor" to "Sponsor",
            "selfpromo" to "Self-Promotion",
            "interaction" to "Interaction Reminder",
            "intro" to "Intermission / Intro",
            "outro" to "Endcards / Credits",
            "preview" to "Recap / Preview",
            "music_offtopic" to "Non-Music Section",
            "poi_highlight" to "Poi_highlight",
            "exclusive_access" to "Exclusive_access"
        )

        cases.forEach { (category, expectedLabel) ->
            val seg = SponsorSegment(uuid = "uuid_$category", startTimeSec = 10f, endTimeSec = 25f, category = category)
            assertEquals(expectedLabel, seg.displayLabel)
        }
    }

    @Test
    fun getSponsorSegments_returnsEmptyImmediatelyForBlankVideoIdOrEmptyCategories() = runTest {
        SponsorBlockRepository.clearSegmentCache()

        assertTrue(SponsorBlockRepository.getSponsorSegments("").isEmpty())
        assertTrue(SponsorBlockRepository.getSponsorSegments("   ").isEmpty())
        assertTrue(SponsorBlockRepository.getSponsorSegments("dQw4w9WgXcQ", emptySet()).isEmpty())
    }

    @Test
    fun getReturnYouTubeDislike_returnsNullImmediatelyForBlankVideoId() = runTest {
        assertNull(SponsorBlockRepository.getReturnYouTubeDislike(""))
        assertNull(SponsorBlockRepository.getReturnYouTubeDislike("   "))
    }

    @Test
    fun returnYouTubeDislikeData_defaultsAreConsistent() {
        val empty = ReturnYouTubeDislikeData()
        assertEquals(0L, empty.likes)
        assertEquals(0L, empty.dislikes)
        assertEquals(0L, empty.viewCount)
        assertEquals(5.0f, empty.rating, 0.001f)
    }
}
