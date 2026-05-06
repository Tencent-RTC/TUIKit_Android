package com.trtc.uikit.livekit.features.anchorview.view.menuview

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.features.anchorview.AnchorBottomItem
import com.trtc.uikit.livekit.features.anchorview.store.AnchorStore
import com.trtc.uikit.livekit.features.anchorview.view.battle.panel.AnchorBattleManageDialog
import com.trtc.uikit.livekit.features.anchorview.view.coguest.panel.AnchorCoGuestManageDialog
import com.trtc.uikit.livekit.features.anchorview.view.coguest.panel.CoGuestIconView
import com.trtc.uikit.livekit.features.anchorview.view.cohost.panel.AnchorCoHostManageDialog
import com.trtc.uikit.livekit.features.anchorview.view.settings.SettingsPanelDialog
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dip2px
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AnchorBottomMenuView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private var anchorStore: AnchorStore? = null
    private var stateScope: CoroutineScope? = null

    private var anchorCoHostManageDialog: AnchorCoHostManageDialog? = null
    private var anchorBattleManageDialog: AnchorBattleManageDialog? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
    }

    fun init(anchorStore: AnchorStore) {
        this.anchorStore = anchorStore
    }

    fun updateItems(items: List<AnchorBottomItem>) {
        removeAllViews()

        for (item in items) {
            val itemView = createItemView(item) ?: continue
            addView(itemView)
        }
    }

    private fun createItemView(item: AnchorBottomItem): View? {
        return when (item) {
            is AnchorBottomItem.Custom -> {
                (item.view.parent as? ViewGroup)?.removeView(item.view)
                if (item.view.layoutParams == null) {
                    item.view.layoutParams = LayoutParams(
                        dip2px(34f), dip2px(46f)
                    ).apply { marginEnd = dip2px(8f) }
                }
                item.view
            }

            is AnchorBottomItem.CoHost -> createCoHostItemView()
            is AnchorBottomItem.Battle -> createBattleItemView()
            is AnchorBottomItem.CoGuest -> createCoGuestItemView()
            is AnchorBottomItem.More -> createMoreItemView()
        }
    }

    private fun createCoHostItemView(): View {
        val wrapper = createDefaultItemView(R.drawable.livekit_function_connection, R.string.common_link_host)
        wrapper.setOnClickListener {
            if (!wrapper.isEnabled) return@setOnClickListener
            wrapper.isEnabled = false
            val store = anchorStore ?: return@setOnClickListener
            val dialog = AnchorCoHostManageDialog(context, store)
            dialog.setOnDismissListener { wrapper.isEnabled = true }
            anchorCoHostManageDialog = dialog
            dialog.show()
        }
        stateScope?.let { scope -> bindCoHostState(wrapper, scope) }
        return wrapper
    }

    private fun createBattleItemView(): View {
        val wrapper = createDefaultItemView(R.drawable.livekit_function_battle, R.string.common_anchor_battle)
        wrapper.setOnClickListener {
            if (!wrapper.isEnabled) return@setOnClickListener
            wrapper.isEnabled = false
            val store = anchorStore ?: return@setOnClickListener
            val dialog = AnchorBattleManageDialog(context, store)
            anchorBattleManageDialog = dialog
            dialog.handleBattleClick()
            wrapper.isEnabled = true
        }
        stateScope?.let { scope -> bindBattleState(wrapper, scope) }
        return wrapper
    }

    private fun createCoGuestItemView(): View {
        val wrapper = createCustomIconItemView(R.string.common_link_guest) { ctx -> CoGuestIconView(ctx) }
        wrapper.setOnClickListener {
            if (!wrapper.isEnabled) return@setOnClickListener
            wrapper.isEnabled = false
            val dialog = AnchorCoGuestManageDialog(context)
            dialog.setOnDismissListener { wrapper.isEnabled = true }
            dialog.show()
        }
        stateScope?.let { scope -> bindCoGuestState(wrapper, scope) }
        return wrapper
    }

    private fun createMoreItemView(): View {
        val wrapper = createDefaultItemView(R.drawable.livekit_function_more, R.string.common_more)
        wrapper.setOnClickListener {
            if (!wrapper.isEnabled) return@setOnClickListener
            wrapper.isEnabled = false
            val store = anchorStore ?: return@setOnClickListener
            val dialog = SettingsPanelDialog(context, store)
            dialog.setOnDismissListener { wrapper.isEnabled = true }
            dialog.show()
        }
        return wrapper
    }

    private fun bindCoHostState(view: View, scope: CoroutineScope) {
        val store = anchorStore ?: return
        val liveID = store.getState().liveInfo.liveID
        scope.launch {
            val coGuestStore = CoGuestStore.create(liveID)
            coGuestStore.coGuestState.connected.collect { seatList ->
                enableView(view, seatList.size <= 1)
            }
        }
        scope.launch {
            val coGuestStore = CoGuestStore.create(liveID)
            coGuestStore.coGuestState.applicants.collect { applicants ->
                enableView(view, applicants.isEmpty())
                anchorCoHostManageDialog?.dismiss()
            }
        }
        scope.launch {
            store.getBattleState()?.isBattleRunning?.collect { isRunning ->
                if (isRunning == true) {
                    val battledUsers = store.getBattleState()?.battledUsers?.value
                    val selfUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID
                    battledUsers?.forEach { user ->
                        if (TextUtils.equals(selfUserId, user.userId)) {
                            enableView(view, false)
                            return@collect
                        }
                    }
                } else {
                    enableView(view, true)
                }
            }
        }
    }

    private fun bindBattleState(view: View, scope: CoroutineScope) {
        val store = anchorStore ?: return
        val battleState = store.getBattleState()
        val anchorCoHostStore = store.getAnchorCoHostStore()
        val anchorBattleStore = store.getAnchorBattleStore()
        scope.launch {
            val liveID = store.getState().liveInfo.liveID
            val coHostStore = CoHostStore.create(liveID)
            coHostStore.coHostState.connected.collect {
                updateBattleIcon(view, anchorCoHostStore, anchorBattleStore)
            }
        }
        scope.launch {
            battleState?.battledUsers?.collect {
                view.post { updateBattleIcon(view, anchorCoHostStore, anchorBattleStore) }
            }
        }
        scope.launch {
            battleState?.isOnDisplayResult?.collect {
                view.post { updateBattleIcon(view, anchorCoHostStore, anchorBattleStore) }
            }
        }
        scope.launch {
            battleState?.isBattleRunning?.collect { isRunning ->
                if (isRunning != true) {
                    anchorBattleManageDialog?.dismissDialogs()
                }
            }
        }
    }

    private fun bindCoGuestState(view: View, scope: CoroutineScope) {
        val store = anchorStore ?: return
        val liveID = store.getState().liveInfo.liveID
        scope.launch {
            val coHostStore = CoHostStore.create(liveID)
            coHostStore.coHostState.connected.collect { userList ->
                enableView(view, userList.isEmpty())
            }
        }
        scope.launch {
            val coGuestStore = CoGuestStore.create(liveID)
            coGuestStore.coGuestState.connected.collect { seatList ->
                val coGuestIconView = view.tag as? CoGuestIconView
                if (seatList.size > 1) {
                    coGuestIconView?.startAnimation()
                } else {
                    coGuestIconView?.stopAnimation()
                }
            }
        }
    }

    // endregion

    // region View Factory

    @SuppressLint("DiscouragedApi")
    private fun createDefaultItemView(iconRes: Int, textRes: Int): LinearLayout {
        val wrapper = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(dip2px(34f), dip2px(46f)).apply {
                marginEnd = dip2px(8f)
            }
        }
        val icon = View(context).apply {
            layoutParams = LayoutParams(dip2px(28f), dip2px(28f))
            setBackgroundResource(iconRes)
        }
        wrapper.addView(icon)
        wrapper.tag = icon

        val label = io.trtc.tuikit.atomicx.widget.basicwidget.label.AtomicLabel(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dip2px(16f))
            gravity = Gravity.CENTER
            setText(textRes)
            setTextColor(ContextCompat.getColor(context, io.trtc.tuikit.atomicx.R.color.text_color_primary))
            textSize = 10f
        }
        wrapper.addView(label)
        return wrapper
    }

    private fun createCustomIconItemView(textRes: Int, viewFactory: (Context) -> View): LinearLayout {
        val wrapper = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(dip2px(34f), dip2px(46f)).apply {
                marginEnd = dip2px(8f)
            }
        }
        val icon = viewFactory(context).apply {
            layoutParams = LayoutParams(dip2px(28f), dip2px(28f))
        }
        wrapper.addView(icon)
        wrapper.tag = icon

        val label = io.trtc.tuikit.atomicx.widget.basicwidget.label.AtomicLabel(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dip2px(16f))
            gravity = Gravity.CENTER
            setText(textRes)
            setTextColor(ContextCompat.getColor(context, io.trtc.tuikit.atomicx.R.color.text_color_primary))
            textSize = 10f
        }
        wrapper.addView(label)
        return wrapper
    }

    private fun updateBattleIcon(
        wrapperView: View,
        anchorCoHostStore: com.trtc.uikit.livekit.features.anchorview.store.AnchorCoHostStore?,
        anchorBattleStore: com.trtc.uikit.livekit.features.anchorview.store.AnchorBattleStore?,
    ) {
        val battleIconView = wrapperView.tag as? View ?: return
        val battleResultDisplay = anchorBattleStore?.battleState?.isOnDisplayResult?.value
        if (anchorCoHostStore == null || anchorBattleStore == null) return
        if (anchorCoHostStore.isSelfInCoHost()) {
            if (battleResultDisplay == true) {
                battleIconView.setBackgroundResource(R.drawable.livekit_function_battle_disable)
            } else if (anchorBattleStore.isSelfInBattle()) {
                battleIconView.setBackgroundResource(R.drawable.livekit_function_battle_exit)
            } else {
                battleIconView.setBackgroundResource(R.drawable.livekit_function_battle)
            }
        } else {
            battleIconView.setBackgroundResource(R.drawable.livekit_function_battle_disable)
        }
    }

   internal fun showCoGuestPanel() {
        val dialog = AnchorCoGuestManageDialog(context)
        dialog.show()
    }

    internal fun showCoHostPanel() {
        anchorStore?.let {
            val dialog = AnchorCoHostManageDialog(context, it)
            anchorCoHostManageDialog = dialog
            dialog.show()
        }
    }

    internal fun requestBattle() {
        anchorStore?.let {
            val dialog = AnchorBattleManageDialog(context, it)
            anchorBattleManageDialog = dialog
            dialog.handleBattleClick()
        }
    }

    internal fun showMorePanel() {
        anchorStore?.let {
            val dialog = SettingsPanelDialog(context, it)
            dialog.show()
        }
    }

    private fun cancelStateObservation() {
        stateScope?.cancel()
        stateScope = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        cancelStateObservation()
        stateScope = CoroutineScope(Dispatchers.Main + Job())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelStateObservation()
    }

    companion object {
        fun enableView(view: View, enable: Boolean) {
            if (!view.isAttachedToWindow) return
            view.isEnabled = enable
            view.alpha = if (enable) 1.0f else 0.5f
        }
    }
}
