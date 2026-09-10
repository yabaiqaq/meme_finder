package com.meme.finder.di

import android.content.ContentResolver
import android.content.Context
import com.meme.finder.data.media.ImageTypeClassifier
import com.meme.finder.data.media.MediaStoreSource
import com.meme.finder.data.repo.ImageRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext ctx: Context): ContentResolver =
        ctx.contentResolver

    @Provides
    @Singleton
    fun provideMediaStoreSource(
        resolver: ContentResolver,
        classifier: ImageTypeClassifier,
    ): MediaStoreSource = MediaStoreSource(resolver, classifier)

    @Provides
    @Singleton
    fun provideImageRepository(source: MediaStoreSource): ImageRepository =
        ImageRepository(source)
}
