package org.koitharu.kotatsu.progresssync

import android.app.Application
import androidx.room.InvalidationTracker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import org.koitharu.kotatsu.progresssync.domain.ProgressSyncHistoryObserver

@Module
@InstallIn(SingletonComponent::class)
object ProgressSyncModule {

	@Provides
	@ElementsIntoSet
	fun provideDatabaseObservers(
		historyObserver: ProgressSyncHistoryObserver,
	): Set<@JvmSuppressWildcards InvalidationTracker.Observer> = setOf(historyObserver)

	@Provides
	@ElementsIntoSet
	fun provideActivityLifecycleCallbacks(
		initializer: ProgressSyncInitializer,
	): Set<@JvmSuppressWildcards Application.ActivityLifecycleCallbacks> = setOf(initializer)
}
