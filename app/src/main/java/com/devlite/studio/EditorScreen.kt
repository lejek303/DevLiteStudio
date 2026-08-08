package com.devlite.studio.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devlite.studio.model.AiAction
import com.devlite.studio.model.AiProvider
import com.devlite.studio.model.ExecutionLine
import com.devlite.studio.model.FileNode
import com.devlite.studio.model.Language
import com.devlite.studio.model.SettingsSnapshot
import com.devlite.studio.model.StreamKind
import com.devlite.studio.model.ToolchainInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val toolchains by viewModel.toolchains.collectAsState()
    val activeTab = uiState.tabs.firstOrNull { it.id == uiState.activeTabId }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showAiMenu by remember { mutableStateOf(false) }
    var showTerminal by remember { mutableStateOf(false) }
    var showToolchainManager by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var terminalInput by remember { mutableStateOf("") }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> uri?.let { viewModel.onFolderPicked(context, it) } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Files", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Open folder")
                    }
                }
                HorizontalDivider()
                val root = uiState.rootNode
                if (root == null) {
                    Text(
                        "Open a folder to browse files",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            FileTreeNodeRow(node = root, depth = 0) { leaf ->
                                viewModel.openFile(context, Uri.parse(leaf.uriString), leaf.name)
                                scope.launch { drawerState.close() }
                            }
                        }
                    }
                }
                HorizontalDivider()
                TextButton(onClick = { showToolchainManager = true }, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.Default.Build, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Language & Toolchain Manager")
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text("DevLite Studio") },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.saveActiveTab(context) }) {
                                Icon(Icons.Default.Save, contentDescription = "Save")
                            }
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "AI settings")
                            }
                        }
                    )
                    if (uiState.tabs.isNotEmpty()) {
                        LazyRow(modifier = Modifier.fillMaxWidth()) {
                            items(uiState.tabs, key = { it.id }) { tab ->
                                TabChip(
                                    label = tab.fileName + if (tab.isDirty) " •" else "",
                                    selected = tab.id == uiState.activeTabId,
                                    onClick = { viewModel.selectTab(tab.id) },
                                    onClose = { viewModel.closeTab(context, tab.id) }
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End) {
                    Box {
                        SmallFloatingActionButton(onClick = { showAiMenu = true }) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "AI Assistant")
                        }
                        DropdownMenu(expanded = showAiMenu, onDismissRequest = { showAiMenu = false }) {
                            AiAction.entries.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    onClick = {
                                        showAiMenu = false
                                        viewModel.runAiAction(action, "")
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    FloatingActionButton(onClick = {
                        val isPreviewLanguage = activeTab?.language == Language.HTML || activeTab?.language == Language.CSS
                        if (!isPreviewLanguage) showTerminal = true
                        viewModel.runActiveFile(context)
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run")
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (activeTab == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Open a file to start editing", style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        CodeEditorView(
                            tabId = activeTab.id,
                            content = activeTab.content,
                            language = activeTab.language,
                            onContentChange = { viewModel.updateActiveTabContent(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                if (uiState.aiPanelText.isNotEmpty() || uiState.isAiStreaming) {
                    AiResponsePanel(text = uiState.aiPanelText, isStreaming = uiState.isAiStreaming)
                }

                if (showTerminal) {
                    TerminalPanel(
                        lines = uiState.terminalLines,
                        isRunning = uiState.isRunning,
                        input = terminalInput,
                        onInputChange = { terminalInput = it },
                        onSend = {
                            viewModel.sendTerminalInput(terminalInput)
                            terminalInput = ""
                        },
                        onStop = { viewModel.stopExecution() },
                        onClose = { showTerminal = false }
                    )
                }
            }
        }
    }

    if (showToolchainManager) {
        ToolchainManagerDialog(
            toolchains = toolchains.values.toList(),
            onDownload = { id, url -> viewModel.downloadToolchain(id, url) },
            onDismiss = { showToolchainManager = false }
        )
    }

    if (showSettings) {
        SettingsDialog(
            snapshot = viewModel.currentSettings(),
            onSave = { anthropic, openAi, ollama, provider, model ->
                viewModel.saveSettings(anthropic, openAi, ollama, provider, model)
            },
            onDismiss = { showSettings = false }
        )
    }

    uiState.htmlPreviewHtml?.let { html ->
        HtmlPreviewDialog(html = html, onDismiss = { viewModel.dismissHtmlPreview() })
    }
}

@Composable
private fun FileTreeNodeRow(node: FileNode, depth: Int, onOpenFile: (FileNode.FileLeaf) -> Unit) {
    when (node) {
        is FileNode.FileLeaf -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenFile(node) }
                    .padding(start = (16 + depth * 12).dp, top = 6.dp, bottom = 6.dp)
            ) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(node.name)
            }
        }
        is FileNode.Directory -> {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (16 + depth * 12).dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(node.name, style = MaterialTheme.typography.bodyMedium)
                }
                node.children.forEach { child -> FileTreeNodeRow(node = child, depth = depth + 1, onOpenFile = onOpenFile) }
            }
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onClose, modifier = Modifier.size(18.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close tab")
            }
        }
    }
}

