package org.koitharu.kotatsu.comments

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

class CommentViewModel(private val context: Context, private val mangaId: String, private val repository: CommentRepository) : ViewModel() {

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

    private val _inputMessage = MutableStateFlow("")
    val inputMessage: StateFlow<String> = _inputMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isOffline = MutableStateFlow(false) // TODO: Implement actual network connectivity check
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _localMutedUsers = MutableStateFlow<Set<String>>(emptySet())
    val localMutedUsers: StateFlow<Set<String>> = _localMutedUsers.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.loadAndSubscribe(mangaId)
            repository.getCommentsFlow(mangaId).collect { newComments ->
                _comments.value = newComments.filter { !it.deleted && !(_localMutedUsers.value.contains(it.userId)) }
            }
        }

        // Initialize settings
        CommentsSettings.init(context)

        // Observe banned status
        viewModelScope.launch {
            CommentsSettings.bannedUntil.collect { bannedUntil ->
                if (System.currentTimeMillis() < bannedUntil) {
                    // User is banned, update moderation state
                    Moderation.banUser(CommentsSettings.userId.value ?: "")
                } else {
                    Moderation.unbanUser(CommentsSettings.userId.value ?: "")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch(Dispatchers.IO) {
            repository.unsubscribe(mangaId)
        }
    }

    fun onInputMessageChange(newMessage: String) {
        _inputMessage.value = newMessage
        _errorMessage.value = null // Clear error when user types
    }

    fun sendComment() {
        val userId = CommentsSettings.userId.value
        val userName = CommentsSettings.displayName.value
        val avatarUrl = CommentsSettings.avatarUrl.value
        val rawText = _inputMessage.value.trim()

        if (userId == null) {
            _errorMessage.value = "User ID not available."
            return
        }

        if (rawText.isBlank()) {
            _errorMessage.value = "Comment cannot be empty."
            return
        }

        if (rawText.length > 500) {
            _errorMessage.value = "Comment too long (max 500 characters)."
            return
        }

        if (Moderation.isBanned(userId)) {
            _errorMessage.value = "You are banned until ${CommentsSettings.getBannedUntil()} due to community reports."
            return
        }

        if (Moderation.isRateLimited(userId)) {
            _errorMessage.value = "You are sending comments too fast. Please wait."
            return
        }

        val normalizedText = Moderation.normalizeText(rawText)
        if (Moderation.isDuplicate(userId, normalizedText)) {
            _errorMessage.value = "Duplicate comment detected. Please say something new."
            return
        }

        var processedText = rawText
        if (CommentsSettings.enableModeration.value) {
            processedText = Moderation.processText(processedText)
            val filteredText = Moderation.filterProfanity(processedText)

            if (Moderation.isFullyCensored(processedText, filteredText)) {
                _errorMessage.value = "Comment fully censored. Please revise."
                return
            }
            processedText = filteredText
        }

        _isSending.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val comment = Comment(
                    mangaId = mangaId,
                    userId = userId,
                    userName = userName,
                    avatarUrl = avatarUrl ?: CommentsSettings.getDiceBearAvatarUrl(userName),
                    text = processedText,
                    timestamp = System.currentTimeMillis()
                )
                repository.sendComment(comment)
                _inputMessage.value = ""
            } catch (e: Exception) {
                _errorMessage.value = "Failed to send comment: ${e.message}"
            } finally {
                _isSending.value = false
            }
        }
    }

    fun deleteComment(comment: Comment) {
        val userId = CommentsSettings.userId.value
        if (userId == null || comment.userId != userId) {
            _errorMessage.value = "You can only delete your own comments."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteComment(comment.mangaId, comment.id)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete comment: ${e.message}"
            }
        }
    }

    fun reportComment(comment: Comment) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.reportComment(comment.mangaId, comment.id)
                // Check for ban condition after reporting
                val updatedComment = _comments.value.find { it.id == comment.id }?.copy(reportCount = comment.reportCount + 1)
                if (updatedComment != null && updatedComment.reportCount >= 5) {
                    // This is a simplified ban logic. In a real app, this would be server-side.
                    // For now, we'll ban the user locally if their comment reaches 5 reports.
                    CommentsSettings.setBannedUntil(System.currentTimeMillis() + 90L * 24 * 60 * 60 * 1000)
                    Moderation.banUser(updatedComment.userId)
                    _errorMessage.value = "User ${updatedComment.userName} has been banned for 90 days."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to report comment: ${e.message}"
            }
        }
    }

    fun toggleMuteUser(userId: String) {
        _localMutedUsers.update { current ->
            if (current.contains(userId)) {
                current - userId
            } else {
                current + userId
            }
        }
        // Re-filter comments based on new mute list
        _comments.value = _comments.value.filter { !it.deleted && !(_localMutedUsers.value.contains(it.userId)) }
    }

    // Factory for ViewModel creation
    class Factory(private val context: Context, private val mangaId: String, private val repository: CommentRepository) : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CommentViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CommentViewModel(context, mangaId, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
