package com.example.chat_application

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Firebase
import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore


class StoryListActivity : AppCompatActivity(), StoryAdapter.OnStoryClickListener {

    private val TAG = "StoryListActivity"

    // UI Components
    private lateinit var storyRecyclerView: RecyclerView
    private lateinit var settingsButton: ImageView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var newStoryFab: FloatingActionButton

    // Firebase Components
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var usersReference: DatabaseReference
    private lateinit var firestore: FirebaseFirestore

    // Data Components
    private lateinit var storyAdapter: StoryAdapter
    private val storiesList = mutableListOf<Stories>()
    private lateinit var listOfUsers: List<String>

    private val firebaseEnabled by lazy { resources.getBoolean(R.bool.firebaseOn) }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.stories_page)

        initViews()
        setupUI()
        setupRecyclerView()
        setupFirebase()
        loadStories()
    }

    //region UI Setup
    private fun initViews() {
        storyRecyclerView = findViewById(R.id.storyRecyclerView)
        settingsButton = findViewById(R.id.settingsButton)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        newStoryFab = findViewById(R.id.newStoryFab)
        bottomNavigation.selectedItemId = R.id.navigation_stories
    }

    private fun setupUI() {

        // Setup settings button
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }

        // Setup FAB
        newStoryFab.setOnClickListener {
            startActivity(Intent(this, AddNewStoryActivity::class.java))
            finish()
        }

        // Setup bottom navigation
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_stories -> true // Already on chats page
                R.id.navigation_chats -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadStories() {
        // Show loading indicator if you have one

        globalFunctions.getAllUserIds() { userIds ->
            if (userIds == null || userIds.isEmpty()) {
                Log.e(TAG, "Failed to get user IDs or list is empty")
                Toast.makeText(this, "Failed to load users", Toast.LENGTH_SHORT).show()
                return@getAllUserIds
            }

            listOfUsers = userIds
            if (firebaseEnabled) {
                loadStoryFromFirebase(userIds)
            }
            else{
                //loadStoryfromLocalStorage()
            }
        }
    }

    private fun loadStoryFromFirebase(users: List<String>) {
        if (!firebaseEnabled) {
            Log.d(TAG, "Firebase is disabled, skipping story loading")
            return
        }

        if (users.isEmpty()) {
            Log.d(TAG, "No users to load stories from")
            return
        }

        // Clear existing stories
        storiesList.clear()

        val totalUsers = users.size
        var loadedUsers = 0

        for (user in users) {
            firestore.collection("Stories").document(user).get()
                .addOnSuccessListener { document ->
                    loadedUsers++

                    if (document != null && document.exists()) {
                        try {
                            val storiesData = document.data
                            if (storiesData != null) {
                                // Safely get stories list with type checking
                                @Suppress("UNCHECKED_CAST")
                                val storyList = document.get("stories") as? List<Map<String, Any>> ?: emptyList()

                                // Convert maps to Story objects
                                val stories = storyList.mapNotNull { storyMap ->
                                    try {
                                        Story(
                                            // Add proper conversions based on your Story class structure
                                            // Example: id = (storyMap["id"] as? String) ?: "",
                                        )
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error parsing story data", e)
                                        null
                                    }
                                }

                                val userStories = Stories(
                                    uid = document.getString("uid") ?: "",
                                    displayName = document.getString("displayName") ?: "",
                                    profilePictureUrl = document.getString("profilePictureUrl") ?: "",
                                    stories = stories
                                )
                                storiesList.add(userStories)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing Stories document for user $user", e)
                        }
                    } else {
                        Log.d(TAG, "No stories found for user $user")
                    }

                    // Update UI when all users are processed
                    if (loadedUsers >= totalUsers) {
                        runOnUiThread {
                            storyAdapter.notifyDataSetChanged()
                            // Hide loading indicator if you have one
                        }
                    }
                }
                .addOnFailureListener { e ->
                    loadedUsers++
                    Log.e(TAG, "Error loading stories for user $user", e)

                    // Update UI when all users are processed, even if some failed
                    if (loadedUsers >= totalUsers) {
                        runOnUiThread {
                            storyAdapter.notifyDataSetChanged()
                            // Hide loading indicator if you have one
                        }
                    }
                }
        }
    }

    private fun setupRecyclerView() {
        storyRecyclerView.layoutManager = LinearLayoutManager(this)
        storyAdapter = StoryAdapter(storiesList, this)
        storyRecyclerView.adapter = storyAdapter
    }

    //region Firebase
    private fun setupFirebase() {
        firebaseDatabase = FirebaseDatabase.getInstance()
        usersReference = firebaseDatabase.getReference("users")
        firestore = FirebaseFirestore.getInstance()
    }

    // Implement the OnStoryClickListener interface method
    override fun onStoryClick(story: Stories) {
        // Handle story click here
        val intent = Intent(this, ViewStoryActivity::class.java)
        intent.putExtra("storyId", story.uid)
        startActivity(intent)
    }
}