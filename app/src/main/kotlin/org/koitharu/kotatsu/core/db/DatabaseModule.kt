
package org.koitharu.kotatsu.core.db

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.koitharu.kotatsu.comments.CommentDao

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    fun provideCommentDao(database: MangaDatabase): CommentDao {
        return database.getCommentDao()
    }
}
