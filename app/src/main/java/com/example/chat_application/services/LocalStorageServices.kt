package com.example.chat_application.services

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.chat_application.dataclasses.*
import com.example.chat_application.ChatManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * Service responsible for handling local storage operations
 * including user data, sessions, and chat history.
 */
@SuppressLint("StaticFieldLeak")
object LocalStorageService {
    // Constants
    private const val CHATS_FILE = "chats.json"
    private const val USERS_FILE = "users.json"
    private const val USER_USERS_FILE = "local_user.json"
    private const val PREFS_NAME = "ChatAppPrefs"
    private const val USER_ID_KEY = "userId"
    private val STORIES_FILE = "local_stories.json"

    // Properties
    private lateinit var context: Context
    private lateinit var tag: String
    private lateinit var localUsersFile: File
    private lateinit var usersFile: File

    /**
     * Initializes the local storage service
     */
    fun initialize(context: Context, Tag: String) {
        LocalStorageService.context = context
        usersFile = File(context.filesDir, USERS_FILE)
        localUsersFile = File(context.filesDir, USER_USERS_FILE)
        tag = Tag

        if (!usersFile.exists()) {
            try {
                usersFile.createNewFile()
                saveUsersToJson(emptyList())
                Log.d(tag, "Created new local users file at: ${usersFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(tag, "Error creating local users file", e)
            }
        }
    }

    // ============================
    // User Data Management Methods
    // ============================

    /**
     * Retrieves all locally stored users
     */
    private fun getLocalUsers(): List<UserData> {
        if (!usersFile.exists() || usersFile.length() == 0L) {
            return emptyList()
        }

        return try {
            val fileContent = FileReader(usersFile).use { it.readText() }

            // If file is empty or not proper JSON array, return empty list
            if (fileContent.isBlank() || !fileContent.trim().startsWith("[")) {
                return emptyList()
            }

            val usersList = mutableListOf<UserData>()
            val jsonArray = JSONArray(fileContent)

            for (i in 0 until jsonArray.length()) {
                val jsonUser = jsonArray.getJSONObject(i)
                val user = UserData(
                    uid = jsonUser.getString("uid"),
                    displayName = jsonUser.getString("displayName"),
                    phoneNumber = jsonUser.getString("phoneNumber"),
                    password = jsonUser.getString("password"),
                    userDescription = jsonUser.optString("userDescription", ""),
                    userStatus = jsonUser.optString("userStatus", ""),
                    online = jsonUser.optBoolean("online", false),
                    lastSeen = jsonUser.optString("lastSeen", ""),
                    profilePictureUrl = jsonUser.optString("profilePictureUrl", "")
                )

                usersList.add(user)
            }

            usersList
        } catch (e: Exception) {
            Log.e(tag, "Error reading local users file", e)
            emptyList()
        }
    }

    /**
     * Saves a list of users to local storage
     */
    private fun saveUsersToJson(users: List<UserData>) {
        try {
            val jsonArray = JSONArray()

            users.forEach { userData ->
                val jsonUser = JSONObject().apply {
                    put("uid", userData.uid)
                    put("displayName", userData.displayName)
                    put("phoneNumber", userData.phoneNumber)
                    put("password", userData.password)
                    put("userDescription", userData.userDescription)
                    put("userStatus", userData.userStatus)
                    put("online", userData.online)
                    put("lastSeen", userData.lastSeen)
                    put("profilePictureUrl", userData.profilePictureUrl)
                }
                jsonArray.put(jsonUser)
            }

            FileWriter(usersFile).use { writer ->
                writer.write(jsonArray.toString())
            }
        } catch (e: Exception) {
            Log.e(tag, "Error saving users to local storage", e)
        }
    }

    /**
     * Saves a user to local storage
     */
    fun saveUserToLocalStorage(userData: UserData) {
        val users = getLocalUsers().toMutableList()

        // Check if user already exists
        val existingUserIndex = users.indexOfFirst { it.uid == userData.uid }
        if (existingUserIndex >= 0) {
            users[existingUserIndex] = userData
        } else {
            users.add(userData)
        }

        saveUsersToJson(users)
    }

    fun saveContactsAsUserToLocalStorage(usersData: List<UserData>) {
        Log.d(tag, "Starting saveContactsAsUserToLocalStorage with ${usersData.size} users")

        try {
            val jsonArray = JSONArray()
            Log.d(tag, "Created JSON array for storage")

            usersData.forEach { userData ->
                Log.d(tag, "Processing user for storage: ${userData.uid} (${userData.displayName})")
                val jsonUser = JSONObject().apply {
                    put("uid", userData.uid)
                    put("displayName", userData.displayName)
                    put("phoneNumber", userData.phoneNumber)
                    put("password", userData.password)
                    put("userDescription", userData.userDescription)
                    put("userStatus", userData.userStatus)
                    put("online", userData.online)
                    put("lastSeen", userData.lastSeen)
                    put("profilePictureUrl", userData.profilePictureUrl)
                }
                jsonArray.put(jsonUser)
            }

            Log.d(
                tag,
                "Attempting to write ${jsonArray.length()} users to file: ${localUsersFile.absolutePath}"
            )

            FileWriter(localUsersFile).use { writer ->
                writer.write(jsonArray.toString())
                Log.d(tag, "Successfully wrote ${jsonArray.length()} users to local storage file")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error saving users to local storage", e)
            Log.e(tag, "Exception details: ${e.message}")
            e.printStackTrace()
        }

        // Verify file was created and contains data
        try {
            val fileExists = localUsersFile.exists()
            val fileSize = localUsersFile.length()
            Log.d(tag, "Storage file exists: $fileExists, size: $fileSize bytes")
        } catch (e: Exception) {
            Log.e(tag, "Error checking saved file status", e)
        }
    }

    /**
     * Checks if a user exists by ID
     */
    fun checkUserExistsLocally(userId: String): Boolean {
        return getLocalUsers().any { it.uid == userId }
    }

    /**
     * Checks if a phone number exists
     */
    fun checkPhoneExistsLocally(phoneNumber: String): Boolean {
        return getLocalUsers().any { it.phoneNumber == phoneNumber }
    }

    /**
     * Validates login credentials locally
     */
    fun validateLoginLocally(phoneNumber: String, password: String): UserData? {
        val users = getLocalUsers()
        return users.find { it.phoneNumber == phoneNumber && it.password == password }
    }

    // ============================
    // Session Management Methods
    // ============================

    /**
     * Saves a user session
     */
    fun saveUserSession(userId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(USER_ID_KEY, userId).apply()
    }

    /**
     * Clears the current user session
     */
    fun clearUserSession() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(USER_ID_KEY).apply()
    }

    /**
     * Gets the current user session
     */
    fun getUserSession(): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(USER_ID_KEY, null)
    }

