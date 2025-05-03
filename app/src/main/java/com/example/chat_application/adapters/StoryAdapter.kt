package com.example.chat_application.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.chat_application.R
import com.example.chat_application.adapters.StoryAdapter.StoryViewHolder
import com.example.chat_application.dataclasses.Stories
import com.example.chat_application.HelperFunctions


class StoryAdapter(
    private var allStories: List<Stories>,
    private val onStoryClickListener: OnStoryClickListener,
) : RecyclerView.Adapter<StoryViewHolder>() {

    private val TAG = "StoryAdapter"
    // Filtered list of stories that only contains stories with UIDs in local_user.json
    // Will be populated after initializeFilteredStories is called
    private var filteredStories: List<Stories> = emptyList()

    interface OnStoryClickListener {
        fun onStoryClick(story: Stories)
    }

    inner class StoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.storyNameTextView)
        private val storyCountView: TextView = itemView.findViewById(R.id.storyCountView)
        private val avatarImageView: ImageView = itemView.findViewById(R.id.storyProfileImageView)


        fun storyBind(story: Stories) {
            try {
                val userdata = HelperFunctions.loadUserById(story.uid, nameTextView.context)
                // This should always be non-null because we've filtered the stories
                if (userdata != null) {
                    nameTextView.text = userdata.displayName
                    storyCountView.text = "${story.stories?.size} stories available"
                    HelperFunctions.loadImageFromUrl(story.profilePictureUrl ?: userdata.profilePictureUrl, avatarImageView)

                    // Set click listener
                    itemView.setOnClickListener {
                        Log.i(TAG, "User clicked: ${userdata.displayName} (${story.uid})")
                        onStoryClickListener.onStoryClick(story)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error binding user data", e)
            }
        }
    }

    /**
     * Filters the stories list to only include stories with UIDs that exist in the local_user.json file
     */
    /**
     * Initialize the filtered list with the valid stories
     * This should be called after we have a context available
     */
    fun initializeFilteredStories(context: android.content.Context) {
        filteredStories = filterValidStories(allStories, context)
        notifyDataSetChanged()
    }

    /**
     * Filters the stories list to only include stories with UIDs that exist in the local_user.json file
     */
    private fun filterValidStories(stories: List<Stories>, context: android.content.Context): List<Stories> {
        val validStories = mutableListOf<Stories>()

        for (story in stories) {
            // Try to load the user data for this story
            val userData = HelperFunctions.loadUserById(story.uid, context)

            // Only add to valid stories if the user exists
            if (userData != null) {
                validStories.add(story)
            } else {
                Log.d(TAG, "Filtered out story with UID: ${story.uid} (user not found in local_user.json)")
            }
        }

        Log.d(TAG, "Filtered stories from ${stories.size} to ${validStories.size}")
        return validStories
    }

    /**
     * Updates the adapter with a new list of stories and refilters
     */
    fun updateStories(newStories: List<Stories>, context: android.content.Context) {
        allStories = newStories
        filteredStories = filterValidStories(newStories, context)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_story, parent, false)
        return StoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: StoryViewHolder, position: Int) {
        Log.d(TAG, "Binding ViewHolder at position $position")
        try {
            holder.storyBind(filteredStories[position])
        } catch (e: Exception) {
            Log.e(TAG, "Error binding view holder at position $position", e)
        }
    }

    override fun getItemCount(): Int {
        val count = filteredStories.size
        Log.v(TAG, "getItemCount(): $count")
        return count
    }
}