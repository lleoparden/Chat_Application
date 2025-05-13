package com.example.chat_application

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.chat_application.dataclasses.UserSettings
import com.example.chat_application.services.FirebaseService
import com.example.chat_application.services.ImageUploadService
import com.example.chat_application.services.LocalStorageService
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.util.regex.Pattern

private const val TAG = "AccountSettingsActivity"

class AccountSettingsActivity : AppCompatActivity() {
    private lateinit var profileImageView: ImageView
    private lateinit var nameEditText: TextInputEditText
    private lateinit var phoneEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var readReceiptsSwitch: SwitchMaterial
    private lateinit var saveButton: TextView
    private lateinit var progressIndicator: ProgressBar
    private lateinit var imageUploadProgressBar: ProgressBar
    private val firebaseEnabled by lazy { resources.getBoolean(R.bool.firebaseOn) }
    private var selectedImageUri: Uri? = null
    private var profilePictureUrl: String? = null
    private var localImagePath: String? = null
    private var userId = ""
    private lateinit var imagePickLauncher: androidx.activity.result.ActivityResultLauncher<Intent>

    companion object {
        private const val PHONE_PATTERN = "^\\+?[1-9]\\d{1,14}\$"
        private const val MIN_PASSWORD_LENGTH = 8 // Updated to match validation requirement
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.getThemeResource())
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.account_settings)
        initializeViews()
        setupImagePickerLauncher()
        LocalStorageService.initialize(this, ContentValues.TAG)
        if (firebaseEnabled) {
            FirebaseService.initialize(this, TAG, firebaseEnabled)
        }
        userId = UserSettings.userId

        // Show authentication dialog before displaying any data
        showAuthenticationDialog()
    }

    private fun showAuthenticationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.authentication_dialog, null)
        val passwordInput = dialogView.findViewById<EditText>(R.id.password_input)
        val verifyButton = dialogView.findViewById<Button>(R.id.verify_button)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)

        // Create dialog without buttons (we'll use the ones from XML)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Verify It's You")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Set up button click listeners
        verifyButton.setOnClickListener {
            val password = passwordInput.text.toString().trim()
            dialog.dismiss()
            verifyPassword(password)
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
            finish() // If user cancels, go back to previous screen
        }

        dialog.show()
    }

    private fun verifyPassword(password: String) {
        progressIndicator.visibility = View.VISIBLE

        // Get stored password from Firebase or local storage for verification
        FirebaseService.loadUserFromFirebase(userId) { user ->
            progressIndicator.visibility = View.GONE

            if (user.password == password) {
                // Password correct, proceed with initialization
                setupClickListeners()
                loadUserData()
            } else {
                // Password incorrect, show error and go back
                Toast.makeText(this, "Incorrect password. Please try again.", Toast.LENGTH_LONG).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    finish()
                }, 1000)
            }
        }
    }

    private fun initializeViews() {
        profileImageView = findViewById(R.id.profileImageView)
        nameEditText = findViewById(R.id.nameEditText)
        phoneEditText = findViewById(R.id.phoneEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        readReceiptsSwitch = findViewById(R.id.readReceiptsSwitch)
        saveButton = findViewById(R.id.saveButton)
        progressIndicator = findViewById(R.id.progressIndicator)
        imageUploadProgressBar = findViewById(R.id.imageUploadProgressBar)
        imageUploadProgressBar.visibility = View.GONE

        // Add text change listener for password validation
        passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                validatePassword(s.toString(), passwordEditText)
            }
        })
    }

    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.editImageButton).setOnClickListener { pickImage() }
        profileImageView.setOnClickListener { pickImage() }
        saveButton.setOnClickListener { saveProfile() }
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { navigateBack() }
    }

    private fun setupImagePickerLauncher() {
        imagePickLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                if (data != null && data.data != null) {
                    selectedImageUri = data.data
                    ImageUploadService.loadImageIntoView(this, selectedImageUri, profileImageView)
                    localImagePath = ImageUploadService.saveImageLocally(this, selectedImageUri!!, userId)
                    ImageUploadService.uploadImageToImgbb(
                        this,
                        selectedImageUri!!,
                        imageUploadProgressBar,
                        object : ImageUploadService.ImageUploadCallback {
                            override fun onUploadSuccess(imageUrl: String) {
                                profilePictureUrl = imageUrl
                            }
                            override fun onUploadFailure(errorMessage: String) {
                                Toast.makeText(this@AccountSettingsActivity, errorMessage, Toast.LENGTH_SHORT).show()
                            }
                            override fun onUploadProgress(isUploading: Boolean) {}
                        }
                    )
                }
            }
        }
    }

    private fun loadUserData() {
        progressIndicator.visibility = View.VISIBLE
        FirebaseService.loadUserFromFirebase(userId) { user ->
            nameEditText.setText(user.displayName)
            phoneEditText.setText(user.phoneNumber)
            readReceiptsSwitch.isChecked = UserSettings.readReceipts
            passwordEditText.setText(user.password) // Show the user's password instead of clearing it
            val localImageFile = File(filesDir, "profile_${userId}.jpg")
            val imageUrl = user.profilePictureUrl
            if (imageUrl.isNotEmpty()) {
                profilePictureUrl = imageUrl
                HelperFunctions.loadImageFromUrl(imageUrl, profileImageView)
                ImageUploadService.downloadAndSaveImageLocally(this, imageUrl, userId)
            } else if (localImageFile.exists()) {
                localImagePath = localImageFile.absolutePath
                selectedImageUri = Uri.fromFile(localImageFile)
                ImageUploadService.loadImageIntoView(this, selectedImageUri, profileImageView)
            }
            progressIndicator.visibility = View.GONE
        }
    }

    private fun pickImage() {
        ImagePicker.with(this)
            .cropSquare()
            .compress(512)
            .maxResultSize(512, 512)
            .createIntent { intent ->
                imagePickLauncher.launch(intent)
            }
    }

    private fun saveProfile() {
        val name = nameEditText.text.toString().trim()
        val phone = phoneEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        val readReceipts = readReceiptsSwitch.isChecked

        UserSettings.readReceipts = readReceipts
        UserSettings.saveSettings(this)

        if (!validateInputs(name, phone, password)) return
        if (ImageUploadService.isUploadInProgress()) {
            Toast.makeText(this, "Please wait for image upload to complete", Toast.LENGTH_SHORT).show()
            return
        }

        progressIndicator.visibility = View.VISIBLE
        saveButton.visibility = View.GONE
        Toast.makeText(this, "Saving settings...", Toast.LENGTH_SHORT).show()

        val user = hashMapOf<String, Any>(
            "displayName" to name,
            "phoneNumber" to phone,
            "password" to password
        )

        FirebaseService.updateUserinFirebase(userId, user) {
            if(it){
                progressIndicator.visibility = View.GONE
                Log.d(TAG, "Password updated successfully in Firebase")
                Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                proceedWithProfileSave(name, phone, readReceipts)
            }else{
                progressIndicator.visibility = View.GONE
                Log.d(TAG, "couldn't update Password in Firebase")
                Toast.makeText(this, "couldn't Password update", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun proceedWithProfileSave(name: String, phone: String, readReceipts: Boolean) {
        val data = hashMapOf<String, Any>(
            "displayName" to name,
            "phoneNumber" to phone,
            "readReceipts" to readReceipts,
            "profilePictureUrl" to (profilePictureUrl ?: ""),
            "password" to passwordEditText.text.toString().trim() // Add password to data being updated
        )

        FirebaseService.updateUserinFirebase(userId, data) { success ->
            if (success) {
                updateUserInChatsList(name)
            }
        }

        val flag = LocalStorageService.updateUserToLocalStorage(
            name,
            "",
            passwordEditText.text.toString().trim(), // Add password to local storage update
            userId,
            localImagePath.toString(),
            profilePictureUrl.toString(),
            phone
        )

        if (flag) {
            updateUserInChatsList(name)
            finalizeProfileSave()
        } else {
            progressIndicator.visibility = View.GONE
            saveButton.visibility = View.VISIBLE
            Toast.makeText(this, "Failed to save locally", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateInputs(name: String, phone: String, password: String): Boolean {
        var isValid = true
        if (name.isEmpty()) {
            nameEditText.error = "Name is required"
            nameEditText.announceForAccessibility("Name is required")
            isValid = false
        }
        if (phone.isEmpty()) {
            phoneEditText.error = "Phone number is required"
            phoneEditText.announceForAccessibility("Phone number is required")
            isValid = false
        } else if (!Pattern.matches(PHONE_PATTERN, phone)) {
            phoneEditText.error = "Invalid phone number"
            phoneEditText.announceForAccessibility("Invalid phone number")
            isValid = false
        }

        // Use the enhanced password validation
        if (password.isNotEmpty() && !isValidPassword(password)) {
            // Error message already set by validatePassword()
            isValid = false
        }

        return isValid
    }

    private fun isValidPassword(password: String): Boolean {
        if (password.length < 8) return false

        val hasUppercase = password.any { it.isUpperCase() }
        val hasSpecialChar = password.any { !it.isLetterOrDigit() }

        return hasUppercase && hasSpecialChar
    }

    private fun validatePassword(password: String, passwordField: EditText) {
        if (password.isEmpty()) {
            passwordField.error = null
            return
        }

        when {
            password.length < 8 -> {
                passwordField.error = "Password must be at least 8 characters"
            }
            !password.any { it.isUpperCase() } -> {
                passwordField.error = "Password must include uppercase letters"
            }
            !password.any { !it.isLetterOrDigit() } -> {
                passwordField.error = "Password must include special characters"
            }
            else -> {
                passwordField.error = null
            }
        }
    }

    private fun navigateBack() {
        startActivity(Intent(this, SettingsActivity::class.java))
        finish()
    }

    private fun updateUserInChatsList(displayName: String) {
        try {
            val usersFile = File(filesDir, "users.json")
            if (usersFile.exists()) {
                val content = usersFile.readText()
                if (content.isNotEmpty()) {
                    val jsonObject = org.json.JSONObject(content)
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
        Toast.makeText(this, "✅ Settings updated", Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({
            navigateBack()
        }, 1200)
    }

    override fun onDestroy() {
        super.onDestroy()
        FirebaseService.cleanup()
    }
}