package com.piku.client.data.repository

private val NOVEL_IMAGE_TOKEN = Regex("""\[nimg:([^\]]+)\]""")

sealed interface NovelBlock {
    data class Text(val text: String) : NovelBlock

    data class Image(val url: String) : NovelBlock
}

fun splitNovelBlocks(text: String): List<NovelBlock> {
    val blocks = mutableListOf<NovelBlock>()
    var start = 0
    for (match in NOVEL_IMAGE_TOKEN.findAll(text)) {
        text.substring(start, match.range.first).trim('\n')
            .takeIf { it.isNotBlank() }
            ?.let { blocks += NovelBlock.Text(it) }
        blocks += NovelBlock.Image(match.groupValues[1])
        start = match.range.last + 1
    }
    text.substring(start).trim('\n')
        .takeIf { it.isNotBlank() }
        ?.let { blocks += NovelBlock.Text(it) }
    return blocks
}
