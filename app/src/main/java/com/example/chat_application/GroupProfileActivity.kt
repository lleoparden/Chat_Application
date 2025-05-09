package com.example.chat_application

import android.app.Activity
import android.content.Intent
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
import com.example.chat_application.dataclasses.Chat
import com.example.chat_application.dataclasses.UserData
import com.example.chat_application.dataclasses.UserSettings
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.example.chat_application.services.ImageUploadService

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
    private lateinit var progressBar: ProgressBar

    // Firebase Components
    private lateinit var firestore: FirebaseFirestore
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var groupsReference: DatabaseReference
    private val firebaseEnabled by lazy { resources.getBoolean(R.bool.firebaseOn) }

    // Data
    private lateinit var groupChat: Chat
    private lateinit var currentUserId: String
    private var groupMembers = mutableListOf<UserData>()
    private lateinit var memberAdapter: GroupMemberAdapter

    // Image handling
    private var selectedImageUri: Uri? = null
    private var groupPictureUrl: String? = null
    private lateinit var imagePickLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.getThemeResource())
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
        progressBar = findViewById(R.id.imageUploadProgressBar) // Make sure to add this ProgressBar to your layout if not already present

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
                    ImageUploadService.loadImageIntoView(this, selectedImageUri, groupImage)

                    // Create an upload callback
                    val uploadCallback = object : ImageUploadService.ImageUploadCallback {
                        override fun onUploadSuccess(imageUrl: String) {
                            groupPictureUrl = imageUrl
                            Toast.makeText(applicationContext, "Image uploaded to cloud successfully!", Toast.LENGTH_SHORT).show()
                            // Save the group details with this new image URL
                            saveGroupDescription()
                        }

                        override fun onUploadFailure(errorMessage: String) {
                            Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_SHORT).show()
                        }

                        override fun onUploadProgress(isUploading: Boolean) {
                            progressBar.visibility = if (isUploading) View.VISIBLE else View.GONE
                        }
                    }

                    // Save image locally first - use the service
                    val localPath = ImageUploadService.saveImageLocally(this, selectedImageUri!!, groupChat.id, "group_")

                    // Also upload to ImgBB for online access
                    if (selectedImageUri != null) {
                        ImageUploadService.uploadImageToImgbb(this, selectedImageUri!!, progressBar, uploadCallback)
                    }
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
            selectedImageUri = Uri.fromFile(localImageFile)
            ImageUploadService.loadImageIntoView(this, selectedImageUri, groupImage)
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
                                HelperFunctions.loadImageFromUrl(imageUrl, groupImage)

                                // Try to download and save the image locally for next time
                                ImageUploadService.downloadAndSaveImageLocally(
                                    this,
                                    imageUrl,
                                    groupChat.id,
                                    "group_"
                                ) { success, localPath ->
                                    if (success) {
                                        Log.d(TAG, "Group image downloaded successfully to $localPath")
                                    }
                                }
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
                        HelperFunctions.loadImageFromUrl(imageUrl, groupImage)
                    }
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading local groups file", e)
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
        for (participantId in groupChat.participantIds.keys) {
            loadGroupMember(participantId)
        }
    }

    private fun loadGroupMember(userId: String) {
        // Try to get from local storage
            var userdata = HelperFunctions.loadUserById(userId, this)
            if (userdata != null) {
                addMemberToList(userdata)
                return
            }else{
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

            // Load profile picture if available
            if (user.profilePictureUrl.isNotEmpty()) {
                HelperFunctions.loadImageFromUrl(user.profilePictureUrl, memberAvatar)
            }

            itemView.setOnClickListener {
                onItemClick(user)
            }
        }
    }
}