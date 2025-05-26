// File: app/src/main/java/com/example/dallama/DrawerContentComponent.kt
package com.llama.dallama

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

// Drawer content handling
@Composable
fun DrawerContent(modelDownloadManager: ModelDownloadManager) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val imm = ContextCompat.getSystemService(context, InputMethodManager::class.java)

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 0.dp, max = 350.dp)
            .background(MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        imm?.hideSoftInputFromWindow(
                            (context as Activity).currentFocus?.windowToken,
                            0
                        )
                    }
                )
            }
    ) {
        var downloadUrl by remember { mutableStateOf(TextFieldValue("https://huggingface.co/bartowski/Dolphin3.0-Llama3.2-3B-GGUF/resolve/main/Dolphin3.0-Llama3.2-3B-Q4_K_M.gguf?download=true")) }
        var fileName by remember { mutableStateOf(TextFieldValue("Dolphin3.0-Llama3.2-3B-Q4_K_M.gguf")) }

        Column(
            modifier = Modifier
                .padding(top = 80.dp, start = 20.dp, end = 20.dp)
                .fillMaxSize()
        ) {
            DownloadUrlField(downloadUrl) { downloadUrl = it }
            FileNameField(fileName) { fileName = it }

            Spacer(modifier = Modifier.height(8.dp))
            val extFilesDir = context.getExternalFilesDir(null)

            val downloadable = Downloadable(
                "Download",
                Uri.parse(downloadUrl.text),
                File(extFilesDir, fileName.text)
            )

            Column(modifier = Modifier.padding(top = 10.dp)) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    // Move download status and progress state up to this composable

                    DownloadButton(
                        modelDownloadManager,
                        fileName.text,
                        downloadUrl.text
                    )
                    DeleteButtonDropdown(
                        activity,
                        modifier = Modifier.padding(start = 20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            ModelSelectionDropdown()

            ModelControlButtons()

            ModelConfigs()
        }
    }
}

// Download URL text field
@Composable
fun DownloadUrlField(downloadUrl: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(
            text = "Download Url",
            color = MaterialTheme.colorScheme.background,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        BasicTextField(
            value = downloadUrl,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Normal,
                fontSize = 20.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(2.dp)
                .height(30.dp)
        )
    }
}

// File Name text field
@Composable
fun FileNameField(fileName: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(
            text = "File Name Saved",
            color = MaterialTheme.colorScheme.background,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        BasicTextField(
            value = fileName,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Normal,
                fontSize = 20.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(2.dp)
                .height(30.dp)
        )
    }
}

// Model selection dropdown
@Composable
fun ModelSelectionDropdown() {
    var expandedChatModel by remember { mutableStateOf(false) }
    var expandedEmbeddingModel by remember { mutableStateOf(false) }
    val context = LocalContext.current

    var modelList by remember { mutableStateOf(emptyList<String>()) }

    var extFilesDir = context.getExternalFilesDir(null)
    extFilesDir?.listFiles()?.let { files ->
        modelList = files.map { it.name }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.width(150.dp)) {
            Button(
                onClick = { expandedChatModel = !expandedChatModel },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background),
            ) {
                Text((context as MainActivity).selectedChatModel, color = MaterialTheme.colorScheme.primary)
            }

            DropdownMenu(
                expanded = expandedChatModel,
                onDismissRequest = { expandedChatModel = false },
            ) {
                modelList.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            (context as MainActivity).selectedChatModel = option
                            onModelSelected(context, option,  false)
                            expandedChatModel = false
                        },
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.background,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(0.dp) // İçerik için boşluk


                    )
                }
            }
        }

        Column(modifier = Modifier.width(150.dp)) {
            Button(
                onClick = { expandedEmbeddingModel = !expandedEmbeddingModel },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background),
            ) {
                Text((context as MainActivity).selectedEmbeddingModel, color = MaterialTheme.colorScheme.primary)
            }

            DropdownMenu(
                expanded = expandedEmbeddingModel,
                onDismissRequest = { expandedEmbeddingModel = false },
            ) {
                modelList.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            (context as MainActivity).selectedEmbeddingModel = option
                            onModelSelected(context, option, true)
                            expandedEmbeddingModel = false
                        },
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.background,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(0.dp) // İçerik için boşluk

                    )
                }
            }
        }

    }


}

