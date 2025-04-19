package com.example.chat_application

import Chat
import ChatAdapter
import ChatManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity(), ChatAdapter.OnChatClickListener {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var searchButton: ImageView
    private lateinit var settingsButton: ImageView
    private lateinit var newChatFab: FloatingActionButton
    private lateinit var bottomNavigation: BottomNavigationView

    private lateinit var chatAdapter: ChatAdapter
    private val chatManager = ChatManager()
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var chatsReference: DatabaseReference


    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.mainpage)

        //!uncomment to clear jason file
        //File(filesDir, "chats.json").delete()

        initViews()
        setupUI()
        setupRecyclerView()
        loadChats()
    }

    private fun initViews() {
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        searchButton = findViewById(R.id.searchButton)
        settingsButton = findViewById(R.id.settingsButton)
        newChatFab = findViewById(R.id.newChatFab)
        bottomNavigation = findViewById(R.id.bottomNavigation)
    }

    private fun setupUI() {
        // Setup search button
        searchButton.setOnClickListener {
            Toast.makeText(this, "Search clicked", Toast.LENGTH_SHORT).show()
            // TODO: Implement search functionality
        }

        // Setup settings button
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }

        // Setup FAB
        newChatFab.setOnClickListener {
            Toast.makeText(this, "New chat clicked", Toast.LENGTH_SHORT).show()
            // Create a new chat and push it to the stack
            val currentTime = System.currentTimeMillis()
            val newChat = Chat(
                id = "demo" + UUID.randomUUID().toString().substring(0, 8),
                name = "New Chat",
                lastMessage = "This is a new conversation",
                timestamp = currentTime,
                unreadCount = 1
            )

            // Push to stack
            chatManager.push(newChat)

            // Save chats to local storage
            saveChatsToLocalStorage()

            // Update UI
            chatAdapter.notifyDataSetChanged()
        }

        // Setup bottom navigation
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_chats -> {
                    // Already on chats page
                    true
                }

                R.id.navigation_stories -> {
                    startActivity(Intent(this, StoryActivity::class.java))
                    finish()
                    true
                }

                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(chatManager, this)
        chatRecyclerView.adapter = chatAdapter
    }

    private fun loadChats() {
        if (resources.getBoolean(R.bool.firebaseOn)) {
            loadChatsFromFirebase()
        } else {
            loadChatsFromLocalStorage()
        }
    }

    private fun loadChatsFromLocalStorage() {
        Log.d("MainActivity", "Loading chats from local storage")
        val file = File(filesDir, "chats.json")
        val jsonString = readChatsFromFile()

        if (!file.exists() || jsonString.isEmpty()) {
            // File doesn't exist or is empty, create it with demo chats
            Log.d("MainActivity", "No chats file found, creating demo chats")
            addDemoChat()
            return
        }

        try {
            val jsonArray = JSONArray(jsonString)
            chatManager.clear()
            val tempChats = mutableListOf<Chat>()

            for (i in 0 until jsonArray.length()) {
                val chatObject = jsonArray.getJSONObject(i)
                val participantIdsJsonArray = chatObject.getJSONArray("participantIds")
                val participantIds = mutableListOf<String>()

                for (j in 0 until participantIdsJsonArray.length()) {
                    participantIds.add(participantIdsJsonArray.getString(j))
                }

                val chat = Chat(
                    id = chatObject.getString("id"),
                    name = chatObject.getString("name"),
                    lastMessage = chatObject.getString("lastMessage"),
                    timestamp = chatObject.getLong("timestamp"),
                    unreadCount = chatObject.getInt("unreadCount"),
                    participantIds = participantIds,
                    type = chatObject.getString("type")
                )

                tempChats.add(chat)
            }

            // Add all chats to the stack at once
            chatManager.pushAll(tempChats)

            // Update UI
            chatAdapter.notifyDataSetChanged()

        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading chats: ${e.message}")
            Toast.makeText(this, "Error loading chats: ${e.message}", Toast.LENGTH_SHORT).show()
            // If parsing fails, add a demo chat
            addDemoChat()
        }
    }

    private fun readChatsFromFile(): String {
        val file = File(filesDir, "chats.json")
        return if (file.exists()) {
            file.readText()
        } else {
            ""
        }
    }

    private fun addDemoChat() {
        Log.d("MainActivity", "Adding demo chats")
        chatManager.clear()
        val currentTime = System.currentTimeMillis()
        val demoChats = listOf(
            Chat(
                id = "demo1",
                name = "Demo Group",
                lastMessage = "Welcome to FireChat! This is a demo message.",
                timestamp = currentTime - 6000,
                unreadCount = 999999999
            ),
            Chat(
                id = "demo2",
                name = "John Doe",
                lastMessage = "Hey there! How are you doing?",
                timestamp = currentTime,
                unreadCount = 0
            )
        )

        // Add all demo chats to the stack
        chatManager.pushAll(demoChats)

        // Save demo chats to local storage
        saveChatsToLocalStorage()

        // Update UI
        chatAdapter.notifyDataSetChanged()

        Log.d("MainActivity", "Demo chats added: ${chatManager.size()}")
    }

    private fun saveChatsToLocalStorage() {
        val jsonArray = JSONArray()
        val allChats = chatManager.getAll()

        for (chat in allChats) {
            val chatObject = JSONObject().apply {
                put("id", chat.id)
                put("name", chat.name)
                put("lastMessage", chat.lastMessage)
                put("timestamp", chat.timestamp)
                put("unreadCount", chat.unreadCount)
                put("participantIds",chat.participantIds)
                put("type",chat.type)
            }
            jsonArray.put(chatObject)
        }
        Log.d("MainActivity", "Saving ${chatManager.size()} chats to local storage")

        val jsonString = jsonArray.toString()
        writeChatsToFile(jsonString)
    }

    private fun writeChatsToFile(jsonString: String) {
        try {
            val file = File(filesDir, "chats.json")
            file.writeText(jsonString)
            Log.d("MainActivity", "Chats saved to file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error writing to file: ${e.message}")
        }
    }

    override fun onChatClick(chat: Chat) {
        startActivity(Intent(this, ChatRoomActivity::class.java))
        finish()
    }



    private fun loadChatsFromFirebase() {
        firebaseDatabase = FirebaseDatabase.getInstance()
        chatsReference = firebaseDatabase.getReference("chats")

        // Listen for changes in the chats
        chatsReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                chatManager.clear()
                val tempChats = mutableListOf<Chat>()

                for (chatSnapshot in snapshot.children) {
                    val chat = chatSnapshot.getValue(Chat::class.java)
                    chat?.let { tempChats.add(it) }
                }

                // Add all chats to the stack at once
                chatManager.pushAll(tempChats)

                // Save chats to local storage
                saveChatsToLocalStorage()

                // Update UI
                chatAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@MainActivity,
                    "Failed to load chats: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
                // If Firebase fails, try loading from local storage as fallback
                loadChatsFromLocalStorage()
            }
        })
    }
}