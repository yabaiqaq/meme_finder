package com.meme.finder.data.ocr.cloud

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.meme.finder.data.ocr.OcrEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 百度智能云 OCR 实现。
 *
 * 接入流程：
 * 1. 在 https://console.bce.baidu.com/ai 开通"文字识别"，创建应用拿到 API Key/Secret Key
 * 2. 在 Settings 页面填入并选择 provider = BAIDU
 * 3. OCR 在 ML Kit 失败或置信度低时回退到这里
 *
 * 用 general_basic（通用文字识别-高精度版可选）+ 默认中文模型。
 */
@Singleton
class BaiduCloudOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configStore: CloudOcrConfigStore,
) : OcrEngine {

    override val name: String = "baidu"
    override val requiresNetwork: Boolean = true

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private var cachedToken: String? = null
    private var tokenExpireAt: Long = 0L

    override suspend fun recognize(uri: Uri): String? = withContext(Dispatchers.IO) {
        val config = configStore.load()
        if (config.provider != CloudOcrProvider.BAIDU) return@withContext null
        if (config.apiKey.isBlank() || config.secretKey.isBlank()) return@withContext null

        runCatching {
            val token = ensureAccessToken(config) ?: return@withContext null
            val imageBase64 = readAsBase64(uri) ?: return@withContext null
            val body = FormBody.Builder()
                .add("image", imageBase64)
                .add("language_type", "CHN_ENG")
                .build()
            val request = Request.Builder()
                .url("https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic?access_token=$token")
                .post(body)
                .build()
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val json = JSONObject(resp.body?.string().orEmpty())
                if (json.has("error_msg")) return@withContext null
                val words = json.optJSONArray("words_result") ?: return@withContext null
                buildString {
                    for (i in 0 until words.length()) {
                        append(words.getJSONObject(i).optString("words"))
                        if (i != words.length() - 1) append("\n")
                    }
                }.ifEmpty { null }
            }
        }.getOrNull()
    }

    private fun ensureAccessToken(config: CloudOcrConfig): String? {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpireAt) return cachedToken
        val url = "https://aip.baidubce.com/oauth/2.0/token" +
            "?grant_type=client_credentials&client_id=${config.apiKey}&client_secret=${config.secretKey}"
        return runCatching {
            val body = "".toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val req = Request.Builder().url(url).post(body).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val json = JSONObject(resp.body?.string().orEmpty())
                val token = json.optString("access_token")
                if (token.isBlank()) return null
                // 百度 token 有效期 30 天，提前 1 天刷新
                val expires = json.optLong("expires_in", 2592000L)
                cachedToken = token
                tokenExpireAt = now + (expires - 86400) * 1000
                token
            }
        }.getOrNull()
    }

    private fun readAsBase64(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri).use { input ->
            val bytes = input?.readBytes() ?: return null
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }.getOrNull()
}
