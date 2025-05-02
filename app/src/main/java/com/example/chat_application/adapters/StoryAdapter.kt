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
    private var stories: List<Stories>,
    private val onStoryClickListener: OnStoryClickListener,
) : RecyclerView.Adapter<StoryViewHolder>() {

    private val TAG = "StoryAdapter"

    interface OnStoryClickListener {
        fun onStoryClick(story: Stories)
    }

    inner class StoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.storyNameTextView)
        private val storyCountView: TextView = itemView.findViewById(R.id.storyCountView)
        private val avatarImageView: ImageView = itemView.findViewById(R.id.storyProfileImageView)


        fun storyBind(story: Stories) {
            try {
                nameTextView.text = story.displayName
                storyCountView.text = "${story.stories?.size} stories available"
                HelperFunctions.loadImageFromUrl(story.profilePictureUrl, avatarImageView)

                // Set click listener
                itemView.setOnClickListener {
                    Log.i(TAG, "User clicked: ${story.displayName} (${story.uid})")
                    onStoryClickListener.onStoryClick(story)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error binding user data", e)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_story, parent, false)
        return StoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: StoryViewHolder, position: Int) {
        Log.d(TAG, "Binding ViewHolder at position $position")
        try {
            holder.storyBind(stories[position])
        } catch (e: Exception) {
            Log.e(TAG, "Error binding view holder at position $position", e)
        }
    }

    override fun getItemCount(): Int {
        val count = stories.size
        Log.v(TAG, "getItemCount(): $count")
        return count
    }
}