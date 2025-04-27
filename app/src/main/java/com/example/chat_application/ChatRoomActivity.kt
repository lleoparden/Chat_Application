package com.example.chat_application

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.auth.User
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Tasks

class ChatRoomActivity : AppCompatActivity() {

    // UI Components
    private lateinit var sendBtn: Button
    private lateinit var backBtn: ImageButton
    private lateinit var inputText: EditText
    private lateinit var profileImageView: ImageButton
    private lateinit var menuBtn: ImageButton
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var nameView: TextView
    private lateinit var inputLayout: LinearLayout

    private var isInSelectionMode = false
    private val selectedMessageIds = mutableSetOf<String>()
    private lateinit var normalToolbarContent: View
    private lateinit var selectionToolbarContent: View
    private lateinit var selectionCountTextView: TextView
    private lateinit var closeSelectionButton: ImageButton
    private lateinit var deleteSelectedButton: ImageButton
    private lateinit var selectionActionPanel: LinearLayout
    private lateinit var selectionCountText: TextView
    private lateinit var deleteAction: ImageButton

    // Data & Adapters
    private lateinit var messageAdapter: MessageAdapter
    private val messageList = mutableListOf<Message>()

    // User & Chat Info
    private val currentUserId = UserSettings.userId
    private lateinit var chatId: String
    private lateinit var otherParticipantId: String  // Added to track the other user's ID

    // Firebase
    private lateinit var db: FirebaseFirestore
    private lateinit var database: DatabaseReference
    private var messagesListener: ChildEventListener? = null

    // Storage
    private lateinit var chatMessagesFile: File

    private lateinit var chat : Chat

    override fun onCreate(savedInstanceState: Bundle?) {
        setupThemeAndLayout(savedInstanceState)
        setupKeyboardBehavior()
        initializeComponents()
        setupClickListeners()

        loadMessagesFromLocalStorage()

        if (resources.getBoolean(R.bool.firebaseOn)) {
            setupRealtimeMessageListener()
        }


        if ( resources.getBoolean(R.bool.firebaseOn)) {
            val db = FirebaseFirestore.getInstance()
            globalFunctions.setUserOnline(UserSettings.userId, db)
        }

    }

    override fun onDestroy() {
        if (resources.getBoolean(R.bool.firebaseOn)) {
            globalFunctions.setUserOffline(UserSettings.userId)
        }
        removeRealtimeMessageListener()
        super.onDestroy()
    }

    //region Setup Methods

