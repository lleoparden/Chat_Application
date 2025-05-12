package com.example.chat_application.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.chat_application.R
import com.example.chat_application.HelperFunctions
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

private const val TAG = "ImageUploadService"

/**
 * Object that handles all image-related operations including:
 * - Loading images into views
 * - Saving images locally
 * - Uploading images to ImgBB
 * - Downloading images from URLs
 */
object ImageUploadService {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var isUploadingImage = false

    /**
     * Interface for image upload callbacks
     */
    interface ImageUploadCallback {
        fun onUploadSuccess(imageUrl: String)
        fun onUploadFailure(errorMessage: String)
        fun onUploadProgress(isUploading: Boolean)
    }

    /**
     * Loads an image from a URI into an ImageView
     *
     * @param context The context
     * @param uri The URI of the image
     * @param imageView The ImageView to load the image into
     */
    fun loadImageIntoView(context: Context, uri: Uri?, imageView: ImageView) {
        if (uri != null) {
            Glide.with(context)
                .load(uri)
                .apply(
                    RequestOptions()
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                )
                .into(imageView)
        } else {
            // Set default image
            imageView.setImageResource(R.drawable.ic_person)
        }
    }

    /**
     * Saves an image locally to the app's files directory
     *
     * @param context The context
     * @param imageUri The URI of the image to save
     * @param userId The user ID to use in the filename
     * @param filePrefix Optional prefix for the filename (default: "profile_")
     * @return The path to the saved image or null if saving failed
     */
    fun saveImageLocally(
        context: Context,
        imageUri: Uri,
        userId: String,
        filePrefix: String = "profile_"
    ): String? {
        try {
            // Create file path for local storage
            val imageFile = File(context.filesDir, "${filePrefix}${userId}.jpg")

            // Copy image to app's private storage
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                imageFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Save the local path
            val localImagePath = imageFile.absolutePath
            Log.d(TAG, "Image saved locally: $localImagePath")
            return localImagePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image locally", e)
            return null
        }
    }

    /**
     * Uploads an image to ImgBB
     *
     * @param context The context
     * @param imageUri The URI of the image to upload
     * @param progressBar Optional progress bar to show upload progress
     * @param callback Callback for upload events
     */
    fun uploadImageToImgbb(
        context: Context,
        imageUri: Uri,
        progressBar: ProgressBar? = null,
        callback: ImageUploadCallback
    ) {
        // Show upload progress
        progressBar?.visibility = View.VISIBLE
        isUploadingImage = true
        callback.onUploadProgress(true)
        Toast.makeText(context, "Uploading image to cloud...", Toast.LENGTH_SHORT).show()

        try {
            // Read image data
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Convert bitmap to byte array
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
            val imageBytes = byteArrayOutputStream.toByteArray()
            val base64Image = Base64.getEncoder().encodeToString(imageBytes)

            // Create form data for ImgBB API
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("key", HelperFunctions.IMGBB_API_KEY)
                .addFormDataPart("image", base64Image)
                .build()

            // Create request
            val request = Request.Builder()
                .url(HelperFunctions.IMGBB_API_URL)
                .post(requestBody)
                .build()

            // Execute request asynchronously
            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Handler(Looper.getMainLooper()).post {
                        Log.e(TAG, "Failed to upload image to ImgBB", e)
                        progressBar?.visibility = View.GONE
                        isUploadingImage = false
                        callback.onUploadProgress(false)
                        callback.onUploadFailure("Cloud image upload failed: ${e.message}")
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val responseBody = response.body?.string()
                        val jsonResponse = JSONObject(responseBody)

                        if (jsonResponse.getBoolean("success")) {
                            val data = jsonResponse.getJSONObject("data")
                            val imageUrl = data.getString("url")

                            Handler(Looper.getMainLooper()).post {
                                progressBar?.visibility = View.GONE
                                isUploadingImage = false
                                callback.onUploadProgress(false)
                                callback.onUploadSuccess(imageUrl)
                                Toast.makeText(
                                    context,
                                    "Image uploaded to cloud successfully!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Handler(Looper.getMainLooper()).post {
                                val errorMessage = jsonResponse.optString("error", "Unknown error")
                                progressBar?.visibility = View.GONE
                                isUploadingImage = false
                                callback.onUploadProgress(false)
                                callback.onUploadFailure("Cloud upload failed: $errorMessage")
                            }
                        }
                    } catch (e: Exception) {
                        Handler(Looper.getMainLooper()).post {
                            Log.e(TAG, "Error parsing ImgBB response", e)
                            progressBar?.visibility = View.GONE
                            isUploadingImage = false
                            callback.onUploadProgress(false)
                            callback.onUploadFailure("Failed to process uploaded image: ${e.message}")
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing image for upload", e)
            progressBar?.visibility = View.GONE
            isUploadingImage = false
            callback.onUploadProgress(false)
            callback.onUploadFailure("Failed to prepare image for upload: ${e.message}")
        }
    }

    /**
     * Downloads an image from a URL and saves it locally
     *
     * @param context The context
     * @param imageUrl The URL of the image to download
     * @param userId The user ID to use in the filename
     * @param filePrefix Optional prefix for the filename (default: "profile_")
     * @return The path to the saved image or null if downloading failed
     */
    fun downloadAndSaveImageLocally(
        context: Context,
        imageUrl: String,
        userId: String,
        filePrefix: String = "profile_",
        callback: ((success: Boolean, localPath: String?) -> Unit)? = null
    ) {
        Thread {
            var success = false
            var localPath: String? = null

            try {
                // Create a request
                val request = Request.Builder()
                    .url(imageUrl)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val inputStream = response.body?.byteStream()
                        if (inputStream != null) {
                            // Save to local file
                            val imageFile = File(context.filesDir, "${filePrefix}${userId}.jpg")
                            imageFile.outputStream().use { output ->
                                inputStream.copyTo(output)
                            }

                            // Update local path
                            localPath = imageFile.absolutePath
                            success = true
                            Log.d(TAG, "Remote image downloaded and saved locally: $localPath")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download image from URL", e)
                success = false
            }

            // Call the callback on the main thread if provided
            callback?.let {
                Handler(Looper.getMainLooper()).post {
                    it(success, localPath)
                }
            }
        }.start()
    }

    /**
     * Checks if an image upload is currently in progress
     *
     * @return true if an upload is in progress, false otherwise
     */
    fun isUploadInProgress(): Boolean {
        return isUploadingImage
    }
}