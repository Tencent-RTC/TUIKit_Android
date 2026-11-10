package com.trtc.uikit.roomkit.inroom.features.roomview

import com.trtc.uikit.roomkit.inroom.features.roomview.RoomVideoLayoutCalculator.LayoutMode
import com.trtc.uikit.roomkit.inroom.features.roomview.RoomVideoLayoutCalculator.VideoItemBounds
import com.trtc.uikit.roomkit.inroom.features.roomview.RoomVideoLayoutCalculator.VideoPageLayout
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.view.VideoStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomVideoLayoutCalculatorTest {

    @Test
    fun emptyRoomHasNoPages() {
        assertTrue(pages(0).isEmpty())
    }

    @Test
    fun singleParticipantFillsViewport() {
        val page = pages(1).single()
        assertEquals(LayoutMode.SINGLE, page.mode)
        assertEquals(VideoItemBounds(0f, 0f, 390f, 700f), bounds(page).single())
    }

    @Test
    fun twoParticipantsAreSideBySideAndVerticallyCentered() {
        val page = pages(2).single()
        assertEquals(LayoutMode.GRID_TWO_COLUMNS, page.mode)
        assertEquals(listOf("user0", "user1"), page.participants.map { it.userID })
        val bounds = bounds(page)
        assertEquals(bounds[0].width, bounds[1].width, 0.01f)
        assertEquals(bounds[0].height, bounds[1].height, 0.01f)
        assertEquals(bounds[0].y, bounds[1].y, 0.01f)
        assertEquals(bounds[0].x + bounds[0].width + RoomVideoLayoutCalculator.GAP_DP, bounds[1].x, 0.01f)
        assertEquals(9f / 16f, bounds[0].width / bounds[0].height, 0.001f)
        assertEquals(bounds[0].y, (700f - bounds[0].height) / 2f, 0.01f)
    }

    @Test
    fun threeParticipantsCenterLastRow() {
        val page = pages(3).single()
        val bounds = bounds(page)
        assertEquals(LayoutMode.GRID_TWO_COLUMNS, page.mode)
        assertEquals(bounds[0].width, bounds[2].width, 0.01f)
        assertEquals(195f, bounds[2].x + bounds[2].width / 2f, 0.01f)
        assertTrue(bounds[2].y > bounds[0].y)
    }

    @Test
    fun fourParticipantsUseTwoByTwoGrid() {
        val bounds = bounds(pages(4).single())
        assertEquals(bounds[0].x, bounds[2].x, 0.01f)
        assertEquals(bounds[1].x, bounds[3].x, 0.01f)
        assertEquals(bounds[2].y, bounds[3].y, 0.01f)
    }

    @Test
    fun fiveParticipantsCenterTwoItemsInLastRow() {
        val page = pages(5).single()
        val bounds = bounds(page)
        assertEquals(LayoutMode.GRID_THREE_COLUMNS, page.mode)
        assertEquals(195f, (bounds[3].x + bounds[4].x + bounds[4].width) / 2f, 0.01f)
        assertTrue(bounds[0].y > 0f)
    }

    @Test
    fun sixParticipantsUsePortraitThreeByTwoGrid() {
        val bounds = bounds(pages(6).single())
        assertEquals(6, bounds.size)
        bounds.forEach { assertEquals(9f / 16f, it.width / it.height, 0.001f) }
        assertEquals(bounds[0].x, bounds[3].x, 0.01f)
    }

    @Test
    fun overflowPagesRetainSixSlotTemplate() {
        for (count in listOf(7, 8, 12, 13, 25)) {
            val pages = pages(count)
            assertEquals((count + 5) / 6, pages.size)
            assertTrue(pages.all { it.mode == LayoutMode.GRID_THREE_COLUMNS })
            assertEquals((0 until count).map { "user$it" }, pages.flatMap { it.participants }.map { it.userID })
            assertEquals(bounds(pages.first()).first().width, bounds(pages.last()).first().width, 0.01f)
        }
    }

    @Test
    fun overflowPagesFillFixedSlotsFromTopLeft() {
        for ((width, height) in listOf(390f to 700f, 700f to 390f)) {
            val slots = RoomVideoLayoutCalculator.calculateBounds(pages(6).single(), width, height)
            for (count in 7..25) {
                pages(count).forEach { page ->
                    assertEquals(
                        slots.take(page.participants.size),
                        RoomVideoLayoutCalculator.calculateBounds(page, width, height),
                    )
                }
            }
        }
    }

    @Test
    fun screenSharingKeepsOverflowParticipantsInFixedSlots() {
        val pages = buildPages((0 until 7).map { "user$it" }, "user0", "user1", null)
        assertEquals(bounds(pages[1]).first(), bounds(pages[2]).single())
    }

    @Test
    fun screenPageIsFirstAndUsesSpeakerCamera() {
        val pages = buildPages(listOf("local", "speaker", "sharer"), "local", "sharer", "speaker")
        val screen = pages.first()
        assertEquals(LayoutMode.SCREEN_SHARE, screen.mode)
        assertEquals(VideoStreamType.SCREEN, screen.streamTypeAt(0))
        assertEquals("sharer", screen.participants[0].userID)
        assertEquals(VideoStreamType.CAMERA, screen.streamTypeAt(1))
        assertEquals("speaker", screen.participants[1].userID)
        assertEquals(3, pages.drop(1).flatMap { it.participants }.size)
    }

    @Test
    fun silentScreenShareFallsBackToLocalCamera() {
        val screen = buildPages(listOf("local", "remote"), "local", "remote", null).first()
        assertEquals("local", screen.participants[1].userID)
        assertEquals(VideoStreamType.CAMERA, screen.streamTypeAt(1))
    }

    @Test
    fun floatingWindowDocksToLeftOrRightEdgeWithVerticalFraction() {
        val screen = buildPages(listOf("local", "remote"), "local", "remote", null).first()
        val leftBottom = RoomVideoLayoutCalculator.calculateBounds(
            screen, 390f, 700f,
            RoomVideoLayoutCalculator.FloatingWindowPosition(
                RoomVideoLayoutCalculator.FloatingSide.LEFT, 1f,
            ),
        )
        assertEquals(RoomVideoLayoutCalculator.FLOATING_INSET_DP, leftBottom[1].x, 0.01f)
        assertEquals(700f - RoomVideoLayoutCalculator.FLOATING_INSET_DP, leftBottom[1].y + leftBottom[1].height, 0.01f)
        val rightTop = RoomVideoLayoutCalculator.calculateBounds(screen, 390f, 700f)
        assertEquals(390f - RoomVideoLayoutCalculator.FLOATING_INSET_DP, rightTop[1].x + rightTop[1].width, 0.01f)
        assertEquals(RoomVideoLayoutCalculator.FLOATING_INSET_DP, rightTop[1].y, 0.01f)
    }

    @Test
    fun sharingUserCanAlsoBeSpeakerWithoutKeyCollision() {
        val screen = buildPages(listOf("local", "remote"), "local", "remote", "remote").first()
        assertSame(screen.participants[0], screen.participants[1])
        assertFalse(screen.itemKeyAt(0) == screen.itemKeyAt(1))
    }

    @Test
    fun missingLocalDoesNotProduceInvalidFloatingWindow() {
        val screen = buildPages(emptyList(), null, "remote", null).single()
        assertEquals(1, screen.participants.size)
        assertEquals(VideoItemBounds(0f, 0f, 390f, 700f), bounds(screen).single())
    }

    @Test
    fun stoppedSharingRemovesScreenPage() {
        val pages = buildPages(listOf("local", "remote"), "local", null, "remote")
        assertFalse(pages.any { it.mode == LayoutMode.SCREEN_SHARE })
    }

    @Test
    fun speakerSelectionIgnoresMutedAndUnknownUsersAndFallsBackOnSilence() {
        val local = RoomParticipant(userID = "local", microphoneStatus = DeviceStatus.ON)
        val remote = RoomParticipant(userID = "remote", microphoneStatus = DeviceStatus.ON)
        val muted = RoomParticipant(userID = "muted", microphoneStatus = DeviceStatus.OFF)
        val participants = listOf(local, remote, muted)
        val volumes = mapOf("local" to 10, "remote" to 50, "muted" to 90, "unknown" to 100)
        assertSame(remote, RoomView.selectSpeaker(participants, local, volumes, null))
        assertNull(RoomView.selectSpeaker(participants, local, emptyMap(), "remote"))
        assertNull(RoomView.selectSpeaker(emptyList(), null, volumes, null))
        assertSame(remote, RoomView.selectSpeaker(participants, local, mapOf("local" to 50, "remote" to 50), "remote"))
    }

    @Test
    fun duplicateAndBlankParticipantsAreIgnored() {
        val pages = buildPages(listOf("", "local", "local", "remote"), "local", null, null)
        assertEquals(2, pages.single().participants.size)
    }

    @Test
    fun boundsStayWithinPortraitLandscapeAndSmallViewports() {
        for ((width, height) in listOf(390f to 700f, 700f to 390f, 160f to 100f, 10f to 10f)) {
            for (count in 1..13) {
                pages(count).forEach { page ->
                    RoomVideoLayoutCalculator.calculateBounds(page, width, height).forEach {
                        assertTrue(it.x >= 0f && it.y >= 0f)
                        assertTrue(it.width >= 0f && it.height >= 0f)
                        assertTrue(it.x + it.width <= width + 0.01f)
                        assertTrue(it.y + it.height <= height + 0.01f)
                    }
                }
            }
        }
    }

    @Test
    fun sdkParticipantsAreUsedWithoutCopying() {
        val participant = RoomParticipant(userID = "local")
        val page = RoomVideoLayoutCalculator.buildPages(listOf(participant), null, null).single()
        assertSame(participant, page.participants.single())
        participant.userName = "Updated name"
        assertEquals("Updated name", page.participants.single().userName)
    }

    @Test
    fun layoutComparisonDoesNotDependOnMutableParticipantEquality() {
        val participant = RoomParticipant(userID = "local")
        val previousPage = RoomVideoLayoutCalculator.buildPages(listOf(participant), null, null).single()
        participant.userName = "Updated name"
        val sameLayout = RoomVideoLayoutCalculator.buildPages(listOf(participant), null, null).single()
        assertTrue(previousPage.hasSameLayout(sameLayout))
        participant.userID = "replacement"
        val differentLayout = RoomVideoLayoutCalculator.buildPages(listOf(participant), null, null).single()
        assertFalse(previousPage.hasSameLayout(differentLayout))
        assertFalse(previousPage.itemKeyAt(0) == differentLayout.itemKeyAt(0))
    }

    private fun bounds(page: VideoPageLayout): List<VideoItemBounds> =
        RoomVideoLayoutCalculator.calculateBounds(page, 390f, 700f)

    private fun pages(count: Int): List<VideoPageLayout> =
        buildPages((0 until count).map { "user$it" }, "user0", null, null)

    private fun buildPages(
        userIDs: List<String>,
        localID: String?,
        screenID: String?,
        speakerID: String?,
    ): List<VideoPageLayout> {
        val participants = userIDs.map { RoomParticipant(userID = it) }
        val screen = screenID?.let { id ->
            participants.firstOrNull { it.userID == id } ?: RoomParticipant(userID = id)
        }
        val speaker = participants.firstOrNull { it.userID == speakerID }
        val local = participants.firstOrNull { it.userID == localID }
        return RoomVideoLayoutCalculator.buildPages(participants, screen, speaker ?: local)
    }
}
