package com.example.chat_application

import android.icu.text.SimpleDateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.*

@Parcelize
data class Chat(
    val id: String,
    val name: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int,
    val participantIds: List<String> = listOf(),
    val type: String = "direct"
) : Parcelable

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

    // Add multiple chats at once
    fun pushAll(chats: List<Chat>) {
        chatStack.addAll(chats)
        for (chat in chats) {
            insertIntoBST(chat)
        }
        sortByTimestamp()
    }

    // BST insertion
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
                // Names are equal, decide based on ID or other criterion
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

    fun getAll(): List<Chat> = chatStack.toList()

    // Sort chats by timestamp (newest first)
    private fun sortByTimestamp() {
        val sortedList = chatStack.sortedByDescending { it.timestamp }
        chatStack.clear()
        chatStack.addAll(sortedList)
    }

    // For finding partial matches using the BST (more efficient than checking every chat)
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
class ChatAdapter(private val chatManager: ChatManager, private val listener: OnChatClickListener) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    interface OnChatClickListener {
        fun onChatClick(chat: Chat)
    }

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.chatNameTextView)
        private val lastMessageTextView: TextView = itemView.findViewById(R.id.lastMessageTextView)
        private val timeTextView: TextView = itemView.findViewById(R.id.timeTextView)
        private val unreadCountTextView: TextView = itemView.findViewById(R.id.unreadCountTextView)

        fun bind(chat: Chat) {
            nameTextView.text = chat.name
            lastMessageTextView.text = chat.lastMessage

            // Format timestamp
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            timeTextView.text = sdf.format(Date(chat.timestamp))

            // Show unread count if any
            if (chat.unreadCount > 0) {
                unreadCountTextView.visibility = View.VISIBLE
                unreadCountTextView.text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString()
            } else {
                unreadCountTextView.visibility = View.GONE
            }

            // Set click listener
            itemView.setOnClickListener {
                listener.onChatClick(chat)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(chatManager.get(position))
    }

    override fun getItemCount(): Int = chatManager.size()
}

class ChatCreationManager(private val chatManager: ChatManager) {

    // Create a new chat and return it without saving
    fun createChat(name: String, participantIds: List<String>, type: String = "direct"): Chat {
        return Chat(
            id = generateUniqueId(),
            name = name,
            lastMessage = "",
            timestamp = System.currentTimeMillis(),
            unreadCount = 0,
            participantIds = participantIds,
            type = type
        )
    }

    // Save a chat to both local storage and data structures
    fun saveChat(chat: Chat) {
        chatManager.push(chat)
        saveChatsToLocalStorage()
    }

    // Save multiple chats at once
    fun saveChats(chats: List<Chat>) {
        chatManager.pushAll(chats)
        saveChatsToLocalStorage()
    }

    // Save all chats to local storage - needs implementation
    private fun saveChatsToLocalStorage() {
        // Implementation needs to be added
        // This should convert chats to JSON and save to file
    }

    // Generate a unique ID for new chats
    private fun generateUniqueId(): String {
        return "chat_${System.currentTimeMillis()}_${(0..999).random()}"
    }
}