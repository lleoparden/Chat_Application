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
import kotlinx.parcelize.Parcelize
import java.util.*
@Parcelize
data class Chat(
    var id: String = "",
    var name: String = "",
    var lastMessage: String = "",
    var timestamp: Long = 0,
    var unreadCount: Int = 0,
    var participantIds: MutableList<String> = mutableListOf(),
    var type: String = "",
    val displayName: String = ""
):Parcelable {
    fun getEffectiveDisplayName(): String {
        // If displayName is set, use it. Otherwise, fall back to name
        return if (displayName.isNotEmpty()) displayName else name
    }
}

// Binary Search Tree Node for name-based searching
class ChatBSTNode(
    val chat: Chat,
    var left: ChatBSTNode? = null,
    var right: ChatBSTNode? = null
)

class ChatManager {
    private val chatStack = Stack<Chat>()
    private var bstRoot: ChatBSTNode? = null

    // Add a new chat to both data structures
    fun push(chat: Chat) {
        chatStack.push(chat)
        insertIntoBST(chat)
        sortByTimestamp()
    }

    // Add this method to ChatManager class
    // Add this function to your ChatManager class
    fun updateDisplayNames(userMap: Map<String, String>) {
        val allChats = getAll().toMutableList()
        var hasUpdates = false

        for (i in allChats.indices) {
            val chat = allChats[i]

            // For direct chats, update the display name based on the other participant
            if (chat.type == "direct") {
                // Find the other participant's ID (not the current user)
                val otherUserId = chat.participantIds.find { it != UserSettings.userId }

                // If we have the other user's display name in our map, update it
                if (otherUserId != null && userMap.containsKey(otherUserId)) {
                    val newDisplayName = userMap[otherUserId] ?: ""
                    if (chat.displayName != newDisplayName && newDisplayName.isNotEmpty()) {
                        // Create updated chat with new display name
                        allChats[i] = chat.copy(displayName = newDisplayName)
                        hasUpdates = true
                    }
                }
            }
            // For group chats, you might want different logic, or leave displayName as is
        }

        // Only update the chat manager and save if we made changes
        if (hasUpdates) {
            clear()
            pushAll(allChats)
        }
    }
    // Add multiple chats at once
    fun pushAll(chats: List<Chat>) {
        chatStack.addAll(chats)
        for (chat in chats) {
            insertIntoBST(chat)
        }
        sortByTimestamp()
    }

    // BST insertion - now using name instead of name for searching
    private fun insertIntoBST(chat: Chat) {
        if (bstRoot == null) {
            bstRoot = ChatBSTNode(chat)
            return
        }

        insertNode(bstRoot!!, chat)
    }

    private fun insertNode(node: ChatBSTNode, chat: Chat): ChatBSTNode {
        // Compare chat names lexicographically
        val comparison = chat.name.compareTo(node.chat.name, ignoreCase = true)

        when {
            comparison < 0 -> {
                // Insert to the left subtree if name comes before current node
                node.left = node.left?.let { insertNode(it, chat) } ?: ChatBSTNode(chat)
            }
            comparison > 0 -> {
                // Insert to the right subtree if name comes after current node
                node.right = node.right?.let { insertNode(it, chat) } ?: ChatBSTNode(chat)
            }
            else -> {
                // Names are equal, decide based on ID
                if (chat.id != node.chat.id) {
                    node.right = node.right?.let { insertNode(it, chat) } ?: ChatBSTNode(chat)
                }
            }
        }

        return node
    }

    // Find a chat by name using BST (O(log n) search)
    fun findByName(name: String): Chat? {
        return findNodeByName(bstRoot, name)?.chat
    }

    private fun findNodeByName(node: ChatBSTNode?, name: String): ChatBSTNode? {
        if (node == null) return null

        val comparison = name.compareTo(node.chat.name, ignoreCase = true)

        return when {
            comparison < 0 -> findNodeByName(node.left, name)
            comparison > 0 -> findNodeByName(node.right, name)
            else -> node // Found a match
        }
    }

    // Remove a chat from both data structures
    fun removeById(id: String): Boolean {
        val chat = chatStack.find { it.id == id } ?: return false
        val removed = chatStack.remove(chat)

        if (removed) {
            rebuildBST() // Rebuild BST after removal
        }

        return removed
    }

    // Update a chat in both data structures
    fun updateById(id: String, updatedChat: Chat): Boolean {
        val index = chatStack.indexOfFirst { it.id == id }
        if (index != -1) {
            val oldChat = chatStack[index]
            chatStack[index] = updatedChat

            // If name changed, we need to rebuild the BST
            if (oldChat.name != updatedChat.name) {
                rebuildBST()
            }

            sortByTimestamp()
            return true
        }
        return false
    }

    // Rebuild the BST from scratch using the current list of chats
    private fun rebuildBST() {
        bstRoot = null
        for (chat in chatStack) {
            insertIntoBST(chat)
        }
    }

