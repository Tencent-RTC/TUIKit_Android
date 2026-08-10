package io.trtc.tuikit.chat.uikit.components.contactlist.ui
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.chat.uikit.components.common.findViewModelStoreOwner
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.canHandle
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.fromUserDisplayName
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.getApplicationTypeText
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.getStatusText
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.groupDisplayName
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.isJoinRequest
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.toUserDisplayName
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.GroupApplicationSubViewModel
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.GroupApplicationSubViewModelFactory
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar.AvatarContent
import io.trtc.tuikit.atomicxcore.api.group.GroupApplicationInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class GroupApplicationDialog(
    context: Context,
    private val groupStore: GroupStore
) : ContactSubPageDialog(context) {

    private val viewModel: GroupApplicationSubViewModel by lazy {
        val owner = context.findViewModelStoreOwner()
            ?: error("GroupApplicationDialog requires a ViewModelStoreOwner host context.")
        val key = "${GroupApplicationSubViewModel::class.java.name}:${System.identityHashCode(this)}"
        ViewModelProvider(owner, GroupApplicationSubViewModelFactory(groupStore))
            .get(key, GroupApplicationSubViewModel::class.java)
    }
    private var scope: CoroutineScope? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyTextView: TextView
    private lateinit var adapter: GroupApplicationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(context.getString(R.string.contact_list_group_application))

        val colors = getColors()
        val dm = context.resources.displayMetrics

        emptyTextView = TextView(context).apply {
            text = context.getString(R.string.contact_list_no_group_application)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(colors.textColorSecondary)
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        contentContainer.addView(emptyTextView)

        adapter = GroupApplicationAdapter(
            context = context,
            onItemClick = { application ->
                GroupApplicationDetailDialog(
                    context = context,
                    application = application,
                    onAccept = { info, close ->
                        viewModel.acceptApplication(
                            application = info,
                            onSuccess = close,
                            onFailure = ::showFailureToast
                        )
                    },
                    onRefuse = { info, close ->
                        viewModel.refuseApplication(
                            application = info,
                            onSuccess = close,
                            onFailure = ::showFailureToast
                        )
                    }
                ).show()
            },
            onAccept = {
                viewModel.acceptApplication(it, onFailure = ::showFailureToast)
            },
            onRefuse = {
                viewModel.refuseApplication(it, onFailure = ::showFailureToast)
            }
        )

        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@GroupApplicationDialog.adapter
            val topPad = dp2px(20f, dm).toInt()
            setPadding(0, topPad, 0, 0)
            clipToPadding = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        contentContainer.addView(recyclerView)
    }

    override fun onStart() {
        super.onStart()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope

        newScope.launch {
            viewModel.groupApplications.collectLatest { applications ->
                if (applications.isEmpty()) {
                    emptyTextView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyTextView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    adapter.submitList(applications)
                }
            }
        }

        newScope.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                val colors = it.currentTheme.tokens.color
                refreshNavBarColors(colors)
                emptyTextView.setTextColor(colors.textColorSecondary)
                adapter.updateColors(colors)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        scope?.cancel()
        scope = null
    }

    private fun showFailureToast(message: String) {
        if (message.isNotBlank()) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

private class GroupApplicationAdapter(
    private val context: Context,
    private val onItemClick: (GroupApplicationInfo) -> Unit,
    private val onAccept: (GroupApplicationInfo) -> Unit,
    private val onRefuse: (GroupApplicationInfo) -> Unit
) : RecyclerView.Adapter<GroupApplicationAdapter.ViewHolder>() {

    private var items: List<GroupApplicationInfo> = emptyList()
    private var colors: ColorTokens = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color

    fun submitList(newList: List<GroupApplicationInfo>) {
        items = newList
        notifyDataSetChanged()
    }

    fun updateColors(newColors: ColorTokens) {
        colors = newColors
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val dm = parent.resources.displayMetrics
        val hPad = dp2px(16f, dm).toInt()
        val vPad = dp2px(12f, dm).toInt()
        val spacing = dp2px(12f, dm).toInt()

        val outerLayout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            setPadding(hPad, vPad, hPad, vPad)
        }

        val avatar = Avatar(parent.context).apply {
            val avatarSize = dp2px(40f, dm).toInt()
            layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize)
        }
        outerLayout.addView(avatar)

        val avatarSpacer = View(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(spacing, 1)
        }
        outerLayout.addView(avatarSpacer)

        val textColumn = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val typeText = TextView(parent.context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textColumn.addView(typeText)

        val personText = TextView(parent.context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textColumn.addView(personText)

        val groupNameText = TextView(parent.context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textColumn.addView(groupNameText)

        val requestMsgText = TextView(parent.context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE
        }
        textColumn.addView(requestMsgText)

        outerLayout.addView(textColumn)

        val actionContainer = FrameLayout(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val buttonRow = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val buttonSpacing = dp2px(8f, dm).toInt()
        }

        val buttonWidth = dp2px(60f, dm).toInt()
        val buttonHeight = dp2px(32f, dm).toInt()
        val cornerRadius = dp2px(8f, dm)

        val agreeBtn = TextView(parent.context).apply {
            text = parent.context.getString(R.string.contact_list_agree)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(buttonWidth, buttonHeight).apply {
                marginEnd = dp2px(8f, dm).toInt()
            }
        }
        buttonRow.addView(agreeBtn)

        val refuseBtn = TextView(parent.context).apply {
            text = parent.context.getString(R.string.contact_list_refuse)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(buttonWidth, buttonHeight)
        }
        buttonRow.addView(refuseBtn)

        actionContainer.addView(buttonRow)

        val statusText = TextView(parent.context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        actionContainer.addView(statusText)

        outerLayout.addView(actionContainer)

        return ViewHolder(
            outerLayout, avatar, typeText, personText, groupNameText,
            requestMsgText, buttonRow, agreeBtn, refuseBtn, statusText, cornerRadius
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val application = items[position]

        holder.itemView.setOnClickListener { onItemClick(application) }

        val avatarUrl = application.fromUserAvatarURL
        if (!avatarUrl.isNullOrEmpty()) {
            holder.avatar.setContent(AvatarContent.Image(avatarUrl, application.fromUserDisplayName))
        } else {
            holder.avatar.setContent(AvatarContent.Text(application.fromUserDisplayName))
        }

        holder.typeText.text = application.getApplicationTypeText(context)
        holder.typeText.setTextColor(colors.textColorPrimary)

        holder.personText.text = if (application.isJoinRequest) {
            "${context.getString(R.string.contact_list_applicant)}\uFF1A${application.fromUserDisplayName}"
        } else {
            "${context.getString(R.string.contact_list_invitee)}\uFF1A${application.toUserDisplayName}"
        }
        holder.personText.setTextColor(colors.textColorSecondary)

        holder.groupNameText.text = "${context.getString(R.string.contact_list_group_name)}\uFF1A${application.groupDisplayName}"
        holder.groupNameText.setTextColor(colors.textColorSecondary)

        if (!application.requestMsg.isNullOrEmpty()) {
            holder.requestMsgText.text = application.requestMsg
            holder.requestMsgText.setTextColor(colors.textColorSecondary)
            holder.requestMsgText.visibility = View.VISIBLE
        } else {
            holder.requestMsgText.visibility = View.GONE
        }

        if (application.canHandle) {
            holder.buttonRow.visibility = View.VISIBLE
            holder.statusText.visibility = View.GONE

            val agreeDrawable = GradientDrawable().apply {
                cornerRadius = holder.cornerRadius
                setColor(this@GroupApplicationAdapter.colors.buttonColorPrimaryDefault)
            }
            holder.agreeBtn.background = agreeDrawable
            holder.agreeBtn.setTextColor(colors.textColorButton)
            holder.agreeBtn.setOnClickListener { onAccept(application) }

            val refuseDrawable = GradientDrawable().apply {
                cornerRadius = holder.cornerRadius
                setStroke(
                    context.resources.displayMetrics.density.toInt().coerceAtLeast(1),
                    this@GroupApplicationAdapter.colors.strokeColorPrimary
                )
                setColor(0x00000000)
            }
            holder.refuseBtn.background = refuseDrawable
            holder.refuseBtn.setTextColor(colors.textColorError)
            holder.refuseBtn.setOnClickListener { onRefuse(application) }
        } else {
            holder.buttonRow.visibility = View.GONE
            holder.statusText.visibility = View.VISIBLE
            holder.statusText.text = application.getStatusText(context)
            holder.statusText.setTextColor(colors.textColorSecondary)
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        itemView: View,
        val avatar: Avatar,
        val typeText: TextView,
        val personText: TextView,
        val groupNameText: TextView,
        val requestMsgText: TextView,
        val buttonRow: LinearLayout,
        val agreeBtn: TextView,
        val refuseBtn: TextView,
        val statusText: TextView,
        val cornerRadius: Float
    ) : RecyclerView.ViewHolder(itemView)
}
