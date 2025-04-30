package com.example.chat_application

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DatabaseReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val currentUserId: String,
    private val messageList: List<Message>,
    private val onMessageLongClick: (Int, Message) -> Unit,
    private val onMessageClick: (Int, Message) -> Unit,
    private val database: DatabaseReference
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val SENT = 1
    private val RECEIVED = 2

    override fun getItemViewType(position: Int): Int {
        val message = messageList[position]
        return if (message.senderId == currentUserId) {
            SENT
        } else {
            RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == SENT) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.message_sent, parent, false)
            SentMessageHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.message_recieved, parent, false)
            ReceivedMessageHolder(view)
        }
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messageList[position]

        when (holder) {
            is SentMessageHolder -> holder.bind(message, position)
            is ReceivedMessageHolder -> holder.bind(message, position)
        }
    }

    // View holders
    inner class SentMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageTextView)
        private val timeText: TextView = itemView.findViewById(R.id.timeTextView)
        private val statusImage: ImageView = itemView.findViewById(R.id.statusImageView)

        fun bind(message: Message, position: Int) {
            messageText.text = message.content
            timeText.text = formatTime(message.timestamp)

            // Set status icon based on read status
            val isRead = message.readStatus.any { (userId, status) ->
                userId != currentUserId && status
            }

            statusImage.setImageResource(
                if (isRead) android.R.drawable.ic_menu_view
                else android.R.drawable.ic_menu_send
            )

            // Apply selection highlighting
            val isSelected = (itemView.context as? ChatRoomActivity)?.isMessageSelected(message.id) == true
            itemView.setBackgroundResource(
                if (isSelected) R.drawable.selected_message_background
                else android.R.color.transparent
            )

            // Set click listeners
            itemView.setOnLongClickListener {
                onMessageLongClick(position, message)
                true
            }

            itemView.setOnClickListener {
                onMessageClick(position, message)
            }
        }
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    inner class ReceivedMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageTextView)
        private val timeText: TextView = itemView.findViewById(R.id.timeTextView)
        private val profileImage: ImageView = itemView.findViewById(R.id.profileImageView)


        fun bind(message: Message, position: Int) {
            messageText.text = message.content
            timeText.text = formatTime(message.timestamp)

            if (message.readStatus[currentUserId] != true) {
                message.readStatus[currentUserId] = true

                val readStatusUpdates = HashMap<String, Any>()
                readStatusUpdates["readStatus/${currentUserId}"] = true

                database.child("messages").child(message.chatId).child(message.id).updateChildren(readStatusUpdates)

            }


            // Apply selection highlighting
            val isSelected = (itemView.context as? ChatRoomActivity)?.isMessageSelected(message.id) == true
            itemView.setBackgroundResource(
                if (isSelected) R.drawable.selected_message_background
                else android.R.color.transparent
            )

            // Set click listeners
            itemView.setOnLongClickListener {
                onMessageLongClick(position, message)
                true
            }

            itemView.setOnClickListener {
                onMessageClick(position, message)
            }
        }
    }

}