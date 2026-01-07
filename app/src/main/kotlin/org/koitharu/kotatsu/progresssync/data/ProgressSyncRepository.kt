package org.koitharu.kotatsu.progresssync.data

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.decodeFromStream
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.progresssync.prefs.ProgressSyncConflictStrategy
import org.koitharu.kotatsu.progresssync.prefs.ProgressSyncSettings
import org.koitharu.kotatsu.favourites.data.FavouriteCategoryEntity
import org.koitharu.kotatsu.favourites.data.FavouriteEntity
import org.koitharu.kotatsu.history.data.HistoryEntity
import org.koitharu.kotatsu.history.data.HistoryWithManga
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import java.io.FileNotFoundException
import java.nio.file.AccessDeniedException
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class ProgressSyncRepository @Inject constructor(
	@ApplicationContext private val context: Context,
	private val database: MangaDatabase,
	private val settings: ProgressSyncSettings,
) {

	data class ExportResult(val items: Int)
	data class ImportResult(val items: Int, val inserted: Int, val updated: Int)

	private val json = Json {
		ignoreUnknownKeys = true
		encodeDefaults = false
		explicitNulls = false
		allowSpecialFloatingPointValues = true
	}
	private val timeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

	suspend fun exportNow(allowOverwriteCorrupt: Boolean = false): ExportResult {
		val root = getRootOrThrow(requireWrite = true)
		val file = getOrCreateTargetFile(root, allowOverwriteCorrupt)
		val history = loadHistory()
		val favorites = if (settings.includeFavorites) {
			loadFavorites()
		} else {
			emptySet()
		}
		val bookmarks = if (settings.includeBookmarks) {
			loadBookmarks()
		} else {
			emptyMap()
		}
		val now = OffsetDateTime.now().format(timeFormatter)
		val items = history.map { entry ->
			val manga = entry.manga
			val key = buildMangaKey(manga.source, manga.url)
			val bookmarkList = bookmarks[manga.id]
			ProgressSyncItem(
				mangaKey = key,
				source = manga.source,
				title = manga.title,
				url = manga.url,
				publicUrl = manga.publicUrl,
				coverUrl = manga.coverUrl,
				progress = ProgressSyncProgress(
					chapterId = entry.history.chapterId.takeIf { it != 0L },
					chapterNumber = null,
					page = entry.history.page,
					percent = entry.history.percent.takeIf { it >= 0f },
					updatedAt = formatTimestamp(entry.history.updatedAt),
				),
				flags = ProgressSyncFlags(
					favorite = manga.id in favorites,
					bookmarked = bookmarkList?.isNotEmpty() == true,
				),
				bookmarks = bookmarkList?.map { it.toSyncBookmark() },
			)
		}
		val payload = ProgressSyncFile(
			deviceId = settings.deviceId,
			deviceName = settings.deviceName,
			exportedAt = now,
			items = items,
		)
		writeFile(file, payload)
		return ExportResult(items.size)
	}

	suspend fun importNow(): ImportResult {
		val root = getRootOrThrow(requireWrite = false)
		val file = root.findFile(FILE_NAME) ?: throw FileNotFoundException(FILE_NAME)
		val remote = readFile(file)
		val conflictStrategy = settings.conflictStrategy
		var inserted = 0
		var updated = 0
		database.withTransaction {
			for (item in remote.items) {
				val progress = item.progress
				val remoteUpdatedAt = parseTimestamp(progress.updatedAt)
				val mangaEntity = findOrCreateManga(item)
				val localHistory = database.getHistoryDao().find(mangaEntity.id)
				val shouldApplyProgress = when {
					localHistory == null -> true
					conflictStrategy.isRemotePreferred(remoteUpdatedAt, localHistory.updatedAt) -> true
					else -> false
				}
				if (shouldApplyProgress) {
					val entity = HistoryEntity(
						mangaId = mangaEntity.id,
						createdAt = localHistory?.createdAt ?: remoteUpdatedAt,
						updatedAt = remoteUpdatedAt,
						chapterId = progress.chapterId ?: 0L,
						page = progress.page,
						scroll = localHistory?.scroll ?: 0f,
						percent = progress.percent ?: localHistory?.percent ?: 0f,
						deletedAt = 0L,
						chaptersCount = localHistory?.chaptersCount ?: 0,
					)
					if (database.getHistoryDao().upsert(entity)) {
						inserted++
					} else {
						updated++
					}
				}
				applyFavoritesIfNeeded(mangaEntity.id, item, localHistory, remoteUpdatedAt)
				applyBookmarksIfNeeded(mangaEntity.id, item)
			}
		}
		return ImportResult(remote.items.size, inserted, updated)
	}

	@WorkerThread
	private suspend fun loadHistory(): List<HistoryWithManga> = withContext(Dispatchers.IO) {
		database.getHistoryDao().dump().toList()
	}

	@WorkerThread
	private suspend fun loadFavorites(): Set<Long> = withContext(Dispatchers.IO) {
		database.getFavouritesDao().findAll().mapTo(HashSet()) { it.manga.id }
	}

	@WorkerThread
	private suspend fun loadBookmarks(): Map<Long, List<org.koitharu.kotatsu.bookmarks.data.BookmarkEntity>> =
		withContext(Dispatchers.IO) {
			val map = LinkedHashMap<Long, List<org.koitharu.kotatsu.bookmarks.data.BookmarkEntity>>()
			database.getBookmarksDao().dump().toList().forEach { (manga, list) ->
				map[manga.manga.id] = list
			}
			map
		}

	private suspend fun findOrCreateManga(item: ProgressSyncItem): MangaEntity {
		val dao = database.getMangaDao()
		val source = item.source
		val url = item.url ?: item.publicUrl ?: item.mangaKey.substringAfter('|', item.mangaKey)
		val existing = dao.findBySourceAndUrl(source, url)
		if (existing != null) {
			return existing.manga
		}
		val id = stableMangaId(source, url)
		val entity = MangaEntity(
			id = id,
			title = item.title,
			altTitles = null,
			url = url,
			publicUrl = item.publicUrl ?: url,
			rating = RATING_UNKNOWN,
			isNsfw = false,
			contentRating = null,
			coverUrl = item.coverUrl ?: "",
			largeCoverUrl = null,
			state = null,
			authors = null,
			source = source,
		)
		dao.upsert(entity, null)
		return entity
	}

	private suspend fun applyFavoritesIfNeeded(
		mangaId: Long,
		item: ProgressSyncItem,
		localHistory: HistoryEntity?,
		remoteUpdatedAt: Long,
	) {
		if (!settings.includeFavorites) {
			return
		}
		val hasLocalFavorite = database.getFavouritesDao().findCategoriesCount(mangaId) != 0
		val shouldFavorite = when (settings.conflictStrategy) {
			ProgressSyncConflictStrategy.MERGE_PER_TITLE ->
				item.flags.favorite || hasLocalFavorite
			ProgressSyncConflictStrategy.LAST_WRITE_WINS -> {
				val localUpdatedAt = localHistory?.updatedAt ?: 0L
				if (remoteUpdatedAt > localUpdatedAt) item.flags.favorite else hasLocalFavorite
			}
		}
		if (shouldFavorite) {
			addToDefaultFavorites(mangaId)
		} else if (settings.conflictStrategy == ProgressSyncConflictStrategy.LAST_WRITE_WINS) {
			database.getFavouritesDao().delete(mangaId)
		}
	}

	private suspend fun applyBookmarksIfNeeded(
		mangaId: Long,
		item: ProgressSyncItem,
	) {
		if (!settings.includeBookmarks) {
			return
		}
		val bookmarks = item.bookmarks ?: return
		if (bookmarks.isEmpty()) {
			return
		}
		val entities = bookmarks.map {
			org.koitharu.kotatsu.bookmarks.data.BookmarkEntity(
				mangaId = mangaId,
				pageId = it.pageId,
				chapterId = it.chapterId,
				page = it.page,
				scroll = it.scroll,
				imageUrl = it.imageUrl,
				createdAt = parseTimestamp(it.createdAt),
				percent = it.percent,
			)
		}
		database.getBookmarksDao().upsert(entities)
	}

	private suspend fun addToDefaultFavorites(mangaId: Long) {
		val categoriesDao = database.getFavouriteCategoriesDao()
		val favouritesDao = database.getFavouritesDao()
		val existingCategories = categoriesDao.findAll()
		val categoryId = if (existingCategories.isEmpty()) {
			categoriesDao.insert(
				FavouriteCategoryEntity(
					categoryId = 0,
					createdAt = System.currentTimeMillis(),
					sortKey = categoriesDao.getNextSortKey(),
					title = context.getString(org.koitharu.kotatsu.R.string.favourites),
					order = org.koitharu.kotatsu.list.domain.ListSortOrder.NEWEST.name,
					track = false,
					isVisibleInLibrary = true,
					deletedAt = 0L,
				),
			)
		} else {
			existingCategories.first().categoryId.toLong()
		}
		favouritesDao.upsert(
			FavouriteEntity(
				mangaId = mangaId,
				categoryId = categoryId,
				createdAt = System.currentTimeMillis(),
				sortKey = 0,
				deletedAt = 0L,
				isPinned = false,
			),
		)
	}

	private suspend fun getRootOrThrow(requireWrite: Boolean): DocumentFile {
		val uri = settings.directory ?: throw IllegalStateException("Progress sync directory is not set")
		val root = DocumentFile.fromTreeUri(context, uri)
			?: throw IllegalStateException("Cannot obtain DocumentFile from $uri")
		if (requireWrite && root.canWrite().not()) {
			throw AccessDeniedException(uri.toString())
		}
		return root
	}

	private suspend fun getOrCreateTargetFile(root: DocumentFile, allowOverwriteCorrupt: Boolean): DocumentFile {
		val existing = root.findFile(FILE_NAME)
		if (existing != null) {
			if (!allowOverwriteCorrupt) {
				try {
					readFile(existing)
				} catch (e: SerializationException) {
					throw e
				}
			}
			return existing
		}
		return root.createFile("application/json", FILE_NAME_BASE)
			?: throw IllegalStateException("Cannot create $FILE_NAME")
	}

	private suspend fun readFile(file: DocumentFile): ProgressSyncFile = withTimeout(IO_TIMEOUT_MS) {
		runInterruptible(Dispatchers.IO) {
			val stream = context.contentResolver.openInputStream(file.uri)
				?: throw FileNotFoundException(file.uri.toString())
			stream.use { input ->
				json.decodeFromStream(input)
			}
		}
	}

	private suspend fun writeFile(file: DocumentFile, payload: ProgressSyncFile) = withTimeout(IO_TIMEOUT_MS) {
		runInterruptible(Dispatchers.IO) {
			val stream = context.contentResolver.openOutputStream(file.uri, "wt")
				?: throw FileNotFoundException(file.uri.toString())
			stream.use { output ->
				json.encodeToStream(payload, output)
			}
		}
	}

	private fun formatTimestamp(epochMillis: Long): String {
		return Instant.ofEpochMilli(epochMillis)
			.atZone(ZoneId.systemDefault())
			.toOffsetDateTime()
			.format(timeFormatter)
	}

	private fun parseTimestamp(value: String?): Long = runCatching {
		OffsetDateTime.parse(value, timeFormatter).toInstant().toEpochMilli()
	}.getOrDefault(0L)

	private fun buildMangaKey(source: String, url: String): String = "$source|$url"

	private fun stableMangaId(source: String, url: String): Long {
		val digest = MessageDigest.getInstance("SHA-256").digest("$source|$url".toByteArray())
		var result = 0L
		for (i in 0 until Long.SIZE_BYTES) {
			result = (result shl 8) or (digest[i].toLong() and 0xff)
		}
		return result
	}

	private fun org.koitharu.kotatsu.bookmarks.data.BookmarkEntity.toSyncBookmark() = ProgressSyncBookmark(
		pageId = pageId,
		chapterId = chapterId,
		page = page,
		scroll = scroll,
		imageUrl = imageUrl,
		createdAt = formatTimestamp(createdAt),
		percent = percent,
	)

	private fun ProgressSyncConflictStrategy.isRemotePreferred(
		remoteUpdatedAt: Long,
		localUpdatedAt: Long,
	): Boolean = when (this) {
		ProgressSyncConflictStrategy.LAST_WRITE_WINS -> remoteUpdatedAt > localUpdatedAt
		ProgressSyncConflictStrategy.MERGE_PER_TITLE -> remoteUpdatedAt > localUpdatedAt
	}

	companion object {
		const val FILE_NAME = "kotatsu_progress_sync.json"
		private const val FILE_NAME_BASE = "kotatsu_progress_sync"
		private const val IO_TIMEOUT_MS = 25_000L
	}
}
