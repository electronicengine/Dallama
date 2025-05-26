// File: app/src/main/java/com/example/dallama/ModelDownloadManager.kt
package com.llama.dallama

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.activity.ComponentActivity.RECEIVER_NOT_EXPORTED
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import java.io.File
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ModelDownloadManager(val activity:  MainActivity) {
    var downloadProgress by mutableStateOf(0f)
    private val downloadManager: DownloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    // ... (move downloadDefaultModels, observeDownloads, areAllDownloadsComplete, updateDownloadProgress here)
    // Expose methods for MainActivity/AppContent to call

    private val DEFAULT_MODELS = mapOf(
        "chat" to Pair(
            "chat_dolphin3.0-3b.gguf",
            "https://huggingface.co/bartowski/Dolphin3.0-Llama3.2-3B-GGUF/resolve/main/Dolphin3.0-Llama3.2-3B-Q4_K_M.gguf?download=true"
        ),
        "embed" to Pair(
            "embedding_mxbai-base-v2.gguf",
            "https://huggingface.co/DevQuasar/mixedbread-ai.mxbai-rerank-base-v2-GGUF/resolve/main/mixedbread-ai.mxbai-rerank-base-v2.Q4_K_M.gguf?download=true"
        )
    )


    @Composable
    fun CheckAndPromptDefaultModels(
        modelDir: File,
        onDownloadDefault: () -> Unit
    ) {
        var showDialog by remember { mutableStateOf(true) }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("No Models Found") },
                text = { Text("Do you want to download default models?") },
                confirmButton = {
                    Button(onClick = {
                        showDialog = false
                        onDownloadDefault()
                    }) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    Button(onClick = { showDialog = false }) {
                        Text("No")
                    }
                }
            )
        }
    }

    fun downloadFile(fileName: String, url: String): Long {
        activity.isBlocked.value = true
        val extFilesDir = activity.getExternalFilesDir(null)
        val downloadable = Downloadable(
            fileName,
            Uri.parse(url),
            File(extFilesDir, fileName)
        )
        val downloadId = Downloadable.enqueue(downloadManager, downloadable, activity)
        observeDownloads(listOf(downloadId))
        return downloadId
    }

    fun downloadDefaultModels() {
        activity.isBlocked.value = true
        val extFilesDir = activity.getExternalFilesDir(null)
        val downloadIds = mutableListOf<Long>()

        DEFAULT_MODELS.forEach { (_, modelInfo) ->
            val (fileName, url) = modelInfo
            val downloadable = Downloadable(
                fileName,
                Uri.parse(url),
                File(extFilesDir, fileName)
            )
            val downloadId = Downloadable.enqueue(downloadManager, downloadable, activity)
            downloadIds.add(downloadId)
        }

        if (downloadIds.isNotEmpty()) {
            observeDownloads(downloadIds)
        } else {
            activity.isBlocked.value = false
        }
    }


    private fun acquireWakeLock(activity: Activity): PowerManager.WakeLock? {
        val pm = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "dallama:download_wakelock"
        )?.apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    private fun observeDownloads(downloadIds: List<Long>) {
        // Acquire a wake lock to keep the device awake during downloads
        val wakeLock = acquireWakeLock(activity)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val downloadId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId != -1L && downloadIds.contains(downloadId)) {
                    // Check if all downloads are complete
                    if (areAllDownloadsComplete(downloadIds)) {
                        activity.isBlocked.value = false
                        try {
                            activity.unregisterReceiver(this)
                        } catch (e: IllegalArgumentException) {
                            // Receiver was already unregistered
                        }
                        releaseWakeLock(wakeLock)
                        activity.recreate()
                    }
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            activity.registerReceiver(receiver, filter)
        }

        // Start a coroutine to periodically check progress and unblock if all downloads are done
        (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
            while (activity.isBlocked.value) {
                updateDownloadProgress(downloadIds)
                if (areAllDownloadsComplete(downloadIds)) {
                    activity.isBlocked.value = false
                    releaseWakeLock(wakeLock)
                    break
                }
                delay(1000)
            }
            // Ensure wake lock is released if coroutine exits
            releaseWakeLock(wakeLock)
        }
    }

    private fun areAllDownloadsComplete(downloadIds: List<Long>): Boolean {
        return downloadIds.all { id ->
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id))
            try {
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                    status == DownloadManager.STATUS_SUCCESSFUL
                } else {
                    false
                }
            } finally {
                cursor.close()
            }
        }
    }

    private fun updateDownloadProgress(downloadIds: List<Long>) {
        var totalBytes = 0L
        var downloadedBytes = 0L

        downloadIds.forEach { id ->
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id))
            try {
                if (cursor.moveToFirst()) {
                    val total = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val downloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    totalBytes += total
                    downloadedBytes += downloaded
                }
            } finally {
                cursor.close()
            }
        }

        downloadProgress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
        // Update your UI with this progress value
    }

}
