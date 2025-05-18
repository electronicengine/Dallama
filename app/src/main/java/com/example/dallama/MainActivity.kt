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


data class Message(val content: String, val sender: String, val timestamp: Long)
// lateinit var tts: TextToSpeech

class MainActivity(
    activityManager: ActivityManager? = null,
    downloadManager: DownloadManager? = null,
): ComponentActivity() {

    private val downloadManager by lazy { downloadManager ?: getSystemService<DownloadManager>()!! }
    private val chatModel: LlamaModel = LlamaModel("Chat Model")
    private val embeddingModel: LlamaModel = LlamaModel("Embedding Model")
    val pdfTextMap = mutableStateMapOf<String, Pair<List<String>, List<FloatArray>>>()
    private var isBlocked by mutableStateOf(false)

    var selectedChatModel by mutableStateOf("Select Chat")
    var selectedEmbeddingModel by mutableStateOf("Select Embed")

    //private lateinit var tts: TextToSpeech  // Global TTS instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PDFBoxResourceLoader.init(getApplicationContext());

        setContent {
            DallamaTheme {
                var drawerState by remember { mutableStateOf(DrawerValue.Closed) }

                Box(modifier = Modifier.fillMaxSize()) {
                    ModalNavigationDrawer(
                        drawerState = rememberDrawerState(drawerState),
                        drawerContent = { DrawerContent() },
                        content = { ChatScreenContent() },
                        modifier = Modifier
                            .then(if (isBlocked) Modifier.alpha(0.5f) else Modifier)
                            .fillMaxSize()
                    )
                    if (isBlocked) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
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
                val extFilesDir = getExternalFilesDir(null)

                val downloadable = Downloadable(
                    "Download",
                    Uri.parse(downloadUrl.text),
                    File(extFilesDir, fileName.text)
                )

                Downloadable.Button(downloadManager, downloadable, modifier = Modifier.padding(top = 10.dp) )

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

        var modelList by remember { mutableStateOf(emptyList<String>()) }

        var extFilesDir = getExternalFilesDir(null)
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
                    Text(selectedChatModel, color = MaterialTheme.colorScheme.primary)
                }

                DropdownMenu(
                    expanded = expandedChatModel,
                    onDismissRequest = { expandedChatModel = false },
                ) {
                    modelList.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                selectedChatModel = option
                                onModelSelected(option,  false)
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
                    Text(selectedEmbeddingModel, color = MaterialTheme.colorScheme.primary)
                }

                DropdownMenu(
                    expanded = expandedEmbeddingModel,
                    onDismissRequest = { expandedEmbeddingModel = false },
                ) {
                    modelList.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                selectedEmbeddingModel = option
                                onModelSelected(option, true)
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

    fun onModelSelected(option: String, isEmbedding: Boolean = true) {
        val selectedModel = option
        val llamaModel = if (isEmbedding) embeddingModel else chatModel
        val context = this@MainActivity

        if (!selectedModel.isNullOrBlank() && (selectedModel != "Select Embed" || selectedModel != "Select Chat")) {
            try {
                selectedModel.let {
                    val extFilesDir = getExternalFilesDir(null)
                    val file = File(extFilesDir, it)
                    runBlocking {
                        isBlocked = true

                        if (llamaModel.isLoaded()) {
                            llamaModel.unload()
                        }
                        llamaModel.load(file.absolutePath, isEmbedding)
                        isBlocked = false

                    }
                }

            } catch (e: Exception) {
                isBlocked =false
                llamaModel.addToMessages("An error accured: ${e.message}", "System")
                Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Model control buttons (Load, Unload, Delete)
    @Composable
    fun ModelControlButtons() {
        var isChatLoaded by remember { mutableStateOf(false) }
        var isEmbeddingModelLoaded by remember { mutableStateOf(false) }

        val context = LocalContext.current
        val REQUEST_CODE = 1001  // constant code for request permissions
        val requestPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.all { it }) {
                Toast.makeText(context, "All permissions are preserved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Required permissions are rejected!", Toast.LENGTH_SHORT).show()
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
                    chatModel.bench(8, 4, 1)
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
                    value = chatModel.maxTokeSize,
                    onValueChange = { chatModel.maxTokeSize = it },
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
                    value = chatModel.temperature,
                    onValueChange = { chatModel.temperature = it },
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
                    value = chatModel.topK,
                    onValueChange = { chatModel.topK = it },
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
                    value = chatModel.topP,
                    onValueChange = { chatModel.topP = it },
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
            value = chatModel.systemMessage,
            onValueChange = { chatModel.systemMessage = it },
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
                .fillMaxSize()
        )

        Text(
            text = "Chat Template",
            color =  MaterialTheme.colorScheme.background,
            style = MaterialTheme.typography.bodyMedium,
        )
        BasicTextField(
            value = chatModel.chatTemplate,
            onValueChange = { chatModel.chatTemplate = it },
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
                chatModel,
                embeddingModel,
                pdfTextMap
            )
        }
    }


}

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    chatModel: LlamaModel,
    embeddingModel: LlamaModel,
    pdfTextMap: MutableMap<String, Pair<List<String>, List<FloatArray>>>,
) {
    var messageText by remember { mutableStateOf(TextFieldValue("")) }
    val context = LocalContext.current
    val imm = ContextCompat.getSystemService(context, InputMethodManager::class.java)
    val focusManager = LocalFocusManager.current

    val listState = rememberLazyListState()
    val parser = remember { PdfTextParser(context) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    isLoading = true
                    statusText = "Parsing PDF..."
                    try {
                        val text = parser.parseTextFromPdf(it)
                        val fileName = parser.getFileNameFromUri(it) ?: "Unknown.pdf"
                        var embeddings: List<FloatArray> = emptyList()
                        if (embeddingModel.isLoaded()) {
                            statusText = "Calculating embeddings..."
                            embeddings = text.map { part ->
                                embeddingModel.calculateEmbedding(part)
                            }
                            pdfTextMap[fileName] = Pair(text, embeddings)
                        } else {
                            embeddingModel.addToMessages("Embedding model is not loaded! Select a embedding model", "System")
                        }
                        statusText = pdfTextMap.keys.joinToString(separator = "\n") { name -> if (name.length > 24) name.take(24) + "..." else name }
                    } catch (e: Exception) {
                        statusText = "Parse failed."
                    } finally {
                        isLoading = false
                    }
                }
            }
        }
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isTextFieldFocused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        focusManager.clearFocus()
                        imm?.hideSoftInputFromWindow(
                            (context as Activity).currentFocus?.windowToken,
                            0
                        )
                    }
                )
            }
    ) {
        Column(Modifier.weight(1f)) {
            ChatMessageList(
                chatModel = chatModel,
                embeddingModel = embeddingModel,
                listState = listState
            )
        }

        PdfStatusAndButton(
            isTextFieldFocused = isTextFieldFocused,
            statusText = statusText,
            isLoading = isLoading,
            onPdfButtonClick = { launcher.launch(arrayOf("application/pdf")) }
        )

        ChatInputRow(
            messageText = messageText,
            onMessageChange = { messageText = it },
            onSendClick = {
                handleSendMessage(messageText, chatModel, embeddingModel, pdfTextMap) { messageText = it }
            },
            interactionSource = interactionSource,
            isLoading
        )

        LaunchedEffect(chatModel.messages) {
            listState.animateScrollToItem(chatModel.messages.size - 1)
        }
    }
}

