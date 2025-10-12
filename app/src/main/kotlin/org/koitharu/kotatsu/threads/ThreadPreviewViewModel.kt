package org.koitharu.kotatsu.threads

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.EventFlow
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.call
import org.koitharu.kotatsu.reviews.AniListViewer
import javax.inject.Inject

internal const val THREAD_ARG_MANGA_ID = "mangaId"
internal const val THREAD_ARG_MANGA_TITLE = "mangaTitle"

@HiltViewModel
class ThreadPreviewViewModel @Inject constructor(
	private val savedStateHandle: SavedStateHandle,
	private val repository: ThreadRepository,
) : BaseViewModel() {

	private val mangaId: Long = requireNotNull(savedStateHandle.get<Long>(THREAD_ARG_MANGA_ID)) {
		"Manga id required"
	}

	val mangaTitle: String? = savedStateHandle.get<String>(THREAD_ARG_MANGA_TITLE)

	private val _state = MutableStateFlow<ThreadPreviewState>(ThreadPreviewState.Loading)
	val state: StateFlow<ThreadPreviewState> = _state

	private val _openThreadDetails = MutableEventFlow<AniListThread>()
	val openThreadDetails: EventFlow<AniListThread> = _openThreadDetails

	private var resolvedMediaId: Int? = null

	init {
		reload(force = true)
	}

	fun reload(force: Boolean = false) {
		if (!force && _state.value is ThreadPreviewState.Content) {
			return
		}
		_state.value = ThreadPreviewState.Loading
		launchLoadingJob {
			loadInternal()
		}
	}

	fun onThreadSelected(thread: AniListThread) {
		_openThreadDetails.call(thread)
	}

	private suspend fun loadInternal() {
		when (val access = repository.resolveAccess(mangaId)) {
			ThreadAccess.NotAuthorized -> {
				resolvedMediaId = null
				_state.value = ThreadPreviewState.NotAuthorized
			}

			ThreadAccess.NotTracked -> {
				resolvedMediaId = null
				_state.value = ThreadPreviewState.NotTracked
			}

			is ThreadAccess.Granted -> {
				resolvedMediaId = access.mediaId
				runCatching {
					repository.fetchPreview(access)
				}.onSuccess { preview ->
					resolvedMediaId = preview.mediaId
					_state.value = ThreadPreviewState.Content(
						mediaId = preview.mediaId,
						threads = preview.threads,
						viewer = preview.viewer,
					)
				}.onFailure { error ->
					resolvedMediaId = access.mediaId
					errorEvent.call(error)
					_state.value = ThreadPreviewState.Content(
						mediaId = access.mediaId,
						threads = emptyList(),
						viewer = null,
					)
				}
			}
		}
	}
}

sealed interface ThreadPreviewState {
	object Loading : ThreadPreviewState
	object NotAuthorized : ThreadPreviewState
	object NotTracked : ThreadPreviewState
	data class Content(
		val mediaId: Int,
		val threads: List<AniListThread>,
		val viewer: AniListViewer?,
	) : ThreadPreviewState {

		val isEmpty: Boolean
			get() = threads.isEmpty()
	}
}
