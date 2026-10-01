package com.piku.client.ui.home.background

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.data.local.SettingsRepository
import com.piku.client.ui.home.HomeUiState
import com.piku.client.ui.home.HomeViewModel
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.PikuColors
import kotlin.math.roundToInt

/** 进入编辑时的背景值快照：revert 的基准 */
internal class HomeBackgroundEditOriginals {
    var backgroundOffsetX = 0f
    var backgroundOffsetY = 0f
    var backgroundDim = SettingsRepository.BACKGROUND_DIM_DEFAULT
    var backgroundScale = SettingsRepository.BACKGROUND_SCALE_DEFAULT
    var heroOffsetX = 0f
    var heroOffsetY = 0f
    var heroScale = SettingsRepository.HERO_SCALE_DEFAULT
    var backgroundBlur = SettingsRepository.BACKGROUND_BLUR_DEFAULT
    var backgroundHeroFraction = SettingsRepository.BACKGROUND_HERO_DEFAULT

    fun snapshot(s: HomeUiState) {
        backgroundOffsetX = s.backgroundOffsetX
        backgroundOffsetY = s.backgroundOffsetY
        backgroundDim = s.backgroundDim
        backgroundScale = s.backgroundScale
        heroOffsetX = s.heroOffsetX
        heroOffsetY = s.heroOffsetY
        heroScale = s.heroScale
        backgroundBlur = s.backgroundBlur
        backgroundHeroFraction = s.backgroundHeroFraction
    }
}

/**
 * 首页背景的编辑会话：进出的状态机、图片选择器与提交/还原语义。
 * 手势、蓝图浮层与参数面板在 [HomeBackgroundEditOverlay]，背景层绘制在 [HomeBackdropLayer]。
 */
