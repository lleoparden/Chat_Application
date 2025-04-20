package com.example.chat_application

import Chat
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
        addSampleMessages()
    }



    fun sendMessage(textmessage:String){

        val messageId =UUID.randomUUID().toString()

        val message = Message(
            id = messageId,
            chatId = chatId,
            senderId =currentUserId,
            content = textmessage,
            timestamp = System.currentTimeMillis(),
            readStatus = mapOf()
        )

        messageList.add(message)
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

}