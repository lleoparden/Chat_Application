package com.example.chat_application

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AddNewChatActivity : AppCompatActivity(), UserAdapter.OnUserClickListener {
    private val TAG = "AddNewChatActivity"

    // UI Components
    private lateinit var searchEditText: TextInputEditText
    private lateinit var usersRecyclerView: RecyclerView
    private lateinit var emptyResultsTextView: androidx.appcompat.widget.AppCompatTextView
    private lateinit var progressIndicator: com.google.android.material.progressindicator.CircularProgressIndicator
    private lateinit var newGroupButton: FloatingActionButton

    // Data Components
    private lateinit var userAdapter: UserAdapter
    private val usersList = mutableListOf<UserData>()

    // Track users that already have chats - we'll use this set instead of modifying UserData
    private val existingChatUserIds = mutableSetOf<String>()

    // Firebase Components
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: Starting activity")
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.add_new_chat)

        // Initialize Firebase
        Log.d(TAG, "onCreate: Initializing Firebase components")
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initViews()
        setupUI()
        // Load existing chats to know which users already have conversations
        loadExistingChats()
        showEmptyState("Enter a phone number to search for users")
        Log.i(TAG, "onCreate: Activity setup complete")
    }

    private fun initViews() {
        // Same as your original code
        Log.d(TAG, "initViews: Finding view references")
        try {
            searchEditText = findViewById(R.id.searchEditText)
            usersRecyclerView = findViewById(R.id.usersRecyclerView)
            emptyResultsTextView = findViewById(R.id.emptyResultsTextView)
            progressIndicator = findViewById(R.id.progressIndicator)
            newGroupButton = findViewById(R.id.newGroup)

            val toolbar = findViewById<Toolbar>(R.id.toolbar)
            toolbar.setNavigationOnClickListener {
                Log.d(TAG, "Back navigation clicked")
                navigateToMainActivity()
            }
            Log.d(TAG, "initViews: All views initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "initViews: Error initializing views", e)
        }
    }

    private fun setupUI() {
        Log.d(TAG, "setupUI: Configuring UI components")
        try {
            // Setup RecyclerView with our customized adapter that uses existingChatUserIds
            usersRecyclerView.layoutManager = LinearLayoutManager(this)
            userAdapter = UserAdapter(usersList, existingChatUserIds, this)
            usersRecyclerView.adapter = userAdapter
            Log.d(TAG, "setupUI: RecyclerView configured")

            searchEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val query = s.toString().trim()
                    Log.d(TAG, "Search query changed: '$query'")

                    if (query.isEmpty()) {
                        // Clear results if search is empty
                        Log.d(TAG, "Query empty, clearing results")
                        usersList.clear()
                        userAdapter.notifyDataSetChanged()
                        showEmptyState("Enter a phone number to search for users")
                    } else {
                        // Search with any input, regardless of length
                        Log.d(TAG, "Initiating search for: '$query'")
                        searchUsersByPhone(query)
                    }
                }
            })
            Log.d(TAG, "setupUI: Search functionality configured")
            Log.i(TAG, "setupUI: All UI components configured successfully")
        } catch (e: Exception) {
            Log.e(TAG, "setupUI: Error configuring UI components", e)
        }
    }


    private fun loadExistingChats() {
        Log.d(TAG, "loadExistingChats: Loading existing chats")
        val currentUserId = UserSettings.userId ?: return

        progressIndicator.visibility = View.VISIBLE

        // Query chats where the current user is a participant
        firestore.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .get()
            .addOnSuccessListener { chatDocuments ->
                Log.d(TAG, "loadExistingChats: Found ${chatDocuments.size()} existing chats")

                for (chatDoc in chatDocuments) {
                    try {
                        // Get the participant list
                        val participants = chatDoc.get("participants") as? List<*> ?: continue

                        // Find the other user ID (not the current user)
                        for (participantId in participants) {
                            if (participantId != currentUserId) {
                                existingChatUserIds.add(participantId.toString())
                                Log.d(TAG, "Added existing chat user: $participantId")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing chat document: ${chatDoc.id}", e)
                    }
                }
                progressIndicator.visibility = View.GONE

                // Refresh adapter if there are already users in the list
                if (usersList.isNotEmpty()) {
                    userAdapter.notifyDataSetChanged()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading existing chats", e)
                progressIndicator.visibility = View.GONE
            }
    }

    private fun searchUsersByPhone(phoneQuery: String) {
        Log.i(TAG, "searchUsersByPhone: Searching for users with phone: '$phoneQuery'")
        try {
            // Show loading indicator
            progressIndicator.visibility = View.VISIBLE
            emptyResultsTextView.visibility = View.GONE

            // Clear any existing users
            usersList.clear()

            // Get current user ID
            val currentUserId = UserSettings.userId
            if (currentUserId == null) {
                Log.e(TAG, "searchUsersByPhone: Current user ID is null")
                return
            }
            Log.d(TAG, "searchUsersByPhone: Current user ID: $currentUserId")

            // Try multiple phone number formats to increase chances of finding a match
            val phoneQueries = listOf(
                phoneQuery,                     // Original input
                "+$phoneQuery",                 // With + prefix
                "+2$phoneQuery",                // With country code +2
                "+20$phoneQuery",
                phoneQuery.replace("+", "")     // Without + if it exists
            )

            var queriesCompleted = 0
            var totalUsersFound = 0

            for (formattedQuery in phoneQueries) {
                firestore.collection("users")
                    .whereGreaterThanOrEqualTo("phoneNumber", formattedQuery)
                    .whereLessThanOrEqualTo("phoneNumber", formattedQuery + "\uf8ff")
                    .limit(10)
                    .get()
                    .addOnSuccessListener { documents ->
                        queriesCompleted++
                        Log.d(TAG, "Query for '$formattedQuery' found ${documents.size()} documents")

                        if (!documents.isEmpty) {
                            for (document in documents) {
                                try {
                                    // Create UserData object using the existing class definition
                                    val userData = UserData(
                                        uid = document.getString("uid") ?: "",
                                        displayName = document.getString("displayName") ?: "",
                                        phoneNumber = document.getString("phoneNumber") ?: "",
                                        password = document.getString("password") ?: "",
                                        userDescription = document.getString("userDescription") ?: "",
                                        userStatus = document.getString("userStatus") ?: "",
                                        online = when (val onlineValue = document.get("online")) {
                                            is Boolean -> onlineValue
                                            is String -> onlineValue.equals("true", ignoreCase = true)
                                            else -> false
                                        },
                                        lastSeen = document.getString("lastSeen") ?: "",
                                        profilePictureUrl = document.getString("profilePictureUrl") ?: ""
                                    )

                                    // Add users that are not the current user and not already in the list
                                    if (userData.uid != currentUserId &&
                                        !usersList.any { it.uid == userData.uid }) {
                                        usersList.add(userData)
                                        totalUsersFound++

                                        val hasChat = existingChatUserIds.contains(userData.uid)
                                        Log.d(TAG, "Added user: ${userData.displayName}, has chat: $hasChat")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing user document: ${document.id}", e)
                                }
                            }

                            // Update adapter with newly found users
                            userAdapter.notifyDataSetChanged()
                        }

                        // Check if all queries are complete
                        if (queriesCompleted == phoneQueries.size) {
                            progressIndicator.visibility = View.GONE

                            if (totalUsersFound == 0) {
                                showEmptyState("No users found with this phone number")
                            } else {
                                emptyResultsTextView.visibility = View.GONE
                            }
                        }
                    }
                    .addOnFailureListener { exception ->
                        queriesCompleted++
                        Log.e(TAG, "Error searching for '$formattedQuery'", exception)

                        // Check if all queries are complete
                        if (queriesCompleted == phoneQueries.size) {
                            progressIndicator.visibility = View.GONE

                            if (totalUsersFound == 0) {
                                showEmptyState("No users found with this phone number")
                            }
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchUsersByPhone: Unexpected error", e)
            progressIndicator.visibility = View.GONE
            showEmptyState("An unexpected error occurred")
        }
    }

    private fun showEmptyState(message: String) {
        Log.d(TAG, "showEmptyState: Showing empty state message: '$message'")
        emptyResultsTextView.text = message
        emptyResultsTextView.visibility = View.VISIBLE
    }

    override fun onUserClick(user: UserData) {
        Log.i(TAG, "onUserClick: User clicked: ${user.displayName} (${user.uid})")
        try {
            // Start user profile activity
            val intent = Intent(this, UserProfileActivity::class.java).apply {
                putExtra("USER_ID", user.uid)
                putExtra("came_from", "AddNewChat")
                putExtra("HAS_EXISTING_CHAT", existingChatUserIds.contains(user.uid))
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "onUserClick: Error navigating to user profile", e)
            Toast.makeText(this, "Error opening user profile", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToMainActivity() {
        Log.i(TAG, "navigateToMainActivity: Navigating back to main activity")
        try {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        } catch (e: Exception) {
            Log.e(TAG, "navigateToMainActivity: Error navigating to main activity", e)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Activity being destroyed")
        super.onDestroy()
    }
}