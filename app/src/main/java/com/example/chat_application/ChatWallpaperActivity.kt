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
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
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

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        // Initialize shared preferences
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE)

        // Initialize views
        initViews()

        val backButton = findViewById<Toolbar>(R.id.toolbar)

        backButton.setNavigationOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        }
        // Load saved preferences
        loadSavedPreferences()

        // Set up click listeners
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
        // Load theme preference
        when (sharedPreferences.getString(KEY_THEME, THEME_SYSTEM)) {
            THEME_LIGHT -> lightThemeRadio.isChecked = true
            THEME_DARK -> darkThemeRadio.isChecked = true
            else -> systemThemeRadio.isChecked = true
        }

        // Load wallpaper URI if exists
        val savedWallpaperUri = sharedPreferences.getString(KEY_WALLPAPER_URI, null)
        if (savedWallpaperUri != null) {
            try {
                selectedWallpaperUri = Uri.parse(savedWallpaperUri)
                selectedWallpaperPreview.setImageURI(selectedWallpaperUri)
                noWallpaperSelected.visibility = View.GONE
            } catch (e: Exception) {
                noWallpaperSelected.visibility = View.VISIBLE
            }
        } else {
            noWallpaperSelected.visibility = View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        // Theme selection listener
        themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.lightThemeRadio -> THEME_LIGHT
                R.id.darkThemeRadio -> THEME_DARK
                else -> THEME_SYSTEM
            }

            // Save the theme preference
            sharedPreferences.edit().putString(KEY_THEME, theme).apply()

            // Apply the theme
            applyTheme(theme)
        }

        // Gallery button click listener
        selectFromGalleryButton.setOnClickListener {
            if (checkStoragePermission()) {
                openGallery()
            } else {
                requestStoragePermission()
            }
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

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//
//        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
//            selectedWallpaperUri = data.data
//
//            // Upload the selected wallpaper to Firebase Storage
//            selectedWallpaperUri?.let { uri ->
//                val fileRef = storageRef.child("wallpapers/${auth.currentUser?.uid}/${System.currentTimeMillis()}.jpg")
//                fileRef.putFile(uri)
//                    .addOnSuccessListener {
//                        fileRef.downloadUrl.addOnSuccessListener { downloadUri ->
//                            // Save the URI of the uploaded image to Firebase Realtime Database
//                            saveWallpaperToDatabase(downloadUri.toString())
//
//                            // Display the selected image
//                            selectedWallpaperPreview.setImageURI(downloadUri)
//                            noWallpaperSelected.visibility = View.GONE
//                        }
//                    }
//                    .addOnFailureListener { e ->
//                        Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
//                    }
//            }
//        }
//    }

    private fun saveWallpaperToDatabase(uri: String) {
        val userId = auth.currentUser?.uid
        userId?.let {
            database.child("users").child(it).child("wallpaper").setValue(uri)
            sharedPreferences.edit().putString(KEY_WALLPAPER_URI, uri).apply()
        }
    }
}
