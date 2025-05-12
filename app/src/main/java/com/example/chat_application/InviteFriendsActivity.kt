package com.example.chat_application

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chat_application.adapters.ContactsAdapter
import com.example.chat_application.dataclasses.UserData
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InviteFriendsActivity : AppCompatActivity() {

    private val TAG = "InviteFriendsActivity"

    // UI Components
    private lateinit var toolbar: Toolbar
    private lateinit var searchView: SearchView
    private lateinit var recyclerViewContacts: RecyclerView
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var textViewEmpty: androidx.appcompat.widget.AppCompatTextView

    private lateinit var contactsAdapter: ContactsAdapter
    private lateinit var contactManager: ContactManager

    // List to store registered users from contacts
    private val registeredUsers = mutableListOf<ContactManager.ProcessedUser>()

    // Add the missing processedUsers variable
    private var processedUsers = listOf<ContactManager.ProcessedUser>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.invite_friends)

        Log.d(TAG, "Activity created")

        // Initialize ContactManager
        contactManager = ContactManager(this)

        initViews()
        setupToolbar()
        setupRecyclerView()
        setupSearchView()

        // Request contacts permission and load data
        requestContactsPermissionAndLoadData()
    }

    private fun initViews() {
        Log.d(TAG, "initViews: Finding view references")
        try {
            toolbar = findViewById(R.id.toolbar)
            searchView = findViewById(R.id.searchView)
            recyclerViewContacts = findViewById(R.id.recyclerViewContacts)
            progressIndicator = findViewById(R.id.progressIndicator)
            textViewEmpty = findViewById(R.id.textViewEmpty)

            Log.d(TAG, "initViews: All views initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "initViews: Error initializing views", e)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Invite Friends"
        Log.d(TAG, "Toolbar setup complete")
    }

    // Handle toolbar back button click
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                Log.d(TAG, "Back button clicked, navigating to SettingsActivity")
                navigateToSettings()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    // Function to navigate to SettingsActivity
    private fun navigateToSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        finish() // Close current activity
    }

    private fun setupRecyclerView() {
        contactsAdapter = ContactsAdapter(
            onContactClick = { processedUser ->
                // Handle user click - open profile details or similar action
                Log.i(TAG, "Contact clicked: ${processedUser.userData.displayName}")
            },
            onActionButtonClick = { processedUser ->
                if (processedUser.isRegistered) {
                    // Navigate to chat with this user
                    Log.i(TAG, "Navigating to chat with: ${processedUser.userData.displayName}")
                    navigateToChat(processedUser.userData.uid)
                } else {
                    // Send invitation to this contact
                    Log.i(TAG, "Sending invitation to: ${processedUser.userData.phoneNumber}")
                    sendInvitation(processedUser.userData.phoneNumber)
                }
            }
        )

        recyclerViewContacts.apply {
            layoutManager = LinearLayoutManager(this@InviteFriendsActivity)
            adapter = contactsAdapter
        }
        Log.d(TAG, "RecyclerView setup complete")
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                Log.d(TAG, "Search query changed: $newText")
                contactsAdapter.filter.filter(newText)
                return true
            }
        })
        Log.d(TAG, "SearchView setup complete")
    }

    private fun requestContactsPermissionAndLoadData() {
        showLoading(true)

        contactManager.checkAndRequestContactsPermission(
            activity = this,
            onGranted = {
                loadUsersFromContacts()
            },
            onDenied = {
                showError("Contact permission is required to show your friends")
                showEmptyState(true)
                showLoading(false)
            }
        )
    }

    private fun loadUsersFromContacts() {
        lifecycleScope.launch {
            try {
                // First cache contacts
                contactManager.cacheContacts()
                Log.d(TAG, "Contacts cached")

                // Then fetch registered users from server
                fetchRegisteredUsers { registeredUsersList ->
                    lifecycleScope.launch {
                        try {
                            Log.d(
                                TAG,
                                "Registered users fetched, count: ${registeredUsersList.size}"
                            )

                            // Now convert contacts to users, filtering only registered ones
                            val allProcessedUsers =
                                contactManager.convertContactsToUsers(registeredUsersList)
                            Log.d(TAG, "All processed users: ${allProcessedUsers.size}")

                            // Filter out registered users
                            processedUsers = allProcessedUsers
                            Log.d(TAG, "Registered processed users: ${processedUsers.size}")

                            withContext(Dispatchers.Main) {
                                registeredUsers.clear()
                                registeredUsers.addAll(processedUsers)
                                contactsAdapter.submitList(processedUsers)

                                showLoading(false)
                                showEmptyState(processedUsers.isEmpty())

                                Log.d(TAG, "Loaded ${processedUsers.size} registered contacts")
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                showLoading(false)
                                showError("Error processing contacts: ${e.message}")
                                Log.e(TAG, "Error processing contacts", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    showError("Error loading contacts: ${e.message}")
                    Log.e(TAG, "Error loading contacts", e)
                }
            }
        }
    }

    // Simulated function to fetch registered users from your backend
    private fun fetchRegisteredUsers(callback: (List<UserData>) -> Unit) {
        val users = mutableListOf<UserData>()
        HelperFunctions.getAllUserIds { userIds ->
            if (userIds != null) {
                for (userId in userIds) {
                    val user = HelperFunctions.loadUserById(userId, this)
                    if (user != null) {
                        users.add(user)
                    }
                }
            }
            callback(users)
        }
    }

    private fun showLoading(isLoading: Boolean) {
        progressIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun showEmptyState(show: Boolean) {
        textViewEmpty.visibility = if (show) View.VISIBLE else View.GONE
        recyclerViewContacts.visibility = if (show) View.GONE else View.VISIBLE
        Log.d(TAG, "Empty state: ${if (show) "visible" else "hidden"}")
    }

    private fun showError(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
        Log.e(TAG, "Error shown: $message")
    }

    private fun navigateToChat(userId: String) {
        Log.i(TAG, "Navigation to chat with user ID: $userId")
        val intent = Intent(this, UserProfileActivity::class.java).apply {
            putExtra("USER_ID", userId)
        }
        startActivity(intent)
    }

    private fun sendInvitation(phoneNumber: String) {
        try {
            val message = "Check out my app! Download it here: https://bit.ly/Za3boot"

            val formattedNumber = phoneNumber.replace(Regex("[^0-9+]"), "")

            // Create SMS intent with properly formatted URI
            val smsUri = Uri.parse("smsto:$formattedNumber")
            val intent = Intent(Intent.ACTION_SENDTO, smsUri)
            intent.putExtra("sms_body", message)

            // Start activity
            startActivity(intent)
            Log.i(TAG, "SMS app opened with draft for: $formattedNumber")

        } catch (e: Exception) {
            Log.e(TAG, "Error launching SMS intent: ${e.message}")
            Snackbar.make(
                findViewById(android.R.id.content),
                "Couldn't open messaging app", Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    // Handle physical back button press
    override fun onBackPressed() {
        Log.d(TAG, "Back button pressed, navigating to SettingsActivity")
        navigateToSettings()
    }
}