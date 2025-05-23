package com.example.chat_application

import android.content.Intent
import android.Manifest
import android.media.MediaPlayer
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.MotionEvent
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
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
import com.example.chat_application.adapters.MessageAdapter
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.example.chat_application.dataclasses.Chat
import com.example.chat_application.dataclasses.Message
import com.example.chat_application.dataclasses.MessageType
import com.example.chat_application.dataclasses.UserSettings
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.chat_application.dataclasses.UserData
import com.github.dhaval2404.imagepicker.ImagePicker
import com.example.chat_application.services.ImageUploadService
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction

class ChatRoomActivity : AppCompatActivity() {

    // UI Components
    private lateinit var sendBtn: Button
    private lateinit var backBtn: ImageButton
    private lateinit var inputText: EditText
    private lateinit var profileImageView: ImageButton
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var nameView: TextView
    private lateinit var inputLayout: LinearLayout
    private lateinit var chatBackground: ImageView

    var isInSelectionMode = false
    private val selectedMessageIds = mutableSetOf<String>()
    private lateinit var normalToolbarContent: View
    private lateinit var selectionToolbarContent: View
    private lateinit var selectionCountTextView: TextView
    private lateinit var closeSelectionButton: ImageButton
    private lateinit var deleteSelectedButton: ImageButton
    private lateinit var selectionActionPanel: LinearLayout
    private lateinit var selectionCountText: TextView
    private lateinit var deleteAction: ImageButton

    private lateinit var voiceRecorder: VoiceRecorder
    private lateinit var recordButton: ImageButton
    private lateinit var recordingLayout: View
    private lateinit var recordStateText: TextView
    private var isRecording = false
    private val RECORD_AUDIO_PERMISSION_CODE = 101
    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var secondsElapsed = 0

    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var imageButton: ImageButton
    private var isImageUploading = false
    private lateinit var imagePreviewLayout: LinearLayout
    private lateinit var previewImageView: ImageView
    private lateinit var cancelImageButton: Button
    private lateinit var confirmImageButton: Button
    private var selectedImageUri: Uri? = null

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

    private lateinit var chat: Chat
    var user: UserData? = null

    //region Lifecycle Methods
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("ChatRoomActivity", "initializing chatroom ")
        setupThemeAndLayout(savedInstanceState)
        voiceRecorder = VoiceRecorder(this)
        setupImagePickerLauncher()
        setupKeyboardBehavior()
        initializeComponents()
        setupClickListeners()

        chatBackground = findViewById(R.id.chatBackground)

        // Load the wallpaper
        loadChatWallpaper()


        loadMessagesFromLocalStorage()

        if (resources.getBoolean(R.bool.firebaseOn)) {
            setupRealtimeMessageListener()
        }

