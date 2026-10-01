package com.piku.client.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.piku.client.BuildConfig
import com.piku.client.ui.common.AvatarViewerDialog
import com.piku.client.ui.home.sheets.AboutSheet
import com.piku.client.ui.home.sheets.AiTranslateSheet
import com.piku.client.ui.home.sheets.CatalogSourceScreen
import com.piku.client.ui.home.sheets.CategorySheet
import com.piku.client.ui.home.sheets.HomeSourceSheet
import com.piku.client.ui.home.sheets.ImageRouteSheet
import com.piku.client.ui.home.sheets.LanguageSheet
import com.piku.client.ui.home.sheets.NetworkDiagDialog
import com.piku.client.ui.home.sheets.RetentionSheet
import com.piku.client.ui.home.sheets.ThemeModeSheet
import com.piku.client.ui.home.sheets.WebDavSettingsScreen

private const val GITHUB_REPO_URL = "https://github.com/NLick47/Piku"
private const val GITHUB_ISSUES_URL = "https://github.com/NLick47/Piku/issues"

/** 显示用版本号：debug 构建用 DEBUG_VERSION_NAME，release 去掉 "-xxx" 后缀 */
internal fun displayVersionName(): String =
    if (BuildConfig.DEBUG && BuildConfig.DEBUG_VERSION_NAME.isNotBlank()) {
        BuildConfig.DEBUG_VERSION_NAME
    } else {
        BuildConfig.VERSION_NAME.substringBefore("-")
    }

/**
 * 首页二级弹层的可见开关。宿主持有（经抽屉/主壳触发置位），[HomeDialogs] 负责渲染与销账；
 * 各开关互斥性不成立（如 AI 翻译弹层上还能叠模型目录、关于页上能叠网络诊断），所以是平铺布尔而不是单槽枚举。
 * 资料编辑不在其中：它是 poipiku 账号的能力，由它的抽屉插件自己挂载。
 */
@Stable
internal class HomeDialogsState internal constructor(
    private val categories: MutableState<Boolean>,
    private val themeSheet: MutableState<Boolean>,
    private val homeSourceSheet: MutableState<Boolean>,
    private val imageRouteSheet: MutableState<Boolean>,
    private val retentionSheet: MutableState<Boolean>,
    private val languageSheet: MutableState<Boolean>,
    private val aiTranslateSheet: MutableState<Boolean>,
    private val aboutSheet: MutableState<Boolean>,
    private val webDavSettings: MutableState<Boolean>,
    private val avatarViewer: MutableState<Boolean>,
) {
    var showCategories: Boolean
        get() = categories.value
        set(value) { categories.value = value }
    var showThemeSheet: Boolean
        get() = themeSheet.value
        set(value) { themeSheet.value = value }
    var showHomeSource: Boolean
        get() = homeSourceSheet.value
        set(value) { homeSourceSheet.value = value }
    var showImageRouteSheet: Boolean
        get() = imageRouteSheet.value
        set(value) { imageRouteSheet.value = value }
    var showRetentionSheet: Boolean
        get() = retentionSheet.value
        set(value) { retentionSheet.value = value }
    var showLanguageSheet: Boolean
        get() = languageSheet.value
        set(value) { languageSheet.value = value }
    var showAiTranslateSheet: Boolean
        get() = aiTranslateSheet.value
        set(value) { aiTranslateSheet.value = value }
    var showAboutSheet: Boolean
        get() = aboutSheet.value
        set(value) { aboutSheet.value = value }
    var showWebDavSettings: Boolean
        get() = webDavSettings.value
        set(value) { webDavSettings.value = value }
    var showAvatarViewer: Boolean
        get() = avatarViewer.value
        set(value) { avatarViewer.value = value }
}

