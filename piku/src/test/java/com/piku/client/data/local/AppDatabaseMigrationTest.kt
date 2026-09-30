package com.piku.client.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseMigrationTest {

    @Test
    fun `migrations cover every step up to the declared version`() {
        val declared = DATABASE_SCHEMA_VERSION
        val byStart = AppDatabase.ALL_MIGRATIONS.associateBy { it.startVersion }

        val missing = (1 until declared).filter { byStart[it]?.endVersion != it + 1 }
        assertTrue(
            "版本 $declared 缺迁移：$missing（升级时会直接崩）",
            missing.isEmpty(),
        )
        assertEquals(
            "有一段迁移的终点对不上",
            emptyList<Int>(),
            AppDatabase.ALL_MIGRATIONS.filter { byStart[it.startVersion]?.endVersion != it.endVersion }
                .map { it.startVersion },
        )
    }

    @Test
    fun `no migration is registered twice`() {
        val starts = AppDatabase.ALL_MIGRATIONS.map { it.startVersion }
        assertEquals("同一个起点注册了多次", starts.distinct().size, starts.size)
    }
}
