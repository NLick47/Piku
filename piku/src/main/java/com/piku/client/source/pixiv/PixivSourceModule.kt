package com.piku.client.source.pixiv

import com.piku.client.core.source.ContentSource
import com.piku.client.core.source.SourceAuth
import com.piku.client.core.source.SourceFollows
import com.piku.client.core.source.SourceLinkParser
import com.piku.client.core.source.SourceSearch
import com.piku.client.core.ui.drawer.SourceDrawerPlugin
import com.piku.client.source.pixiv.data.auth.PixivAuthRepository
import com.piku.client.source.pixiv.data.link.PixivLinkParser
import com.piku.client.source.pixiv.data.source.PixivContentSource
import com.piku.client.source.pixiv.data.source.PixivFollowsSource
import com.piku.client.source.pixiv.data.source.PixivSearchSource
import com.piku.client.source.pixiv.ui.drawer.PixivDrawerPlugin
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object PixivSourceModule {

    @Provides
    @IntoSet
    fun pixivContentSource(impl: PixivContentSource): ContentSource = impl

    @Provides
    @IntoSet
    fun pixivAuth(impl: PixivAuthRepository): SourceAuth = impl

    @Provides
    @IntoSet
    fun pixivFollows(impl: PixivFollowsSource): SourceFollows = impl

    /** 搜索插件按源登记：检索页问这张表，没登记的源走外壳默认搜索 */
    @Provides
    @IntoSet
    fun pixivSearch(impl: PixivSearchSource): SourceSearch = impl

    @Provides
    @IntoSet
    fun pixivDrawerPlugin(impl: PixivDrawerPlugin): SourceDrawerPlugin = impl

    @Provides
    @IntoSet
    fun pixivLinkParser(impl: PixivLinkParser): SourceLinkParser = impl
}
