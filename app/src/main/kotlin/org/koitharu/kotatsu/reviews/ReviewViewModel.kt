package org.koitharu.kotatsu.reviews

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.EventFlow
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.call
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
	private val savedStateHandle: SavedStateHandle,
	private val repository: ReviewRepository,
) : BaseViewModel() {

	private val mangaId: Long = requireNotNull(savedStateHandle.get<Long>(ARG_MANGA_ID)) {
		"Manga id required"
	}

	val mangaTitle: String? = savedStateHandle.get<String>(ARG_MANGA_TITLE)

	private val _state = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
	val state: StateFlow<ReviewUiState> = _state

	private val _reviews = MutableStateFlow<List<AniListReview>>(emptyList())
	val reviews: StateFlow<List<AniListReview>> = _reviews

	private val _messages = MutableEventFlow<ReviewMessage>()
	val messages: EventFlow<ReviewMessage>
		get() = _messages

	private var resolvedMediaId: Int? = null

	init {
		reload(force = true)
	}

	fun reload(force: Boolean = false) {
		if (force) {
			_state.value = ReviewUiState.Loading
		}
		launchLoadingJob {
			loadInternal(force)
		}
	}

	fun loadMore() {
		val currentState = _state.value
		if (currentState !is ReviewUiState.Content || !currentState.hasNext || currentState.isLoadingMore) {
			return
		}
		_state.value = currentState.copy(isLoadingMore = true)
		launchJob {
			try {
				val feed = repository.loadMore()
				_reviews.value = feed.reviews.also {
					Log.d(TAG, "Updating reviews flow size=${it.size} (loadMore)")
				}
				_state.value = currentState.copy(
					mediaId = feed.mediaId ?: currentState.mediaId,
					reviews = feed.reviews,
					hasNext = feed.hasNext,
					viewer = feed.viewer,
					myReview = feed.myReview,
					isLoadingMore = false,
				)
				if (feed.mediaId != null) {
					resolvedMediaId = feed.mediaId
				}
			} catch (rateLimit: AniListRateLimitException) {
				handleRateLimit(rateLimit)
				_state.value = currentState.copy(isLoadingMore = false)
			} catch (error: Throwable) {
				if (error is CancellationException) {
					throw error
				}
				errorEvent.call(error)
				_state.value = currentState.copy(isLoadingMore = false)
			}
		}
	}

	fun submitReview(summary: String, body: String, score: Int) {
		val mediaId = resolvedMediaId ?: return
		launchLoadingJob {
			try {
				val feed = repository.saveReview(mediaId, summary, body, score)
				resolvedMediaId = feed.mediaId ?: mediaId
				_reviews.value = feed.reviews.also {
					Log.d(TAG, "Updating reviews flow size=${it.size} (save)")
				}
				_state.value = buildContent(resolvedMediaId ?: mediaId, feed)
				_messages.call(ReviewMessage.Resource(R.string.review_saved))
			} catch (rateLimit: AniListRateLimitException) {
				handleRateLimit(rateLimit)
			} catch (error: Throwable) {
				if (error is CancellationException) {
					throw error
				}
				errorEvent.call(error)
			}
		}
	}

	fun deleteReview() {
		val mediaId = resolvedMediaId ?: return
		val reviewId = (_state.value as? ReviewUiState.Content)?.myReview?.id ?: return
		launchLoadingJob {
			try {
				val feed = repository.deleteReview(mediaId, reviewId)
				resolvedMediaId = feed.mediaId ?: mediaId
				_reviews.value = feed.reviews.also {
					Log.d(TAG, "Updating reviews flow size=${it.size} (delete)")
				}
				_state.value = buildContent(resolvedMediaId ?: mediaId, feed)
				_messages.call(ReviewMessage.Resource(R.string.review_deleted))
			} catch (rateLimit: AniListRateLimitException) {
				handleRateLimit(rateLimit)
			} catch (error: Throwable) {
				if (error is CancellationException) {
					throw error
				}
				errorEvent.call(error)
			}
		}
	}

	suspend fun renderMarkdown(markdown: String): Result<String> = withContext(Dispatchers.IO) {
		runCatching { repository.renderMarkdown(markdown) }
	}

	private suspend fun loadInternal(force: Boolean) {
		if (!force && _state.value is ReviewUiState.Content) {
			return
		}
		_state.value = ReviewUiState.Loading
		when (val access = repository.resolveAccess(mangaId)) {
			is ReviewAccess.NotAuthorized -> {
				resolvedMediaId = null
				_state.value = ReviewUiState.NotAuthorized
				_reviews.value = emptyList()
			}

			is ReviewAccess.NotTracked -> {
				resolvedMediaId = null
				_state.value = ReviewUiState.NotTracked
				_reviews.value = emptyList()
			}

			is ReviewAccess.Granted -> {
				resolvedMediaId = access.mediaId
				val previousContent = _state.value as? ReviewUiState.Content
				try {
					val feed = repository.refresh(access)
					Log.d(TAG, "Feed from repository has ${feed.reviews.size} reviews")
					resolvedMediaId = feed.mediaId ?: resolvedMediaId
					_reviews.value = feed.reviews.also {
						Log.d(TAG, "Updating reviews flow size=${it.size}")
					}
					val content = buildContent(resolvedMediaId ?: access.mediaId, feed)
					Log.d(TAG, "State content reviews size=${content.reviews.size}")
					_state.value = content
				} catch (rateLimit: AniListRateLimitException) {
					handleRateLimit(rateLimit)
					_state.value = previousContent ?: ReviewUiState.Content(
						mediaId = resolvedMediaId ?: access.mediaId,
						reviews = emptyList(),
						hasNext = false,
						viewer = null,
						myReview = null,
						isLoadingMore = false,
					)
				} catch (error: Throwable) {
					if (error is CancellationException) {
						throw error
					}
					errorEvent.call(error)
					_state.value = previousContent ?: ReviewUiState.Content(
						mediaId = resolvedMediaId ?: access.mediaId,
						reviews = emptyList(),
						hasNext = false,
						viewer = null,
						myReview = null,
						isLoadingMore = false,
					)
				}
			}
		}
	}

	private fun handleRateLimit(exception: AniListRateLimitException) {
		_messages.call(ReviewMessage.Resource(R.string.review_rate_limited))
		errorEvent.call(exception)
	}

private fun buildContent(mediaId: Int, feed: ReviewFeed, isLoadingMore: Boolean = false): ReviewUiState.Content {
	return ReviewUiState.Content(
		mediaId = feed.mediaId ?: mediaId,
		reviews = feed.reviews,
		hasNext = feed.hasNext,
		viewer = feed.viewer,
		myReview = feed.myReview,
		isLoadingMore = isLoadingMore,
	)
}

	companion object {
		private const val TAG = "KotatsuReviews"
	}
}

sealed class ReviewUiState {
	data object Loading : ReviewUiState()
	data object NotAuthorized : ReviewUiState()
	data object NotTracked : ReviewUiState()
	data class Content(
		val mediaId: Int,
		val reviews: List<AniListReview>,
		val hasNext: Boolean,
		val viewer: AniListViewer?,
		val myReview: AniListReview?,
		val isLoadingMore: Boolean,
	) : ReviewUiState()
}

sealed class ReviewMessage {
	data class Resource(@StringRes val resId: Int, val formatArgs: Array<out Any> = emptyArray()) : ReviewMessage()
	data class Plain(val value: CharSequence) : ReviewMessage()
}
