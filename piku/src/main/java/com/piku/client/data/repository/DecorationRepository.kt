package com.piku.client.data.repository

import android.content.Context
import android.util.Log
import com.piku.client.data.local.DecorationDao
import com.piku.client.data.local.DecorationItem
import com.piku.client.domain.model.WorkDetail
import com.piku.client.ui.widget.DecorationWidgetReceiver
import com.piku.client.ui.widget.isWidgetSafe
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 桌面装饰白名单的唯一入口。widget 只渲染 [decorationDao] 的表，
 * 不接触收藏/历史/feed——服务端漏标的成人内容进不了桌面。
 */
@Singleton
class DecorationRepository @Inject constructor(
    private val decorationDao: DecorationDao,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context,
) {

    fun observeAll() = decorationDao.observeAll()

    /** 管理页预览用：本地图片文件（可能已被用户清理，返回不存在的路径由 UI 兜底） */
    fun imageFile(item: DecorationItem): String =
        File(decorationDir(), item.fileName).absolutePath

    /**
     * 白名单入库的最后一步：[detail] 已通过 [isWidgetSafe] 多信号审核
     * （UI 层在拉完详情后先审一次，拒绝入库给提示），这里负责把
     * [imageUrl] 指定的那张图（用户长按的那一页）下载到 App 私有目录并落库。
     * 下载失败回滚即整单失败。WorkDetail 本身不带 id，由调用方传入。
     */
    suspend fun add(
        detail: WorkDetail,
        workId: Long,
        authorId: Long,
        imageUrl: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
            val fileName = "decoration_$workId.img"
            val target = File(decorationDir(), fileName)
            runCatching {
                download(imageUrl, target)
                decorationDao.upsert(
                    DecorationItem(
                        workId = workId,
                        authorId = authorId,
                        title = detail.title,
                        authorName = detail.authorName,
                        fileName = fileName,
                        addedAt = System.currentTimeMillis(),
                        r18 = detail.r18,
                        warning = detail.warning,
                        adultLocked = detail.adultLocked,
                        passwordProtected = detail.passwordProtected,
                        categoryCd = detail.categoryCd,
                        tags = detail.tags.joinToString(","),
                    ),
                )
            }.onFailure { e ->
                Log.d(TAG, "add decoration work=$workId fail: ${e.message}", e)
                target.delete()
            }.map { DecorationWidgetReceiver.requestUpdate(context) }
        }

    suspend fun remove(workId: Long) {
        val item = decorationDao.get(workId)
        decorationDao.delete(workId)
        if (item != null) {
            File(decorationDir(), item.fileName).delete()
        }
        DecorationWidgetReceiver.requestUpdate(context)
    }

    /**
     * 总开关变化后的联动。[clearData] 为 true 时清空白名单与已下载图片——
     * 隐私诉求（不想让装饰图片留在本地/桌面）通常要求这一步；
     * 之后无论如何都刷新 widget（关闭态占位 / 恢复渲染）并重排轮播调度。
     */
    suspend fun onFeatureToggled(clearData: Boolean) {
        if (clearData) {
            withContext(Dispatchers.IO) {
                decorationDao.getAllOnce().forEach { item ->
                    File(decorationDir(), item.fileName).delete()
                }
                decorationDao.deleteAll()
            }
        }
        DecorationWidgetReceiver.requestUpdate(context)
    }

    /** 图片文件都在这个私有目录，widget 无需任何存储权限即可读 */
    private fun decorationDir(): File =
        File(context.filesDir, "decoration").apply { mkdirs() }

    private fun download(url: String, target: File) {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.outputStream().use { out -> response.body.byteStream().copyTo(out) }
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw java.io.IOException("rename failed")
            }
        }
    }

    private companion object {
        const val TAG = "PikuDiag"
    }
}
