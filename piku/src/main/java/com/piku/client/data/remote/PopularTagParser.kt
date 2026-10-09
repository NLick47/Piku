package com.piku.client.data.remote

import com.piku.client.domain.model.PopularTag

object PopularTagParser {

    private val TAG = Regex("""<h2 class="GenreNameOrg">#?([^<]+)</h2>""")
    private val ICON = Regex("""GenreImage" style="background-image: url\('([^']+)'\)""")
    private val GENRE_ID = Regex("""genre_(\d+)_icon""")

    private val WORK_THUMB = Regex("""IllustThumbImgPic" src="([^"]+)"""")

    fun parse(html: String): List<PopularTag> =
        html.split("<section class=\"CategoryListItem\">")
            .drop(1)
            .mapNotNull { block ->
                val name = TAG.find(block)?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
                val icon = ICON.find(block)?.groupValues?.get(1)
                val genreId = GENRE_ID.find(icon ?: "")?.groupValues?.get(1)?.toLongOrNull()
                val thumb = WORK_THUMB.find(block)?.groupValues?.get(1)
                PopularTag(name = name, genreId = genreId, iconUrl = icon, thumbnailUrl = thumb)
            }
}