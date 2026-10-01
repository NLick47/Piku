package com.piku.client.di

import com.piku.client.data.auth.PixivAuthRepository
import com.piku.client.data.auth.PoipikuAuth
import com.piku.client.data.repository.FavoriteRepository
import com.piku.client.data.source.PixivContentSource
import com.piku.client.data.source.PixivSearchSource
import com.piku.client.data.source.PoipikuContentBackup
import com.piku.client.data.source.PoipikuContentSource
import com.piku.client.domain.model.Work
import com.piku.client.domain.source.ContentSource
import com.piku.client.domain.source.PixivLinkParser
import com.piku.client.domain.source.PoipikuLinkParser
import com.piku.client.domain.source.ShellFavorites
import com.piku.client.domain.source.SourceAuth
import com.piku.client.domain.source.SourceContentBackup
import com.piku.client.domain.source.SourceLinkParser
import com.piku.client.domain.source.SourceLinkResolver
import com.piku.client.domain.source.SourceSearch
import com.piku.client.ui.home.drawer.PixivDrawerPlugin
import com.piku.client.ui.home.drawer.PoipikuDrawerPlugin
import com.piku.client.ui.home.drawer.SourceDrawerPlugin
import com.piku.client.ui.source.SourceFeedConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SourceModule {

    @Provides
    @IntoSet
    fun poipikuContentSource(impl: PoipikuContentSource): ContentSource = impl

    @Provides
    @IntoSet
    fun pixivContentSource(impl: PixivContentSource): ContentSource = impl

    /** 内容备份能力按源登记：WebDAV 同步只认这张表，没登记的源只同步元数据 */
    @Provides
    @IntoSet
    fun poipikuContentBackup(impl: PoipikuContentBackup): SourceContentBackup = impl

    /** 登录插件按源登记；账号管理、备份、登录门都从这里问，不再各自硬编码站点 */
    @Provides
    @IntoSet
    fun poipikuAuth(impl: PoipikuAuth): SourceAuth = impl

    @Provides
    @IntoSet
    fun pixivAuth(impl: PixivAuthRepository): SourceAuth = impl

    /** 搜索插件按源登记：检索页问这张表，没登记的源走外壳默认搜索 */
    @Provides
    @IntoSet
    fun pixivSearch(impl: PixivSearchSource): SourceSearch = impl

    /** 抽屉插件按源登记：源专属抽屉条目与浮层由插件自己声明，外壳只按当前源渲染 */
    @Provides
    @IntoSet
    fun poipikuDrawerPlugin(impl: PoipikuDrawerPlugin): SourceDrawerPlugin = impl

    @Provides
    @IntoSet
    fun pixivDrawerPlugin(impl: PixivDrawerPlugin): SourceDrawerPlugin = impl

    /** 链接解析插件按源登记：深链唤起与搜索框粘贴共用同一张表 */
    @Provides
    @IntoSet
    fun poipikuLinkParser(impl: PoipikuLinkParser): SourceLinkParser = impl

    @Provides
    @IntoSet
    fun pixivLinkParser(impl: PixivLinkParser): SourceLinkParser = impl

    /** 聚合各源的链接解析：host 命名空间互不重叠，轮询先命中先得 */
    @Provides
    @Singleton
    fun provideSourceLinkResolver(parsers: Set<@JvmSuppressWildcards SourceLinkParser>): SourceLinkResolver =
        SourceLinkResolver(parsers.toList())

    /** 收藏态与收藏切换同理：外壳只依赖能力接口 */
    @Provides
    @Singleton
    fun provideShellFavorites(repository: FavoriteRepository): ShellFavorites =
        object : ShellFavorites {
            override val favoriteIds = repository.observeFavoriteIds()
            override suspend fun toggle(work: Work): Boolean = repository.toggleFavorite(work)
        }

    @Provides
    @Singleton
    fun provideSourceFeedConfig(): SourceFeedConfig = SourceFeedConfig()
}
