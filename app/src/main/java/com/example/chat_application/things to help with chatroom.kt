//package com.example.chat_application
//
//import android.R
//import android.os.Bundle
//import android.view.LayoutInflater
//import android.view.ViewGroup
//import android.widget.EditText
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.recyclerview.widget.LinearLayoutManager
//import androidx.recyclerview.widget.RecyclerView
//import java.util.Locale
//
//
////todo:adapter
////da men 8er firebase 2aw 2ay 7aga dy examples 3shan tesa3dak bas
//
//class MessageAdapter(
//    private val messageList: List<Message>,
//    private val currentUserId: String
//) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
//
//    companion object {
//        private const val VIEW_TYPE_SENT = 1
//        private const val VIEW_TYPE_RECEIVED = 2
//    }
//
//    override fun getItemViewType(position: Int): Int {
//        val message = messageList[position]
//        return if (message.senderId == currentUserId) {
//            VIEW_TYPE_SENT
//        } else {
//            VIEW_TYPE_RECEIVED
//        }
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
//        return if (viewType == VIEW_TYPE_SENT) {
//            val view = LayoutInflater.from(parent.context)
//                .inflate(R.layout.item_message_sent, parent, false)
//            SentMessageHolder(view)
//        } else {
//            val view = LayoutInflater.from(parent.context)
//                .inflate(R.layout.item_message_received, parent, false)
//            ReceivedMessageHolder(view)
//        }
//    }
//
//    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
//        val message = messageList[position]
//
//        when (holder) {
//            is SentMessageHolder -> holder.bind(message)
//            is ReceivedMessageHolder -> holder.bind(message)
//        }
//    }
//
//    override fun getItemCount(): Int = messageList.size
//
//    // View holders
//    inner class SentMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//        private val messageText: TextView = itemView.findViewById(R.id.messageTextView)
//        private val timeText: TextView = itemView.findViewById(R.id.timeTextView)
//        private val statusImage: ImageView = itemView.findViewById(R.id.statusImageView)
//
//        fun bind(message: Message) {
//            messageText.text = message.content
//            timeText.text = formatTime(message.timestamp)
//
//            // Set status icon based on read status
//            val isRead = message.readStatus.any { (userId, status) ->
//                userId != currentUserId && status
//            }
//
//            statusImage.setImageResource(
//                if (isRead) android.R.drawable.ic_menu_view
//                else android.R.drawable.ic_menu_send
//            )
//        }
//    }
//
//    inner class ReceivedMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//        private val messageText: TextView = itemView.findViewById(R.id.messageTextView)
//        private val timeText: TextView = itemView.findViewById(R.id.timeTextView)
//        private val profileImage: ImageView = itemView.findViewById(R.id.profileImageView)
//
//        fun bind(message: Message) {
//            messageText.text = message.content
//            timeText.text = formatTime(message.timestamp)
//
//            // Load profile image (using your preferred image loading library)
//            // Glide.with(itemView).load(getProfileUrlForUser(message.senderId)).into(profileImage)
//        }
//    }
//
//    private fun formatTime(timestamp: Long): String {
//        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
//        return sdf.format(Date(timestamp))
//    }
//
//    // Helper function to get profile URL (implement based on your user system)
//    private fun getProfileUrlForUser(userId: String): String {
//        // Return a URL based on user ID
//        return "https://example.com/profiles/$userId.jpg"
//    }
//}
//
////todo
////todo
////todo
////todo
////todo
////todo
////todo
////todo
////todo
////todo
////todo
////todo:chatroom activity
//
//private lateinit var messagesRecyclerView: RecyclerView
//private lateinit var messageAdapter: MessageAdapter
//private val messageList = mutableListOf<Message>()
//private val currentUserId = "user_123" // Replace with actual user ID
//private val chatId = "chat_456" // Replace with actual chat ID
//
//override fun onCreate(savedInstanceState: Bundle?) {
//    super.onCreate(savedInstanceState)
//    setContentView(R.layout.activity_chat_room)
//
//    // Set contact name in top bar
//    val contactNameTextView = findViewById<TextView>(R.id.contactNameTextView)
//    contactNameTextView.text = "John Doe" // Replace with actual contact name
//
//    // Initialize RecyclerView
//    messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
//    messageAdapter = MessageAdapter(messageList, currentUserId)
//
//    // Set up RecyclerView
//    val layoutManager = LinearLayoutManager(this)
//    layoutManager.stackFromEnd = true // Messages appear from bottom
//    messagesRecyclerView.layoutManager = layoutManager
//    messagesRecyclerView.adapter = messageAdapter
//
//    // Set up send button
//    val sendButton = findViewById<Button>(R.id.sendButton)
//    val messageInput = findViewById<EditText>(R.id.messageInput)
//
//    sendButton.setOnClickListener {
//        val messageText = messageInput.text.toString().trim()
//        if (messageText.isNotEmpty()) {
//            sendMessage(messageText)
//            messageInput.setText("")
//        }
//    }
//
//    // Add some sample messages for testing
//    addSampleMessages()
//}
//
//private fun sendMessage(messageText: String) {
//    val messageId = UUID.randomUUID().toString()
//    val message = Message(
//        id = messageId,
//        chatId = chatId,
//        senderId = currentUserId,
//        content = messageText,
//        timestamp = System.currentTimeMillis(),
//        readStatus = mapOf(currentUserId to true)
//    )
//
//    messageList.add(message)
//    messageAdapter.notifyItemInserted(messageList.size - 1)
//    messagesRecyclerView.smoothScrollToPosition(messageList.size - 1)
//
//    // Here you would typically send the message to your backend
//}
//
//private fun addSampleMessages() {
//    // Add some sample messages for testing the UI
//    val otherUserId = "user_789"
//
//    val messages = listOf(
//        Message(
//            id = "msg1",
//            chatId = chatId,
//            senderId = otherUserId,
//            content = "Hello there!",
//            timestamp = System.currentTimeMillis() - 3600000,
//            readStatus = mapOf(otherUserId to true, currentUserId to true)
//        ),
//        Message(
//            id = "msg2",
//            chatId = chatId,
//            senderId = currentUserId,
//            content = "Hi! How are you?",
//            timestamp = System.currentTimeMillis() - 3500000,
//            readStatus = mapOf(currentUserId to true, otherUserId to true)
//        ),
//        Message(
//            id = "msg3",
//            chatId = chatId,
//            senderId = otherUserId,
//            content = "I'm doing well, thanks for asking!",
//            timestamp = System.currentTimeMillis() - 3400000,
//            readStatus = mapOf(otherUserId to true, currentUserId to true)
//        ),
//        Message(
//            id = "msg4",
//            chatId = chatId,
//            senderId = currentUserId,
//            content = "That's great to hear!",
//            timestamp = System.currentTimeMillis() - 3300000,
//            readStatus = mapOf(currentUserId to true, otherUserId to false)
//        )
//    )
//
//    messageList.addAll(messages)
//    messageAdapter.notifyDataSetChanged()
//    messagesRecyclerView.scrollToPosition(messageList.size - 1)
//}
