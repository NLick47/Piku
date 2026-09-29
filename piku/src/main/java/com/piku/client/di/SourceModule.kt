package com.piku.client.di

import com.piku.client.data.repository.AuthRepository
import com.piku.client.data.source.PixivContentSource
import com.piku.client.data.source.PoipikuContentSource
import com.piku.client.domain.source.ContentSource
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

    /** 需要登录的流只问「登录了没」；注入函数而非 AuthRepository，外壳保持无状态依赖 */
    @Provides
    @Singleton
    fun provideIsLoggedIn(authRepository: AuthRepository): () -> Boolean = authRepository::isLoggedIn

    @Provides
    @Singleton
    fun provideSourceFeedConfig(): SourceFeedConfig = SourceFeedConfig()
}
