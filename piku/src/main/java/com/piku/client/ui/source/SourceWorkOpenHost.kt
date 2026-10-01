package com.piku.client.ui.source

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.piku.client.R
import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.model.key
import com.piku.client.domain.source.AuthorPageStyle
import com.piku.client.domain.source.SourceAuthorOpen
import com.piku.client.domain.source.SourceAuthRegistry
import com.piku.client.domain.source.SourceRegistry
import com.piku.client.domain.source.SourceWorkOpen
import com.piku.client.ui.theme.PikuColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SourceOpenViewModel @Inject constructor(
    private val sourceRegistry: SourceRegistry,
    private val authRegistry: SourceAuthRegistry,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** 看图器的 R-18 门：与 poipiku 详情的门同开关，判定在查看器自己这里 */
    val adultEnabled: StateFlow<Boolean> = settingsRepository.showAdultContent

    fun open(work: Work): SourceWorkOpen = sourceRegistry.byId(work.source).open(work)

    /** 这一源的作品是否由主壳详情页承载；是的话调用方直接走自家详情路由 */
    fun opensInNativeShell(work: Work): Boolean = open(work) is SourceWorkOpen.NativeDetail

    /** 点作者的去向；null = 本源没有作者页，作者区不可点 */
    fun authorPage(work: Work): SourceAuthorOpen? = sourceRegistry.byId(work.source).authorPage(work)

    /**
     * 作者页形态。从 FollowUser 入口（我的关注/搜索用户）进来时没有 Work 可问，
     * 由调用方带上源；从 Work 进来时可配合 [authorPage] 的返回值一起用。
     */
    fun authorPageStyle(source: WorkSource): AuthorPageStyle = sourceRegistry.authorPageStyle(source)

    /** 源自己的登录页路由；null = 本源没注册登录插件 */
    fun loginRoute(source: WorkSource): String? = authRegistry.byId(source)?.loginRoute
}

/**
 * 「按作品所属源打开」的统一入口：非主壳详情的作品在收藏/历史等跨源列表里点击时用它。
 * 打开方式由源自己的 [SourceWorkOpen] 声明决定——应用内看图器或跳外部浏览器；
 * 声明 [SourceWorkOpen.NativeDetail] 的作品该走主壳详情路由，调用方先用
 * [SourceOpenViewModel.opensInNativeShell] 问过，真到了这里只把浮层收掉。
 */
@Composable
internal fun SourceWorkOpenHost(
    work: Work,
    dark: Boolean,
    onDismiss: () -> Unit,
    /** 详情里点作者：宿主给应用内跳转；null = 退回出站到源的网页（宿主没能力导航时） */
    onOpenAuthor: ((Work) -> Unit)? = null,
    viewModel: SourceOpenViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val adultEnabled by viewModel.adultEnabled.collectAsState()
    when (val open = viewModel.open(work)) {
        SourceWorkOpen.NativeDetail -> LaunchedEffect(work.key) { onDismiss() }

        is SourceWorkOpen.External -> LaunchedEffect(work.key) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(open.url)))
            }
            onDismiss()
        }

        // 成人门只对 poipiku 系生效：pixiv 的 R-18 由账号侧表示设置在服务端管控，不再设门
        SourceWorkOpen.InAppViewer -> if (work.source != WorkSource.PIXIV && work.r18 && !adultEnabled) {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.detail_gate_adult_title)) },
                text = { Text(stringResource(R.string.detail_gate_adult_body)) },
                confirmButton = {
                    Text(
                        text = stringResource(R.string.detail_fullscreen_close),
                        color = PikuColors.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                },
            )
        } else {
            SourceWorkDetailDialog(
                work = work,
                dark = dark,
                onDismiss = onDismiss,
                onOpenAuthor = onOpenAuthor,
            )
        }
    }
}
