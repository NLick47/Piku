package com.piku.client.data.repository

import com.piku.client.domain.model.NsfwLevel
import com.piku.client.domain.model.PublishDraft
import com.piku.client.domain.model.ShowVisibility
import com.piku.client.domain.model.UploadKind
import okhttp3.FormBody
import okhttp3.MultipartBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UploadFormBuilderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun fields(form: FormBody): Map<String, String> =
        (0 until form.size).associate { form.name(it) to form.value(it) }

    private fun multipartNames(body: MultipartBody): Set<String> =
        body.parts.mapNotNull { part ->
            val cd = part.headers?.get("Content-Disposition") ?: return@mapNotNull null
            Regex("""name="([^"]+)"""").find(cd)?.groupValues?.get(1)
        }.toSet()

    private val base = PublishDraft()

    @Test
    fun illustEntrySendsPublicDefaults() {
        val f = fields(UploadFormBuilder.entryForm(base, uid = 14189264))
        assertEquals("0", f["ED"])
        assertEquals("14189264", f["UID"])
        assertEquals("1", f["GD"])
        assertEquals("0", f["CAT"])
        assertEquals("-1", f["RID"])
        assertEquals("true", f["OPTION_PUBLISH"])
        assertEquals("true", f["OPTION_NOT_TIME_LIMITED"])
        assertEquals("true", f["OPTION_NOT_PUBLISH_NSFW"])
        assertEquals("true", f["OPTION_NO_CONDITIONAL_SHOW"])
        assertEquals("true", f["OPTION_NO_PASSWORD"])
        assertEquals("false", f["OPTION_SHOW_FIRST"])
        assertEquals("true", f["OPTION_RECENT"])
        assertEquals("false", f["OPTION_TWEET"])
        assertFalse("全年龄不应携带 NSFW_VAL", f.containsKey("NSFW_VAL"))
        assertFalse("任何人可见不应携带 SHOW_LIMIT_VAL", f.containsKey("SHOW_LIMIT_VAL"))
        assertFalse("无密码不应携带 PASSWORD_VAL", f.containsKey("PASSWORD_VAL"))
        assertFalse("图片不应携带 TIT", f.containsKey("TIT"))
        for (i in 0..8) assertTrue("DES$i 应全量发送", f.containsKey("DES$i"))
    }

    @Test
    fun nsfwR18MapsToFlagOffAndValue4() {
        val f = fields(UploadFormBuilder.entryForm(base.copy(nsfw = NsfwLevel.R18), uid = 1))
        assertEquals("false", f["OPTION_NOT_PUBLISH_NSFW"])
        assertEquals("4", f["NSFW_VAL"])
    }

    @Test
    fun restrictedVisibilityMapsToShowLimitVal() {
        val login = fields(UploadFormBuilder.entryForm(base.copy(visibility = ShowVisibility.POIPIKU_LOGIN), uid = 1))
        assertEquals("false", login["OPTION_NO_CONDITIONAL_SHOW"])
        assertEquals("5", login["SHOW_LIMIT_VAL"])

        val follower = fields(UploadFormBuilder.entryForm(base.copy(visibility = ShowVisibility.FOLLOWER), uid = 1))
        assertEquals("false", follower["OPTION_NO_CONDITIONAL_SHOW"])
        assertEquals("6", follower["SHOW_LIMIT_VAL"])
    }

    @Test
    fun passwordSetFlipsNoPasswordFlag() {
        val f = fields(UploadFormBuilder.entryForm(base.copy(password = "secret"), uid = 1))
        assertEquals("false", f["OPTION_NO_PASSWORD"])
        assertEquals("secret", f["PASSWORD_VAL"])
    }

    @Test
    fun novelEntryUsesEd3AndCarriesTitleBodyDirection() {
        val f = fields(
            UploadFormBuilder.entryForm(
                base.copy(kind = UploadKind.NOVEL, title = "标题", body = "正文", novelDirection = 0),
                uid = 1,
            ),
        )
        assertEquals("3", f["ED"])
        assertEquals("标题", f["TIT"])
        assertEquals("正文", f["BDY"])
        assertEquals("0", f["NOVEL_DIRECTION_VAL"])
    }

    @Test
    fun multipartCarriesFixedFieldsAndInvertedRec() {
        val file: File = tmp.newFile("p1.png")

        val recentOn = UploadFormBuilder.imageUploadBody(
            base.copy(showRecent = true), uid = 1, iid = 10, oid = 20, file = file,
        )
        val names = multipartNames(recentOn)
        assertTrue(names.containsAll(setOf("UID", "IID", "OID", "REC", "qqfile")))
        assertEquals("showRecent=true → REC=0", "0", fieldValue(recentOn, "REC"))

        val recentOff = UploadFormBuilder.imageUploadBody(
            base.copy(showRecent = false), uid = 1, iid = 10, oid = 20, file = file,
        )
        assertEquals("showRecent=false → REC=1", "1", fieldValue(recentOff, "REC"))
    }

    private fun fieldValue(body: MultipartBody, name: String): String {
        val buffer = okio.Buffer()
        body.writeTo(buffer)
        val text = buffer.readUtf8()
        return Regex("""name="$name"\r\n\r\n([^\r\n]*)""").find(text)!!.groupValues[1]
    }
}
