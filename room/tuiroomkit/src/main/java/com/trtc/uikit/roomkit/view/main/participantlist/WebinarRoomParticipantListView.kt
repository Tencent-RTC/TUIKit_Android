package com.trtc.uikit.roomkit.view.main.participantlist

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.ui.BaseView
import com.trtc.uikit.roomkit.base.ui.RoomPopupDialog
import com.trtc.uikit.roomkit.view.main.AudienceManagerView
import com.trtc.uikit.roomkit.view.main.AudienceManagerView.OnAudienceActionListener
import com.trtc.uikit.roomkit.view.main.ParticipantManagerView
import com.trtc.uikit.roomkit.view.main.ParticipantManagerView.OnParticipantActionListener
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import io.trtc.tuikit.atomicxcore.api.room.RoomType
import io.trtc.tuikit.atomicxcore.api.room.RoomUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class WebinarRoomParticipantListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr) {

    private val logger = RoomKitLogger.getLogger("RoomParticipantListView")

    companion object {
        private const val TAB_INDEX_PARTICIPANT = 0
        private const val TAB_INDEX_AUDIENCE = 1
    }

    private var subscribeStateJob: Job? = null

    private val tabLayout: TabLayout by lazy { findViewById(R.id.tab_layout) }
    private val rvParticipants: RecyclerView by lazy { findViewById(R.id.rv_participants) }
    private val rvAudience: RecyclerView by lazy { findViewById(R.id.rv_audience) }
    private lateinit var participantTab: TabLayout.Tab
    private lateinit var audienceTab: TabLayout.Tab

    private val participantAdapter = ParticipantListAdapter(RoomType.WEBINAR)
    private val audienceAdapter = AudienceListAdapter()

    private var participantStore: RoomParticipantStore? = null
    private var roomStore: RoomStore? = null
    private var participantManagerDialog: RoomPopupDialog? = null
    private var audienceManagerDialog: RoomPopupDialog? = null
    private var participantManagerView: ParticipantManagerView? = null
    private var audienceManagerView: AudienceManagerView? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_webinar_room_participant_list_view, this)
        initView()
    }

    public override fun init(roomID: String) {
        super.init(roomID)
    }

    override fun initStore(roomID: String) {
        participantStore = RoomParticipantStore.create(roomID)
        participantStore?.getAudienceList("", null)
        roomStore = RoomStore.shared()
    }

    override fun addObserver() {
        val participantStore = participantStore ?: return
        val roomStore = roomStore ?: return

        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {

            launch {
                participantStore.state.participantList.collect { participants ->
                    logger.info("participants size: ${participants.size}")
                    updateParticipantList(participants)
                }
            }

            launch {
                participantStore.state.adminList.collect { admins ->
                    val adminIds = admins.map { it.userID }.toSet()
                    logger.info("admin list: $adminIds")
                    audienceAdapter.updateAdminList(adminIds)
                }
            }

            launch {
                participantStore.state.audienceList.collect { audienceList ->
                    logger.info("audience size: ${audienceList.size}")
                    updateAudienceList(audienceList)
                }
            }

            launch {
                roomStore.state.currentRoom.collect { roomInfo ->
                    roomInfo?.let {
                        updateTabView(it.participantCount, it.audienceCount)
                    }
                }
            }
        }
    }

    override fun removeObserver() {
        subscribeStateJob?.cancel()
        subscribeStateJob = null
        participantManagerDialog?.dismiss()
        participantManagerDialog = null
    }

    private fun initView() {
        participantTab = tabLayout.newTab()
        audienceTab = tabLayout.newTab()
        tabLayout.addTab(participantTab)
        tabLayout.addTab(audienceTab)

        rvParticipants.layoutManager = LinearLayoutManager(context)
        rvParticipants.adapter = participantAdapter

        rvAudience.layoutManager = LinearLayoutManager(context)
        rvAudience.adapter = audienceAdapter

        rvParticipants.visibility = VISIBLE
        rvAudience.visibility = GONE

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    TAB_INDEX_PARTICIPANT -> {
                        rvParticipants.visibility = VISIBLE
                        rvAudience.visibility = GONE
                    }

                    TAB_INDEX_AUDIENCE -> {
                        rvParticipants.visibility = GONE
                        rvAudience.visibility = VISIBLE
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        participantAdapter.setOnItemClickListener { participant ->
            showParticipantActionDialog(participant)
        }

        audienceAdapter.setOnItemClickListener { audience ->
            showAudienceActionDialog(audience)
        }
    }

    private fun updateParticipantList(participants: List<RoomParticipant>) {
        participantAdapter.updateData(participants)
    }

    private fun updateTabView(participantCount: Int, audienceCount: Int) {
        participantTab.text = context.getString(R.string.roomkit_participant, participantCount.toString())
        audienceTab.text = context.getString(R.string.roomkit_audience, audienceCount.toString())
    }

    private fun updateAudienceList(audience: List<RoomUser>) {
        audienceAdapter.updateData(audience)
    }

    private fun showParticipantActionDialog(participant: RoomParticipant) {
        logger.info("show action dialog for participant: ${participant.userID}")
        val localParticipant = participantStore?.state?.localParticipant?.value ?: return
        val canOperate = localParticipant.role.value < participant.role.value
        if (!canOperate) {
            return
        }

        if (participantManagerDialog == null) {
            participantManagerView = ParticipantManagerView(context).apply {
                init(roomID, RoomType.WEBINAR, object : OnParticipantActionListener {
                    override fun onDismiss() {
                        participantManagerDialog?.dismiss()
                    }
                })
            }
            participantManagerDialog = RoomPopupDialog(context).apply {
                participantManagerView?.let {
                    setView(it)
                }
            }
        }
        participantManagerView?.setRoomParticipant(participant)
        participantManagerDialog?.show()
    }

    private fun showAudienceActionDialog(audience: RoomUser) {
        logger.info("Show action dialog for audience: ${audience.userID}")
        val localParticipant = participantStore?.state?.localParticipant?.value ?: return
        val adminList = participantStore?.state?.adminList?.value ?: return
        val adminIds = adminList.map { it.userID }.toSet()
        val canOperate = localParticipant.role == ParticipantRole.OWNER
                || (adminIds.contains(localParticipant.userID)
                && !adminIds.contains(audience.userID))
        if (!canOperate) {
            return
        }

        if (audienceManagerDialog == null) {
            audienceManagerView = AudienceManagerView(context).apply {
                init(roomID, object : OnAudienceActionListener {
                    override fun onDismiss() {
                        audienceManagerDialog?.dismiss()
                    }
                })
            }
            audienceManagerDialog = RoomPopupDialog(context).apply {
                audienceManagerView?.let {
                    setView(it)
                }
            }
        }
        audienceManagerView?.setAudience(audience)
        audienceManagerDialog?.show()
    }
}