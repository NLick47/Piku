package com.piku.client.domain.source

fun ContentSource.facetGroups(feedId: String): List<SourceFacetGroup> =
    facets.filter { it.feedId == null || it.feedId == feedId }

fun ContentSource.defaultFacetChoices(feedId: String): Map<String, String> =
    facetGroups(feedId).associate { group ->
        group.id to (group.options.firstOrNull { it.selectedByDefault }?.id ?: group.options.first().id)
    }

fun ContentSource.sanitizeFacetChoices(feedId: String, choices: Map<String, String>?): Map<String, String> {
    val groups = facetGroups(feedId)
    return defaultFacetChoices(feedId).mapValues { (groupId, defaultId) ->
        val chosen = choices?.get(groupId)
        if (chosen != null && groups.any { it.id == groupId && it.options.any { option -> option.id == chosen } }) {
            chosen
        } else {
            defaultId
        }
    }
}
