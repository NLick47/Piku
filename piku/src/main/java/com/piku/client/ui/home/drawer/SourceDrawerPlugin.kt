package com.piku.client.ui.home.drawer

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import javax.inject.Inject
import javax.inject.Singleton

interface SourceDrawerPlugin {

    val source: WorkSource

    /**
     * 本源接管外壳设置区的「成人内容」行：true 时外壳不再渲染通用 R-18 开关。
     * 用于开关对本源无效的源（如 pixiv：R-18/敏感的下发由账号侧表示设置在服务端管控）。
     */
    val ownsAdultRow: Boolean get() = false

    /**
     * 组合期声明本源的抽屉贡献。在这里读本源的登录态/资料流，决定给哪些条目；
     * 没有独有功能就不覆写。条目会由外壳按 [DrawerContribution.slot] 插进固定排版。
     */
    @Composable
    fun contributions(scope: DrawerScope): List<DrawerContribution> = emptyList()
}

/** 贡献的摆放位置。排列顺序即抽屉从上到下的插入点 */
enum class DrawerSlot {
    /** 账号动作区：账号管理行之下、资料库分隔线之上（poipiku 的投稿/编辑资料） */
    Account,

    /** 资料库区末尾（poipiku 的关注/屏蔽列表） */
    Library,

    /** 设置区末尾：本源特有的设置项 */
    Settings,
}

/** 一个抽屉菜单条目。声明式：外壳用与通用行完全同款的组件渲染 */
data class DrawerEntry(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/** 插件往一个槽位里放的一组条目 */
data class DrawerContribution(
    val slot: DrawerSlot,
    val entries: List<DrawerEntry>,
)

/**
 * 外壳喂给插件的环境。插件用它拼自己的功能入口，外壳不解释条目语义——
 * 插件能拿到的只有当前源账号行、通用导航原语与浮层通道，碰不到其它源。
 */
interface DrawerScope {

    /** 当前首页源的账号行（抽屉头部同款数据）。插件只会在自己的 source 命中当前源时被调用 */
    val account: SourceAccountRow?

    /** 去当前源的登录页；该源没有应用内登录页时落到主壳登录 */
    fun openLogin()

    /** 外壳导航：打开作品详情 */
    fun openWork(work: Work)

    /** 外壳导航：打开用户主页 */
    fun openAuthorProfile(uid: Long, name: String)

    /**
     * 打开本源声明的浮层（投稿页/资料编辑/列表页都走这一条通道）。
     * [closeDrawer] = 先收起抽屉再开（全屏页）；false = 盖在抽屉上（sheet 类）。
     * content 的两个回调：[onDismiss] 关闭浮层并恢复抽屉（返回/取消路径），
     * [onClose] 只关闭浮层（关闭后接着要做外壳导航的路径）。
     */
    fun openOverlay(
        closeDrawer: Boolean = true,
        content: @Composable (onDismiss: () -> Unit, onClose: () -> Unit) -> Unit,
    )
}

/** 抽屉插件注册表：外壳按当前源取插件，查不到 = 该源没有独有功能 */
@Singleton
class SourceDrawerRegistry @Inject constructor(
    plugins: Set<@JvmSuppressWildcards SourceDrawerPlugin>,
) {
    private val byId: Map<WorkSource, SourceDrawerPlugin> = plugins.associateBy { it.source }

    fun byIdOrNull(source: WorkSource): SourceDrawerPlugin? = byId[source]
}