internal class HomeBackgroundEdit(
    val isEditMode: Boolean,
    /** 预览模式：所见即所得（真实内容）或隐藏内容只调背景 */
    val previewMode: Int,
    /** 当前编辑目标：头部层或独立背景层（背景未设置时强制头部层） */
    val editTarget: Int,
    val panelCollapsed: Boolean,
    private val viewModel: HomeViewModel,
    private val heroPickLauncher: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>,
    private val backdropPickLauncher: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>,
    private val editModeState: MutableState<Boolean>,
    private val previewModeState: MutableState<Int>,
    private val editTargetState: MutableState<Int>,
    private val panelCollapsedState: MutableState<Boolean>,
    private val originals: HomeBackgroundEditOriginals,
) {
    /** 背景层是否处于"隐藏内容预览"：背景层据此进入所见即所得 */
    val previewingContent: Boolean
        get() = isEditMode && previewMode != BG_PREVIEW_REAL

    /** 内容层是否可见：编辑中切到"隐藏内容"预览时淡出 */
    val contentVisible: Boolean
        get() = !isEditMode || previewMode == BG_PREVIEW_REAL

    fun togglePreview() {
        previewModeState.value = if (previewMode == BG_PREVIEW_REAL) BG_PREVIEW_HIDDEN else BG_PREVIEW_REAL
    }

    fun collapsePanel() {
        panelCollapsedState.value = true
    }

    fun selectTarget(target: Int) {
        editTargetState.value = target
    }

    /** 按当前编辑目标把系统图片选择器派给对应层 */
    fun pickImage() {
        if (editTarget == BG_EDIT_TARGET_BACKDROP) {
            backdropPickLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            heroPickLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    /** 从抽屉进入编辑：记下当前值供「还原」，回到所见即所得预览 */
    fun enterEdit(current: HomeUiState) {
        originals.snapshot(current)
        viewModel.consumeBackgroundError()
        previewModeState.value = BG_PREVIEW_REAL
        editModeState.value = true
    }

    /** 关闭编辑：当前所见（含手势未持久化的取景）原样落盘 */
    fun dismiss(current: HomeUiState) {
        persistAll(current)
        editModeState.value = false
    }

    /** 确认：显式持久化头部取景，再按当前所见落盘 */
    fun confirm(current: HomeUiState) {
        viewModel.setHeroOffset(current.heroOffsetX, current.heroOffsetY, persist = true)
        viewModel.setHeroScale(current.heroScale, persist = true)
        persistAll(current)
        editModeState.value = false
    }

    /** 还原到进入编辑时的值 */
    fun revert(current: HomeUiState) {
        viewModel.setHeroOffset(originals.heroOffsetX, originals.heroOffsetY, persist = true)
        viewModel.setHeroScale(originals.heroScale, persist = true)
        viewModel.setBackgroundOffset(originals.backgroundOffsetX, originals.backgroundOffsetY, persist = true)
        viewModel.setBackgroundDim(originals.backgroundDim, persist = true)
        viewModel.setBackgroundScale(originals.backgroundScale, persist = true)
        viewModel.setBackgroundBlur(originals.backgroundBlur, persist = true)
        viewModel.setBackgroundHeroFraction(originals.backgroundHeroFraction, persist = true)
        editModeState.value = false
    }

    /** 滑杆停手：把当前所见落盘（不退出编辑） */
    fun onSettingsFinished(current: HomeUiState) {
        persistAll(current)
    }

    private fun persistAll(current: HomeUiState) {
        viewModel.persistHeroOffset()
        viewModel.setBackgroundOffset(current.backgroundOffsetX, current.backgroundOffsetY, persist = true)
        viewModel.setBackgroundDim(current.backgroundDim, persist = true)
        viewModel.setBackgroundScale(current.backgroundScale, persist = true)
        viewModel.setBackgroundBlur(current.backgroundBlur, persist = true)
        viewModel.setHeroScale(current.heroScale, persist = true)
        viewModel.setBackgroundHeroFraction(current.backgroundHeroFraction, persist = true)
    }
}

@Composable
internal fun rememberHomeBackgroundEdit(
    viewModel: HomeViewModel,
    state: HomeUiState,
): HomeBackgroundEdit {
    val editModeState = rememberSaveable { mutableStateOf(false) }
    val previewModeState = rememberSaveable { mutableIntStateOf(BG_PREVIEW_REAL) }
    val editTargetState = rememberSaveable { mutableIntStateOf(BG_EDIT_TARGET_HERO) }
    val panelCollapsedState = rememberSaveable { mutableStateOf(false) }
    val originals = remember { HomeBackgroundEditOriginals() }

    val heroPickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setCustomBackground(uri)
            editModeState.value = true
        }
    }
    val backdropPickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.setCustomBackdrop(uri)
    }

    LaunchedEffect(state.customBackgroundPath) {
        // 头部图被清除后同步落回头部层，避免残留的背景层目标在下次选图时继续生效
        if (state.customBackgroundPath == null && editTargetState.value != BG_EDIT_TARGET_HERO) {
            editTargetState.value = BG_EDIT_TARGET_HERO
        }
    }

    return HomeBackgroundEdit(
        isEditMode = editModeState.value,
        previewMode = previewModeState.value,
        editTarget = if (state.customBackgroundPath == null) BG_EDIT_TARGET_HERO else editTargetState.value,
        panelCollapsed = panelCollapsedState.value,
        viewModel = viewModel,
        heroPickLauncher = heroPickLauncher,
        backdropPickLauncher = backdropPickLauncher,
        editModeState = editModeState,
        previewModeState = previewModeState,
        editTargetState = editTargetState,
        panelCollapsedState = panelCollapsedState,
        originals = originals,
    )
}

