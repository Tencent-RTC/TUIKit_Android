package com.trtc.uikit.livekit.voiceroom.interaction.cohost

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.voiceroom.interaction.common.ConnectedUserAdapter
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.atomicx.widget.basicwidget.button.AtomicButton
import io.trtc.tuikit.atomicx.widget.basicwidget.button.ButtonColorType
import io.trtc.tuikit.atomicx.widget.basicwidget.button.ButtonVariant
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.BattleConfig
import io.trtc.tuikit.atomicxcore.api.live.BattleEndedReason
import io.trtc.tuikit.atomicxcore.api.live.BattleInfo
import io.trtc.tuikit.atomicxcore.api.live.BattleListener
import io.trtc.tuikit.atomicxcore.api.live.BattleRequestCallback
import io.trtc.tuikit.atomicxcore.api.live.BattleStore
import io.trtc.tuikit.atomicxcore.api.live.CoHostStatus
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import com.trtc.uikit.livekit.voiceroom.store.ViewStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class InteractionManagerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), LifecycleOwner {

    private val DEBOUNCE_INTERVAL_MS = 500L
    private lateinit var buttonStartPK: AtomicButton
    private lateinit var participantAdapter: ConnectedUserAdapter
    private val coHostStore: CoHostStore
    private val battleStore: BattleStore
    private lateinit var recyclerConnectedList: RecyclerView
    private val jobs = mutableListOf<Job>()
    private lateinit var textTitle: TextView
    private lateinit var exitView: AtomicButton
    private var isRequestingBattle = false
    private var lastClickTime: Long = 0L
    private var currentBattleID: String? = null
    private var exitBattleDialog: AtomicAlertDialog? = null
    private var exitConnectionDialog: AtomicAlertDialog? = null
    private var invitedUserList: List<String> = emptyList()
    private var viewStore: ViewStore? = null
    private lateinit var exitBattleListener: OnClickListener
    private lateinit var exitConnectionListener: OnClickListener
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
    private val parentLifecycleObserver = LifecycleEventObserver { _, event ->
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    private val battleListener: BattleListener = object : BattleListener() {
        override fun onBattleEnded(battleInfo: BattleInfo, reason: BattleEndedReason?) {
            isRequestingBattle = false
            currentBattleID = null
            invitedUserList = emptyList()
            viewStore?.onBattleRequestCleared()
            post { updatePKButtonUI() }
        }

        override fun onBattleRequestTimeout(
            battleId: String,
            inviter: SeatUserInfo,
            invitee: SeatUserInfo,
        ) {
            resetBattleRequestState(battleId)
        }

        override fun onBattleRequestReject(
            battleId: String,
            inviter: SeatUserInfo,
            invitee: SeatUserInfo,
        ) {
            resetBattleRequestState(battleId)
        }
    }

    init {
        val liveID = LiveListStore.shared().liveState.currentLive.value.liveID
        coHostStore = CoHostStore.create(liveID)
        battleStore = BattleStore.create(liveID)
        battleStore.addBattleListener(battleListener)
        initView(context)
    }

    fun setViewStore(viewStore: ViewStore) {
        this.viewStore = viewStore
    }

    private fun initView(context: Context) {
        LayoutInflater.from(context)
            .inflate(R.layout.livekit_voiceroom_interaction_manager_view, this, true)
        bindViewId()
        initStartPKButton()
        initLeaveView()
        initCoHostConnectedList()
    }

    private fun bindViewId() {
        textTitle = findViewById(R.id.tv_title)
        exitView = findViewById(R.id.atomic_exit_pk)
        buttonStartPK = findViewById(R.id.atomic_btn_start_pk)
        recyclerConnectedList = findViewById(R.id.rv_in_co_list)
    }

    private fun initStartPKButton() {
        updatePKButtonUI()
        buttonStartPK.setOnClickListener {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastClickTime < DEBOUNCE_INTERVAL_MS) {
                return@setOnClickListener
            }
            lastClickTime = currentTime
            if (isRequestingBattle) {
                cancelBattleRequest()
            } else {
                val selfId = TUIRoomEngine.getSelfInfo().userId
                val connectedList = coHostStore.coHostState.connected.value
                    .filter { it.userID != selfId }
                    .map { it.userID }

                if (connectedList.isEmpty()) return@setOnClickListener

                val config = BattleConfig(BATTLE_DURATION, true, "")
                battleStore.requestBattle(
                    config,
                    connectedList,
                    BATTLE_REQUEST_TIMEOUT,
                    object : BattleRequestCallback {
                        override fun onSuccess(
                            battleInfo: BattleInfo,
                            resultMap: Map<String, Int>,
                        ) {
                            isRequestingBattle = true
                            currentBattleID = battleInfo.battleID
                            invitedUserList = connectedList
                            viewStore?.onBattleRequestSent(battleInfo.battleID, connectedList)
                            post { updatePKButtonUI() }
                        }

                        override fun onError(code: Int, desc: String) {
                            ErrorLocalized.onError(code)
                        }
                    })
            }
        }
    }

    private fun resetBattleRequestState(failedBattleId: String) {
        if (isRequestingBattle && failedBattleId == currentBattleID) {
            isRequestingBattle = false
            currentBattleID = null
            invitedUserList = emptyList()
            viewStore?.onBattleRequestCleared()
            post {
                updatePKButtonUI()
            }
        }
    }

    private fun cancelBattleRequest() {
        if (invitedUserList.isEmpty()) return
        val battleId = currentBattleID ?: return
        battleStore.cancelBattleRequest(
            battleId,
            invitedUserList,
            object : CompletionHandler {
                override fun onSuccess() {
                    isRequestingBattle = false
                    currentBattleID = null
                    invitedUserList = emptyList()
                    viewStore?.onBattleRequestCleared()
                    post { updatePKButtonUI() }
                }

                override fun onFailure(code: Int, desc: String) {
                    ErrorLocalized.onError(code)
                }
            })
        invitedUserList = emptyList()
    }

    private fun updatePKButtonUI() {
        if (isRequestingBattle) {
            buttonStartPK.text = context.getString(R.string.seat_cancel_invite)
            buttonStartPK.variant = ButtonVariant.OUTLINED
            buttonStartPK.colorType = ButtonColorType.SECONDARY
        } else {
            buttonStartPK.text = context.getString(R.string.seat_request_battle)
            buttonStartPK.variant = ButtonVariant.FILLED
            buttonStartPK.colorType = ButtonColorType.PRIMARY
        }
    }

    private fun initLeaveView() {
        exitConnectionListener = OnClickListener {
            val atomicDialog = AtomicAlertDialog(context)
            atomicDialog.init {
                title = resources.getString(R.string.common_disconnect_tips)
                autoDismiss = false

                cancelButton(
                    text = context.getString(R.string.common_disconnect_cancel),
                    type = AtomicAlertDialog.TextColorPreset.GREY
                ) { dialog ->
                    dialog.dismiss()
                }

                confirmButton(
                    text = context.getString(R.string.common_end_connect),
                    type = AtomicAlertDialog.TextColorPreset.RED,
                ) { dialog ->
                    coHostStore.exitHostConnection(object : CompletionHandler {
                        override fun onSuccess() {
                            dialog.dismiss()
                        }

                        override fun onFailure(code: Int, desc: String) {
                            ErrorLocalized.onError(code)
                        }
                    })
                }
            }

            exitConnectionDialog = atomicDialog
            atomicDialog.show()
        }

        exitBattleListener = OnClickListener {
            val atomicDialog = AtomicAlertDialog(context)
            atomicDialog.init {
                title = context.getString(R.string.common_battle_end_pk_tips)
                autoDismiss = false

                cancelButton(
                    text = context.getString(R.string.common_disconnect_cancel),
                    type = AtomicAlertDialog.TextColorPreset.GREY
                ) { dialog ->
                    dialog.dismiss()
                }

                confirmButton(
                    text = context.getString(R.string.seat_end_Battle),
                    type = AtomicAlertDialog.TextColorPreset.RED,
                ) { dialog ->
                    battleStore.exitBattle(
                        battleStore.battleState.currentBattleInfo.value?.battleID,
                        object : CompletionHandler {
                            override fun onSuccess() {
                                dialog.dismiss()
                            }

                            override fun onFailure(code: Int, desc: String) {
                                ErrorLocalized.onError(code)
                            }
                        })
                }
            }
            exitBattleDialog = atomicDialog
            atomicDialog.show()
        }
    }

    private fun initCoHostConnectedList() {
        recyclerConnectedList.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        participantAdapter = ConnectedUserAdapter(context)
        recyclerConnectedList.adapter = participantAdapter
    }

    private fun addObserver() {
        jobs.forEach { it.cancel() }
        jobs.clear()

        coHostStore.coHostState.connected
            .onEach(::onConnectedListUserChange)
            .launchIn(lifecycleScope)
            .let(jobs::add)

        coHostStore.coHostState.coHostStatus
            .onEach(::onConnectedStatusChange)
            .launchIn(lifecycleScope)
            .let(jobs::add)

        battleStore.battleState.battleUsers
            .onEach(::onBattleListUserChange)
            .launchIn(lifecycleScope)
            .let(jobs::add)
    }

    private fun refreshUIFromCurrentState() {
        onConnectedListUserChange(coHostStore.coHostState.connected.value)
        onConnectedStatusChange(coHostStore.coHostState.coHostStatus.value)
        onBattleListUserChange(battleStore.battleState.battleUsers.value)
        if (battleStore.battleState.battleUsers.value.isNotEmpty()) {
            isRequestingBattle = false
            currentBattleID = null
            invitedUserList = emptyList()
            viewStore?.onBattleRequestCleared()
        }
        updatePKButtonUI()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onConnectedListUserChange(connectedList: List<SeatUserInfo>) {
        participantAdapter.submitList(ArrayList(connectedList))
        if (connectedList.isEmpty() && isRequestingBattle && currentBattleID != null) {
            cancelBattleRequest()
        }
    }

    private fun onBattleListUserChange(battleList: List<SeatUserInfo>) {
        if (battleList.isNotEmpty()) {
            textTitle.setText(R.string.seat_in_battle)
            exitView.text = context.getString(R.string.common_battle_end_pk)
            buttonStartPK.visibility = GONE
            participantAdapter.isInPK = true
            exitView.setOnClickListener(exitBattleListener)
            exitConnectionDialog?.dismiss()
        } else {
            textTitle.setText(R.string.seat_in_connection)
            exitView.text = context.getString(R.string.common_exit_connect)
            buttonStartPK.visibility = VISIBLE
            participantAdapter.isInPK = false
            exitView.setOnClickListener(exitConnectionListener)
            exitBattleDialog?.dismiss()
        }
    }

    private fun onConnectedStatusChange(status: CoHostStatus) {
        val isConnected = (status == CoHostStatus.CONNECTED)
        buttonStartPK.visibility = if (isConnected) VISIBLE else GONE
        exitView.visibility = if (isConnected) VISIBLE else GONE
        if (!isConnected) exitConnectionDialog?.dismiss()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(parentLifecycleObserver)
        addObserver()
        refreshUIFromCurrentState()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(parentLifecycleObserver)
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    companion object {
        const val BATTLE_REQUEST_TIMEOUT = 10
        const val BATTLE_DURATION = 30
    }
}