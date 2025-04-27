package com.example.chat_application

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

private const val TAG = "EditProfileActivity"
private const val IMGBB_API_URL = "https://api.imgbb.com/1/upload"
private const val IMGBB_API_KEY = "38328309adada9acb189c19a81befaa6"

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
    private lateinit var db: FirebaseFirestore
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // State variables
    private var selectedImageUri: Uri? = null
    private var profilPictureUrl: String? = null
    private var localImagePath: String? = null
    private var userId = ""
    private var isUploadingImage = false
    private lateinit var imagePickLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.edit_profile)

        // Initialize UI components and setup launchers
        initializeViews()
        setupImagePickerLauncher()

        // Get current user ID
        userId = UserSettings.userId

        // Initialize Firebase if enabled
        if (firebaseEnabled) {
            db = FirebaseFirestore.getInstance()
        }

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
                    loadImageIntoView(selectedImageUri)

                    // Save image locally first
                    saveImageLocally(selectedImageUri!!)

                    // Also upload to ImgBB for online access
                    uploadImageToImgbb(selectedImageUri!!)
                }
            }
        }
    }

    private fun loadUserData() {
        progressIndicator.visibility = View.VISIBLE

        if (firebaseEnabled) {
            loadUserFromFirebase()
        } else {
            loadUserFromLocalStorage()
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

    private fun loadImageIntoView(uri: Uri?) {
        if (uri != null) {
            Glide.with(this)
                .load(uri)
                .apply(RequestOptions()
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .diskCacheStrategy(DiskCacheStrategy.NONE))
                .into(profileImageView)
        } else {
            // Set default image
            profileImageView.setImageResource(R.drawable.ic_person)
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
        if (isUploadingImage) {
            Toast.makeText(this, "Please wait for image upload to complete", Toast.LENGTH_SHORT).show()
            return
        }

        progressIndicator.visibility = View.VISIBLE
        saveButton.visibility = View.GONE
        Toast.makeText(this, "Saving profile...", Toast.LENGTH_SHORT).show()

        if (firebaseEnabled) {
            saveProfileToFirebase(displayName, status, description)
        } else {
            saveProfileToLocalStorage(displayName, status, description)
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

    // ---- Image Handling Methods ----

    private fun saveImageLocally(imageUri: Uri): String? {
        try {
            // Create file path for local storage
            val imageFile = File(filesDir, "profile_${userId}.jpg")

            // Copy image to app's private storage
            contentResolver.openInputStream(imageUri)?.use { input ->
                imageFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Save the local path
            localImagePath = imageFile.absolutePath
            Log.d(TAG, "Image saved locally: $localImagePath")
            return localImagePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image locally", e)
            return null
        }
    }

    // ---- Local Storage Methods ----

    private fun loadUserFromLocalStorage() {
        try {
            val localUsersFile = File(filesDir, "local_users.json")
            if (!localUsersFile.exists() || localUsersFile.readText().isBlank()) {
                Log.e(TAG, "Local Users File Not Found or Empty")
                Toast.makeText(this, "User Data Not Found", Toast.LENGTH_SHORT).show()
                progressIndicator.visibility = View.GONE
                return
            }

            val fileContent = localUsersFile.readText()
            if (fileContent.trim().startsWith("[")) {
                val jsonArray = JSONArray(fileContent)

                for (i in 0 until jsonArray.length()) {
                    val jsonUser = jsonArray.getJSONObject(i)
                    if (jsonUser.getString("uid") == userId) {
                        nameEditText.setText(jsonUser.getString("displayName"))
                        statusEditText.setText(jsonUser.optString("Status", ""))
                        descriptionEditText.setText(jsonUser.optString("description", ""))
                        phoneTextView.text = jsonUser.optString("phoneNumber", "N/A")

                        // Load profile image - first try local path
                        val profileImagePath = jsonUser.optString("profileImagePath", "")
                        if (profileImagePath.isNotEmpty()) {
                            val imageFile = File(profileImagePath)
                            if (imageFile.exists()) {
                                selectedImageUri = Uri.fromFile(imageFile)
                                localImagePath = profileImagePath
                                loadImageIntoView(selectedImageUri)
                            }
                        }

                        // Then try URL if local image failed or doesn't exist
                        if (selectedImageUri == null) {
                            val imageUrl = jsonUser.optString("profilPictureUrl", "")
                            if (imageUrl.isNotEmpty()) {
                                profilPictureUrl = imageUrl
                                globalFunctions.loadImageFromUrl(imageUrl,profileImageView)
                            }
                        }

                        progressIndicator.visibility = View.GONE
                        return
                    }
                }
            }
            Log.e(TAG, "User Not Found In Local Storage")
            Toast.makeText(this, "User Not Found locally", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error Reading Local Users File", e)
            Toast.makeText(this, "Error Loading User Data", Toast.LENGTH_SHORT).show()
        } finally {
            progressIndicator.visibility = View.GONE
        }
    }

    private fun saveProfileToLocalStorage(displayName: String, status: String, description: String) {
        try {
            val localUsersFile = File(filesDir, "local_users.json")
            val fileContent = if (localUsersFile.exists() && localUsersFile.readText().isNotBlank()) {
                localUsersFile.readText()
            } else {
                "[]"
            }

            val jsonArray = JSONArray(fileContent)
            var userFound = false

            // Update existing user or add new one
            for (i in 0 until jsonArray.length()) {
                val jsonUser = jsonArray.getJSONObject(i)
                if (jsonUser.getString("uid") == userId) {
                    // Update basic info
                    jsonUser.put("displayName", displayName)
                    jsonUser.put("Status", status)
                    jsonUser.put("description", description)

                    // Always save local image path if available
                    if (localImagePath != null) {
                        jsonUser.put("profileImagePath", localImagePath)
                    }

                    // Also save URL if available (as backup or for online access)
                    if (profilPictureUrl != null) {
                        jsonUser.put("profilPictureUrl", profilPictureUrl)
                    }

                    userFound = true
                    break
                }
            }

            // If user not found, add new user
            if (!userFound) {
                val newUser = JSONObject().apply {
                    put("uid", userId)
                    put("displayName", displayName)
                    put("Status", status)
                    put("description", description)
                    put("phoneNumber", phoneTextView.text.toString())

                    // Always save local image path if available
                    if (localImagePath != null) {
                        put("profileImagePath", localImagePath)
                    }

                    // Also save URL if available
                    if (profilPictureUrl != null) {
                        put("profilPictureUrl", profilPictureUrl)
                    }
                }
                jsonArray.put(newUser)
            }

            // Write updated JSON to file
            localUsersFile.writeText(jsonArray.toString())

            // Also update in chat list if applicable
            updateUserInChatsList(displayName)

            finalizeProfileSave()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving profile to local storage", e)
            Toast.makeText(this, "Failed to save profile", Toast.LENGTH_SHORT).show()
            progressIndicator.visibility = View.GONE
            saveButton.visibility = View.VISIBLE
        }
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

    // ---- Firebase Methods ----

    private fun loadUserFromFirebase() {
        if (!firebaseEnabled) {
            loadUserFromLocalStorage()
            return
        }

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    nameEditText.setText(document.getString("displayName") ?: "")
                    statusEditText.setText(document.getString("userStatus") ?: "")
                    descriptionEditText.setText(document.getString("userDescription") ?: "")
                    phoneTextView.text = document.getString("phoneNumber") ?: "N/A"

                    // Check for local image first - we may have saved it locally previously
                    val localImageFile = File(filesDir, "profile_${userId}.jpg")
                    if (localImageFile.exists()) {
                        localImagePath = localImageFile.absolutePath
                        selectedImageUri = Uri.fromFile(localImageFile)
                        loadImageIntoView(selectedImageUri)
                    }
                    // Otherwise, try to load from URL
                    else {
                        val imageUrl = document.getString("profilPictureUrl")
                        if (!imageUrl.isNullOrEmpty()) {
                            profilPictureUrl = imageUrl
                            globalFunctions.loadImageFromUrl(imageUrl,profileImageView)

                            // Try to download and save the image locally for next time
                            downloadAndSaveImageLocally(imageUrl)
                        }
                    }
                } else {
                    Log.d(TAG, "User not found in Firebase, trying local storage")
                    loadUserFromLocalStorage()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading user data from Firebase", e)
                Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
                loadUserFromLocalStorage() // Fallback to local
            }
            .addOnCompleteListener {
                progressIndicator.visibility = View.GONE
            }
    }

    private fun saveProfileToFirebase(displayName: String, status: String, description: String) {
        if (!firebaseEnabled) {
            saveProfileToLocalStorage(displayName, status, description)
            return
        }

        val data = hashMapOf<String, Any>(
            "displayName" to displayName,
            "userStatus" to status,
            "userDescription" to description
        )

        // Add image URL if available for online access
        if (profilPictureUrl != null) {
            data["profilPictureUrl"] = profilPictureUrl!!
        }

        db.collection("users").document(userId)
            .update(data)
            .addOnSuccessListener {
                // Update in chat list
                updateUserInChatsList(displayName)

                // Also make sure to save everything locally
                saveProfileToLocalStorage(displayName, status, description)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating profile in Firebase", e)
                Toast.makeText(this, "Failed to save profile online, saving locally", Toast.LENGTH_SHORT).show()
                // Still try to save locally even if Firebase failed
                saveProfileToLocalStorage(displayName, status, description)
            }
    }

    // ---- API Methods ----

    private fun uploadImageToImgbb(imageUri: Uri) {
        // Show upload progress
        imageUploadProgressBar.visibility = View.VISIBLE
        isUploadingImage = true
        Toast.makeText(this, "Uploading image to cloud...", Toast.LENGTH_SHORT).show()

        try {
            // Read image data
            val inputStream = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Convert bitmap to byte array
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
            val imageBytes = byteArrayOutputStream.toByteArray()
            val base64Image = Base64.getEncoder().encodeToString(imageBytes)

            // Create form data for ImgBB API
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("key", IMGBB_API_KEY)
                .addFormDataPart("image", base64Image)
                .build()

            // Create request
            val request = Request.Builder()
                .url(IMGBB_API_URL)
                .post(requestBody)
                .build()

            // Execute request asynchronously
            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Log.e(TAG, "Failed to upload image to ImgBB", e)
                        Toast.makeText(applicationContext, "Cloud image upload failed, but local copy saved", Toast.LENGTH_SHORT).show()
                        imageUploadProgressBar.visibility = View.GONE
                        isUploadingImage = false
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val responseBody = response.body?.string()
                        val jsonResponse = JSONObject(responseBody)

                        if (jsonResponse.getBoolean("success")) {
                            val data = jsonResponse.getJSONObject("data")
                            val imageUrl = data.getString("url")

                            runOnUiThread {
                                profilPictureUrl = imageUrl
                                Toast.makeText(applicationContext, "Image uploaded to cloud successfully!", Toast.LENGTH_SHORT).show()
                                imageUploadProgressBar.visibility = View.GONE
                                isUploadingImage = false
                            }
                        } else {
                            runOnUiThread {
                                Toast.makeText(applicationContext, "Cloud upload failed: " + jsonResponse.optString("error", "Unknown error"), Toast.LENGTH_SHORT).show()
                                imageUploadProgressBar.visibility = View.GONE
                                isUploadingImage = false
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Log.e(TAG, "Error parsing ImgBB response", e)
                            Toast.makeText(applicationContext, "Failed to process uploaded image", Toast.LENGTH_SHORT).show()
                            imageUploadProgressBar.visibility = View.GONE
                            isUploadingImage = false
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing image for upload", e)
            Toast.makeText(this, "Failed to upload to cloud, but local copy saved", Toast.LENGTH_SHORT).show()
            imageUploadProgressBar.visibility = View.GONE
            isUploadingImage = false
        }
    }

    private fun downloadAndSaveImageLocally(imageUrl: String) {
        Thread {
            try {
                // Create a request
                val request = Request.Builder()
                    .url(imageUrl)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val inputStream = response.body?.byteStream()
                        if (inputStream != null) {
                            // Save to local file
                            val imageFile = File(filesDir, "profile_${userId}.jpg")
                            imageFile.outputStream().use { output ->
                                inputStream.copyTo(output)
                            }

                            // Update local path
                            localImagePath = imageFile.absolutePath
                            Log.d(TAG, "Remote image downloaded and saved locally: $localImagePath")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download image from URL", e)
            }
        }.start()
    }

    // ---- Utility Methods ----

    private fun finalizeProfileSave() {
        Toast.makeText(this, "✅ Profile updated", Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({
            navigateBack()
        }, 1200) // 1.2 seconds delay so the Toast shows before closing
    }
}