package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.AppDatabase
import com.example.data.db.PdfHistoryRepository
import com.example.data.model.PdfTool
import com.example.domain.pdf.PdfEngine
import com.example.domain.pdf.SamplePdfGenerator
import com.example.ui.components.InfinityNavBar
import com.example.ui.components.InfinityTopBar
import com.example.ui.components.MainNavTab
import com.example.ui.screens.AiToolsScreen
import com.example.ui.screens.AllToolsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PdfReaderScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ToolExecutionScreen
import com.example.ui.theme.InfinityPdfTheme
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize PDF engine
        PdfEngine.init(this)

        val database = AppDatabase.getDatabase(this)
        val historyRepository = PdfHistoryRepository(database.pdfHistoryDao())
        val sharedPrefs = getSharedPreferences("infinity_pdf_prefs", Context.MODE_PRIVATE)

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkTheme by remember {
                mutableStateOf(sharedPrefs.getBoolean("dark_theme", systemDark))
            }

            fun toggleDarkTheme() {
                val newTheme = !isDarkTheme
                isDarkTheme = newTheme
                sharedPrefs.edit().putBoolean("dark_theme", newTheme).apply()
            }

            var currentNavTab by remember { mutableStateOf(MainNavTab.HOME) }
            var activeTool by remember { mutableStateOf<PdfTool?>(null) }
            var activeReaderFile by remember { mutableStateOf<File?>(null) }
            var preloadedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
            var isShowingSettings by remember { mutableStateOf(false) }

            val historyList by historyRepository.allHistory.collectAsStateWithLifecycle(initialValue = emptyList())

            // Handle system back navigation
            BackHandler(enabled = activeReaderFile != null || activeTool != null || isShowingSettings || currentNavTab != MainNavTab.HOME) {
                when {
                    activeReaderFile != null -> activeReaderFile = null
                    activeTool != null -> {
                        activeTool = null
                        preloadedFiles = emptyList()
                    }
                    isShowingSettings -> isShowingSettings = false
                    currentNavTab != MainNavTab.HOME -> currentNavTab = MainNavTab.HOME
                }
            }

            InfinityPdfTheme(darkTheme = isDarkTheme) {
                if (activeReaderFile != null) {
                    PdfReaderScreen(
                        pdfFile = activeReaderFile!!,
                        onClose = { activeReaderFile = null }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            InfinityTopBar(
                                title = when {
                                    isShowingSettings -> "Settings"
                                    activeTool != null -> activeTool!!.name
                                    currentNavTab == MainNavTab.HOME -> "Infinity PDF"
                                    currentNavTab == MainNavTab.ALL_TOOLS -> "All PDF Tools"
                                    currentNavTab == MainNavTab.SCANNER -> "Document Scanner"
                                    currentNavTab == MainNavTab.AI_TOOLS -> "AI Intelligence"
                                    currentNavTab == MainNavTab.HISTORY -> "Activity History"
                                    else -> "Infinity PDF"
                                },
                                showBackButton = isShowingSettings || activeTool != null,
                                onBackClick = {
                                    if (isShowingSettings) {
                                        isShowingSettings = false
                                    } else if (activeTool != null) {
                                        activeTool = null
                                        preloadedFiles = emptyList()
                                    }
                                },
                                isDarkTheme = isDarkTheme,
                                onToggleDarkTheme = { toggleDarkTheme() },
                                onHistoryClick = {
                                    isShowingSettings = false
                                    activeTool = null
                                    currentNavTab = MainNavTab.HISTORY
                                },
                                onSettingsClick = {
                                    isShowingSettings = true
                                }
                            )
                        },
                        bottomBar = {
                            if (!isShowingSettings && activeTool == null) {
                                InfinityNavBar(
                                    selectedTab = currentNavTab,
                                    onTabSelected = { tab ->
                                        currentNavTab = tab
                                        activeTool = null
                                        isShowingSettings = false
                                    }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (isShowingSettings) {
                                SettingsScreen(
                                    isDarkTheme = isDarkTheme,
                                    onToggleDarkTheme = { toggleDarkTheme() }
                                )
                            } else if (activeTool != null) {
                                if (activeTool!!.id == "scanner") {
                                    ScannerScreen(
                                        historyRepository = historyRepository,
                                        onOpenPdfInReader = { file -> activeReaderFile = file }
                                    )
                                } else if (activeTool!!.id in listOf("chat_pdf", "ai_summarizer", "translate_pdf", "ai_questions")) {
                                    AiToolsScreen(
                                        initialFile = preloadedFiles.firstOrNull(),
                                        onNavigateToSettings = { isShowingSettings = true }
                                    )
                                } else if (activeTool!!.id == "pdf_reader") {
                                    // Direct launch into reader or prompt to pick file
                                    if (preloadedFiles.isNotEmpty()) {
                                        activeReaderFile = preloadedFiles.first()
                                        activeTool = null
                                    } else {
                                        val sample = SamplePdfGenerator.createSamplePdf(this@MainActivity)
                                        activeReaderFile = sample
                                        activeTool = null
                                    }
                                } else {
                                    ToolExecutionScreen(
                                        tool = activeTool!!,
                                        initialFiles = preloadedFiles,
                                        historyRepository = historyRepository,
                                        onOpenPdfInReader = { file -> activeReaderFile = file }
                                    )
                                }
                            } else {
                                when (currentNavTab) {
                                    MainNavTab.HOME -> {
                                        HomeScreen(
                                            onToolClick = { tool ->
                                                activeTool = tool
                                            },
                                            onViewAllToolsClick = {
                                                currentNavTab = MainNavTab.ALL_TOOLS
                                            },
                                            onSampleLoaded = { sample ->
                                                preloadedFiles = listOf(sample)
                                                activeReaderFile = sample
                                            },
                                            recentHistory = historyList
                                        )
                                    }
                                    MainNavTab.ALL_TOOLS -> {
                                        AllToolsScreen(
                                            onToolClick = { tool ->
                                                activeTool = tool
                                            }
                                        )
                                    }
                                    MainNavTab.SCANNER -> {
                                        ScannerScreen(
                                            historyRepository = historyRepository,
                                            onOpenPdfInReader = { file -> activeReaderFile = file }
                                        )
                                    }
                                    MainNavTab.AI_TOOLS -> {
                                        AiToolsScreen(
                                            initialFile = preloadedFiles.firstOrNull(),
                                            onNavigateToSettings = { isShowingSettings = true }
                                        )
                                    }
                                    MainNavTab.HISTORY -> {
                                        HistoryScreen(
                                            historyList = historyList,
                                            historyRepository = historyRepository,
                                            onOpenPdfInReader = { file -> activeReaderFile = file }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
