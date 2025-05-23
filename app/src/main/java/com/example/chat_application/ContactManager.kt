package com.example.chat_application

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.chat_application.dataclasses.UserData
import com.example.chat_application.dataclasses.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactManager(private val context: Context) {

    // Cached contacts list
    private var cachedContacts: List<Contact> = emptyList()

    // Permission related callbacks
    private var onPermissionGranted: () -> Unit = {}
    private var onPermissionDenied: () -> Unit = {}

    // Data class to represent a contact
    data class Contact(
        val name: String,
        val phoneNumber: String
    )

    // Data class for user with additional flag for registration status
    data class ProcessedUser(
        val userData: UserData,
        val isRegistered: Boolean = true
    )

    fun checkAndRequestContactsPermission(
        activity: AppCompatActivity,
        onGranted: () -> Unit = { cacheContactsOnStartup(activity) },
        onDenied: () -> Unit = {
            Toast.makeText(
                activity,
                "Contacts permission denied. Some features may not work.",
                Toast.LENGTH_LONG
            ).show()
        }
    ) {
        this.onPermissionGranted = onGranted
        this.onPermissionDenied = onDenied

        when {
            // Permission is already granted
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                onPermissionGranted()
            }
            // Should show permission rationale
            activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) -> {
                // You could show a dialog explaining why you need this permission
                requestPermission(activity)
            }
            // Request permission directly
            else -> {
                requestPermission(activity)
            }
        }
    }

    private fun requestPermission(activity: AppCompatActivity) {
        val requestPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // Permission granted
                onPermissionGranted()
            } else {
                // Permission denied
                onPermissionDenied()
            }
        }

        // Launch the permission request
        requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    private fun cacheContactsOnStartup(activity: Activity) {
        // This should be called from a coroutine or background thread
        kotlinx.coroutines.MainScope().launch {
            cacheContacts()
        }
    }

    suspend fun cacheContacts() {
        withContext(Dispatchers.IO) {
            val contacts = mutableListOf<Contact>()

            // Query the contacts content provider
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )

            cursor?.use {
                val nameColumnIndex =
                    it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberColumnIndex =
                    it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val name = it.getString(nameColumnIndex) ?: ""
                    val number = it.getString(numberColumnIndex)?.replace("\\s".toRegex(), "") ?: ""

                    if (name.isNotEmpty() && number.isNotEmpty()) {
                        contacts.add(Contact(name, number))
                    }
                }
            }

            // Update cached contacts
            cachedContacts = contacts.distinctBy { it.phoneNumber }
        }
    }

    private fun generatePhoneNumberVariants(phoneNumber: String): List<String> {
        val cleanNumber = phoneNumber.replace("\\s".toRegex(), "")

        return listOf(
            cleanNumber,                    // Original (spaces removed)
            "+$cleanNumber",                // With + prefix
            "+2$cleanNumber",               // With country code +2
            "+20$cleanNumber",              // With country code +20
            cleanNumber.replace("+", "")    // Without + if it exists
        ).distinct()
    }

    fun processUsersToContact(users: List<UserData>, removeNonContacts: Boolean): List<UserData> {
        val processedUsers = mutableListOf<UserData>()

        // Process each user
        for (user in users) {
            // Check if this is the current user
            if (user.uid == UserSettings.userId) {
                // For the current user, set display name as "me"
                val updatedUser = user.copy(displayName = "me")
                processedUsers.add(updatedUser)
                continue  // Skip to the next user
            }

            // Generate phone number variants for this user
            val phoneVariants = generatePhoneNumberVariants(user.phoneNumber)

            // Try to find a match with any of the phone number variants
            val matchingContact = cachedContacts.find { contact ->
                val contactVariants = generatePhoneNumberVariants(contact.phoneNumber)

                // Check if any variant of the user's phone number matches any variant of the contact's phone number
                phoneVariants.any { userVariant ->
                    contactVariants.any { contactVariant ->
                        userVariant == contactVariant
                    }
                }
            }

            if (matchingContact != null) {
                // If found in contacts, use the contact name as display name
                val updatedUser = user.copy(displayName = matchingContact.name)
                processedUsers.add(updatedUser)
            } else if (!removeNonContacts) {
                // If not found and we're not removing non-contacts, keep the original user
                processedUsers.add(user)
            }
        }

        return processedUsers
    }

    fun processUserToContact(user: UserData): UserData {
        // Generate phone number variants for this user
        val phoneVariants = generatePhoneNumberVariants(user.phoneNumber)

        // Try to find a match with any of the phone number variants
        val matchingContact = cachedContacts.find { contact ->
            val contactVariants = generatePhoneNumberVariants(contact.phoneNumber)

            // Check if any variant of the user's phone number matches any variant of the contact's phone number
            phoneVariants.any { userVariant ->
                contactVariants.any { contactVariant ->
                    userVariant == contactVariant
                }
            }
        }

        return when {
            matchingContact != null -> user.copy(displayName = matchingContact.name)
            else -> user
        }
    }

    fun convertContactsToUsers(registeredUsers: List<UserData>): List<ProcessedUser> {
        val processedUsers = mutableListOf<ProcessedUser>()

        // Create a map for registered users with phone number variants for quick lookup
        val registeredPhoneMap = mutableMapOf<String, UserData>()

        // Pre-process registered users to map all possible phone variants
        registeredUsers.forEach { user ->
            val phoneVariants = generatePhoneNumberVariants(user.phoneNumber)
            phoneVariants.forEach { variant ->
                registeredPhoneMap[variant] = user
            }
        }

        // Process each contact
        for (contact in cachedContacts) {
            // Generate variants for this contact's phone number
            val phoneVariants = generatePhoneNumberVariants(contact.phoneNumber)

            // Try to find a match in registered users using any variant
            val registeredUser = phoneVariants.firstNotNullOfOrNull { variant ->
                registeredPhoneMap[variant]
            }

            if (registeredUser != null) {
                // Contact is a registered user
                val updatedUser = registeredUser.copy(displayName = contact.name)
                processedUsers.add(ProcessedUser(updatedUser, true))
            } else {
                // Contact is not a registered user, create a new user with the "not registered" flag
                val newUser = UserData(
                    uid = "",  // Empty UID for non-registered users
                    displayName = contact.name,
                    phoneNumber = contact.phoneNumber,
                    password = "",
                    userDescription = "",
                    userStatus = "",
                    online = false,
                    lastSeen = "",
                    profilePictureUrl = ""
                )
                processedUsers.add(ProcessedUser(newUser, false))
            }
        }

        return processedUsers
    }
}