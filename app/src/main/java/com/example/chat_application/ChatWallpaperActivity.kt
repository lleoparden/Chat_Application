package com.example.chat_application

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import android.content.pm.PackageManager
import android.provider.MediaStore

class ChatWallpaperActivity : AppCompatActivity() {

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
        private const val PERMISSION_REQUEST_READ_EXTERNAL_STORAGE = 100
        private const val PREF_NAME = "chat_wallpaper_preferences"
        private const val KEY_THEME = "selected_theme"
        private const val KEY_WALLPAPER_URI = "wallpaper_uri"
        private const val THEME_LIGHT = "light"
        private const val THEME_DARK = "dark"
        private const val THEME_SYSTEM = "system"
    }

    private lateinit var themeRadioGroup: RadioGroup
    private lateinit var lightThemeRadio: RadioButton
    private lateinit var darkThemeRadio: RadioButton
    private lateinit var systemThemeRadio: RadioButton
    private lateinit var selectedWallpaperPreview: ImageView
    private lateinit var selectFromGalleryButton: Button
    private lateinit var removeWallpaper: Button
    private lateinit var noWallpaperSelected: TextView
    private lateinit var sharedPreferences: SharedPreferences

    private var selectedWallpaperUri: Uri? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chat_wallpaper)

        // Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        // Shared Preferences
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE)

        initViews()

        val backButton = findViewById<Toolbar>(R.id.toolbar)
        backButton.setNavigationOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        }

        loadSavedPreferences()
        setupClickListeners()
    }

    private fun initViews() {
        themeRadioGroup = findViewById(R.id.themeRadioGroup)
        lightThemeRadio = findViewById(R.id.lightThemeRadio)
        darkThemeRadio = findViewById(R.id.darkThemeRadio)
        systemThemeRadio = findViewById(R.id.systemThemeRadio)
        selectedWallpaperPreview = findViewById(R.id.selectedWallpaperPreview)
        selectFromGalleryButton = findViewById(R.id.selectFromGalleryButton)
        noWallpaperSelected = findViewById(R.id.noWallpaperSelected)
        removeWallpaper = findViewById(R.id.removeWallpaper)
    }

    private fun loadSavedPreferences() {
        // Theme
        when (sharedPreferences.getString(KEY_THEME, THEME_SYSTEM)) {
            THEME_LIGHT -> lightThemeRadio.isChecked = true
            THEME_DARK -> darkThemeRadio.isChecked = true
            else -> systemThemeRadio.isChecked = true
        }

        // Wallpaper
        val savedWallpaperUri = sharedPreferences.getString(KEY_WALLPAPER_URI, null)
        if (savedWallpaperUri != null) {
            try {
                selectedWallpaperUri = Uri.parse(savedWallpaperUri)
                selectedWallpaperPreview.setImageURI(selectedWallpaperUri)
                noWallpaperSelected.visibility = View.GONE
                removeWallpaper.visibility = View.VISIBLE
            } catch (e: Exception) {
                noWallpaperSelected.visibility = View.VISIBLE
                removeWallpaper.visibility = View.GONE
            }
        } else {
            noWallpaperSelected.visibility = View.VISIBLE
            removeWallpaper.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = when (checkedId) {
                R.id.lightThemeRadio -> THEME_LIGHT
                R.id.darkThemeRadio -> THEME_DARK
                else -> THEME_SYSTEM
            }

            val currentTheme = sharedPreferences.getString(KEY_THEME, THEME_SYSTEM)

            if (newTheme != currentTheme) {
                sharedPreferences.edit().putString(KEY_THEME, newTheme).apply()
                applyTheme(newTheme)
            }
        }

        selectFromGalleryButton.setOnClickListener {
            if (checkStoragePermission()) {
                openGallery()
            } else {
                requestStoragePermission()
            }
        }

        removeWallpaper.setOnClickListener {
            removeWallpaperImage()
        }
    }

    private fun applyTheme(theme: String) {
        when (theme) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                PERMISSION_REQUEST_READ_EXTERNAL_STORAGE
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_READ_EXTERNAL_STORAGE
            )
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery()
            } else {
                Toast.makeText(
                    this,
                    "Permission denied. Cannot access gallery.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            selectedWallpaperUri = data.data

            selectedWallpaperUri?.let { uri ->
                try {
                    selectedWallpaperPreview.setImageURI(uri)
                    noWallpaperSelected.visibility = View.GONE
                    removeWallpaper.visibility = View.VISIBLE

                    // Save to SharedPreferences and Firebase
                    saveWallpaperToDatabase(uri.toString())
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
                    noWallpaperSelected.visibility = View.VISIBLE
                    removeWallpaper.visibility = View.GONE
                }
            }
        }
    }

    private fun saveWallpaperToDatabase(uri: String) {
        sharedPreferences.edit().putString(KEY_WALLPAPER_URI, uri).apply()

        val userId = auth.currentUser?.uid
        userId?.let {
            database.child("users").child(it).child("wallpaper").setValue(uri)
                .addOnSuccessListener {
                    Toast.makeText(this, "Wallpaper saved successfully", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to save to Firebase: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun removeWallpaperImage() {
        selectedWallpaperPreview.setImageResource(R.drawable.circle2) // fallback default
        sharedPreferences.edit().remove(KEY_WALLPAPER_URI).apply()

        val userId = auth.currentUser?.uid
        userId?.let {
            database.child("users").child(it).child("wallpaper").removeValue()
                .addOnSuccessListener {
                    Toast.makeText(this, "Wallpaper removed", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to remove: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        selectedWallpaperUri = null
        noWallpaperSelected.visibility = View.VISIBLE
        removeWallpaper.visibility = View.GONE
    }
}
