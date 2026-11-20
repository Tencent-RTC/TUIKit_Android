package com.trtc.uikit.livekit.voicelounge.living.giftrank

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeBag
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.addTo
import com.trtc.uikit.livekit.voicelounge.clipOval
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.loadUrl
import com.trtc.uikit.livekit.voicelounge.ovalDrawable
import com.trtc.uikit.livekit.voicelounge.rgb
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.singleLineEnd
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText
import com.trtc.uikit.livekit.voicelounge.whiteAlpha

internal class VoiceLoungeGiftRankPanel(
    context: Context,
    private val store: VoiceLoungeGiftRankStore,
) : BottomSheetView(context) {
    private val scrollView = ScrollView(context)
    private val listView = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val podium = PodiumView(context)
    private val emptyLabel =
        voiceLoungeText(context, LiveTheme.TextRole.BODY, LiveTheme.TextTone.TERTIARY, Gravity.CENTER)
    private val bag = VoiceLoungeBag()

    override val sheetHeight: Int
        get() {
            val design = context.dp(SHEET_HEIGHT)
            val screen = context.resources.displayMetrics.heightPixels
            return if (screen > 0) minOf(design, screen) else design
        }

    init {
        contentView.addView(
            emptyLabel, frameParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = context.dp(120))
        )
        contentView.addView(scrollView, frameParams(MATCH, MATCH, Gravity.TOP, top = context.dp(CONTENT_TOP)))
        scrollView.addView(listView, FrameLayout.LayoutParams(MATCH, WRAP))
        scrollView.isVerticalScrollBarEnabled = false
        sheetTitle = context.getString(R.string.livekit_voiceroom_room_gift_rank)
        emptyLabel.text = context.getString(R.string.livekit_voiceroom_rank_empty)
        emptyLabel.visibility = View.GONE
    }

    override fun show() {
        render(store.ranking)
        super.show()
        store.refresh()
    }

    override fun sheetWillPresent() {
        bag.clear()
        store.changes.subscribe { render(store.ranking) }.addTo(bag)
    }

    override fun sheetDidDismiss() = bag.clear()

    private fun render(ranking: List<VoiceLoungeGiftRankStore.Entry>) {
        emptyLabel.visibility = if (ranking.isEmpty()) View.VISIBLE else View.GONE
        scrollView.visibility = if (ranking.isEmpty()) View.GONE else View.VISIBLE
        listView.removeAllViews()
        podium.bind(ranking.take(3))
        listView.addView(podium, linearParams(MATCH, context.dp(PODIUM_HEIGHT)))
        ranking.drop(3).forEachIndexed { offset, entry ->
            val cell = RankCell(context)
            cell.bind(offset + 4, entry)
            listView.addView(cell, linearParams(MATCH, context.dp(RankCell.HEIGHT)))
        }
    }

    private class PodiumView(context: Context) : FrameLayout(context) {
        private val slot1 = PodiumSlot(context, 1)
        private val slot2 = PodiumSlot(context, 2)
        private val slot3 = PodiumSlot(context, 3)

        init {
            addView(slot2, frameParams(dp(100), WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = dp(52)))
            slot2.translationX = -dp(104).toFloat()
            addView(slot1, frameParams(dp(110), WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = dp(10)))
            addView(slot3, frameParams(dp(100), WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = dp(52)))
            slot3.translationX = dp(104).toFloat()
        }

        fun bind(top3: List<VoiceLoungeGiftRankStore.Entry>) {
            slot1.bind(top3.getOrNull(0))
            slot2.bind(top3.getOrNull(1))
            slot3.bind(top3.getOrNull(2))
        }
    }

    private class PodiumSlot(context: Context, rank: Int) : LinearLayout(context) {
        private val avatarView = ImageView(context)
        private val nameLabel = TextView(context)
        private val coinsLabel = TextView(context)

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val size = dp(if (rank == 1) 72 else 60)
            val color = rankColor(rank)
            val ringSize = size + dp(6)
            val ring = FrameLayout(context)
            ring.background = ovalDrawable(Color.TRANSPARENT, dp(2.5f), color)
            ring.clipChildren = false
            avatarView.scaleType = ImageView.ScaleType.CENTER_CROP
            avatarView.background = ovalDrawable(VoiceLoungeColor.overlayBase)
            avatarView.clipOval()
            ring.addView(avatarView, frameParams(size, size, Gravity.CENTER))
            val badge = TextView(context).apply {
                text = rank.toString()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
                background = ovalDrawable(color)
            }
            val ringHolder = FrameLayout(context).apply { clipChildren = false }
            ringHolder.addView(ring, frameParams(ringSize, ringSize, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            ringHolder.addView(
                badge,
                frameParams(dp(20), dp(20), Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = ringSize + dp(2) - dp(20)),
            )
            addView(ringHolder, linearParams(MATCH, ringSize))

            nameLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            nameLabel.typeface = Typeface.DEFAULT_BOLD
            nameLabel.setTextColor(Color.WHITE)
            nameLabel.gravity = Gravity.CENTER
            nameLabel.singleLineEnd()
            addView(nameLabel, linearParams(MATCH, WRAP, top = dp(8)))

            coinsLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            coinsLabel.setTextColor(whiteAlpha(0.55f))
            coinsLabel.gravity = Gravity.CENTER
            addView(coinsLabel, linearParams(MATCH, WRAP, top = dp(2)))
        }

        fun bind(entry: VoiceLoungeGiftRankStore.Entry?) {
            if (entry == null) {
                visibility = View.INVISIBLE
                return
            }
            avatarView.loadUrl(entry.avatarURL, R.drawable.livekit_livelist_default_avatar)
            nameLabel.text = entry.name
            coinsLabel.text = entry.coins.toString()
            visibility = View.VISIBLE
        }

        private companion object {
            fun rankColor(rank: Int): Int = when (rank) {
                1 -> rgb(1.0f, 0.82f, 0.3f)
                2 -> rgb(0.78f, 0.82f, 0.9f)
                else -> rgb(0.84f, 0.6f, 0.4f)
            }
        }
    }

    private class RankCell(context: Context) : FrameLayout(context) {
        private val rankLabel = TextView(context)
        private val avatar = ImageView(context)
        private val nameLabel = TextView(context)
        private val coinsLabel = TextView(context)

        init {
            rankLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            rankLabel.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            rankLabel.setTextColor(whiteAlpha(0.5f))
            rankLabel.gravity = Gravity.CENTER
            addView(rankLabel, frameParams(dp(24), WRAP, Gravity.CENTER_VERTICAL, start = dp(20)))

            avatar.scaleType = ImageView.ScaleType.CENTER_CROP
            avatar.background = ovalDrawable(VoiceLoungeColor.overlayBase)
            avatar.clipOval()
            addView(avatar, frameParams(dp(36), dp(36), Gravity.CENTER_VERTICAL, start = dp(20 + 24 + 12)))

            coinsLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            coinsLabel.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            coinsLabel.setTextColor(whiteAlpha(0.8f))
            coinsLabel.gravity = Gravity.END
            addView(coinsLabel, frameParams(WRAP, WRAP, Gravity.CENTER_VERTICAL or Gravity.END, end = dp(20)))

            nameLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            nameLabel.setTextColor(Color.WHITE)
            nameLabel.singleLineEnd()
            addView(
                nameLabel,
                frameParams(MATCH, WRAP, Gravity.CENTER_VERTICAL, start = dp(20 + 24 + 12 + 36 + 12), end = dp(100))
            )

            val separator = View(context).apply { setBackgroundColor(VoiceLoungeColor.overlayHair) }
            addView(separator, frameParams(MATCH, 1, Gravity.BOTTOM, start = dp(20)))
        }

        fun bind(rank: Int, entry: VoiceLoungeGiftRankStore.Entry) {
            rankLabel.text = rank.toString()
            nameLabel.text = entry.name
            coinsLabel.text = entry.coins.toString()
            avatar.loadUrl(entry.avatarURL, R.drawable.livekit_livelist_default_avatar)
        }

        companion object {
            const val HEIGHT = 64
        }
    }

    private companion object {
        const val PODIUM_HEIGHT = 230
        const val SHEET_HEIGHT = 460
        const val CONTENT_TOP = 60
    }
}
