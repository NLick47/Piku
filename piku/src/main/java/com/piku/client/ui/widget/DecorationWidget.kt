package com.piku.client.ui.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.piku.client.MainActivity
import com.piku.client.R
import com.piku.client.data.local.AppDatabase
import com.piku.client.data.local.DecorationItem
import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.WorkDetail
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 多信号安全判据——「设为桌面装饰」入库与 widget 渲染兜底共用。
 * 任一命中即认为不适合出现在公开桌面：R-18 标记只是其中一类，
 * warning/密码锁/成人锁同样意味着内容未经全年龄确认；
 * 纯文字（小说）作品没有可作画的图，也直接排除。
 */
fun isWidgetSafe(detail: WorkDetail): Boolean =
    !detail.r18 &&
        !detail.warning &&
        !detail.adultLocked &&
        !detail.passwordProtected &&
        detail.imageUrls.isNotEmpty()

/**
 * 桌面装饰小组件：把白名单作品渲染成桌面上的「画框」。
 *
 * 数据流刻意封闭：白名单表 → 本地私有目录图片 → widget 渲染。
 * widget 不联网、不读收藏/历史/feed；白名单为空时显示极简空态，
 * 绝不退化成自动抽图——空卡片比意外露出成人内容安全得多。
 *
 * 布局随尺寸自适应（[SizeMode.Responsive]）：
 * 窄（2×2 / 4×2）= 单幅轮播画框，宽（拖宽后）= 两列画廊网格。
 */
object DecorationWidget : GlanceAppWidget() {

    // 断点集合：系统按 widget 实际尺寸就近取一个，组合内再用 LocalSize 判断宽窄。
    // 具体渲染按件数再降级（少于 4 张任何尺寸都是画框）。
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(width = 110.dp, height = 110.dp),
            DpSize(width = 240.dp, height = 180.dp),
            DpSize(width = 300.dp, height = 240.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        // Glance 组合环境不提供 Compose 的 LocalContext：
        // 字符串与亮暗判定必须在 provideGlance 主体（有 context 参数）算好再传入
        val dark = isDarkMode(context)
        if (!entryPoint.settingsRepository().decorationEnabled.value) {
            val offTitle = context.getString(R.string.decoration_widget_off_title)
            val offHint = context.getString(R.string.decoration_widget_off_hint)
            provideContent {
                DisabledFrame(dark = dark, title = offTitle, hint = offHint)
            }
            return
        }
        val items: List<DecorationItem> = entryPoint.database().decorationDao().getAllOnce()
        val dir = File(context.filesDir, "decoration")
        val emptyHint = context.getString(R.string.decoration_widget_empty_hint)
        provideContent {
            DecorationContent(
                items = items.map { item ->
                    DecorationItemView(
                        path = File(dir, item.fileName).absolutePath,
                        workId = item.workId,
                        authorId = item.authorId,
                    )
                },
                rotationIndex = DecorationState.readRotationIndex(context),
                dark = dark,
                emptyHint = emptyHint,
            )
        }
    }
}

/** widget 渲染用的白名单条目快照：本地图片路径 + 深链所需 id */
data class DecorationItemView(
    val path: String,
    val workId: Long,
    val authorId: Long,
)

/** 轮播游标的持久化：SharedPreferences 足够（进程无关、无需事务性） */
object DecorationState {
    private const val PREFS = "decoration_widget"
    private const val KEY_ROTATION_INDEX = "rotation_index"

    fun readRotationIndex(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_ROTATION_INDEX, 0)

    fun writeRotationIndex(context: Context, index: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ROTATION_INDEX, index)
            .apply()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun database(): AppDatabase

    /** 总开关状态：widget 关闭态与轮播调度都读它 */
    fun settingsRepository(): SettingsRepository
}

class DecorationWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = DecorationWidget

    companion object {
        /** 白名单变化后由 Repository 调用：立即重渲染，并同步轮播调度（单张不调度） */
        fun requestUpdate(context: Context) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                DecorationWidget.updateAll(context)
                // 已经在协程里，直接 await 挂起版，少一层游离协程
                DecorationRotationWorker.syncSchedule(context)
            }
        }
    }
}

/**
 * 点击 widget 图片 → 显式拉起 MainActivity 深链进作品详情（不能依赖系统解析 https，否则会被丢给浏览器）
 */
class OpenWorkAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val authorId = parameters[authorIdKey] ?: 0L
        val workId = parameters[workIdKey] ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            // MainActivity 的 launchMode=singleTask + onNewIntent 会把 dataString
            // 交给 AppNavHost 的深链解析，直达作品详情而非浏览器
            data = Uri.parse("https://poipiku.com/$authorId/$workId.html")
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        val workIdKey = ActionParameters.Key<Long>("workId")
        val authorIdKey = ActionParameters.Key<Long>("authorId")
    }
}

/** 点击空态 → 直接进 App 首页 */
private val openApp = actionStartActivity<MainActivity>()

