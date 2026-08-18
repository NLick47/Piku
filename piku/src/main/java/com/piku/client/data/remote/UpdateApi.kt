package com.piku.client.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val prerelease: Boolean = false,
)

interface UpdateApi {
    @GET("repos/NLick47/Piku/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease
}