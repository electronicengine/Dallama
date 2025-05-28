package com.llama.dallama

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.PowerManager
import androidx.core.database.getLongOrNull
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import java.io.File

data class Downloadable(val name: String, val source: Uri, val destination: File) {
    companion object {
        sealed interface State
        data object Download : State
        data class Downloading(val id: Long) : State
        data class Error(val message: String) : State

        fun enqueue(
            downloadManager: DownloadManager,
            downloadable: Downloadable,
            activity: Activity
        ): Long {
            if (downloadable.destination.exists()) {
                downloadable.destination.delete()
            }

            val request = DownloadManager.Request(downloadable.source)
                .setTitle(downloadable.name)
                .setDescription("Downloading ${downloadable.name}")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(downloadable.destination))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            return downloadManager.enqueue(request)
        }

    }
}
