import android.icu.text.SimpleDateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.chat_application.R
import java.util.Date
import java.util.Locale

data class Chat(
    val id: String = "",
    val name: String = "",
    val lastMessage: String = "",
    val timestamp: Long = 0,
    val unreadCount: Int = 0
)

// Chat adapter for RecyclerView
class ChatAdapter(private val chats: List<Chat>, private val listener: OnChatClickListener) :
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
                if(chat.unreadCount > 99){
                    unreadCountTextView.text = "99+"
                }else
                unreadCountTextView.text = chat.unreadCount.toString()
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
        holder.bind(chats[position])
    }

    override fun getItemCount(): Int = chats.size
}