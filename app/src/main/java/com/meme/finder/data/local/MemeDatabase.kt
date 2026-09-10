package com.meme.finder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [ImageEntity::class, ImageFtsEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MemeDatabase : RoomDatabase() {
    abstract fun imageDao(): ImageDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): MemeDatabase =
        Room.databaseBuilder(ctx, MemeDatabase::class.java, "meme_finder.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideImageDao(db: MemeDatabase): ImageDao = db.imageDao()
}
