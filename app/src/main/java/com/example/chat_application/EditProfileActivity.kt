package com.example.chat_application

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "EditProfileActivity"

class EditProfileActivity : AppCompatActivity() {
    private lateinit var profileImageView: ImageView
    private lateinit var nameEditText: TextInputEditText
    private lateinit var statusEditText: TextInputEditText
    private lateinit var descriptionEditText: TextInputEditText
    private lateinit var phoneTextView: TextView
    private lateinit var saveButton: TextView
    private lateinit var progressIndicator: ProgressBar

    private val firebaseEnabled by lazy { resources.getBoolean(R.bool.firebaseOn) }
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private var imageUri: Uri? = null
    private val pickImageRequest = 1
    private var userId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.edit_profile)

        // Initialize UI components
        initializeViews()

        // Get current user ID
        userId = UserSettings.userId

        // Initialize Firebase if enabled
        if (firebaseEnabled) {
            db = FirebaseFirestore.getInstance()
            storage = FirebaseStorage.getInstance()
        }

        // Set click listeners
        findViewById<ImageView>(R.id.editImageButton).setOnClickListener {
            pickImage()
        }

        saveButton.setOnClickListener {
            saveProfile()
        }

        // Set up toolbar back button
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            navigateBack()
        }

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
    }

    private fun loadUserData() {
        progressIndicator.visibility = View.VISIBLE

        if (firebaseEnabled) {
            loadUserFromFirebase()
        } else {
            loadUserFromLocalStorage()
        }
    }

    private fun loadUserFromFirebase() {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    nameEditText.setText(document.getString("displayName") ?: "")
                    statusEditText.setText(document.getString("userStatus") ?: "")
                    descriptionEditText.setText(document.getString("userDescription") ?: "")
                    phoneTextView.text = document.getString("phoneNumber") ?: "N/A"

                    val imageUrl = document.getString("profileImageUrl")
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this).load(imageUrl).into(profileImageView)
                    }
                } else {
                    Log.d(TAG, "User not found in Firebase, trying local storage")
                    loadUserFromLocalStorage()
                }
                progressIndicator.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading user data from Firebase", e)
                Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
                loadUserFromLocalStorage() // Fallback to local
                progressIndicator.visibility = View.GONE
            }
    }

    private fun loadUserFromLocalStorage() {
        try {
            val localUsersFile = File(filesDir, "local_users.json")
            if (!localUsersFile.exists()) {
                Log.e(TAG, "Local Users File Not Found")
                Toast.makeText(this, "User Data Not Found", Toast.LENGTH_SHORT).show()
                progressIndicator.visibility = View.GONE
                return
            }

            val fileContent = localUsersFile.readText()
            if (fileContent.isBlank()) {
                Log.e(TAG, "Local Users File Is Empty")
                progressIndicator.visibility = View.GONE
                return
            }

            if (fileContent.trim().startsWith("[")) {
                val jsonArray = JSONArray(fileContent)

                for (i in 0 until jsonArray.length()) {
                    val jsonUser = jsonArray.getJSONObject(i)
                    if (jsonUser.getString("uid") == userId) {
                        nameEditText.setText(jsonUser.getString("displayName"))
                        statusEditText.setText(jsonUser.getString("Status"))
                        descriptionEditText.setText(jsonUser.getString("description"))
                        phoneTextView.text = jsonUser.getString("phoneNumber")

                        // Load profile image if path is available
                        val profileImagePath = jsonUser.optString("profileImagePath", "")
                        if (profileImagePath.isNotEmpty()) {
                            val imageFile = File(profileImagePath)
                            if (imageFile.exists()) {
                                imageUri = Uri.fromFile(imageFile)
                                profileImageView.setImageURI(imageUri)
                            }
                        }

                        progressIndicator.visibility = View.GONE
                        return
                    }
                }
            }

            Log.e(TAG, "User Not Found In Local Storage")
            Toast.makeText(this, "User Not Found locally", Toast.LENGTH_SHORT).show()
            progressIndicator.visibility = View.GONE

        } catch (e: Exception) {
            Log.e(TAG, "Error Reading Local Users File", e)
            Toast.makeText(this, "Error Loading User Data", Toast.LENGTH_SHORT).show()
            progressIndicator.visibility = View.GONE
        }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, pickImageRequest)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickImageRequest && resultCode == Activity.RESULT_OK && data != null) {
            imageUri = data.data
            profileImageView.setImageURI(imageUri)
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

        progressIndicator.visibility = View.VISIBLE
        Toast.makeText(this, "Saving profile...", Toast.LENGTH_SHORT).show()

        if (firebaseEnabled) {
            saveProfileToFirebase(displayName, status, description)
        } else {
            saveProfileToLocalStorage(displayName, status, description)
        }
    }

    private fun saveProfileToFirebase(displayName: String, status: String, description: String) {
        val data = hashMapOf<String, Any>(
            "displayName" to displayName,
            "userStatus" to status,
            "userDescription" to description
        )

        if (imageUri != null) {
            val imageRef = storage.reference.child("profile_images/$userId.jpg")
            imageRef.putFile(imageUri!!)
                .continueWithTask { task ->
                    if (!task.isSuccessful) throw task.exception!!
                    imageRef.downloadUrl
                }.addOnSuccessListener { uri ->
                    data["profileImageUrl"] = uri.toString()
                    updateFirestore(data)
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Error uploading image", e)
                    Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
                    updateFirestore(data) // Continue without image update
                }
        } else {
            updateFirestore(data)
        }
    }

    private fun updateFirestore(data: HashMap<String, Any>) {
        db.collection("users").document(userId).update(data as Map<String, Any>)
            .addOnSuccessListener {
                Toast.makeText(this, "✅ Profile updated", Toast.LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    navigateBack()
                }, 1200) // 1.2 seconds delay so the Toast shows before closing
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating profile", e)
                Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
                progressIndicator.visibility = View.GONE
            }
    }

    private fun saveProfileToLocalStorage(displayName: String, status: String, description: String) {
        try {
            val localUsersFile = File(filesDir, "local_users.json")
            var fileContent = "[]"

            if (localUsersFile.exists()) {
                fileContent = localUsersFile.readText()
                if (fileContent.isBlank()) {
                    fileContent = "[]"
                }
            }

            val jsonArray = JSONArray(fileContent)
            var userFound = false

            // Update existing user or add new one
            for (i in 0 until jsonArray.length()) {
                val jsonUser = jsonArray.getJSONObject(i)
                if (jsonUser.getString("uid") == userId) {
                    jsonUser.put("displayName", displayName)
                    jsonUser.put("Status", status)
                    jsonUser.put("description", description)

                    // Save image if selected
                    if (imageUri != null) {
                        // Copy image to app's private storage
                        val imageFile = File(filesDir, "profile_${userId}.jpg")
                        contentResolver.openInputStream(imageUri!!)?.use { input ->
                            imageFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        jsonUser.put("profileImagePath", imageFile.absolutePath)
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

                    // Save image if selected
                    if (imageUri != null) {
                        // Copy image to app's private storage
                        val imageFile = File(filesDir, "profile_${userId}.jpg")
                        contentResolver.openInputStream(imageUri!!)?.use { input ->
                            imageFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        put("profileImagePath", imageFile.absolutePath)
                    }
                }
                jsonArray.put(newUser)
            }

            // Write updated JSON to file
            localUsersFile.writeText(jsonArray.toString())
            Toast.makeText(this, "✅ Profile updated", Toast.LENGTH_SHORT).show()

            // Also update in chat list if applicable
            updateUserInChatsList(displayName)

            Handler(Looper.getMainLooper()).postDelayed({
                navigateBack()
            }, 1200) // 1.2 seconds delay so the Toast shows before closing

        } catch (e: Exception) {
            Log.e(TAG, "Error saving profile to local storage", e)
            Toast.makeText(this, "Failed to save profile", Toast.LENGTH_SHORT).show()
            progressIndicator.visibility = View.GONE
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

    private fun navigateBack() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        finish()
    }
}