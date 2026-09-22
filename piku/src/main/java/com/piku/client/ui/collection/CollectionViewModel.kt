package com.piku.client.ui.collection

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.data.repository.CollectionEdit
import com.piku.client.data.repository.FavoriteRepository
import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.FavoriteFolder
import com.piku.client.domain.model.FolderSort
import com.piku.client.domain.model.ReadingProgress
import com.piku.client.domain.model.Work
import com.piku.client.ui.common.FeedbackChannel
import com.piku.client.ui.common.FeedbackText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** 删除收藏夹确认框：先算清会连带取消收藏多少作品，再让用户确认 */
data class FolderDeleteRequest(
    val folder: FavoriteFolder,
    /** 只属于该收藏夹、删除后会被连带取消收藏的作品数 */
    val exclusiveCount: Int,
)

/**
 * 收藏页当前在看什么。
 *
 * 「全部收藏」是一个**视图**而不是数据库里的收藏夹：它没有主键、不参与同步，
 * 所以不能塞进 selectedFolderId 之类的 Long 字段里冒充真实收藏夹，
 * 否则跨夹操作会往仓储里传一个不存在的 folderId。
 */
sealed interface CollectionView {
    /** 收藏夹列表（收藏页根视图） */
    data object Folders : CollectionView

    /** 全部收藏：跨收藏夹的全部作品 */
    data object All : CollectionView

    data class InFolder(val id: Long, val name: String) : CollectionView
}

/**
 * 一个视图里的一段列表。[label] 非空时该段前面渲染一个分组头（按作者排序时用），
 * 其余排序只有一段、不带头，UI 因此可以用同一套渲染逻辑。
 */
data class CollectionGroup(
    val key: String,
    val label: String?,
    val authorAvatarUrl: String?,
    val works: List<Work>,
)

