package org.koitharu.kotatsu.threads

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.EventFlow
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.call
import org.koitharu.kotatsu.reviews.AniListViewer
import javax.inject.Inject

const val THREAD_EXTRA_MEDIA_ID = "threadMediaId"

@HiltViewModel
class ThreadListViewModel @Inject constructor(
	private val savedStateHandle: SavedStateHandle,
	private val repository: ThreadRepository,
) : BaseViewModel() {

	private val mangaId: Long = requireNotNull(savedStateHandle.get<Long>(THREAD_ARG_MANGA_ID)) {
		"Manga id required"
	}

	val mangaTitle: String? = savedStateHandle.get<String>(THREAD_ARG_MANGA_TITLE)

	private val _state = MutableStateFlow<ThreadListUiState>(ThreadListUiState.Loading)
	val state: StateFlow<ThreadListUiState> = _state

	private val _messages = MutableEventFlow<ThreadMessage>()
	val messages: EventFlow<ThreadMessage> = _messages

	private val _openThread = MutableEventFlow<AniListThread>()
	val openThread: EventFlow<AniListThread> = _openThread

	private var resolvedMediaId: Int? = savedStateHandle.get<Int>(THREAD_EXTRA_MEDIA_ID)
	private var currentSort: ThreadSort = ThreadSort.RECENT_ACTIVITY

	init {
		reload(force = true)
	}

	fun reload(force: Boolean = false) {
		if (!force && _state.value is ThreadListUiState.Content) {
			return
		}
		_state.value = ThreadListUiState.Loading
		launchLoadingJob {
			loadInternal(force)
		}
	}

	fun selectSort(sort: ThreadSort) {
		if (currentSort == sort) {
			return
		}
		currentSort = sort
		reload(force = true)
	}

	fun loadMore() {
		val current = _state.value as? ThreadListUiState.Content ?: return
		if (!current.hasNext || current.isLoadingMore) {
			return
		}
		_state.value = current.copy(isLoadingMore = true)
		launchJob {
			runCatching {
				repository.loadMore(currentSort)
			}.onSuccess { feed ->
				val mediaId = feed.mediaId ?: resolvedMediaId ?: current.mediaId
				resolvedMediaId = mediaId
				savedStateHandle[THREAD_EXTRA_MEDIA_ID] = mediaId
				_state.value = current.copy(
					mediaId = mediaId,
					threads = feed.threads,
					hasNext = feed.hasNext,
					viewer = feed.viewer,
					isLoadingMore = false,
				)
			}.onFailure { error ->
				_state.value = current.copy(isLoadingMore = false)
				errorEvent.call(error)
			}
		}
	}

	fun onThreadSelected(thread: AniListThread) {
		_openThread.call(thread)
	}

	fun createThread(title: String, body: String) {
		val mediaId = resolvedMediaId ?: return
		launchLoadingJob {
			runCatching {
				repository.saveThread(mediaId, title, body)
			}.onSuccess { feed ->
				val resolved = feed.mediaId ?: mediaId
				resolvedMediaId = resolved
				savedStateHandle[THREAD_EXTRA_MEDIA_ID] = resolved
				_state.value = ThreadListUiState.Content(
					mediaId = resolved,
					sort = currentSort,
					threads = feed.threads,
					hasNext = feed.hasNext,
					viewer = feed.viewer,
					isLoadingMore = false,
				)
				_messages.call(ThreadMessage.Resource(R.string.thread_created))
			}.onFailure { error ->
				errorEvent.call(error)
			}
		}
	}

	private suspend fun loadInternal(force: Boolean) {
		when (val access = repository.resolveAccess(mangaId)) {
			ThreadAccess.NotAuthorized -> {
				resolvedMediaId = null
				_state.value = ThreadListUiState.NotAuthorized
			}

			ThreadAccess.NotTracked -> {
				resolvedMediaId = null
				_state.value = ThreadListUiState.NotTracked
			}

			is ThreadAccess.Granted -> {
				resolvedMediaId = access.mediaId
				runCatching {
					repository.refresh(access, currentSort)
				}.onSuccess { feed ->
					val mediaId = feed.mediaId ?: access.mediaId
					resolvedMediaId = mediaId
					savedStateHandle[THREAD_EXTRA_MEDIA_ID] = mediaId
					_state.value = ThreadListUiState.Content(
						mediaId = mediaId,
						sort = currentSort,
						threads = feed.threads,
						hasNext = feed.hasNext,
						viewer = feed.viewer,
						isLoadingMore = false,
					)
				}.onFailure { error ->
					errorEvent.call(error)
					_state.value = ThreadListUiState.Content(
						mediaId = resolvedMediaId ?: access.mediaId,
						sort = currentSort,
						threads = emptyList(),
						hasNext = false,
						viewer = null,
						isLoadingMore = false,
					)
				}
			}
		}
	}
}

sealed class ThreadListUiState {
	object Loading : ThreadListUiState()
	object NotAuthorized : ThreadListUiState()
	object NotTracked : ThreadListUiState()
	data class Content(
		val mediaId: Int,
		val sort: ThreadSort,
		val threads: List<AniListThread>,
		val hasNext: Boolean,
		val viewer: AniListViewer?,
		val isLoadingMore: Boolean,
	) : ThreadListUiState()
}

sealed class ThreadMessage {
	data class Resource(@StringRes val resId: Int, val args: Array<out Any> = emptyArray()) : ThreadMessage()
	data class Plain(val value: CharSequence) : ThreadMessage()
}
