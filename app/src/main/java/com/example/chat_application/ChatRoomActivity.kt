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
import com.example.chat_application.AuthActivity.UserData
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID


class ChatRoomActivity : AppCompatActivity() {

    private lateinit var sendBtn : Button
    private lateinit var backBtn :ImageButton
    private lateinit var inputText : EditText
    private lateinit var profilePic :ImageButton
    private lateinit var menuBtn : ImageButton
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private val messageList = mutableListOf<Message>()
    private val currentUserId = "user_123"
    private lateinit var  chatId : String

    // Firestore instance
    private lateinit var db: FirebaseFirestore

    // JSON file for local storage
    private lateinit var localchat: File

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.chatroom)


        // Set window flags
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)


        // Optional: Add a keyboard listener to handle animation or custom behavior
        val rootView: View = findViewById(android.R.id.content)
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            private val r = Rect()
            private var lastVisibleHeight = 0

            override fun onGlobalLayout() {
                // Calculate visible height
                rootView.getWindowVisibleDisplayFrame(r)
                val visibleHeight = r.height()


                // Detect keyboard visibility changes
                if (lastVisibleHeight != 0 && lastVisibleHeight != visibleHeight) {
                    val isKeyboardVisible = lastVisibleHeight > visibleHeight
                    // You can add custom animations or behavior when keyboard appears/disappears
                }

                lastVisibleHeight = visibleHeight
            }
        })
        db = FirebaseFirestore.getInstance()

        // Retrieve the chat object from intent
        val chat = intent.getParcelableExtra<Chat>("CHAT_OBJECT")
            ?: Chat(id = "", name = "Chat", lastMessage = "", timestamp = 0, unreadCount = 0)

        chatId = chat.id

        // Set the contact name in the top bar
        val contactNameTextView = findViewById<TextView>(R.id.contactNameTextView)
        contactNameTextView.text = chat.name

        sendBtn = findViewById(R.id.sendButton)
        backBtn = findViewById(R.id.backButton)
        profilePic = findViewById(R.id.accountpic)
        inputText = findViewById(R.id.messageInput)
        menuBtn = findViewById(R.id.menuButton)
        messagesRecyclerView=findViewById(R.id.messagesRecyclerView)
        messageAdapter=MessageAdapter(currentUserId,messageList)
        val LayoutManager = LinearLayoutManager(this)
        messagesRecyclerView.layoutManager = LayoutManager
        messagesRecyclerView.adapter = messageAdapter


        backBtn.setOnClickListener{
            startActivity(Intent(this,MainActivity::class.java))
            finish()
        }


        sendBtn.setOnClickListener{
            val textMsg =inputText.text.toString().trim()
            if(textMsg.isNotEmpty()){
                sendMessage(textMsg)
                inputText.text.clear()
            }

        }

        loadMessagesFromLocalStorage()
    }



    fun sendMessage(textmessage:String){

        val messageId = generateMessageId()

        val message = Message(
            id = messageId,
            chatId = chatId,
            senderId =currentUserId,
            content = textmessage,
            timestamp = System.currentTimeMillis(),
            readStatus = mapOf()
        )

        messageList.add(message)
        saveMessage(messageId)
        messageAdapter.notifyItemInserted(messageList.size-1)
        messagesRecyclerView.smoothScrollToPosition(messageList.size-1)

    }

    private fun addSampleMessages() {
        // Add some sample messages for testing the UI
        val otherUserId = "user_789"

        val messages = listOf(
            Message(
                id = "msg1",
                chatId = chatId,
                senderId = otherUserId,
                content = "Hello there!",
                timestamp = System.currentTimeMillis() - 3600000,
                readStatus = mapOf(otherUserId to true, currentUserId to true)
            ),
            Message(
                id = "msg2",
                chatId = chatId,
                senderId = currentUserId,
                content = "Hi! How are you?",
                timestamp = System.currentTimeMillis() - 3500000,
                readStatus = mapOf(currentUserId to true, otherUserId to true)
            ),
            Message(
                id = "msg3",
                chatId = chatId,
                senderId = otherUserId,
                content = "I'm doing well, thanks for asking!",
                timestamp = System.currentTimeMillis() - 3400000,
                readStatus = mapOf(otherUserId to true, currentUserId to true)
            ),
            Message(
                id = "msg4",
                chatId = chatId,
                senderId = currentUserId,
                content = "That's great to hear!",
                timestamp = System.currentTimeMillis() - 3300000,
                readStatus = mapOf(currentUserId to true, otherUserId to false)
            )
        )

        messageList.addAll(messages)
        messageAdapter.notifyDataSetChanged()
        messagesRecyclerView.scrollToPosition(messageList.size - 1)
    }



    private fun saveMessage(messageId: String) {

        // Create a localMessageData object for local storage
        val messageData = messageList[messageList.size-1]



        // Save to local storage first
        saveMessagesToLocalStorage()


        // Then attempt to save to Firebase
        if (resources.getBoolean(R.bool.firebaseOn)) {
            saveMessageToFirebase(messageData, messageId)
        }

    }

    private fun generateMessageId(): String {
        return db.collection("users").document().id
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

        db.collection("messages").document(messageId)
            .set(messageMap)
            .addOnSuccessListener {
                //save locally
            }
            .addOnFailureListener { e ->
                //add to queue and save locally
            }
    }


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
            Log.d("ChatRoomActivity", "No messages file found, creating demo messages")
            addSampleMessages()
            return
        }

        try {
            val jsonArray = JSONArray(jsonString)
            messageList.clear()
            val tempMessages = mutableListOf<Message>()

            for (i in 0 until jsonArray.length()) {
                val messageObject = jsonArray.getJSONObject(i)
                val readStatus = mutableMapOf<String, Boolean>()

                try {
                    // Try to parse as JSONObject first (preferred format)
                    val readStatusJsonObject = messageObject.getJSONObject("readStatus")
                    val keys = readStatusJsonObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        readStatus[key] = readStatusJsonObject.getBoolean(key)
                    }
                } catch (e: Exception) {
                    // If that fails, try to parse as JSONArray
                    try {
                        val readStatusArray = messageObject.getJSONArray("readStatus")
                        for (j in 0 until readStatusArray.length()) {
                            val entry = readStatusArray.getString(j)
                            // Parse entries like "user_789=true"
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

            messageList.addAll(tempMessages)
            messageAdapter.notifyDataSetChanged()
            messagesRecyclerView.scrollToPosition(messageList.size - 1)

        } catch (e: Exception) {
            Log.e("ChatRoomActivity", "Error loading messages: ${e.message}")
            Toast.makeText(this, "Error loading messages: ${e.message}", Toast.LENGTH_SHORT).show()
            addSampleMessages()
        }
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