package com.trtc.uikit.roomkit.inroom.features.roomview

import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.view.VideoStreamType
import kotlin.math.min

object RoomVideoLayoutCalculator {

    const val PAGE_CAPACITY = 6
    const val INSET_DP = 13f
    const val ITEM_ASPECT_RATIO = 9f / 16f
    const val FLOATING_INSET_DP = 8f
    const val CORNER_RADIUS_DP = 12f

    private const val GRID_ROW_COUNT = 2

    enum class LayoutMode {
        SINGLE,
        GRID_TWO_COLUMNS,
        GRID_THREE_COLUMNS,
        SCREEN_SHARE,
    }

    class VideoPageLayout(
        val key: String,
        val mode: LayoutMode,
        val participants: List<RoomParticipant>,
        val centerIncompleteRows: Boolean = true,
        val gridRowCount: Int = GRID_ROW_COUNT,
    ) {
        private val participantIDs = participants.map { it.userID }

        fun streamTypeAt(index: Int): VideoStreamType =
            if (mode == LayoutMode.SCREEN_SHARE && index == 0) VideoStreamType.SCREEN else VideoStreamType.CAMERA

        fun itemKeyAt(index: Int): String = "${streamTypeAt(index).name}:${participantIDs[index]}"

        fun hasSameLayout(other: VideoPageLayout): Boolean =
            key == other.key && mode == other.mode && participantIDs == other.participantIDs &&
                centerIncompleteRows == other.centerIncompleteRows && gridRowCount == other.gridRowCount
    }

    data class VideoItemBounds(val x: Float, val y: Float, val width: Float, val height: Float)

    enum class FloatingSide { LEFT, RIGHT }

    class FloatingWindowPosition(
        val side: FloatingSide = FloatingSide.RIGHT,
        val verticalFraction: Float = 0f,
    )

    fun buildPages(
        participants: List<RoomParticipant>,
        screenParticipant: RoomParticipant?,
        floatingParticipant: RoomParticipant?,
    ): List<VideoPageLayout> {
        val validParticipants = participants.filter { it.userID.isNotBlank() }.distinctBy { it.userID }
        val pages = mutableListOf<VideoPageLayout>()
        if (screenParticipant != null && screenParticipant.userID.isNotBlank()) {
            val cameraParticipant = floatingParticipant?.takeIf { it.userID.isNotBlank() }
            pages.add(VideoPageLayout(
                key = "screen:${screenParticipant.userID}",
                mode = LayoutMode.SCREEN_SHARE,
                participants = listOfNotNull(screenParticipant, cameraParticipant),
            ))
        }

        val hasMultiplePages = validParticipants.size > PAGE_CAPACITY
        for (pageParticipants in validParticipants.chunked(PAGE_CAPACITY)) {
            pages.add(buildParticipantPage(pageParticipants, hasMultiplePages))
        }
        return pages
    }

    fun calculateBounds(
        page: VideoPageLayout,
        width: Float,
        height: Float,
        floatingWindowPosition: FloatingWindowPosition = FloatingWindowPosition(),
    ): List<VideoItemBounds> {
        val itemCount = page.participants.size
        if (width <= 0f || height <= 0f) return List(itemCount) { VideoItemBounds(0f, 0f, 0f, 0f) }

        val fullScreenBounds = VideoItemBounds(x = 0f, y = 0f, width = width, height = height)
        return when (page.mode) {
            LayoutMode.SINGLE -> listOf(
                VideoItemBounds(
                    x = INSET_DP,
                    y = INSET_DP,
                    width = width - INSET_DP * 2,
                    height = height - INSET_DP * 2,
                ),
            )
            LayoutMode.SCREEN_SHARE -> {
                if (itemCount > 1) {
                    listOf(fullScreenBounds, calculateFloatingBounds(width, height, floatingWindowPosition))
                } else {
                    listOf(fullScreenBounds)
                }
            }
            LayoutMode.GRID_TWO_COLUMNS -> calculateGridBounds(itemCount, 2, page.gridRowCount, width, height)
            LayoutMode.GRID_THREE_COLUMNS -> {
                val slotCount = if (page.centerIncompleteRows) itemCount else PAGE_CAPACITY
                calculateGridBounds(slotCount, 3, page.gridRowCount, width, height).take(itemCount)
            }
        }
    }

    private fun buildParticipantPage(
        participants: List<RoomParticipant>,
        hasMultiplePages: Boolean,
    ): VideoPageLayout {
        val mode = when {
            hasMultiplePages -> LayoutMode.GRID_THREE_COLUMNS
            participants.size == 1 -> LayoutMode.SINGLE
            participants.size <= 4 -> LayoutMode.GRID_TWO_COLUMNS
            else -> LayoutMode.GRID_THREE_COLUMNS
        }
        val gridRowCount = if (!hasMultiplePages && participants.size == 2) 1 else GRID_ROW_COUNT
        return VideoPageLayout(
            key = "participants:${participants.first().userID}",
            mode = mode,
            participants = participants,
            centerIncompleteRows = !hasMultiplePages,
            gridRowCount = gridRowCount,
        )
    }

    private fun calculateFloatingBounds(
        width: Float,
        height: Float,
        position: FloatingWindowPosition,
    ): VideoItemBounds {
        val maxInsetForViewport = min(width, height) / 4f
        val inset = min(FLOATING_INSET_DP, maxInsetForViewport)
        val preferredWidth = min(width * 0.24f, 120f)
        val widthFittingHeight = (height - inset * 2) * ITEM_ASPECT_RATIO
        val itemWidth = min(preferredWidth, widthFittingHeight)
        val itemHeight = itemWidth / ITEM_ASPECT_RATIO
        val minTop = inset
        val maxTop = height - inset - itemHeight
        val top = if (maxTop <= minTop) {
            minTop
        } else {
            minTop + (maxTop - minTop) * position.verticalFraction.coerceIn(0f, 1f)
        }
        val x = if (position.side == FloatingSide.LEFT) inset else width - inset - itemWidth
        return VideoItemBounds(x = x, y = top, width = itemWidth, height = itemHeight)
    }

    private fun calculateGridBounds(
        itemCount: Int,
        columns: Int,
        rowCount: Int,
        width: Float,
        height: Float,
    ): List<VideoItemBounds> {
        val maxSpacingForViewport = min(width, height) / 8f
        val inset = min(INSET_DP, maxSpacingForViewport)
        val gap = min(4f, maxSpacingForViewport)
        val widthPerColumn = (width - inset * 2 - gap * (columns - 1)) / columns
        val heightPerRow = (height - inset * 2 - gap * (rowCount - 1)) / rowCount
        val itemWidth = min(widthPerColumn, heightPerRow * ITEM_ASPECT_RATIO).coerceAtLeast(0f)
        val itemHeight = itemWidth / ITEM_ASPECT_RATIO
        val gridTop = (height - itemHeight * rowCount - gap * (rowCount - 1)) / 2

        return (0 until itemCount).map { index ->
            val rowIndex = index / columns
            val columnIndex = index % columns
            val itemsInRow = min(columns, itemCount - rowIndex * columns)
            val rowWidth = itemWidth * itemsInRow + gap * (itemsInRow - 1)
            val rowLeft = (width - rowWidth) / 2
            VideoItemBounds(
                x = rowLeft + columnIndex * (itemWidth + gap),
                y = gridTop + rowIndex * (itemHeight + gap),
                width = itemWidth,
                height = itemHeight,
            )
        }
    }
}
