package com.example.chat_application.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.chat_application.ContactManager.ProcessedUser
import com.example.chat_application.R
import com.example.chat_application.dataclasses.Story
import com.google.android.material.button.MaterialButton
import java.util.*

class ContactsAdapter(
    private val onContactClick: (ProcessedUser) -> Unit,
    private val onActionButtonClick: (ProcessedUser) -> Unit
) : ListAdapter<ProcessedUser, ContactsAdapter.ContactViewHolder>(ContactDiffCallback()), Filterable {

    private var allItems = listOf<ProcessedUser>()
    private var filteredItems = listOf<ProcessedUser>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = getItem(position)
        holder.bind(contact)
    }

    // Modified to sort registered contacts to the top
    override fun submitList(list: List<ProcessedUser>?) {
        list?.let {
            allItems = it
            // Sort the list with registered users at the top
            filteredItems = sortContactsByRegistration(it)
        }
        super.submitList(filteredItems)
    }

    // Helper function to sort contacts
    private fun sortContactsByRegistration(contacts: List<ProcessedUser>): List<ProcessedUser> {
        return contacts.sortedWith(compareByDescending { it.isRegistered })
    }

    private fun updateTimeDisplay(lastseen: String): String {
        return try {
            val lastSeenTime = lastseen.toLong()
            val timeElapsedMs = System.currentTimeMillis() - lastSeenTime

            when {
                timeElapsedMs < 60000 -> "${(timeElapsedMs / 1000).toInt()}s" // Less than 1 minute
                timeElapsedMs < 3600000 -> "${(timeElapsedMs / 60000).toInt()}m" // Less than 1 hour
                else -> "${(timeElapsedMs / 3600000).toInt()}h" // Display in hours
            }
        } catch (e: NumberFormatException) {
            // Return a default value if parsing fails
            "Unknown"
        }
    }

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)
        private val txtName: TextView = itemView.findViewById(R.id.txtName)
        private val txtPhone: TextView = itemView.findViewById(R.id.txtPhone)
        private val txtStatus: TextView = itemView.findViewById(R.id.txtStatus)
        private val btnAction: MaterialButton = itemView.findViewById(R.id.btnAction)

        fun bind(processedUser: ProcessedUser) {
            val user = processedUser.userData

            // Set contact name
            txtName.text = user.displayName

            // Set phone number
            txtPhone.text = user.phoneNumber

            // Set status
            if (processedUser.isRegistered) {
                txtStatus.text = if (user.online) "Online" else "Last seen: ${updateTimeDisplay(user.lastSeen)}"
                txtStatus.visibility = View.VISIBLE

                // Set action button for registered users
                btnAction.text = "Message"
                btnAction.setIconResource(R.drawable.ic_back)
            } else {
                txtStatus.visibility = View.GONE

                // Set action button for non-registered users (should not happen in this implementation)
                btnAction.text = "Invite"
                btnAction.setIconResource(R.drawable.ic_back)
            }

            // Set avatar image - use placeholder for now
            // In a real app, you would load the image from user.profilePictureUrl
            imgAvatar.setImageResource(R.drawable.ic_person)

            // Set click listeners
            itemView.setOnClickListener { onContactClick(processedUser) }
            btnAction.setOnClickListener { onActionButtonClick(processedUser) }
        }
    }

    class ContactDiffCallback : DiffUtil.ItemCallback<ProcessedUser>() {
        override fun areItemsTheSame(oldItem: ProcessedUser, newItem: ProcessedUser): Boolean {
            return oldItem.userData.uid == newItem.userData.uid
        }

        override fun areContentsTheSame(oldItem: ProcessedUser, newItem: ProcessedUser): Boolean {
            return oldItem == newItem
        }
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filterResults = FilterResults()
                val query = constraint?.toString()?.lowercase(Locale.getDefault())

                val filtered = if (query.isNullOrEmpty()) {
                    allItems
                } else {
                    allItems.filter { item ->
                        item.userData.displayName.lowercase(Locale.getDefault()).contains(query) ||
                                item.userData.phoneNumber.contains(query)
                    }
                }

                // Make sure filtered results also maintain registration sorting
                filteredItems = sortContactsByRegistration(filtered)

                filterResults.values = filteredItems
                filterResults.count = filteredItems.size
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredItems = results?.values as? List<ProcessedUser> ?: emptyList()
                super@ContactsAdapter.submitList(filteredItems)
            }
        }
    }
}