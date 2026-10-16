package com.trtc.uikit.livekit.pusher.living.audience

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.avatar.AtomicAvatar
import com.trtc.uikit.livekit.common.theme.label.AtomicLabel

class LiveAudiencePresenceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val MAX_VISIBLE_AUDIENCE = 3
        private const val COMPACT_COUNT_BASE = 10_000
        private const val MAX_COMPACT_COUNT = 10
    }

    private val avatarStack: LinearLayout
    private val countView: AtomicLabel
    private val avatarViews: List<AudienceAvatarView>
    private var renderedAvatarURLs: List<String>? = null
    private var renderedCount: Int? = null

    /** Raised when the host taps the presence pill to open the audience list. */
    var onShowAudience: (() -> Unit)? = null

    init {
        LayoutInflater.from(context)
            .inflate(R.layout.livekit_pusher_audience_presence_view, this, true)
        avatarStack = findViewById(R.id.livekit_pusher_presence_avatar_stack)
        countView = findViewById(R.id.livekit_pusher_presence_count)
        avatarViews = listOf(
            findViewById(R.id.livekit_pusher_presence_rank_1),
            findViewById(R.id.livekit_pusher_presence_rank_2),
            findViewById(R.id.livekit_pusher_presence_rank_3),
        )
        setOnClickListener { onShowAudience?.invoke() }
        contentDescription = context.getString(R.string.common_anchor_audience_list_panel_title)
        bind(emptyList(), 0)
    }

    internal fun bind(audience: List<PusherAudienceStore.User>, count: Int) {
        val visibleAudience = if (count > 0) audience.take(MAX_VISIBLE_AUDIENCE) else emptyList()
        val avatarURLs = visibleAudience.map { it.avatarURL.trim() }
        if (renderedAvatarURLs != avatarURLs) {
            renderedAvatarURLs = avatarURLs
            avatarStack.visibility = if (avatarURLs.isEmpty()) View.GONE else View.VISIBLE
            val countLayoutParams = countView.layoutParams as MarginLayoutParams
            countLayoutParams.marginStart = if (avatarURLs.isEmpty()) 0 else dp(4)
            countView.layoutParams = countLayoutParams
            avatarViews.forEachIndexed { index, view ->
                view.visibility = if (index < avatarURLs.size) View.VISIBLE else View.GONE
                view.updateAvatarURL(avatarURLs.getOrNull(index))
            }
        }
        val safeCount = count.coerceAtLeast(0)
        if (renderedCount == safeCount) return
        renderedCount = safeCount
        countView.text = formatCount(safeCount)
        ViewCompat.setStateDescription(this, safeCount.toString())
    }

    private fun formatCount(count: Int): String {
        val safeCount = count.coerceAtLeast(0)
        if (safeCount < COMPACT_COUNT_BASE) return safeCount.toString()
        return context.getString(
            R.string.livekit_pusher_audience_count_compact,
            (safeCount / COMPACT_COUNT_BASE).coerceAtMost(MAX_COMPACT_COUNT),
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

internal class AudienceAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val avatarView: AtomicAvatar
    private var renderedAvatarURL: String? = null
    init {
        LayoutInflater.from(context)
            .inflate(R.layout.livekit_pusher_audience_avatar_view, this, true)
        avatarView = findViewById(R.id.livekit_pusher_presence_avatar)
    }

    fun updateAvatarURL(avatarURL: String?) {
        val url = avatarURL.orEmpty().trim()
        if (renderedAvatarURL == url) return
        renderedAvatarURL = url
        avatarView.setContent(
            AtomicAvatar.AvatarContent.URL(
                url,
                R.drawable.livekit_livelist_default_avatar,
            )
        )
    }
}
