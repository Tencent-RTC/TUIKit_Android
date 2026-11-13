package com.tencent.uikit.app.call.entry

import android.app.Activity
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tencent.uikit.app.App
import com.tencent.uikit.app.R
import com.tencent.uikit.app.call.common.CallAccessSheet
import com.tencent.uikit.app.call.common.CallAccessToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class FriendPickerSheet {

    private var friends: List<ContactInfo> = emptyList()
    private val pickedIDs = mutableSetOf<String>()
    private var sheet: CallAccessSheet? = null
    private var subscribeJob: Job? = null
    private var adapter: FriendAdapter? = null
    private var emptyLabel: TextView? = null

    fun present(activity: Activity, host: ViewGroup, onDone: () -> Unit) {
        val state = (activity.application as App).callEntryState
        pickedIDs.clear()
        pickedIDs.addAll(state.userIDs)

        val sheet = CallAccessSheet(activity)
        this.sheet = sheet
        sheet.onDismiss = {
            subscribeJob?.cancel()
            subscribeJob = null
            onDone()
        }
        sheet.setTitle(activity.getString(R.string.callaccess_friend_picker_title))
        sheet.setAction(activity.getString(R.string.callaccess_sheet_cancel), isCancel = true)
        sheet.onAction = {
            if (confirmSelection(activity, state)) {
                sheet.dismiss()
            }
        }
        sheet.setContent(makeContent(activity, state))
        sheet.present(host)
        loadFriends(activity)
    }

    private fun makeContent(activity: Activity, state: CallEntryState): View {
        val content = LayoutInflater.from(activity).inflate(R.layout.callaccess_friend_picker_content, null)
        emptyLabel = content.findViewById(R.id.friend_empty)
        val list: RecyclerView = content.findViewById(R.id.friend_list)
        list.layoutManager = LinearLayoutManager(activity)
        val adapter = FriendAdapter(activity, state) { syncAction(activity, state) }
        this.adapter = adapter
        list.adapter = adapter
        return content
    }

    private fun loadFriends(activity: Activity) {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            ContactStore.shared.state.friendList.collect { friendList ->
                friends = friendList
                reload(activity)
            }
        }
        ContactStore.shared.loadFriends(object : CompletionHandler {
            override fun onSuccess() {}

            override fun onFailure(code: Int, desc: String) {
                activity.runOnUiThread {
                    if (subscribeJob?.isActive != true) return@runOnUiThread
                    reload(activity)
                }
            }
        })
    }

    private fun reload(activity: Activity) {
        emptyLabel?.isVisible = friends.isEmpty()
        adapter?.updateData(friends, pickedIDs)
        val state = (activity.application as App).callEntryState
        syncAction(activity, state)
    }

    private fun syncAction(activity: Activity, state: CallEntryState) {
        val friendIDs = friends.mapTo(mutableSetOf()) { it.userID }
        val manualCount = state.userIDs.count { it !in friendIDs }
        val hasSelection = pickedIDs.any { it in friendIDs } || manualCount > 0
        sheet?.setAction(
            activity.getString(if (hasSelection) R.string.callaccess_sheet_confirm else R.string.callaccess_sheet_cancel),
            isCancel = !hasSelection
        )
    }

    private fun confirmSelection(activity: Activity, state: CallEntryState): Boolean {
        val friendIDs = friends.mapTo(mutableSetOf()) { it.userID }
        val manualIDs = state.userIDs.filter { it !in friendIDs }
        val selectedFriends = friends.filter { pickedIDs.contains(it.userID) }.map { it.userID }
        val next = manualIDs.toMutableList()
        for (id in selectedFriends) {
            if (next.none { it.equals(id, ignoreCase = true) }) {
                next.add(id)
            }
        }
        if (next.size > CallEntryState.MAX_CALLEE_IDS) {
            CallAccessToast.show(activity, activity.getString(R.string.callaccess_call_too_many))
            return false
        }
        state.userIDs = next
        return true
    }

    private inner class FriendAdapter(
        private val activity: Activity,
        private val state: CallEntryState,
        private val onSelectionChanged: () -> Unit,
    ) : RecyclerView.Adapter<FriendViewHolder>() {

        private var items: List<ContactInfo> = emptyList()
        private var picked: Set<String> = emptySet()

        fun updateData(newItems: List<ContactInfo>, newPicked: Set<String>) {
            items = newItems
            picked = newPicked.toSet()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
            val itemView = LayoutInflater.from(parent.context)
                .inflate(R.layout.callaccess_friend_picker_item, parent, false)
            return FriendViewHolder(itemView)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
            holder.bind(items[position], picked.contains(items[position].userID), showsDivider = position > 0) { friend -> toggle(friend, activity) }
        }

        private fun toggle(friend: ContactInfo, activity: Activity) {
            if (pickedIDs.contains(friend.userID)) {
                pickedIDs.remove(friend.userID)
            } else {
                val friendIDs = friends.mapTo(mutableSetOf()) { it.userID }
                val manualCount = state.userIDs.count { it !in friendIDs }
                if (manualCount + pickedIDs.size >= CallEntryState.MAX_CALLEE_IDS) {
                    CallAccessToast.show(activity, activity.getString(R.string.callaccess_call_too_many))
                    return
                }
                pickedIDs.add(friend.userID)
            }
            updateData(items, pickedIDs)
            onSelectionChanged()
        }

    }
}

private class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val avatar: ImageView = itemView.findViewById(R.id.item_friend_avatar)
    private val name: TextView = itemView.findViewById(R.id.item_friend_name)
    private val check: TextView = itemView.findViewById(R.id.item_friend_check)
    private val divider: View = itemView.findViewById(R.id.item_friend_divider)

    fun bind(friend: ContactInfo, isSelected: Boolean, showsDivider: Boolean, onToggle: (ContactInfo) -> Unit) {
        divider.isVisible = showsDivider
        val displayName = friend.friendRemark?.takeIf { it.isNotEmpty() }
            ?: friend.nickname?.takeIf { it.isNotEmpty() }
            ?: friend.userID
        name.text = displayName
        name.setTextColor(
            ContextCompat.getColor(
                itemView.context,
                if (isSelected) R.color.callaccess_brand else R.color.callaccess_text
            )
        )
        name.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        check.isVisible = isSelected
        val avatarURL = friend.avatarURL
        if (avatarURL.isNullOrEmpty()) {
            Glide.with(itemView).clear(avatar)
            avatar.setImageDrawable(null)
        } else {
            Glide.with(itemView).load(avatarURL).into(avatar)
        }
        itemView.setOnClickListener { onToggle(friend) }
    }
}
