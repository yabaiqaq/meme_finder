package com.meme.finder.data.local

import androidx.room.TypeConverter
import com.meme.finder.domain.model.ImageType

class Converters {
    @TypeConverter
    fun fromImageType(type: ImageType): String = type.name

    @TypeConverter
    fun toImageType(value: String): ImageType =
        runCatching { ImageType.valueOf(value) }.getOrDefault(ImageType.UNKNOWN)

    @TypeConverter
    fun fromStringList(list: List<String>): String = list.joinToString(",")

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        value?.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: emptyList()
}
