package com.piku.client.data.remote

import com.piku.client.domain.model.TagCard
import java.net.URLDecoder

object TagCardParser {

    private val KWD = Regex("""TagInfoTagName" href="/SearchIllustByTagPcV\.jsp\?KWD=([^"]+)"""")
    private val THUMB = Regex("""TagThumbImg" style="background-image:url\('([^']+)'\)""")
    private val DEFAULT_THUMB = Regex("""/img/default_genre\.png""")

    fun parse(html: String): List<TagCard> =
        html.split("<div class=\"TagThumbPc\">")
            .drop(1)
            .mapNotNull { block ->
                val keyword = KWD.find(block)?.groupValues?.get(1) ?: return@mapNotNull null
                val name = try {
                    URLDecoder.decode(keyword, Charsets.UTF_8.name())
                } catch (e: Exception) {
                    keyword
                }.trim()
                if (name.isEmpty()) return@mapNotNull null
                val thumb = THUMB.find(block)?.groupValues?.get(1)
                    ?.takeIf { !DEFAULT_THUMB.containsMatchIn(it) }
                TagCard(name = name, thumbnailUrl = thumb)
            }
}