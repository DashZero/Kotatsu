package org.koitharu.kotatsu.progresssync.prefs

import java.util.concurrent.TimeUnit

enum class ProgressSyncInterval(val minutes: Long) {
	MINUTES_15(15),
	HOURS_1(60),
	HOURS_6(360),
	DAILY(1_440),
	;

	val intervalMillis: Long
		get() = TimeUnit.MINUTES.toMillis(minutes)
}
