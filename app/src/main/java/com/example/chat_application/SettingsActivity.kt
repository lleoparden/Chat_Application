package com.example.chat_application

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class SettingsActivity : AppCompatActivity() {

    private lateinit var deleteChats : LinearLayout




    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.settings)

        deleteChats = findViewById(R.id.chatHistorySettingItem)




        val profileSettingItem = findViewById<LinearLayout>(R.id.profileSettingItem)
        val signOutSettingItem = findViewById<LinearLayout>(R.id.signOutSettingItem)


        val backButton = findViewById<Toolbar>(R.id.toolbar)

        backButton.setNavigationOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }


        profileSettingItem.setOnClickListener {
            // Navigate to ProfileActivity
            val intent = Intent(this, EditProfileActivity::class.java)
            startActivity(intent)
        }

        deleteChats.setOnClickListener {
            clearAllChatFiles()
        }


        signOutSettingItem.setOnClickListener {
            logout()
        }
    }

    private fun clearAllChatFiles() {
        try {
            var deletedCount = 0
            var failedCount = 0

            // Find all message files in the app's internal storage
            val files = filesDir.listFiles { file ->
                file.name.startsWith("messages_") && file.name.endsWith(".json")
            }

            files?.forEach { file ->
                if (file.delete()) {
                    deletedCount++
                } else {
                    failedCount++
                }
            }

            // Clear the current messages in memory and update UI

            val message = when {
                deletedCount > 0 && failedCount == 0 -> "Cleared $deletedCount chats"
                deletedCount > 0 && failedCount > 0 -> "Cleared $deletedCount chats, $failedCount failed"
                else -> "No chats cleared"
            }

            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("ChatRoomActivity", "Error clearing all chats: ${e.message}")
            Toast.makeText(this, "Error clearing chats: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Handle back button in toolbar

    private fun logout() {
        // Clear user session from SharedPreferences
        val prefs = getSharedPreferences("ChatAppPrefs", MODE_PRIVATE)
        prefs.edit().remove("userId").apply()

        // Reset UserSettings
        UserSettings.userId = ""

        // Navigate back to AuthActivity
        val intent = Intent(this, AuthActivity::class.java)
        // Clear back stack so user can't go back to MainActivity after logout
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}