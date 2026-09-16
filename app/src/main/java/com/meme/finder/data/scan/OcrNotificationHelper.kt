package com.meme.finder.data.scan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.meme.finder.R
import com.meme.finder.ui.MainActivity

/**
 * OCR 前台通知构建器。
 *
 * WorkManager 的 `CoroutineWorker.setForegroundAsync(ForegroundInfo)` 会把 Worker 提升为
 * 前台服务，系统不会在应用切后台时杀死它。ForegroundInfo 需要一个常驻 Notification。
 *
 * 本 helper 负责：
 * 1. 创建通知渠道（Android 8.0+ 必需）
 * 2. 构建带进度条的 OCR 通知
 */
object OcrNotificationHelper {

    const val CHANNEL_ID = "ocr_progress_channel"
    const val NOTIFICATION_ID = 1001

    /** 创建通知渠道（重复调用安全）。 */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "OCR 识别进度",
                    NotificationManager.IMPORTANCE_LOW,  // LOW：不发出声音，只在通知栏显示
                ).apply {
                    description = "显示图片文字识别（OCR）的处理进度"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    /**
     * 构建带进度条的 OCR 通知。
     *
     * @param context 上下文
     * @param done 已处理数量
     * @param total 待处理总数
     */
    fun buildNotification(
        context: Context,
        done: Int,
        total: Int,
    ): android.app.Notification {
        ensureChannel(context)

        val percent = if (total > 0) (done * 100 / total) else 0
        val contentText = if (total > 0) "已识别 $done / $total 张" else "准备识别..."

        // 点击通知打开应用
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("正在识别图片文字")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setProgress(total, done, false)  // 不确定进度用 setProgress(0, 0, true)
            .setContentIntent(pendingIntent)
            .setOngoing(true)  // 常驻，用户不能手动清除
            .setOnlyAlertOnce(true)  // 只在首次显示时提醒
            .build()
    }
}
