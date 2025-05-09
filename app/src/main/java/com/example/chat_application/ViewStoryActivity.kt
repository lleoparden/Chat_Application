package com.example.chat_application

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.chat_application.dataclasses.Stories
import com.example.chat_application.dataclasses.Story
import com.example.chat_application.dataclasses.UserSettings
import com.google.firebase.firestore.FirebaseFirestore

private const val TAG = "ViewStoryActivity"

class ViewStoryActivity : AppCompatActivity() {

    private lateinit var pfpImage: ImageView
    private lateinit var storyImage: ImageView
    private lateinit var storyCaption: TextView
    private lateinit var accName: TextView
    private lateinit var postTimer: TextView
    private lateinit var nextButton: Button
    private lateinit var backButton: Button

    // Data objects
    private val firebaseEnabled by lazy { resources.getBoolean(R.bool.firebaseOn) }
    private lateinit var db: FirebaseFirestore

    // State variables
    private var userId = ""
    private lateinit var viewedStory: Stories
    private var storyIndex = 0
    private var filteredStories: MutableList<Story> = mutableListOf()

    // Time variable
    private var currentTimeMs: Long = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.getThemeResource())
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.view_story)

        // Get current user ID
        userId = UserSettings.userId

        // Initialize Firebase if enabled
        if (firebaseEnabled) {
            db = FirebaseFirestore.getInstance()
        }

        viewedStory = intent.getParcelableExtra<Stories>("passedStory") ?: Stories(
            "",
            "",
            "",
            listOf(Story("", "", ""))
        )

        initViews()
        processStories()
        setupNavigation()
        loadStoryInfoIntoView()
    }

    private fun initViews() {
        pfpImage = findViewById(R.id.pfpImage)
        accName = findViewById(R.id.accName)
        postTimer = findViewById(R.id.postTimer)
        storyCaption = findViewById(R.id.storyCaption)
        storyImage = findViewById(R.id.storyImage)
        nextButton = findViewById(R.id.nextButton)
        backButton = findViewById(R.id.backButton)
    }

    private fun processStories() {
        // Filter out stories older than 24 hours and update Firestore
        val validStories = mutableListOf<Story>()
        val expiredStoryIndices = mutableListOf<Int>()

        viewedStory.stories?.forEachIndexed { index, story ->
            val timeElapsedMs = currentTimeMs - story.uploadedAt.toLong()

            if (timeElapsedMs <= 86400000) { // Less than or equal to 24 hours (86,400,000 ms)
                validStories.add(story)
            } else {
                expiredStoryIndices.add(index)
            }
        }

        // If there are expired stories, update Firestore
        if (expiredStoryIndices.isNotEmpty() && firebaseEnabled) {
            updateFirestoreStories(validStories)
        }

        // Update our local list for display
        filteredStories = validStories

        // If all stories have expired, onBackPressed the activity
        if (filteredStories.isEmpty()) {
            Toast.makeText(this, "All stories have expired", Toast.LENGTH_SHORT).show()
            db.collection("Stories").document(viewedStory.uid).delete()
            onBackPressed()
        }
    }

    private fun updateFirestoreStories(validStories: List<Story>) {
        try {
            db.collection("Stories").document(viewedStory.uid)
                .update("stories", validStories)
                .addOnSuccessListener {
                    // Stories updated successfully
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to update stories: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNavigation() {
        nextButton.setOnClickListener {
            if (storyIndex < filteredStories.size - 1) {
                storyIndex++
                loadCurrentStory()
            } else {
                onBackPressed() // Go back when we've seen all stories
            }
        }

        backButton.setOnClickListener {
            if (storyIndex > 0) {
                storyIndex--
                loadCurrentStory()
            }
        }
    }

    private fun loadStoryInfoIntoView() {
        val userdata = HelperFunctions.loadUserById(viewedStory.uid, this)
        // This should always be non-null because we've filtered the stories
        if (userdata != null) {
            HelperFunctions.loadImageFromUrl(viewedStory.profilePictureUrl, pfpImage)
            accName.text = userdata.displayName
        }
        // Load the first valid story
        if (filteredStories.isNotEmpty()) {
            loadCurrentStory()
        }
    }

    private fun loadCurrentStory() {
        if (storyIndex < 0 || storyIndex >= filteredStories.size) return

        val currentStory = filteredStories[storyIndex]

        // Display caption if available
        if (currentStory.storyCaption.isNotEmpty()) {
            storyCaption.isVisible = true
            storyCaption.text = currentStory.storyCaption
        } else {
            storyCaption.isVisible = false
        }

        // Load image
        HelperFunctions.loadImageFromUrl(currentStory.imageurl, storyImage)

        // Update time display
        updateTimeDisplay(currentStory)
    }

    private fun updateTimeDisplay(story: Story) {
        val timeElapsedMs = currentTimeMs - story.uploadedAt.toLong()

        val timeText = when {
            timeElapsedMs < 60000 -> "${(timeElapsedMs / 1000).toInt()}s" // Less than 1 minute
            timeElapsedMs < 3600000 -> "${(timeElapsedMs / 60000).toInt()}m" // Less than 1 hour
            else -> "${(timeElapsedMs / 3600000).toInt()}h" // Display in hours
        }

        postTimer.text = timeText
    }

    private fun navigateBack() {
        val intent = Intent(this, StoryListActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        finish()
    }

    override fun onBackPressed() {
        navigateBack()
    }
}