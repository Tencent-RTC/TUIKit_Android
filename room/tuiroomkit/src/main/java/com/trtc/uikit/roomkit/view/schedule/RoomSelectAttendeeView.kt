package com.trtc.uikit.roomkit.view.schedule

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.ui.widget.RoomTopBar
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.room.RoomUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * "Add attendee" View: multi-select participants from the friend list.
 *
 * Data source is [ContactStore.friendList]. Supports fuzzy search by userID / nickname.
 * Selected panel: when count <= 10 it shows an inline avatar strip; when > 10 it collapses into a
 * "Selected: n" entry that opens the full selection list dialog on click.
 */
internal class RoomSelectAttendeeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val MAX_VISIBLE_AVATARS = 10
    }

    private val topBar: RoomTopBar by lazy { findViewById(R.id.top_bar) }
    private val etSearch: EditText by lazy { findViewById(R.id.et_search_attendee) }
    private val tvAllTitle: TextView by lazy { findViewById(R.id.tv_all_participant_title) }
    private val rvList: RecyclerView by lazy { findViewById(R.id.rv_attendee_list) }
    private val tvEmpty: TextView by lazy { findViewById(R.id.tv_empty) }
    private val rvSelectedAvatars: RecyclerView by lazy { findViewById(R.id.rv_selected_avatars) }
    private val llSelectedMore: LinearLayout by lazy { findViewById(R.id.ll_selected_more) }
    private val tvSelectedMore: TextView by lazy { findViewById(R.id.tv_selected_more) }
    private val btnConfirm: TextView by lazy { findViewById(R.id.btn_confirm_attendee) }

    private val listAdapter = RoomSelectAttendeeAdapter(context) { info -> toggleSelect(info) }
    private val avatarAdapter = RoomSelectedAvatarAdapter(context)

    private var allFriends: List<ContactInfo> = emptyList()
    private var displayFriends: List<ContactInfo> = emptyList()
    private val selectedIds = LinkedHashSet<String>()
    private val selectedUsers = LinkedHashMap<String, RoomUser>()

    private var subscribeJob: Job? = null
    private var selectedDialog: BottomSheetDialog? = null

    var onBackClick: (() -> Unit)? = null
    var onConfirm: ((List<String>) -> Unit)? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_select_attendee, this)
        setupList()
        setupSearch()
        topBar.onBackClick = { onBackClick?.invoke() }
        btnConfirm.setOnClickListener { onConfirm?.invoke(selectedIds.toList()) }
        llSelectedMore.setOnClickListener { showSelectedDialog() }
        refreshBottomBar()
    }

    /** Pre-selected userIDs. Their ContactInfo is backfilled asynchronously once friendList arrives. */
    fun setInitialSelectedIds(ids: Collection<String>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
        listAdapter.setSelectedIds(selectedIds)
        refreshBottomBar()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            ContactStore.shared.state.friendList.collect { list ->
                allFriends = list
                // Rebuild selectedUsers from the latest data, keeping the original insertion order.
                val orderedUsers = LinkedHashMap<String, RoomUser>()
                for (id in selectedIds) {
                    val info = list.firstOrNull { it.userID == id } ?: continue
                    orderedUsers[id] = info.toRoomUser()
                }
                selectedUsers.clear()
                selectedUsers.putAll(orderedUsers)
                applyFilter(etSearch.text?.toString().orEmpty())
                refreshBottomBar()
            }
        }
        ContactStore.shared.loadFriends()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        subscribeJob?.cancel()
        subscribeJob = null
        selectedDialog?.takeIf { it.isShowing }?.dismiss()
        selectedDialog = null
    }

    private fun setupList() {
        rvList.layoutManager = LinearLayoutManager(context)
        rvList.adapter = listAdapter

        rvSelectedAvatars.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rvSelectedAvatars.adapter = avatarAdapter
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                applyFilter(s?.toString().orEmpty())
            }
        })
    }

    private fun applyFilter(keyword: String) {
        displayFriends = if (keyword.isBlank()) {
            allFriends
        } else {
            val kw = keyword.lowercase()
            allFriends.filter { info ->
                info.userID.lowercase().contains(kw) ||
                    (info.nickname?.lowercase()?.contains(kw) == true)
            }
        }
        listAdapter.setData(displayFriends)
        listAdapter.setSelectedIds(selectedIds)
        tvAllTitle.text = context.getString(R.string.roomkit_all_participant_format, displayFriends.size.toString())
        tvEmpty.visibility = if (displayFriends.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toggleSelect(info: ContactInfo) {
        if (selectedIds.contains(info.userID)) {
            selectedIds.remove(info.userID)
            selectedUsers.remove(info.userID)
        } else {
            selectedIds.add(info.userID)
            selectedUsers[info.userID] = info.toRoomUser()
        }
        listAdapter.setSelectedIds(selectedIds)
        refreshBottomBar()
    }

    /** ContactInfo → RoomUser mapping. Display-name priority: friendRemark > nickname > userID. */
    private fun ContactInfo.toRoomUser(): RoomUser {
        val name = friendRemark?.takeIf { it.isNotEmpty() }
            ?: nickname?.takeIf { it.isNotEmpty() }
            ?: userID
        return RoomUser(userID = userID, userName = name, avatarURL = avatarURL.orEmpty())
    }

    private fun refreshBottomBar() {
        val size = selectedIds.size
        btnConfirm.text = context.getString(R.string.roomkit_confirm_select_format, size.toString())
        // Before friendList emits, selectedUsers is empty; degrade gracefully by hiding the avatar strip.
        val selectedUserList = selectedUsers.values.toList()
        if (size in 1..MAX_VISIBLE_AVATARS && selectedUserList.size == size) {
            rvSelectedAvatars.visibility = View.VISIBLE
            llSelectedMore.visibility = View.GONE
            avatarAdapter.setData(selectedUserList)
        } else if (size > MAX_VISIBLE_AVATARS) {
            rvSelectedAvatars.visibility = View.GONE
            llSelectedMore.visibility = View.VISIBLE
            tvSelectedMore.text = context.getString(R.string.roomkit_selected_participant_format, size.toString())
        } else {
            rvSelectedAvatars.visibility = View.GONE
            llSelectedMore.visibility = View.GONE
        }
    }

    private fun showSelectedDialog() {
        val dialog = selectedDialog ?: BottomSheetDialog(context, R.style.RoomkitBottomSheetDialog)
            .also { selectedDialog = it }

        val view = View.inflate(context, R.layout.roomkit_dialog_selected_attendees, null)
        val dragIndicator: View = view.findViewById(R.id.drag_indicator_selected)
        val tvTitle: TextView = view.findViewById(R.id.tv_selected_dialog_title)
        val rv: RecyclerView = view.findViewById(R.id.rv_selected_attendees)

        dragIndicator.setOnClickListener { dialog.dismiss() }

        // Removing inside the dialog also unselects in the main list, keeping the two views in sync.
        val dialogAdapter = RoomSelectedAttendeeAdapter(context) { user ->
            val contact = allFriends.firstOrNull { it.userID == user.userID }
            if (contact != null) {
                toggleSelect(contact)
                val remaining = selectedUsers.values.toList()
                (rv.adapter as? RoomSelectedAttendeeAdapter)?.setData(remaining)
                tvTitle.text = context.getString(
                    R.string.roomkit_selected_participant_format,
                    selectedIds.size.toString()
                )
            }
        }
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = dialogAdapter
        dialogAdapter.setData(selectedUsers.values.toList())
        tvTitle.text = context.getString(R.string.roomkit_selected_participant_format, selectedIds.size.toString())

        dialog.setContentView(view)
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)
        dialog.show()
    }
}
