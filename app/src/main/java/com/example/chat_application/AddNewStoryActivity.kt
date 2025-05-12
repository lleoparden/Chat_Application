package com.example.chat_application

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.chat_application.dataclasses.Stories
import com.example.chat_application.dataclasses.Story
import com.example.chat_application.dataclasses.UserSettings
import com.example.chat_application.services.ImageUploadService
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.io.File

private const val TAG = "AddNewStoryActivity"

class AddNewStoryActivity : AppCompatActivity() {

    // User data storage for registration process
    private var caption: String = ""
    private var imageURL: String = ""

    private lateinit var storyPreview: ImageView
    private lateinit var storyCaption: EditText
    private lateinit var postStoryButton: Button
    private lateinit var chooseImageButton: Button

    // Data objects
    private val firebaseEnabled by lazy { resources.getBoolean(R.bool.firebaseOn) }
    private lateinit var db: FirebaseFirestore

    // State variables
    private var selectedImageUri: Uri? = null
    private var storyPictureUrl: String? = null
    private var localImagePath: String? = null
    private var userId = ""
    private lateinit var imagePickLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.getThemeResource())
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.create_story)

        // Get current user ID
        userId = UserSettings.userId

        // Initialize Firebase if enabled
        if (firebaseEnabled) {
            db = FirebaseFirestore.getInstance()
        }

        initViews()
        setupClickListeners()
        setupImagePickerLauncher()
    }

    private fun initViews() {
        storyPreview = findViewById(R.id.storyPreview)
        storyCaption = findViewById(R.id.storyCaption)
        postStoryButton = findViewById(R.id.postStoryButton)
        chooseImageButton = findViewById(R.id.chooseImageButton)
    }

    private fun setupClickListeners() {
        // Profile image click handlers
        chooseImageButton.setOnClickListener { pickImage() }

        postStoryButton.setOnClickListener {
            postStory()
        }

        val backButton = findViewById<Toolbar>(R.id.toolbar)
        backButton.setNavigationOnClickListener {
            goBack()
        }
    }

    private fun goBack(){
        val intent = Intent(this, StoryListActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
    }

    private fun postStory() {
        caption = storyCaption.text.toString().trim()
        imageURL = storyPictureUrl.toString()

        // Check if image URL is valid
        if (imageURL.isNullOrEmpty() || imageURL == "null") {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading indicator
        val loadingToast = Toast.makeText(this, "Posting story...", Toast.LENGTH_SHORT)
        loadingToast.show()

        val story = Story(
            imageurl = imageURL,
            storyCaption = caption,
            uploadedAt = System.currentTimeMillis().toString()
        )

        // Use the asynchronous version of getUserData with callback
        HelperFunctions.getUserData(userId) { userData ->
            if (userData != null) {
                // Save story locally first - this happens regardless of network status
                val localSaveSuccess = saveStoryLocally(
                    story = story,
                    userId = userId,
                    userName = userData.displayName,
                    userProfilePic = userData.profilePictureUrl
                )

                if (localSaveSuccess) {
                    Log.d(TAG, "Story saved locally successfully")
                } else {
                    Log.e(TAG, "Failed to save story locally")
                }

                // Create stories object with actual user data
                val stories = Stories(
                    uid = userId,
                    displayName = userData.displayName,
                    profilePictureUrl = userData.profilePictureUrl,
                    stories = listOf(story)
                )

                // Proceed with online posting if Firebase is enabled
                if (firebaseEnabled) {
                    // Check if user already has stories collection
                    db.collection("Stories").document(userId).get()
                        .addOnSuccessListener { document ->
                            if (document != null && document.exists()) {
                                // Add new story to existing stories array
                                db.collection("Stories").document(userId)
                                    .update("stories", FieldValue.arrayUnion(story))
                                    .addOnSuccessListener {
                                        loadingToast.cancel()
                                        Toast.makeText(this@AddNewStoryActivity, "Story posted successfully!", Toast.LENGTH_SHORT).show()
                                        goBack()
                                    }
                                    .addOnFailureListener { e ->
                                        loadingToast.cancel()
                                        // Still show success because we saved locally
                                        Toast.makeText(this@AddNewStoryActivity, "Story saved locally but couldn't upload to cloud: ${e.message}", Toast.LENGTH_SHORT).show()
                                        goBack()
                                    }
                            } else {
                                // Create new stories document for user
                                db.collection("Stories").document(userId)
                                    .set(stories)
                                    .addOnSuccessListener {
                                        loadingToast.cancel()
                                        Toast.makeText(this@AddNewStoryActivity, "Story posted successfully!", Toast.LENGTH_SHORT).show()
                                        goBack()
                                    }
                                    .addOnFailureListener { e ->
                                        loadingToast.cancel()
                                        // Still show success because we saved locally
                                        Toast.makeText(this@AddNewStoryActivity, "Story saved locally but couldn't upload to cloud: ${e.message}", Toast.LENGTH_SHORT).show()
                                        goBack()
                                    }
                            }
                        }
                        .addOnFailureListener { e ->
                            loadingToast.cancel()
                            // Still show success because we saved locally
                            Toast.makeText(this@AddNewStoryActivity, "Story saved locally but couldn't connect to cloud: ${e.message}", Toast.LENGTH_SHORT).show()
                            goBack()
                        }
                } else {
                    // If Firebase is disabled, just show local save success
                    loadingToast.cancel()
                    Toast.makeText(this@AddNewStoryActivity, "Story saved locally!", Toast.LENGTH_SHORT).show()
                    goBack()
                }
            } else {
                // Even if we can't get user data from server, try to save locally with available info
                val localSaveSuccess = saveStoryLocally(
                    story = story,
                    userId = userId,
                    userName = "Unknown", // Fallback name
                    userProfilePic = "" // No profile pic available
                )

                if (localSaveSuccess) {
                    loadingToast.cancel()
                    Toast.makeText(this@AddNewStoryActivity, "Could not retrieve user data from server. Story saved locally only.", Toast.LENGTH_SHORT).show()
                    goBack()
                } else {
                    loadingToast.cancel()
                    Toast.makeText(this@AddNewStoryActivity, "Could not save story. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupImagePickerLauncher() {
        imagePickLauncher = registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                if (data != null && data.data != null) {
                    selectedImageUri = data.data

                    // Display the selected image immediately
                    ImageUploadService.loadImageIntoView(this, selectedImageUri, storyPreview)

                    // Save image locally first
                    localImagePath = ImageUploadService.saveImageLocally(this, selectedImageUri!!, userId, "story_")

                    // Also upload to ImgBB for online access
                    val progressBar = findViewById<ProgressBar>(R.id.imageUploadProgressBar)
                    ImageUploadService.uploadImageToImgbb(
                        context = this,
                        imageUri = selectedImageUri!!,
                        progressBar = progressBar,
                        callback = object : ImageUploadService.ImageUploadCallback {
                            override fun onUploadSuccess(imageUrl: String) {
                                storyPictureUrl = imageUrl
                            }

                            override fun onUploadFailure(errorMessage: String) {
                                Toast.makeText(this@AddNewStoryActivity, errorMessage, Toast.LENGTH_SHORT).show()
                            }

                            override fun onUploadProgress(isUploading: Boolean) {
                                // This is handled by the progressBar visibility in the service
                            }
                        }
                    )
                }
            }
        }
    }

    private fun pickImage() {
        ImagePicker.with(this)
            .crop()
            .compress(512)           // Compress image size
            .maxResultSize(1920, 1080) // Maximum result size
            .createIntent { intent: Intent ->
                imagePickLauncher.launch(intent)
            }
    }

    private fun saveStoryLocally(story: Story, userId: String, userName: String, userProfilePic: String): Boolean {
        try {
            // Create a unique filename based on user ID and timestamp
            val storyFileName = "story_${userId}_${story.uploadedAt}.json"

            // Create JSON object with all story data
            val storyJson = JSONObject().apply {
                put("storyId", story.uploadedAt)  // Using timestamp as ID
                put("userId", userId)
                put("userName", userName)
                put("userProfilePic", userProfilePic)
                put("imageUrl", story.imageurl)
                put("caption", story.storyCaption)
                put("timestamp", story.uploadedAt)

                // Add local image path if available
                if (localImagePath != null) {
                    put("localImagePath", localImagePath)
                }
            }

            // Write to internal storage
            val file = File(filesDir, "stories/$storyFileName")

            // Ensure the directory exists
            file.parentFile?.mkdirs()

            // Write the JSON data to the file
            file.writeText(storyJson.toString())

            Log.d(TAG, "Story saved locally: ${file.absolutePath}")

            // Update local stories index
            updateStoriesIndex(storyFileName)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving story locally", e)
            return false
        }
    }

    private fun updateStoriesIndex(storyFileName: String) {
        try {
            // Create or load existing index file
            val indexFile = File(filesDir, "stories/index.json")

            val storiesIndex = if (indexFile.exists()) {
                JSONObject(indexFile.readText())
            } else {
                JSONObject().apply {
                    put("stories", JSONObject())
                    put("lastUpdated", System.currentTimeMillis())
                }
            }

            // Add new story to index
            val storiesObj = storiesIndex.getJSONObject("stories")
            storiesObj.put(storyFileName, System.currentTimeMillis())

            // Update timestamp
            storiesIndex.put("lastUpdated", System.currentTimeMillis())

            // Save updated index
            indexFile.parentFile?.mkdirs()
            indexFile.writeText(storiesIndex.toString())

            Log.d(TAG, "Stories index updated with: $storyFileName")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating stories index", e)
        }
    }
}