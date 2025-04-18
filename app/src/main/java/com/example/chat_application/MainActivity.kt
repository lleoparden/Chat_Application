package com.example.chat_application

import Chat
import ChatAdapter
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
import java.text.SimpleDateFormat
import java.util.*

//test
class MainActivity : AppCompatActivity(), ChatAdapter.OnChatClickListener {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var searchButton: ImageView
    private lateinit var settingsButton: ImageView
    private lateinit var newChatFab: FloatingActionButton
    private lateinit var bottomNavigation: BottomNavigationView

    private lateinit var chatAdapter: ChatAdapter
    private val chatsList = mutableListOf<Chat>()
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var chatsReference: DatabaseReference

    // Control flag for Firebase access
    private val firebase_on = false

    override fun onCreate(savedInstanceState: Bundle?) {
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
//            Toast.makeText(this, "Settings clicked", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }

        // Setup FAB
        newChatFab.setOnClickListener {
            Toast.makeText(this, "New chat clicked", Toast.LENGTH_SHORT).show()
            // TODO: Implement new chat functionality
            val currentTime = System.currentTimeMillis()
            chatsList.add(
                Chat(
                    id = "demo" + UUID.randomUUID().toString().substring(0, 8),
                    name = "New Chat",
                    lastMessage = "This is a new conversation",
                    timestamp = currentTime,
                    unreadCount = 1
                )
            )

            // Sort chats by timestamp (newest first)
            chatsList.sortByDescending { it.timestamp }

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
//                    Toast.makeText(this, "Stories page not implemented yet", Toast.LENGTH_SHORT).show()
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
        chatAdapter = ChatAdapter(chatsList, this)
        chatRecyclerView.adapter = chatAdapter
    }

    private fun loadChats() {
        if (firebase_on) {
            loadChatsFromFirebase()
        } else {
            loadChatsFromLocalStorage()
        }
    }

    private fun loadChatsFromLocalStorage() {
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
            chatsList.clear()

            for (i in 0 until jsonArray.length()) {
                val chatObject = jsonArray.getJSONObject(i)
                val chat = Chat(
                    id = chatObject.getString("id"),
                    name = chatObject.getString("name"),
                    lastMessage = chatObject.getString("lastMessage"),
                    timestamp = chatObject.getLong("timestamp"),
                    unreadCount = chatObject.getInt("unreadCount")
                )
                chatsList.add(chat)
            }

            // Sort chats by timestamp (newest first)
            chatsList.sortByDescending { it.timestamp }

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
        chatsList.clear()
        val currentTime = System.currentTimeMillis()

        // Add demo chats
        chatsList.add(
            Chat(
                id = "demo1",
                name = "Demo Group",
                lastMessage = "Welcome to FireChat! This is a demo message.",
                timestamp = currentTime - 6000,
                unreadCount = 999999999
            )
        )

        chatsList.add(
            Chat(
                id = "demo2",
                name = "John Doe",
                lastMessage = "Hey there! How are you doing?",
                timestamp = currentTime,
                unreadCount = 0
            )
        )

        // Save demo chats to local storage
        saveChatsToLocalStorage()

        // Update UI
        chatAdapter.notifyDataSetChanged()

        Log.d("MainActivity", "Demo chats added: ${chatsList.size}")
    }

    private fun saveChatsToLocalStorage() {
        val jsonArray = JSONArray()

        for (chat in chatsList) {
            val chatObject = JSONObject().apply {
                put("id", chat.id)
                put("name", chat.name)
                put("lastMessage", chat.lastMessage)
                put("timestamp", chat.timestamp)
                put("unreadCount", chat.unreadCount)
            }
            jsonArray.put(chatObject)
        }

        val jsonString = jsonArray.toString()

        try {
            val file = File(filesDir, "chats.json")
            file.writeText(jsonString)
            Log.d("MainActivity", "Chats saved to file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving chats: ${e.message}")
            Toast.makeText(this, "Error saving chats: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
//        Toast.makeText(this, "Opening chat with ${chat.name}", Toast.LENGTH_SHORT).show()
    }








    private fun loadChatsFromFirebase() {

        firebaseDatabase = FirebaseDatabase.getInstance()
        chatsReference = firebaseDatabase.getReference("chats")

        // Listen for changes in the chats
        chatsReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                chatsList.clear()

                for (chatSnapshot in snapshot.children) {
                    val chat = chatSnapshot.getValue(Chat::class.java)
                    chat?.let { chatsList.add(it) }
                }

                // Sort chats by timestamp (newest first)
                chatsList.sortByDescending { it.timestamp }

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