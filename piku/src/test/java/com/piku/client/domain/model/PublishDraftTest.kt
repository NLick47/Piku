package com.piku.client.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishDraftTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun jsonRoundTripKeepsAllFields() {
        val draft = PublishDraft(
            kind = UploadKind.NOVEL,
            categoryCd = 4,
            tags = "tag1 tag2",
            description = "说明",
            publish = false,
            nsfw = NsfwLevel.R18,
            visibility = ShowVisibility.FOLLOWER,
            password = "pw123",
            showRecent = false,
            showFirstOnly = true,
            title = "标题",
            body = "正文",
            novelDirection = 1,
            imageFiles = listOf("/a/1.jpg"),
        )
        val restored = json.decodeFromString(PublishDraft.serializer(), json.encodeToString(PublishDraft.serializer(), draft))
        assertEquals(draft, restored)
    }

    @Test
    fun emptyDraftHasNoContent() {
        assertFalse(PublishDraft().hasContent)
    }

    @Test
    fun anyMeaningfulFieldCountsAsContent() {
        assertTrue(PublishDraft(imageFiles = listOf("/a/1.jpg")).hasContent)
        assertTrue(PublishDraft(kind = UploadKind.NOVEL, title = "t").hasContent)
        assertTrue(PublishDraft(kind = UploadKind.NOVEL, body = "b").hasContent)
        assertTrue(PublishDraft(description = "d").hasContent)
        assertTrue(PublishDraft(publish = false).hasContent)
        assertTrue(PublishDraft(nsfw = NsfwLevel.R18PLUS).hasContent)
        assertTrue(PublishDraft(visibility = ShowVisibility.FOLLOWER).hasContent)
        assertTrue(PublishDraft(password = "x").hasContent)
        assertFalse(PublishDraft(showRecent = false).hasContent)
    }
}
