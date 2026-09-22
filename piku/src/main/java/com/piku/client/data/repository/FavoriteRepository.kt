package com.piku.client.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.piku.client.data.local.AppDatabase
import com.piku.client.data.local.FavoriteDao
import com.piku.client.data.local.FavoriteEntity
import com.piku.client.data.local.FavoriteFolderDao
import com.piku.client.data.local.FavoriteFolderEntity
import com.piku.client.data.local.FavoriteMembershipEntity
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.local.toFavoriteEntity
import com.piku.client.data.local.toWork
import com.piku.client.domain.model.FavoriteFolder
import com.piku.client.domain.model.FolderSort
import com.piku.client.domain.model.Work
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 一次收藏归属变更的快照，供「撤销」写回。
 *
 * [restored] 是被本次操作移除的归属，[removed] 是本次操作新增的归属，
 * [favoriteBackups] 是作品行快照：移出收藏夹时若作品不再属于任何收藏夹，
 * `favorites` 行会被删掉，撤销时必须连它一起写回。
 */
data class CollectionEdit(
    val restored: List<FavoriteMembershipEntity> = emptyList(),
    val removed: List<FavoriteMembershipEntity> = emptyList(),
    val favoriteBackups: List<FavoriteEntity> = emptyList(),
) {
    val isEmpty: Boolean get() = restored.isEmpty() && removed.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FavoriteRepository @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val favoriteFolderDao: FavoriteFolderDao,
    private val settingsRepository: SettingsRepository,
    private val webDavSyncRepository: WebDavSyncRepository,
    /** 批量归属变更是多条语句，必须包在一个事务里，见 [addWorksToFolder] 等 */
    private val database: AppDatabase,
) {
    // SupervisorJob：单个同步失败不会拖垮后续；与 app 进程同生命周期。
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 默认收藏夹「查 → 建」的串行锁，见 [ensureDefaultFolder] */
    private val defaultFolderLock = Mutex()

    // 自动同步触发源：用 SharedFlow 而不是直接 launch，可以让连续操作被 debounce + conflate 合并。
    private val autoSyncTrigger = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        scope.launch {
            autoSyncTrigger
                .onEach {
                    Log.d(TAG, "autoSync: trigger received, debouncing ${AUTO_SYNC_DEBOUNCE_MS}ms")
                    delay(AUTO_SYNC_DEBOUNCE_MS)
                }
                .collect {
                    Log.d(TAG, "autoSync: debounce fired, calling sync")
                    try {
                        val result = webDavSyncRepository.sync()
                        Log.d(TAG, "autoSync: result state=${result.state} error=${result.error}")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "autoSync failed", e)
                    }
                }
        }
    }

    /**
     * 收藏 id 集合。「收藏」状态被详情、历史、标签、搜索、首屏等多个界面同时订阅，
     * shareIn 让一次查询 + 一次 Set 构建喂给所有订阅方；无人订阅一段时间后上游停掉，
     * 下次订阅由 Room 重新查一遍，不会长期留着脏值。
     */
    fun observeFavoriteIds(): Flow<Set<Long>> =
        favoriteFolderDao.observeAllFavoriteIds()
            .map { ids -> ids.mapNotNull { it.toLongOrNull() }.toSet() }
            .shareIn(scope, SharingStarted.WhileSubscribed(FAVORITE_IDS_KEEP_ALIVE_MS), replay = 1)

    fun observeFavorites(): Flow<List<Work>> =
        favoriteDao.observeAll().map { list -> list.map { it.toWork() } }

    fun observeFolders(): Flow<List<FavoriteFolder>> = flow {
        ensureDefaultFolder()
        emitAll(
            combine(
                favoriteFolderDao.observeFolders(),
                favoriteFolderDao.observeFolderCounts(),
                favoriteFolderDao.observeFolderPreviews(),
            ) { folders, counts, previews ->
                val countByFolder = counts.associate { it.folderId to it.count }
                val previewsByFolder = previews
                    .groupBy { it.folderId }
                    .mapValues { (_, list) -> list.take(3).map { it.thumbnailUrl } }
                folders.map { folder ->
                    FavoriteFolder(
                        id = folder.id,
                        name = folder.name,
                        workCount = countByFolder[folder.id] ?: 0,
                        previewUrls = previewsByFolder[folder.id] ?: emptyList(),
                        isDefault = folder.isDefault,
                    )
                }
            },
        )
    }

    /** 收藏夹内的作品列表；排序在 SQL 里做，标题/作者用 `COLLATE NOCASE` 忽略大小写。 */
    fun observeFolderWorks(
        folderId: Long,
        sort: FolderSort = FolderSort.ADDED,
    ): Flow<List<Work>> {
        val source = when (sort) {
            FolderSort.ADDED -> favoriteFolderDao.observeWorksInFolder(folderId)
            FolderSort.TITLE -> favoriteFolderDao.observeWorksInFolderByTitle(folderId)
            FolderSort.AUTHOR -> favoriteFolderDao.observeWorksInFolderByAuthor(folderId)
        }
        return source.map { list -> list.map { it.toWork() } }
    }

    /**
     * 「全部收藏」视图：跨收藏夹的全部作品。
     * observeAll() 本身就是按 addedAt 倒序，正好是「加入时间」这一档，不用再写一条 SQL。
     */
    fun observeAllWorks(sort: FolderSort = FolderSort.ADDED): Flow<List<Work>> {
        val source = when (sort) {
            FolderSort.ADDED -> favoriteDao.observeAll()
            FolderSort.TITLE -> favoriteDao.observeAllByTitle()
            FolderSort.AUTHOR -> favoriteDao.observeAllByAuthor()
        }
        return source.map { list -> list.map { it.toWork() } }
    }

    fun observeWorkFolderIds(workId: Long): Flow<Set<Long>> =
        favoriteFolderDao.observeFolderIdsForWork(workId.toString()).map { it.toSet() }

    suspend fun toggleFavorite(work: Work): Boolean {
        val workId = work.id.toString()
        val defaultFolderId = ensureDefaultFolder()
        // 判定与写入必须在同一个事务里：否则两次快速点击会各自读到旧状态，双双走同一条分支
        val added = database.withTransaction {
            if (defaultFolderId in favoriteFolderDao.folderIdsForWork(workId)) {
                removeFromFolder(workId, defaultFolderId)
                false
            } else {
                addToFolder(work, defaultFolderId)
                true
            }
        }
        triggerAutoSync()
        return added
    }

    suspend fun toggleFolder(work: Work, folderId: Long) {
        val workId = work.id.toString()
        database.withTransaction {
            if (folderId in favoriteFolderDao.folderIdsForWork(workId)) {
                removeFromFolder(workId, folderId)
            } else {
                addToFolder(work, folderId)
            }
        }
        triggerAutoSync()
    }

    /**
     * 新建收藏夹。[addCurrentWork] 一并收藏到位。
     *
     * 重名返回 null：收藏夹在同步里是按名字匹配的（[WebDavSyncRepository]），
     * 两个同名的夹下次同步会被云端并成一个，所以从一开始就不允许重名。
     */
    suspend fun createFolder(name: String, addCurrentWork: Work? = null): Long? {
        val folderName = name.trim().ifBlank { UNNAMED_FOLDER }
        val folderId = database.withTransaction {
            if (favoriteFolderDao.folderByName(folderName) != null) return@withTransaction null
            val id = favoriteFolderDao.insertFolder(
                FavoriteFolderEntity(
                    name = folderName,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            if (addCurrentWork != null) {
                addToFolder(addCurrentWork, id)
            }
            id
        } ?: return null
        triggerAutoSync()
        return folderId
    }

    /** 重命名收藏夹；与另一个收藏夹重名时返回 false 且不写入，理由同 [createFolder] */
    suspend fun renameFolder(folderId: Long, name: String): Boolean {
        val folderName = name.trim().ifBlank { UNNAMED_FOLDER }
        val renamed = database.withTransaction {
            val existing = favoriteFolderDao.folderByName(folderName)
            if (existing != null && existing.id != folderId) return@withTransaction false
            favoriteFolderDao.renameFolder(folderId, folderName)
            true
        }
        if (renamed) triggerAutoSync()
        return renamed
    }

    suspend fun deleteFolder(folderId: Long): Boolean {
        val defaultFolderId = favoriteFolderDao.defaultFolderId() ?: return true
        if (folderId == defaultFolderId) return false
        database.withTransaction {
            favoriteFolderDao.deleteFolder(folderId)
            favoriteFolderDao.deleteOrphanedFavorites()
        }
        triggerAutoSync()
        return true
    }

    private suspend fun addToFolder(work: Work, folderId: Long) {
        val workId = work.id.toString()
        favoriteDao.upsert(work.toFavoriteEntity())
        favoriteFolderDao.upsertMembership(
            FavoriteMembershipEntity(
                folderId = folderId,
                workId = workId,
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun removeFromFolder(workId: String, folderId: Long) {
        favoriteFolderDao.deleteMembership(folderId, workId)
        val remaining = favoriteFolderDao.folderIdsForWork(workId)
        if (remaining.isEmpty()) {
            favoriteDao.delete(workId)
        }
    }

    /**
     * 把作品添加到收藏夹，**保留**原有归属（与 [moveWorksToFolder] 的区别）。
     * 已在该收藏夹内的作品直接跳过，不刷新 addedAt：添加是幂等的。
     *
     * 整个操作落在一个事务里：中途被杀不会留下"加了一半"的归属，
     * 大批量也只提交一次（否则每件一次 upsert 就是一次独立事务）。
     */
    suspend fun addWorksToFolder(works: List<Work>, folderId: Long): CollectionEdit {
        if (works.isEmpty()) return CollectionEdit()
        val edit = database.withTransaction {
            val workIds = works.map { it.id.toString() }
            val alreadyInFolder = membershipsIn(folderId, workIds)
                .map { it.workId }
                .toSet()
            val targets = works.filterNot { it.id.toString() in alreadyInFolder }
            if (targets.isEmpty()) return@withTransaction CollectionEdit()

            // 作品已在别的收藏夹里时保留原 addedAt（收藏时间），只有首次收藏才写新时间
            val existingFavorites = favoritesByIds(workIds).associateBy { it.workId }
            val now = System.currentTimeMillis()
            val added = mutableListOf<FavoriteMembershipEntity>()
            for ((index, work) in targets.withIndex()) {
                val workId = work.id.toString()
                favoriteDao.upsert(existingFavorites[workId] ?: work.toFavoriteEntity(now + index))
                // 同一批内 addedAt 递增，保证批量添加后夹内顺序稳定（列表按 addedAt 倒序）
                val membership = FavoriteMembershipEntity(
                    folderId = folderId,
                    workId = workId,
                    addedAt = now + index,
                )
                favoriteFolderDao.upsertMembership(membership)
                added += membership
            }
            CollectionEdit(removed = added)
        }
        if (!edit.isEmpty) triggerAutoSync()
        return edit
    }

    /**
     * 批量移动：加入目标收藏夹并从源收藏夹移出。
     * 已在目标收藏夹内的作品只做移出，撤销时不会被误删（不计入快照的 removed）。
     */
    suspend fun moveWorksToFolder(
        works: List<Work>,
        fromFolderId: Long,
        toFolderId: Long,
    ): CollectionEdit {
        if (works.isEmpty() || fromFolderId == toFolderId) return CollectionEdit()
        val edit = database.withTransaction {
            val workIds = works.map { it.id.toString() }
            val fromMemberships = membershipsIn(fromFolderId, workIds)
            if (fromMemberships.isEmpty()) return@withTransaction CollectionEdit()

            val backedUp = favoritesByIds(workIds)
            val alreadyInTarget = membershipsIn(toFolderId, workIds)
                .map { it.workId }
                .toSet()
            val movedIds = fromMemberships.map { it.workId }
            val now = System.currentTimeMillis()
            val inserted = mutableListOf<FavoriteMembershipEntity>()
            for ((index, workId) in movedIds.filterNot { it in alreadyInTarget }.withIndex()) {
                val membership = FavoriteMembershipEntity(
                    folderId = toFolderId,
                    workId = workId,
                    addedAt = now + index,
                )
                favoriteFolderDao.upsertMembership(membership)
                inserted += membership
            }
            deleteMembershipsIn(fromFolderId, movedIds)
            CollectionEdit(
                restored = fromMemberships,
                removed = inserted,
                favoriteBackups = backedUp,
            )
        }
        if (!edit.isEmpty) triggerAutoSync()
        return edit
    }

    /**
     * 批量移出收藏夹；作品不再属于任何收藏夹时同时取消收藏。
     * 返回的快照含作品行备份，撤销可以把它们原样恢复。
     */
    suspend fun removeWorksFromFolder(workIds: List<String>, folderId: Long): CollectionEdit {
        if (workIds.isEmpty()) return CollectionEdit()
        val edit = database.withTransaction {
            val memberships = membershipsIn(folderId, workIds)
            if (memberships.isEmpty()) return@withTransaction CollectionEdit()
            val backedUp = favoritesByIds(workIds)
            deleteMembershipsIn(folderId, workIds)
            deleteOrphanFavorites(workIds)
            CollectionEdit(restored = memberships, favoriteBackups = backedUp)
        }
        if (!edit.isEmpty) triggerAutoSync()
        return edit
    }

    /**
     * 撤销一次归属变更。
     * 顺序不能颠倒：`favorite_memberships` 对 `favorites` 有 CASCADE 外键，
     * 必须先写回作品行再写回归属，否则直接违反外键约束。
     */
    suspend fun undoCollectionEdit(edit: CollectionEdit) {
        if (edit.isEmpty) return
        database.withTransaction {
            edit.removed.forEach { favoriteFolderDao.deleteMembership(it.folderId, it.workId) }
            edit.favoriteBackups.forEach { favoriteDao.upsert(it) }
            edit.restored.forEach { favoriteFolderDao.upsertMembership(it) }
            // 取消「添加」后可能留下不再属于任何收藏夹的作品行，兜底清一次
            favoriteFolderDao.deleteOrphanedFavorites()
        }
        triggerAutoSync()
    }

    /**
     * 取消收藏：把作品从所有收藏夹移除。
     * 「全部收藏」视图里的「移出」只能理解成取消收藏——那里没有"当前收藏夹"可移出。
     * 快照保留了全部归属，撤销能把作品连同它原本所在的每个收藏夹一起恢复。
     */
    suspend fun unfavoriteWorks(workIds: List<String>): CollectionEdit {
        if (workIds.isEmpty()) return CollectionEdit()
        val edit = database.withTransaction {
            val memberships = membershipsForWorks(workIds)
            if (memberships.isEmpty()) return@withTransaction CollectionEdit()
            val backedUp = favoritesByIds(workIds)
            deleteMembershipsForWorks(workIds)
            favoriteDao.deleteOrphansByIds(workIds)
            CollectionEdit(restored = memberships, favoriteBackups = backedUp)
        }
        if (!edit.isEmpty) triggerAutoSync()
        return edit
    }

    /**
     * 把作品收拢到目标收藏夹：保留目标归属，从其余所有收藏夹移出。
     * 「全部收藏」视图里的「移动」就是这个语义——跨夹整理到一处。
     */
    suspend fun moveWorksFromAllFolders(works: List<Work>, toFolderId: Long): CollectionEdit {
        if (works.isEmpty()) return CollectionEdit()
        val edit = database.withTransaction {
            val workIds = works.map { it.id.toString() }
            val memberships = membershipsForWorks(workIds)
            val leaving = memberships.filterNot { it.folderId == toFolderId }
            // 本来就在目标夹里（且没有其他归属），没什么可收拢的
            if (leaving.isEmpty()) return@withTransaction CollectionEdit()

            val backedUp = favoritesByIds(workIds)
            val alreadyInTarget = memberships.filter { it.folderId == toFolderId }
                .map { it.workId }
                .toSet()
            val now = System.currentTimeMillis()
            val inserted = mutableListOf<FavoriteMembershipEntity>()
            for ((index, work) in works.filterNot { it.id.toString() in alreadyInTarget }.withIndex()) {
                val membership = FavoriteMembershipEntity(
                    folderId = toFolderId,
                    workId = work.id.toString(),
                    addedAt = now + index,
                )
                favoriteFolderDao.upsertMembership(membership)
                inserted += membership
            }
            // 逐个来源夹删除：SQL 的 IN 只作用在 workId 上，来源夹各不相同
            leaving.groupBy { it.folderId }.forEach { (folderId, group) ->
                deleteMembershipsIn(folderId, group.map { it.workId })
            }
            CollectionEdit(
                restored = leaving,
                removed = inserted,
                favoriteBackups = backedUp,
            )
        }
        if (!edit.isEmpty) triggerAutoSync()
        return edit
    }

    /** 只属于该收藏夹的作品数：删除该收藏夹时会被连带取消收藏的数量 */
    suspend fun countExclusiveWorks(folderId: Long): Int =
        favoriteFolderDao.countExclusiveWorks(folderId)

    fun observeFavoriteCount(): Flow<Int> = favoriteDao.observeCount()

    // IN 子句分片：SQLite 的变量上限在旧系统上只有 999（API 26 对应 SQLite 3.18），
    // 「全选」一个大收藏夹会一次塞进上千个 workId，不分片会直接抛 too many SQL variables。
    private suspend fun membershipsIn(
        folderId: Long,
        workIds: List<String>,
    ): List<FavoriteMembershipEntity> =
        workIds.chunked(SQL_VAR_LIMIT).flatMap { favoriteFolderDao.membershipsIn(folderId, it) }

    private suspend fun favoritesByIds(workIds: List<String>): List<FavoriteEntity> =
        workIds.chunked(SQL_VAR_LIMIT).flatMap { favoriteDao.favoritesByIds(it) }

    private suspend fun deleteMembershipsIn(folderId: Long, workIds: List<String>) {
        workIds.chunked(SQL_VAR_LIMIT).forEach { favoriteFolderDao.deleteMemberships(folderId, it) }
    }

    private suspend fun deleteOrphanFavorites(workIds: List<String>) {
        workIds.chunked(SQL_VAR_LIMIT).forEach { favoriteDao.deleteOrphansByIds(it) }
    }

    private suspend fun membershipsForWorks(workIds: List<String>): List<FavoriteMembershipEntity> =
        workIds.chunked(SQL_VAR_LIMIT).flatMap { favoriteDao.membershipsForWorks(it) }

    private suspend fun deleteMembershipsForWorks(workIds: List<String>) {
        workIds.chunked(SQL_VAR_LIMIT).forEach { favoriteDao.deleteMembershipsForWorks(it) }
    }

    /**
     * 默认收藏夹是快速收藏的落点，全 App 唯一。
     * 用 Mutex 串行化「查 → 建」：详情页收藏与收藏页加载可能同时首次触发，
     * 不加锁会各查一次都为空，插入两个默认收藏夹。
     */
    suspend fun ensureDefaultFolder(): Long = defaultFolderLock.withLock {
        favoriteFolderDao.defaultFolderId()?.let { existing ->
            // 早期版本可能已经留下多个默认收藏夹，这里顺手自愈：保留最早的一个
            if (favoriteFolderDao.defaultFolderCount() > 1) {
                favoriteFolderDao.demoteOtherDefaults(existing)
                Log.w(TAG, "ensureDefaultFolder: demoted extra default folders, kept $existing")
            }
            return@withLock existing
        }
        favoriteFolderDao.insertFolder(
            FavoriteFolderEntity(
                name = "默认收藏夹",
                createdAt = System.currentTimeMillis(),
                isDefault = true,
            ),
        )
    }

    /**
     * 触发 WebDAV 自动同步。
     * - 总开关未启用：直接 return
     * - 凭据不全：直接 return（UI 不应再静默吞错）
     * - 同一窗口内的多次触发会 debounce 合并为一次 syncMetadataOnly()
     */
    private fun triggerAutoSync() {
        val enabled = settingsRepository.webDavEnabled.value
        val url = settingsRepository.webDavUrl.value
        val username = settingsRepository.webDavUsername.value
        val passwordLen = settingsRepository.webDavPassword.value.length
        if (!enabled) {
            Log.d(TAG, "triggerAutoSync: skipped, webDavEnabled=false")
            return
        }
        if (url.isBlank() || username.isBlank()) {
            Log.d(TAG, "triggerAutoSync: skipped, url='$url' username='$username' urlBlank=${url.isBlank()} userBlank=${username.isBlank()}")
            return
        }
        val emitted = autoSyncTrigger.tryEmit(Unit)
        Log.d(TAG, "triggerAutoSync: tryEmit=$emitted url=$url username=$username passwordLen=$passwordLen")
    }

    companion object {
        private const val TAG = "FavoriteRepo"

        /** 自动同步去抖：合并 1.5 秒内的连续收藏/移动/重命名等操作。 */
        private const val AUTO_SYNC_DEBOUNCE_MS = 1_500L

        /** IN 子句分片大小：留出余量，兼容旧系统 999 个绑定变量的上限。 */
        private const val SQL_VAR_LIMIT = 900

        /** 用户没填名字时的兜底名（界面已禁止空名，这里是数据层的最后一道） */
        private const val UNNAMED_FOLDER = "未命名收藏夹"

        /** 收藏 id 集合的共享订阅存活时长：界面来回切换时不必每次重查。 */
        private const val FAVORITE_IDS_KEEP_ALIVE_MS = 5_000L
    }
}
