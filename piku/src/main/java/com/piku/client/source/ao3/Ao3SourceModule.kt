package com.piku.client.source.ao3

import android.content.Context
import com.piku.client.core.source.ContentSource
import com.piku.client.core.source.SourceLinkParser
import com.piku.client.core.source.SourceScopes
import com.piku.client.core.source.SourceSearch
import com.piku.client.core.ui.drawer.SourceDrawerPlugin
import com.piku.client.source.ao3.data.link.Ao3LinkParser
import com.piku.client.source.ao3.data.local.Ao3ScopeStore
import com.piku.client.source.ao3.data.source.Ao3ContentSource
import com.piku.client.source.ao3.data.source.Ao3Scopes
import com.piku.client.source.ao3.data.source.Ao3SearchSource
import com.piku.client.source.ao3.ui.drawer.Ao3DrawerPlugin
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object Ao3SourceModule {

    @Provides
    @IntoSet
    fun ao3ContentSource(impl: Ao3ContentSource): ContentSource = impl

    /** 检索插件按源登记：没登记的源会落到 poipiku 链路，AO3 的查询词会被当 poipiku 搜 */
    @Provides
    @IntoSet
    fun ao3SearchSource(impl: Ao3SearchSource): SourceSearch = impl

    /** 链接解析按源登记：深链唤起与搜索框粘贴共用同一张表 */
    @Provides
    @IntoSet
    fun ao3LinkParser(impl: Ao3LinkParser): SourceLinkParser = impl

    /** 圈层订阅落盘在 AO3 自己的 prefs 文件里（别的源没有圈层概念，不碰全局设置） */
    @Provides
    @Singleton
    fun ao3ScopeStore(@ApplicationContext context: Context): Ao3ScopeStore =
        Ao3ScopeStore(context.getSharedPreferences(Ao3ScopeStore.PREFS_NAME, Context.MODE_PRIVATE))

    /** 圈层能力按源登记：首页外壳按它渲染圈层行与选择器，没登记的源保持通用流式壳 */
    @Provides
    @IntoSet
    fun ao3Scopes(impl: Ao3Scopes): SourceScopes = impl

    /** 抽屉插件按源登记：给 AO3 一条「我的圈层」入口，抽屉外壳不认站点名 */
    @Provides
    @IntoSet
    fun ao3DrawerPlugin(impl: Ao3DrawerPlugin): SourceDrawerPlugin = impl
}