    fun updateUserToLocalStorage(
        displayName: String,
        status: String,
        description: String,
        userId: String,
        localImagePath: String,
        profilePictureUrl: String,
        phoneNumber: String,
    ): Boolean {
        try {
            val fileContent = if (usersFile.exists() && usersFile.readText().isNotBlank()) {
                usersFile.readText()
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
                    if (profilePictureUrl != null) {
                        jsonUser.put("profilePictureUrl", profilePictureUrl)
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
                    put("phoneNumber", phoneNumber)

                    // Always save local image path if available
                    if (localImagePath != null) {
                        put("profileImagePath", localImagePath)
                    }

                    // Also save URL if available
                    if (profilePictureUrl != null) {
                        put("profilePictureUrl", profilePictureUrl)
                    }
                }
                jsonArray.put(newUser)
            }

            // Write updated JSON to file
            usersFile.writeText(jsonArray.toString())

            return true

        } catch (e: Exception) {
            Log.e(tag, "Error saving profile to local storage", e)
            Toast.makeText(context, "Failed to save profile", Toast.LENGTH_SHORT).show()

            return false
        }
    }

    fun loadUserFromLocalStorage(userId: String, callback: (UserData) -> Unit) {
        try {
            if (!usersFile.exists() || usersFile.readText().isBlank()) {
                Log.e(tag, "Local Users File Not Found or Empty")
                Toast.makeText(context, "User Data Not Found", Toast.LENGTH_SHORT).show()
                return
            }
            var user: UserData
            val fileContent = usersFile.readText()
            if (fileContent.trim().startsWith("[")) {
                val jsonArray = JSONArray(fileContent)
                for (i in 0 until jsonArray.length()) {
                    val jsonUser = jsonArray.getJSONObject(i)
                    if (jsonUser.getString("uid") == userId) {
                        user = UserData(
                            uid = userId,
                            displayName = jsonUser.getString("displayName") ?: "",
                            phoneNumber = jsonUser.getString("phoneNumber") ?: "",
                            password = jsonUser.getString("password") ?: "",
                            userDescription = jsonUser.getString("userDescription") ?: "",
                            userStatus = jsonUser.getString("userStatus") ?: "",
                            online = jsonUser.getBoolean("online") ?: false,
                            lastSeen = jsonUser.getLong("lastSeen")?.toString() ?: "",
                            profilePictureUrl = jsonUser.getString("profilePictureUrl") ?: ""
                        )

                        callback(user)
                    }
                }
            }
            Log.e(tag, "User Not Found In Local Storage")
            Toast.makeText(context, "User Not Found locally", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(tag, "Error Reading Local Users File", e)
            Toast.makeText(context, "Error Loading User Data", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================
    // Chat Management Methods
    // ============================

    /**
     * Loads chats from local storage without updating the ChatManager
     */
    fun loadChatsFromLocalStorageWithoutSaving(): List<Chat> {
        Log.d(tag, "Loading chats from local storage")
        val jsonString = readChatsFromFile()
        val localChats = mutableListOf<Chat>()

        if (jsonString.isEmpty()) {
            Log.d(tag, "No chats file found or empty file")
            return localChats
        }

        try {
            val jsonArray = JSONArray(jsonString)
            Log.d(tag, "Found ${jsonArray.length()} chats in JSON")

            for (i in 0 until jsonArray.length()) {
                try {
                    val chatObject = jsonArray.getJSONObject(i)

                    // Debug the chat object
                    Log.d(tag, "Processing chat JSON: ${chatObject.toString().take(100)}...")

                    // Extract participantIds correctly - handle both array and map formats
                    val participantIds = HashMap<String, Boolean>()

                    // Check if participantIds is an array or object
                    if (chatObject.has("participantIds")) {
                        val participantIdsValue = chatObject.get("participantIds")

                        if (participantIdsValue is JSONArray) {
                            // Old format - array of strings
                            val participantIdsJsonArray = chatObject.getJSONArray("participantIds")
                            for (j in 0 until participantIdsJsonArray.length()) {
                                val participantId = participantIdsJsonArray.getString(j)
                                participantIds[participantId] = true
                            }
                        } else if (participantIdsValue is JSONObject) {
                            // New format - object with boolean values
                            val participantIdsJson = chatObject.getJSONObject("participantIds")
                            val keys = participantIdsJson.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                participantIds[key] = participantIdsJson.getBoolean(key)
                            }
                        }
                    }

                    // Log the participantIds for debugging
                    Log.d(tag, "ParticipantIds: $participantIds")

                    // Get displayName if it exists, otherwise default to empty string
                    val displayName = if (chatObject.has("displayName"))
                        chatObject.getString("displayName") else ""

                    // Extract unreadCount
                    val unreadCount = mutableMapOf<String, Int>()
                    if (chatObject.has("unreadCount")) {
                        val unreadCountJson = chatObject.getJSONObject("unreadCount")
                        val keys = unreadCountJson.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            unreadCount[key] = unreadCountJson.getInt(key)
                        }
                    }

                    // Debug
                    Log.d(tag, "Current user ID: ${UserSettings.userId}")
                    Log.d(
                        tag,
                        "Is user in participants: ${participantIds.containsKey(UserSettings.userId)}"
                    )

                    // Set a default type if missing
                    val type = if (chatObject.has("type"))
                        chatObject.getString("type")
                    else
                        "direct"

                    val chat = Chat(
                        id = chatObject.getString("id"),
                        name = chatObject.getString("name"),
                        displayName = displayName,
                        lastMessage = chatObject.getString("lastMessage"),
                        timestamp = chatObject.getLong("timestamp"),
                        unreadCount = unreadCount,
                        participantIds = participantIds,
                        type = type
                    )

                    // Check if current user is a participant with active status (or default to true)
                    val isParticipant = participantIds.containsKey(UserSettings.userId) &&
                            (participantIds[UserSettings.userId] ?: true)

                    if (isParticipant) {
                        Log.d(tag, "Adding chat: ${chat.name} with ID: ${chat.id}")
                        localChats.add(chat)
                    } else {
                        Log.d(
                            tag,
                            "Skipping chat: ${chat.name} - user is not an active participant"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error processing chat at index $i: ${e.message}")
                    e.printStackTrace()
                }
            }

            Log.d(tag, "Loaded ${localChats.size} chats from local storage")
        } catch (e: Exception) {
            Log.e(tag, "Error loading chats from file: ${e.message}")
            e.printStackTrace() // Add stack trace for detailed error info
        }

        return localChats
    }

    /**
     * Saves all chats from the ChatManager to local storage
     */
    fun saveChatsToLocalStorage(chatManager: ChatManager) {
        val jsonArray = JSONArray()
        val allChats = chatManager.getAll()

        for (chat in allChats) {
            try {
                val chatObject = JSONObject().apply {
                    put("id", chat.id)
                    put("name", chat.name)
                    put("displayName", chat.displayName)
                    put("lastMessage", chat.lastMessage)
                    put("timestamp", chat.timestamp)

                    // Save unreadCount as an object
                    val unreadCountJson = JSONObject()
                    for ((userId, count) in chat.unreadCount) {
                        unreadCountJson.put(userId, count)
                    }
                    put("unreadCount", unreadCountJson)

                    // Save participantIds as an object with boolean values
                    val participantIdsJson = JSONObject()
                    for ((userId, isActive) in chat.participantIds) {
                        participantIdsJson.put(userId, isActive)
                    }
                    put("participantIds", participantIdsJson)

                    put("type", chat.type)
                }
                jsonArray.put(chatObject)
            } catch (e: Exception) {
                Log.e(tag, "Error saving chat ${chat.id}: ${e.message}")
            }
        }

        Log.d(tag, "Saving ${chatManager.size()} chats to local storage")

        val jsonString = jsonArray.toString()
        writeChatsToFile(jsonString)
    }

    /**
     * Reads chat data from the local file
     */
    private fun readChatsFromFile(): String {
        val file = File(context.filesDir, CHATS_FILE)
        val content = if (file.exists()) {
            val text = file.readText()
            Log.d(tag, "Chat file content length: ${text.length}")
            // Print first 100 chars to debug log
            if (text.isNotEmpty()) {
                Log.d(tag, "Chat file content preview: ${text.take(100)}...")
            }
            text
        } else {
            Log.d(tag, "Chat file doesn't exist at: ${file.absolutePath}")
            ""
        }
        return content
    }

    /**
     * Writes chat data to the local file
     */
    private fun writeChatsToFile(jsonString: String) {
        try {
            val file = File(context.filesDir, CHATS_FILE)
            file.writeText(jsonString)
            Log.d(tag, "Chats saved to file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(tag, "Error writing to file: ${e.message}")
        }
    }


    // ============================
    // story Data Management Methods
    // ============================

    /**
     * Loads stories from local storage without updating the StoriesManager
     */
    fun loadStoriesFromLocalStorage(): List<Stories> {
        Log.d(tag, "Loading stories from local storage")
        val jsonString = readStoriesFromFile()
        val localStories = mutableListOf<Stories>()

        if (jsonString.isEmpty()) {
            Log.d(tag, "No stories file found or empty file")
            return localStories
        }

        try {
            val jsonArray = JSONArray(jsonString)
            Log.d(tag, "Found ${jsonArray.length()} story entries in JSON")

            for (i in 0 until jsonArray.length()) {
                try {
                    val storiesObject = jsonArray.getJSONObject(i)

                    // Debug the stories object
                    Log.d(tag, "Processing stories JSON: ${storiesObject.toString().take(100)}...")

                    val uid = storiesObject.getString("uid")
                    val displayName = storiesObject.getString("displayName")
                    val profilePictureUrl = storiesObject.getString("profilePictureUrl")

                    // Extract stories list
                    val storiesArray = storiesObject.getJSONArray("stories")
                    val storiesList = mutableListOf<Story>()

                    for (j in 0 until storiesArray.length()) {
                        val storyObject = storiesArray.getJSONObject(j)
                        val story = Story(
                            imageurl = storyObject.getString("imageurl"),
                            storyCaption = storyObject.getString("storyCaption"),
                            uploadedAt = storyObject.getString("uploadedAt")
                        )
                        storiesList.add(story)
                    }

                    val stories = Stories(
                        uid = uid,
                        displayName = displayName,
                        profilePictureUrl = profilePictureUrl,
                        stories = storiesList
                    )

                    localStories.add(stories)
                    Log.d(tag, "Added stories for user: $displayName with ID: $uid")
                } catch (e: Exception) {
                    Log.e(tag, "Error processing stories at index $i: ${e.message}")
                    e.printStackTrace()
                }
            }

            Log.d(tag, "Loaded ${localStories.size} stories entries from local storage")
        } catch (e: Exception) {
            Log.e(tag, "Error loading stories from file: ${e.message}")
            e.printStackTrace()
        }

        return localStories
    }

    /**
     * Saves all stories to local storage
     */
    fun saveStoriesToLocalStorage(storiesList: List<Stories>) {
        val jsonArray = JSONArray()

        for (stories in storiesList) {
            try {
                val storiesObject = JSONObject().apply {
                    put("uid", stories.uid)
                    put("displayName", stories.displayName)
                    put("profilePictureUrl", stories.profilePictureUrl)

                    // Save list of stories
                    val storiesJsonArray = JSONArray()
                    for (story in stories.stories!!) {
                        val storyObject = JSONObject().apply {
                            put("imageurl", story.imageurl)
                            put("storyCaption", story.storyCaption)
                            put("uploadedAt", story.uploadedAt)
                        }
                        storiesJsonArray.put(storyObject)
                    }
                    put("stories", storiesJsonArray)
                }
                jsonArray.put(storiesObject)
            } catch (e: Exception) {
                Log.e(tag, "Error saving stories for user ${stories.uid}: ${e.message}")
            }
        }

        Log.d(tag, "Saving ${storiesList.size} stories entries to local storage")

        val jsonString = jsonArray.toString()
        writeStoriesToFile(jsonString)
    }

    /**
     * Reads stories data from the local file
     */
    private fun readStoriesFromFile(): String {
        val file = File(context.filesDir, STORIES_FILE)
        val content = if (file.exists()) {
            val text = file.readText()
            Log.d(tag, "Stories file content length: ${text.length}")
            // Print first 100 chars to debug log
            if (text.isNotEmpty()) {
                Log.d(tag, "Stories file content preview: ${text.take(100)}...")
            }
            text
        } else {
            Log.d(tag, "Stories file doesn't exist at: ${file.absolutePath}")
            ""
        }
        return content
    }

    /**
     * Writes stories data to the local file
     */
    private fun writeStoriesToFile(jsonString: String) {
        try {
            val file = File(context.filesDir, STORIES_FILE)
            file.writeText(jsonString)
            Log.d(tag, "Stories saved to file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(tag, "Error writing stories to file: ${e.message}")
        }
    }
}