data class CollectionUiState(
    val folders: List<FavoriteFolder> = emptyList(),
    /** 收藏作品总数（去重后的作品数，不是各收藏夹计数之和） */
    val totalWorks: Int = 0,
    val view: CollectionView = CollectionView.Folders,
    /** 当前显示的列表（已按检索词过滤；排序由 SQL 完成） */
    val works: List<Work> = emptyList(),
    /** 过滤前的作品数：检索时需要把「匹配数 / 总数」一起显示出来 */
    val worksTotal: Int = 0,
    /** 当前视图的列表是否还在等数据源：用来区分"这个夹是空的"和"还没读出来" */
    val listLoading: Boolean = false,
    /** 渲染用的分段列表；按作者排序时一段一个作者 */
    val groups: List<CollectionGroup> = emptyList(),
    val sort: FolderSort = FolderSort.ADDED,
    val query: String = "",
    val loaded: Boolean = false,
    /** 多选模式：仅在收藏夹详情内有效 */
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    /** 最近一次可撤销操作的说明，null 表示没有可撤销的操作 */
    val undoLabel: FeedbackText? = null,
    /** 尚未回退的操作数（撤销按操作倒序一条条生效） */
    val undoCount: Int = 0,
    val deleteRequest: FolderDeleteRequest? = null,
    /** 全量阅读进度快照：给收藏夹卡片画进度条 */
    val progress: Map<Long, ReadingProgress> = emptyMap(),
    /** 当前操作作品的已有归属：打开「添加到…」面板时用它标出已添加的收藏夹 */
    val actionWorkFolderIds: Set<Long> = emptySet(),
    /** 从放大镜进入「全部收藏」时置位，工具栏聚焦一次检索框后立刻消费掉 */
    val focusSearch: Boolean = false,
) {
    val searching: Boolean get() = query.isNotBlank()

    /** 只看当前显示的这批：全选只作用于筛选后的结果，隐藏的投稿不会被选中 */
    val allSelected: Boolean get() = works.isNotEmpty() && works.all { it.id in selectedIds }

    /** 当前所在收藏夹的 id；「全部收藏」与夹列表返回 null */
    val currentFolderId: Long? get() = (view as? CollectionView.InFolder)?.id

    /** 是否在「全部收藏」视图：决定「移出」是移出收藏夹还是取消收藏 */
    val allScope: Boolean get() = view is CollectionView.All
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    /** 一次性反馈（仅用于操作失败；移出/移动的结果由撤销条承载，不再弹 Snackbar） */
    val feedback = FeedbackChannel()

    /** 桌面装饰总开关：关闭时顶栏的装饰管理入口隐藏 */
    val decorationEnabled = settingsRepository.decorationEnabled

    /** 撤销栈：与浏览记录同款语义，连续操作时可以一直点撤销，按倒序一条条回退 */
    private val undoStack = ArrayDeque<UndoRecord>()

    /** 归属变更与撤销串行执行：保证撤销栈的顺序与用户操作顺序一致 */
    private val editLock = Mutex()

    private data class UndoRecord(val edit: CollectionEdit, val label: FeedbackText)

    /** 数据库里最新的原始列表（未过滤）。过滤与分组都从它推导，避免把两份列表都塞进 UI 状态 */
    private var rawWorks: List<Work> = emptyList()

    /** 列表数据源的键：只含决定 SQL 的三件事，收藏夹改名不会让它变化 */
    private data class ListKey(
        val folderId: Long?,
        val all: Boolean,
        val sort: FolderSort,
    )

    init {
        viewModelScope.launch {
            favoriteRepository.observeFolders().collect { folders ->
                _uiState.update { it.copy(folders = folders, loaded = true) }
            }
        }
        viewModelScope.launch {
            favoriteRepository.observeFavoriteCount().collect { count ->
                _uiState.update { it.copy(totalWorks = count) }
            }
        }
        viewModelScope.launch {
            // 键用原始值而不是 view 对象：InFolder 带名字，改名会被误判成换视图而重新订阅
            _uiState.map { ListKey(it.currentFolderId, it.allScope, it.sort) }
                .distinctUntilChanged()
                .flatMapLatest { key ->
                    when {
                        key.all -> favoriteRepository.observeAllWorks(key.sort)
                        key.folderId == null -> flowOf(emptyList())
                        else -> favoriteRepository.observeFolderWorks(key.folderId, key.sort)
                    }
                }
                .collect { works ->
                    rawWorks = works
                    publish()
                }
        }
        viewModelScope.launch {
            settingsRepository.readingProgress.collect { progress ->
                _uiState.update { it.copy(progress = progress) }
            }
        }
        viewModelScope.launch {
            settingsRepository.folderSort.collect { saved ->
                _uiState.update { if (it.sort == saved) it else it.copy(sort = saved) }
            }
        }
    }

    /** 用当前的检索词与排序，把原始列表推导成 UI 直接渲染的形态 */
    private fun publish() {
        val state = _uiState.value
        val keyword = state.query.trim()
        val visible = if (keyword.isEmpty()) {
            rawWorks
        } else {
            // 内存里过滤而不是走 SQL：中日文标题没有词边界，FTS 的分词器切不出子串，
            // LIKE '%kw%' 又用不上索引；列表本来就在内存里，直接 contains 最稳也最快
            rawWorks.filter {
                it.title.contains(keyword, ignoreCase = true) ||
                    it.authorName.contains(keyword, ignoreCase = true)
            }
        }
        val visibleIds = visible.mapTo(mutableSetOf()) { it.id }
        _uiState.update {
            it.copy(
                works = visible,
                worksTotal = rawWorks.size,
                listLoading = false,
                groups = buildGroups(visible, it.sort),
                // 列表一变就把看不见的选中项丢掉：否则「已选 N 个」会虚高，
                // 而批量动作只作用于当前可见的这批
                selectedIds = it.selectedIds.intersect(visibleIds),
            )
        }
    }

    private fun buildGroups(works: List<Work>, sort: FolderSort): List<CollectionGroup> {
        if (works.isEmpty()) return emptyList()
        if (sort != FolderSort.AUTHOR) {
            return listOf(
                CollectionGroup(
                    key = "all",
                    label = null,
                    authorAvatarUrl = null,
                    works = works,
                ),
            )
        }
        // 同一作者的作品在 SQL 里已排在一起，groupBy 保持遇到顺序即可直接用
        return works
            .groupBy { it.authorId to it.authorName }
            .map { (author, groupWorks) ->
                CollectionGroup(
                    key = "author-${author.first}",
                    label = author.second,
                    authorAvatarUrl = groupWorks.firstOrNull()?.authorAvatarUrl,
                    works = groupWorks,
                )
            }
    }

    fun setQuery(query: String) {
        if (_uiState.value.query == query) return
        _uiState.update { it.copy(query = query) }
        publish()
    }

    fun clearQuery() = setQuery("")

    fun setSort(sort: FolderSort) {
        if (_uiState.value.sort == sort) return
        _uiState.update { it.copy(sort = sort) }
        settingsRepository.setFolderSort(sort)
        // 排序由 SQL 决定，新的列表到了之后会再 publish 一次；这里先按新排序重排分组，
        // 免得分组头在等待数据库返回的间隙里还停留在旧顺序
        publish()
    }

    fun selectFolder(folder: FavoriteFolder) {
        switchView(CollectionView.InFolder(folder.id, folder.name))
    }

    /** 进「全部收藏」视图。[focusSearch] 为真表示从放大镜进来，检索框应自动获得焦点 */
    fun openAll(focusSearch: Boolean = false) {
        switchView(CollectionView.All, focusSearch = focusSearch)
    }

    fun backToFolders() {
        switchView(CollectionView.Folders)
    }

    /** 检索框拿到焦点后调用，避免每次重组都重新抢焦点 */
    fun consumeSearchFocus() {
        if (_uiState.value.focusSearch) {
            _uiState.update { it.copy(focusSearch = false) }
        }
    }

    private fun switchView(view: CollectionView, focusSearch: Boolean = false) {
        _uiState.update {
            it.copy(
                view = view,
                works = emptyList(),
                groups = emptyList(),
                worksTotal = 0,
                listLoading = true,
                query = "",
                selectionMode = false,
                selectedIds = emptySet(),
                actionWorkFolderIds = emptySet(),
                focusSearch = focusSearch,
            )
        }
        rawWorks = emptyList()
    }

    // ---- 收藏夹增删改 ----

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            if (favoriteRepository.createFolder(trimmed) == null) {
                feedback.show(R.string.collection_folder_name_taken)
            }
        }
    }

    fun renameFolder(folderId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            // 重名时数据层不写入（同步按名字认收藏夹），标题自然也不该跟着改
            if (!favoriteRepository.renameFolder(folderId, trimmed)) {
                feedback.show(R.string.collection_folder_name_taken)
                return@launch
            }
            // 若正在查看该收藏夹，同步更新标题
            _uiState.update { state ->
                val current = state.view
                if (current is CollectionView.InFolder && current.id == folderId) {
                    state.copy(view = current.copy(name = trimmed))
                } else {
                    state
                }
            }
        }
    }

    /** 打开删除确认框：先查清有多少作品会因为失去唯一归属而被连带取消收藏 */
    fun requestDeleteFolder(folder: FavoriteFolder) {
        viewModelScope.launch {
            val exclusive = runCatching { favoriteRepository.countExclusiveWorks(folder.id) }
                .getOrDefault(0)
            _uiState.update { it.copy(deleteRequest = FolderDeleteRequest(folder, exclusive)) }
        }
    }

    fun dismissDeleteFolder() {
        _uiState.update { it.copy(deleteRequest = null) }
    }

    fun confirmDeleteFolder() {
        val request = _uiState.value.deleteRequest ?: return
        _uiState.update { it.copy(deleteRequest = null) }
        deleteFolder(request.folder.id)
    }

    fun deleteFolder(folderId: Long) {
        // 该收藏夹已不存在，涉及它的待撤销操作要一并丢掉，否则撤销会写入悬空归属
        dropUndoRecordsFor(folderId)
        viewModelScope.launch {
            favoriteRepository.deleteFolder(folderId)
            // 删除的是当前查看的收藏夹时返回列表
            if (_uiState.value.currentFolderId == folderId) {
                backToFolders()
            }
        }
    }

    // ---- 多选 ----

    /**
     * 进入多选模式。[workId] 为长按发起时指到的那张卡：从卡片进多选说明用户想操作它，
     * 先带上；从顶栏进入则不带任何预选，避免"我还没选它就自己选中了"。
     */
    fun enterSelection(workId: Long? = null) {
        _uiState.update {
            it.copy(
                selectionMode = true,
                selectedIds = workId?.let { id -> setOf(id) } ?: emptySet(),
            )
        }
    }

    fun exitSelection() {
        _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
    }

    fun toggleSelection(workId: Long) {
        _uiState.update { state ->
            val next = if (workId in state.selectedIds) {
                state.selectedIds - workId
            } else {
                state.selectedIds + workId
            }
            state.copy(selectedIds = next)
        }
    }

    fun toggleSelectAll() {
        _uiState.update { state ->
            val all = state.works.mapTo(mutableSetOf()) { it.id }
            state.copy(
                selectedIds = if (state.allSelected) emptySet() else all,
                selectionMode = true,
            )
        }
    }

    /** 打开「添加到…」面板前，读一次选中作品的已有归属（只选了一件才标得出「已添加」） */
    fun loadSelectedFolderIds() {
        val state = _uiState.value
        val work = state.works.singleOrNull { it.id in state.selectedIds } ?: return
        viewModelScope.launch {
            val ids = favoriteRepository.observeWorkFolderIds(work.id).first()
            _uiState.update { it.copy(actionWorkFolderIds = ids) }
        }
    }

    // ---- 归属变更（全部可撤销） ----

    /** 移出收藏夹：多选批量 */
    fun removeSelected() {
        val state = _uiState.value
        removeWorks(state.works.filter { it.id in state.selectedIds })
    }

    /**
     * 「移出」在不同视图里是两件事：夹内是移出这个收藏夹，
     * 「全部收藏」里没有"当前收藏夹"可移出，只能理解成取消收藏。
     */
    private fun removeWorks(works: List<Work>) {
        if (works.isEmpty()) return
        val ids = works.map { it.id.toString() }
        when (val view = _uiState.value.view) {
            is CollectionView.InFolder -> applyEdit(
                label = FeedbackText(R.string.collection_removed_multiple, listOf(works.size)),
            ) { favoriteRepository.removeWorksFromFolder(ids, view.id) }

            CollectionView.All -> applyEdit(
                label = FeedbackText(
                    R.string.collection_unfavorited_multiple,
                    listOf(works.size),
                ),
            ) { favoriteRepository.unfavoriteWorks(ids) }

            CollectionView.Folders -> Unit
        }
    }

    /** 添加到其他收藏夹：保留当前收藏夹的归属 */
    fun addSelectedTo(target: FavoriteFolder) {
        val state = _uiState.value
        val works = state.works.filter { it.id in state.selectedIds }
        if (works.isEmpty()) return
        applyEdit(
            label = FeedbackText(
                R.string.collection_added_multiple,
                listOf(works.size, target.name),
            ),
            emptyLabel = FeedbackText(R.string.collection_nothing_added),
        ) { favoriteRepository.addWorksToFolder(works, target.id) }
    }

    /** 移动到其他收藏夹：原收藏夹不再保留 */
    fun moveSelectedTo(target: FavoriteFolder) {
        val state = _uiState.value
        moveWorks(state.works.filter { it.id in state.selectedIds }, target)
    }

    /**
     * 「移动」同样分两种：夹内是搬到目标夹，
     * 「全部收藏」里是把作品从它所在的每个夹收拢到目标夹（跨夹整理到一处）。
     */
    private fun moveWorks(works: List<Work>, target: FavoriteFolder) {
        if (works.isEmpty()) return
        val label = FeedbackText(
            R.string.collection_moved_multiple,
            listOf(works.size, target.name),
        )
        when (val view = _uiState.value.view) {
            is CollectionView.InFolder -> applyEdit(label) {
                favoriteRepository.moveWorksToFolder(works, view.id, target.id)
            }

            CollectionView.All -> applyEdit(label) {
                favoriteRepository.moveWorksFromAllFolders(works, target.id)
            }

            CollectionView.Folders -> Unit
        }
    }

    fun undo() {
        val record = undoStack.removeLastOrNull() ?: return
        syncUndoState()
        viewModelScope.launch {
            editLock.withLock {
                try {
                    favoriteRepository.undoCollectionEdit(record.edit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "undo failed", e)
                    feedback.show(R.string.collection_action_failed)
                }
            }
        }
    }

    /** 关掉撤销条：剩下的不再可回退 */
    fun dismissUndo() {
        undoStack.clear()
        syncUndoState()
    }

    private fun applyEdit(
        label: FeedbackText,
        emptyLabel: FeedbackText? = null,
        block: suspend () -> CollectionEdit,
    ) {
        viewModelScope.launch {
            // 串行执行：撤销栈要按用户的操作顺序记录，否则两次操作重叠完成时栈序是完成序
            editLock.withLock {
                val edit = try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "collection edit failed", e)
                    feedback.show(R.string.collection_action_failed)
                    return@withLock
                }
                if (edit.isEmpty) {
                    // 幂等操作（全部已在目标收藏夹）不是错误，但要让用户知道为什么没反应
                    emptyLabel?.let { feedback.show(it.res, *it.args.toTypedArray()) }
                    return@withLock
                }
                if (undoStack.size >= MAX_UNDO) undoStack.removeFirst()
                undoStack.addLast(UndoRecord(edit, label))
                _uiState.update {
                    it.copy(
                        selectionMode = false,
                        selectedIds = emptySet(),
                        actionWorkFolderIds = emptySet(),
                        undoLabel = label,
                        undoCount = undoStack.size,
                    )
                }
            }
        }
    }

    private fun dropUndoRecordsFor(folderId: Long) {
        val removed = undoStack.removeAll { record ->
            record.edit.restored.any { it.folderId == folderId } ||
                record.edit.removed.any { it.folderId == folderId }
        }
        if (removed) syncUndoState()
    }

    private fun syncUndoState() {
        _uiState.update {
            it.copy(undoLabel = undoStack.lastOrNull()?.label, undoCount = undoStack.size)
        }
    }

    private companion object {
        const val TAG = "CollectionVM"

        /** 撤销栈上限：与浏览记录一致，只保留最近的操作 */
        const val MAX_UNDO = 20
    }
}