/** 背景层的常驻绘制：自定义头图 + 雾化层，或默认渐变背景 */
@Composable
internal fun HomeBackdropLayer(
    state: HomeUiState,
    backdrop: HomeBackdropState,
    dark: Boolean,
    /** 编辑中且隐藏内容预览：背景层进入所见即所得 */
    editPreviewing: Boolean,
    /** 视差位移源：绘制阶段读取，滚动不触发重组 */
    scrolledOverTopPx: () -> Int,
) {
    val customBgPath = state.customBackgroundPath
    if (customBgPath != null) {
        CustomHomeBackground(
            heroPath = customBgPath,
            heroFrame = backdrop.heroFrame,
            heroHeight = backdrop.zoneHeightDp,
            heroScale = state.heroScale,
            backdropPath = state.backdropPath,
            frostScale = backdrop.frostScale,
            frostOffsetX = backdrop.frostOffsetX,
            frostOffsetY = backdrop.frostOffsetY,
            dim = state.backgroundDim,
            dark = dark,
            scrimDark = state.backgroundScrimDark,
            scrimLight = state.backgroundScrimLight,
            blurDp = state.backgroundBlur,
            editMode = editPreviewing,
            scrolledOverTopPx = scrolledOverTopPx,
        )
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val gradient = homeBackdrop(dark, size)
                    onDrawBehind { drawBackdrop(gradient) }
                },
        )
    }
}

/**
 * 背景编辑浮层：双指缩放/拖拽取景手势、取景蓝图、参数面板（含收起态）。
 * 只在编辑会话激活时由宿主挂载；写入走 [HomeBackgroundEdit] 与 viewModel。
 */
