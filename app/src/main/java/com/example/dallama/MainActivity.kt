package com.llama.dallama

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dallama.ui.theme.DallamaTheme
import java.io.File

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.text.Html
//import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class Message(val content: String, val sender: String, val timestamp: Long)
// lateinit var tts: TextToSpeech

class MainActivity(
    activityManager: ActivityManager? = null,
    downloadManager: DownloadManager? = null,
): ComponentActivity() {

    var isBlocked = mutableStateOf(false)
    val downloadManager by lazy { downloadManager ?: getSystemService<DownloadManager>()!! }
    val chatModel: LlamaModel = LlamaModel("Chat Model")
    val embeddingModel: LlamaModel = LlamaModel("Embedding Model")
    val pdfTextMap = mutableStateMapOf<String, Pair<List<String>, List<FloatArray>>>()
    var currentDownloadStatus by mutableStateOf("Preparing download...")
    var selectedChatModel by mutableStateOf("Select Chat")
    var selectedEmbeddingModel by mutableStateOf("Select Embed")

    private lateinit var modelDownloadManager: ModelDownloadManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PDFBoxResourceLoader.init(getApplicationContext());
        modelDownloadManager = ModelDownloadManager(this)


        setContent {

            DallamaTheme {
                var drawerState by remember { mutableStateOf(DrawerValue.Closed) }
                val extFilesDir = getExternalFilesDir(null)
                val modelDirEmpty by remember {
                    mutableStateOf(
                        extFilesDir?.listFiles()?.filter { it.isFile }?.isEmpty() ?: true
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    ModalNavigationDrawer(
                        drawerState = rememberDrawerState(drawerState),
                        drawerContent = { DrawerContent(modelDownloadManager) },
                        content = { ChatScreenContent(chatModel, embeddingModel, pdfTextMap) },
                        modifier = Modifier
                            .then(if (isBlocked.value) Modifier.alpha(0.5f) else Modifier)
                            .fillMaxSize()
                    )
                    if (isBlocked.value) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                DownloadProgressIndicator(
                                    isDownloading = true,
                                    progress = modelDownloadManager.downloadProgress,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    // Show dialog if no models found on first launch
                    if (modelDirEmpty) {
                        modelDownloadManager.CheckAndPromptDefaultModels(
                            modelDir = extFilesDir!!,
                            onDownloadDefault = { modelDownloadManager.downloadDefaultModels() }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun DownloadProgressIndicator(
    isDownloading: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    if (isDownloading) {
        Column(modifier = modifier) {
            Text(
                text = "Downloading default models... ${(progress * 100).toInt()}%",
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    val mockChatModel = LlamaModel("Chat Model")
    val mockEmbeddingModel = LlamaModel("Embedding Model")

    DallamaTheme {
        ChatScreenContent(
            chatModel = mockChatModel,
            embeddingModel = mockEmbeddingModel,
            pdfTextMap = remember { mutableStateMapOf() }
        )
    }
}
