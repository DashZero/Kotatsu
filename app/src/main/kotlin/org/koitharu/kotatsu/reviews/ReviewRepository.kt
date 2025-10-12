package org.koitharu.kotatsu.reviews

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.exception.GraphQLException
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.scrobbling.anilist.domain.AniListScrobbler
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerService
import org.koitharu.kotatsu.scrobbling.common.data.ScrobblingEntity
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val FIRST_PAGE = 1
private const val DEFAULT_PAGE_SIZE = 10
private const val PREVIEW_PAGE_SIZE = 3

@Singleton
class ReviewRepository @Inject constructor(
	private val client: AniListReviewClient,
	private val database: MangaDatabase,
	private val aniListScrobbler: AniListScrobbler,
	private val draftStorage: ReviewDraftStorage,
) {

	private var cachedMediaId: Int? = null
	private var cachedReviews: MutableList<AniListReview> = mutableListOf()
	private var cachedPage: Int = 0
	private var cachedHasNext: Boolean = false
	private var cachedViewer: AniListViewer? = null
	private var cachedMyReview: AniListReview? = null
	private var cachedMediaListId: Int? = null
	private var currentMangaId: Long = 0L

	suspend fun resolveAccess(mangaId: Long): ReviewAccess = withContext(Dispatchers.IO) {
		currentMangaId = mangaId
		if (!aniListScrobbler.isEnabled) {
			return@withContext ReviewAccess.NotAuthorized
		}
		val entity = database.getScrobblingDao().find(ScrobblerService.ANILIST.id, mangaId)
			?: return@withContext ReviewAccess.NotTracked
		cachedMediaListId = entity.id
		return@withContext ReviewAccess.Granted(
			mediaId = entity.targetId.toInt(),
			mediaListId = entity.id,
		)
	}

	suspend fun getReviewPreviews(access: ReviewAccess.Granted): List<AniListReview> {
		return client.fetchReviews(
			mediaId = access.mediaId,
			page = FIRST_PAGE,
			perPage = PREVIEW_PAGE_SIZE
		).reviews
	}

	suspend fun refresh(access: ReviewAccess.Granted): ReviewFeed {
		cachedMediaListId = access.mediaListId
		return refreshInternal(access.mediaId, allowRetry = true)
	}

	private suspend fun refreshInternal(mediaId: Int, allowRetry: Boolean): ReviewFeed {
		return try {
			if (cachedMediaId != mediaId) {
				resetCache(mediaId)
			}
			val viewer = ensureViewer()
			val page = client.fetchReviews(mediaId, FIRST_PAGE, DEFAULT_PAGE_SIZE)
			Log.d(TAG, "Fetched ${page.reviews.size} reviews for mediaId=$mediaId (allowRetry=$allowRetry)")
			if (page.reviews.isEmpty() && allowRetry) {
				resolveMediaIdFallback(mediaId)?.let { fallbackId ->
					Log.d(TAG, "Retrying reviews with fallback mediaId=$fallbackId")
					return refreshInternal(fallbackId, allowRetry = false)
				}
			}
			cachedPage = page.currentPage
			cachedHasNext = page.hasNextPage
			cachedReviews = page.reviews.map { it.withMediaId(mediaId) }.toMutableList()
			Log.d(TAG, "Cached reviews size=${cachedReviews.size}")
			cachedMyReview = try {
				viewer?.let { user ->
					client.fetchUserReview(mediaId, user.id)
				}
			} catch (e: IOException) {
				if (e.message?.contains("404") == true) {
					null
				} else {
					throw e
				}
			}
			integrateMyReview()
			val feed = buildFeed()
			Log.d(TAG, "Returning feed with ${feed.reviews.size} reviews")
			feed
		} catch (error: GraphQLException) {
			if (!allowRetry) {
				throw error
			}
			Log.w(TAG, "GraphQL error for mediaId=$mediaId, attempting fallback", error)
			val fallbackId = resolveMediaIdFallback(mediaId) ?: throw error
			Log.d(TAG, "Retrying after GraphQL error with fallback mediaId=$fallbackId")
			return refreshInternal(fallbackId, allowRetry = false)
		} catch (error: Throwable) {
			Log.e(TAG, "Failed to build review feed for mediaId=$mediaId (Ask Gemini)", error)
			throw error
		}
	}

	suspend fun loadMore(): ReviewFeed {
		val mediaId = cachedMediaId ?: throw IllegalStateException("Reviews not initialized")
		if (!cachedHasNext) {
			return buildFeed()
		}
		val nextPage = cachedPage + 1
		val page = client.fetchReviews(mediaId, nextPage, DEFAULT_PAGE_SIZE)
		cachedPage = page.currentPage
		cachedHasNext = page.hasNextPage
		val myReviewId = cachedMyReview?.id
		val entries = page.reviews.map { it.withMediaId(mediaId) }
		if (myReviewId != null) {
			cachedReviews.addAll(entries.filterNot { it.id == myReviewId })
		} else {
			cachedReviews.addAll(entries)
		}
		return buildFeed()
	}

	suspend fun saveReview(mediaId: Int, summary: String, body: String, score: Int): ReviewFeed {
		val review = client.saveReview(mediaId, summary, body, score)
		cachedMyReview = review
		cachedMediaId = mediaId
		removeReviewById(review.id)
		cachedReviews.add(0, review)
		clearDraftInternal(mediaId)
		return refreshInternal(mediaId, allowRetry = true)
	}

	suspend fun deleteReview(mediaId: Int, reviewId: Long): ReviewFeed {
		client.deleteReview(reviewId)
		removeReviewById(reviewId)
		cachedMyReview = null
		clearDraftInternal(mediaId)
		return refreshInternal(mediaId, allowRetry = true)
	}

	suspend fun rateReview(reviewId: Long, rating: ReviewRating): AniListReview {
		val mediaId = cachedMediaId
		val fallback = cachedReviews.firstOrNull { it.id == reviewId }
			?: cachedMyReview
		val updated = client.rateReview(reviewId, rating, fallback).let {
			if (mediaId != null) it.withMediaId(mediaId) else it
		}
		val normalized = fallback?.let { updated.normalizeVotes(it, rating) } ?: updated
		val index = cachedReviews.indexOfFirst { it.id == reviewId }
		if (index >= 0) {
			cachedReviews[index] = normalized
		}
		if (cachedMyReview?.id == reviewId) {
			cachedMyReview = normalized
		}
		return normalized
	}

	suspend fun renderMarkdown(markdown: String): String = client.renderMarkdown(markdown)

	suspend fun saveDraft(mediaId: Int, summary: String, body: String, score: Int) {
		val userId = ensureViewer()?.id ?: return
		withContext(Dispatchers.IO) {
			draftStorage.save(
				ReviewDraft(
					mediaId = mediaId,
					userId = userId,
					summary = summary,
					body = body,
					score = score,
					lastEdited = System.currentTimeMillis(),
				),
			)
		}
	}

	suspend fun loadDraft(mediaId: Int): ReviewDraft? {
		val userId = ensureViewer()?.id ?: return null
		return withContext(Dispatchers.IO) {
			draftStorage.load(mediaId, userId)
		}
	}

	suspend fun clearDraft(mediaId: Int) {
		val userId = ensureViewer()?.id ?: return
		clearDraftInternal(mediaId, userId)
	}

	private suspend fun ensureViewer(): AniListViewer? {
		if (cachedViewer != null) {
			return cachedViewer
		}
		val viewer = client.fetchViewer()
		cachedViewer = viewer
		return viewer
	}

	private suspend fun clearDraftInternal(mediaId: Int) {
		val userId = ensureViewer()?.id ?: return
		clearDraftInternal(mediaId, userId)
	}

	private fun clearDraftInternal(mediaId: Int, userId: Long) {
		draftStorage.clear(mediaId, userId)
	}

	private fun integrateMyReview() {
		val review = cachedMyReview ?: return
		removeReviewById(review.id)
		cachedReviews.add(0, review)
	}

	private fun removeReviewById(reviewId: Long) {
		val idx = cachedReviews.indexOfFirst { it.id == reviewId }
		if (idx >= 0) {
			cachedReviews.removeAt(idx)
		}
	}

	private fun resetCache(mediaId: Int) {
		cachedMediaId = mediaId
		cachedReviews.clear()
		cachedPage = 0
		cachedHasNext = false
		cachedMyReview = null
	}

	private fun buildFeed(): ReviewFeed {
		return ReviewFeed(
			mediaId = cachedMediaId,
			reviews = cachedReviews.toList(),
			hasNext = cachedHasNext,
			currentPage = cachedPage,
			viewer = cachedViewer,
			myReview = cachedMyReview,
		)
	}

	private suspend fun resolveMediaIdFallback(currentMediaId: Int): Int? {
		val listId = cachedMediaListId ?: return null
		val ref = client.fetchMediaIdByListId(listId) ?: return null
		if (ref.id == currentMediaId) {
			return null
		}
		updateStoredMediaId(ref.id.toLong())
		resetCache(ref.id)
		return ref.id
	}

	private suspend fun updateStoredMediaId(newMediaId: Long) {
		val entity = database.getScrobblingDao().find(ScrobblerService.ANILIST.id, currentMangaId) ?: return
		val updated = ScrobblingEntity(
			scrobbler = entity.scrobbler,
			id = entity.id,
			mangaId = entity.mangaId,
			targetId = newMediaId,
			status = entity.status,
			chapter = entity.chapter,
			comment = entity.comment,
			rating = entity.rating,
		)
		database.getScrobblingDao().upsert(updated)
	}

	companion object {
		private const val TAG = "KotatsuReviews"
	}

	private fun AniListReview.withMediaId(mediaId: Int): AniListReview =
		if (this.mediaId != 0) this else copy(mediaId = mediaId)

	private fun AniListReview.normalizeVotes(previous: AniListReview, target: ReviewRating): AniListReview {
		val prevVote = previous.userRating ?: ReviewRating.NO_VOTE
		val prevTotal = previous.ratingAmount ?: 0
		val newTotal = when (target) {
			ReviewRating.UP_VOTE -> if (prevVote != ReviewRating.UP_VOTE) prevTotal + 1 else prevTotal
			ReviewRating.NO_VOTE -> if (prevVote == ReviewRating.UP_VOTE) (prevTotal - 1).coerceAtLeast(0) else prevTotal
			ReviewRating.DOWN_VOTE -> prevTotal
		}
		return copy(
			ratingAmount = ratingAmount ?: newTotal,
			userRating = if (target == ReviewRating.NO_VOTE) null else target,
		)
	}
}

sealed interface ReviewAccess {
	data object NotAuthorized : ReviewAccess
	data object NotTracked : ReviewAccess
	data class Granted(val mediaId: Int, val mediaListId: Int) : ReviewAccess
}

data class ReviewFeed(
	val mediaId: Int?,
	val reviews: List<AniListReview>,
	val hasNext: Boolean,
	val currentPage: Int,
	val viewer: AniListViewer?,
	val myReview: AniListReview?,
)