private fun handleSendMessage(
    messageText: TextFieldValue,
    chatModel: LlamaModel,
    embeddingModel: LlamaModel,
    pdfTextMap: MutableMap<String, Pair<List<String>, List<FloatArray>>>,
    onMessageCleared: (TextFieldValue) -> Unit
) {
    if (messageText.text.isNotBlank()) {
        if(pdfTextMap.isNotEmpty()){
            if(embeddingModel.isLoaded()){
                val embText = runBlocking {
                    embeddingModel.calculateEmbedding(messageText.text)
                }
                val top2 = runBlocking {
                    pdfTextMap.values.flatMap { (texts, embeddings) ->
                        embeddings.mapIndexed { idx, emb ->
                            val sim = embeddingModel.calculateSimilarity(embText, emb)
                            Pair(sim, texts[idx])
                        }
                    }.sortedByDescending { it.first }.take(2)
                }

                val top2Text = top2.joinToString(separator = "\n") { " ${it.second}" }

                chatModel.updateSystemMessage("Relevant Document Info: $top2Text")
                chatModel.updateMessage("${messageText.text}", "You")
                onMessageCleared(TextFieldValue(""))
                chatModel.send()

                //embeddingModel.addToMessages("Parts: \n$top3Text", "System")

            }else{
                embeddingModel.addToMessages("Embedding model is not loaded! Select a embedding model", "System")
            }
        }else{
            if(chatModel.isLoaded()){
                chatModel.updateMessage(messageText.text, "You")
                onMessageCleared(TextFieldValue(""))
                chatModel.send()
            }else{
                chatModel.addToMessages("Chat model is not loaded! Select a chat model", "System")
            }

        }
    }
}

