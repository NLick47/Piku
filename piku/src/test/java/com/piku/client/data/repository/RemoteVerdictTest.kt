package com.piku.client.data.repository

import com.piku.client.domain.model.FavoriteSyncData
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteVerdictTest {

    private val current = FavoriteSyncData.CURRENT_VERSION

    @Test
    fun sameVersionIsMerged() {
        assertEquals(RemoteVerdict.USABLE, remoteVerdict(remoteVersion = current, currentVersion = current))
    }

    @Test
    fun newerRemoteIsLeftUntouched() {
        assertEquals(
            RemoteVerdict.FROM_NEWER,
            remoteVerdict(remoteVersion = current + 1, currentVersion = current),
        )
    }

    @Test
    fun olderRemoteIsTreatedAsFirstSync() {
        assertEquals(
            RemoteVerdict.UNUSABLE,
            remoteVerdict(remoteVersion = current - 1, currentVersion = current),
        )
    }
}
