package com.example.chat_application

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chat_application.adapters.UserAdapter
import com.example.chat_application.dataclasses.UserData
import com.example.chat_application.dataclasses.UserSettings
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

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
    private val localUsersList = mutableListOf<UserData>()

    // Firebase Components
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: Starting activity")
        setTheme(UserSettings.getThemeResource())
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.add_new_chat)

        // Initialize Firebase
        Log.d(TAG, "onCreate: Initializing Firebase components")
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initViews()
        setupUI()
        loadLocalUsers()
        showEmptyState("Enter a phone number to search for users")
        Log.i(TAG, "onCreate: Activity setup complete")
    }

    private fun initViews() {
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
            // Setup RecyclerView
            usersRecyclerView.layoutManager = LinearLayoutManager(this)
            userAdapter = UserAdapter(usersList, emptySet(), this)
            usersRecyclerView.adapter = userAdapter
            Log.d(TAG, "setupUI: RecyclerView configured")

            newGroupButton.setOnClickListener {
                startActivity(Intent(this, AddNewGroupActivity::class.java))
                finish()
            }

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
                    } else if (isFullPhoneNumber(query)) {
                        // Search using Firebase for full phone numbers
                        Log.d(TAG, "Full phone number detected, searching Firebase")
                        searchUsersByPhoneOnFirebase(query)
                    } else {
                        // Search using local JSON for partial numbers
                        Log.d(TAG, "Partial number, searching local database")
                        searchLocalUsersByPhone(query)
                    }
                }
            })
            Log.d(TAG, "setupUI: Search functionality configured")
            Log.i(TAG, "setupUI: All UI components configured successfully")
        } catch (e: Exception) {
            Log.e(TAG, "setupUI: Error configuring UI components", e)
        }
    }

    private fun isFullPhoneNumber(query: String): Boolean {
        // Check if the number consists of exactly 11 digits
        return query.all { it.isDigit() } && query.length == 11 ||
                // Or if it starts with "+" followed by exactly 10 digits
                (query.startsWith("+") && query.drop(1).all { it.isDigit() } && query.length == 11)
    }

    private fun loadLocalUsers() {
        Log.d(TAG, "loadLocalUsers: Loading users from local JSON file")
        try {
            progressIndicator.visibility = View.VISIBLE

            // Load from internal files directory instead of assets
            val file = File(filesDir, "local_user.json")

            if (!file.exists()) {
                Log.e(TAG, "loadLocalUsers: local_user.json not found in files directory: ${file.absolutePath}")
                // Create default users if the file doesn't exist
                progressIndicator.visibility = View.GONE
                return
            }

            Log.d(TAG, "loadLocalUsers: Reading file from: ${file.absolutePath}")

            // Read from local_user.json in internal files directory
            val jsonString = file.readText()

            val jsonArray = JSONArray(jsonString)

            // Parse JSON and create UserData objects
            for (i in 0 until jsonArray.length()) {
                val userObject = jsonArray.getJSONObject(i)

                val userData = UserData(
                    uid = userObject.getString("uid"),
                    displayName = userObject.getString("displayName"),
                    phoneNumber = userObject.getString("phoneNumber"),
                    password = userObject.optString("password", ""),
                    userDescription = userObject.optString("userDescription", ""),
                    userStatus = userObject.optString("userStatus", ""),
                    online = userObject.optBoolean("online", false),
                    lastSeen = userObject.optString("lastSeen", ""),
                    profilePictureUrl = userObject.optString("profilePictureUrl", "")
                )

                localUsersList.add(userData)
            }

            Log.d(TAG, "loadLocalUsers: Loaded ${localUsersList.size} users from local file")

        } catch (e: Exception) {
            Log.e(TAG, "loadLocalUsers: Error loading local users", e)
        } finally {
            progressIndicator.visibility = View.GONE
        }
    }

    private fun searchLocalUsersByPhone(phoneQuery: String) {
        Log.i(TAG, "searchLocalUsersByPhone: Searching local users with phone: '$phoneQuery'")
        try {
            // Show loading indicator
            progressIndicator.visibility = View.VISIBLE
            emptyResultsTextView.visibility = View.GONE

            // Clear any existing users
            usersList.clear()

            // Get current user ID
            val currentUserId = UserSettings.userId
            if (currentUserId == null) {
                Log.e(TAG, "searchLocalUsersByPhone: Current user ID is null")
                progressIndicator.visibility = View.GONE
                return
            }

            // Search for users whose phone number contains the query
            val results = localUsersList.filter {
                it.uid != currentUserId && // Exclude current user
                        it.phoneNumber.contains(phoneQuery) // Phone contains query
            }

            Log.d(TAG, "searchLocalUsersByPhone: Found ${results.size} matching users")

            if (results.isNotEmpty()) {
                usersList.addAll(results)
                userAdapter.notifyDataSetChanged()
                emptyResultsTextView.visibility = View.GONE
            } else {
                showEmptyState("No users found with this phone number")
            }

            progressIndicator.visibility = View.GONE

        } catch (e: Exception) {
            Log.e(TAG, "searchLocalUsersByPhone: Error searching local users", e)
            progressIndicator.visibility = View.GONE
            showEmptyState("An error occurred while searching")
        }
    }

    private fun searchUsersByPhoneOnFirebase(phoneQuery: String) {
        Log.i(TAG, "searchUsersByPhoneOnFirebase: Searching for users with phone: '$phoneQuery'")
        try {
            // Show loading indicator
            progressIndicator.visibility = View.VISIBLE
            emptyResultsTextView.visibility = View.GONE

            // Clear any existing users
            usersList.clear()

            // Get current user ID
            val currentUserId = UserSettings.userId
            if (currentUserId == null) {
                Log.e(TAG, "searchUsersByPhoneOnFirebase: Current user ID is null")
                return
            }
            Log.d(TAG, "searchUsersByPhoneOnFirebase: Current user ID: $currentUserId")

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
                                    // Create UserData object
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
                                        lastSeen = document.getLong("lastSeen").toString(),
                                        profilePictureUrl = document.getString("profilePictureUrl") ?: ""
                                    )

                                    // Add users that are not the current user and not already in the list
                                    if (userData.uid != currentUserId &&
                                        !usersList.any { it.uid == userData.uid }) {
                                        usersList.add(userData)
                                        totalUsersFound++
                                        Log.d(TAG, "Added user: ${userData.displayName}")
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
            Log.e(TAG, "searchUsersByPhoneOnFirebase: Unexpected error", e)
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