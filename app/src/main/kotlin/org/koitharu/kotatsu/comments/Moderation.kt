
package org.koitharu.kotatsu.comments

object Moderation {

    private val profanity = setOf("badword1", "badword2") // In a real app, this would be a more extensive list.

    fun filterProfanity(text: String): String {
        var filteredText = text
        profanity.forEach { word ->
            filteredText = filteredText.replace(word, "***", ignoreCase = true)
        }
        return filteredText
    }

    fun isSpam(text: String, lastMessage: String?): Boolean {
        if (text == lastMessage) {
            return true
        }
        if (text.length > 500) {
            return true
        }
        if (text.count { it.isUpperCase() }.toDouble() / text.length > 0.7) {
            return true
        }
        if (hasExcessiveRepeatedChars(text)) {
            return true
        }
        if (countLinks(text) > 2) {
            return true
        }
        return false
    }

    fun sanitize(text: String): String {
        var sanitizedText = text
        if (text.count { it.isUpperCase() }.toDouble() / text.length > 0.7) {
            sanitizedText = sanitizedText.toLowerCase()
        }
        sanitizedText = stripUnsafeMarkdown(sanitizedText)
        sanitizedText = filterProfanity(sanitizedText)
        if (sanitizedText.replace("*", "").isBlank()) {
            // Fully censored, block sending
            return ""
        }
        return sanitizedText
    }

    private fun hasExcessiveRepeatedChars(text: String): Boolean {
        val pattern = "(\w)\1{20,}".toRegex()
        return pattern.containsMatchIn(text)
    }

    private fun countLinks(text: String): Int {
        val pattern = "(https?://\S+)".toRegex()
        return pattern.findAll(text).count()
    }

    private fun stripUnsafeMarkdown(text: String): String {
        // In a real app, use a proper Markdown parser to allow only specific tags.
        // For now, we'll just strip anything that looks like a script tag.
        return text.replace("<script.*?>.*?</script>", "", ignoreCase = true)
    }
}
