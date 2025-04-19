package com.example.chat_application

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter (private val currentUserId: String , private val messageList :List<Message>): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private final val SENT = 1
    private final val RECIEVED = 2
    override fun getItemViewType(position: Int): Int {
        val message = messageList[position]
        return if(message.senderId==currentUserId){
            SENT
        }
        else{
            RECIEVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        return if(viewType==SENT){
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.message_sent,parent, false)
            SentMessageHolder(view)
        }
        else{
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.message_recieved,parent, false)
            ReceivedMessageHolder(view)
        }
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message =messageList[position]
        when (holder) {
            is SentMessageHolder -> holder.bind(message)
            is ReceivedMessageHolder -> holder.bind(message)
        }
    }


    // View holders
    inner class SentMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageTextView)
        private val timeText: TextView = itemView.findViewById(R.id.timeTextView)
        private val statusImage: ImageView = itemView.findViewById(R.id.statusImageView)

        fun bind(message: Message) {
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
        }
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("h:mm a",Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    inner class ReceivedMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageTextView)
        private val timeText: TextView = itemView.findViewById(R.id.timeTextView)
        private val profileImage: ImageView = itemView.findViewById(R.id.profileImageView)

        fun bind(message: Message) {
            messageText.text = message.content
            timeText.text = formatTime(message.timestamp)


        }
    }

}