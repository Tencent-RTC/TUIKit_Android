package com.trtc.uikit.livekit.voicelounge.living.coguest

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.common.theme.LiveToast
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeBag
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeMain
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.addTo
import com.trtc.uikit.livekit.voicelounge.clipOval
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.gradientDrawable
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.loadUrl
import com.trtc.uikit.livekit.voicelounge.ovalDrawable
import com.trtc.uikit.livekit.voicelounge.rgb
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeSkin
import com.trtc.uikit.livekit.voicelounge.singleLineEnd
import com.trtc.uikit.livekit.voicelounge.voiceLoungeAppContext
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText
import com.trtc.uikit.livekit.voicelounge.whiteAlpha
import com.trtc.uikit.livekit.voicelounge.withAlpha
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class VoiceLoungeCoGuestManagePanel(
    context: Context,
    private val seatStore: com.trtc.uikit.livekit.voicelounge.living.seat.VoiceLoungeSeatStore,
) : BottomSheetView(context) {
    private val liveID: String get() = seatStore.liveID
    private val coGuest: VoiceLoungeCoGuestStore get() = seatStore.coGuest
    private val scrollView = ScrollView(context)
    private val listView = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var skin = VoiceLoungeSkin.defaultSkin
    private val bag = VoiceLoungeBag()

    override val sheetHeight: Int get() = context.dp(520)

    init {
        contentView.addView(scrollView, frameParams(MATCH, MATCH, Gravity.TOP, top = context.dp(66)))
        scrollView.addView(listView, FrameLayout.LayoutParams(MATCH, WRAP))
        scrollView.isVerticalScrollBarEnabled = false
        scrollView.clipToPadding = false
        scrollView.setPadding(0, context.dp(4), 0, context.dp(16))
        sheetTitle = context.getString(R.string.livekit_voiceroom_comic_settings_title)
    }

    fun show(skin: VoiceLoungeSkin) {
        this.skin = skin
        show()
    }

    override fun sheetWillPresent() {
        bag.clear()
        coGuest.changes.subscribe { render() }.addTo(bag)
        seatStore.changes.subscribe { render() }.addTo(bag)
        render()
    }

    override fun sheetDidDismiss() = bag.clear()

    private fun hangUp(userID: String) {
        LiveSeatStore.create(liveID).kickUserOutOfSeat(userID, object : CompletionHandler {
            override fun onSuccess() = Unit

            override fun onFailure(code: Int, desc: String) {
                VoiceLoungeMain.post { LiveToast.show(voiceLoungeAppContext, VoiceLoungeErrorText.coGuest(code)) }
            }
        })
    }

    private fun render() {
        listView.removeAllViews()
        val connected = coGuest.connectedUsers
        val applicants = coGuest.applicants
        val isSeatFull = seatStore.hasNoFreeGuestSeat

        addHeader(
            context.getString(R.string.livekit_voiceroom_comic_connected_title), connected.isNotEmpty(), first = true
        )
        if (connected.isEmpty()) {
            addEmpty(context.getString(R.string.livekit_voiceroom_comic_connected_empty))
        } else {
            connected.forEachIndexed { index, user ->
                val userID = user.userID
                addUser(
                    name = user.userName.ifEmpty { userID },
                    avatarURL = user.avatarURL,
                    title = context.getString(R.string.livekit_voiceroom_comic_hang_up),
                    showsMic = false,
                    style = ButtonStyle.DESTRUCTIVE,
                    isEnabled = true,
                    isLast = index == connected.lastIndex,
                ) { hangUp(userID) }
            }
        }

        addHeader(
            context.getString(R.string.livekit_voiceroom_comic_applied_title, applicants.size.toString()),
            showsDivider = true,
            first = false,
        )
        if (applicants.isEmpty()) {
            addEmpty(context.getString(R.string.livekit_voiceroom_comic_applicants_empty))
        } else {
            applicants.forEachIndexed { index, user ->
                val userID = user.userID
                val (style, title, showsMic) = if (isSeatFull) {
                    Triple(
                        ButtonStyle.DISABLED,
                        context.getString(R.string.livekit_player_coguest_error_seat_full),
                        false,
                    )
                } else {
                    Triple(ButtonStyle.TINTED, context.getString(R.string.livekit_voiceroom_comic_connect), true)
                }
                addUser(
                    name = user.userName.ifEmpty { userID },
                    avatarURL = user.avatarURL,
                    title = title,
                    showsMic = showsMic,
                    style = style,
                    isEnabled = !isSeatFull && userID !in coGuest.pendingUserIDs,
                    isLast = index == applicants.lastIndex,
                ) { coGuest.accept(userID) }
            }
        }
    }

    private enum class ButtonStyle { TINTED, DESTRUCTIVE, DISABLED }

    private fun cardBackground(top: Boolean, bottom: Boolean): GradientDrawable {
        val r = context.dp(CARD_CORNER).toFloat()
        val t = if (top) r else 0f
        val b = if (bottom) r else 0f
        return GradientDrawable().apply {
            setColor(VoiceLoungeColor.card)
            cornerRadii = floatArrayOf(t, t, t, t, b, b, b, b)
        }
    }

    private fun addHeader(title: String, showsDivider: Boolean, first: Boolean) {
        val card = FrameLayout(context)
        card.background = cardBackground(top = true, bottom = false)
        val label = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(whiteAlpha(0.85f))
            includeFontPadding = false
        }
        card.addView(label, frameParams(WRAP, WRAP, Gravity.TOP, start = context.dp(16), top = context.dp(18)))
        if (showsDivider) {
            val divider = View(context).apply { setBackgroundColor(VoiceLoungeColor.overlaySoft) }
            card.addView(
                divider,
                frameParams(
                    MATCH, context.dp(1), Gravity.BOTTOM, start = context.dp(16), end = context.dp(16),
                    bottom = context.dp(4)
                ),
            )
        }
        listView.addView(
            card,
            linearParams(
                MATCH, context.dp(HEADER_HEIGHT),
                start = context.dp(CARD_INSET), end = context.dp(CARD_INSET),
                top = if (first) 0 else context.dp(CARD_SPACING),
            ),
        )
    }

    private fun addEmpty(text: String) {
        val card = FrameLayout(context)
        card.background = cardBackground(top = false, bottom = true)
        val label =
            voiceLoungeText(context, LiveTheme.TextRole.BODY, LiveTheme.TextTone.TERTIARY, Gravity.CENTER, text = text)
        card.addView(label, frameParams(WRAP, WRAP, Gravity.CENTER))
        listView.addView(
            card,
            linearParams(MATCH, context.dp(EMPTY_HEIGHT), start = context.dp(CARD_INSET), end = context.dp(CARD_INSET))
        )
    }

    @Suppress("LongParameterList")
    private fun addUser(
        name: String,
        avatarURL: String,
        title: String,
        showsMic: Boolean,
        style: ButtonStyle,
        isEnabled: Boolean,
        isLast: Boolean,
        onTap: () -> Unit,
    ) {
        val card = FrameLayout(context)
        card.background = cardBackground(top = false, bottom = isLast)
        val avatar = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = ovalDrawable(VoiceLoungeColor.overlayBase)
            clipOval()
            loadUrl(avatarURL, R.drawable.livekit_livelist_default_avatar)
        }
        card.addView(
            avatar, frameParams(context.dp(44), context.dp(44), Gravity.CENTER_VERTICAL, start = context.dp(16))
        )

        val button = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(context.dp(12), 0, context.dp(12), 0)
            val (fill, stroke) = when (style) {
                ButtonStyle.TINTED -> skin.deep.withAlpha(0.55f) to skin.mid.withAlpha(0.8f)
                ButtonStyle.DESTRUCTIVE -> VoiceLoungeColor.rose.withAlpha(0.55f) to VoiceLoungeColor.rose.withAlpha(
                    0.8f
                )

                ButtonStyle.DISABLED -> whiteAlpha(0.06f) to whiteAlpha(0.06f)
            }
            background = roundedDrawable(fill, context.dp(10).toFloat(), context.dp(1), stroke)
            if (showsMic) {
                val mic = ImageView(context).apply {
                    setImageResource(R.drawable.livekit_coguest_mic)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                addView(mic, linearParams(context.dp(14), context.dp(14), end = context.dp(4)))
            }
            val label = TextView(context).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(if (style == ButtonStyle.DISABLED) whiteAlpha(0.4f) else Color.WHITE)
                includeFontPadding = false
            }
            addView(label, linearParams(WRAP, WRAP))
            this.isEnabled = isEnabled
            alpha = if (isEnabled || style == ButtonStyle.DISABLED) 1f else 0.5f
            setOnClickListener { if (isEnabled) onTap() }
        }
        card.addView(
            button, frameParams(WRAP, context.dp(32), Gravity.CENTER_VERTICAL or Gravity.END, end = context.dp(16))
        )

        val nameLabel = TextView(context).apply {
            text = name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(LiveTheme.Color.text(LiveTheme.TextTone.SECONDARY))
            includeFontPadding = false
            singleLineEnd()
        }
        card.addView(
            nameLabel,
            frameParams(
                MATCH, WRAP, Gravity.CENTER_VERTICAL, start = context.dp(16 + 44 + 16), end = context.dp(16 + 96 + 12)
            ),
        )
        listView.addView(
            card,
            linearParams(MATCH, context.dp(USER_HEIGHT), start = context.dp(CARD_INSET), end = context.dp(CARD_INSET))
        )
    }

    private companion object {
        const val CARD_INSET = 16
        const val CARD_CORNER = 18
        const val HEADER_HEIGHT = 56
        const val CARD_SPACING = 14
        const val EMPTY_HEIGHT = 118
        const val USER_HEIGHT = 72
    }
}
