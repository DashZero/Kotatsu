package org.koitharu.kotatsu.comments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CommentViewModel @Inject constructor(
    private val repository: CommentRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Key for SavedStateHandle to retrieve mangaId. Ensure this key is used when passing arguments.
    companion object {
        const val MANGA_ID_KEY = "mangaId"
    }

    private val mangaId: String = savedStateHandle.get<String>(MANGA_ID_KEY)
        ?: throw IllegalStateException("mangaId not found in SavedStateHandle. Make sure it's passed as an argument to the fragment/activity.")

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
            // You might want to expose this state to the UI, e.g., via another StateFlow
            return
        }

        val comment = Comment(
            mangaId = mangaId, // Now correctly uses the mangaId from SavedStateHandle
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
            // Ensure reportComment in repository is implemented
            repository.reportComment(comment)
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.unsubscribe(mangaId)
    }
}
