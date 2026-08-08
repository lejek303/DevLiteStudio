package com.devlite.studio.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devlite.studio.data.AiAssistantRepository
import com.devlite.studio.data.ExecutionEngine
import com.devlite.studio.data.LanguageManager
import com.devlite.studio.data.SecurePreferences
import com.devlite.studio.model.AiAction
import com.devlite.studio.model.AiProvider
import com.devlite.studio.model.EditorTab
import com.devlite.studio.model.ExecutionLine
import com.devlite.studio.model.FileNode
import com.devlite.studio.model.Language
import com.devlite.studio.model.SettingsSnapshot
import com.devlite.studio.model.StreamKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class EditorUiState(
    val tabs: List<EditorTab> = emptyList(),
    val activeTabId: String? = null,
    val rootNode: FileNode.Directory? = null,
    val terminalLines: List<ExecutionLine> = emptyList(),
    val aiPanelText: String = "",
    val isAiStreaming: Boolean = false,
    val isRunning: Boolean = false,
    val htmlPreviewHtml: String? = null
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val securePreferences = SecurePreferences(application)
    private val languageManager = LanguageManager(application)
    private val executionEngine = ExecutionEngine(application, languageManager)
    private val aiRepository = AiAssistantRepository(securePreferences)

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    val toolchains = languageManager.toolchains

    fun onFolderPicked(context: Context, treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val doc = DocumentFile.fromTreeUri(context, treeUri) ?: return
        _uiState.update { it.copy(rootNode = buildTree(doc)) }
    }

    private fun buildTree(doc: DocumentFile): FileNode.Directory {
        val children = doc.listFiles().map { child ->
            if (child.isDirectory) buildTree(child)
            else FileNode.FileLeaf(child.name ?: "unnamed", child.uri.toString())
        }
        return FileNode.Directory(doc.name ?: "root", doc.uri.toString(), children)
    }

    fun openFile(context: Context, uri: Uri, name: String) {
        val existing = _uiState.value.tabs.firstOrNull { it.uriString == uri.toString() }
        if (existing != null) {
            _uiState.update { it.copy(activeTabId = existing.id) }
            return
        }
        val content = context.contentResolver.openInputStream(uri)?.use {
            it.bufferedReader().readText()
        }.orEmpty()

        val tab = EditorTab(
            id = UUID.randomUUID().toString(),
            fileName = name,
            uriString = uri.toString(),
            content = content,
            language = Language.fromExtension(name.substringAfterLast('.', ""))
        )
        _uiState.update { it.copy(tabs = it.tabs + tab, activeTabId = tab.id) }
    }

    fun updateActiveTabContent(newContent: String) {
        _uiState.update { state ->
            state.copy(tabs = state.tabs.map { tab ->
                if (tab.id == state.activeTabId) tab.copy(content = newContent, isDirty = true) else tab
            })
        }
    }

    fun selectTab(tabId: String) {
        _uiState.update { it.copy(activeTabId = tabId) }
    }

    fun closeTab(context: Context, tabId: String, save: Boolean = true) {
        if (save) saveTab(context, tabId)
        _uiState.update { state ->
            val remaining = state.tabs.filterNot { it.id == tabId }
            val newActive = if (state.activeTabId == tabId) remaining.lastOrNull()?.id else state.activeTabId
            state.copy(tabs = remaining, activeTabId = newActive)
        }
    }

    fun saveActiveTab(context: Context) {
        _uiState.value.activeTabId?.let { saveTab(context, it) }
    }

    private fun saveTab(context: Context, tabId: String) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        val uriString = tab.uriString ?: return
        try {
            context.contentResolver.openOutputStream(Uri.parse(uriString), "wt")?.use { out ->
                out.write(tab.content.toByteArray())
            }
            _uiState.update { state ->
                state.copy(tabs = state.tabs.map { if (it.id == tabId) it.copy(isDirty = false) else it })
            }
        } catch (_: Throwable) {
            appendTerminal(ExecutionLine(StreamKind.SYSTEM, "Failed to save ${tab.fileName}"))
        }
    }

    fun runActiveFile(context: Context) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == _uiState.value.activeTabId } ?: return
        saveTab(context, tab.id)

        if (tab.language == Language.HTML || tab.language == Language.CSS) {
            val html = if (tab.language == Language.HTML) {
                tab.content
            } else {
                "<html><head><style>${tab.content}</style></head>" +
                    "<body><p>CSS preview — open the matching HTML file to see it applied to real markup.</p></body></html>"
            }
            _uiState.update { it.copy(htmlPreviewHtml = html) }
            return
        }

        val cacheFile = File(getApplication<Application>().cacheDir, tab.fileName).apply {
            writeText(tab.content)
        }
        _uiState.update { it.copy(isRunning = true, terminalLines = emptyList()) }
        viewModelScope.launch {
            executionEngine.run(cacheFile, tab.language).collect { line -> appendTerminal(line) }
            _uiState.update { it.copy(isRunning = false) }
        }
    }

    fun dismissHtmlPreview() {
        _uiState.update { it.copy(htmlPreviewHtml = null) }
    }

    fun sendTerminalInput(text: String) {
        executionEngine.sendInput(text)
        appendTerminal(ExecutionLine(StreamKind.SYSTEM, "> $text"))
    }

    fun stopExecution() {
        executionEngine.stop()
        _uiState.update { it.copy(isRunning = false) }
    }

    fun runAiAction(action: AiAction, selectedText: String) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == _uiState.value.activeTabId }
        val code = selectedText.ifBlank { tab?.content.orEmpty() }
        _uiState.update { it.copy(aiPanelText = "", isAiStreaming = true) }
        viewModelScope.launch {
            aiRepository.runAction(action, code, tab?.language?.displayName ?: "code").collect { chunk ->
                _uiState.update { it.copy(aiPanelText = it.aiPanelText + chunk) }
            }
            _uiState.update { it.copy(isAiStreaming = false) }
        }
    }

    fun downloadToolchain(toolchainId: String, url: String) {
        viewModelScope.launch { languageManager.downloadAndInstall(toolchainId, url) }
    }

    // --- Settings ---

    fun currentSettings(): SettingsSnapshot = SettingsSnapshot(
        anthropicKey = securePreferences.anthropicApiKey.orEmpty(),
        openAiKey = securePreferences.openAiApiKey.orEmpty(),
        ollamaUrl = securePreferences.ollamaBaseUrl.orEmpty(),
        provider = securePreferences.selectedProvider,
        model = securePreferences.selectedModel
    )

    fun saveSettings(anthropicKey: String, openAiKey: String, ollamaUrl: String, provider: AiProvider, model: String) {
        securePreferences.anthropicApiKey = anthropicKey.ifBlank { null }
        securePreferences.openAiApiKey = openAiKey.ifBlank { null }
        securePreferences.ollamaBaseUrl = ollamaUrl
        securePreferences.selectedProvider = provider
        securePreferences.selectedModel = model
    }

    private fun appendTerminal(line: ExecutionLine) {
        _uiState.update { it.copy(terminalLines = it.terminalLines + line) }
    }
}
