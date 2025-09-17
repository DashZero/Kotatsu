package org.koitharu.kotatsu.comments

import android.content.Context
import android.util.LruCache
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object Moderation {

    private val loadedProfanityWords = mutableSetOf<String>()
    private val loadedProfanityPatterns = mutableSetOf<Pattern>()

    // Cache for rate limiting: userId -> list of timestamps
    private val commentTimestamps = ConcurrentHashMap<String, MutableList<Long>>()

    // Cache for duplicate blocking: userId -> last 60 seconds comments (normalized text)
    private val recentComments = ConcurrentHashMap<String, MutableList<Pair<String, Long>>>()

    // Cache for banned users: userId -> bannedUntil timestamp
    private val bannedUsers = ConcurrentHashMap<String, Long>()

    fun init(context: Context) {
        if (loadedProfanityWords.isNotEmpty() || loadedProfanityPatterns.isNotEmpty()) {
            return // Already initialized
        }
        loadProfanityFromAssets(context, "profane/en.txt") { line ->
            loadedProfanityWords.add(line.lowercase())
        }
        loadProfanityFromAssets(context, "profane/emoji.txt") { line ->
            loadedProfanityWords.add(line)
        }
        loadProfanityFromAssets(context, "profane/en_profanity.json") { jsonString ->
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    loadedProfanityWords.add(jsonArray.getString(i).lowercase())
                }
            } catch (e: JSONException) {
                println("Error parsing en_profanity.json: $e")
            }
        }
        loadProfanityFromAssets(context, "profane/emoji_profanity.json") { jsonString ->
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val matchPattern = jsonObject.getString("match")
                    loadedProfanityPatterns.add(Pattern.compile(matchPattern))
                }
            } catch (e: JSONException) {
                println("Error parsing emoji_profanity.json: $e")
            }
        }
    }

    private fun loadProfanityFromAssets(context: Context, fileName: String, processor: (String) -> Unit) {
        try {
            context.assets.open(fileName).use {
                val reader = BufferedReader(InputStreamReader(it))
                if (fileName.endsWith(".json")) {
                    processor(reader.readText())
                } else {
                    reader.lineSequence().forEach(processor)
                }
            }
        } catch (e: Exception) {
            println("Error loading profanity from $fileName: $e")
        }
    }

    /**
     * Filters profanity from the given text.
     * Replaces bad words with '***'.
     */
    fun filterProfanity(text: String): String {
        var filteredText = text

        // Filter literal words
        loadedProfanityWords.forEach { word ->
            val pattern = Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE)
            filteredText = pattern.matcher(filteredText).replaceAll("***")
        }

        // Filter regex patterns (for emojis and more complex cases)
        loadedProfanityPatterns.forEach { pattern ->
            filteredText = pattern.matcher(filteredText).replaceAll("***")
        }
        return filteredText
    }

    /**
     * Checks if the entire text would become only '***' tokens after filtering.
     */
    fun isFullyCensored(originalText: String, filteredText: String): Boolean {
        val originalWords = originalText.split(Regex("\\s+")).filter { it.isNotBlank() }
        val filteredWords = filteredText.split(Regex("\\s+")).filter { it.isNotBlank() }

        if (originalWords.isEmpty()) return false
        // If the filtered text is just asterisks and spaces, and it's roughly the same length
        return filteredText.replace(" ", "").all { it == '*' } && filteredText.length >= originalText.length / 2
    }

    /**
     * Checks if a user is rate-limited.
     * Max 3 comments per 30 seconds per user.
     */
    fun isRateLimited(userId: String): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = commentTimestamps.getOrPut(userId) { mutableListOf() }

        // Remove old timestamps
        timestamps.removeAll { it < now - 30 * 1000 }

        if (timestamps.size >= 3) {
            return true
        }
        timestamps.add(now)
        return false
    }

    /**
     * Checks for duplicate comments.
     * If same normalized text posted in last 60 seconds, reject.
     */
    fun isDuplicate(userId: String, normalizedText: String): Boolean {
        val now = System.currentTimeMillis()
        val userRecentComments = recentComments.getOrPut(userId) { mutableListOf() }

        // Remove old comments
        userRecentComments.removeAll { it.second < now - 60 * 1000 }

        if (userRecentComments.any { it.first == normalizedText }) {
            return true
        }
        userRecentComments.add(normalizedText to now)
        return false
    }

    /**
     * Normalizes text for duplicate checking (e.g., remove punctuation, lower case).
     */
    fun normalizeText(text: String): String {
        return text.lowercase().replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
    }

    /**
     * Checks if a user is currently banned.
     */
    fun isBanned(userId: String): Boolean {
        val bannedUntil = bannedUsers[userId] ?: 0L
        return System.currentTimeMillis() < bannedUntil
    }

    /**
     * Bans a user for 90 days.
     */
    fun banUser(userId: String) {
        bannedUsers[userId] = System.currentTimeMillis() + 90L * 24 * 60 * 60 * 1000 // 90 days
    }

    /**
     * Unbans a user (e.g., if ban period expires).
     */
    fun unbanUser(userId: String) {
        bannedUsers.remove(userId)
    }

    /**
     * Processes text for link limits, ALL-CAPS spam, and emoji/repeat spam.
     */
    fun processText(text: String): String {
        var processedText = text

        // Link limit: allow up to 2 URLs per comment; strip excess.
        val urlPattern = Pattern.compile("(https?://\\S+)")
        val matcher = urlPattern.matcher(processedText)
        val urls = mutableListOf<String>()
        while (matcher.find()) {
            urls.add(matcher.group(1))
        }
        if (urls.size > 2) {
            var count = 0
            processedText = urlPattern.matcher(processedText).replaceAll { matchResult ->
                if (count < 2) {
                    count++
                    matchResult.group(1)
                } else {
                    "[LINK REMOVED]"
                }
            }
        }

        // ALL-CAPS spam: if >70% uppercase, downcase automatically.
        val uppercaseCount = processedText.count { it.isUpperCase() }
        val letterCount = processedText.count { it.isLetter() }
        if (letterCount > 0 && uppercaseCount.toDouble() / letterCount > 0.7) {
            processedText = processedText.lowercase()
        }

        // Repeats/emoji spam: collapse runs >20.
        // This is a simplified approach. A more robust solution might involve regex for specific emoji blocks.
        processedText = processedText.replace(Regex("(.)\\1{20,}"), "$1$1$1$1$1$1$1$1$1$1$1$1$1$1$1$1$1$1$1$1") // Collapse to 20 repeats

        return processedText
    }
}