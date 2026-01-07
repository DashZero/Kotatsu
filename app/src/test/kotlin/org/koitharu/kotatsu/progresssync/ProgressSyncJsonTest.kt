package org.koitharu.kotatsu.progresssync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.progresssync.data.ProgressSyncFile
import org.koitharu.kotatsu.progresssync.data.ProgressSyncFlags
import org.koitharu.kotatsu.progresssync.data.ProgressSyncItem
import org.koitharu.kotatsu.progresssync.data.ProgressSyncProgress
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ProgressSyncJsonTest {

	private val json = Json {
		ignoreUnknownKeys = true
		encodeDefaults = false
		explicitNulls = false
	}

	@Test
	fun roundTripProgressSyncFile() {
		val file = ProgressSyncFile(
			deviceId = "device-123",
			deviceName = "Test Phone",
			exportedAt = "2025-12-28T12:00:00+07:00",
			items = listOf(
				ProgressSyncItem(
					mangaKey = "source|https://example.com/manga/1",
					source = "source",
					title = "Example",
					url = "https://example.com/manga/1",
					publicUrl = "https://example.com/manga/1",
					coverUrl = "https://example.com/cover.jpg",
					progress = ProgressSyncProgress(
						chapterId = 10L,
						chapterNumber = "10",
						page = 7,
						percent = 0.5f,
						updatedAt = "2025-12-28T12:00:00+07:00",
					),
					flags = ProgressSyncFlags(
						favorite = true,
						bookmarked = false,
					),
				),
			),
		)
		val output = ByteArrayOutputStream()
		json.encodeToStream(file, output)
		val input = ByteArrayInputStream(output.toByteArray())
		val decoded = json.decodeFromStream<ProgressSyncFile>(input)
		assertEquals(file.deviceId, decoded.deviceId)
		assertEquals(file.deviceName, decoded.deviceName)
		assertEquals(file.exportedAt, decoded.exportedAt)
		assertEquals(file.items.size, decoded.items.size)
		assertEquals(file.items.first().mangaKey, decoded.items.first().mangaKey)
		assertEquals(file.items.first().progress.updatedAt, decoded.items.first().progress.updatedAt)
	}

	@Test
	fun ignoresUnknownFields() {
		val jsonInput = """
			{
			  "schema_version": 1,
			  "device_id": "device-123",
			  "device_name": "Test Phone",
			  "exported_at": "2025-12-28T12:00:00+07:00",
			  "items": [
				{
				  "manga_key": "source|https://example.com/manga/1",
				  "source": "source",
				  "title": "Example",
				  "progress": {
					"page": 1,
					"updated_at": "2025-12-28T12:00:00+07:00"
				  },
				  "flags": {
					"favorite": false,
					"bookmarked": false
				  },
				  "unexpected_field": "ignored"
				}
			  ],
			  "extra_root": "ignored"
			}
		""".trimIndent()
		val decoded = json.decodeFromStream<ProgressSyncFile>(ByteArrayInputStream(jsonInput.toByteArray()))
		assertEquals("device-123", decoded.deviceId)
		assertEquals(1, decoded.items.size)
	}
}
