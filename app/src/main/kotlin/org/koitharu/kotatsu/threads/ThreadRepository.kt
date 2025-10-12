package org.koitharu.kotatsu.threads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.reviews.AniListMediaRef
import org.koitharu.kotatsu.reviews.AniListViewer
import org.koitharu.kotatsu.scrobbling.anilist.domain.AniListScrobbler
import org.koitharu.kotatsu.scrobbling.common.data.ScrobblingEntity
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerService
import org.koitharu.kotatsu.parsers.exception.GraphQLException
import javax.inject.Inject
import javax.inject.Singleton

private const val FIRST_PAGE = 1
private const val DEFAULT_PAGE_SIZE = 10
private const val PREVIEW_PAGE_SIZE = 3
private const val DEFAULT_COMMENT_PAGE_SIZE = 20

@Singleton
class ThreadRepository @Inject constructor(
	private val client: AniListThreadClient,
	private val database: MangaDatabase,
	private val aniListScrobbler: AniListScrobbler,
) {

	private var cachedMediaId: Int? = null
	private var cachedSort: ThreadSort = ThreadSort.RECENT_ACTIVITY
	private var cachedThreads: MutableList<AniListThread> = mutableListOf()
	private var cachedPage: Int = 0
	private var cachedHasNext: Boolean = false
	private var cachedViewer: AniListViewer? = null
	private var cachedMediaListId: Int? = null
	private var currentMangaId: Long = 0L

	suspend fun resolveAccess(mangaId: Long): ThreadAccess = withContext(Dispatchers.IO) {
		currentMangaId = mangaId
		if (!aniListScrobbler.isEnabled) {
			return@withContext ThreadAccess.NotAuthorized
		}
		val entity = database.getScrobblingDao().find(ScrobblerService.ANILIST.id, mangaId)
			?: return@withContext ThreadAccess.NotTracked
		cachedMediaListId = entity.id
		ThreadAccess.Granted(
			mediaId = entity.targetId.toInt(),
			mediaListId = entity.id,
		)
	}

	suspend fun getThreadPreviews(access: ThreadAccess.Granted): List<AniListThread> {
		return client.fetchThreads(
			mediaId = access.mediaId,
			page = FIRST_PAGE,
			perPage = PREVIEW_PAGE_SIZE,
			sort = listOf(ThreadSort.RECENT_ACTIVITY.graphQlValue)
		).threads
	}

	suspend fun fetchPreview(access: ThreadAccess.Granted): ThreadPreview {
		cachedMediaListId = access.mediaListId
		val mediaId = access.mediaId
		val viewer = ensureViewer()
		val page = client.fetchThreads(mediaId, FIRST_PAGE, PREVIEW_PAGE_SIZE, listOf(ThreadSort.RECENT_ACTIVITY.graphQlValue))
		return ThreadPreview(
			mediaId = mediaId,
			threads = page.threads,
			viewer = viewer,
		)
	}

	suspend fun refresh(access: ThreadAccess.Granted, sort: ThreadSort): ThreadFeed {
		cachedMediaListId = access.mediaListId
		return refreshInternal(access.mediaId, sort, allowRetry = true)
	}

	suspend fun loadMore(sort: ThreadSort): ThreadFeed {
		val mediaId = cachedMediaId ?: throw IllegalStateException("ThreadRepository not initialized")
		if (cachedSort != sort) {
			return refreshInternal(mediaId, sort, allowRetry = true)
		}
		if (!cachedHasNext) {
			return buildFeed()
		}
		val nextPage = cachedPage + 1
		val page = client.fetchThreads(mediaId, nextPage, DEFAULT_PAGE_SIZE, listOf(sort.graphQlValue))
		cachedPage = page.currentPage
		cachedHasNext = page.hasNextPage
		cachedThreads.addAll(page.threads)
		return buildFeed()
	}

	suspend fun fetchThread(threadId: Long, page: Int = FIRST_PAGE): ThreadWithComments? =
		client.fetchThread(threadId, page, DEFAULT_COMMENT_PAGE_SIZE)

	suspend fun fetchComments(threadId: Long, page: Int = FIRST_PAGE): ThreadCommentPage =
		client.fetchThreadComments(threadId, page, DEFAULT_COMMENT_PAGE_SIZE)

	suspend fun saveThread(mediaId: Int, title: String, body: String): ThreadFeed {
		client.saveThread(mediaId, title, body)
		return refreshInternal(mediaId, cachedSort, allowRetry = true)
	}

	suspend fun saveComment(threadId: Long, parentCommentId: Long?, comment: String): AniListThreadComment =
		client.saveThreadComment(threadId, parentCommentId, comment)

	suspend fun deleteComment(commentId: Long): Boolean = client.deleteThreadComment(commentId)

	suspend fun renderMarkdown(markdown: String): String = client.renderMarkdown(markdown)

	suspend fun getViewer(): AniListViewer? = ensureViewer()

	private suspend fun refreshInternal(mediaId: Int, sort: ThreadSort, allowRetry: Boolean): ThreadFeed {
		return try {
			if (cachedMediaId != mediaId || cachedSort != sort) {
				resetCache(mediaId, sort)
			}
			val viewer = ensureViewer()
			val page = client.fetchThreads(mediaId, FIRST_PAGE, DEFAULT_PAGE_SIZE, listOf(sort.graphQlValue))
			if (page.threads.isEmpty() && allowRetry) {
				resolveMediaIdFallback(mediaId)?.let { fallback ->
					return refreshInternal(fallback, sort, allowRetry = false)
				}
			}
			cachedPage = page.currentPage
			cachedHasNext = page.hasNextPage
			cachedThreads = page.threads.toMutableList()
			cachedViewer = viewer
			buildFeed()
		} catch (error: GraphQLException) {
			if (!allowRetry) {
				throw error
			}
			val fallback = resolveMediaIdFallback(mediaId) ?: throw error
			refreshInternal(fallback, sort, allowRetry = false)
		}
	}

	private suspend fun ensureViewer(): AniListViewer? {
		if (cachedViewer != null) {
			return cachedViewer
		}
		val viewer = client.fetchViewer()
		cachedViewer = viewer
		return viewer
	}

	private fun buildFeed(): ThreadFeed {
		return ThreadFeed(
			mediaId = cachedMediaId,
			threads = cachedThreads.toList(),
			hasNext = cachedHasNext,
			currentPage = cachedPage,
			sort = cachedSort,
			viewer = cachedViewer,
		)
	}

	private fun resetCache(mediaId: Int, sort: ThreadSort) {
		cachedMediaId = mediaId
		cachedSort = sort
		cachedThreads.clear()
		cachedPage = 0
		cachedHasNext = false
	}

	private suspend fun resolveMediaIdFallback(currentMediaId: Int): Int? {
		val listId = cachedMediaListId ?: return null
		val ref: AniListMediaRef = client.fetchMediaIdByListId(listId) ?: return null
		if (ref.id == currentMediaId) {
			return null
		}
		updateStoredMediaId(ref.id.toLong())
		resetCache(ref.id, cachedSort)
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
}

sealed interface ThreadAccess {
	object NotAuthorized : ThreadAccess
	object NotTracked : ThreadAccess
	data class Granted(val mediaId: Int, val mediaListId: Int) : ThreadAccess
}

data class ThreadFeed(
	val mediaId: Int?,
	val threads: List<AniListThread>,
	val hasNext: Boolean,
	val currentPage: Int,
	val sort: ThreadSort,
	val viewer: AniListViewer?,
)

data class ThreadPreview(
	val mediaId: Int,
	val threads: List<AniListThread>,
	val viewer: AniListViewer?,
)
