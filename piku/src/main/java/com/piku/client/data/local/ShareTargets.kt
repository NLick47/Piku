package com.piku.client.data.local

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build


object ShareTargets {
    const val WECHAT = "com.tencent.mm"
    const val QQ = "com.tencent.mobileqq"

    fun isInstalled(context: Context, packageName: String): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)


    fun appIcon(context: Context, packageName: String): android.graphics.drawable.Drawable? =
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()

    /**
     * 定向 Intent 是否真的有人接：微信/QQ 不一定接通用 ACTION_SEND，
     * 没人接时直接 startActivity 只会抛 ActivityNotFoundException。
     * 调用方应先检查，不可解析就静静回落到系统面板
     */
    fun isResolvable(context: Context, intent: Intent): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }.isNotEmpty()
        }.getOrDefault(false)
}
