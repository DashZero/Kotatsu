
package org.koitharu.kotatsu.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CommentViewModel(private val repository: CommentRepository, private val mangaId: String) : ViewModel() {

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

    private var lastCommentTimestamps = mutableListOf<Long>()

    init {
        viewModelScope.launch {
            repository.getComments(mangaId).collect {
                _comments.value = it
            }
        }
    }

    fun sendComment(text: String, userId: String, userName: String, avatarUrl: String?) {
        val currentTime = System.currentTimeMillis()
        lastCommentTimestamps.removeAll { currentTime - it > 30000 } // Remove timestamps older than 30 seconds

        if (lastCommentTimestamps.size >= 3) {
            // Rate limit exceeded
            return
        }

        val comment = Comment(
            mangaId = mangaId,
            userId = userId,
            userName = userName,
            avatarUrl = avatarUrl,
            text = text,
            timestamp = currentTime
        )

        viewModelScope.launch {
            repository.sendComment(comment)
            lastCommentTimestamps.add(currentTime)
        }
    }

    fun deleteComment(comment: Comment) {
        viewModelScope.launch {
            repository.deleteComment(comment)
        }
    }

    fun reportComment(comment: Comment) {
        viewModelScope.launch {
            repository.reportComment(comment)
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.unsubscribe(mangaId)
    }
}
