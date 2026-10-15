package com.piku.client.core.usecase

import com.piku.client.core.source.LibraryPool
import com.piku.client.core.source.SourceRegistry
import com.piku.client.core.storage.prefs.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveLibraryPoolUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sourceRegistry: SourceRegistry,
) {
    operator fun invoke(): Flow<LibraryPool> =
        settingsRepository.homeSource
            .map(sourceRegistry::libraryPoolOf)
            .distinctUntilChanged()

    /** 同步取一次：页面起手就要按池取数，先渲染一帧全源再纠正会闪 */
    fun current(): LibraryPool = sourceRegistry.libraryPoolOf(settingsRepository.homeSource.value)
}
