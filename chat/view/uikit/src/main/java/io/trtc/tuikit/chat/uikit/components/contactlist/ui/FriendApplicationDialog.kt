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
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.FriendApplicationSubViewModel
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.FriendApplicationSubViewModelFactory
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar.AvatarContent
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.contact.FriendApplicationInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class FriendApplicationDialog(
    context: Context,
    private val contactStore: ContactStore
) : ContactSubPageDialog(context) {

    private val viewModel: FriendApplicationSubViewModel by lazy {
        val owner = context.findViewModelStoreOwner()
            ?: error("FriendApplicationDialog requires a ViewModelStoreOwner host context.")
        val key = "${FriendApplicationSubViewModel::class.java.name}:${System.identityHashCode(this)}"
        ViewModelProvider(owner, FriendApplicationSubViewModelFactory(contactStore))
            .get(key, FriendApplicationSubViewModel::class.java)
    }
    private var scope: CoroutineScope? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyTextView: TextView
    private lateinit var adapter: FriendApplicationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(context.getString(R.string.contact_list_friend_application))

        val colors = getColors()
        val dm = context.resources.displayMetrics

        emptyTextView = TextView(context).apply {
            text = context.getString(R.string.contact_list_no_friend_application)
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

        adapter = FriendApplicationAdapter(
            context = context,
            onItemClick = { application ->
                FriendApplicationDetailDialog(
                    context = context,
                    application = application,
                    onAccept = { info, close ->
                        viewModel.acceptFriendApplication(
                            application = info,
                            onSuccess = close,
                            onFailure = ::showFailureToast
                        )
                    },
                    onRefuse = { info, close ->
                        viewModel.refuseFriendApplication(
                            application = info,
                            onSuccess = close,
                            onFailure = ::showFailureToast
                        )
                    }
                ).show()
            },
            onAccept = {
                viewModel.acceptFriendApplication(it, onFailure = ::showFailureToast)
            },
            onRefuse = {
                viewModel.refuseFriendApplication(it, onFailure = ::showFailureToast)
            }
        )

        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@FriendApplicationDialog.adapter
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
            viewModel.friendApplications.collectLatest { applications ->
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

private class FriendApplicationAdapter(
    private val context: Context,
    private val onItemClick: (FriendApplicationInfo) -> Unit,
    private val onAccept: (FriendApplicationInfo) -> Unit,
    private val onRefuse: (FriendApplicationInfo) -> Unit
) : RecyclerView.Adapter<FriendApplicationAdapter.ViewHolder>() {

    private var items: List<FriendApplicationInfo> = emptyList()
    private var colors: ColorTokens = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color

    fun submitList(newList: List<FriendApplicationInfo>) {
        items = newList
        notifyDataSetChanged()
    }

    fun updateColors(newColors: ColorTokens) {
        colors = newColors
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val dm = parent.resources.displayMetrics
        val rowHeight = dp2px(56f, dm).toInt()
        val hPadStart = dp2px(16f, dm).toInt()
        val spacing = dp2px(13f, dm).toInt()

        val row = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                rowHeight
            )
            setPaddingRelative(hPadStart, dp2px(8f, dm).toInt(), 0, dp2px(8f, dm).toInt())
        }

        val avatar = Avatar(parent.context).apply {
            val avatarSize = dp2px(40f, dm).toInt()
            layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize)
        }
        row.addView(avatar)

        val spacer = View(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(spacing, 1)
        }
        row.addView(spacer)

        val textColumn = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameText = TextView(parent.context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textColumn.addView(nameText)

        val wordingText = TextView(parent.context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textColumn.addView(wordingText)

        row.addView(textColumn)

        val buttonRow = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            val endPad = dp2px(16f, dm).toInt()
            setPaddingRelative(0, 0, endPad, 0)
            val buttonSpacing = dp2px(10f, dm).toInt()
            gravity = Gravity.CENTER_VERTICAL
        }

        val buttonWidth = dp2px(70f, dm).toInt()
        val buttonHeight = dp2px(32f, dm).toInt()
        val cornerRadius = dp2px(10f, dm)

        val agreeBtn = TextView(parent.context).apply {
            text = parent.context.getString(R.string.contact_list_agree)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(buttonWidth, buttonHeight).apply {
                marginEnd = dp2px(10f, dm).toInt()
            }
        }
        buttonRow.addView(agreeBtn)

        val refuseBtn = TextView(parent.context).apply {
            text = parent.context.getString(R.string.contact_list_refuse)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(buttonWidth, buttonHeight)
        }
        buttonRow.addView(refuseBtn)

        row.addView(buttonRow)

        return ViewHolder(row, avatar, nameText, wordingText, agreeBtn, refuseBtn, cornerRadius)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val application = items[position]

        holder.itemView.setOnClickListener { onItemClick(application) }

        val avatarUrl = application.avatarURL
        if (!avatarUrl.isNullOrEmpty()) {
            holder.avatar.setContent(AvatarContent.Image(avatarUrl, application.displayName))
        } else {
            holder.avatar.setContent(AvatarContent.Text(application.displayName))
        }

        holder.nameText.text = application.displayName
        holder.nameText.setTextColor(colors.textColorPrimary)

        holder.wordingText.text = application.addWording ?: ""
        holder.wordingText.setTextColor(colors.textColorSecondary)

        val agreeDrawable = GradientDrawable().apply {
            cornerRadius = holder.cornerRadius
            setColor(this@FriendApplicationAdapter.colors.buttonColorPrimaryDefault)
        }
        holder.agreeBtn.background = agreeDrawable
        holder.agreeBtn.setTextColor(colors.textColorButton)
        holder.agreeBtn.setOnClickListener { onAccept(application) }

        val refuseDrawable = GradientDrawable().apply {
            cornerRadius = holder.cornerRadius
            setStroke(
                context.resources.displayMetrics.density.toInt().coerceAtLeast(1),
                this@FriendApplicationAdapter.colors.strokeColorPrimary
            )
            setColor(0x00000000)
        }
        holder.refuseBtn.background = refuseDrawable
        holder.refuseBtn.setTextColor(colors.textColorError)
        holder.refuseBtn.setOnClickListener { onRefuse(application) }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        itemView: View,
        val avatar: Avatar,
        val nameText: TextView,
        val wordingText: TextView,
        val agreeBtn: TextView,
        val refuseBtn: TextView,
        val cornerRadius: Float
    ) : RecyclerView.ViewHolder(itemView)
}
