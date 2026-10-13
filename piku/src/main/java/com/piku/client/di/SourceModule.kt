package com.piku.client.di

import com.piku.client.core.data.FavoriteRepository
import com.piku.client.core.model.Work
import com.piku.client.core.source.ShellFavorites
import com.piku.client.core.source.SourceLinkParser
import com.piku.client.core.source.SourceLinkResolver
import com.piku.client.core.ui.shell.SourceFeedConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SourceModule {

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
