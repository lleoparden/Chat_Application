package com.example.chat_application

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.widget.TextView
import com.google.android.material.card.MaterialCardView

class AboutActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.about)

        // Set up toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Developer website link
        val developerWebsite: TextView = findViewById(R.id.developerWebsite)
        developerWebsite.setOnClickListener {
            openUrl("https://bit.ly/Za3boot") // Replace with actual developer website
        }

        // Developer email link
        val developerEmail: TextView = findViewById(R.id.developerEmail)
        developerEmail.setOnClickListener {
            val email = "Za3boot.biko@gmail.com"
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$email")
                    putExtra(Intent.EXTRA_SUBJECT, "Inquiry about your app")
                }
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    // No email app is installed, copy email to clipboard
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Developer Email", email)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(this@AboutActivity,
                        "Email copied to clipboard: $email",
                        android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Handle any other exceptions that might occur
                android.widget.Toast.makeText(this@AboutActivity,
                    "Couldn't open email app",
                    android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Privacy Policy link
        val privacyPolicy: TextView = findViewById(R.id.privacyPolicy)
        privacyPolicy.setOnClickListener {
            openUrl("https://bit.ly/Za3boot") // Replace with actual privacy policy URL
        }

        // Terms of Service link
        val termsOfService: TextView = findViewById(R.id.termsOfService)
        termsOfService.setOnClickListener {
            openUrl("https://bit.ly/Za3boot") // Replace with actual terms URL
        }

        // Open Source Licenses link
        val licenses: TextView = findViewById(R.id.licenses)
        licenses.setOnClickListener {
            openUrl("https://bit.ly/Za3boot") // Replace with actual licenses URL
        }

        // Back button navigation
        toolbar.setNavigationOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        }
    }
    // Helper method to open URLs with error handling
    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            // Handle the exception (could show a toast or dialog informing the user)
        }
    }


}