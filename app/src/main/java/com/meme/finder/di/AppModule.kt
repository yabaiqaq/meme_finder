package com.meme.finder.di

import android.content.ContentResolver
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 应用级 Hilt 模块。
 * - [ContentResolver] 不在 @Inject 构造器中，需要手动提供。
 * - 其它单例（MediaStoreSource、ImageTypeClassifier、ImageRepository、ScanStarter、
 *   MlKitOcrEngine、CloudOcrEngine、OcrProcessor 等）都有 @Inject constructor，由 Hilt 自动生成。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext ctx: Context): ContentResolver =
        ctx.contentResolver
}