private val FrameColor = ColorProvider(Color(0xFFFFFFFF))
private val EmptyTextColor = ColorProvider(Color(0xFF6E6A75))
private val FrameColorDark = ColorProvider(Color(0xFF1C1B20))
private val EmptyTextColorDark = ColorProvider(Color(0xFFA8A4B0))

/** Glance 的 ColorProvider 不带 day/night 构造，亮暗色按系统夜间模式现取 */
private fun isDarkMode(context: Context): Boolean =
    (context.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

@Composable
private fun DecorationContent(
    items: List<DecorationItemView>,
    rotationIndex: Int,
    dark: Boolean,
    emptyHint: String,
) {
    // Responsive 断点选中的实际尺寸：宽度过画廊门槛就平铺（件数够的前提下）
    val wide = LocalSize.current.width >= GalleryMinWidth
    val frameColor = if (dark) FrameColorDark else FrameColor
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(3.dp)
            .cornerRadius(18.dp)
            .background(frameColor),
    ) {
        when {
            items.isEmpty() -> EmptyFrame(dark, emptyHint)
            // 窄尺寸 = 画框：只显示轮播游标指向的那一幅
            !wide || items.size < GALLERY_MIN_ITEMS -> {
                val index = rotationIndex % items.size
                FrameImage(
                    path = items[index].path,
                    workId = items[index].workId,
                    authorId = items[index].authorId,
                )
            }
            // 宽尺寸 = 画廊：全部平铺，各自深链
            else -> GalleryGrid(items)
        }
    }
}

@Composable
private fun GalleryGrid(items: List<DecorationItemView>) {
    LazyVerticalGrid(
        gridCells = GridCells.Fixed(2),
        modifier = GlanceModifier.fillMaxSize(),
    ) {
        items(items.size) { index ->
            val item = items[index]
            Box(
                modifier = GlanceModifier
                    .padding(2.dp)
                    .cornerRadius(10.dp)
                    .clickable(
                        actionRunCallback<OpenWorkAction>(
                            parameters = actionParametersOf(
                                OpenWorkAction.workIdKey to item.workId,
                                OpenWorkAction.authorIdKey to item.authorId,
                            ),
                        ),
                    ),
            ) {
                val bitmap = decodeSampled(item.path)
                if (bitmap != null) {
                    Image(
                        provider = ImageProvider(bitmap),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier.fillMaxSize().cornerRadius(10.dp),
                    )
                } else {
                    Box(modifier = GlanceModifier.fillMaxSize()) {}
                }
            }
        }
    }
}

@Composable
private fun FrameImage(path: String, workId: Long, authorId: Long) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(
                actionRunCallback<OpenWorkAction>(
                    parameters = actionParametersOf(
                        OpenWorkAction.workIdKey to workId,
                        OpenWorkAction.authorIdKey to authorId,
                    ),
                ),
            ),
    ) {
        val bitmap = decodeSampled(path)
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(12.dp),
            )
        } else {
            // 图缺失：整框留白（点按仍可进 App），轮播 worker 下个周期会换到下一张
            Box(modifier = GlanceModifier.fillMaxSize()) {}
        }
    }
}

/** 极简空态：白名单为空时只给指引文案，点按进 App */
@Composable
private fun EmptyFrame(dark: Boolean, emptyHint: String) {
    val textColor = if (dark) EmptyTextColorDark else EmptyTextColor
    Column(
        modifier = GlanceModifier.fillMaxSize().clickable(openApp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Piku",
            style = TextStyle(color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(GlanceModifier.size(4.dp))
        Text(
            text = emptyHint,
            style = TextStyle(color = textColor, fontSize = 10.sp),
        )
    }
}

/**
 * 关闭态占位：用户在 App 里关掉了桌面装饰功能。
 * App 无法自行移除桌面上的 widget 实例（Android 无此 API），
 * 所以这里只呈现中性文案并提示用户长按卡片移除，不渲染任何作品。
 */
@Composable
private fun DisabledFrame(dark: Boolean, title: String, hint: String) {
    val textColor = if (dark) EmptyTextColorDark else EmptyTextColor
    Column(
        modifier = GlanceModifier.fillMaxSize().clickable(openApp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TextStyle(color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(GlanceModifier.size(4.dp))
        Text(
            text = hint,
            style = TextStyle(color = textColor, fontSize = 9.sp),
        )
    }
}

/**
 * 白名单图是原图尺寸，widget 位图必须先降采样，RemoteViews 传输
 * 过大 bitmap 会崩；目标按 4x4 小部件的最大显示尺寸约 600px 取样。
 */
private fun decodeSampled(path: String): Bitmap? {
    val file = File(path)
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 600 || bounds.outHeight / (sample * 2) >= 600) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}

/** 画廊模式的门槛（件数）：少于该数时任何尺寸都走单幅画框 */
internal const val GALLERY_MIN_ITEMS = 4

/** 画廊模式的宽度门槛：widget 实际宽度达到该值才平铺 */
internal val GalleryMinWidth = 250.dp
