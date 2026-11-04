package com.piku.client.core.source

import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkSource

/**
 * 收藏状态变化后的远程镜像能力。按源登记：没登记的源只做本地收藏。
 *
 * 实现方自己判断设置开关与登录态；关闭时返回 success（不算失败），
 * 只有真正调用远端失败才返回 failure，由调度器决定是否提示。
 */
interface SourceFavoriteMirror {
    val source: WorkSource

    /**
     * 提交任务时的账号标识；null = 该源没有账号概念。
     * 调度器在执行任务时会再取一次，不一致就丢弃，避免提交后换号把收藏写到另一个账号。
     */
    fun accountKey(): String? = null

    /**
     * [favorited] 是本地收藏动作完成后的状态：
     * true = 现在应存在于远程收藏，false = 现在应从远程收藏移除。
     */
    suspend fun mirror(work: Work, favorited: Boolean): Result<Unit>
}
