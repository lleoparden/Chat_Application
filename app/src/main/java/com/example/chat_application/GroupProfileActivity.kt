package com.example.chat_application

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

private const val TAG = "GroupProfileActivity"

class GroupProfileActivity : AppCompatActivity() {

    // UI Components
    private lateinit var toolbar: Toolbar
    private lateinit var groupImage: ImageView
    private lateinit var editImageButton: ImageView
    private lateinit var groupNameText: TextView
    private lateinit var memberCountText: TextView
    private lateinit var groupDescriptionText: EditText
    private lateinit var saveDescriptionButton: Button
    private lateinit var membersRecyclerView: RecyclerView
    private lateinit var startChatButton: Button
    private lateinit var leaveGroupButton: Button

    // Firebase Components
    private lateinit var firestore: FirebaseFirestore
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var groupsReference: DatabaseReference
    private val firebaseEnabled by lazy { resources.getBoolean(R.bool.firebaseOn) }

    // HTTP Client for image uploads
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Data
    private lateinit var groupChat: Chat
    private lateinit var currentUserId: String
    private var groupMembers = mutableListOf<UserData>()
    private lateinit var memberAdapter: GroupMemberAdapter

    // Image handling
    private var selectedImageUri: Uri? = null
    private var groupPictureUrl: String? = null
    private var localImagePath: String? = null
    private var isUploadingImage = false
    private lateinit var imagePickLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.group_user_profile)

        // Initialize Firebase if enabled
        if (firebaseEnabled) {
            firestore = FirebaseFirestore.getInstance()
            firebaseDatabase = FirebaseDatabase.getInstance()
            groupsReference = firebaseDatabase.getReference("chats")
        }

        // Get current user ID
        currentUserId = UserSettings.userId

        // Get group chat from intent
        groupChat = intent.getParcelableExtra("CHAT_OBJECT") ?: run {
            Log.e(TAG, "No group chat object provided")
            Toast.makeText(this, "Error loading group", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Check if this is actually a group chat
        if (groupChat.type != "group") {
            Log.e(TAG, "Not a group chat: ${groupChat.id}")
            Toast.makeText(this, "Not a group chat", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupImagePickerLauncher()
        setupToolbar()
        populateGroupInfo()
        loadGroupMembers()
        setupButtons()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        groupImage = findViewById(R.id.profileImageView)
        editImageButton = findViewById(R.id.editImageButton)
        groupNameText = findViewById(R.id.groupNameText)
        memberCountText = findViewById(R.id.memberCountText)
        groupDescriptionText = findViewById(R.id.groupDescriptionText)
        saveDescriptionButton = findViewById(R.id.saveDescriptionButton)
        membersRecyclerView = findViewById(R.id.membersRecyclerView)
        startChatButton = findViewById(R.id.startChatButton)
        leaveGroupButton = findViewById(R.id.leaveGroupButton)


        // Setup RecyclerView
        membersRecyclerView.layoutManager = LinearLayoutManager(this)
        memberAdapter = GroupMemberAdapter(groupMembers) { user ->
            // Handle click on group member
            viewUserProfile(user)
        }
        membersRecyclerView.adapter = memberAdapter
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

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Group Profile"

        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun populateGroupInfo() {
        // Set group name
        groupNameText.text = groupChat.getEffectiveDisplayName()

        // Set member count
        val memberCount = groupChat.participantIds.size
        memberCountText.text = "$memberCount Members"

        // Load group image
        loadGroupImage()

        // Load group description
        loadGroupDescription()
    }

    private fun loadGroupImage() {
        // Check for local image first
        val localImageFile = File(filesDir, "group_${groupChat.id}.jpg")
        if (localImageFile.exists()) {
            localImagePath = localImageFile.absolutePath
            selectedImageUri = Uri.fromFile(localImageFile)
            loadImageIntoView(selectedImageUri)
        } else {
            // If no local image, try to load from Firebase/URL
            if (firebaseEnabled) {
                firestore.collection("groups").document(groupChat.id)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val imageUrl = document.getString("groupPictureUrl")
                            if (!imageUrl.isNullOrEmpty()) {
                                groupPictureUrl = imageUrl
                                globalFunctions.loadImageFromUrl(imageUrl, groupImage)

                                // Try to download and save the image locally for next time
                                downloadAndSaveImageLocally(imageUrl)
                            }
                        } else {
                            // Try to load from local storage JSON
                            loadGroupImageFromLocalStorage()
                        }
                    }
                    .addOnFailureListener {
                        loadGroupImageFromLocalStorage()
                    }
            } else {
                loadGroupImageFromLocalStorage()
            }
        }
    }

    private fun loadGroupImageFromLocalStorage() {
        try {
            val groupsFile = File(filesDir, "groups.json")
            if (!groupsFile.exists()) {
                return
            }

            val fileContent = groupsFile.readText()
            if (fileContent.isBlank()) {
                return
            }

            val jsonArray = JSONArray(fileContent)

            for (i in 0 until jsonArray.length()) {
                val jsonGroup = jsonArray.getJSONObject(i)
                if (jsonGroup.getString("id") == groupChat.id) {
                    val imageUrl = jsonGroup.optString("groupPictureUrl", "")
                    if (imageUrl.isNotEmpty()) {
                        groupPictureUrl = imageUrl
                        globalFunctions.loadImageFromUrl(imageUrl, groupImage)
                    }
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading local groups file", e)
        }
    }

    private fun loadImageIntoView(uri: Uri?) {
        if (uri != null) {
            Glide.with(this)
                .load(uri)
                .apply(RequestOptions()
                    .placeholder(R.drawable.ic_person) // Replace with your group placeholder icon
                    .error(R.drawable.ic_person)
                    .diskCacheStrategy(DiskCacheStrategy.NONE))
                .into(groupImage)
        } else {
            // Set default image
            groupImage.setImageResource(R.drawable.ic_person)
        }
    }

    private fun loadGroupDescription() {
        if (firebaseEnabled) {
            loadGroupDescriptionFromFirebase()
        } else {
            loadGroupDescriptionFromLocalStorage()
        }
    }

    private fun loadGroupDescriptionFromFirebase() {
        firestore.collection("groups").document(groupChat.id)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Update UI with group description
                    val description = document.getString("description") ?: ""
                    groupDescriptionText.setText(description)
                } else {
                    Log.d(TAG, "Group details not found in Firestore")
                    groupDescriptionText.setText("")
                    loadGroupDescriptionFromLocalStorage()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading group details", e)
                Toast.makeText(this, "Failed to load group details", Toast.LENGTH_SHORT).show()
                groupDescriptionText.setText("")
                loadGroupDescriptionFromLocalStorage()
            }
    }

    private fun loadGroupDescriptionFromLocalStorage() {
        try {
            val groupsFile = File(filesDir, "groups.json")
            if (!groupsFile.exists()) {
                Log.d(TAG, "Local groups file not found")
                return
            }

            val fileContent = groupsFile.readText()
            if (fileContent.isBlank()) {
                Log.d(TAG, "Local groups file is empty")
                return
            }

            val jsonArray = JSONArray(fileContent)

            for (i in 0 until jsonArray.length()) {
                val jsonGroup = jsonArray.getJSONObject(i)
                if (jsonGroup.getString("id") == groupChat.id) {
                    val description = jsonGroup.optString("description", "")
                    groupDescriptionText.setText(description)
                    return
                }
            }

            Log.d(TAG, "Group not found in local storage")

        } catch (e: Exception) {
            Log.e(TAG, "Error reading local groups file", e)
        }
    }

    private fun saveGroupDescription() {
        val description = groupDescriptionText.text.toString().trim()

        if (firebaseEnabled) {
            saveGroupDetailsToFirebase(description)
        } else {
            saveGroupDetailsToLocalStorage(description)
        }
    }

    private fun saveGroupDetailsToFirebase(description: String) {
        // Create a map that includes description, group name, and image URL if available
        val groupData = hashMapOf(
            "description" to description,
            "name" to groupChat.getEffectiveDisplayName()
        )

        // Add image URL if available
        if (groupPictureUrl != null) {
            groupData["groupPictureUrl"] = groupPictureUrl!!
        }

        firestore.collection("groups").document(groupChat.id)
            .update(groupData as Map<String, Any>)
            .addOnSuccessListener {
                Log.d(TAG, "Group details updated successfully in Firestore")
                Toast.makeText(this, "Group details saved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating group details in Firestore", e)

                // If document doesn't exist yet, create it
                firestore.collection("groups").document(groupChat.id)
                    .set(groupData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Group details created successfully in Firestore")
                        Toast.makeText(this, "Group details saved", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e2 ->
                        Log.e(TAG, "Error creating group details in Firestore", e2)
                        Toast.makeText(this, "Failed to save group details", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun saveGroupDetailsToLocalStorage(description: String) {
        try {
            val groupsFile = File(filesDir, "groups.json")
            var jsonArray = JSONArray()
            var groupFound = false

            if (groupsFile.exists()) {
                val fileContent = groupsFile.readText()
                if (fileContent.isNotEmpty()) {
                    jsonArray = JSONArray(fileContent)

                    // Check if group exists and update description
                    for (i in 0 until jsonArray.length()) {
                        val jsonGroup = jsonArray.getJSONObject(i)
                        if (jsonGroup.getString("id") == groupChat.id) {
                            jsonGroup.put("description", description)

                            // Add image URL if available
                            if (groupPictureUrl != null) {
                                jsonGroup.put("groupPictureUrl", groupPictureUrl)
                            }

                            groupFound = true
                            break
                        }
                    }
                }
            }

            // If group not found, create new entry
            if (!groupFound) {
                val jsonGroup = JSONObject()
                jsonGroup.put("id", groupChat.id)
                jsonGroup.put("description", description)

                // Add image URL if available
                if (groupPictureUrl != null) {
                    jsonGroup.put("groupPictureUrl", groupPictureUrl)
                }

                jsonArray.put(jsonGroup)
            }

            // Write updated array back to file
            groupsFile.writeText(jsonArray.toString())
            Toast.makeText(this, "Details saved", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "Error saving group details to local storage", e)
            Toast.makeText(this, "Failed to save details", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageLocally(imageUri: Uri): String? {
        try {
            // Create file path for local storage
            val imageFile = File(filesDir, "group_${groupChat.id}.jpg")

            // Copy image to app's private storage
            contentResolver.openInputStream(imageUri)?.use { input ->
                imageFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Save the local path
            localImagePath = imageFile.absolutePath
            Log.d(TAG, "Group image saved locally: $localImagePath")
            return localImagePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving group image locally", e)
            return null
        }
    }

    private fun uploadImageToImgbb(imageUri: Uri) {
        // Show upload progress

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
                .url(globalFunctions.IMGBB_API_URL)
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
                                groupPictureUrl = imageUrl
                                Toast.makeText(applicationContext, "Image uploaded to cloud successfully!", Toast.LENGTH_SHORT).show()

                                // Save the group details with this new image URL
                                saveGroupDescription()


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
                            val imageFile = File(filesDir, "group_${groupChat.id}.jpg")
                            imageFile.outputStream().use { output ->
                                inputStream.copyTo(output)
                            }

                            // Update local path
                            localImagePath = imageFile.absolutePath
                            Log.d(TAG, "Remote group image downloaded and saved locally: $localImagePath")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download group image from URL", e)
            }
        }.start()
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

    private fun loadGroupMembers() {
        groupMembers.clear()

        // For each participant ID in the group
        for (participantId in groupChat.participantIds) {
            loadGroupMember(participantId)
        }
    }

    private fun loadGroupMember(userId: String) {
        if (firebaseEnabled) {
            firestore.collection("users").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val user = UserData(
                            uid = userId,
                            displayName = document.getString("displayName") ?: userId,
                            phoneNumber = document.getString("phoneNumber") ?: "",
                            profilePictureUrl = document.getString("profilePictureUrl") ?: ""
                        )
                        addMemberToList(user)
                    } else {
                        // If not found in Firebase, add a basic user
                        addMemberToList(UserData(uid = userId, displayName = userId))
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error loading user data", e)
                    // Add a basic user on failure
                    addMemberToList(UserData(uid = userId, displayName = userId))
                }
        } else {
            // Try to get from local storage
            try {
                val usersFile = File(filesDir, "local_users.json")
                if (usersFile.exists()) {
                    val fileContent = usersFile.readText()
                    val jsonArray = JSONArray(fileContent)

                    for (i in 0 until jsonArray.length()) {
                        val jsonUser = jsonArray.getJSONObject(i)
                        if (jsonUser.getString("uid") == userId) {
                            val user = UserData(
                                uid = userId,
                                displayName = jsonUser.getString("displayName"),
                                phoneNumber = jsonUser.getString("phoneNumber"),
                                profilePictureUrl = jsonUser.optString("profilePictureUrl", "")
                            )
                            addMemberToList(user)
                            return
                        }
                    }
                }

                // If not found, add basic user
                addMemberToList(UserData(uid = userId, displayName = userId))

            } catch (e: Exception) {
                Log.e(TAG, "Error reading local users file", e)
                addMemberToList(UserData(uid = userId, displayName = userId))
            }
        }
    }

    private fun addMemberToList(user: UserData) {
        if (!groupMembers.any { it.uid == user.uid }) {
            groupMembers.add(user)

            // Update the adapter
            runOnUiThread {
                memberAdapter.notifyDataSetChanged()
                // Update member count
                memberCountText.text = "${groupMembers.size} Members"
            }
        }
    }

    private fun setupButtons() {
        // Group image click handlers
        editImageButton.setOnClickListener { pickImage() }
        groupImage.setOnClickListener { pickImage() }

        saveDescriptionButton.setOnClickListener {
            saveGroupDescription()
        }

        startChatButton.setOnClickListener {
            // Open the group chat screen
            val intent = Intent(this, ChatRoomActivity::class.java).apply {
                putExtra("CHAT_OBJECT", groupChat)
            }
            startActivity(intent)
            finish()
        }

        leaveGroupButton.setOnClickListener {
            confirmLeaveGroup()
        }
    }

    private fun confirmLeaveGroup() {
        AlertDialog.Builder(this)
            .setTitle("Leave Group")
            .setMessage("Are you sure you want to leave this group?")
            .setPositiveButton("Yes") { _, _ ->
                leaveGroup()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun leaveGroup() {
        // Remove current user from participants
        if (groupChat.participantIds.contains(currentUserId)) {
            groupChat.participantIds.remove(currentUserId)

            // Update local storage
            updateGroupInLocalStorage()

            // Update Firebase if enabled
            if (firebaseEnabled) {
                updateGroupInFirebase()
            }

            Toast.makeText(this, "You have left the group", Toast.LENGTH_SHORT).show()

            // Return to main activity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun updateGroupInLocalStorage() {
        try {
            val chatsFile = File(filesDir, "chats.json")
            if (!chatsFile.exists()) {
                Log.e(TAG, "Chats file not found")
                return
            }

            val fileContent = chatsFile.readText()
            val jsonArray = JSONArray(fileContent)
            val updatedArray = JSONArray()

            // Update or remove the group in the local storage
            for (i in 0 until jsonArray.length()) {
                val jsonChat = jsonArray.getJSONObject(i)

                if (jsonChat.getString("id") == groupChat.id) {
                    // If there are still participants, update the chat
                    if (groupChat.participantIds.isNotEmpty()) {
                        val updatedChat = JSONObject().apply {
                            put("id", groupChat.id)
                            put("name", groupChat.name)
                            put("displayName", groupChat.displayName)
                            put("lastMessage", groupChat.lastMessage)
                            put("timestamp", groupChat.timestamp)
                            put("unreadCount", groupChat.unreadCount)

                            // Create a JSONArray for participantIds
                            val participantIdsArray = JSONArray()
                            for (participantId in groupChat.participantIds) {
                                participantIdsArray.put(participantId)
                            }
                            put("participantIds", participantIdsArray)
                            put("type", groupChat.type)
                        }
                        updatedArray.put(updatedChat)
                    }
                    // If no participants, the chat is removed by not adding it to updatedArray
                } else {
                    // Keep other chats unchanged
                    updatedArray.put(jsonChat)
                }
            }

            // Write updated chats back to file
            chatsFile.writeText(updatedArray.toString())

        } catch (e: Exception) {
            Log.e(TAG, "Error updating group in local storage", e)
        }
    }

    private fun updateGroupInFirebase() {
        if (groupChat.participantIds.isEmpty()) {
            // If no participants left, remove the chat
            groupsReference.child(groupChat.id).removeValue()
                .addOnSuccessListener {
                    Log.d(TAG, "Group removed from Firebase successfully")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error removing group from Firebase", e)
                }
        } else {
            // Update the chat with new participant list
            groupsReference.child(groupChat.id).setValue(groupChat)
                .addOnSuccessListener {
                    Log.d(TAG, "Group updated in Firebase successfully")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error updating group in Firebase", e)
                }
        }
    }

    private fun viewUserProfile(user: UserData) {
        val intent = Intent(this, UserProfileActivity::class.java).apply {
            putExtra("USER_ID", user.uid)
            putExtra("came_from", "GroupProfile")
            putExtra("CHAT_OBJECT", groupChat)
        }
        startActivity(intent)
    }

    override fun onBackPressed() {
        val sourceActivity = intent.getStringExtra("came_from")

        if (sourceActivity == "ChatRoom") {
            val intent = Intent(this, ChatRoomActivity::class.java).apply {
                putExtra("CHAT_OBJECT", groupChat)
            }
            startActivity(intent)
            overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        } else {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        }

        finish()
    }
}

// Adapter for the group members RecyclerView
class GroupMemberAdapter(
    private val members: List<UserData>,
    private val onItemClick: (UserData) -> Unit
) : RecyclerView.Adapter<GroupMemberAdapter.MemberViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_member, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]
        holder.bind(member)
    }

    override fun getItemCount(): Int = members.size

    inner class MemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val memberName: TextView = itemView.findViewById(R.id.memberNameText)
        private val memberAvatar: ImageView = itemView.findViewById(R.id.memberAvatar)

        fun bind(user: UserData) {
            memberName.text = user.displayName

            // Load profile picture if available - using same approach as UserProfileActivity
            if (user.profilePictureUrl.isNotEmpty()) {
                globalFunctions.loadImageFromUrl(user.profilePictureUrl, memberAvatar)
            }

            itemView.setOnClickListener {
                onItemClick(user)
            }
        }
    }
}