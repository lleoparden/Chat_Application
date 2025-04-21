package com.example.chat_application

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.settings)




        val profileSettingItem = findViewById<LinearLayout>(R.id.profileSettingItem)
        val signOutSettingItem = findViewById<LinearLayout>(R.id.signOutSettingItem)



        profileSettingItem.setOnClickListener {
            // Navigate to ProfileActivity
            val intent = Intent(this, EditProfileActivity::class.java)
            startActivity(intent)
        }


        signOutSettingItem.setOnClickListener {
            logout()
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