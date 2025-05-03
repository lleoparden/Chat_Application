package com.example.chat_application

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.chat_application.dataclasses.Chat
import com.example.chat_application.dataclasses.Message
import com.example.chat_application.dataclasses.MessageType
import com.example.chat_application.dataclasses.UserSettings
import com.example.chat_application.services.FirebaseService
import com.example.chat_application.services.LocalStorageService
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ChatHistoryActivity : AppCompatActivity() {
    private lateinit var chatHistoryRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var clearChatsButton: ExtendedFloatingActionButton
    private lateinit var toolbar: Toolbar
    private lateinit var messageAdapter: MessageListAdapter
    private lateinit var chat: Chat
    private val messages = mutableListOf<Message>()
    private val firebaseEnabled by lazy { resources.getBoolean(R.bool.firebaseOn) }
    private var messagesListener: ChildEventListener? = null
    private lateinit var messagesReference: com.google.firebase.database.DatabaseReference
    private val TAG = "ChatHistoryActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.chat_history)

        // Initialize services
        LocalStorageService.initialize(this, TAG)
        if (firebaseEnabled) {
            FirebaseService.initialize(this, TAG, firebaseEnabled)
        }

        // Get chat object from intent
        chat = intent.getParcelableExtra("CHAT_OBJECT") ?: run {
            Toast.makeText(this, "Error: Chat data not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize Firebase reference
        if (firebaseEnabled) {
            messagesReference = FirebaseDatabase.getInstance().getReference("messages").child(chat.id)
        }

        initializeViews()
        setupRecyclerView()
        setupToolbar()
        loadMessages()
        setupClearChatsButton()
    }

    private fun initializeViews() {
        chatHistoryRecyclerView = findViewById(R.id.chatHistoryRecyclerView)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        clearChatsButton = findViewById(R.id.clearChatsButton)
        toolbar = findViewById(R.id.toolbar)
    }

    private fun setupRecyclerView() {
        chatHistoryRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // Show latest messages at the bottom
        }
        messageAdapter = MessageListAdapter(
            currentUserId = UserSettings.userId,
            onMessageLongClick = { _, _ -> }, // Placeholder for long-click
            onMessageClick = { _, _ -> } // Placeholder for click
        )
        chatHistoryRecyclerView.adapter = messageAdapter
        messageAdapter.submitList(messages.toList())
        updateEmptyState()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.title = chat.getEffectiveDisplayName()
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadMessages() {
        loadMessagesFromLocalStorage()
        if (firebaseEnabled) {
            setupFirebaseMessageListener()
        }
    }

    private fun loadMessagesFromLocalStorage() {
        val messagesFile = File(filesDir, "messages_${chat.id}.json")
        if (!messagesFile.exists()) {
            updateEmptyState()
            return
        }
        try {
            val content = messagesFile.readText()
            if (content.isNotBlank()) {
                val jsonArray = JSONArray(content)
                messages.clear()
                for (i in 0 until jsonArray.length()) {
                    val jsonMessage = jsonArray.getJSONObject(i)
                    val readStatus = parseReadStatus(jsonMessage)
                    val messageType = try {
                        MessageType.valueOf(jsonMessage.optString("messageType", MessageType.TEXT.toString()))
                    } catch (e: Exception) {
                        MessageType.TEXT
                    }
                    val message = if (messageType == MessageType.VOICE_NOTE) {
                        Message(
                            id = jsonMessage.getString("id"),
                            chatId = jsonMessage.getString("chatId"),
                            senderId = jsonMessage.getString("senderId"),
                            content = jsonMessage.getString("content"),
                            timestamp = jsonMessage.getLong("timestamp"),
                            readStatus = readStatus,
                            messageType = messageType,
                            voiceNoteDuration = jsonMessage.optInt("voiceNoteDuration", 0),
                            voiceNoteLocalPath = jsonMessage.optString("voiceNoteLocalPath", ""),
                            voiceNoteBase64 = jsonMessage.optString("voiceNoteBase64", "")
                        )
                    } else {
                        Message(
                            id = jsonMessage.getString("id"),
                            chatId = jsonMessage.getString("chatId"),
                            senderId = jsonMessage.getString("senderId"),
                            content = jsonMessage.getString("content"),
                            timestamp = jsonMessage.getLong("timestamp"),
                            readStatus = readStatus,
                            messageType = messageType
                        )
                    }
                    messages.add(message)
                }
                messageAdapter.submitList(messages.toList())
                chatHistoryRecyclerView.smoothScrollToPosition(messages.size - 1)
                updateEmptyState()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading messages: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseReadStatus(messageObject: JSONObject): HashMap<String, Boolean> {
        val readStatusMap = HashMap<String, Boolean>()
        if (messageObject.has("readStatus") && !messageObject.isNull("readStatus")) {
            val readStatusObject = messageObject.getJSONObject("readStatus")
            val keys = readStatusObject.keys()
            while (keys.hasNext()) {
                val userId = keys.next()
                readStatusMap[userId] = readStatusObject.getBoolean(userId)
            }
        }
        return readStatusMap
    }

    private fun setupFirebaseMessageListener() {
        messagesListener?.let { messagesReference.removeEventListener(it) }
        messagesListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val messageMap = snapshot.value as? Map<*, *> ?: return
                val message = createMessageFromMap(messageMap) ?: return
                if (message.chatId == chat.id) {
                    messages.removeAll { it.id == message.id }
                    messages.add(message)
                    messageAdapter.submitList(messages.toList())
                    chatHistoryRecyclerView.smoothScrollToPosition(messages.size - 1)
                    updateEmptyState()
                    saveMessagesLocally()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val messageMap = snapshot.value as? Map<*, *> ?: return
                val messageId = snapshot.key ?: return
                val messageIndex = messages.indexOfFirst { it.id == messageId }
                if (messageIndex >= 0) {
                    val updatedMessage = createMessageFromMap(messageMap) ?: return
                    messages[messageIndex] = updatedMessage
                    messageAdapter.submitList(messages.toList())
                    saveMessagesLocally()
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val messageId = snapshot.key
                messages.removeAll { it.id == messageId }
                messageAdapter.submitList(messages.toList())
                updateEmptyState()
                saveMessagesLocally()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ChatHistoryActivity, "Firebase error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
        messagesReference.addChildEventListener(messagesListener!!)
    }

    private fun createMessageFromMap(messageMap: Map<*, *>): Message? {
        try {
            val id = messageMap["id"] as? String ?: return null
            val chatId = messageMap["chatId"] as? String ?: return null
            val sender對于Id = messageMap["senderId"] as? String ?: return null
            val content = messageMap["content"] as? String ?: ""
            val timestamp = (messageMap["timestamp"] as? Long) ?: 0L
            val messageType = try {
                MessageType.valueOf(messageMap["messageType"] as? String ?: MessageType.TEXT.toString())
            } catch (e: Exception) {
                MessageType.TEXT
            }
            val readStatusMap = HashMap<String, Boolean>()
            (messageMap["readStatus"] as? Map<*, *>)?.forEach { (key, value) ->
                if (key is String && value is Boolean) readStatusMap[key] = value
            }
            return if (messageType == MessageType.VOICE_NOTE) {
                val voiceNoteDuration = (messageMap["voiceNoteDuration"] as? Long)?.toInt() ?: 0
                val voiceNoteBase64 = messageMap["voiceNoteBase64"] as? String ?: ""
                val localPath = if (voiceNoteBase64.isNotEmpty()) {
                    val voiceNoteDir = File(filesDir, "VoiceNotes")
                    if (!voiceNoteDir.exists()) voiceNoteDir.mkdirs()
                    val localFilePath = "${voiceNoteDir.absolutePath}/VN_$id.3gp"
                    val localFile = File(localFilePath)
                    if (!localFile.exists() && voiceNoteBase64.isNotEmpty()) {
                        VoiceRecorder(this@ChatHistoryActivity).saveBase64ToFile(voiceNoteBase64, localFilePath)
                    }
                    localFilePath
                } else ""
                val senderId = ""
                Message(
                    id = id, chatId = chatId, senderId = senderId, content = content,
                    timestamp = timestamp, readStatus = readStatusMap, messageType = messageType,
                    voiceNoteDuration = voiceNoteDuration, voiceNoteLocalPath = localPath,
                    voiceNoteBase64 = voiceNoteBase64
                )
            } else {
                val senderId = ""
                Message(
                    id = id, chatId = chatId, senderId = senderId, content = content,
                    timestamp = timestamp, readStatus = readStatusMap, messageType = messageType
                )
            }
        } catch (e: Exception) {
            return null
        }
    }

    private fun saveMessagesLocally() {
        try {
            val messagesFile = File(filesDir, "messages_${chat.id}.json")
            val jsonArray = JSONArray()
            messages.forEach { message ->
                val jsonMessage = JSONObject().apply {
                    put("id", message.id)
                    put("chatId", message.chatId)
                    put("senderId", message.senderId)
                    put("content", message.content)
                    put("timestamp", message.timestamp)
                    put("messageType", message.messageType.toString())
                    if (message.messageType == MessageType.VOICE_NOTE) {
                        put("voiceNoteDuration", message.voiceNoteDuration)
                        put("voiceNoteLocalPath", message.voiceNoteLocalPath)
                        put("voiceNoteBase64", message.voiceNoteBase64)
                    }
                    val readStatusObject = JSONObject()
                    message.readStatus.forEach { (userId, status) ->
                        readStatusObject.put(userId, status)
                    }
                    put("readStatus", readStatusObject)
                }
                jsonArray.put(jsonMessage)
            }
            messagesFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving messages: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupClearChatsButton() {
        clearChatsButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Chat History")
                .setMessage("Are you sure you want to delete all messages?")
                .setPositiveButton("Yes") { _, _ ->
                    clearChatHistory()
                }
                .setNegativeButton("No", null)
                .show()
        }
    }

    private fun clearChatHistory() {
        messages.clear()
        messageAdapter.submitList(messages.toList())
        updateEmptyState()
        val messagesFile = File(filesDir, "messages_${chat.id}.json")
        messagesFile.delete()
        if (firebaseEnabled) {
            messagesReference.removeValue()
                .addOnSuccessListener { Toast.makeText(this, "Chat history cleared", Toast.LENGTH_SHORT).show() }
                .addOnFailureListener { Toast.makeText(this, "Failed to clear chat history", Toast.LENGTH_SHORT).show() }
        } else {
            Toast.makeText(this, "Chat history cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateEmptyState() {
        emptyStateLayout.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
        chatHistoryRecyclerView.visibility = if (messages.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        messagesListener?.let { messagesReference.removeEventListener(it) }
    }

    override fun onResume() {
        super.onResume()
        if (firebaseEnabled && messagesListener != null) {
            messagesReference.addChildEventListener(messagesListener!!)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.let { messagesReference.removeEventListener(it) }
    }
}

// MessageListAdapter using ListAdapter and DiffUtil
class MessageListAdapter(
    private val currentUserId: String,
    private val onMessageLongClick: (Message, Int) -> Unit,
    private val onMessageClick: (Message, Int) -> Unit
) : ListAdapter<Message, MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.message_recieved, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)
        holder.bind(message, currentUserId, onMessageClick, onMessageLongClick)
    }
}

class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
    override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
        return oldItem == newItem
    }
}

class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val messageText: TextView = itemView.findViewById(R.id.messageTextView)

    fun bind(
        message: Message,
        currentUserId: String,
        onMessageClick: (Message, Int) -> Unit,
        onMessageLongClick: (Message, Int) -> Unit
    ) {
        messageText.text = when (message.messageType) {
            MessageType.VOICE_NOTE -> "[Voice Note] ${message.content}"
            else -> message.content
        }
        itemView.setOnClickListener { onMessageClick(message, adapterPosition) }
        itemView.setOnLongClickListener {
            onMessageLongClick(message, adapterPosition)
            true
        }
    }
}