@Composable
private fun ChatMessageList(
    chatModel: LlamaModel,
    embeddingModel: LlamaModel,
    listState: LazyListState
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        items((chatModel.messages + embeddingModel.messages).sortedBy { it.timestamp }) { message ->
            MessageCard(message = message)
        }
    }
}

@Composable
private fun PdfStatusAndButton(
    isTextFieldFocused: Boolean,
    statusText: String,
    isLoading: Boolean,
    onPdfButtonClick: () -> Unit
) {

    if (isTextFieldFocused) {
        Row {
            Column {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (isLoading) {
                    CircularProgressIndicator()
                }
            }

            Button(
                onClick = onPdfButtonClick,
                modifier = Modifier
                    .size(50.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Pdf",
                    tint = Color.White
                )
            }

        }

    }
}


@Composable
private fun ChatInputRow(
    messageText: TextFieldValue,
    onMessageChange: (TextFieldValue) -> Unit,
    onSendClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = messageText,
            onValueChange = onMessageChange,
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(0.8f),
            interactionSource = interactionSource
        )

        Button(
            onClick = onSendClick,
            enabled = !isLoading,
            modifier = Modifier
                .padding(end = 0.dp)
                .width(90.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Send Message",
                tint = Color.White
            )
        }
    }
}
@Composable
fun MessageCard(message: Message) {
    val isUser = message.sender == "You"
    val isSystem = message.sender.contains("System")
    val isBot = message.sender.contains("Bot")

    val bcolor = when {
        isUser -> MaterialTheme.colorScheme.primary
        isSystem -> MaterialTheme.colorScheme.secondary
        isBot -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.surface
    }

    // Animation for writing indicator
    val dotCount = remember { mutableStateOf(1) }
    if (message.content.isEmpty()) {
        LaunchedEffect(Unit) {
            while (true) {
                dotCount.value = (dotCount.value % 3) + 1
                kotlinx.coroutines.delay(500)
            }
        }
    }
    val fontColor =  MaterialTheme.colorScheme.background
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .padding(4.dp),
            colors = CardDefaults.cardColors(
                containerColor = bcolor // Set background color
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = message.sender,
                    color = MaterialTheme.colorScheme.outline,
                    style = TextStyle(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                if (message.content.isEmpty()) {
                    val dots = ".".repeat(dotCount.value)
                    Text(text = "thinking$dots")
                } else {
                    // Render HTML content in a native TextView
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { context ->
                            android.widget.TextView(context).apply {
                                setText(
                                    android.text.Html.fromHtml(message.content, android.text.Html.FROM_HTML_MODE_LEGACY)
                                )
                                setTextColor(fontColor.toArgb())
                                textSize = 16f
                                // Optional: enable links
                                linksClickable = true
                                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                            }
                        },
                        update = { textView ->
                            textView.setText(
                                android.text.Html.fromHtml(message.content, android.text.Html.FROM_HTML_MODE_LEGACY)
                            )
                        }
                    )
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    val mockChatModel = remember { LlamaModel() } // Create ViewModel instance manually
    val mockEmbeddingModel = remember { LlamaModel() } // Create ViewModel instance manually

    DallamaTheme {
        ChatScreen(chatModel = mockChatModel, embeddingModel = mockEmbeddingModel, pdfTextMap = remember { mutableStateMapOf() }) // Pass the ViewModel to ChatScreen
    }
}