    private fun setupThemeAndLayout(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.chatroom)
    }

    private fun initializeComponents() {
        // Initialize Firebase
        db = FirebaseFirestore.getInstance()
        database = FirebaseDatabase.getInstance().reference

        // Set up chat info
        chat = intent.getParcelableExtra<Chat>("CHAT_OBJECT")
            ?: Chat(id = "", name = "Chat", lastMessage = "", timestamp = 0, unreadCount = 0)
        chatId = chat.id

        // Determine the other participant's ID
        otherParticipantId = globalFunctions.determineOtherParticipantId(chat)

        // Initialize UI elements
        initializeViews()

        // Set contact name in the top bar
        nameView.text = chat.getEffectiveDisplayName()

        initializeProfileImage()

        // Initialize RecyclerView
        setupRecyclerView()

        // Initialize chat-specific messages file
        chatMessagesFile = File(filesDir, "messages_${chatId}.json")
    }

    private fun initializeProfileImage() {

        val user: UserData? = globalFunctions.getUserData(this@ChatRoomActivity, otherParticipantId)

        if (user != null) {
            globalFunctions.loadImageFromUrl(user.profilePictureUrl, profileImageView)
        }

    }





    private fun initializeViews() {
        sendBtn = findViewById(R.id.sendButton)
        backBtn = findViewById(R.id.backButton)
        profileImageView = findViewById(R.id.profileImageView)
        inputText = findViewById(R.id.messageInput)
        menuBtn = findViewById(R.id.menuButton)
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        nameView = findViewById(R.id.contactNameTextView)
        inputLayout = findViewById(R.id.messageInputLayout)

        // New views for selection mode
        normalToolbarContent = findViewById(R.id.normalToolbarContent)
        selectionToolbarContent = findViewById(R.id.selectionToolbarContent)
        selectionCountTextView = findViewById(R.id.selectionCountTextView)
        closeSelectionButton = findViewById(R.id.closeSelectionButton)
        deleteSelectedButton = findViewById(R.id.deleteSelectedButton)
        selectionActionPanel = findViewById(R.id.selectionActionPanel)
        selectionCountText = findViewById(R.id.selectionCountText)
        deleteAction = findViewById(R.id.deleteAction)
    }


    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(
            currentUserId = currentUserId,
            messageList = messageList,
            onMessageLongClick = { position, message ->
                handleMessageLongClick(message)
            },
            onMessageClick = { position, message ->
                if (isInSelectionMode) {
                    toggleMessageSelection(message)
                }
            }
        )

        val layoutManager = LinearLayoutManager(this)
        messagesRecyclerView.layoutManager = layoutManager
        messagesRecyclerView.adapter = messageAdapter
    }

    private fun handleMessageLongClick(message: Message) {
        if (!isInSelectionMode) {
            enterSelectionMode()
        }
        toggleMessageSelection(message)
    }

    private fun enterSelectionMode() {
        isInSelectionMode = true
        selectedMessageIds.clear()

        // Show selection toolbar
        normalToolbarContent.visibility = View.GONE
        selectionToolbarContent.visibility = View.VISIBLE

        // Show selection action panel (optional, depending on your design preference)
        inputLayout.visibility = View.GONE
        selectionActionPanel.visibility = View.VISIBLE

        updateSelectionCount()
    }

    private fun exitSelectionMode() {
        isInSelectionMode = false
        selectedMessageIds.clear()

        // Restore normal toolbar
        selectionToolbarContent.visibility = View.GONE
        normalToolbarContent.visibility = View.VISIBLE

        // Restore input layout
        selectionActionPanel.visibility = View.GONE
        inputLayout.visibility = View.VISIBLE

        // Reset UI
        messageAdapter.notifyDataSetChanged()
    }

    private fun toggleMessageSelection(message: Message) {
        if (selectedMessageIds.contains(message.id)) {
            selectedMessageIds.remove(message.id)
        } else {
            selectedMessageIds.add(message.id)
        }

        // If no messages are selected, exit selection mode
        if (selectedMessageIds.isEmpty()) {
            exitSelectionMode()
            return
        }

        updateSelectionCount()
        messageAdapter.notifyDataSetChanged()
    }

    private fun updateSelectionCount() {
        val count = selectedMessageIds.size
        val text = "$count selected"
        selectionCountTextView.text = text
        selectionCountText.text = text
    }

    private fun deleteSelectedMessages() {
        if (selectedMessageIds.isEmpty()) return

        // Create a new list with unselected messages
        val updatedList = messageList.filter { !selectedMessageIds.contains(it.id) }.toMutableList()

        // Delete from Firebase if enabled
        if (resources.getBoolean(R.bool.firebaseOn)) {
            for (messageId in selectedMessageIds) {
                database.child("messages").child(chatId).child(messageId).removeValue()
            }
        }

        // Update local list
        messageList.clear()
        messageList.addAll(updatedList)

        // Save to local storage
        saveMessagesToLocalStorage()

        // Exit selection mode and update UI
        Toast.makeText(this, "${selectedMessageIds.size} message(s) deleted", Toast.LENGTH_SHORT).show()
        exitSelectionMode()
    }

    private fun setupKeyboardBehavior() {
        // Set window flags for keyboard adjustments
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Add keyboard visibility listener
        val rootView: View = findViewById(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            private val r = Rect()
            private var lastVisibleHeight = 0

            override fun onGlobalLayout() {
                // Calculate visible height
                rootView.getWindowVisibleDisplayFrame(r)
                val visibleHeight = r.height()

                // Detect keyboard visibility changes
                if (lastVisibleHeight != 0 && lastVisibleHeight != visibleHeight) {
                    val isKeyboardVisible = lastVisibleHeight > visibleHeight

                    if (isKeyboardVisible) {
                        adjustRecyclerViewHeight()
                    } else {
                        resetRecyclerViewHeight()
                    }
                }

                lastVisibleHeight = visibleHeight
            }
        })
    }

    private fun adjustRecyclerViewHeight() {
        // Get position of input layout
        val location = IntArray(2)
        inputLayout.getLocationOnScreen(location)
        val inputY = location[1]

        // Apply padding
        val density: Float = resources.displayMetrics.density
        val paddingBottom = (100 * density).toInt()

        // Calculate and set new height
        val newHeight = inputY - paddingBottom

        val params = messagesRecyclerView.layoutParams
        params.height = newHeight
        messagesRecyclerView.layoutParams = params

        // Scroll to bottom
        messagesRecyclerView.scrollToPosition(messageList.size - 1)
    }

    private fun resetRecyclerViewHeight() {
        // Reset to MATCH_PARENT
        val params = messagesRecyclerView.layoutParams
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        messagesRecyclerView.layoutParams = params

        // Add bottom padding for spacing
        messagesRecyclerView.setPadding(
            messagesRecyclerView.paddingLeft,
            messagesRecyclerView.paddingTop,
            messagesRecyclerView.paddingRight,
            (35 * resources.displayMetrics.density).toInt()
        )
    }

    private fun setupClickListeners() {
        backBtn.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        }

        sendBtn.setOnClickListener {
            val textMsg = inputText.text.toString().trim()
            if (textMsg.isNotEmpty()) {
                sendMessage(textMsg)
                inputText.text.clear()
            }
        }

        profileImageView.setOnClickListener {
            val intent = Intent(this, UserProfileActivity::class.java).apply {
                putExtra("came_from", "ChatRoom")
                putExtra("USER_ID", otherParticipantId)
                putExtra("CHAT_OBJECT", chat)
            }
            startActivity(intent)
            finish()
        }

        nameView.setOnClickListener {
            val intent = Intent(this, UserProfileActivity::class.java).apply {
                putExtra("came_from", "ChatRoom")
                putExtra("CHAT_OBJECT", chat)
                putExtra("USER_ID", otherParticipantId)
            }
            startActivity(intent)
            finish()
        }

        // Existing click listeners...

        // Add these new click listeners for selection mode
        closeSelectionButton.setOnClickListener {
            exitSelectionMode()
        }

        deleteSelectedButton.setOnClickListener {
            deleteSelectedMessages()
        }

        deleteAction.setOnClickListener {
            deleteSelectedMessages()
        }
    }

    //region Message Handling

    private fun sendMessage(textMessage: String) {
        val messageId = generateMessageId()

        val message = Message(
            id = messageId,
            chatId = chatId,
            senderId = currentUserId,
            content = textMessage,
            timestamp = System.currentTimeMillis(),
            readStatus = mapOf()
        )

        // Add message to local list and update UI immediately
        messageList.add(message)
        messageAdapter.notifyItemInserted(messageList.size - 1)
        messagesRecyclerView.smoothScrollToPosition(messageList.size - 1)

        // Save locally immediately
        saveMessagesToLocalStorage()

        // Then attempt to save to Firebase if enabled
        if (resources.getBoolean(R.bool.firebaseOn)) {
            saveMessageToFirebase(message, messageId)
        }
    }

    private fun generateMessageId(): String {
        return db.collection("users").document().id
    }

    //region Local Storage

    private fun saveMessagesToLocalStorage() {
        val jsonArray = JSONArray()

        for (message in messageList) {
            val messageObject = JSONObject().apply {
                put("id", message.id)
                put("chatId", message.chatId)
                put("senderId", message.senderId)
                put("content", message.content)
                put("timestamp", message.timestamp)

                // Create a JSONObject for readStatus
                val readStatusObject = JSONObject()
                for ((userId, status) in message.readStatus) {
                    readStatusObject.put(userId, status)
                }
                put("readStatus", readStatusObject)
            }
            jsonArray.put(messageObject)
        }

        Log.d("ChatRoomActivity", "Saving ${messageList.size} messages to chat-specific storage")
        val jsonString = jsonArray.toString()
        writeMessagesToFile(jsonString)
    }

    private fun writeMessagesToFile(jsonString: String) {
        try {
            chatMessagesFile.writeText(jsonString)
            Log.d("ChatRoomActivity", "Messages saved to file: ${chatMessagesFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("ChatRoomActivity", "Error writing to file: ${e.message}")
        }
    }

    private fun loadMessagesFromLocalStorage() {
        Log.d("ChatRoomActivity", "Loading messages from chat-specific storage")
        if (!chatMessagesFile.exists()) {
            Log.d("ChatRoomActivity", "No messages file found for this chat")
            return
        }

        try {
            val jsonString = chatMessagesFile.readText()
            if (jsonString.isNotEmpty()) {
                loadMessages(jsonString)
            }
        } catch (e: Exception) {
            Log.e("ChatRoomActivity", "Error loading messages: ${e.message}")
            Toast.makeText(this, "Error loading messages: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadMessages(jsonString: String) {
        val jsonArray = JSONArray(jsonString)
        messageList.clear()

        for (i in 0 until jsonArray.length()) {
            val messageObject = jsonArray.getJSONObject(i)
            val readStatus = parseReadStatus(messageObject)

            val message = Message(
                id = messageObject.getString("id"),
                chatId = messageObject.getString("chatId"),
                senderId = messageObject.getString("senderId"),
                content = messageObject.getString("content"),
                timestamp = messageObject.getLong("timestamp"),
                readStatus = readStatus
            )

            messageList.add(message)
        }

        Log.d("ChatRoomActivity", "Loaded ${messageList.size} messages for chat $chatId")
        messageAdapter.notifyDataSetChanged()
        if (messageList.isNotEmpty()) {
            messagesRecyclerView.scrollToPosition(messageList.size - 1)
        }
    }

    private fun parseReadStatus(messageObject: JSONObject): MutableMap<String, Boolean> {
        val readStatus = mutableMapOf<String, Boolean>()

        try {
            // Try parsing as JSONObject first
            val readStatusJsonObject = messageObject.getJSONObject("readStatus")
            val keys = readStatusJsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                readStatus[key] = readStatusJsonObject.getBoolean(key)
            }
        } catch (e: Exception) {
            try {
                // Fall back to parsing as JSONArray
                val readStatusArray = messageObject.getJSONArray("readStatus")
                for (j in 0 until readStatusArray.length()) {
                    val entry = readStatusArray.getString(j)
                    if (entry.contains("=")) {
                        val parts = entry.split("=")
                        if (parts.size == 2) {
                            readStatus[parts[0]] = parts[1].toBoolean()
                        }
                    }
                }
            } catch (e2: Exception) {
                Log.e("ChatRoomActivity", "Error parsing readStatus: ${e2.message}")
            }
        }

        return readStatus
    }

    //region Firebase Methods

    private fun saveMessageToFirebase(messageData: Message, messageId: String) {
        val messageMap = hashMapOf(
            "id" to messageData.id,
            "chatId" to messageData.chatId,
            "senderId" to messageData.senderId,
            "content" to messageData.content,
            "timestamp" to messageData.timestamp,
            "readStatus" to messageData.readStatus
        )

        // Save under messages/chatId/messageId in Realtime Database
        database.child("messages")
            .child(chatId)
            .child(messageId)
            .setValue(messageMap)
            .addOnSuccessListener {
                Log.d("Firebase", "Message saved to Realtime DB")
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Error saving to Realtime DB: ${e.message}")
                Toast.makeText(this, "Failed to send message. Check your connection.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupRealtimeMessageListener() {
        val messagesRef = database.child("messages").child(chatId)

        messagesListener = messagesRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                Log.d("Firebase", "New message received in realtime")
                val messageMap = snapshot.value as? Map<*, *> ?: return
                processMessageFromFirebase(messageMap, snapshot.key ?: "")
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                Log.d("Firebase", "Message updated in realtime")
                val messageMap = snapshot.value as? Map<*, *> ?: return
                val messageId = snapshot.key ?: return

                // Find and update the message in our list
                val messageIndex = messageList.indexOfFirst { it.id == messageId }
                if (messageIndex >= 0) {
                    val updatedMessage = createMessageFromMap(messageMap) ?: return
                    messageList[messageIndex] = updatedMessage
                    messageAdapter.notifyItemChanged(messageIndex)
                    saveMessagesToLocalStorage()
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val messageId = snapshot.key ?: return
                val messageIndex = messageList.indexOfFirst { it.id == messageId }
                if (messageIndex >= 0) {
                    messageList.removeAt(messageIndex)
                    messageAdapter.notifyItemRemoved(messageIndex)
                    saveMessagesToLocalStorage()
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Not needed for this implementation
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Database error: ${error.message}")
                Toast.makeText(
                    this@ChatRoomActivity,
                    "Database connection error: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun processMessageFromFirebase(messageMap: Map<*, *>, messageId: String) {
        val message = createMessageFromMap(messageMap) ?: return

        // Check if we already have this message
        val existingIndex = messageList.indexOfFirst { it.id == message.id }
        if (existingIndex >= 0) {
            // Update existing message
            messageList[existingIndex] = message
            messageAdapter.notifyItemChanged(existingIndex)
        } else {
            // Add new message
            messageList.add(message)
            messageAdapter.notifyItemInserted(messageList.size - 1)
            messagesRecyclerView.smoothScrollToPosition(messageList.size - 1)
        }

        saveMessagesToLocalStorage()
    }

    private fun createMessageFromMap(messageMap: Map<*, *>): Message? {
        try {
            val id = messageMap["id"] as? String ?: return null
            val chatId = messageMap["chatId"] as? String ?: return null
            val senderId = messageMap["senderId"] as? String ?: return null
            val content = messageMap["content"] as? String ?: ""
            val timestamp = (messageMap["timestamp"] as? Long) ?: 0L

            val readStatusMap = mutableMapOf<String, Boolean>()
            val readStatusRaw = messageMap["readStatus"] as? Map<*, *>
            readStatusRaw?.forEach { (key, value) ->
                if (key is String && value is Boolean) {
                    readStatusMap[key] = value
                }
            }

            return Message(
                id = id,
                chatId = chatId,
                senderId = senderId,
                content = content,
                timestamp = timestamp,
                readStatus = readStatusMap
            )
        } catch (e: Exception) {
            Log.e("ChatRoomActivity", "Error creating message from map: ${e.message}")
            return null
        }
    }

    private fun removeRealtimeMessageListener() {
        if (resources.getBoolean(R.bool.firebaseOn)) {
            messagesListener?.let {
                database.child("messages").child(chatId).removeEventListener(it)
            }
        }
    }
    fun isMessageSelected(messageId: String): Boolean {
        return selectedMessageIds.contains(messageId)
    }
}