import android.icu.text.SimpleDateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.chat_application.R
import java.util.Date
import java.util.Locale
import java.util.Stack

data class Chat(
    val id: String,
    val name: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int,
    val participantIds: List<String> = listOf(),
    val type: String = "direct" // "direct" or "group"
)

class ChatManager {
    private val chatStack = Stack<Chat>()

    // Add a new chat to the stack
    fun push(chat: Chat) {
        chatStack.push(chat)
        sortByTimestamp()
    }

    // Add multiple chats at once
    fun pushAll(chats: List<Chat>) {
        chatStack.addAll(chats)
        sortByTimestamp()
    }

    // Remove and return the top chat from the stack
    fun pop(): Chat? {
        if (chatStack.isEmpty()) return null
        return chatStack.pop()
    }

    // Look at the top chat without removing it
    fun peek(): Chat? {
        if (chatStack.isEmpty()) return null
        return chatStack.peek()
    }

    // Get the size of the stack
    fun size(): Int = chatStack.size

    // Check if the stack is empty
    fun isEmpty(): Boolean = chatStack.isEmpty()

    // Clear the stack
    fun clear() {
        chatStack.clear()
    }

    // Get a specific chat by index
    fun get(index: Int): Chat {
        return chatStack[index]
    }

    // Get all chats as a list
    fun getAll(): List<Chat> {
        return chatStack.toList()
    }

    // Sort chats by timestamp (newest first)
    private fun sortByTimestamp() {
        val sortedList = chatStack.sortedByDescending { it.timestamp }
        chatStack.clear()
        chatStack.addAll(sortedList)
    }

    // Remove a chat by ID
    fun removeById(id: String): Boolean {
        val chat = chatStack.find { it.id == id } ?: return false
        return chatStack.remove(chat)
    }

    // Update a chat by ID
    fun updateById(id: String, updatedChat: Chat): Boolean {
        val index = chatStack.indexOfFirst { it.id == id }
        if (index != -1) {
            chatStack[index] = updatedChat
            sortByTimestamp()
            return true
        }
        return false
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
                if (chat.unreadCount > 99) {
                    unreadCountTextView.text = "99+"
                } else {
                    unreadCountTextView.text = chat.unreadCount.toString()
                }
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