@Composable
internal fun HomeBackgroundEditOverlay(
    viewModel: HomeViewModel,
    edit: HomeBackgroundEdit,
    state: HomeUiState,
    backdrop: HomeBackdropState,
    dark: Boolean,
) {
    // 手势里读到的必须是当前值：pointerInput 的 key 只跟编辑目标/背景层走，
    // 设置改了不会重启它的协程，捕获到旧 state 就会用旧缩放/旧清晰区算平移量
    val currentState by rememberUpdatedState(state)
    val currentViewWidth by rememberUpdatedState(backdrop.viewWidthPx)
    val currentViewHeight by rememberUpdatedState(backdrop.viewHeightPx)
    val currentZoneHeight by rememberUpdatedState(backdrop.zoneHeightPx)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(edit.editTarget, state.backdropPath) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val editedState = currentState
                    if (editedState.customBackgroundPath != null) {
                        val editHero = edit.editTarget == BG_EDIT_TARGET_HERO ||
                            editedState.backdropPath == null
                        if (editHero) {
                            val ns = (editedState.heroScale * zoom).coerceIn(
                                SettingsRepository.HERO_SCALE_MIN,
                                SettingsRepository.HERO_SCALE_MAX,
                            )
                            if (ns != editedState.heroScale) viewModel.setHeroScale(ns)
                            // 按新缩放的取景算可平移量：缩放会同步放大拖动范围，拖拽与手指 1:1
                            val frame = contentFrame(
                                imgWidth = editedState.backgroundImgWidth,
                                imgHeight = editedState.backgroundImgHeight,
                                viewWidth = currentViewWidth,
                                viewHeight = currentZoneHeight,
                                scale = ns,
                                offsetX = editedState.heroOffsetX,
                                offsetY = editedState.heroOffsetY,
                            ) ?: return@detectTransformGestures
                            viewModel.setHeroOffset(
                                dragOffset(editedState.heroOffsetX, pan.x, frame.slackX),
                                dragOffset(editedState.heroOffsetY, pan.y, frame.slackY),
                            )
                        } else {
                            val ns = (editedState.backgroundScale * zoom).coerceIn(
                                SettingsRepository.BACKGROUND_SCALE_MIN,
                                SettingsRepository.BACKGROUND_SCALE_MAX,
                            )
                            if (ns != editedState.backgroundScale) viewModel.setBackgroundScale(ns)
                            val frame = contentFrame(
                                imgWidth = editedState.backdropImgWidth,
                                imgHeight = editedState.backdropImgHeight,
                                viewWidth = currentViewWidth,
                                viewHeight = currentViewHeight,
                                scale = ns,
                                offsetX = editedState.backgroundOffsetX,
                                offsetY = editedState.backgroundOffsetY,
                            ) ?: return@detectTransformGestures
                            viewModel.setBackgroundOffset(
                                dragOffset(editedState.backgroundOffsetX, pan.x, frame.slackX),
                                dragOffset(editedState.backgroundOffsetY, pan.y, frame.slackY),
                            )
                        }
                    }
                }
            }
            .pointerInput(edit.editTarget, state.backdropPath) {
                detectTapGestures(onDoubleTap = {
                    if (state.customBackgroundPath != null) {
                        if (edit.editTarget == BG_EDIT_TARGET_BACKDROP &&
                            state.backdropPath != null
                        ) {
                            viewModel.setBackgroundOffset(0f, 0f, persist = true)
                            viewModel.setBackgroundScale(
                                SettingsRepository.BACKGROUND_SCALE_DEFAULT,
                                persist = true,
                            )
                        } else {
                            viewModel.setHeroOffset(0f, 0f, persist = true)
                            viewModel.setHeroScale(
                                SettingsRepository.HERO_SCALE_DEFAULT,
                                persist = true,
                            )
                        }
                    }
                })
            }
    )

    BackgroundBlueprintOverlay(
        dark = dark,
        zoneHeightPx = backdrop.zoneHeightPx,
        heroFrame = backdrop.heroFrame,
        framedT = ((1f - state.heroScale) / (1f - SettingsRepository.HERO_SCALE_MIN))
            .coerceIn(0f, 1f),
        readoutX = if (edit.editTarget == BG_EDIT_TARGET_BACKDROP &&
            state.backdropPath != null
        ) {
            state.backgroundOffsetX
        } else {
            state.heroOffsetX
        },
        readoutY = if (edit.editTarget == BG_EDIT_TARGET_BACKDROP &&
            state.backdropPath != null
        ) {
            state.backgroundOffsetY
        } else {
            state.heroOffsetY
        },
        minimal = edit.previewMode == BG_PREVIEW_REAL,
        editingBackdrop = edit.editTarget == BG_EDIT_TARGET_BACKDROP &&
            state.backdropPath != null,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        val panelCollapsed = edit.panelCollapsed && state.customBackgroundPath != null
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (dark) Color(0xE61C1A18) else Color(0xE6FFFFFF)
            ),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
        ) {
            if (panelCollapsed) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = edit::togglePreview,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = PikuColors.textPrimary
                        ),
                    ) {
                        Icon(
                            imageVector = if (edit.previewMode == BG_PREVIEW_REAL) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                if (edit.previewMode == BG_PREVIEW_REAL) R.string.background_preview_real else R.string.background_preview_hidden
                            ),
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = edit::collapsePanel) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.background_panel_expand),
                        )
                    }
                }
            } else {
                BackgroundEditPanel(
                    state = state,
                    bgPreviewMode = edit.previewMode,
                    bgEditTarget = edit.editTarget,
                    dark = dark,
                    onTogglePreview = edit::togglePreview,
                    onSelectTarget = edit::selectTarget,
                    onCollapse = edit::collapsePanel,
                    onPickImage = edit::pickImage,
                    onClearBackground = { viewModel.clearCustomBackground() },
                    onRestoreFollow = { viewModel.restoreFollowBackground() },
                    onDismiss = { edit.dismiss(state) },
                    onConfirm = { edit.confirm(state) },
                    onRevert = { edit.revert(state) },
                    onBackgroundDimChange = { viewModel.setBackgroundDim(it) },
                    onBackgroundBlurChange = { viewModel.setBackgroundBlur(it) },
                    onBackgroundScaleChange = { viewModel.setBackgroundScale(it) },
                    onHeroScaleChange = { viewModel.setHeroScale(it) },
                    onHeroFractionChange = { viewModel.setBackgroundHeroFraction(it) },
                    onSettingsFinished = { edit.onSettingsFinished(state) },
                )
            }
        }
    }
}

