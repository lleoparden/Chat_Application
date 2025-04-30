package com.example.chat_application

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

private const val TAG = "AddNewStoryActivity"

class AddNewStoryActivity : AppCompatActivity(){

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
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // State variables
    private var selectedImageUri: Uri? = null
    private var storyPictureUrl: String? = null
    private var localImagePath: String? = null
    private var userId = ""
    private var isUploadingImage = false
    private lateinit var imagePickLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
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
            seen = false,
            uploadedAt = System.currentTimeMillis().toString()
        )

        // Use the asynchronous version of getUserData with callback
        globalFunctions.getUserData(userId) { userData ->
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
                    loadImageIntoView(selectedImageUri)

                    // Save image locally first
                    saveImageLocally(selectedImageUri!!)

                    // Also upload to ImgBB for online access
                    uploadImageToImgbb(selectedImageUri!!)
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

    private fun loadImageIntoView(uri: Uri?) {
        if (uri != null) {
            Glide.with(this)
                .load(uri)
                .apply(
                    RequestOptions()
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .diskCacheStrategy(DiskCacheStrategy.NONE))
                .into(storyPreview)
        } else {
            // Set default image
            storyPreview.setImageResource(R.drawable.ic_person)
        }
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


    // ---- API Methods ----

    private fun uploadImageToImgbb(imageUri: Uri) {

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
                .addFormDataPart("key", globalFunctions.IMGBB_API_KEY)
                .addFormDataPart("image", base64Image)
                .build()

            // Create request
            val request = Request.Builder()
                .url( globalFunctions.IMGBB_API_URL)
                .post(requestBody)
                .build()

            // Execute request asynchronously
            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Log.e(TAG, "Failed to upload image to ImgBB", e)
                        Toast.makeText(applicationContext, "Cloud image upload failed, but local copy saved", Toast.LENGTH_SHORT).show()
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
                                storyPictureUrl = imageUrl
                                Toast.makeText(applicationContext, "Image uploaded to cloud successfully!", Toast.LENGTH_SHORT).show()
                                isUploadingImage = false
                            }
                        } else {
                            runOnUiThread {
                                Toast.makeText(applicationContext, "Cloud upload failed: " + jsonResponse.optString("error", "Unknown error"), Toast.LENGTH_SHORT).show()
                                isUploadingImage = false
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Log.e(TAG, "Error parsing ImgBB response", e)
                            Toast.makeText(applicationContext, "Failed to process uploaded image", Toast.LENGTH_SHORT).show()
                            isUploadingImage = false
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing image for upload", e)
            Toast.makeText(this, "Failed to upload to cloud, but local copy saved", Toast.LENGTH_SHORT).show()
            isUploadingImage = false
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
                put("seen", story.seen)

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

    /**
     * Updates the index of locally saved stories
     *
     * @param storyFileName The filename of the newly saved story
     */
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