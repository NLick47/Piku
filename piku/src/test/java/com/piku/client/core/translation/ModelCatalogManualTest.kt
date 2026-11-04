package com.piku.client.core.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogManualTest {

    private fun manual(
        id: String = "manual:1",
        authMode: AuthMode = AuthMode.NONE,
        apiKey: String? = null,
    ) = ModelEntry(
        id = id,
        label = id,
        baseUrl = "http://192.168.1.10:8000/v1",
        model = "hunyuan-mt",
        apiKey = apiKey,
        roles = listOf(Role.NOVEL),
        authMode = authMode,
        isManual = true,
    )

    @Test
    fun manualEntriesNeverBecomeCatalogRoleDefaults() {
        assertNull(
            ModelCatalog.resolveRoleDefault(Role.NOVEL, listOf(manual()), emptyMap()),
        )
    }

    @Test
    fun manualEntryResolvesOnlyWhenExplicitlySelected() {
        val models = listOf(manual())
        assertEquals("manual:1", ModelCatalog.resolveStoredSelection("manual:1", models, Role.NOVEL)?.id)
        assertNull(ModelCatalog.resolveStoredSelection("", models, Role.NOVEL))
    }

    @Test
    fun manualEntryNeverMatchedByBareModelName() {
        val models = listOf(manual())
        assertNull(ModelCatalog.resolveStoredSelection("hunyuan-mt", models, Role.NOVEL))
        assertEquals("manual:1", ModelCatalog.resolveStoredSelection("manual:1", models, Role.NOVEL)?.id)
    }

    @Test
    fun noAuthManualEntryUsableWithoutKey() {
        assertTrue(manual(authMode = AuthMode.NONE).isUsable())
    }

    @Test
    fun bearerManualEntryWithoutKeyIsUnusable() {
        assertFalse(manual(authMode = AuthMode.BEARER, apiKey = null).isUsable())
        assertTrue(manual(authMode = AuthMode.BEARER, apiKey = "sk").isUsable())
    }

    @Test
    fun failoverNeverPicksManualEntry() {
        assertNull(
            ModelCatalog.failoverCandidate(
                current = null,
                role = Role.NOVEL,
                models = listOf(manual()),
            ),
        )
    }

    @Test
    fun modelEntryToStringRedactsKey() {
        val entry = manual(authMode = AuthMode.BEARER, apiKey = "sk-secret-value")
        val text = entry.toString()
        assertFalse(text.contains("sk-secret-value"))
        assertTrue(text.contains("***"))
    }
}
