package com.piku.client.source.poipiku

import com.piku.client.core.source.ContentSource
import com.piku.client.core.source.SourceAuth
import com.piku.client.core.source.SourceContentBackup
import com.piku.client.core.source.SourceFollows
import com.piku.client.core.source.SourceLinkParser
import com.piku.client.core.ui.drawer.SourceDrawerPlugin
import com.piku.client.source.poipiku.data.auth.PoipikuAuth
import com.piku.client.source.poipiku.data.link.PoipikuLinkParser
import com.piku.client.source.poipiku.data.source.PoipikuContentBackup
import com.piku.client.source.poipiku.data.source.PoipikuContentSource
import com.piku.client.source.poipiku.data.source.PoipikuFollowsSource
import com.piku.client.source.poipiku.ui.drawer.PoipikuDrawerPlugin
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object PoipikuSourceModule {

    @Provides
    @IntoSet
    fun poipikuContentSource(impl: PoipikuContentSource): ContentSource = impl

    /** 内容备份能力按源登记：WebDAV 同步只认这张表，没登记的源只同步元数据 */
    @Provides
    @IntoSet
    fun poipikuContentBackup(impl: PoipikuContentBackup): SourceContentBackup = impl

    /** 登录插件按源登记；账号管理、备份、登录门都从这里问，不再各自硬编码站点 */
    @Provides
    @IntoSet
    fun poipikuAuth(impl: PoipikuAuth): SourceAuth = impl

    /** 关注能力按源登记：关注列表路由按源问这张表，没登记的源不出「我的关注」入口 */
    @Provides
    @IntoSet
    fun poipikuFollows(impl: PoipikuFollowsSource): SourceFollows = impl

    /** 抽屉插件按源登记：源专属抽屉条目与浮层由插件自己声明，外壳只按当前源渲染 */
    @Provides
    @IntoSet
    fun poipikuDrawerPlugin(impl: PoipikuDrawerPlugin): SourceDrawerPlugin = impl

    /** 链接解析插件按源登记：深链唤起与搜索框粘贴共用同一张表 */
    @Provides
    @IntoSet
    fun poipikuLinkParser(impl: PoipikuLinkParser): SourceLinkParser = impl
}
