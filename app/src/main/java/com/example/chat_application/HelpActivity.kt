package com.example.chat_application

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.chat_application.dataclasses.UserSettings
import com.example.chat_application.services.ImageUploadService
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore

class HelpActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "HelpActivity"
    }

    // UI Components
    private lateinit var nameEditText: TextInputEditText
    private lateinit var emailEditText: TextInputEditText
    private lateinit var messageEditText: TextInputEditText
    private lateinit var selectedImageView: ImageView
    private lateinit var removeImageButton: ImageButton
    private lateinit var pickImageButton: Button
    private lateinit var submitSupportButton: Button
    private lateinit var uploadProgressBar: ProgressBar

    // Firebase
    private val firestore = FirebaseFirestore.getInstance()

    // Image URI
    private var selectedImageUri: Uri? = null

    // Image picker launcher
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            displaySelectedImage(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.getThemeResource())
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.help)

        // Initialize UI components
        initializeViews()
        setupListeners()

        // Pre-fill user data if available
        prefillUserData()
    }

    private fun initializeViews() {
        // Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // Form fields
        nameEditText = findViewById(R.id.nameEditText)
        emailEditText = findViewById(R.id.emailEditText)
        messageEditText = findViewById(R.id.messageEditText)

        // Image related views
        selectedImageView = findViewById(R.id.selectedImageView)
        removeImageButton = findViewById(R.id.removeImageButton)
        pickImageButton = findViewById(R.id.pickImageButton)

        // Submit button and progress bar
        submitSupportButton = findViewById(R.id.submitSupportButton)
        uploadProgressBar = findViewById(R.id.uploadProgressBar)
    }

    private fun setupListeners() {
        // Back button
        findViewById<Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        }

        // Pick image button
        pickImageButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Remove image button
        removeImageButton.setOnClickListener {
            clearSelectedImage()
        }

        // Submit button
        submitSupportButton.setOnClickListener {
            if (validateForm()) {
                submitSupportRequest()
            }
        }
    }

    private fun prefillUserData() {

        HelperFunctions.getUserData(UserSettings.userId) {user ->
            if (user != null) {
                nameEditText.setText(user.displayName)
            }
        }
    }

    private fun displaySelectedImage(uri: Uri) {
        // Display the selected image
        selectedImageView.visibility = View.VISIBLE
        removeImageButton.visibility = View.VISIBLE

        // Load image into the ImageView
        ImageUploadService.loadImageIntoView(this, uri, selectedImageView)
    }

    private fun clearSelectedImage() {
        // Clear the selected image
        selectedImageUri = null
        selectedImageView.visibility = View.GONE
        removeImageButton.visibility = View.GONE
    }

    private fun validateForm(): Boolean {
        var isValid = true

        // Validate name
        if (TextUtils.isEmpty(nameEditText.text)) {
            nameEditText.error = "Name is required"
            isValid = false
        }

        // Validate email
        val email = emailEditText.text.toString()
        if (TextUtils.isEmpty(email)) {
            emailEditText.error = "Email is required"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Please enter a valid email address"
            isValid = false
        }

        // Validate message
        if (TextUtils.isEmpty(messageEditText.text)) {
            messageEditText.error = "Please describe your issue"
            isValid = false
        }

        return isValid
    }

    private fun submitSupportRequest() {
        // Show progress and disable submit button
        uploadProgressBar.visibility = View.VISIBLE
        submitSupportButton.isEnabled = false

        // Get form data
        val name = nameEditText.text.toString()
        val email = emailEditText.text.toString()
        val message = messageEditText.text.toString()
        val userId = UserSettings.userId

        // Create support request data
        val supportData: HashMap<String, Any> = hashMapOf(
            "userId" to userId,
            "name" to name,
            "email" to email,
            "message" to message,
            "timestamp" to System.currentTimeMillis(),
            "resolved" to false
        )


        // If an image was selected, upload it first
        if (selectedImageUri != null) {
            uploadImageAndCreateSupportRequest(supportData)
        } else {
            // No image, create support request directly
            createSupportRequest(supportData)
        }
    }

    private fun uploadImageAndCreateSupportRequest(supportData: HashMap<String, Any>) {
        val uri = selectedImageUri ?: return

        // Use the ImageUploadService to upload the image
        ImageUploadService.uploadImageToImgbb(
            context = this,
            imageUri = uri,
            progressBar = uploadProgressBar,
            callback = object : ImageUploadService.ImageUploadCallback {
                override fun onUploadSuccess(imageUrl: String) {
                    // Add image URL to support data
                    supportData["imageUrl"] = imageUrl
                    createSupportRequest(supportData)
                }

                override fun onUploadFailure(errorMessage: String) {
                    Log.e(TAG, "Image upload failed: $errorMessage")
                    uploadProgressBar.visibility = View.GONE
                    submitSupportButton.isEnabled = true
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        "Failed to upload image: $errorMessage",
                        Snackbar.LENGTH_LONG
                    ).show()
                }

                override fun onUploadProgress(isUploading: Boolean) {
                    // Progress is already handled by the progress bar
                }
            }
        )
    }

    private fun createSupportRequest(supportData: HashMap<String, Any>) {
        // Save to Firestore in 'support_requests' collection
        firestore.collection("support_requests")
            .add(supportData)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Support request created with ID: ${documentReference.id}")
                uploadProgressBar.visibility = View.GONE
                submitSupportButton.isEnabled = true
                showSuccessMessage()
                clearForm()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error creating support request", e)
                uploadProgressBar.visibility = View.GONE
                submitSupportButton.isEnabled = true
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "Failed to submit: ${e.message}",
                    Snackbar.LENGTH_LONG
                ).show()
            }
    }

    private fun showSuccessMessage() {
        Snackbar.make(
            findViewById(android.R.id.content),
            "Support request submitted successfully! We'll get back to you soon.",
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun clearForm() {
        // Clear form fields
        messageEditText.setText("")
        clearSelectedImage()
    }

    override fun onBackPressed() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        finish()
    }
}