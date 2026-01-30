package com.cvsuagritech.spim.api

import retrofit2.Response
import retrofit2.http.GET

interface GitHubUpdateService {
    // Getting the list of releases instead of just the 'latest'
    // This helps if the release is marked as a pre-release
    @GET("repos/Robinih/SPIMv2/releases")
    suspend fun getAllReleases(): Response<List<GitHubRelease>>
}