        if (resources.getBoolean(R.bool.firebaseOn)) {
            val db = FirebaseFirestore.getInstance()
            HelperFunctions.setUserOnline(UserSettings.userId, db)
        }
    }

    override fun onDestroy() {
        if (resources.getBoolean(R.bool.firebaseOn)) {
            HelperFunctions.setUserOffline(UserSettings.userId)
        }
        removeRealtimeMessageListener()

        // Clean up voice notes resources
        if (isRecording) {
            voiceRecorder.cancelRecording()
        }

        // Clean up voice note player in adapter
        if (::messageAdapter.isInitialized) {
            messageAdapter.cleanup()
        }

        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Recording permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Recording permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
    //endregion

    //region Setup and Initialization
    private fun setupThemeAndLayout(savedInstanceState: Bundle?) {
        // Apply theme from UserSettings before calling super.onCreate()
        setTheme(UserSettings.getThemeResource())

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
            ?: Chat(id = "", name = "Chat", lastMessage = "", timestamp = 0)
        chatId = chat.id

        // Determine the other participant's ID
        otherParticipantId = HelperFunctions.determineOtherParticipantId(chat).toString()

        // Initialize UI elements
        initializeViews()

        nameView.text = chat.getEffectiveDisplayName()

        initializeProfileImage()

        // Initialize RecyclerView
        setupRecyclerView()

        // Initialize chat-specific messages file
        chatMessagesFile = File(filesDir, "messages_${chatId}.json")
    }

    private fun initializeViews() {
        sendBtn = findViewById(R.id.sendButton)
        backBtn = findViewById(R.id.backButton)
        profileImageView = findViewById(R.id.profileImageView)
        inputText = findViewById(R.id.messageInput)

        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        nameView = findViewById(R.id.contactNameTextView)
        inputLayout = findViewById(R.id.messageInputLayout)



        recordButton = findViewById(R.id.recordButton)
        recordingLayout = findViewById(R.id.recordingLayout)
        recordStateText = findViewById(R.id.recordStateText)

        imageButton = findViewById(R.id.imageButton)
        imagePreviewLayout = findViewById(R.id.imagePreviewLayout)
        previewImageView = findViewById(R.id.previewImageView)
        cancelImageButton = findViewById(R.id.cancelImageButton)
        confirmImageButton = findViewById(R.id.confirmImageButton)


        // Initially hide recording UI
        findViewById<LinearLayout>(R.id.recordingLayout).visibility = View.GONE
        imagePreviewLayout.visibility = View.GONE


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

    private fun initializeProfileImage() {

        if (chat.type == "direct") {
            user = HelperFunctions.loadUserById(otherParticipantId, this)

            if (user != null) {
                HelperFunctions.loadImageFromUrl(user!!.profilePictureUrl, profileImageView)
                nameView.text = user!!.displayName
            } else {
                HelperFunctions.getUserData(otherParticipantId) { user ->
                    HelperFunctions.loadImageFromUrl(
                        user?.profilePictureUrl.toString(),
                        profileImageView
                    )
                    nameView.text = user?.displayName.toString()
                }
            }
        } else {
            HelperFunctions.getGroupPfp(chat.id) { url ->
                if (url != null) {
                    // Make sure we're on the UI thread when updating the ImageView
                    profileImageView.post {
                        HelperFunctions.loadImageFromUrl(url, profileImageView)
                        // Set contact name in the top bar
                    }
                }
            }
        }

    }

    private fun setupRecyclerView() {

        messageList.sortBy { it.timestamp }

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
            },
            database
        )

        val layoutManager = LinearLayoutManager(this)
        messagesRecyclerView.layoutManager = layoutManager
        messagesRecyclerView.adapter = messageAdapter
    }

    private fun setupClickListeners() {
        backBtn.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        }

        sendBtn.setOnClickListener {
            val textMsg = inputText.text.toString().trim()
            val messageSound = MediaPlayer.create(this, R.raw.happy_pop_3_185288)
            if (textMsg.isNotEmpty()) {
                sendMessage(textMsg)
                inputText.text.clear()
                messageSound.start()
            }
        }

        setupVoiceRecordingButton()

        imageButton.setOnClickListener {
            pickImage()
        }

        cancelImageButton.setOnClickListener {
            hideImagePreview()
        }

        confirmImageButton.setOnClickListener {
            // If there's a selected image, upload it
            val messageSound = MediaPlayer.create(this, R.raw.happy_pop_3_185288)
            selectedImageUri?.let { uri ->
                uploadAndSendImage(uri)
            }
            messageSound.start()
        }



        profileImageView.setOnClickListener {
            if (chat.type == "group") {
                val intent = Intent(this, GroupProfileActivity::class.java).apply {
                    putExtra("came_from", "ChatRoom")
                    putExtra("CHAT_OBJECT", chat)
                }
                startActivity(intent)
                finish()
            } else {
                val intent = Intent(this, UserProfileActivity::class.java).apply {
                    putExtra("came_from", "ChatRoom")
                    putExtra("USER_ID", otherParticipantId)
                    putExtra("CHAT_OBJECT", chat)
                }
                startActivity(intent)
                finish()
            }
        }

        nameView.setOnClickListener {
            if (chat.type == "group") {
                val intent = Intent(this, GroupProfileActivity::class.java).apply {
                    putExtra("came_from", "ChatRoom")
                    putExtra("CHAT_OBJECT", chat)
                }
                startActivity(intent)
                finish()
            } else {
                val intent = Intent(this, UserProfileActivity::class.java).apply {
                    putExtra("came_from", "ChatRoom")
                    putExtra("CHAT_OBJECT", chat)
                    putExtra("USER_ID", otherParticipantId)
                }
                startActivity(intent)
                finish()
            }
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

    private fun setupKeyboardBehavior() {
        // Set window flags to adjust resize for keyboard
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Get the root view for detecting layout changes
        val rootView: View = findViewById(android.R.id.content)

        // Define default bottom padding - replace with your own value
        val defaultBottomPadding = 80.dpToPx()

        // Reference to message input layout
        val messageInputLayout: View = findViewById(R.id.messageInputLayout)

        rootView.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            private val rect = Rect()
            private var lastVisibleHeight = 0
            private var initialHeight = 0

            override fun onGlobalLayout() {
                // Get current visible frame
                rootView.getWindowVisibleDisplayFrame(rect)
                val visibleHeight = rect.height()

                // Initialize height on first run
                if (initialHeight == 0) {
                    initialHeight = visibleHeight
                }

                // Detect keyboard visibility changes
                if (lastVisibleHeight != 0 && lastVisibleHeight != visibleHeight) {
                    val isKeyboardVisible =
                        visibleHeight < initialHeight * 0.85 // More reliable detection

                    if (isKeyboardVisible) {
                        // Calculate keyboard height as a percentage of screen height for better adaptation
                        val keyboardHeight = initialHeight - visibleHeight
                        val safeKeyboardPadding = keyboardHeight + (defaultBottomPadding / 2)

                        // Apply padding to accommodate keyboard
                        messagesRecyclerView.setPadding(
                            messagesRecyclerView.paddingLeft,
                            messagesRecyclerView.paddingTop,
                            messagesRecyclerView.paddingRight,
                            safeKeyboardPadding + 150
                        )
                    } else {
                        // Reset padding when keyboard is hidden
                        messagesRecyclerView.setPadding(
                            messagesRecyclerView.paddingLeft,
                            messagesRecyclerView.paddingTop,
                            messagesRecyclerView.paddingRight,
                            defaultBottomPadding
                        )
                    }

                    // Smooth scroll to bottom after layout adjustment with small delay
                    messagesRecyclerView.postDelayed({
                        if (messageList.isNotEmpty()) {
                            messagesRecyclerView.smoothScrollToPosition(messageList.size - 1)
                        }
                    }, 100)
                }

                lastVisibleHeight = visibleHeight
            }
        })

        // Helper extension function for dp to pixel conversion
        fun Int.dpToPx(): Int {
            val density = resources.displayMetrics.density
            return (this * density).toInt()
        }
    }

    private fun loadChatWallpaper() {
        val wallpaperPath = UserSettings.getChatWallpaper()

        Glide.with(this)
            .load(if (wallpaperPath != null) Uri.fromFile(File(wallpaperPath)) else R.drawable.chatbg)
            .apply(
                RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.NONE) // Disable caching
                    .skipMemoryCache(true) // Skip memory cache
                    .error(R.drawable.chatbg)
            )
            .into(chatBackground)
    }
    //endregion

    //region Message Handling
    private fun sendMessage(textMessage: String) {
        val messageId = generateMessageId()

        var map: HashMap<String, Boolean> = HashMap()

        for (par in chat.participantIds.keys) {
            if (par != currentUserId) {
                map[par] = false
            }
        }

        // Increment unread counts for all other participants
        for (participantId in chat.participantIds.keys) {
            if (participantId != currentUserId) {
                // Get the current unread count reference
                val unreadCountRef = database.child("chats").child(chatId)
                    .child("unreadCount").child(participantId)

                // Use a transaction to safely increment the counter
                unreadCountRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(mutableData: MutableData): Transaction.Result {
                        val count = mutableData.getValue(Int::class.java) ?: 0
                        mutableData.value = count + 1
                        return Transaction.success(mutableData)
                    }

                    override fun onComplete(
                        databaseError: DatabaseError?,
                        committed: Boolean,
                        dataSnapshot: DataSnapshot?
                    ) {
                        if (databaseError != null) {
                            Log.e(
                                "Firebase",
                                "Error updating unread count: ${databaseError.message}"
                            )
                        }
                    }
                })
            }
        }

        val message = Message(
            id = messageId,
            chatId = chatId,
            senderId = currentUserId,
            content = textMessage,
            timestamp = System.currentTimeMillis(),
            readStatus = map
        )

        val timestampUpdate = hashMapOf<String, Any>("timestamp" to message.timestamp)
        val lastMessageUpdate = hashMapOf<String, Any>("lastMessage" to message.content)

        database.child("chats").child(message.chatId).updateChildren(timestampUpdate)
        database.child("chats").child(message.chatId).updateChildren(lastMessageUpdate)

        // Add message to local list
        messageList.add(message)

        // Sort the list after adding the new message
        sortMessagesByTimestamp()

        // Update UI
        messageAdapter.notifyDataSetChanged()
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
            // Re-sort the list after adding the message
            sortMessagesByTimestamp()
            // Notify the adapter that the dataset has changed
            messageAdapter.notifyDataSetChanged()
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

            val messageTypeStr = messageMap["messageType"] as? String ?: MessageType.TEXT.toString()
            val messageType = try {
                MessageType.valueOf(messageTypeStr)
            } catch (e: Exception) {
                MessageType.TEXT
            }

            val readStatusMap = HashMap<String, Boolean>()
            val readStatusRaw = messageMap["readStatus"] as? Map<*, *>
            readStatusRaw?.forEach { (key, value) ->
                if (key is String && value is Boolean) {
                    readStatusMap[key] = value
                }
            }

            return if (messageType == MessageType.VOICE_NOTE) {
                val voiceNoteDuration = (messageMap["voiceNoteDuration"] as? Long)?.toInt() ?: 0
                val voiceNoteBase64 = messageMap["voiceNoteBase64"] as? String ?: ""

                // Generate a local file path for the voice note
                val localPath = if (voiceNoteBase64.isNotEmpty()) {
                    // Create a unique filename based on message ID
                    val voiceNoteDir = File(filesDir, "VoiceNotes")
                    if (!voiceNoteDir.exists()) {
                        voiceNoteDir.mkdirs()
                    }
                    val localFilePath = "${voiceNoteDir.absolutePath}/VN_${id}.3gp"

                    // Save the Base64 data to a local file if it doesn't exist already
                    val localFile = File(localFilePath)
                    if (!localFile.exists() && voiceNoteBase64.isNotEmpty()) {
                        voiceRecorder.saveBase64ToFile(voiceNoteBase64, localFilePath)
                    }

                    localFilePath
                } else {
                    ""
                }

                Message(
                    id = id,
                    chatId = chatId,
                    senderId = senderId,
                    content = content,
                    timestamp = timestamp,
                    readStatus = readStatusMap,
                    messageType = messageType,
                    voiceNoteDuration = voiceNoteDuration,
                    voiceNoteLocalPath = localPath,
                    voiceNoteBase64 = voiceNoteBase64
                )
            } else {
                Message(
                    id = id,
                    chatId = chatId,
                    senderId = senderId,
                    content = content,
                    timestamp = timestamp,
                    readStatus = readStatusMap,
                    messageType = messageType
                )
            }
        } catch (e: Exception) {
            Log.e("ChatRoomActivity", "Error creating message from map: ${e.message}")
            return null
        }
    }
    //endregion

    //region Firebase Integration

    private fun saveMessageToFirebase(messageData: Message, messageId: String) {
        val messageMap = if (messageData.messageType == MessageType.VOICE_NOTE) {
            hashMapOf(
                "id" to messageData.id,
                "chatId" to messageData.chatId,
                "senderId" to messageData.senderId,
                "content" to messageData.content,
                "timestamp" to messageData.timestamp,
                "readStatus" to messageData.readStatus,
                "messageType" to messageData.messageType.toString(),
                "voiceNoteDuration" to messageData.voiceNoteDuration,
                "voiceNoteLocalPath" to "", // Don't save local path to Firebase
                "voiceNoteBase64" to messageData.voiceNoteBase64 // Save the Base64 data instead
            )
        } else {
            hashMapOf(
                "id" to messageData.id,
                "chatId" to messageData.chatId,
                "senderId" to messageData.senderId,
                "content" to messageData.content,
                "timestamp" to messageData.timestamp,
                "readStatus" to messageData.readStatus,
                "messageType" to messageData.messageType.toString()
            )
        }

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
                Toast.makeText(
                    this,
                    "Failed to send message. Check your connection.",
                    Toast.LENGTH_SHORT
                ).show()
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

    private fun removeRealtimeMessageListener() {
        if (resources.getBoolean(R.bool.firebaseOn)) {
            messagesListener?.let {
                database.child("messages").child(chatId).removeEventListener(it)
            }
        }
    }
    //endregion

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
                put("messageType", message.messageType.toString())

                if (message.messageType == MessageType.VOICE_NOTE) {
                    put("voiceNoteDuration", message.voiceNoteDuration)
                    put("voiceNoteLocalPath", message.voiceNoteLocalPath)
                    put("voiceNoteBase64", message.voiceNoteBase64)
                }

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

            // Determine message type
            val messageTypeStr = messageObject.optString("messageType", MessageType.TEXT.toString())
            val messageType = try {
                MessageType.valueOf(messageTypeStr)
            } catch (e: Exception) {
                MessageType.TEXT
            }

            val message = if (messageType == MessageType.VOICE_NOTE) {
                Message(
                    id = messageObject.getString("id"),
                    chatId = messageObject.getString("chatId"),
                    senderId = messageObject.getString("senderId"),
                    content = messageObject.getString("content"),
                    timestamp = messageObject.getLong("timestamp"),
                    readStatus = readStatus,
                    messageType = messageType,
                    voiceNoteDuration = messageObject.optInt("voiceNoteDuration", 0),
                    voiceNoteLocalPath = messageObject.optString("voiceNoteLocalPath", ""),
                    voiceNoteBase64 = messageObject.optString("voiceNoteBase64", "")
                )
            } else {
                Message(
                    id = messageObject.getString("id"),
                    chatId = messageObject.getString("chatId"),
                    senderId = messageObject.getString("senderId"),
                    content = messageObject.getString("content"),
                    timestamp = messageObject.getLong("timestamp"),
                    readStatus = readStatus,
                    messageType = messageType
                )
            }

            messageList.add(message)
        }

        // Always sort messages by timestamp after loading
        sortMessagesByTimestamp()

        Log.d("ChatRoomActivity", "Loaded ${messageList.size} messages for chat $chatId")
        messageAdapter.notifyDataSetChanged()
        if (messageList.isNotEmpty()) {
            messagesRecyclerView.scrollToPosition(messageList.size - 1)
        }
    }

    private fun sortMessagesByTimestamp() {
        messageList.sortBy { it.timestamp }
    }

    private fun parseReadStatus(messageObject: JSONObject): HashMap<String, Boolean> {
        val readStatusMap = HashMap<String, Boolean>()

        // Check if readStatus field exists in the JSON
        if (messageObject.has("readStatus") && !messageObject.isNull("readStatus")) {
            val readStatusObject = messageObject.getJSONObject("readStatus")

            // Get all keys (user IDs) from the readStatus object
            val keys = readStatusObject.keys()

            // Iterate through keys and add to HashMap
            while (keys.hasNext()) {
                val userId = keys.next()
                val isRead = readStatusObject.getBoolean(userId)
                readStatusMap[userId] = isRead
            }
        }

        return readStatusMap
    }
    //endregion

    //region Message Selection Mode
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

        // Store chatId before filtering (in case we delete all messages)
        val currentChatId = if (messageList.isNotEmpty()) messageList[0].chatId else chatId

        // Create a new list with unselected messages
        val updatedList = messageList.filter { !selectedMessageIds.contains(it.id) }.toMutableList()

        // Delete from Firebase if enabled
        if (resources.getBoolean(R.bool.firebaseOn)) {
            for (messageId in selectedMessageIds) {
                database.child("messages").child(currentChatId).child(messageId).removeValue()
            }
        }

        // Update local list
        messageList.clear()
        messageList.addAll(updatedList)

        // Update the last message and timestamp in Firebase
        val updates = HashMap<String, Any>()

        if (messageList.isNotEmpty()) {
            // If there are still messages, update with the last one
            updates["timestamp"] = messageList.last().timestamp
            updates["lastMessage"] = messageList.last().content
        } else {
            updates["timestamp"] = ServerValue.TIMESTAMP
            updates["lastMessage"] = "Chat Created..."
        }

        // Apply the updates with explicit error handling
        database.child("chats").child(currentChatId).updateChildren(updates)
            .addOnSuccessListener {
                Log.d("ChatActivity", "Successfully updated chat metadata")
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Failed to update chat metadata", e)
            }

        // Save to local storage
        saveMessagesToLocalStorage()

        // Exit selection mode and update UI
        Toast.makeText(this, "${selectedMessageIds.size} message(s) deleted", Toast.LENGTH_SHORT).show()
        exitSelectionMode()
    }

    private fun handleMessageLongClick(message: Message) {
        if (!isInSelectionMode) {
            enterSelectionMode()
        }
        toggleMessageSelection(message)
    }

    internal fun isMessageSelected(messageId: String): Boolean {
        return selectedMessageIds.contains(messageId)
    }
    //endregion

    //region Voice Recording
    private fun setupVoiceRecordingButton() {
        val cancelThreshold = 150 // in pixels, adjust as needed
        var startX = 0f
        var hasCancelled = false
        val messageSound = MediaPlayer.create(this, R.raw.voice_note_start)

        recordButton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (checkRecordPermission()) {
                        startX = event.rawX
                        hasCancelled = false
                        startRecording()
                        messageSound.start()
                    } else {
                        requestRecordPermission()
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val distance = startX - event.rawX
                    if (!hasCancelled && distance > cancelThreshold) {
                        hasCancelled = true
                        cancelRecording()
                        Toast.makeText(this, "Recording cancelled", Toast.LENGTH_SHORT).show()
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isRecording && !hasCancelled) {
                        stopRecording()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun startRecording() {
        isRecording = true
        val voiceNotePath = voiceRecorder.startRecording()

        val recordingLayout = findViewById<LinearLayout>(R.id.recordingLayout)
        val messageInputLayout = findViewById<LinearLayout>(R.id.messageInputLayout)
        val recordTimerText = findViewById<TextView>(R.id.recordTimerText)

        recordingLayout?.visibility = View.VISIBLE
        messageInputLayout?.visibility = View.GONE

        // Start timer
        secondsElapsed = 0
        timerHandler = Handler(Looper.getMainLooper())
        timerRunnable = object : Runnable {
            override fun run() {
                val minutes = secondsElapsed / 60
                val seconds = secondsElapsed % 60
                recordTimerText?.text = String.format("%02d:%02d", minutes, seconds)
                secondsElapsed++
                timerHandler?.postDelayed(this, 1000)
            }
        }
        timerHandler?.post(timerRunnable!!)
    }

    private fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        val (filePath, duration) = voiceRecorder.stopRecording()

        // Hide recording UI and show input UI with null checks
        val recordingLayout = findViewById<LinearLayout>(R.id.recordingLayout)
        val messageInputLayout = findViewById<LinearLayout>(R.id.messageInputLayout)

        recordingLayout?.visibility = View.GONE
        messageInputLayout?.visibility = View.VISIBLE
        timerHandler?.removeCallbacks(timerRunnable!!)


        // Send voice note message
        if (duration > 1) { // Only send if recording is longer than 1 second
            sendVoiceNote(filePath, duration)
        } else {
            Toast.makeText(this, "Recording too short", Toast.LENGTH_SHORT).show()
            // Delete the short recording file
            val file = File(filePath)
            if (file.exists()) file.delete()
        }
    }

    private fun cancelRecording() {
        if (isRecording) {
            isRecording = false
            voiceRecorder.cancelRecording()

            // Hide recording UI with null checks
            val recordingLayout = findViewById<LinearLayout>(R.id.recordingLayout)
            val messageInputLayout = findViewById<LinearLayout>(R.id.messageInputLayout)

            recordingLayout?.visibility = View.GONE
            messageInputLayout?.visibility = View.VISIBLE
            timerHandler?.removeCallbacks(timerRunnable!!)

        }
    }

    private fun sendVoiceNote(filePath: String, duration: Int) {
        val messageId = generateMessageId()

        // Encode the voice note file to Base64 string
        val base64VoiceNote = voiceRecorder.encodeFileToBase64(filePath)

        // Create readStatus map
        var map: HashMap<String, Boolean> = HashMap()
        for (par in chat.participantIds.keys) {
            map[par] = false
        }

        val message = Message(
            id = messageId,
            chatId = chatId,
            senderId = currentUserId,
            content = "Voice Note", // Content is just a placeholder for voice notes
            timestamp = System.currentTimeMillis(),
            readStatus = map,
            messageType = MessageType.VOICE_NOTE,
            voiceNoteDuration = duration,
            voiceNoteLocalPath = filePath,
            voiceNoteBase64 = base64VoiceNote // Store the Base64 data
        )

        val timestampUpdate = hashMapOf<String, Any>("timestamp" to message.timestamp)
        val lastMessageUpdate = hashMapOf<String, Any>("lastMessage" to "Voice Note")

        // Update Firebase chat info if enabled
        if (resources.getBoolean(R.bool.firebaseOn)) {
            database.child("chats").child(message.chatId).updateChildren(timestampUpdate)
            database.child("chats").child(message.chatId).updateChildren(lastMessageUpdate)
        }

        // Add message to local list
        messageList.add(message)

        // Sort messages after adding new one
        sortMessagesByTimestamp()

        // Update UI
        messageAdapter.notifyDataSetChanged()
        messagesRecyclerView.smoothScrollToPosition(messageList.size - 1)

        // Save locally
        saveMessagesToLocalStorage()

        // Save to Firebase if enabled
        if (resources.getBoolean(R.bool.firebaseOn)) {
            saveMessageToFirebase(message, messageId)
        }
    }

    private fun checkRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRecordPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            RECORD_AUDIO_PERMISSION_CODE
        )
    }
    //endregion

    //region Image Handling
    private fun setupImagePickerLauncher() {
        imagePickerLauncher =
            registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == RESULT_OK) {
                    val data = result.data
                    if (data != null && data.data != null) {
                        // Get the selected image URI
                        selectedImageUri = data.data

                        // Show image preview
                        showImagePreview(selectedImageUri!!)
                    }
                }
            }
    }

    private fun pickImage() {
        ImagePicker.with(this)
            .compress(1024)         // Compress image size
            .maxResultSize(1080, 1080) // Maximum result size
            .createIntent { intent: Intent ->
                imagePickerLauncher.launch(intent)
            }
    }

    private fun showImagePreview(imageUri: Uri) {
        try {
            // Load the image into the preview
            previewImageView.setImageURI(imageUri)

            // Hide the normal input layout and show the preview layout
            inputLayout.visibility = View.GONE
            imagePreviewLayout.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e("ChatRoomActivity", "Error showing image preview: ${e.message}")
            Toast.makeText(this, "Failed to preview image", Toast.LENGTH_SHORT).show()
        }
    }
    private fun hideImagePreview() {
        // Clear the selected image
        selectedImageUri = null

        // Hide preview layout and show the normal input layout
        imagePreviewLayout.visibility = View.GONE
        inputLayout.visibility = View.VISIBLE
    }

    private fun uploadAndSendImage(imageUri: Uri) {


        // Hide the preview
        imagePreviewLayout.visibility = View.GONE
        inputLayout.visibility = View.VISIBLE

        // Upload to ImgBB
        isImageUploading = true
        ImageUploadService.uploadImageToImgbb(
            this,
            imageUri,
            null, // You could add a progress bar specifically for image uploads
            object : ImageUploadService.ImageUploadCallback {
                override fun onUploadSuccess(imageUrl: String) {
                    // Send the image URL as a message
                    sendImageMessage(imageUrl)
                    isImageUploading = false
                    selectedImageUri = null
                }

                override fun onUploadFailure(errorMessage: String) {
                    Toast.makeText(
                        this@ChatRoomActivity,
                        "Failed to upload image: $errorMessage",
                        Toast.LENGTH_SHORT
                    ).show()
                    isImageUploading = false
                    selectedImageUri = null
                }

                override fun onUploadProgress(isUploading: Boolean) {
                    // You could update UI based on upload state
                }
            }
        )
    }

    private fun sendImageMessage(imageUrl: String) {
        val messageId = generateMessageId()

        var map: HashMap<String, Boolean> = HashMap()
        for (par in chat.participantIds.keys) {
            map[par] = false
        }

        val message = Message(
            id = messageId,
            chatId = chatId,
            senderId = currentUserId,
            content = imageUrl,
            timestamp = System.currentTimeMillis(),
            readStatus = map,
            messageType = MessageType.IMAGE
        )

        val timestampUpdate = hashMapOf<String, Any>("timestamp" to message.timestamp)
        val lastMessageUpdate = hashMapOf<String, Any>("lastMessage" to "📷 Image")

        database.child("chats").child(message.chatId).updateChildren(timestampUpdate)
        database.child("chats").child(message.chatId).updateChildren(lastMessageUpdate)

        // Add message to local list
        messageList.add(message)

        // Sort messages after adding new one
        sortMessagesByTimestamp()

        // Update UI
        messageAdapter.notifyDataSetChanged()
        messagesRecyclerView.smoothScrollToPosition(messageList.size - 1)

        // Save locally immediately
        saveMessagesToLocalStorage()

        // Send to Firebase if enabled
        if (resources.getBoolean(R.bool.firebaseOn)) {
            saveMessageToFirebase(message, messageId)
        }
    }
    //endregion

    //region Utility Methods
    private fun Int.dpToPx(): Int {
        val density = resources.displayMetrics.density
        return (this * density).toInt()
    }
    //endregion
}