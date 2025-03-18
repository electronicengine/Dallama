package com.example.dallama

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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dallama.ui.theme.DallamaTheme
import java.io.File
import android.Manifest

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.os.Build
//import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.getSystemService
import androidx.lifecycle.viewmodel.viewModelFactory


data class Message(val content: String, val sender: String)
// lateinit var tts: TextToSpeech

class MainActivity(
    activityManager: ActivityManager? = null,
    downloadManager: DownloadManager? = null,
    clipboardManager: ClipboardManager? = null,
): ComponentActivity() {

    private val downloadManager by lazy { downloadManager ?: getSystemService<DownloadManager>()!! }
    private val viewModel: MainViewModel by viewModels()
    var selectedOption by mutableStateOf("Select a model")
    //private lateinit var tts: TextToSpeech  // Global TTS instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
/*
        tts = TextToSpeech(applicationContext) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.language = Locale("tr", "TR")  // Turkish locale
                tts.speak("Merhaba. Nasılsın?", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
        Toast.makeText(applicationContext, "onCreate", Toast.LENGTH_SHORT).show()
*/
        setContent {
            DallamaTheme {
                var drawerState by remember { mutableStateOf(DrawerValue.Closed) }

                ModalNavigationDrawer(
                    drawerState = rememberDrawerState(drawerState),
                    drawerContent = { DrawerContent() },
                    content = { ChatScreenContent() }
                )
            }
        }
    }

    // Drawer content handling
    @Composable
    fun DrawerContent() {
        val context = LocalContext.current
        val imm = ContextCompat.getSystemService(context, InputMethodManager::class.java)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            imm?.hideSoftInputFromWindow((context as Activity).currentFocus?.windowToken, 0)
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
                val extFilesDir = getExternalFilesDir(null)

                val downloadable = Downloadable(
                    "Download",
                    Uri.parse(downloadUrl.text),
                    File(extFilesDir, fileName.text)
                )

                Downloadable.Button(viewModel, downloadManager, downloadable)

                Spacer(modifier = Modifier.height(8.dp))

                ModelSelectionDropdown()

                ModelControlButtons()

                ModelConfigs()
            }
        }
    }

    // Download URL text field
    @Composable
    fun DownloadUrlField(downloadUrl: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
        Text(
            text = "Download Url",
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
                .background(MaterialTheme.colorScheme.background, shape = RoundedCornerShape(10.dp))
                .padding(top = 2.dp)
                .height(30.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(10.dp))
        )
    }

    // File Name text field
    @Composable
    fun FileNameField(fileName: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
        Text(
            text = "File Name Saved",
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
                .background(MaterialTheme.colorScheme.background, shape = RoundedCornerShape(10.dp))
                .padding(top = 2.dp)
                .height(30.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(10.dp))
        )
    }

    // Model selection dropdown
    @Composable
    fun ModelSelectionDropdown() {
        var expanded by remember { mutableStateOf(false) }
        var modelList by remember { mutableStateOf(emptyList<String>()) }

        var extFilesDir = getExternalFilesDir(null)
        extFilesDir?.listFiles()?.let { files ->
            modelList = files.map { it.name }
        }

        Column(modifier = Modifier.width(300.dp)) {
            Button(
                onClick = { expanded = !expanded },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background),
            ) {
                Text(selectedOption, color = MaterialTheme.colorScheme.primary)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                modelList.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            selectedOption = option
                            expanded = false
                        },
                        modifier = Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(10.dp))
                    )
                }
            }
        }
    }

    // Model control buttons (Load, Unload, Delete)
    @Composable
    fun ModelControlButtons() {
        var isModelLoaded by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val REQUEST_CODE = 1001  // İzin isteği için sabit kod
        val requestPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.all { it }) {
                Toast.makeText(context, "Tüm izinler verildi", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "İzinler reddedildi", Toast.LENGTH_SHORT).show()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Button(
                onClick = {
                    try {

                        selectedOption?.let {
                            isModelLoaded = true // Set model as loaded when button is clicked
                            val extFilesDir = getExternalFilesDir(null)
                            val file = File(extFilesDir, it)
                            //Toast.makeText(context, "Selected: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
                            Log.e("ViewModel", "Model yüklenirken hata oluştu:")

                            viewModel.load(file.absolutePath)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isModelLoaded) "Loaded" else "Load", color = MaterialTheme.colorScheme.primary)
            }

            Button(
                onClick = {
                    Toast.makeText(context, "Model cleared", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.weight(1f)
            ) {
                Text("Unload", color = MaterialTheme.colorScheme.primary)
            }

            Button(
                onClick = {
/*
                    val extFilesDir = getExternalFilesDir(null)
                    val file = File(extFilesDir, selectedOption)
                    file.delete()
                    Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
*/
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.weight(1f)
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.primary)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    Toast.makeText(context, "Metrics Showing in Chat", Toast.LENGTH_SHORT).show()
                    viewModel.bench(8, 4, 1)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.weight(1f)
            ) {
                Text("Bench", color = MaterialTheme.colorScheme.primary)
            }

            Button(
                onClick = {
                    Toast.makeText(context, "Messages are Cleared", Toast.LENGTH_SHORT).show()
                    viewModel.clear()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear Messages", color = MaterialTheme.colorScheme.primary)
            }

        }
    }

    @Composable
    fun ModelConfigs() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Column() {
                Text(
                    text = "Max Token Size",
                    style = MaterialTheme.typography.bodyMedium,
                )
                BasicTextField(
                    value = viewModel.maxTokeSize,
                    onValueChange = { viewModel.maxTokeSize = it },
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
                )

                Text(
                    text = "Temperature",
                    style = MaterialTheme.typography.bodyMedium,
                )
                BasicTextField(
                    value = viewModel.temperature,
                    onValueChange = { viewModel.temperature = it },
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
                )
            }

            Column() {
                Text(
                    text = "topK",
                    style = MaterialTheme.typography.bodyMedium,
                )
                BasicTextField(
                    value = viewModel.topK,
                    onValueChange = { viewModel.topK = it },
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
                )

                Text(
                    text = "topP",
                    style = MaterialTheme.typography.bodyMedium,
                )
                BasicTextField(
                    value = viewModel.topP,
                    onValueChange = { viewModel.topP = it },
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

                )
            }
        }

        Text(
            text = "System Prompt",
            style = MaterialTheme.typography.bodyMedium,
        )
        BasicTextField(
            value = viewModel.prompt,
            onValueChange = { viewModel.prompt = it },
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
                .fillMaxSize()
        )
    }

    // Chat screen content
    @Composable
    fun ChatScreenContent() {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            ChatScreen(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(end = 0.dp)
                    .fillMaxSize(),
                viewModel
            )
        }
    }


}

