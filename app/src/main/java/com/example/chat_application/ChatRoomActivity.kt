package com.example.chat_application

import Chat
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ChatRoomActivity : AppCompatActivity() {

    // UI Components
    private lateinit var sendBtn: Button
    private lateinit var backBtn: ImageButton
    private lateinit var inputText: EditText
    private lateinit var profilePic: ImageButton
    private lateinit var menuBtn: ImageButton
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var nameView : TextView


    // Data & Adapters
    private lateinit var messageAdapter: MessageAdapter
    private val messageList = mutableListOf<Message>()

    // User & Chat Info
    private val currentUserId = UserSettings.userId
    private lateinit var chatId: String

    // Firebase
    private lateinit var db: FirebaseFirestore
    private lateinit var database: DatabaseReference
    private var messagesListener: ChildEventListener? = null

    // Storage
    private lateinit var localChat: File

    override fun onCreate(savedInstanceState: Bundle?) {
        setupThemeAndLayout(savedInstanceState)
        setupKeyboardBehavior()
        initializeFirebase()
        setupChatInfo()
        initializeViews()
        setupRecyclerView()
        setupClickListeners()

        //!uncomment to clear jason file
        //File(filesDir, "messages.json").delete()


        loadMessagesFromLocalStorage()
        if (resources.getBoolean(R.bool.firebaseOn)) {
            setupRealtimeMessageListener()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove the realtime listener when activity is destroyed
        removeRealtimeMessageListener()
    }

    //region Setup Methods

    private fun setupThemeAndLayout(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.chatroom)
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
                    // Custom animations or behavior can be added here
                }

                lastVisibleHeight = visibleHeight
            }
        })
    }

    private fun initializeFirebase() {
        db = FirebaseFirestore.getInstance()
        database = FirebaseDatabase.getInstance().reference
    }

    private fun setupChatInfo() {
        // Retrieve the chat object from intent
        val chat = intent.getParcelableExtra<Chat>("CHAT_OBJECT")
            ?: Chat(id = "", name = "Chat", lastMessage = "", timestamp = 0, unreadCount = 0)

        chatId = chat.id

        // Set the contact name in the top bar
        val contactNameTextView = findViewById<TextView>(R.id.contactNameTextView)
        contactNameTextView.text = chat.name
    }

    private fun initializeViews() {
        sendBtn = findViewById(R.id.sendButton)
        backBtn = findViewById(R.id.backButton)
        profilePic = findViewById(R.id.accountpic)
        inputText = findViewById(R.id.messageInput)
        menuBtn = findViewById(R.id.menuButton)
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        nameView = findViewById(R.id.contactNameTextView)
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(currentUserId, messageList)
        val layoutManager = LinearLayoutManager(this)
        messagesRecyclerView.layoutManager = layoutManager
        messagesRecyclerView.adapter = messageAdapter
    }

    private fun setupClickListeners() {
        backBtn.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        sendBtn.setOnClickListener {
            val textMsg = inputText.text.toString().trim()
            if (textMsg.isNotEmpty()) {
                sendMessage(textMsg)
                inputText.text.clear()
            }
        }

        profilePic.setOnClickListener{
            startActivity(Intent(this, UserProfileActivity::class.java))
            finish()
        }

        nameView.setOnClickListener{
            startActivity(Intent(this, UserProfileActivity::class.java))
            finish()
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

        // We don't need to add to messageList here anymore since the listener will do it
        saveMessage(message, messageId)
    }

    private fun generateMessageId(): String {
        return db.collection("users").document().id
    }

    private fun saveMessage(messageData: Message, messageId: String) {
        // Save to local storage
        messageList.add(messageData)
        saveMessagesToLocalStorage()

        // Scroll to new message position
        messagesRecyclerView.smoothScrollToPosition(messageList.size - 1)

        // Then attempt to save to Firebase
        if (resources.getBoolean(R.bool.firebaseOn)) {
            saveMessageToFirebase(messageData, messageId)
        }
    }

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
        if (!resources.getBoolean(R.bool.firebaseOn)) {
            Log.d("ChatRoomActivity", "Firebase is disabled, not setting up realtime listener")
            return
        }

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
        messagesListener?.let {
            database.child("messages").child(chatId).removeEventListener(it)
        }
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

        Log.d("ChatRoomActivity", "Saving ${messageList.size} messages to local storage")
        val jsonString = jsonArray.toString()
        writeMessagesToFile(jsonString)
    }

    private fun writeMessagesToFile(jsonString: String) {
        try {
            val file = File(filesDir, "messages.json")
            file.writeText(jsonString)
            Log.d("ChatRoomActivity", "messages saved to file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("ChatRoomActivity", "Error writing to file: ${e.message}")
        }
    }

    private fun loadMessagesFromLocalStorage() {
        Log.d("ChatRoomActivity", "Loading messages from local storage")
        val file = File(filesDir, "messages.json")
        val jsonString = readMessagesFromFile()

        if (!file.exists() || jsonString.isEmpty()) {
            Log.d("ChatRoomActivity", "No messages file found")
            return
        }

        try {
            LoadMessages(jsonString)
        } catch (e: Exception) {
            Log.e("ChatRoomActivity", "Error loading messages: ${e.message}")
            Toast.makeText(this, "Error loading messages: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun LoadMessages(jsonString: String) {
        val jsonArray = JSONArray(jsonString)
        messageList.clear()
        val tempMessages = mutableListOf<Message>()

        for (i in 0 until jsonArray.length()) {
            val messageObject = jsonArray.getJSONObject(i)

            // Only process messages for this chat
            if (messageObject.getString("chatId") == chatId) {
                val readStatus = ReadStatus(messageObject)

                val message = Message(
                    id = messageObject.getString("id"),
                    chatId = messageObject.getString("chatId"),
                    senderId = messageObject.getString("senderId"),
                    content = messageObject.getString("content"),
                    timestamp = messageObject.getLong("timestamp"),
                    readStatus = readStatus
                )

                tempMessages.add(message)
            }
        }

        messageList.addAll(tempMessages)
        messageAdapter.notifyDataSetChanged()
        messagesRecyclerView.scrollToPosition(messageList.size - 1)
    }

    private fun ReadStatus(messageObject: JSONObject): MutableMap<String, Boolean> {
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

    private fun readMessagesFromFile(): String {
        val file = File(filesDir, "messages.json")
        return if (file.exists()) {
            file.readText()
        } else {
            ""
        }
    }
}