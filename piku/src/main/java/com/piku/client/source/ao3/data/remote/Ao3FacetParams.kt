package com.piku.client.source.ao3.data.remote

import com.piku.client.source.ao3.data.source.Ao3ContentSource

internal object Ao3FacetParams {

    fun of(choices: Map<String, String>): Map<String, String> = buildMap {
        when (choices[Ao3ContentSource.FACET_COMPLETE]) {
            Ao3ContentSource.COMPLETE_YES -> put("work_search[complete]", "T")
            Ao3ContentSource.COMPLETE_NO -> put("work_search[complete]", "F")
        }
    }
}
