package com.example.chat_application

import android.icu.text.SimpleDateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.os.Parcelable
import android.util.Log
import android.widget.CheckBox
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.parcelize.Parcelize
import java.util.*

// Chat adapter for RecyclerView
class ChatAdapter(
    private var chats: List<Chat>,
    private val onChatClickListener: OnChatClickListener,
    private val onChatLongClickListener: OnChatLongClickListener,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var selectedItems = mutableSetOf<String>()
    private var inSelectionMode = false

    interface OnChatClickListener {
        fun onChatClick(chat: Chat)
    }

    interface OnChatLongClickListener {
        fun onChatLongClick(chat: Chat): Boolean
    }

    companion object {
        private const val VIEW_TYPE_DIRECT = 1
        private const val VIEW_TYPE_GROUP = 2
    }

    // Direct chat specific ViewHolder
    class DirectChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val chatCardView: ConstraintLayout = itemView.findViewById(R.id.item_chat)
        val nameTextView: TextView = itemView.findViewById(R.id.chatNameTextView)
        val lastMessageTextView: TextView = itemView.findViewById(R.id.lastMessageTextView)
        val timeTextView: TextView = itemView.findViewById(R.id.timeTextView)
        val unreadCountTextView: TextView = itemView.findViewById(R.id.unreadCountTextView)
        val avatarImageView: ImageView = itemView.findViewById(R.id.profileImageView)
        val selectionCheckbox: CheckBox = itemView.findViewById(R.id.selectionCheckbox)
    }

    // Group chat specific ViewHolder
    class GroupChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val chatCardView: ConstraintLayout = itemView.findViewById(R.id.item_chat)
        val nameTextView: TextView = itemView.findViewById(R.id.chatNameTextView)
        val lastMessageTextView: TextView = itemView.findViewById(R.id.lastMessageTextView)
        val timeTextView: TextView = itemView.findViewById(R.id.timeTextView)
        val unreadCountTextView: TextView = itemView.findViewById(R.id.unreadCountTextView)
        val avatarImageView: ImageView = itemView.findViewById(R.id.profileImageView)
        val selectionCheckbox: CheckBox = itemView.findViewById(R.id.selectionCheckbox)
    }

    override fun getItemViewType(position: Int): Int {
        return if (chats[position].type == "direct") VIEW_TYPE_DIRECT else VIEW_TYPE_GROUP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return when (viewType) {
            VIEW_TYPE_DIRECT -> DirectChatViewHolder(view)
            VIEW_TYPE_GROUP -> GroupChatViewHolder(view)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val chat = chats[position]

        when (holder) {
            is DirectChatViewHolder -> bindDirectChat(holder, chat)
            is GroupChatViewHolder -> bindGroupChat(holder, chat)
            else -> throw IllegalArgumentException("Unknown view holder type")
        }
    }

    override fun getItemCount(): Int = chats.size

    fun updateData(newChats: List<Chat>) {
        this.chats = newChats
        notifyDataSetChanged()
    }

    fun updateSelectionMode(inSelectionMode: Boolean) {
        this.inSelectionMode = inSelectionMode
        if (!inSelectionMode) {
            selectedItems.clear()
        }
        notifyDataSetChanged()
    }

    fun updateSelectedItems(selectedIds: Set<String>) {
        this.selectedItems = selectedIds.toMutableSet()
        notifyDataSetChanged()
    }

    fun clearSelections() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    private fun handleSelectionState(chatId: String, chatCardView: ConstraintLayout, selectionCheckbox: CheckBox) {
        try {
            // First ensure the checkbox exists
            if (selectionCheckbox == null) {
                Log.e("ChatAdapter", "Selection checkbox is null")
                return
            }

            // Set visibility BEFORE changing checked state
            selectionCheckbox.visibility = if (inSelectionMode) View.VISIBLE else View.GONE

            // Only set checked state if visible
            if (inSelectionMode) {
                selectionCheckbox.isChecked = selectedItems.contains(chatId)

                // Apply background changes
                if (selectedItems.contains(chatId)) {
                    chatCardView.setBackgroundColor(
                        ContextCompat.getColor(chatCardView.context, R.color.black)
                    )
                } else {
                    chatCardView.background =
                        ContextCompat.getDrawable(chatCardView.context, R.drawable.chatlistborder)
                }
            } else {
                // Reset background when not in selection mode
                chatCardView.background =
                    ContextCompat.getDrawable(chatCardView.context, R.drawable.chatlistborder)
            }
        } catch (e: Exception) {
            Log.e("ChatAdapter", "Error handling selection state: ${e.message}")
        }
    }

    private fun bindDirectChat(holder: DirectChatViewHolder, chat: Chat) {
        // Basic chat info

        holder.lastMessageTextView.text = chat.lastMessage

        // Format timestamp
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        holder.timeTextView.text = sdf.format(Date(chat.timestamp))

        // Load profile picture with extensive logging
        Log.d("ChatAdapter", "Starting profile picture load for chat: ${chat.id}")

        try {
            // Get other participant ID
            val otherUserId = globalFunctions.determineOtherParticipantId(chat)
            Log.d("ChatAdapter", "Other participant ID: $otherUserId")

            if (otherUserId.isEmpty()) {
                Log.e("ChatAdapter", "Empty otherUserId returned - check participantIds in chat object")
                holder.avatarImageView.setImageResource(R.drawable.ic_person)
                return
            }

            // First check if we can get cached data immediately
            val cachedUserData = globalFunctions.getUserData(holder.itemView.context, otherUserId)
            if (cachedUserData != null) {
                Log.d("ChatAdapter", "Cached user data found for $otherUserId")
                Log.d("ChatAdapter", "Profile picture URL from cache: '${cachedUserData.profilePictureUrl}'")

                if (!cachedUserData.profilePictureUrl.isNullOrEmpty()) {
                    Log.d("ChatAdapter", "Loading profile picture from cache: ${cachedUserData.profilePictureUrl}")
                    globalFunctions.loadImageFromUrl(cachedUserData.profilePictureUrl, holder.avatarImageView)
                } else {
                    Log.w("ChatAdapter", "Empty profile picture URL in cached data for $otherUserId")
                    holder.avatarImageView.setImageResource(R.drawable.ic_person)
                }
            } else {
                // Set default immediately, then try to load asynchronously
                holder.avatarImageView.setImageResource(R.drawable.ic_person)

                // Use the correct getUserData method with callback to fetch latest
                Log.d("ChatAdapter", "Fetching user data for ID: $otherUserId")
                globalFunctions.getUserData(otherUserId) { userData ->
                    Log.d("ChatAdapter", "User data callback received for $otherUserId: ${userData != null}")

                    if (userData != null && !userData.profilePictureUrl.isNullOrEmpty()) {
                        Log.d("ChatAdapter", "Loading profile picture from URL: ${userData.profilePictureUrl}")

                        // Make sure we're on the UI thread when updating the ImageView
                        holder.avatarImageView.post {
                            globalFunctions.loadImageFromUrl(userData.profilePictureUrl, holder.avatarImageView)
                            holder.nameTextView.text = userData.displayName
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChatAdapter", "Exception in profile picture loading process: ${e.message}", e)
            holder.avatarImageView.setImageResource(R.drawable.ic_person)
        }

        if( holder.nameTextView.text.isEmpty()){
            holder.nameTextView.text = chat.getEffectiveDisplayName()
        }


        handleSelectionState(chat.id, holder.chatCardView, holder.selectionCheckbox)


        // Set click listeners
        holder.itemView.setOnClickListener {
            try {
                onChatClickListener.onChatClick(chat)
            } catch (e: Exception) {
                Log.e("ChatAdapter", "Error in direct chat click listener: ${e.message}")
            }
        }

        holder.itemView.setOnLongClickListener {
            try {
                onChatLongClickListener.onChatLongClick(chat)
            } catch (e: Exception) {
                Log.e("ChatAdapter", "Error in direct chat long click listener: ${e.message}")
                false
            }
        }
    }

    private fun bindGroupChat(holder: GroupChatViewHolder, chat: Chat) {
        // Basic chat info
        holder.nameTextView.text = chat.name // For groups, use the group name directly
        holder.lastMessageTextView.text = chat.lastMessage

        // Format timestamp
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        holder.timeTextView.text = sdf.format(Date(chat.timestamp))

        // Set group-specific avatar
        holder.avatarImageView.setImageResource(R.drawable.ic_person)

        // Show unread count if any
        if (chat.unreadCount > 0) {
            holder.unreadCountTextView.visibility = View.VISIBLE
            holder.unreadCountTextView.text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString()
        } else {
            holder.unreadCountTextView.visibility = View.GONE
        }


        globalFunctions.getGroupPfp(chat.id) { url ->
            if (url != null) {
                // Make sure we're on the UI thread when updating the ImageView
                holder.avatarImageView.post {
                    globalFunctions.loadImageFromUrl(url, holder.avatarImageView)
                }
            }
        }


        // Handle selection state
        handleSelectionState(chat.id, holder.chatCardView, holder.selectionCheckbox)

        // Set click listeners
        holder.itemView.setOnClickListener {
            try {
                onChatClickListener.onChatClick(chat)
            } catch (e: Exception) {
                Log.e("CombinedChatAdapter", "Error in group chat click listener: ${e.message}")
            }
        }

        holder.itemView.setOnLongClickListener {
            try {
                onChatLongClickListener.onChatLongClick(chat)
            } catch (e: Exception) {
                Log.e("CombinedChatAdapter", "Error in group chat long click listener: ${e.message}")
                false
            }
        }
    }
}