package com.piku.client.di

import com.piku.client.data.repository.AuthRepository
import com.piku.client.data.source.PixivContentSource
import com.piku.client.data.source.PoipikuContentBackup
import com.piku.client.data.source.PoipikuContentSource
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import com.piku.client.data.repository.FavoriteRepository
import com.piku.client.domain.source.ContentSource
import com.piku.client.domain.source.ShellFavorites
import com.piku.client.domain.source.SourceContentBackup
import com.piku.client.domain.source.SourceLogin
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

    /** 需要登录的流只问「这个源登录了没」。两套账号体系：poipiku 问 poipiku 会话，
     *  pixiv 登录工程接入前一律未登录——poipiku 的登录态不得误开 pixiv 的门 */
    @Provides
    @Singleton
    fun provideSourceLogin(authRepository: AuthRepository): SourceLogin = SourceLogin { source ->
        if (source == WorkSource.POIPIKU) authRepository.isLoggedIn() else false
    }

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
