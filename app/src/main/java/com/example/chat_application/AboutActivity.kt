package com.example.chat_application

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.AppBarLayout
import androidx.appcompat.widget.Toolbar
import android.widget.TextView
import com.google.android.material.card.MaterialCardView

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.about)

        // Setting up Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Developer website link
        val developerWebsite: TextView = findViewById(R.id.developerWebsite)
        developerWebsite.setOnClickListener {
            val websiteUrl = "https://www.yourwebsite.com"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl))
            startActivity(intent)
        }

        // Developer email link
        val developerEmail: TextView = findViewById(R.id.developerEmail)
        developerEmail.setOnClickListener {
            val email = "contact@yourwebsite.com"
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
            startActivity(intent)
        }

        // Privacy Policy link
        val privacyPolicy: TextView = findViewById(R.id.privacyPolicy)
        privacyPolicy.setOnClickListener {
            val privacyUrl = "https://www.yourwebsite.com/privacy"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl))
            startActivity(intent)
        }

        // Terms of Service link
        val termsOfService: TextView = findViewById(R.id.termsOfService)
        termsOfService.setOnClickListener {
            val termsUrl = "https://www.yourwebsite.com/terms"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(termsUrl))
            startActivity(intent)
        }

        // Open Source Licenses link
        val licenses: TextView = findViewById(R.id.licenses)
        licenses.setOnClickListener {
            val licensesUrl = "https://www.yourwebsite.com/licenses"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(licensesUrl))
            startActivity(intent)
        }
    }
}
