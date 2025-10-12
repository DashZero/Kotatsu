package org.koitharu.kotatsu.threads

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.EventFlow
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.call
import org.koitharu.kotatsu.reviews.AniListViewer
import javax.inject.Inject

const val THREAD_EXTRA_THREAD_ID = "threadId"

@HiltViewModel
class ThreadDetailViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val repository: ThreadRepository,
) : BaseViewModel() {

	private val threadId: Long = requireNotNull(savedStateHandle.get<Long>(THREAD_EXTRA_THREAD_ID)) {
		"Thread id required"
	}

	val mangaTitle: String? = savedStateHandle.get<String>(THREAD_ARG_MANGA_TITLE)

	private val _state = MutableStateFlow<ThreadDetailUiState>(ThreadDetailUiState.Loading)
	val state: StateFlow<ThreadDetailUiState> = _state

	private val _messages = MutableEventFlow<ThreadMessage>()
	val messages: EventFlow<ThreadMessage> = _messages

	init {
		reload()
	}

	fun reload() {
		_state.value = ThreadDetailUiState.Loading
		launchLoadingJob {
			loadInitial()
		}
	}

	fun loadMore() {
		val content = _state.value as? ThreadDetailUiState.Content ?: return
		if (!content.hasMore || content.isLoadingMore) {
			return
		}
		_state.update {
			(it as? ThreadDetailUiState.Content)?.copy(isLoadingMore = true) ?: it
		}
		launchJob {
			runCatching {
				repository.fetchComments(threadId, page = content.currentPage + 1)
			}.onSuccess { page ->
				_state.update {
					(it as? ThreadDetailUiState.Content)?.copy(
						comments = it.comments + page.comments.flatten(),
						currentPage = page.currentPage,
						hasMore = page.hasNextPage,
						isLoadingMore = false,
					) ?: it
				}
			}.onFailure { error ->
				_state.update {
					(it as? ThreadDetailUiState.Content)?.copy(isLoadingMore = false) ?: it
				}
				errorEvent.call(error)
			}
		}
	}

	fun postComment(body: String) {
		val content = _state.value as? ThreadDetailUiState.Content ?: return
		if (content.viewer == null || body.isBlank()) {
			return
		}
		launchLoadingJob {
			runCatching {
				repository.saveComment(threadId, parentCommentId = null, comment = body)
			}.onSuccess { comment ->
				_state.update {
					(it as? ThreadDetailUiState.Content)?.copy(
						comments = listOf(ThreadCommentItem(comment, depth = 0)) + it.comments
					) ?: it
				}
				_messages.call(ThreadMessage.Resource(R.string.thread_comment_posted))
			}.onFailure { error ->
				errorEvent.call(error)
			}
		}
	}

	private suspend fun loadInitial() {
		runCatching {
			val result = repository.fetchThread(threadId) ?: throw IllegalStateException("Thread not found")
			val viewer = repository.getViewer()
			Triple(result.thread, result.commentPage, viewer)
		}.onSuccess { (thread, page, viewer) ->
			val comments = page.comments.flatten()
			_state.value = ThreadDetailUiState.Content(
				thread = thread,
				comments = comments,
				viewer = viewer,
				currentPage = page.currentPage,
				hasMore = page.hasNextPage,
				isLoadingMore = false,
			)
		}.onFailure { error ->
			errorEvent.call(error)
			_state.value = ThreadDetailUiState.Error
		}
	}
}

sealed class ThreadDetailUiState {
	object Loading : ThreadDetailUiState()
	object Error : ThreadDetailUiState()
	data class Content(
		val thread: AniListThread,
		val comments: List<ThreadCommentItem>,
		val viewer: AniListViewer?,
		val currentPage: Int,
		val hasMore: Boolean,
		val isLoadingMore: Boolean,
	) : ThreadDetailUiState()
}

data class ThreadCommentItem(
	val comment: AniListThreadComment,
	val depth: Int,
)

private fun List<AniListThreadComment>.flatten(depth: Int = 0): List<ThreadCommentItem> {
	val result = ArrayList<ThreadCommentItem>()
	for (comment in this) {
		result.add(ThreadCommentItem(comment, depth))
		if (comment.children.isNotEmpty()) {
			result.addAll(comment.children.flatten(depth + 1))
		}
	}
	return result
}
