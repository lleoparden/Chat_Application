package com.example.chat_application

import Chat
import ChatAdapter
import ChatManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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
    private lateinit var searchBar: EditText
    private lateinit var searchContainer: LinearLayout
    private var isSearchVisible = false

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
        searchBar = findViewById(R.id.searchBar)
        searchContainer = findViewById(R.id.searchContainer)

        // Initially hide search bar
        searchContainer.visibility = View.GONE

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }
    }

    private fun setupUI() {
        // Setup search button
        searchButton.setOnClickListener {
            toggleSearchBar()
        }

        // Setup search bar functionality
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                performSearch(s.toString())
            }
        })

        // Setup settings button
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }

        // Setup FAB
        newChatFab.setOnClickListener {
            startActivity(Intent(this, AddNewChatActivity::class.java))
            finish()
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
        // Always load local chats first
        loadChatsFromLocalStorage()

        // Then add Firebase chats if enabled (but don't clear existing chats)
        if (resources.getBoolean(R.bool.firebaseOn)) {
            mergeChatsFromFirebase()
        }
    }

    private fun loadChatsFromLocalStorage() {
        Log.d("MainActivity", "Loading chats from local storage")
        val file = File(filesDir, "chats.json")
        val jsonString = readChatsFromFile()

        if (!file.exists() || jsonString.isEmpty()) {
            // File doesn't exist or is empty, create it with demo chats
            Log.d("MainActivity", "No chats file found, creating demo chats")
            //addDemoChat()
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
            //addDemoChat()
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

//    private fun addDemoChat() {
//        Log.d("MainActivity", "Adding demo chats")
//        chatManager.clear()
//        val currentTime = System.currentTimeMillis()
//        val demoChats = listOf(
//            Chat(
//                id = "demo1",
//                name = "Demo Group",
//                lastMessage = "Welcome to FireChat! This is a demo message.",
//                timestamp = currentTime - 6000,
//                unreadCount =9,
//                participantIds = mutableListOf("demo_user_1", "demo_user_2"),
//                type = "group"
//            ),
//            Chat(
//                id = "demo2",
//                name = "John Doe",
//                lastMessage = "Hey there! How are you doing?",
//                timestamp = currentTime,
//                unreadCount = 0,
//                participantIds = mutableListOf("demo_user_1"),
//                type = "direct"
//            )
//        )
//
//        // Add all demo chats to the stack
//        chatManager.pushAll(demoChats)
//
//        // Save demo chats to local storage
//        saveChatsToLocalStorage()
//
//        // Update UI
//        chatAdapter.notifyDataSetChanged()
//
//        Log.d("MainActivity", "Demo chats added: ${chatManager.size()}")
//    }

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

                // Create a JSONArray for participantIds
                val participantIdsArray = JSONArray()
                for (participantId in chat.participantIds) {
                    participantIdsArray.put(participantId)
                }
                put("participantIds", participantIdsArray)

                put("type", chat.type ?: "direct")
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
        val intent = Intent(this, ChatRoomActivity::class.java).apply {
            putExtra("CHAT_OBJECT", chat)
        }
        startActivity(intent)
        finish()
    }



    private fun mergeChatsFromFirebase() {
        firebaseDatabase = FirebaseDatabase.getInstance()
        chatsReference = firebaseDatabase.getReference("chats")

        // Listen for changes in the chats
        chatsReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val existingChatIds = chatManager.getAll().map { it.id }.toSet()
                val firebaseChats = mutableListOf<Chat>()

                for (chatSnapshot in snapshot.children) {
                    val chat = chatSnapshot.getValue(Chat::class.java)
                    // Only add chats that don't already exist locally
                    if (chat != null && chat.id !in existingChatIds) {
                        firebaseChats.add(chat)
                    }
                }

                // Add new Firebase chats to the stack without clearing existing ones
                if (firebaseChats.isNotEmpty()) {
                    chatManager.pushAll(firebaseChats)

                    // Save merged chats to local storage
                    saveChatsToLocalStorage()

                    // Update UI
                    chatAdapter.notifyDataSetChanged()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@MainActivity,
                    "Failed to load chats from Firebase: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }


    private fun toggleSearchBar() {
        isSearchVisible = !isSearchVisible

        // Make sure we're showing/hiding the correct container
        if (isSearchVisible) {
            searchContainer.visibility = View.VISIBLE
            searchBar.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchBar, InputMethodManager.SHOW_IMPLICIT)

            // Log for debugging
            Log.d("MainActivity", "Search bar should be visible now")
        } else {
            searchContainer.visibility = View.GONE
            searchBar.text.clear()
            // Hide keyboard
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(searchBar.windowToken, 0)

            // Reset RecyclerView to show all chats
            chatAdapter = ChatAdapter(chatManager, this)
            chatRecyclerView.adapter = chatAdapter

            // Log for debugging
            Log.d("MainActivity", "Search bar should be hidden now")
        }
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            // Show all chats when query is empty
            chatAdapter = ChatAdapter(chatManager, this)
            chatRecyclerView.adapter = chatAdapter
            return
        }

        // Use binary search to find matching chats
        val matchingChat = chatManager.findByName(query)

        if (matchingChat != null) {
            // Show only the exact match
            val singleChatManager = ChatManager()
            singleChatManager.push(matchingChat)
            chatAdapter = ChatAdapter(singleChatManager, this)
            chatRecyclerView.adapter = chatAdapter
        } else {
            // If no exact match, try to find partial matches
            val partialMatches = chatManager.findPartialMatches(query)

            if (partialMatches.isNotEmpty()) {
                val tempChatManager = ChatManager()
                tempChatManager.pushAll(partialMatches)
                chatAdapter = ChatAdapter(tempChatManager, this)
                chatRecyclerView.adapter = chatAdapter
            } else {
                // Display a message for no results
                Toast.makeText(this, "No chats found matching '$query'", Toast.LENGTH_SHORT).show()
                // Show empty list
                val emptyChatManager = ChatManager()
                chatAdapter = ChatAdapter(emptyChatManager, this)
                chatRecyclerView.adapter = chatAdapter
            }
        }
    }

}