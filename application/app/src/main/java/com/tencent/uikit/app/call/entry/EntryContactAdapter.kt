package com.tencent.uikit.app.call.entry

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tencent.uikit.app.R
import com.tencent.uikit.app.call.common.dp

internal sealed class EntryListItem {
    abstract val id: String

    data class Friend(
        val contact: EntryContact,
        val selected: Boolean,
        val first: Boolean,
        val last: Boolean,
    ) : EntryListItem() {
        override val id: String = "friend:${contact.key}"
    }

    data class Remote(val contact: EntryContact, val added: Boolean) : EntryListItem() {
        override val id: String = "remote:${contact.key}"
    }

    data class Empty(val title: String, val subtitle: String?, val action: String?) : EntryListItem() {
        override val id: String = "empty"
    }
}

internal class EntryContactAdapter(
    private val controller: EntryContactController,
    private val onRetry: () -> Unit,
) : ListAdapter<EntryListItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is EntryListItem.Friend -> TYPE_FRIEND
        is EntryListItem.Remote -> TYPE_REMOTE
        is EntryListItem.Empty -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_EMPTY -> EmptyHolder(inflater.inflate(R.layout.callaccess_entry_list_empty, parent, false))
            else -> ContactHolder(inflater.inflate(R.layout.callaccess_entry_contact_item, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is EntryListItem.Friend -> (holder as ContactHolder).bindFriend(item)
            is EntryListItem.Remote -> (holder as ContactHolder).bindRemote(item)
            is EntryListItem.Empty -> (holder as EmptyHolder).bind(item)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is ContactHolder) holder.recycle()
        super.onViewRecycled(holder)
    }

    private inner class EmptyHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.empty_title)
        private val subtitle: TextView = view.findViewById(R.id.empty_subtitle)
        private val action: TextView = view.findViewById(R.id.empty_action)

        fun bind(item: EntryListItem.Empty) {
            title.text = item.title
            subtitle.isVisible = item.subtitle != null
            subtitle.text = item.subtitle
            action.isVisible = item.action != null
            action.text = item.action
            action.setOnClickListener { onRetry() }
        }
    }

    private inner class ContactHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val divider: View = view.findViewById(R.id.contact_divider)
        private val avatar: ImageView = view.findViewById(R.id.contact_avatar)
        private val name: TextView = view.findViewById(R.id.contact_name)
        private val id: TextView = view.findViewById(R.id.contact_id)
        private val tag: TextView = view.findViewById(R.id.contact_tag)
        private val check: ImageView = view.findViewById(R.id.contact_check)
        private val action: TextView = view.findViewById(R.id.contact_action)

        init {
            avatar.clipToOutline = true
        }

        fun bindFriend(item: EntryListItem.Friend) {
            bindCommon(item.contact, item.first, item.last)
            tag.isVisible = item.contact.isFriend
            action.isVisible = false
            check.isVisible = true
            check.setBackgroundResource(
                if (item.selected) R.drawable.callaccess_bg_check_selected else R.drawable.callaccess_bg_check,
            )
            check.setImageResource(if (item.selected) R.drawable.callaccess_icon_check else 0)
            itemView.isClickable = true
            itemView.setOnClickListener { controller.toggle(item.contact) }
        }

        fun bindRemote(item: EntryListItem.Remote) {
            bindCommon(item.contact, first = true, last = true)
            tag.isVisible = false
            check.isVisible = false
            action.isVisible = true
            val context = itemView.context
            action.text = context.getString(
                if (item.added) R.string.callaccess_entry_added else R.string.callaccess_entry_add,
            )
            action.textSize = if (item.added) 13f else 14f
            action.paint.isFakeBoldText = !item.added
            action.setTextColor(
                ContextCompat.getColor(context, if (item.added) R.color.callaccess_text3 else R.color.callaccess_brand),
            )
            if (item.added) {
                itemView.setOnClickListener(null)
                itemView.isClickable = false
            } else {
                itemView.setOnClickListener { controller.addRemote(item.contact) }
            }
        }

        fun recycle() {
            Glide.with(avatar).clear(avatar)
        }

        private fun bindCommon(contact: EntryContact, first: Boolean, last: Boolean) {
            val context = itemView.context
            divider.isVisible = !first
            itemView.background = cardBackground(first, last)
            name.text = contact.displayName
            id.text = context.getString(R.string.callaccess_entry_user_id_format, contact.userID)
            Glide.with(avatar).load(contact.avatarURL).circleCrop()
                .placeholder(R.drawable.callaccess_default_avatar)
                .error(R.drawable.callaccess_default_avatar)
                .fallback(R.drawable.callaccess_default_avatar)
                .into(avatar)
        }

        private fun cardBackground(first: Boolean, last: Boolean): GradientDrawable {
            val radius = 12.dp(itemView.context).toFloat()
            val top = if (first) radius else 0f
            val bottom = if (last) radius else 0f
            return GradientDrawable().apply {
                setColor(ContextCompat.getColor(itemView.context, R.color.callaccess_card))
                cornerRadii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
            }
        }
    }

    private companion object {
        const val TYPE_FRIEND = 1
        const val TYPE_REMOTE = 2
        const val TYPE_EMPTY = 3

        val DIFF = object : DiffUtil.ItemCallback<EntryListItem>() {
            override fun areItemsTheSame(oldItem: EntryListItem, newItem: EntryListItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: EntryListItem, newItem: EntryListItem): Boolean =
                oldItem == newItem
        }
    }
}