@Composable
internal fun rememberHomeDialogsState(): HomeDialogsState {
    val categories = rememberSaveable { mutableStateOf(false) }
    val themeSheet = rememberSaveable { mutableStateOf(false) }
    val homeSourceSheet = rememberSaveable { mutableStateOf(false) }
    val imageRouteSheet = rememberSaveable { mutableStateOf(false) }
    val retentionSheet = rememberSaveable { mutableStateOf(false) }
    val languageSheet = rememberSaveable { mutableStateOf(false) }
    val aiTranslateSheet = rememberSaveable { mutableStateOf(false) }
    val aboutSheet = rememberSaveable { mutableStateOf(false) }
    val webDavSettings = rememberSaveable { mutableStateOf(false) }
    val avatarViewer = rememberSaveable { mutableStateOf(false) }
    return remember {
        HomeDialogsState(
            categories = categories,
            themeSheet = themeSheet,
            homeSourceSheet = homeSourceSheet,
            imageRouteSheet = imageRouteSheet,
            retentionSheet = retentionSheet,
            languageSheet = languageSheet,
            aiTranslateSheet = aiTranslateSheet,
            aboutSheet = aboutSheet,
            webDavSettings = webDavSettings,
            avatarViewer = avatarViewer,
        )
    }
}

/**
 * 首页二级弹层宿主：抽屉里能打开的设置弹层、关于/诊断、AI 翻译与模型目录、WebDAV、
 * 资料编辑与头像查看都在这里挂载。宿主只置位 [HomeDialogsState]，不看弹层内部结构。
 */
