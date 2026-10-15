package com.piku.client.core.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KeyDisciplineTest {

    private val forbidden = Regex("""\.id\.toString\(\)""")

    @Test
    fun noSelfConcatenatedWorkKeysInDataLayers() {
        val offenders = scannedDirs().flatMap { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        if (!forbidden.containsMatchIn(line)) return@mapIndexedNotNull null
                        "${file.relativeTo(mainRoot())}:${index + 1}: ${line.trim()}"
                    }
                }
                .toList()
        }

        assertTrue(
            "数据层不许用 work.id 拼键（要用 work.key.workId）：\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    /** 键只在这些地方诞生：跨源仓库、DB 层、各源自己的 data 层（ui 层不碰键） */
    private fun scannedDirs(): List<File> {
        val root = mainRoot()
        val perSourceData = File(root, "source").listFiles().orEmpty()
            .filter { it.isDirectory }
            .map { File(it, "data") }
            .filter { it.isDirectory }
        return listOf(File(root, "core/data"), File(root, "core/storage")) + perSourceData
    }

    private fun mainRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "src/main/java/com/piku/client")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("找不到 main 源码根目录")
    }
}
