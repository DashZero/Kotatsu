package org.koitharu.kotatsu.core.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ErrorLogger @Inject constructor(
	@ApplicationContext context: Context,
) {

	private val logFile = File(context.filesDir, "logs/error.log")
	private val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
	private val lock = Any()

	fun log(tag: String, message: String, throwable: Throwable? = null) {
		Log.e(tag, message, throwable)
		appendLine(buildString {
			append(timeFormat.format(Date()))
			append(" ")
			append(tag)
			append(": ")
			append(message)
			if (throwable != null) {
				append('\n')
				append(Log.getStackTraceString(throwable))
			}
		})
	}

	fun logUncaught(thread: Thread, throwable: Throwable) {
		log(
			tag = "UncaughtException",
			message = "Thread=${thread.name}",
			throwable = throwable,
		)
	}

	private fun appendLine(text: String) {
		synchronized(lock) {
			logFile.parentFile?.mkdirs()
			FileWriter(logFile, true).use { writer ->
				writer.append(text)
				writer.append('\n')
			}
		}
	}
}