    fun pop(): Chat? {
        if (chatStack.isEmpty()) return null
        val chat = chatStack.pop()
        rebuildBST()  // Update BST after removal
        return chat
    }

    fun peek(): Chat? {
        return if (chatStack.isEmpty()) null else chatStack.peek()
    }

    fun size(): Int = chatStack.size

    fun isEmpty(): Boolean = chatStack.isEmpty()

    fun clear() {
        chatStack.clear()
        bstRoot = null
    }

    fun get(index: Int): Chat = chatStack[index]

    fun getAt(index: Int): Chat = chatStack[index]

    fun getAll(): List<Chat> = chatStack.toList()

    // Sort chats by timestamp (newest first)
    private fun sortByTimestamp() {
        val sortedList = chatStack.sortedByDescending { it.timestamp }
        chatStack.clear()
        chatStack.addAll(sortedList)
    }

    // For finding partial matches using the BST (using name)
    fun findPartialMatches(query: String): List<Chat> {
        val results = mutableListOf<Chat>()
        findPartialMatchesInSubtree(bstRoot, query.lowercase(), results)
        return results
    }

    private fun findPartialMatchesInSubtree(node: ChatBSTNode?, query: String, results: MutableList<Chat>) {
        if (node == null) return

        // First check left subtree (smaller names)
        findPartialMatchesInSubtree(node.left, query, results)

        // Check current node
        if (node.chat.name.lowercase().contains(query)) {
            results.add(node.chat)
        }

        // Check right subtree (larger names)
        findPartialMatchesInSubtree(node.right, query, results)
    }
}

// Chat adapter for RecyclerView
class ChatAdapter(
    private var chats: List<Chat>,
    private val onChatClickListener: OnChatClickListener,
    private val onChatLongClickListener: OnChatLongClickListener
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private var selectedItems = mutableSetOf<String>()
    private var inSelectionMode = false

    interface OnChatClickListener {
        fun onChatClick(chat: Chat)
    }

    interface OnChatLongClickListener {
        fun onChatLongClick(chat: Chat): Boolean
    }

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val chatCardView: ConstraintLayout = itemView.findViewById(R.id.item_chat)
        private val nameTextView: TextView = itemView.findViewById(R.id.chatNameTextView)
        private val lastMessageTextView: TextView = itemView.findViewById(R.id.lastMessageTextView)
        private val timeTextView: TextView = itemView.findViewById(R.id.timeTextView)
        private val unreadCountTextView: TextView = itemView.findViewById(R.id.unreadCountTextView)
        private val avatarImageView: ImageView = itemView.findViewById(R.id.profileImageView)
        private val selectionCheckbox: CheckBox = itemView.findViewById(R.id.selectionCheckbox)

        fun bind(chat: Chat) {
            // Basic chat info
            nameTextView.text = chat.getEffectiveDisplayName()
            lastMessageTextView.text = chat.lastMessage

            // Format timestamp
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            timeTextView.text = sdf.format(Date(chat.timestamp))

            // Set avatar
            if (chat.type == "group") {
                avatarImageView.setImageResource(R.drawable.ic_person)
            } else {
                avatarImageView.setImageResource(R.drawable.ic_person)
            }

            // Show unread count if any
            if (chat.unreadCount > 0) {
                unreadCountTextView.visibility = View.VISIBLE
                unreadCountTextView.text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString()
            } else {
                unreadCountTextView.visibility = View.GONE
            }

            // Handle selection state
            handleSelectionState(chat)

            // Set click listeners
            itemView.setOnClickListener {
                try {
                    onChatClickListener.onChatClick(chat)
                } catch (e: Exception) {
                    Log.e("ChatAdapter", "Error in click listener: ${e.message}")
                }
            }

            itemView.setOnLongClickListener {
                try {
                    onChatLongClickListener.onChatLongClick(chat)
                } catch (e: Exception) {
                    Log.e("ChatAdapter", "Error in long click listener: ${e.message}")
                    false
                }
            }
        }

        private fun handleSelectionState(chat: Chat) {
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
                    selectionCheckbox.isChecked = selectedItems.contains(chat.id)

                    // Apply background changes
                    if (selectedItems.contains(chat.id)) {
                        chatCardView.setBackgroundColor(
                            ContextCompat.getColor(itemView.context, R.color.black)
                        )
                    } else {
                        chatCardView.background =
                            ContextCompat.getDrawable(itemView.context, R.drawable.chatlistborder)
                    }
                } else {
                    // Reset background when not in selection mode
                    chatCardView.background =
                        ContextCompat.getDrawable(itemView.context, R.drawable.chatlistborder)
                }
            } catch (e: Exception) {
                Log.e("ChatAdapter", "Error handling selection state: ${e.message}")
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chats[position]
        holder.bind(chat)
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
}