package com.cvsuagritech.spim.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == action) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId != -1L) {
                installApk(context, downloadId)
            }
        }
    }

    private fun installApk(context: Context, downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        // Use DownloadManager to get the URI. This is more reliable than manual FileProvider conversion
        // for files downloaded via DownloadManager.
        val apkUri = downloadManager.getUriForDownloadedFile(downloadId)
        
        if (apkUri != null) {
            val installIntent = Intent(Intent.ACTION_VIEW)
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            try {
                context.startActivity(installIntent)
            } catch (e: Exception) {
                e.printStackTrace()
                
                // Fallback to old method if getUriForDownloadedFile fails or if we need FileProvider
                try {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor.moveToFirst()) {
                        val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        val localUriString = cursor.getString(localUriIndex)
                        if (localUriString != null) {
                            val fileUri = Uri.parse(localUriString)
                            val file = File(fileUri.path!!)
                            val contentUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                file
                            )
                            val fallbackIntent = Intent(Intent.ACTION_VIEW)
                            fallbackIntent.setDataAndType(contentUri, "application/vnd.android.package-archive")
                            fallbackIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(fallbackIntent)
                        }
                    }
                    cursor.close()
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
        }
    }
}
