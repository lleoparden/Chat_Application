package com.example.chat_application

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.chat_application.dataclasses.UserSettings
import java.io.File

class SettingsActivity : AppCompatActivity() {

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {

        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.settings)


        val backButton = findViewById<Toolbar>(R.id.toolbar)

        backButton.setNavigationOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        }


        findViewById<LinearLayout>(R.id.profileSettingItem).setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.accountSettingItem).setOnClickListener {
            startActivity(Intent(this, AccountSettingsActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.chatBackgroundSettingItem).setOnClickListener {
            startActivity(Intent(this, ChatWallpaperActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.helpSettingItem).setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.aboutSettingItem).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.inviteFriendsSettingItem).setOnClickListener {
            startActivity(Intent(this, InviteFriendsActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.chatHistorySettingItem).setOnClickListener {
            startActivity(Intent(this, ChatHistoryActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.signOutSettingItem).setOnClickListener {
            logout()
        }
    }

    private fun clearAllChatFiles() {
        try {
            var deletedCount = 0
            var failedCount = 0

            File(filesDir, "local_users.json").delete()
            File(filesDir, "chats.json").delete()

            filesDir.listFiles { file ->
                file.name.startsWith("messages_") && file.name.endsWith(".json")
            }?.forEach { file ->
                if (file.delete()) deletedCount++ else failedCount++
            }

            val message = when {
                deletedCount > 0 && failedCount == 0 ->
                    "Cleared $deletedCount chats"
                deletedCount > 0 && failedCount > 0 ->
                    "Cleared $deletedCount chats, $failedCount failed"
                else ->
                    "No chats cleared"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error clearing chats: ${e.message}")
            Toast.makeText(this, "Error clearing chats: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logout() {
        getSharedPreferences("ChatAppPrefs", Context.MODE_PRIVATE)
            .edit().remove("userId").apply()

        UserSettings.userId = ""

        Intent(this, AuthActivity::class.java).also { intent ->
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
        finish()
    }
}