/**
 * 背景编辑底部面板：参数控制面板。
 * 从 HomeScreen 的巨大 Card 内容区抽取，避免主函数超过 800 行。
 */
@Composable
private fun BackgroundEditPanel(
    state: HomeUiState,
    bgPreviewMode: Int,
    bgEditTarget: Int,
    dark: Boolean,
    onTogglePreview: () -> Unit,
    onSelectTarget: (Int) -> Unit,
    onCollapse: () -> Unit,
    onPickImage: () -> Unit,
    onClearBackground: () -> Unit,
    onRestoreFollow: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onRevert: () -> Unit,
    onBackgroundDimChange: (Float) -> Unit,
    onBackgroundBlurChange: (Float) -> Unit,
    onBackgroundScaleChange: (Float) -> Unit,
    onHeroScaleChange: (Float) -> Unit,
    onHeroFractionChange: (Float) -> Unit,
    onSettingsFinished: () -> Unit,
) {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()

    Column(
        modifier = Modifier
            .padding(20.dp)
            .animateContentSize()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Wallpaper,
                contentDescription = null,
                tint = PikuColors.textPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.background_select_title),
                color = PikuColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            if (state.customBackgroundPath != null) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.background_panel_collapse),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (state.customBackgroundPath != null) {
                stringResource(R.string.background_drag_hint)
            } else {
                stringResource(R.string.background_select_hint)
            },
            color = PikuColors.textFaint,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(16.dp))

        if (state.customBackgroundPath != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    BG_EDIT_TARGET_HERO to R.string.background_edit_target_hero,
                    BG_EDIT_TARGET_BACKDROP to R.string.background_edit_target_backdrop,
                ).forEach { (target, labelRes) ->
                    val selected = bgEditTarget == target
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) {
                                    if (dark) Color(0xFF6C538C) else Color(0xFFE8DEF8)
                                } else {
                                    if (dark) Color(0xFF332F2B) else Color(0xFFF0EDE9)
                                }
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onSelectTarget(target) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            color = if (selected) {
                                if (dark) Color.White else Color(0xFF21005D)
                            } else {
                                if (dark) Color.White else Color.Black
                            },
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (bgEditTarget == BG_EDIT_TARGET_BACKDROP) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            if (state.backdropPath != null) R.string.background_backdrop_separated else R.string.background_follow_hint
                        ),
                        color = PikuColors.textFaint,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.backdropPath != null) {
                        TextButton(
                            onClick = onRestoreFollow,
                            colors = ButtonDefaults.textButtonColors(contentColor = AccentDark),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.background_restore_follow),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.background_dim_label),
                        color = PikuColors.textPrimary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${(state.backgroundDim * 100).roundToInt()}%",
                        color = PikuColors.textFaint,
                        fontSize = 12.sp,
                    )
                }
                Slider(
                    value = state.backgroundDim,
                    onValueChange = onBackgroundDimChange,
                    onValueChangeFinished = onSettingsFinished,
                    valueRange = 0f..SettingsRepository.BACKGROUND_DIM_MAX,
                )
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.background_blur_label),
                        color = PikuColors.textPrimary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${state.backgroundBlur.roundToInt()}dp",
                        color = PikuColors.textFaint,
                        fontSize = 12.sp,
                    )
                }
                Slider(
                    value = state.backgroundBlur,
                    onValueChange = onBackgroundBlurChange,
                    onValueChangeFinished = onSettingsFinished,
                    valueRange = SettingsRepository.BACKGROUND_BLUR_MIN..SettingsRepository.BACKGROUND_BLUR_MAX,
                )

                if (state.backdropPath != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.background_backdrop_scale_label),
                            color = PikuColors.textPrimary,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${String.format("%.2f", state.backgroundScale)}x",
                            color = PikuColors.textFaint,
                            fontSize = 12.sp,
                        )
                    }
                    Slider(
                        value = state.backgroundScale,
                        onValueChange = onBackgroundScaleChange,
                        onValueChangeFinished = onSettingsFinished,
                        valueRange = SettingsRepository.BACKGROUND_SCALE_MIN..SettingsRepository.BACKGROUND_SCALE_MAX,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.background_hero_scale_label),
                        color = PikuColors.textPrimary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${String.format("%.2f", state.heroScale)}x",
                        color = PikuColors.textFaint,
                        fontSize = 12.sp,
                    )
                }
                Slider(
                    value = heroScaleToSlider(state.heroScale),
                    onValueChange = { onHeroScaleChange(sliderToHeroScale(it)) },
                    onValueChangeFinished = onSettingsFinished,
                )
                if (state.heroScale < 1f) {
                    Text(
                        text = stringResource(R.string.background_frame_hint),
                        color = PikuColors.textFaint,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.background_hero_label),
                        color = PikuColors.textPrimary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        // 读数给实际清晰区占比：比例被 200~420dp 钳住时（短屏/高屏两端）
                        // 设置值不等于实际高度，显示钳后的值才不会和画面不一致
                        text = "${(heroZoneHeightDp(screenHeightDp, state.backgroundHeroFraction) /
                            screenHeightDp * 100).roundToInt()}%",
                        color = PikuColors.textFaint,
                        fontSize = 12.sp,
                    )
                }
                val heroRange = heroFractionRange(screenHeightDp)
                Slider(
                    value = state.backgroundHeroFraction.coerceIn(heroRange.start, heroRange.endInclusive),
                    onValueChange = onHeroFractionChange,
                    onValueChangeFinished = onSettingsFinished,
                    valueRange = heroRange,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        state.backgroundErrorRes?.let { res ->
            Text(
                text = stringResource(res),
                color = PikuColors.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPickImage,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (dark) Color(0xFF332F2B) else Color(0xFFF0EDE9),
                    contentColor = if (dark) Color.White else Color.Black
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        when {
                            state.customBackgroundPath == null -> R.string.background_pick_image
                            bgEditTarget == BG_EDIT_TARGET_BACKDROP ->
                                if (state.backdropPath != null) R.string.background_change_backdrop else R.string.background_pick_backdrop
                            else -> R.string.background_change_image
                        }
                    ),
                    fontSize = 13.sp
                )
            }

            if (state.customBackgroundPath != null) {
                Button(
                    onClick = onTogglePreview,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (bgPreviewMode != BG_PREVIEW_REAL) {
                            if (dark) Color(0xFF6C538C) else Color(0xFFE8DEF8)
                        } else {
                            if (dark) Color(0xFF332F2B) else Color(0xFFF0EDE9)
                        },
                        contentColor = if (bgPreviewMode != BG_PREVIEW_REAL) {
                            if (dark) Color.White else Color(0xFF21005D)
                        } else {
                            if (dark) Color.White else Color.Black
                        }
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (bgPreviewMode == BG_PREVIEW_REAL) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            if (bgPreviewMode == BG_PREVIEW_REAL) R.string.background_preview_real else R.string.background_preview_hidden
                        ),
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.HorizontalDivider(color = if (dark) Color(0xFF2C2825) else Color(0xFFEAE7E4))
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.customBackgroundPath != null) {
                TextButton(
                    onClick = onClearBackground,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = PikuColors.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.background_reset),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            TextButton(
                onClick = onRevert,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = PikuColors.textFaint
                )
            ) {
                Text(text = stringResource(R.string.search_cancel), fontSize = 13.sp)
            }

            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentDark,
                    contentColor = Color.White
                )
            ) {
                Text(text = stringResource(R.string.profile_edit_save), fontSize = 13.sp)
            }
        }
    }
}