@Composable
private fun AiResponsePanel(text: String, isStreaming: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 220.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("AI Assistant", style = MaterialTheme.typography.labelLarge)
                if (isStreaming) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TerminalPanel(
    lines: List<ExecutionLine>,
    isRunning: Boolean,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth().height(240.dp), color = Color(0xFF0A0C12)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Console", color = Color.White, style = MaterialTheme.typography.labelLarge)
                Row {
                    if (isRunning) {
                        IconButton(onClick = onStop) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close console", tint = Color.White)
                    }
                }
            }
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(lines) { line ->
                    val color = when (line.stream) {
                        StreamKind.STDERR -> Color(0xFFFF6E6E)
                        StreamKind.SYSTEM -> Color(0xFF89DDFF)
                        StreamKind.STDOUT -> Color(0xFFEEFFFF)
                    }
                    Text(line.text, color = color, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type input for the running program…") },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color(0xFF14161F),
                        focusedContainerColor = Color(0xFF14161F)
                    )
                )
                IconButton(onClick = onSend) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolchainManagerDialog(
    toolchains: List<ToolchainInfo>,
    onDownload: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var customUrl by remember { mutableStateOf("") }
    var customId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Language & Toolchain Manager") },
        text = {
            Column {
                Text(
                    "Built-in entries need a source archive URL before they can be installed — add one below or point a custom package at your own mirror.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(toolchains) { info ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(info.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    info.state.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(
                                enabled = info.defaultDownloadUrl.isNotBlank(),
                                onClick = { onDownload(info.id, info.defaultDownloadUrl) }
                            ) { Text("Install") }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Add a custom package (GitHub / mirror archive URL)", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = customId, onValueChange = { customId = it },
                    label = { Text("Toolchain ID") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = customUrl, onValueChange = { customUrl = it },
                    label = { Text("Archive URL (.zip)") }, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (customId.isNotBlank() && customUrl.isNotBlank()) onDownload(customId, customUrl)
                onDismiss()
            }) { Text("Download & Close") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun SettingsDialog(
    snapshot: SettingsSnapshot,
    onSave: (String, String, String, AiProvider, String) -> Unit,
    onDismiss: () -> Unit
) {
    var anthropicKey by remember { mutableStateOf(snapshot.anthropicKey) }
    var openAiKey by remember { mutableStateOf(snapshot.openAiKey) }
    var ollamaUrl by remember { mutableStateOf(snapshot.ollamaUrl) }
    var provider by remember { mutableStateOf(snapshot.provider) }
    var model by remember { mutableStateOf(snapshot.model) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI Assistant Settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Provider", style = MaterialTheme.typography.labelLarge)
                Row {
                    AiProvider.entries.forEach { p ->
                        FilterChip(
                            selected = provider == p,
                            onClick = { provider = p },
                            label = { Text(p.name) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = anthropicKey, onValueChange = { anthropicKey = it },
                    label = { Text("Anthropic API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = openAiKey, onValueChange = { openAiKey = it },
                    label = { Text("OpenAI API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ollamaUrl, onValueChange = { ollamaUrl = it },
                    label = { Text("Ollama base URL") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("Model name") }, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Keys are stored on-device with EncryptedSharedPreferences and are only sent directly to the provider you select.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(anthropicKey, openAiKey, ollamaUrl, provider, model)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun HtmlPreviewDialog(html: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth().height(480.dp)) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Preview", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close preview")
                    }
                }
                AndroidView(
                    factory = { ctx -> android.webkit.WebView(ctx) },
                    update = { webView -> webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null) },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}
