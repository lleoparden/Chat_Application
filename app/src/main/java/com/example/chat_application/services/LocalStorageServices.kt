package com.example.chat_application.services

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.chat_application.dataclasses.Chat
import com.example.chat_application.adapters.ChatAdapter
import com.example.chat_application.ChatManager
import com.example.chat_application.dataclasses.UserData
import com.example.chat_application.dataclasses.UserSettings
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
    private const val USERS_FILE = "local_users.json"
    private const val PREFS_NAME = "ChatAppPrefs"
    private const val USER_ID_KEY = "userId"

    // Properties
    private lateinit var context: Context
    private lateinit var tag :String
    private lateinit var localUsersFile: File

    /**
     * Initializes the local storage service
     */
    fun initialize(context: Context,Tag :String) {
        LocalStorageService.context = context
        localUsersFile = File(context.filesDir, USERS_FILE)
        tag= Tag

        if (!localUsersFile.exists()) {
            try {
                localUsersFile.createNewFile()
                saveUsersToJson(emptyList())
                Log.d(tag, "Created new local users file at: ${localUsersFile.absolutePath}")
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
        if (!localUsersFile.exists() || localUsersFile.length() == 0L) {
            return emptyList()
        }

        return try {
            val fileContent = FileReader(localUsersFile).use { it.readText() }

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

            FileWriter(localUsersFile).use { writer ->
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

     fun updateUserToLocalStorage(displayName: String, status: String, description: String,userId: String,localImagePath:String,profilePictureUrl:String,phoneNumber: String,) : Boolean{
        try {
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
            localUsersFile.writeText(jsonArray.toString())

            return true
            
        } catch (e: Exception) {
            Log.e(tag, "Error saving profile to local storage", e)
            Toast.makeText(context, "Failed to save profile", Toast.LENGTH_SHORT).show()
            
            return false
        }
    }

    fun loadUserFromLocalStorage(userId: String,callback: (UserData) -> Unit) {
        try {
            if (!localUsersFile.exists() || localUsersFile.readText().isBlank()) {
                Log.e(tag, "Local Users File Not Found or Empty")
                Toast.makeText(context, "User Data Not Found", Toast.LENGTH_SHORT).show()
                return
            }
            var user : UserData
            val fileContent = localUsersFile.readText()
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
     * Loads chats from local storage and updates the ChatManager and ChatAdapter
     */
    fun loadChatsFromLocalStorageAndDisplay(chatManager: ChatManager, chatAdapter: ChatAdapter, ) {
        val localChats = loadChatsFromLocalStorageWithoutSaving()
        chatManager.clear()
        chatManager.pushAll(localChats)
        chatAdapter.updateData(chatManager.getAll())
    }

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

            for (i in 0 until jsonArray.length()) {
                val chatObject = jsonArray.getJSONObject(i)
                val participantIdsJsonArray = chatObject.getJSONArray("participantIds")
                val participantIds = HashMap<String,Boolean>()

                for (j in 0 until participantIdsJsonArray.length()) {
                    participantIds[participantIdsJsonArray.toString()] = true;
                }

                // Get displayName if it exists, otherwise default to empty string
                val displayName = if (chatObject.has("displayName"))
                    chatObject.getString("displayName") else ""

                val chat = Chat(
                    id = chatObject.getString("id"),
                    name = chatObject.getString("name"),
                    displayName = displayName,
                    lastMessage = chatObject.getString("lastMessage"),
                    timestamp = chatObject.getLong("timestamp"),
                    unreadCount = chatObject.getInt("unreadCount"),
                    participantIds = participantIds,
                    type = chatObject.getString("type")
                )

                // Only add chats where current user is a participant
                if (participantIds.contains(UserSettings.userId)) {
                    localChats.add(chat)
                }
            }

            Log.d(tag, "Loaded ${localChats.size} chats from local storage")
        } catch (e: Exception) {
            Log.e(tag, "Error loading chats from file: ${e.message}")
        }

        return localChats
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
     * Saves all chats from the ChatManager to local storage
     */
    fun saveChatsToLocalStorage(chatManager: ChatManager, ) {
        val jsonArray = JSONArray()
        val allChats = chatManager.getAll()

        for (chat in allChats) {
            val chatObject = JSONObject().apply {
                put("id", chat.id)
                put("name", chat.name)
                put("displayName", chat.displayName)
                put("lastMessage", chat.lastMessage)
                put("timestamp", chat.timestamp)
                put("unreadCount", chat.unreadCount)

                // Create a JSONArray for participantIds
                val participantIdsArray = JSONArray()
                for (participantId in chat.participantIds) {
                    participantIdsArray.put(participantId)
                }
                put("participantIds", participantIdsArray)
                put("type", chat.type)
            }
            jsonArray.put(chatObject)
        }
        Log.d(tag, "Saving ${chatManager.size()} chats to local storage")

        val jsonString = jsonArray.toString()
        writeChatsToFile(jsonString)
    }

    /**
     * Writes chat data to the local file
     */
    private fun writeChatsToFile(jsonString: String, ) {
        try {
            val file = File(context.filesDir, CHATS_FILE)
            file.writeText(jsonString)
            Log.d(tag, "Chats saved to file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(tag, "Error writing to file: ${e.message}")
        }
    }
}