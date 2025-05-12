package com.example.chat_application.dataclasses

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import com.example.chat_application.R
import com.example.chat_application.services.ImageUploadService
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

/**
 * Object that manages user settings and preferences
 */
object UserSettings {
    private const val TAG = "UserSettings"

    // Default settings
    var theme: String = "system" // light, dark, or system
    lateinit var userId: String
    var chatroomBackground: String? = null // Will store local path or null for default
    var readReceipts: Boolean = true

    /**
     * Initializes user settings with a user ID
     *
     * @param context The application context
     * @param userId The user ID to initialize settings for
     */
    fun initialize(context: Context, userId: String) {
        this.userId = userId
        loadSettings(context)
    }

    /**
     * Saves the chat wallpaper image locally and updates settings
     *
     * @param context The application context
     * @param imageUri The URI of the selected wallpaper image
     * @param callback Optional callback for completion
     */
    fun saveChatWallpaper(
        context: Context,
        imageUri: Uri,
        callback: ((success: Boolean) -> Unit)? = null
    ) {
        if (!::userId.isInitialized) {
            Log.e(TAG, "User ID not initialized")
            callback?.invoke(false)
            return
        }

        // Remove existing wallpaper first
        if (chatroomBackground != null) {
            try {
                File(chatroomBackground!!).delete()
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting old wallpaper", e)
            }
        }

        // Save new wallpaper
        val localPath = ImageUploadService.saveImageLocally(
            context,
            imageUri,
            userId,
            "wallpaper_"
        )

        if (localPath != null) {
            chatroomBackground = localPath
            saveSettings(context)
            callback?.invoke(true)
        } else {
            Log.e(TAG, "Failed to save wallpaper image")
            callback?.invoke(false)
        }
    }

    /**
     * Removes the custom wallpaper and reverts to default
     *
     * @param context The application context
     * @return true if successfully removed, false otherwise
     */
    fun removeChatWallpaper(context: Context): Boolean {
        if (chatroomBackground != null) {
            try {
                // Delete the wallpaper file if it exists
                val wallpaperFile = File(chatroomBackground!!)
                if (wallpaperFile.exists()) {
                    wallpaperFile.delete()
                }

                // Reset setting to default
                chatroomBackground = null
                saveSettings(context)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Error removing wallpaper", e)
                return false
            }
        }
        return true // No wallpaper to remove
    }

    /**
     * Saves user settings to a JSON file
     *
     * @param context The application context
     * @return true if successfully saved, false otherwise
     */
    fun saveSettings(context: Context): Boolean {
        if (!::userId.isInitialized) {
            Log.e(TAG, "User ID not initialized")
            return false
        }

        try {
            val settingsJson = JSONObject().apply {
                put("theme", theme)
                put("userId", userId)
                put("chatroomBackground", chatroomBackground ?: JSONObject.NULL)
                put("readReceipts", readReceipts)
            }

            val settingsFile = File(context.filesDir, "${userId}_settings.json")
            settingsFile.writeText(settingsJson.toString())

            Log.d(TAG, "Settings saved successfully for user $userId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save settings", e)
            Toast.makeText(context, "Failed to save settings: ${e.message}", Toast.LENGTH_SHORT)
                .show()
            return false
        }
    }

    /**
     * Loads user settings from a JSON file
     *
     * @param context The application context
     * @return true if successfully loaded, false otherwise
     */
    fun loadSettings(context: Context): Boolean {
        if (!::userId.isInitialized) {
            Log.e(TAG, "User ID not initialized")
            return false
        }

        try {
            val settingsFile = File(context.filesDir, "${userId}_settings.json")

            if (!settingsFile.exists()) {
                Log.d(TAG, "Settings file not found, using defaults")
                return false
            }

            val jsonString = settingsFile.readText()
            val settingsJson = JSONObject(jsonString)

            // Load settings with fallbacks to defaults
            theme = settingsJson.optString("theme", "system")

            when (theme) {
                "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }

            if (!settingsJson.isNull("chatroomBackground")) {
                chatroomBackground = settingsJson.getString("chatroomBackground")

                // Verify wallpaper file still exists
                val wallpaperFile = File(chatroomBackground!!)
                if (!wallpaperFile.exists()) {
                    Log.w(TAG, "Wallpaper file not found at $chatroomBackground")
                    chatroomBackground = null
                }
            } else {
                chatroomBackground = null
            }

            readReceipts = settingsJson.optBoolean("readReceipts", true)

            Log.d(TAG, "Settings loaded successfully for user $userId")
            return true
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "Settings file not found, using defaults")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load settings", e)
            Toast.makeText(context, "Failed to load settings: ${e.message}", Toast.LENGTH_SHORT)
                .show()
            return false
        }
    }

    /**
     * Applies theme settings to the application
     *
     * @return The resource ID of the theme to apply
     */
    fun getThemeResource(): Int {
        return when (theme) {
            "light" -> R.style.defaultTheme
            "dark" -> R.style.defaultTheme
            else -> R.style.defaultTheme
        }
    }

    /**
     * Gets the chat wallpaper URI or resource ID
     *
     * @return The URI string for a custom wallpaper or null for default
     */
    fun getChatWallpaper(): String? {
        return chatroomBackground
    }
}