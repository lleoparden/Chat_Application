package com.example.chat_application

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chat_application.adapters.ChatAdapter
import com.example.chat_application.dataclasses.Chat
import com.example.chat_application.dataclasses.UserSettings
import com.example.chat_application.services.FirebaseService
import com.example.chat_application.services.LocalStorageService
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File


private const val TAG = "MainActivity"


class MainActivity : AppCompatActivity(), ChatAdapter.OnChatClickListener, ChatAdapter.OnChatLongClickListener{

    // UI Components
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var searchButton: ImageView
    private lateinit var settingsButton: ImageView
    private lateinit var newChatFab: FloatingActionButton
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var searchBar: EditText
    private lateinit var searchContainer: LinearLayout
    private var isSearchVisible = false

    // Selection mode components
    private lateinit var normalToolbarView: View
    private lateinit var selectionToolbarView: View
    private lateinit var selectionCountTextView: TextView
    private lateinit var closeSelectionButton: ImageView
    private lateinit var deleteSelectedButton: ImageView
    private var isInSelectionMode = false
    private val selectedChatIds = mutableSetOf<String>()

    // Data Components
    private lateinit var chatAdapter: ChatAdapter
    private val chatManager = ChatManager()

    private val firebaseEnabled by lazy { resources.getBoolean(R.bool.firebaseOn) }



    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.mainpage)



        initViews()
        setupUI()
        setupRecyclerView()
        // Initialize services
        LocalStorageService.initialize(this, ContentValues.TAG)

        if (firebaseEnabled) {
            FirebaseService.initialize(this,TAG,firebaseEnabled)
        }

        // Load data
        loadChats()
    }


    //region UI Setup
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

        // Initialize selection mode views
        normalToolbarView = findViewById(R.id.normalToolbarContent)
        selectionToolbarView = findViewById(R.id.selectionToolbarContent)
        selectionCountTextView = findViewById(R.id.selectionCountTextView)
        closeSelectionButton = findViewById(R.id.closeSelectionButton)
        deleteSelectedButton = findViewById(R.id.deleteSelectedButton)

        // Initially hide search bar and selection toolbar
        searchContainer.visibility = View.GONE
        selectionToolbarView.visibility = View.GONE
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

        }

        // Setup FAB
        newChatFab.setOnClickListener {
            startActivity(Intent(this, AddNewChatActivity::class.java))

        }

        // Setup bottom navigation
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_chats -> true // Already on chats page
                R.id.navigation_stories -> {
                    startActivity(Intent(this, StoryListActivity::class.java))

                    true
                }
                else -> false
            }
        }

        closeSelectionButton.setOnClickListener {
            exitSelectionMode()
        }

        deleteSelectedButton.setOnClickListener {
            deleteSelectedChats()
        }
    }

    private fun setupRecyclerView() {
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(chatManager.getAll(), this, this)
        chatRecyclerView.adapter = chatAdapter
    }

    // Update the toggleSearchBar method to handle shimmer
    private fun enterSelectionMode() {
        if (isInSelectionMode) return

        isInSelectionMode = true
        selectedChatIds.clear()

        // Update adapter FIRST
        chatAdapter.updateSelectionMode(true)

        // Then update UI
        normalToolbarView.visibility = View.GONE
        selectionToolbarView.visibility = View.VISIBLE
        newChatFab.visibility = View.GONE

        updateSelectionCount()
    }

    private fun exitSelectionMode() {
        if (!isInSelectionMode) return

        isInSelectionMode = false
        selectedChatIds.clear()

        // Restore normal toolbar
        selectionToolbarView.visibility = View.GONE
        normalToolbarView.visibility = View.VISIBLE

        chatAdapter.updateSelectionMode(false)

        // Show FAB again
        newChatFab.visibility = View.VISIBLE

        // Update adapter to reset selection visual states
        chatAdapter.updateSelectionMode(false)
        chatAdapter.clearSelections() // Make sure to clear adapter's internal state too
    }

    private fun toggleChatSelection(chat: Chat) {
        if (selectedChatIds.contains(chat.id)) {
            selectedChatIds.remove(chat.id)
        } else {
            selectedChatIds.add(chat.id)
        }

        // Update adapter with the new selection state
        chatAdapter.updateSelectedItems(selectedChatIds)


        // If no chats are selected, exit selection mode
        if (selectedChatIds.isEmpty()) {
            exitSelectionMode()
            return
        }

        updateSelectionCount()
    }

    private fun updateSelectionCount() {
        val count = selectedChatIds.size
        selectionCountTextView.text = "$count selected"
    }


    private fun deleteSelectedChats() {
        var groupExists = false
        if (selectedChatIds.isEmpty()) return

        val groupChatsToLeave = selectedChatIds.toList().mapNotNull { chatId ->
            chatManager.getChatById(chatId)?.takeIf { it.type == "group" }
        }

        // Process group chats separately
        for (chat in groupChatsToLeave) {
            selectedChatIds.remove(chat.id)
            groupExists = true
        }

        // Remove selected chats from the chat manager
        val updatedChats = chatManager.getAll().filter { !selectedChatIds.contains(it.id) }

        // Delete associated messages locally for each chat
        for (chatId in selectedChatIds) {
            // Delete messages for this chat from local storage
            File(filesDir,"messages_${chatId}.json").delete()

            // Remove from Firebase if enabled
            if (firebaseEnabled) {
                FirebaseService.removeUserFromParticipants(chatId)
            }
        }

        // Update local list
        chatManager.clear()
        chatManager.pushAll(updatedChats)

        // Save to local storage
        LocalStorageService.saveChatsToLocalStorage(chatManager)

        // Show confirmation
        if (selectedChatIds.size > 1) {
            Toast.makeText(this, "${selectedChatIds.size} chats deleted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Chat deleted", Toast.LENGTH_SHORT).show()
        }

        if (groupExists) {
            Toast.makeText(this, "Can't delete groups", Toast.LENGTH_SHORT).show()
        }

        // Exit selection mode and update UI
        exitSelectionMode()
        chatAdapter.updateData(chatManager.getAll())
    }

    // Update the toggleSearchBar method to handle shimmer
    private fun toggleSearchBar() {
        isSearchVisible = !isSearchVisible

        if (isSearchVisible) {
            searchContainer.visibility = View.VISIBLE
            searchBar.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchBar, InputMethodManager.SHOW_IMPLICIT)
        } else {
            searchContainer.visibility = View.GONE
            searchBar.text.clear()
            // Hide keyboard
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(searchBar.windowToken, 0)



            // Reset RecyclerView to show all chats
            chatAdapter.updateData(chatManager.getAll())

        }
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            // Show all chats when query is empty
            chatAdapter.updateData(chatManager.getAll())
            return
        }

        // Show shimmer effect during search


        // Use binary search to find matching chats
        val matchingChat = chatManager.findByName(query)

        // Use a handler to simulate search delay and show shimmer effect
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (matchingChat != null) {
                // Show only the exact match
                chatAdapter.updateData(listOf(matchingChat))
            } else {
                // If no exact match, try to find partial matches
                val partialMatches = chatManager.findPartialMatches(query)

                if (partialMatches.isNotEmpty()) {
                    chatAdapter.updateData(partialMatches)
                } else {
                    // Display a message for no results
                    Toast.makeText(this, "No chats found matching '$query'", Toast.LENGTH_SHORT).show()
                    // Show empty list
                    chatAdapter.updateData(emptyList())
                }
            }

            // Hide shimmer effect after search is complete

        }, 500) // 500ms delay to show the shimmer effect
    }
    //endregion

    //region Chat Management

    private fun loadChats() {


        // Load local chats first
        val localChats = LocalStorageService.loadChatsFromLocalStorageWithoutSaving()

        // Update UI with local chats immediately
        chatManager.clear()
        chatManager.pushAll(localChats)
        chatAdapter.updateData(chatManager.getAll()) // Add this line to update adapter


        FirebaseService.fetchUserDataAndUpdateDisplayNames(chatManager,chatAdapter)


        // Only attempt Firebase loading if explicitly enabled AND we're online
        if (firebaseEnabled) {
            FirebaseService.checkConnectionAndLoadChatsFromFirebase(localChats,chatAdapter,chatManager)
        }
    }


    //region Event Handling

    override fun onChatClick(chat: Chat) {

        if (isInSelectionMode) {
            toggleChatSelection(chat)
        } else {
            val intent = Intent(this, ChatRoomActivity::class.java).apply {
                putExtra("CHAT_OBJECT", chat)
            }
            if (firebaseEnabled) {
                FirebaseService.saveChatsToFirebase(chatManager)
            }
            startActivity(intent)
        }
    }


    override fun onChatLongClick(chat: Chat): Boolean {
        if (!isInSelectionMode) {
            // Enter selection mode
            enterSelectionMode()
        }

        // Toggle selection after mode is updated
        toggleChatSelection(chat)
        return true
    }
    //endregion

}
