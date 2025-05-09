package com.example.chat_application

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.chat_application.dataclasses.UserSettings
import com.example.chat_application.services.FirebaseService
import com.example.chat_application.services.ImageUploadService
import com.example.chat_application.services.LocalStorageService
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import java.io.File

private const val TAG = "EditProfileActivity"

class EditProfileActivity : AppCompatActivity() {
    // UI Components
    private lateinit var profileImageView: ImageView
    private lateinit var nameEditText: TextInputEditText
    private lateinit var statusEditText: TextInputEditText
    private lateinit var descriptionEditText: TextInputEditText
    private lateinit var phoneTextView: TextView
    private lateinit var saveButton: TextView
    private lateinit var progressIndicator: ProgressBar
    private lateinit var imageUploadProgressBar: ProgressBar

    // Data objects
    private val firebaseEnabled by lazy { resources.getBoolean(R.bool.firebaseOn) }

    // State variables
    private var selectedImageUri: Uri? = null
    private var profilePictureUrl: String? = null
    private var localImagePath: String? = null
    private var userId = ""
    private lateinit var imagePickLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.getThemeResource())
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.edit_profile)

        // Initialize UI components and setup launchers
        initializeViews()
        setupImagePickerLauncher()

        // Initialize services
        LocalStorageService.initialize(this, ContentValues.TAG)

        if (firebaseEnabled) {
            FirebaseService.initialize(this, TAG, firebaseEnabled)
        }

        // Get current user ID
        userId = UserSettings.userId

        // Set click listeners
        setupClickListeners()

        // Load user data
        loadUserData()
    }

    private fun initializeViews() {
        profileImageView = findViewById(R.id.profileImageView)
        nameEditText = findViewById(R.id.nameEditText)
        statusEditText = findViewById(R.id.aboutEditText)
        descriptionEditText = findViewById(R.id.descriptionEditText)
        phoneTextView = findViewById(R.id.phoneTextView)
        saveButton = findViewById(R.id.saveButton)
        progressIndicator = findViewById(R.id.progressIndicator)
        imageUploadProgressBar = findViewById(R.id.imageUploadProgressBar)
        imageUploadProgressBar.visibility = View.GONE
    }

    private fun setupClickListeners() {
        // Profile image click handlers
        findViewById<ImageView>(R.id.editImageButton).setOnClickListener { pickImage() }
        profileImageView.setOnClickListener { pickImage() }

        // Save button
        saveButton.setOnClickListener { saveProfile() }

        // Toolbar back button
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { navigateBack() }
    }

    private fun setupImagePickerLauncher() {
        imagePickLauncher = registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                if (data != null && data.data != null) {
                    selectedImageUri = data.data

                    // Display the selected image immediately
                    ImageUploadService.loadImageIntoView(this, selectedImageUri, profileImageView)

                    // Save image locally first
                    localImagePath = ImageUploadService.saveImageLocally(this, selectedImageUri!!, userId)

                    // Also upload to ImgBB for online access
                    ImageUploadService.uploadImageToImgbb(
                        this,
                        selectedImageUri!!,
                        imageUploadProgressBar,
                        object : ImageUploadService.ImageUploadCallback {
                            override fun onUploadSuccess(imageUrl: String) {
                                profilePictureUrl = imageUrl
                            }

                            override fun onUploadFailure(errorMessage: String) {
                                Toast.makeText(this@EditProfileActivity,
                                    errorMessage, Toast.LENGTH_SHORT).show()
                            }

                            override fun onUploadProgress(isUploading: Boolean) {
                                // This can be used to update UI elements based on upload state
                            }
                        }
                    )
                }
            }
        }
    }

    private fun loadUserData() {
        progressIndicator.visibility = View.VISIBLE
        loadInfoIntoViews()
    }

    private fun loadInfoIntoViews() {
        Log.d(TAG, "Starting to load user info into views for userId: $userId")
        progressIndicator.visibility = View.VISIBLE

        FirebaseService.loadUserFromFirebase(userId) { user ->
            Log.d(TAG, "User data received in loadInfoIntoViews: ${user.displayName}")

            nameEditText.setText(user.displayName)
            statusEditText.setText(user.userStatus)
            descriptionEditText.setText(user.userDescription)
            phoneTextView.text = user.phoneNumber

            Log.d(TAG, "Text fields populated with user data")

            // Check for local image first - we may have saved it locally previously
            val localImageFile = File(filesDir, "profile_${userId}.jpg")
            val imageUrl = user.profilePictureUrl

            Log.d(TAG, "Checking profile picture - URL: ${if (imageUrl.isNotEmpty()) imageUrl else "empty"}, Local file exists: ${localImageFile.exists()}")

            if (imageUrl.isNotEmpty()) {
                Log.d(TAG, "Loading remote profile image from URL: $imageUrl")
                profilePictureUrl = imageUrl
                HelperFunctions.loadImageFromUrl(imageUrl, profileImageView)

                // Try to download and save the image locally for next time
                Log.d(TAG, "Attempting to download and save remote image locally")
                ImageUploadService.downloadAndSaveImageLocally(this, imageUrl, userId)
            } else if (localImageFile.exists()) {
                Log.d(TAG, "Loading profile image from local file: ${localImageFile.absolutePath}")
                localImagePath = localImageFile.absolutePath
                selectedImageUri = Uri.fromFile(localImageFile)
                ImageUploadService.loadImageIntoView(this, selectedImageUri, profileImageView)
            } else {
                Log.d(TAG, "No profile image available for user")
            }

            progressIndicator.visibility = View.GONE
            Log.d(TAG, "Finished loading user info into views")
        }
    }

    private fun pickImage() {
        ImagePicker.with(this)
            .cropSquare()            // Crop image as square
            .compress(512)           // Compress image size
            .maxResultSize(512, 512) // Maximum result size
            .createIntent { intent: Intent ->
                imagePickLauncher.launch(intent)
            }
    }

    private fun saveProfile() {
        val displayName = nameEditText.text.toString().trim()
        val status = statusEditText.text.toString().trim()
        val description = descriptionEditText.text.toString().trim()

        if (displayName.isEmpty()) {
            nameEditText.error = "Name is required"
            return
        }

        // Don't proceed if image is still uploading
        if (ImageUploadService.isUploadInProgress()) {
            Toast.makeText(this, "Please wait for image upload to complete", Toast.LENGTH_SHORT).show()
            return
        }

        progressIndicator.visibility = View.VISIBLE
        saveButton.visibility = View.GONE
        Toast.makeText(this, "Saving profile...", Toast.LENGTH_SHORT).show()

        val data = hashMapOf<String, Any>(
            "displayName" to displayName,
            "userStatus" to status,
            "userDescription" to description,
            "profilePictureUrl" to (profilePictureUrl ?: "")
        )

        FirebaseService.updateUserinFirebase(userId, data) {
            if (it) {
                // Update in chat list
                updateUserInChatsList(displayName)
            }
        }

        val flag = LocalStorageService.updateUserToLocalStorage(
            displayName,
            status,
            description,
            userId,
            localImagePath.toString(),
            profilePictureUrl.toString(),
            phoneTextView.text.toString(),
        )

        if (flag) {
            // Also update in chat list if applicable
            updateUserInChatsList(displayName)
            finalizeProfileSave()
        } else {
            progressIndicator.visibility = View.GONE
            saveButton.visibility = View.VISIBLE
        }
    }

    private fun navigateBack() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        finish()
    }

    override fun onBackPressed() {
        navigateBack()
    }

    private fun updateUserInChatsList(displayName: String) {
        try {
            // Update user's display name in the users.json file
            val usersFile = File(filesDir, "users.json")
            if (usersFile.exists()) {
                val content = usersFile.readText()
                if (content.isNotEmpty()) {
                    val jsonObject = JSONObject(content)
                    jsonObject.put(userId, displayName)
                    usersFile.writeText(jsonObject.toString())
                    Log.d(TAG, "Updated user display name in users.json")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user in chats list", e)
        }
    }

    private fun finalizeProfileSave() {
        Toast.makeText(this, "✅ Profile updated", Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({
            navigateBack()
        }, 1200) // 1.2 seconds delay so the Toast shows before closing
    }
}