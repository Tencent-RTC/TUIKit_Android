package com.trtc.uikit.livekit.pusher.living.coguest.controlpanel

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.pusher.living.coguest.PusherCoGuestStore
import com.trtc.uikit.livekit.common.theme.avatar.AtomicAvatar
import com.trtc.uikit.livekit.common.theme.button.AtomicButton
import com.trtc.uikit.livekit.common.theme.button.ButtonColorType
import com.trtc.uikit.livekit.common.theme.label.AtomicLabel

internal enum class PusherCoGuestSection(
    @StringRes val title: Int,
    @StringRes val countedTitle: Int,
    @StringRes val emptyText: Int?,
) {
    CONNECTED(
        R.string.livekit_pusher_coguest_connected,
        R.string.livekit_pusher_coguest_connected_count,
        R.string.livekit_pusher_coguest_empty,
    ),
    APPLICANTS(
        R.string.livekit_pusher_coguest_applicants,
        R.string.livekit_pusher_coguest_applicants_count,
        null,
    ),
    CANDIDATES(
        R.string.livekit_pusher_coguest_candidates,
        R.string.livekit_pusher_coguest_candidates_count,
        null,
    ),
}

internal enum class PusherCoGuestAction(@StringRes val title: Int, val destructive: Boolean) {
    END(R.string.livekit_pusher_coguest_end, true),
    ACCEPT(R.string.livekit_pusher_coguest_accept, false),
    INVITE(R.string.livekit_pusher_coguest_invite, false),
    CANCEL_INVITATION(R.string.livekit_pusher_coguest_cancel_invitation, true),
    ;

}

internal data class PusherCoGuestRow(
    val user: PusherCoGuestStore.User,
    val action: PusherCoGuestAction,
    val isPending: Boolean,
    val isEnabled: Boolean,
)

internal class PusherCoGuestUserSectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {
    var onAction: ((PusherCoGuestRow) -> Unit)?
        get() = listAdapter.onAction
        set(value) {
            listAdapter.onAction = value
        }

    private val titleView: AtomicLabel
    private val emptyView: AtomicLabel
    private val separatorView: View
    private val listView: RecyclerView
    private val listAdapter = PusherCoGuestUserSectionAdapter()
    private var rowCount = 0

    val preferredHeight: Int
        get() = toPixels(if (rowCount == 0) EMPTY_HEIGHT_DP else {
            ROWS_TOP_DP + rowCount * ROW_HEIGHT_DP + BOTTOM_INSET_DP
        })

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_coguest_user_section_view, this, true)
        titleView = findViewById(R.id.livekit_pusher_coguest_section_title)
        emptyView = findViewById(R.id.livekit_pusher_coguest_section_empty)
        separatorView = findViewById(R.id.livekit_pusher_coguest_section_separator)
        listView = findViewById(R.id.livekit_pusher_coguest_section_list)
        listView.layoutManager = LinearLayoutManager(context)
        listView.adapter = listAdapter
        listView.itemAnimator = null
        listView.isNestedScrollingEnabled = false
    }

    fun bind(section: PusherCoGuestSection, rows: List<PusherCoGuestRow>) {
        rowCount = rows.size
        titleView.text = context.getString(
            if (rows.isEmpty()) section.title else section.countedTitle,
            *if (rows.isEmpty()) emptyArray() else arrayOf(rows.size),
        )
        emptyView.text = section.emptyText?.let(context::getString).orEmpty()
        emptyView.visibility = if (rows.isEmpty() && section.emptyText != null) VISIBLE else GONE
        separatorView.visibility = if (rows.isEmpty()) GONE else VISIBLE
        listView.visibility = if (rows.isEmpty()) GONE else VISIBLE
        listAdapter.submitList(rows)
    }

    private fun toPixels(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val ROWS_TOP_DP = 50
        const val ROW_HEIGHT_DP = 52
        const val BOTTOM_INSET_DP = 16
        const val EMPTY_HEIGHT_DP = 152
    }
}

private class PusherCoGuestUserSectionAdapter :
    ListAdapter<PusherCoGuestRow, PusherCoGuestUserSectionAdapter.Holder>(DIFF) {
    var onAction: ((PusherCoGuestRow) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.livekit_pusher_coguest_user_item, parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), onAction)
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarBorder: FrameLayout =
            itemView.findViewById(R.id.livekit_pusher_coguest_user_avatar_border)
        private val avatarView: AtomicAvatar =
            itemView.findViewById(R.id.livekit_pusher_coguest_user_avatar)
        private val nameView: AtomicLabel =
            itemView.findViewById(R.id.livekit_pusher_coguest_user_name)
        private val actionContainer: FrameLayout =
            itemView.findViewById(R.id.livekit_pusher_coguest_user_action_container)
        private val actionButton: AtomicButton =
            itemView.findViewById(R.id.livekit_pusher_coguest_user_action)

        init {
            avatarBorder.clipToOutline = true
        }

        private var avatarURL: String? = null

        fun bind(row: PusherCoGuestRow, onAction: ((PusherCoGuestRow) -> Unit)?) {
            val nextAvatarURL = row.user.avatarURL.trim()
            if (avatarURL != nextAvatarURL) {
                avatarURL = nextAvatarURL
                avatarView.setContent(
                    AtomicAvatar.AvatarContent.URL(
                        nextAvatarURL,
                        R.drawable.livekit_livelist_default_avatar,
                    ),
                )
            }
            nameView.text = row.user.userName
            actionButton.text = itemView.context.getString(
                if (row.isPending) R.string.livekit_pusher_coguest_processing else row.action.title,
            )
            actionButton.colorType = if (row.action.destructive) {
                ButtonColorType.DANGER
            } else {
                ButtonColorType.PRIMARY
            }
            actionContainer.setBackgroundResource(
                if (row.action.destructive) {
                    R.drawable.livekit_pusher_coguest_action_destructive
                } else {
                    R.drawable.livekit_pusher_coguest_action_primary
                },
            )
            val enabled = row.isEnabled && !row.isPending
            actionContainer.isEnabled = enabled
            actionContainer.alpha = if (enabled) 1f else 0.46f
            actionButton.setOnClickListener(if (enabled) {
                View.OnClickListener { onAction?.invoke(row) }
            } else {
                null
            })
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<PusherCoGuestRow>() {
            override fun areItemsTheSame(oldItem: PusherCoGuestRow, newItem: PusherCoGuestRow): Boolean =
                oldItem.user.userID == newItem.user.userID

            override fun areContentsTheSame(oldItem: PusherCoGuestRow, newItem: PusherCoGuestRow): Boolean =
                oldItem.user.userID == newItem.user.userID &&
                    oldItem.user.userName == newItem.user.userName &&
                    oldItem.user.avatarURL == newItem.user.avatarURL &&
                    oldItem.action == newItem.action && oldItem.isPending == newItem.isPending &&
                    oldItem.isEnabled == newItem.isEnabled
        }
    }
}
