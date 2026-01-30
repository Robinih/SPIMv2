package com.cvsuagritech.spim.utils

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.cvsuagritech.spim.BuildConfig
import com.cvsuagritech.spim.api.GitHubUpdateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class UpdateManager(private val context: Context) {

    private val githubService: GitHubUpdateService by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("User-Agent", "SPIM-App")
                    .build()
                chain.proceed(request)
            }
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubUpdateService::class.java)
    }

    suspend fun checkForUpdates(manualCheck: Boolean = false) {
        try {
            val response = githubService.getAllReleases()
            if (response.isSuccessful && response.body() != null) {
                val releases = response.body()!!
                
                if (releases.isEmpty()) {
                    if (manualCheck) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "No releases found on GitHub.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return
                }

                // Get the first one (most recent)
                val latestRelease = releases[0]
                val latestVersion = latestRelease.tagName.replace("v", "").replace("SPIM", "").trim()
                val currentVersion = BuildConfig.VERSION_NAME

                Log.d("UpdateManager", "Latest: $latestVersion, Current: $currentVersion")

                if (isNewerVersion(currentVersion, latestVersion)) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(latestVersion, latestRelease.body, latestRelease.assets.firstOrNull()?.downloadUrl)
                    }
                } else if (manualCheck) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "You are using the latest version", Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (manualCheck) {
                val errorBody = response.errorBody()?.string()
                Log.e("UpdateManager", "Error Code: ${response.code()}, Body: $errorBody")
                withContext(Dispatchers.Main) {
                    val msg = when(response.code()) {
                        404 -> "Repository not found."
                        403 -> "API rate limit exceeded."
                        else -> "Server error: ${response.code()}"
                    }
                    Toast.makeText(context, "Could not check for updates: $msg", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Exception: ${e.message}", e)
            if (manualCheck) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Check failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        return current != latest && latest.isNotEmpty()
    }

    private fun showUpdateDialog(version: String, notes: String, downloadUrl: String?) {
        if (downloadUrl == null) return

        AlertDialog.Builder(context)
            .setTitle("Update Available ($version)")
            .setMessage(if (notes.trim().isNotEmpty()) notes else "A new version of SPIM is available. Would you like to download it?")
            .setPositiveButton("Download") { _, _ ->
                startDownload(downloadUrl, version)
            }
            .setNegativeButton("Later", null)
            .setCancelable(false)
            .show()
    }

    private fun startDownload(url: String, version: String) {
        Toast.makeText(context, "Downloading update...", Toast.LENGTH_SHORT).show()
        
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("SPIM Update $version")
                .setDescription("Downloading new version...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "SPIM_update_$version.apk")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
    }
}
