package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.gemini.GeminiAiService
import com.example.domain.pdf.PdfEngine
import com.example.ui.components.FilePickerBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ChatMessage(
    val sender: String, // "user" or "ai"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun AiToolsScreen(
    initialFile: File? = null,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var selectedPdfFile by remember { mutableStateOf<File?>(initialFile) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Chat, 1: Summarize, 2: Translate, 3: Questions
    var extractedText by remember { mutableStateOf("") }
    var isLoadingText by remember { mutableStateOf(false) }

    // Chat
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    var userMessageInput by remember { mutableStateOf("") }
    var isAiGenerating by remember { mutableStateOf(false) }

    // Single result screens (Summarize, Translate, Questions)
    var summaryResult by remember { mutableStateOf<String?>(null) }
    var translateResult by remember { mutableStateOf<String?>(null) }
    var questionsResult by remember { mutableStateOf<String?>(null) }

    fun loadPdfContent(file: File) {
        selectedPdfFile = file
        isLoadingText = true
        coroutineScope.launch {
            val text = withContext(Dispatchers.IO) {
                PdfEngine.extractText(file)
            }
            extractedText = text
            isLoadingText = false

            // Auto-trigger summarization or analysis
            chatMessages.clear()
            chatMessages.add(
                ChatMessage(
                    sender = "ai",
                    text = "Hello! I have loaded \"${file.name}\". Ask me any question about this document, its data, or key decisions."
                )
            )
        }
    }

    if (initialFile != null && selectedPdfFile == null) {
        loadPdfContent(initialFile)
    }

    fun sendChatMessage() {
        val query = userMessageInput.trim()
        if (query.isBlank()) return
        chatMessages.add(ChatMessage("user", query))
        userMessageInput = ""
        isAiGenerating = true

        coroutineScope.launch {
            val isApiSet = GeminiAiService.isApiKeyConfigured()
            val answer = if (isApiSet) {
                val res = GeminiAiService.generateAiResponse(query, extractedText)
                res.getOrElse { "Error: ${it.localizedMessage}" }
            } else {
                GeminiAiService.analyzeLocally("chat", extractedText, query)
            }
            chatMessages.add(ChatMessage("ai", answer))
            isAiGenerating = false
        }
    }

    fun runSummarizer() {
        if (extractedText.isBlank()) return
        isAiGenerating = true
        coroutineScope.launch {
            val isApiSet = GeminiAiService.isApiKeyConfigured()
            val answer = if (isApiSet) {
                val res = GeminiAiService.generateAiResponse(
                    "Please provide an executive summary of this document with key metrics and bullet points.",
                    extractedText
                )
                res.getOrElse { "Error: ${it.localizedMessage}" }
            } else {
                GeminiAiService.analyzeLocally("summarize", extractedText)
            }
            summaryResult = answer
            isAiGenerating = false
        }
    }

    fun runTranslate() {
        if (extractedText.isBlank()) return
        isAiGenerating = true
        coroutineScope.launch {
            val isApiSet = GeminiAiService.isApiKeyConfigured()
            val answer = if (isApiSet) {
                val res = GeminiAiService.generateAiResponse(
                    "Translate the main sections and summaries of this document into Spanish and French, maintaining layout context.",
                    extractedText
                )
                res.getOrElse { "Error: ${it.localizedMessage}" }
            } else {
                GeminiAiService.analyzeLocally("translate", extractedText)
            }
            translateResult = answer
            isAiGenerating = false
        }
    }

    fun runQuestionGenerator() {
        if (extractedText.isBlank()) return
        isAiGenerating = true
        coroutineScope.launch {
            val isApiSet = GeminiAiService.isApiKeyConfigured()
            val answer = if (isApiSet) {
                val res = GeminiAiService.generateAiResponse(
                    "Generate 5 challenging study and comprehension questions with answers based strictly on this document.",
                    extractedText
                )
                res.getOrElse { "Error: ${it.localizedMessage}" }
            } else {
                GeminiAiService.analyzeLocally("questions", extractedText)
            }
            questionsResult = answer
            isAiGenerating = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("ai_tools_screen")
    ) {
        // Document Selector Header
        if (selectedPdfFile == null) {
            Box(modifier = Modifier.padding(16.dp)) {
                FilePickerBox(
                    title = "Choose PDF for AI Analysis",
                    subtitle = "Select a document to chat, summarize, translate, or generate questions",
                    selectedFiles = emptyList(),
                    onFilesSelected = { files -> files.firstOrNull()?.let { loadPdfContent(it) } },
                    onFileRemoved = {},
                    onSampleLoaded = { sample -> loadPdfContent(sample) }
                )
            }
        } else {
            // Selected Document Top Ribbon
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedPdfFile!!.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        val wordCount = extractedText.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                        Text(
                            text = if (isLoadingText) "Extracting text..." else "$wordCount words indexed • ${if (GeminiAiService.isApiKeyConfigured()) "Gemini 3.5 Flash Active" else "Local Engine"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            selectedPdfFile = null
                            extractedText = ""
                            chatMessages.clear()
                            summaryResult = null
                            translateResult = null
                            questionsResult = null
                        }
                    ) {
                        Text("Change PDF")
                    }
                }
            }

            // AI Feature Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val tabs = listOf("Chat", "Summarize", "Translate", "Questions")
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            if (index == 1 && summaryResult == null) runSummarizer()
                            if (index == 2 && translateResult == null) runTranslate()
                            if (index == 3 && questionsResult == null) runQuestionGenerator()
                        },
                        text = { Text(title) },
                        modifier = Modifier.testTag("ai_tab_${title.lowercase()}")
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // Chat with PDF Tab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(chatMessages) { msg ->
                                val isAi = msg.sender == "ai"
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = if (isAi) Alignment.CenterStart else Alignment.CenterEnd
                                ) {
                                    Card(
                                        shape = RoundedCornerShape(
                                            topStart = 14.dp,
                                            topEnd = 14.dp,
                                            bottomStart = if (isAi) 2.dp else 14.dp,
                                            bottomEnd = if (isAi) 14.dp else 2.dp
                                        ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isAi) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = if (isAi) "Infinity AI" else "You",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isAi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = msg.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isAi) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            if (isAiGenerating) {
                                item {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(8.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Reading PDF and thinking...", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        // Message Input
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = userMessageInput,
                                onValueChange = { userMessageInput = it },
                                placeholder = { Text("Ask about document contents...") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_chat_input"),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { sendChatMessage() },
                                enabled = userMessageInput.isNotBlank() && !isAiGenerating,
                                modifier = Modifier.testTag("ai_chat_send_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                1 -> {
                    // Summarizer
                    SingleAiResultView(
                        title = "Document Summary",
                        result = summaryResult,
                        isLoading = isAiGenerating,
                        onRegenerate = { runSummarizer() },
                        onCopy = {
                            summaryResult?.let {
                                clipboardManager.setText(AnnotatedString(it))
                                Toast.makeText(context, "Summary copied!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                2 -> {
                    // Translate
                    SingleAiResultView(
                        title = "Document Translation",
                        result = translateResult,
                        isLoading = isAiGenerating,
                        onRegenerate = { runTranslate() },
                        onCopy = {
                            translateResult?.let {
                                clipboardManager.setText(AnnotatedString(it))
                                Toast.makeText(context, "Translation copied!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                3 -> {
                    // Questions
                    SingleAiResultView(
                        title = "Comprehension Questions",
                        result = questionsResult,
                        isLoading = isAiGenerating,
                        onRegenerate = { runQuestionGenerator() },
                        onCopy = {
                            questionsResult?.let {
                                clipboardManager.setText(AnnotatedString(it))
                                Toast.makeText(context, "Questions copied!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SingleAiResultView(
    title: String,
    result: String?,
    isLoading: Boolean,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row {
                IconButton(onClick = onCopy, enabled = result != null) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }
                Button(onClick = onRegenerate, enabled = !isLoading) {
                    Text("Regenerate")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (result != null) {
                    LazyColumn {
                        item {
                            Text(
                                text = result,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Tap Regenerate to begin AI processing.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}
