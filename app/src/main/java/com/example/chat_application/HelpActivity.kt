package com.example.chat_application

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.snackbar.Snackbar

class HelpActivity : AppCompatActivity() {

    // Launcher لاختيار صورة
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // بدل contactButton استخدم جذر الـ content view
            val root: View = findViewById(android.R.id.content)
            Snackbar.make(root, "Image selected: $uri", Snackbar.LENGTH_LONG).show()
        }
    }

    private lateinit var contactButton: Button
    private lateinit var pickImageButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        // طبق الثيم
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.help)  // تأكد من أن الملف اسمه help.xml

        // Toolbar مع زر رجوع
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // زر الاتصال بالدعم
        contactButton = findViewById(R.id.contactSupportButton)
        contactButton.setOnClickListener { view ->
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("support@example.com"))
                putExtra(Intent.EXTRA_SUBJECT, "Support Request")
                putExtra(Intent.EXTRA_TEXT, "Hi, I need help with...")
            }
            if (emailIntent.resolveActivity(packageManager) != null) {
                startActivity(Intent.createChooser(emailIntent, "Contact Support via Email"))
            } else {
                Snackbar.make(view, "No email app found", Snackbar.LENGTH_LONG).show()
            }
        }

        // زر اختيار صورة
        pickImageButton = findViewById(R.id.pickImageButton)
        pickImageButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }
}
