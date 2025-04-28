package com.example.chat_application

import kotlinx.parcelize.Parcelize
import java.util.*

// Binary Search Tree Node for name-based searching

class ChatBSTNode(
    val chat: Chat,
    var left: ChatBSTNode? = null,
    var right: ChatBSTNode? = null
)

class ChatManager {
    private val chatStack = Stack<Chat>()
    private var bstRoot: ChatBSTNode? = null

    // Add a new chat to both data structures
    fun push(chat: Chat) {
        chatStack.push(chat)
        insertIntoBST(chat)
        sortByTimestamp()
    }

    fun getChatById(id: String): Chat? {
        // Direct lookup in the stack - O(n) operation
        return chatStack.find { it.id == id }

        // Note: We could potentially optimize this in the future by
        // maintaining a separate HashMap<String, Chat> for ID-based lookups
        // if this operation becomes a performance bottleneck
    }
    // Add this method to ChatManager class
    fun updateDisplayNames(userMap: Map<String, String>) {
        val allChats = getAll().toMutableList()
        var hasUpdates = false

        for (i in allChats.indices) {
            val chat = allChats[i]

            // For direct chats, update the display name based on the other participant
            if (chat.type == "direct") {
                // Find the other participant's ID (not the current user)
                val otherUserId = chat.participantIds.find { it != UserSettings.userId }

                // If we have the other user's display name in our map, update it
                if (otherUserId != null && userMap.containsKey(otherUserId)) {
                    val newDisplayName = userMap[otherUserId] ?: ""
                    if (chat.displayName != newDisplayName && newDisplayName.isNotEmpty()) {
                        // Create updated chat with new display name
                        allChats[i] = chat.copy(displayName = newDisplayName)
                        hasUpdates = true
                    }
                }
            }
            // For group chats, you might want different logic, or leave displayName as is
        }

        // Only update the chat manager and save if we made changes
        if (hasUpdates) {
            clear()
            pushAll(allChats)
        }
    }

    // Add multiple chats at once
    fun pushAll(chats: List<Chat>) {
        chatStack.addAll(chats)
        for (chat in chats) {
            insertIntoBST(chat)
        }
        sortByTimestamp()
    }

    // BST insertion - now using name instead of name for searching
    private fun insertIntoBST(chat: Chat) {
        if (bstRoot == null) {
            bstRoot = ChatBSTNode(chat)
            return
        }

        insertNode(bstRoot!!, chat)
    }

    private fun insertNode(node: ChatBSTNode, chat: Chat): ChatBSTNode {
        // Compare chat names lexicographically
        val comparison = chat.name.compareTo(node.chat.name, ignoreCase = true)

        when {
            comparison < 0 -> {
                // Insert to the left subtree if name comes before current node
                node.left = node.left?.let { insertNode(it, chat) } ?: ChatBSTNode(chat)
            }
            comparison > 0 -> {
                // Insert to the right subtree if name comes after current node
                node.right = node.right?.let { insertNode(it, chat) } ?: ChatBSTNode(chat)
            }
            else -> {
                // Names are equal, decide based on ID
                if (chat.id != node.chat.id) {
                    node.right = node.right?.let { insertNode(it, chat) } ?: ChatBSTNode(chat)
                }
            }
        }

        return node
    }

    // Find a chat by name using BST (O(log n) search)
    fun findByName(name: String): Chat? {
        return findNodeByName(bstRoot, name)?.chat
    }

    private fun findNodeByName(node: ChatBSTNode?, name: String): ChatBSTNode? {
        if (node == null) return null

        val comparison = name.compareTo(node.chat.name, ignoreCase = true)

        return when {
            comparison < 0 -> findNodeByName(node.left, name)
            comparison > 0 -> findNodeByName(node.right, name)
            else -> node // Found a match
        }
    }

    // Remove a chat from both data structures
    fun removeById(id: String): Boolean {
        val chat = chatStack.find { it.id == id } ?: return false
        val removed = chatStack.remove(chat)

        if (removed) {
            rebuildBST() // Rebuild BST after removal
        }

        return removed
    }

    // Update a chat in both data structures
    fun updateById(id: String, updatedChat: Chat): Boolean {
        val index = chatStack.indexOfFirst { it.id == id }
        if (index != -1) {
            val oldChat = chatStack[index]
            chatStack[index] = updatedChat

            // If name changed, we need to rebuild the BST
            if (oldChat.name != updatedChat.name) {
                rebuildBST()
            }

            sortByTimestamp()
            return true
        }
        return false
    }

    // Rebuild the BST from scratch using the current list of chats
    private fun rebuildBST() {
        bstRoot = null
        for (chat in chatStack) {
            insertIntoBST(chat)
        }
    }

    fun pop(): Chat? {
        if (chatStack.isEmpty()) return null
        val chat = chatStack.pop()
        rebuildBST()  // Update BST after removal
        return chat
    }

    fun peek(): Chat? {
        return if (chatStack.isEmpty()) null else chatStack.peek()
    }

    fun size(): Int = chatStack.size

    fun isEmpty(): Boolean = chatStack.isEmpty()

    fun clear() {
        chatStack.clear()
        bstRoot = null
    }

    fun get(index: Int): Chat = chatStack[index]

    fun getAt(index: Int): Chat = chatStack[index]

    fun getAll(): List<Chat> = chatStack.toList()

    // Sort chats by timestamp (newest first)
    private fun sortByTimestamp() {
        val sortedList = chatStack.sortedByDescending { it.timestamp }
        chatStack.clear()
        chatStack.addAll(sortedList)
    }

    // For finding partial matches using the BST (using name)
    fun findPartialMatches(query: String): List<Chat> {
        val results = mutableListOf<Chat>()
        findPartialMatchesInSubtree(bstRoot, query.lowercase(), results)
        return results
    }

    private fun findPartialMatchesInSubtree(node: ChatBSTNode?, query: String, results: MutableList<Chat>) {
        if (node == null) return

        // First check left subtree (smaller names)
        findPartialMatchesInSubtree(node.left, query, results)

        // Check current node
        if (node.chat.name.lowercase().contains(query)) {
            results.add(node.chat)
        }

        // Check right subtree (larger names)
        findPartialMatchesInSubtree(node.right, query, results)
    }
}