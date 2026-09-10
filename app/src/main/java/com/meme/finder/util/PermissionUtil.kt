package com.meme.finder.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionUtil {

    /** 当前应用需要的图像读取权限。Android 13+ 用 READ_MEDIA_IMAGES，以下用 READ_EXTERNAL_STORAGE。 */
    val imageReadPermission: String
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

    fun hasImageReadPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, imageReadPermission) ==
            PackageManager.PERMISSION_GRANTED
}
