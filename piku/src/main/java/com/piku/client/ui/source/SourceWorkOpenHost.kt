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
import com.piku.client.domain.model.key
import com.piku.client.domain.source.SourceRegistry
import com.piku.client.domain.source.SourceWorkOpen
import com.piku.client.ui.theme.PikuColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SourceOpenViewModel @Inject constructor(
    private val sourceRegistry: SourceRegistry,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** 看图器的 R-18 门：与 poipiku 详情的门同开关，判定在查看器自己这里 */
    val adultEnabled: StateFlow<Boolean> = settingsRepository.showAdultContent

    fun open(work: Work): SourceWorkOpen = sourceRegistry.byId(work.source).open(work)
}

/**
 * 「按作品所属源打开」的统一入口：非 poipiku 作品在收藏/历史等跨源列表里点击时用它。
 * 打开方式由源自己的 [SourceWorkOpen] 声明决定——应用内看图器或跳外部浏览器；
 * poipiku 作品不进这里，它继续走主壳的专属详情路由（登录门/R-18 门/密码门都在那条链路里）。
 */
@Composable
internal fun SourceWorkOpenHost(
    work: Work,
    dark: Boolean,
    onDismiss: () -> Unit,
    viewModel: SourceOpenViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val adultEnabled by viewModel.adultEnabled.collectAsState()
    when (val open = viewModel.open(work)) {
        is SourceWorkOpen.External -> LaunchedEffect(work.key) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(open.url)))
            }
            onDismiss()
        }

        SourceWorkOpen.InAppViewer -> if (work.r18 && !adultEnabled) {
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
            SourceWorkDetailDialog(work = work, dark = dark, onDismiss = onDismiss)
        }
    }
}
