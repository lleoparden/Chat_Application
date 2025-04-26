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
        // We don't load all users initially - we'll wait for search
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
            userAdapter = UserAdapter(usersList, this)
            usersRecyclerView.adapter = userAdapter
            Log.d(TAG, "setupUI: RecyclerView configured")

            // Setup search functionality - specifically for phone numbers
            searchEditText.hint = "Search by phone number"
            searchEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val query = s.toString().trim()
                    Log.d(TAG, "Search query changed: '$query'")

                    if (query.length >= 3) { // Only search when at least 3 digits entered
                        Log.d(TAG, "Query length sufficient, initiating search")
                        var formatednumber = formatPhoneNumber(query)
                        searchUsersByPhone(formatednumber)
                    } else {
                        // Clear results if search too short
                        Log.d(TAG, "Query too short, clearing results")
                        usersList.clear()
                        userAdapter.notifyDataSetChanged()
                        showEmptyState("Enter at least 3 digits to search")
                    }
                }
            })
            Log.d(TAG, "setupUI: Search functionality configured")

            // Setup new user button - now for adding contacts directly
            newGroupButton.setOnClickListener {
                val phoneNumber = searchEditText.text.toString().trim()
                Log.d(TAG, "Add contact button clicked with phone: '$phoneNumber'")

                if (phoneNumber.isNotEmpty()) {
                    addNewContact(phoneNumber)
                } else {
                    Log.w(TAG, "Attempted to add contact without entering phone number")
                    Toast.makeText(this, "Enter a phone number first", Toast.LENGTH_SHORT).show()
                }
            }
            Log.d(TAG, "setupUI: Add contact button configured")

            Log.i(TAG, "setupUI: All UI components configured successfully")
        } catch (e: Exception) {
            Log.e(TAG, "setupUI: Error configuring UI components", e)
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

            // Option 1: Simplified query - Get all matching phone numbers first,
            // then filter out current user in memory
            Log.d(TAG, "searchUsersByPhone: Querying Firestore for users with phone prefix")
            firestore.collection("users")
                .whereGreaterThanOrEqualTo("phoneNumber", phoneQuery)
                .whereLessThanOrEqualTo("phoneNumber", phoneQuery + "\uf8ff")
                .limit(10) // Increased limit to account for potential filtering
                .get()
                .addOnSuccessListener { documents ->
                    Log.d(TAG, "searchUsersByPhone: Query successful, found ${documents.size()} documents")
                    usersList.clear()

                    if (documents.isEmpty) {
                        // No users found with this phone number
                        Log.i(TAG, "searchUsersByPhone: No users found with phone: '$phoneQuery'")
                        showEmptyState("No users found with this phone number")
                    } else {
                        var successCount = 0

                        // Filter out current user in memory
                        // In searchUsersByPhone method, replace the document.toObject() call with manual conversion
                        for (document in documents) {
                            try {
                                Log.v(TAG, "Processing document: ${document.id}")

                                // Instead of automatic conversion, manually create the UserData object
                                val userData = UserData(
                                    uid = document.getString("uid") ?: "",
                                    displayName = document.getString("displayName") ?: "",
                                    phoneNumber = document.getString("phoneNumber") ?: "",
                                    password = document.getString("password") ?: "",
                                    userDescription = document.getString("userDescription") ?: "",
                                    userStatus = document.getString("userStatus") ?: "",
                                    // Manual conversion of the online field from string to boolean
                                    online = when (val onlineValue = document.get("online")) {
                                        is Boolean -> onlineValue
                                        is String -> onlineValue.equals("true", ignoreCase = true)
                                        else -> false // Default value if field is missing or of unexpected type
                                    },
                                    lastSeen = document.getString("lastSeen") ?: "",
                                    profilePictureUrl = document.getString("profilePictureUrl") ?: ""
                                )

                                // Skip if this is the current user
                                if (userData.uid != currentUserId) {
                                    usersList.add(userData)
                                    Log.d(TAG, "Added user: ${userData.displayName}, phone: ${userData.phoneNumber}")
                                } else {
                                    Log.d(TAG, "Skipped current user: ${userData.displayName}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing user document: ${document.id}", e)
                                // Continue with next document
                            }
                        }

                        Log.i(TAG, "searchUsersByPhone: Successfully processed $successCount users")

                        if (usersList.isEmpty()) {
                            showEmptyState("No users found with this phone number")
                        } else {
                            // Show results
                            userAdapter.notifyDataSetChanged()
                            emptyResultsTextView.visibility = View.GONE
                        }
                    }

                    // Hide loading indicator
                    progressIndicator.visibility = View.GONE
                }
                .addOnFailureListener { exception ->
                    // Hide loading indicator
                    progressIndicator.visibility = View.GONE

                    // Show error message
                    Log.e(TAG, "searchUsersByPhone: Error searching users", exception)
                    showEmptyState("Error searching users: ${exception.message}")
                }

        } catch (e: Exception) {
            Log.e(TAG, "searchUsersByPhone: Unexpected error", e)
            progressIndicator.visibility = View.GONE
            showEmptyState("An unexpected error occurred")
        }
    }

    private fun addNewContact(phoneNumber: String) {
        Log.i(TAG, "addNewContact: Adding contact with phone: '$phoneNumber'")
        try {
            // Show loading indicator
            progressIndicator.visibility = View.VISIBLE

            // First check if user with this phone number exists
            Log.d(TAG, "addNewContact: Checking if user exists")
            firestore.collection("users")
                .whereEqualTo("phoneNumber", phoneNumber)
                .limit(1)
                .get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        // User doesn't exist, invite them
                        Log.i(TAG, "addNewContact: No user found with phone: '$phoneNumber'")
                        progressIndicator.visibility = View.GONE
                        Toast.makeText(this, "No user found with this number. Invite them to join!", Toast.LENGTH_LONG).show()
                        // Here you could implement SMS invite functionality
                    } else {
                        // User exists, add to contacts
                        Log.d(TAG, "addNewContact: User found, attempting to add to contacts")
                        val user = documents.documents[0].toObject(UserData::class.java)
                        if (user != null) {
                            Log.d(TAG, "addNewContact: Adding user: ${user.displayName} (${user.uid})")
                            addUserToContacts(user)
                        } else {
                            Log.e(TAG, "addNewContact: Failed to convert document to UserData")
                            progressIndicator.visibility = View.GONE
                            Toast.makeText(this, "Error adding contact", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    progressIndicator.visibility = View.GONE
                    Log.e(TAG, "addNewContact: Error checking if user exists", e)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e(TAG, "addNewContact: Unexpected error", e)
            progressIndicator.visibility = View.GONE
            Toast.makeText(this, "An unexpected error occurred", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addUserToContacts(user: UserData) {
        Log.i(TAG, "addUserToContacts: Adding user to contacts: ${user.displayName} (${user.uid})")
        try {
            val currentUserId = auth.currentUser?.uid
            if (currentUserId == null) {
                Log.e(TAG, "addUserToContacts: Current user ID is null")
                return
            }

            // Create contact entry in Firestore
            val contactData = hashMapOf(
                "userId" to user.uid,
                "addedAt" to com.google.firebase.Timestamp.now()
            )

            Log.d(TAG, "addUserToContacts: Creating contact document in Firestore")
            firestore.collection("users")
                .document(currentUserId)
                .collection("contacts")
                .document(user.uid)
                .set(contactData)
                .addOnSuccessListener {
                    Log.i(TAG, "addUserToContacts: Successfully added contact")
                    progressIndicator.visibility = View.GONE
                    Toast.makeText(this, "Contact added: ${user.displayName}", Toast.LENGTH_SHORT).show()

                    // Start chat with this user
                    Log.d(TAG, "addUserToContacts: Navigating to UserProfileActivity")
                    val intent = Intent(this, UserProfileActivity::class.java).apply {
                        putExtra("USER_ID", user.uid)
                        putExtra("came_from", "AddNewChat")
                    }
                    startActivity(intent)
                }
                .addOnFailureListener { e ->
                    progressIndicator.visibility = View.GONE
                    Log.e(TAG, "addUserToContacts: Failed to add contact", e)
                    Toast.makeText(this, "Failed to add contact: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e(TAG, "addUserToContacts: Unexpected error", e)
            progressIndicator.visibility = View.GONE
            Toast.makeText(this, "An unexpected error occurred", Toast.LENGTH_SHORT).show()
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
    private fun formatPhoneNumber(phoneNumber: String): String {
        // This is just a simple example - adjust to your requirements
        var formattedNumber = phoneNumber

        // If doesn't start with +, assume it's a local number and add country code
        if (!formattedNumber.startsWith("+")) {
            formattedNumber = "+2$formattedNumber" // Assuming US (+1), change as needed
        }

        return formattedNumber
    }
    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Activity being destroyed")
        super.onDestroy()
    }
}