@Composable
fun ChatScreen(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    var messageText by remember { mutableStateOf(TextFieldValue("")) }
    val context = LocalContext.current
    val imm = ContextCompat.getSystemService(context, InputMethodManager::class.java)


    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // Hide the keyboard when touching outside
                        imm?.hideSoftInputFromWindow((context as Activity).currentFocus?.windowToken, 0)
                    }
                )
            }
    ) {
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {

            items(viewModel.messages) { message ->
                MessageCard(message = message)
            }
        }


        Row(
            modifier = Modifier
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text("Message") },
                modifier = Modifier
                   .fillMaxWidth(0.7f)
            )


            Button(
                onClick = {
                    if (messageText.text.isNotBlank()) {
                        viewModel.updateMessage(messageText.text, "You")
                        messageText = TextFieldValue("")
                        viewModel.send()
                    }

                },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("Send")
            }
        }
        LaunchedEffect(viewModel.messages) {
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }
}

@Composable
fun MessageCard(message: Message) {
    val isUser = message.sender == "You"
    val bcolor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .padding(4.dp),
            colors = CardDefaults.cardColors(
                containerColor = bcolor// Set background color
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = message.sender,
                    style = TextStyle(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(text = message.content)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    val mockViewModel = remember { MainViewModel() } // Create ViewModel instance manually

    DallamaTheme {
        ChatScreen(viewModel = mockViewModel) // Pass the ViewModel to ChatScreen
    }
}
