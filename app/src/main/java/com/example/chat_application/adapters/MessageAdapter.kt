package com.example.chat_application.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.chat_application.ChatRoomActivity
import com.example.chat_application.HelperFunctions
import com.example.chat_application.ImageViewActivity
import com.example.chat_application.R
import com.example.chat_application.VoiceNotePlayer
import com.example.chat_application.dataclasses.Message
import com.example.chat_application.dataclasses.MessageType
import com.example.chat_application.dataclasses.UserSettings
import com.google.firebase.database.DatabaseReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TAG = "MessageAdapter"

class MessageAdapter(
    private val currentUserId: String,
    private val messageList: List<Message>,
    private val onMessageLongClick: (Int, Message) -> Unit,
    private val onMessageClick: (Int, Message) -> Unit,
    private val database: DatabaseReference,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val SENT_TEXT = 1
    private val RECEIVED_TEXT = 2
    private val SENT_VOICE_NOTE = 3
    private val RECEIVED_VOICE_NOTE = 4
    private val SENT_IMAGE = 5
    private val RECEIVED_IMAGE = 6

    private val voiceNotePlayer = VoiceNotePlayer()

    override fun getItemViewType(position: Int): Int {
        val message = messageList[position]
        return when {
            // Text messages
            message.senderId == currentUserId && message.messageType == MessageType.TEXT -> SENT_TEXT
            message.senderId != currentUserId && message.messageType == MessageType.TEXT -> RECEIVED_TEXT

            // Voice notes
            message.senderId == currentUserId && message.messageType == MessageType.VOICE_NOTE -> SENT_VOICE_NOTE
            message.senderId != currentUserId && message.messageType == MessageType.VOICE_NOTE -> RECEIVED_VOICE_NOTE

            // Image messages
            message.senderId == currentUserId && message.messageType == MessageType.IMAGE -> SENT_IMAGE
            message.senderId != currentUserId && message.messageType == MessageType.IMAGE -> RECEIVED_IMAGE

            // Default to text if unknown
            else -> SENT_TEXT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            SENT_TEXT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.message_sent, parent, false)
                SentTextMessageHolder(view)
            }
            RECEIVED_TEXT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.message_recieved, parent, false)
                ReceivedTextMessageHolder(view)
            }
            SENT_VOICE_NOTE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.message_sent_voice_note, parent, false)
                SentVoiceNoteHolder(view)
            }
            RECEIVED_VOICE_NOTE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.message_received_voice_note, parent, false)
                ReceivedVoiceNoteHolder(view)
            }
            SENT_IMAGE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.message_sent_image, parent, false)
                SentImageMessageHolder(view)
            }
            else -> { // RECEIVED_IMAGE
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.message_received_image, parent, false)
                ReceivedImageMessageHolder(view)
            }
        }
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messageList[position]

        when (holder) {
            is SentTextMessageHolder -> holder.bind(message, position)
            is ReceivedTextMessageHolder -> holder.bind(message, position)
            is SentVoiceNoteHolder -> holder.bind(message, position)
            is ReceivedVoiceNoteHolder -> holder.bind(message, position)
            is SentImageMessageHolder -> holder.bind(message, position)
            is ReceivedImageMessageHolder -> holder.bind(message, position)
        }
    }

    // Base common functions
    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun formatDuration(seconds: Int): String {
        return String.format("%d:%02d", seconds / 60, seconds % 60)
    }

    private fun handleMessageSelection(itemView: View, message: Message, position: Int) {
        val isSelected = (itemView.context as? ChatRoomActivity)?.isMessageSelected(message.id) == true
        itemView.setBackgroundResource(
            if (isSelected) R.drawable.selected_message_background
            else android.R.color.transparent
        )
        itemView.setOnLongClickListener {
            onMessageLongClick(position, message)
            true
        }

        itemView.setOnClickListener {
            onMessageClick(position, message)
        }
    }

    // TEXT MESSAGE HOLDERS
    inner class SentTextMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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

            handleMessageSelection(itemView, message, position)
        }
    }

    inner class ReceivedTextMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageTextView)
        private val timeText: TextView = itemView.findViewById(R.id.timeTextView)
        private val profileImage: ImageView = itemView.findViewById(R.id.profileImageView)

        fun bind(message: Message, position: Int) {
            messageText.text = message.content
            timeText.text = formatTime(message.timestamp)

            var user = HelperFunctions.loadUserById(message.senderId,messageText.context)

            HelperFunctions.loadImageFromUrl(user?.profilePictureUrl.toString(), profileImage)

            if (message.readStatus[currentUserId] != true && UserSettings.readReceipts) {
                message.readStatus[currentUserId] = true

                val readStatusUpdates = HashMap<String, Any>()
                readStatusUpdates["readStatus/${currentUserId}"] = true

                database.child("messages").child(message.chatId).child(message.id).updateChildren(readStatusUpdates)
            }

            handleMessageSelection(itemView, message, position)
        }
    }

    // VOICE NOTE HOLDERS
    inner class SentVoiceNoteHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timeText: TextView = itemView.findViewById(R.id.timeTextView)
        private val statusImage: ImageView = itemView.findViewById(R.id.statusImageView)
        private val playPauseButton: ImageButton = itemView.findViewById(R.id.playPauseButton)
        private val seekBar: SeekBar = itemView.findViewById(R.id.voiceNoteSeekBar)
        private val durationText: TextView = itemView.findViewById(R.id.voiceNoteDurationText)

        fun bind(message: Message, position: Int) {
            timeText.text = formatTime(message.timestamp)
            durationText.text = formatDuration(message.voiceNoteDuration)

            // Set status icon based on read status
            val isRead = message.readStatus.any { (userId, status) ->
                userId != currentUserId && status
            }

            statusImage.setImageResource(
                if (isRead) android.R.drawable.ic_menu_view
                else android.R.drawable.ic_menu_send
            )

            playPauseButton.setOnClickListener {
                voiceNotePlayer.playVoiceNote(
                    message.voiceNoteLocalPath,
                    seekBar,
                    durationText,
                    playPauseButton
                )
            }

            handleMessageSelection(itemView, message, position)
        }
    }

    inner class ReceivedVoiceNoteHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timeText: TextView = itemView.findViewById(R.id.timeTextView)
        private val profileImage: ImageView = itemView.findViewById(R.id.profileImageView)
        private val playPauseButton: ImageButton = itemView.findViewById(R.id.playPauseButton)
        private val seekBar: SeekBar = itemView.findViewById(R.id.voiceNoteSeekBar)
        private val durationText: TextView = itemView.findViewById(R.id.voiceNoteDurationText)

        fun bind(message: Message, position: Int) {
            timeText.text = formatTime(message.timestamp)
            durationText.text = formatDuration(message.voiceNoteDuration)

            var user = HelperFunctions.loadUserById(message.senderId,timeText.context)

            Glide.with(profileImage.context)
                .load(user?.profilePictureUrl)
                .placeholder(R.drawable.circle_background) // optional placeholder
                .error(R.drawable.circle_background)       // optional error fallback
                .circleCrop()
                .into(profileImage)



            if (message.readStatus[currentUserId] != true) {
                message.readStatus[currentUserId] = true

                val readStatusUpdates = HashMap<String, Any>()
                readStatusUpdates["readStatus/${currentUserId}"] = true

                database.child("messages").child(message.chatId).child(message.id).updateChildren(readStatusUpdates)
            }

            playPauseButton.setOnClickListener {
                voiceNotePlayer.playVoiceNote(
                    message.voiceNoteLocalPath,
                    seekBar,
                    durationText,
                    playPauseButton
                )
            }

            handleMessageSelection(itemView, message, position)
        }
    }

    // IMAGE MESSAGE HOLDERS
    inner class SentImageMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imageMessageView)
        private val timeText: TextView = itemView.findViewById(R.id.timeTextView)
        private val statusImage: ImageView = itemView.findViewById(R.id.statusImageView)

        fun bind(message: Message, position: Int) {
            timeText.text = formatTime(message.timestamp)

            // Load image using Glide or your preferred image loading library
            Glide.with(itemView.context)
                .load(message.content)
                .placeholder(R.drawable.ic_image) // Create a placeholder drawable
                .error(R.drawable.ic_error_image) // Create an error drawable
                .into(imageView)

            // Set status icon based on read status
            val isRead = message.readStatus.any { (userId, status) ->
                userId != currentUserId && status
            }

            statusImage.setImageResource(
                if (isRead) android.R.drawable.ic_menu_view
                else android.R.drawable.ic_menu_send
            )


            imageView.setOnClickListener {
                if ((itemView.context as? ChatRoomActivity)?.isInSelectionMode == false) {
                    val intent = Intent(itemView.context, ImageViewActivity::class.java)
                    intent.putExtra("image_url", message.content)
                    itemView.context.startActivity(intent)
                }
            }


            handleMessageSelection(itemView, message, position)
        }
    }

    inner class ReceivedImageMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imageMessageView)
        private val timeText: TextView = itemView.findViewById(R.id.timeTextView)
        private val profileImage: ImageView = itemView.findViewById(R.id.profileImageView)

        fun bind(message: Message, position: Int) {
            timeText.text = formatTime(message.timestamp)


            HelperFunctions.loadImageFromUrl(message.content, imageView)
            var user = HelperFunctions.loadUserById(message.senderId,timeText.context)

            HelperFunctions.loadImageFromUrl(user?.profilePictureUrl.toString(), profileImage)

            // Mark as read
            if (message.readStatus[currentUserId] != true) {
                message.readStatus[currentUserId] = true

                val readStatusUpdates = HashMap<String, Any>()
                readStatusUpdates["readStatus/${currentUserId}"] = true

                database.child("messages").child(message.chatId).child(message.id).updateChildren(readStatusUpdates)
            }

            imageView.setOnClickListener {
                if ((itemView.context as? ChatRoomActivity)?.isInSelectionMode == false) {
                    val intent = Intent(itemView.context, ImageViewActivity::class.java)
                    intent.putExtra("image_url", message.content)
                    itemView.context.startActivity(intent)
                }
            }


            handleMessageSelection(itemView, message, position)
        }
    }

    // Clean up resources when adapter is detached
    fun cleanup() {
        voiceNotePlayer.release()
    }
}