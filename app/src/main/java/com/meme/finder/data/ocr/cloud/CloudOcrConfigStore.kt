package com.meme.finder.data.ocr.cloud

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 云端 OCR 提供方。 */
enum class CloudOcrProvider(val displayName: String) {
    NONE("不启用"),
    BAIDU("百度智能云 OCR"),
    TENCENT("腾讯云 OCR"),  // 预留，v0.7+ 接入
    ALIYUN("阿里云 OCR");  // 预留
}

data class CloudOcrConfig(
    val provider: CloudOcrProvider = CloudOcrProvider.NONE,
    val apiKey: String = "",
    val secretKey: String = "",
)

/**
 * 云端 OCR 配置存储（SharedPreferences）。
 * 由 Settings 页面写入、OcrProcessor 读取。
 */
@Singleton
class CloudOcrConfigStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("cloud_ocr", Context.MODE_PRIVATE)

    fun load(): CloudOcrConfig = CloudOcrConfig(
        provider = CloudOcrProvider.entries
            .firstOrNull { it.name == prefs.getString(KEY_PROVIDER, CloudOcrProvider.NONE.name) }
            ?: CloudOcrProvider.NONE,
        apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
        secretKey = prefs.getString(KEY_SECRET_KEY, "").orEmpty(),
    )

    fun save(config: CloudOcrConfig) {
        prefs.edit {
            putString(KEY_PROVIDER, config.provider.name)
            putString(KEY_API_KEY, config.apiKey)
            putString(KEY_SECRET_KEY, config.secretKey)
        }
    }

    fun isConfigured(): Boolean = load().let { c ->
        c.provider != CloudOcrProvider.NONE &&
            c.apiKey.isNotBlank() &&
            c.secretKey.isNotBlank()
    }

    companion object {
        private const val KEY_PROVIDER = "provider"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SECRET_KEY = "secret_key"
    }
}
