package com.example.chat_application

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore


class StoryListActivity : AppCompatActivity() {

    // UI Components
    private lateinit var storyRecyclerView: RecyclerView
    private lateinit var settingsButton: ImageView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var newStoryFab: FloatingActionButton

    // Firebase Components
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var usersReference: DatabaseReference
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.stories_page)

        initViews()
        setupUI()
        setupRecyclerView()
        setupFirebase()
    }

    //region UI Setup
    private fun initViews() {
        storyRecyclerView = findViewById(R.id.storyRecyclerView)
        settingsButton = findViewById(R.id.settingsButton)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        newStoryFab = findViewById(R.id.newStoryFab)
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

    private fun setupRecyclerView() {
        storyRecyclerView.layoutManager = LinearLayoutManager(this)
//        chatAdapter = ChatAdapter(chatManager.getAll(), this, this)
//        chatRecyclerView.adapter = chatAdapter
    }

    //region Firebase
    private fun setupFirebase() {
            firebaseDatabase = FirebaseDatabase.getInstance()
            usersReference = firebaseDatabase.getReference("users")
            firestore = Firebase.firestore
    }
}