@Composable
internal fun HomeDialogs(
    dialogs: HomeDialogsState,
    viewModel: HomeViewModel,
    state: HomeUiState,
    /** 头像查看器看的图：当前首页源抽屉头部那个头像（userProfile 只是 poipiku 的） */
    headerAvatarUrl: String?,
    dark: Boolean,
    onOpenUpdate: () -> Unit,
    /** 全屏二级页（WebDAV）返回时收起页面并重新拉出抽屉 */
    onCloseAndReopenDrawer: () -> Unit,
) {
    val context = LocalContext.current
    // 模型目录只在 AI 翻译弹层里打开，诊断只在关于页里打开：开关是本宿主的私有状态
    var showCatalogSource by rememberSaveable { mutableStateOf(false) }
    var showNetworkDiag by rememberSaveable { mutableStateOf(false) }

    if (dialogs.showCategories) {
        CategorySheet(
            selected = state.category,
            onSelect = {
                viewModel.selectCategory(it)
                dialogs.showCategories = false
            },
            onDismiss = { dialogs.showCategories = false },
            dark = dark,
        )
    }

    if (dialogs.showHomeSource) {
        HomeSourceSheet(
            selected = state.homeSource,
            options = viewModel.sourceOptions,
            labelRes = viewModel::homeSourceLabelRes,
            onSelect = { source ->
                viewModel.setHomeSource(source)
                dialogs.showHomeSource = false
            },
            onDismiss = { dialogs.showHomeSource = false },
            dark = dark,
        )
    }

    if (dialogs.showThemeSheet) {
        ThemeModeSheet(
            selected = state.themeMode,
            onSelect = { mode ->
                viewModel.setThemeMode(mode)
                dialogs.showThemeSheet = false
            },
            onDismiss = { dialogs.showThemeSheet = false },
            dark = dark,
        )
    }

    if (dialogs.showImageRouteSheet) {
        ImageRouteSheet(
            selected = state.imageRouteMode,
            onSelect = { mode ->
                viewModel.setImageRouteMode(mode)
                dialogs.showImageRouteSheet = false
            },
            onDismiss = { dialogs.showImageRouteSheet = false },
            dark = dark,
        )
    }

    if (dialogs.showRetentionSheet) {
        RetentionSheet(
            selectedDays = state.historyRetentionDays,
            onSelect = { days ->
                viewModel.setHistoryRetentionDays(days)
                dialogs.showRetentionSheet = false
            },
            onDismiss = { dialogs.showRetentionSheet = false },
            dark = dark,
        )
    }

    if (dialogs.showLanguageSheet) {
        LanguageSheet(
            selected = state.language,
            onSelect = { language ->
                viewModel.setLanguage(language)
                dialogs.showLanguageSheet = false
                (context as? Activity)?.recreate()
            },
            onDismiss = { dialogs.showLanguageSheet = false },
            dark = dark,
        )
    }

    if (dialogs.showAiTranslateSheet) {
        AiTranslateSheet(
            state = state,
            onToggleEnabled = viewModel::setAiTranslateEnabled,
            onToggleTagsAuto = viewModel::setAutoTranslateTags,
            onSelectModel = viewModel::selectTranslateModel,
            onSelectNovelModel = viewModel::selectTranslateNovelModel,
            onSelectImageModel = viewModel::selectTranslateImageModel,
            onSaveCatalog = viewModel::saveCatalog,
            onResetCatalog = viewModel::resetCatalogUrl,
            onActivateSource = viewModel::activateCatalogSource,
            onSaveAsSource = { url, key -> viewModel.saveCatalogAsSource(null, url, key) },
            onRenameSource = { source, name -> viewModel.renameCatalogSource(source.id, name) },
            onDeleteSource = { source -> viewModel.deleteCatalogSource(source.id) },
            onOpenSources = { showCatalogSource = true },
            catalogOpen = showCatalogSource,
            onDismiss = { dialogs.showAiTranslateSheet = false },
            dark = dark,
        )
    }

    if (showCatalogSource) {
        Dialog(
            onDismissRequest = { showCatalogSource = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false,
            ),
        ) {
            CatalogSourceScreen(
                state = state,
                onSaveCatalog = viewModel::saveCatalog,
                onResetCatalog = viewModel::resetCatalogUrl,
                onActivateSource = viewModel::activateCatalogSource,
                onSaveAsSource = { url, key -> viewModel.saveCatalogAsSource(null, url, key) },
                onRenameSource = { source, name -> viewModel.renameCatalogSource(source.id, name) },
                onDeleteSource = { source -> viewModel.deleteCatalogSource(source.id) },
                onBack = { showCatalogSource = false },
                dark = dark,
            )
        }
    }

    if (dialogs.showAvatarViewer) {
        AvatarViewerDialog(
            avatarUrl = headerAvatarUrl,
            onDismiss = { dialogs.showAvatarViewer = false },
            onSave = { url -> viewModel.saveAvatar(url) },
        )
    }

    if (dialogs.showAboutSheet) {
        AboutSheet(
            currentVersion = displayVersionName(),
            autoCheckEnabled = state.autoCheckEnabled,
            updateCheckState = state.updateCheckState,
            onToggleAutoCheck = { viewModel.setAutoCheckEnabled(!state.autoCheckEnabled) },
            onCheckUpdate = {
                if (state.updateCheckState !is UpdateCheckState.Checking) {
                    viewModel.checkForUpdateManual()
                }
            },
            onOpenUpdate = onOpenUpdate,
            onOpenGithub = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL)))
            },
            onOpenFeedback = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_ISSUES_URL)))
            },
            onOpenNetworkDiag = {
                viewModel.refreshNetworkReport(live = false)
                showNetworkDiag = true
            },
            onDismiss = { dialogs.showAboutSheet = false },
            dark = dark,
        )
    }

    if (showNetworkDiag) {
        val report by viewModel.networkReport.collectAsStateWithLifecycle()
        NetworkDiagDialog(
            text = report.text,
            loading = report.loading,
            onRefresh = { viewModel.refreshNetworkReport(live = true) },
            onClear = { viewModel.clearNetworkDiagnostics() },
            onDismiss = { showNetworkDiag = false },
            dark = dark,
        )
    }

    if (dialogs.showWebDavSettings) {
        Dialog(
            onDismissRequest = { dialogs.showWebDavSettings = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false,
            ),
        ) {
            WebDavSettingsScreen(
                url = state.webDavUrl,
                username = state.webDavUsername,
                password = state.webDavPassword,
                enabled = state.webDavEnabled,
                lastSyncAt = state.lastSyncAt,
                syncResult = state.syncResult,
                syncState = state.syncState,
                testConnectionState = state.testConnectionState,
                onUrlChange = viewModel::setWebDavUrl,
                onUsernameChange = viewModel::setWebDavUsername,
                onPasswordChange = viewModel::setWebDavPassword,
                onEnabledChange = viewModel::setWebDavEnabled,
                onTestConnection = viewModel::testWebDavConnection,
                onClearTestResult = viewModel::clearTestConnectionState,
                onSyncNow = viewModel::syncNow,
                onBack = {
                    dialogs.showWebDavSettings = false
                    onCloseAndReopenDrawer()
                },
                dark = dark,
            )
        }
    }
}
