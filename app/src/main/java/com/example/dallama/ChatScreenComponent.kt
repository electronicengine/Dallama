// File: app/src/main/java/com/example/dallama/ChatScreenComponent.kt
package com.llama.dallama

import android.app.Activity
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.dallama.AppDatabase
//import com.example.dallama.AppDatabase
//import com.example.dallama.PdfChunkEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// Chat screen content
@Composable
fun ChatScreenContent(
    chatModel: LlamaModel,
    embeddingModel: LlamaModel,
    pdfTextMap: MutableMap<String, Pair<List<String>, List<FloatArray>>>
) {
    val context = LocalContext.current

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        ChatScreen(
            modifier = Modifier
                .padding(innerPadding)
                .padding(end = 0.dp)
                .fillMaxSize(),
            chatModel = chatModel,
            embeddingModel = embeddingModel,
            pdfTextMap = pdfTextMap
        )
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
                top2.forEachIndexed { index, part ->
                    embeddingModel.addToMessages("Similar Section $index: ${part.second}", "Embedding Model System")
                }
                Thread.sleep(300) // Add delay between messages

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

            Text(
                text = "Add PDF",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .align(Alignment.CenterVertically),
                color = MaterialTheme.colorScheme.onBackground
            )
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