fun onModelSelected(context: Context, option: String, isEmbedding: Boolean = true) {
    val selectedModel = option
    val activity = context as MainActivity
    val llamaModel = if (isEmbedding) activity.embeddingModel else activity.chatModel

    if (!selectedModel.isNullOrBlank() && (selectedModel != "Select Embed" || selectedModel != "Select Chat")) {
        try {
            selectedModel.let {
                val extFilesDir = context.getExternalFilesDir(null)
                val file = File(extFilesDir, it)
                runBlocking {
                    activity.isBlocked.value = true

                    if (llamaModel.isLoaded()) {
                        llamaModel.unload()
                    }
                    llamaModel.load(file.absolutePath, isEmbedding)
                    activity.isBlocked.value = false

                }
            }

        } catch (e: Exception) {
            activity.isBlocked.value = false
            llamaModel.addToMessages("An error accured: ${e.message}", "System")
            Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

// Model control buttons (Load, Unload, Delete)
@Composable
fun ModelControlButtons() {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                Toast.makeText(context, "Metrics Showing in Chat", Toast.LENGTH_SHORT).show()
                (context as? MainActivity)?.chatModel?.bench(8, 4, 1)
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier.weight(1f)
        ) {
            Text("Bench Chat Model", color = MaterialTheme.colorScheme.primary)
        }

    }
}

@Composable
fun ModelConfigs() {
    val context = LocalContext.current
    val activity = context as MainActivity

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Column() {
            Text(
                text = "Max Token Size",
                color =  MaterialTheme.colorScheme.background,
                style = MaterialTheme.typography.bodyMedium,
            )
            BasicTextField(
                value = activity.chatModel.maxTokeSize,
                onValueChange = { activity.chatModel.maxTokeSize = it },
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp
                ),
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .height(30.dp)
                    .padding(start = 5.dp)
            )

            Text(
                text = "Temperature",
                color =  MaterialTheme.colorScheme.background,
                style = MaterialTheme.typography.bodyMedium,
            )
            BasicTextField(
                value = activity.chatModel.temperature,
                onValueChange = { activity.chatModel.temperature = it },
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp
                ),
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .height(30.dp)
                    .padding(start = 5.dp)
            )
        }

        Column() {
            Text(
                text = "topK",
                color =  MaterialTheme.colorScheme.background,
                style = MaterialTheme.typography.bodyMedium,
            )
            BasicTextField(
                value = activity.chatModel.topK,
                onValueChange = { activity.chatModel.topK = it },
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp

                ),
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .height(30.dp)
                    .padding(start = 5.dp)
            )

            Text(
                text = "topP",
                color =  MaterialTheme.colorScheme.background,
                style = MaterialTheme.typography.bodyMedium,
            )
            BasicTextField(
                value = activity.chatModel.topP,
                onValueChange = { activity.chatModel.topP = it },
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp
                ),
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .height(30.dp)
                    .padding(start = 5.dp)

            )
        }
    }

    Text(
        text = "System Prompt",
        color =  MaterialTheme.colorScheme.background,
        style = MaterialTheme.typography.bodyMedium,
    )
    BasicTextField(
        value = activity.chatModel.systemMessage,
        onValueChange = { activity.chatModel.systemMessage = it },
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp
        ),
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(10.dp)
            )
            .border(
                2.dp,
                MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp)
            )
            .height(90.dp)
            .padding(start = 5.dp)
            .fillMaxSize()
    )

    Text(
        text = "Chat Template",
        color =  MaterialTheme.colorScheme.background,
        style = MaterialTheme.typography.bodyMedium,
    )
    BasicTextField(
        value = activity.chatModel.chatTemplate,
        onValueChange = { activity.chatModel.chatTemplate = it },
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp
        ),
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(10.dp)
            )
            .border(
                2.dp,
                MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp)
            )
            .height(90.dp)
            .padding(start = 5.dp)
            .fillMaxSize()
    )
}

@Composable
fun DownloadButton(
    modelDownloadManager: ModelDownloadManager,
    fileName: String,
    link: String
) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val extFilesDir = context.getExternalFilesDir(null)
    val fileExists = extFilesDir?.let { dir -> java.io.File(dir, fileName).exists() } == true

    fun onDownload() {
        modelDownloadManager.downloadFile(fileName, link)

    }

    Button(
        onClick = { onDownload() },
        modifier = Modifier.width(125.dp),
        enabled = !fileExists,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    ) {
        Text("Download", color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun DeleteButtonDropdown(
    activity: Activity,
    modifier: Modifier = Modifier
) {
    val extFilesDir = activity.getExternalFilesDir(null)
    val fileList = extFilesDir?.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
    var expanded by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            enabled = fileList.isNotEmpty()
        ) {
            Text("Delete File", color = MaterialTheme.colorScheme.error)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            fileList.forEach { fileName ->
                DropdownMenuItem(
                    text = { Text(fileName, color = MaterialTheme.colorScheme.primary) },
                    onClick = {
                        fileToDelete = fileName
                        showDialog = true
                        expanded = false
                    }
                )
            }
        }
        if (showDialog && fileToDelete != null) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Deletion ${fileToDelete}") },
                text = { Text("Are you sure to delete ${fileToDelete} ?") },
                confirmButton = {
                    Button(onClick = {
                        val file = java.io.File(extFilesDir, fileToDelete!!)
                        val ret = file.delete()
                        Toast.makeText(activity, if (ret) "File deleted" else "File could not be deleted", Toast.LENGTH_SHORT).show()

                        showDialog = false
                        fileToDelete = null
                    }) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    Button(onClick = {
                        showDialog = false
                        fileToDelete = null
                    }) {
                        Text("No")
                    }
                }
            )
        }
    }
}

