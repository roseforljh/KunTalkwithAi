package com.example.everytalk.statecontroler

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.everytalk.data.DataClass.ApiConfig
import com.example.everytalk.data.DataClass.Message
import com.example.everytalk.data.DataClass.Sender
import com.example.everytalk.data.DataClass.WebSearchResult
import com.example.everytalk.data.local.SharedPreferencesDataSource
import com.example.everytalk.data.network.ApiClient
import android.net.Uri
import coil3.ImageLoader
import com.example.everytalk.model.SelectedMediaItem
import com.example.everytalk.ui.screens.MainScreen.chat.ChatListItem
import com.example.everytalk.ui.screens.viewmodel.ConfigManager
import com.example.everytalk.ui.screens.viewmodel.DataPersistenceManager
import com.example.everytalk.ui.screens.viewmodel.HistoryManager
import com.example.everytalk.util.parseMarkdownToBlocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import androidx.annotation.Keep

class AppViewModel(
    application: Application,
    private val dataSource: SharedPreferencesDataSource
) : AndroidViewModel(application) {

    @Keep
    private data class ExportedSettings(
        val apiConfigs: List<ApiConfig>,
        val customProviders: Set<String>
    )

    internal val stateHolder = ViewModelStateHolder()
    private val imageLoader = ImageLoader.Builder(application.applicationContext).build()
    private val persistenceManager =
        DataPersistenceManager(application.applicationContext, dataSource, stateHolder, viewModelScope, imageLoader)

    private val historyManager: HistoryManager =
        HistoryManager(
            stateHolder,
            persistenceManager,
            ::areMessageListsEffectivelyEqual
        )

    private val apiHandler: ApiHandler by lazy {
        ApiHandler(
            stateHolder,
            viewModelScope,
            historyManager,
            ::onAiMessageFullTextChanged
        )
    }
    private val configManager: ConfigManager by lazy {
        ConfigManager(
            stateHolder,
            persistenceManager,
            apiHandler,
            viewModelScope
        )
    }

    private val messageSender: MessageSender by lazy {
        MessageSender(
            application = getApplication(),
            viewModelScope = viewModelScope,
            stateHolder = stateHolder,
            apiHandler = apiHandler,
            historyManager = historyManager,
            showSnackbar = ::showSnackbar,
            triggerScrollToBottom = ::triggerScrollToBottom
        )
    }


    private val _markdownChunkToAppendFlow =
        MutableSharedFlow<Pair<String, Pair<String, String>>>(replay = 0, extraBufferCapacity = 128)
    val markdownChunkToAppendFlow: SharedFlow<Pair<String, Pair<String, String>>> =
        _markdownChunkToAppendFlow.asSharedFlow()

    val drawerState: DrawerState get() = stateHolder.drawerState
    val text: StateFlow<String> get() = stateHolder._text.asStateFlow()
    val messages: SnapshotStateList<Message> get() = stateHolder.messages
    val historicalConversations: StateFlow<List<List<Message>>> get() = stateHolder._historicalConversations.asStateFlow()
    val loadedHistoryIndex: StateFlow<Int?> get() = stateHolder._loadedHistoryIndex.asStateFlow()
    val apiConfigs: StateFlow<List<ApiConfig>> get() = stateHolder._apiConfigs.asStateFlow()
    val selectedApiConfig: StateFlow<ApiConfig?> get() = stateHolder._selectedApiConfig.asStateFlow()
    val isApiCalling: StateFlow<Boolean> get() = stateHolder._isApiCalling.asStateFlow()
    val currentStreamingAiMessageId: StateFlow<String?> get() = stateHolder._currentStreamingAiMessageId.asStateFlow()
    val reasoningCompleteMap: SnapshotStateMap<String, Boolean> get() = stateHolder.reasoningCompleteMap
    val expandedReasoningStates: SnapshotStateMap<String, Boolean> get() = stateHolder.expandedReasoningStates
    val snackbarMessage: SharedFlow<String> get() = stateHolder._snackbarMessage.asSharedFlow()
    val scrollToBottomEvent: SharedFlow<Unit> get() = stateHolder._scrollToBottomEvent.asSharedFlow()
    val selectedMediaItems: SnapshotStateList<SelectedMediaItem> get() = stateHolder.selectedMediaItems

    private val _exportRequest = MutableSharedFlow<Pair<String, String>>()
    val exportRequest: SharedFlow<Pair<String, String>> = _exportRequest.asSharedFlow()

    private val _showEditDialog = MutableStateFlow(false)
    val showEditDialog: StateFlow<Boolean> = _showEditDialog.asStateFlow()
    private val _editingMessageId = MutableStateFlow<String?>(null)
    val editDialogInputText: StateFlow<String> get() = stateHolder._editDialogInputText.asStateFlow()
    private val _showRenameDialogState = MutableStateFlow(false)
    val showRenameDialogState: StateFlow<Boolean> = _showRenameDialogState.asStateFlow()
    private val _renamingIndexState = MutableStateFlow<Int?>(null)
    val renamingIndexState: StateFlow<Int?> = _renamingIndexState.asStateFlow()
    val renameInputText: StateFlow<String> get() = stateHolder._renameInputText.asStateFlow()
    private val _isSearchActiveInDrawer = MutableStateFlow(false)
    val isSearchActiveInDrawer: StateFlow<Boolean> = _isSearchActiveInDrawer.asStateFlow()
    private val _searchQueryInDrawer = MutableStateFlow("")
    val searchQueryInDrawer: StateFlow<String> = _searchQueryInDrawer.asStateFlow()

    private val _customProviders = MutableStateFlow<Set<String>>(emptySet())

    private val predefinedPlatformsList = listOf(
        "openai compatible",
        "google",
        "硅基流动",
        "阿里云百炼",
        "火山引擎",
        "深度求索",
        "OpenRouter"
    )

    val allProviders: StateFlow<List<String>> = combine(
        _customProviders
    ) { customsArray: Array<Set<String>> ->
        val customs = customsArray[0]
        val combinedList = (predefinedPlatformsList + customs.toList()).distinct()
        val predefinedOrderMap = predefinedPlatformsList.withIndex()
            .associate { it.value.lowercase().trim() to it.index }
        combinedList.sortedWith(compareBy<String> { platform ->
            predefinedOrderMap[platform.lowercase().trim()]
                ?: (predefinedPlatformsList.size + customs.indexOfFirst {
                    it.equals(
                        platform,
                        ignoreCase = true
                    )
                }
                    .let { if (it == -1) Int.MAX_VALUE else it })
        }.thenBy { it })
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        predefinedPlatformsList
    )
    val isWebSearchEnabled: StateFlow<Boolean> get() = stateHolder._isWebSearchEnabled.asStateFlow()
    val showSourcesDialog: StateFlow<Boolean> get() = stateHolder._showSourcesDialog.asStateFlow()
    val sourcesForDialog: StateFlow<List<WebSearchResult>> get() = stateHolder._sourcesForDialog.asStateFlow()

   private val _showSelectableTextDialog = MutableStateFlow(false)
   val showSelectableTextDialog: StateFlow<Boolean> = _showSelectableTextDialog.asStateFlow()
   private val _textForSelectionDialog = MutableStateFlow("")
   val textForSelectionDialog: StateFlow<String> = _textForSelectionDialog.asStateFlow()

   val chatListItems: StateFlow<List<ChatListItem>> = combine(
       snapshotFlow { messages.toList() },
       isApiCalling,
       currentStreamingAiMessageId
   ) { messages, isApiCalling, currentStreamingAiMessageId ->
       messages.flatMap { message ->
           when {
               message.sender == Sender.User -> {
                   listOf(ChatListItem.UserMessage(message))
               }
               message.isError -> {
                   listOf(ChatListItem.ErrorMessage(message))
               }
               message.sender == Sender.AI -> {
                   val showLoading = isApiCalling &&
                           message.id == currentStreamingAiMessageId &&
                           message.text.isBlank() &&
                           message.reasoning.isNullOrBlank() &&
                           !message.contentStarted

                   if (showLoading) {
                       listOf(ChatListItem.LoadingIndicator(message.id))
                   } else {
                       val reasoningItem = if (!message.reasoning.isNullOrBlank()) {
                           listOf(ChatListItem.AiMessageReasoning(message))
                       } else {
                           emptyList()
                       }

                       val blocks = parseMarkdownToBlocks(message.text)
                       val hasReasoning = reasoningItem.isNotEmpty()
                       val blockItems = blocks.mapIndexed { index, block ->
                           ChatListItem.AiMessageBlock(
                               messageId = message.id,
                               block = block,
                               blockIndex = index,
                               isFirstBlock = index == 0,
                               isLastBlock = index == blocks.size - 1,
                               hasReasoning = hasReasoning
                           )
                       }

                       val footerItem = if (!message.webSearchResults.isNullOrEmpty() && !(isApiCalling && message.id == currentStreamingAiMessageId)) {
                           listOf(ChatListItem.AiMessageFooter(message))
                       } else {
                           emptyList()
                       }

                       reasoningItem + blockItems + footerItem
                   }
               }
               else -> emptyList()
           }
       }
   }.flowOn(Dispatchers.Default)
       .stateIn(
           scope = viewModelScope,
           started = SharingStarted.WhileSubscribed(5000),
           initialValue = emptyList()
       )

  init {
       viewModelScope.launch(Dispatchers.IO) {
           _customProviders.value = dataSource.loadCustomProviders()
       }

       persistenceManager.loadInitialData(loadLastChat = false) { initialConfigPresent, initialHistoryPresent ->
            if (!initialConfigPresent) {
                viewModelScope.launch { }
            }
        }


        viewModelScope.launch(Dispatchers.IO) {
            apiHandler
            configManager
            messageSender
        }
    }


    private fun areMessageListsEffectivelyEqual(
        list1: List<Message>?,
        list2: List<Message>?
    ): Boolean {
        if (list1 == null && list2 == null) return true
        if (list1 == null || list2 == null) return false
        val filteredList1 = filterMessagesForComparison(list1)
        val filteredList2 = filterMessagesForComparison(list2)
        if (filteredList1.size != filteredList2.size) return false
        for (i in filteredList1.indices) {
            val msg1 = filteredList1[i]
            val msg2 = filteredList2[i]
            if (msg1.id != msg2.id || msg1.sender != msg2.sender || msg1.text.trim() != msg2.text.trim() ||
                msg1.reasoning?.trim() != msg2.reasoning?.trim() || msg1.isError != msg2.isError
            ) return false
        }
        return true
    }

    private fun filterMessagesForComparison(messagesToFilter: List<Message>): List<Message> {
        return messagesToFilter.filter { msg ->
            (msg.sender != Sender.System || msg.isPlaceholderName) &&
                    (msg.sender == Sender.User ||
                            (msg.sender == Sender.AI && (msg.contentStarted || msg.text.isNotBlank() || !msg.reasoning.isNullOrBlank())) ||
                            (msg.sender == Sender.System && msg.isPlaceholderName) ||
                            msg.isError)
        }.toList()
    }

    fun toggleWebSearchMode(enabled: Boolean) {
        stateHolder._isWebSearchEnabled.value = enabled
    }

    fun addProvider(providerName: String) {
        val trimmedName = providerName.trim()
        if (trimmedName.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                val currentCustomProviders = _customProviders.value.toMutableSet()

                if (predefinedPlatformsList.any { it.equals(trimmedName, ignoreCase = true) }) {
                    withContext(Dispatchers.Main) { showSnackbar("平台名称 '$trimmedName' 是预设名称或已存在，无法添加。") }
                    return@launch
                }
                if (currentCustomProviders.any { it.equals(trimmedName, ignoreCase = true) }) {
                    withContext(Dispatchers.Main) { showSnackbar("模型平台 '$trimmedName' 已存在") }
                    return@launch
                }
                currentCustomProviders.add(trimmedName)
                _customProviders.value = currentCustomProviders.toSet()
                dataSource.saveCustomProviders(currentCustomProviders.toSet())
                withContext(Dispatchers.Main) { showSnackbar("模型平台 '$trimmedName' 已添加") }
            }
        } else {
            showSnackbar("平台名称不能为空")
        }
    }

    fun deleteProvider(providerName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmedProviderName = providerName.trim()

            if (predefinedPlatformsList.any { it.equals(trimmedProviderName, ignoreCase = true) }) {
                withContext(Dispatchers.Main) {
                    showSnackbar("预设平台 '$trimmedProviderName' 不可删除。")
                }
                return@launch
            }

            val currentCustomProviders = _customProviders.value.toMutableSet()
            val removed = currentCustomProviders.removeIf {
                it.equals(
                    trimmedProviderName,
                    ignoreCase = true
                )
            }

            if (removed) {
                _customProviders.value = currentCustomProviders.toSet()
                dataSource.saveCustomProviders(currentCustomProviders.toSet())

                val configsToDelete = stateHolder._apiConfigs.value.filter {
                    it.provider.equals(trimmedProviderName, ignoreCase = true)
                }
                configsToDelete.forEach { config ->
                    configManager.deleteConfig(config)
                }
                withContext(Dispatchers.Main) {
                    showSnackbar("模型平台 '$trimmedProviderName' 已删除")
                }
            } else {
                withContext(Dispatchers.Main) {
                    showSnackbar("未能删除模型平台 '$trimmedProviderName'，可能它不是一个自定义平台。")
                }
            }
        }
    }

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            stateHolder._snackbarMessage.emit(message)
        }
    }

    fun setSearchActiveInDrawer(isActive: Boolean) {
        _isSearchActiveInDrawer.value = isActive
        if (!isActive) _searchQueryInDrawer.value = ""
    }

    fun onDrawerSearchQueryChange(query: String) {
        _searchQueryInDrawer.value = query
    }

    fun onTextChange(newText: String) {
        stateHolder._text.value = newText
    }

    fun onSendMessage(
        messageText: String,
        isFromRegeneration: Boolean = false,
        attachments: List<SelectedMediaItem> = emptyList()
    ) {
        messageSender.sendMessage(messageText, isFromRegeneration, attachments)
    }

    fun addMediaItem(item: SelectedMediaItem) {
        stateHolder.selectedMediaItems.add(item)
    }

    fun removeMediaItemAtIndex(index: Int) {
        if (index >= 0 && index < stateHolder.selectedMediaItems.size) {
            stateHolder.selectedMediaItems.removeAt(index)
        }
    }

    fun clearMediaItems() {
        stateHolder.clearSelectedMedia()
    }
 
     fun onEditDialogTextChanged(newText: String) {
         stateHolder._editDialogInputText.value = newText
    }

    fun requestEditMessage(message: Message) {
        if (message.sender == Sender.User) {
            _editingMessageId.value = message.id
            stateHolder._editDialogInputText.value = message.text
            _showEditDialog.value = true
        }
    }

    fun confirmMessageEdit() {
        val messageIdToEdit = _editingMessageId.value ?: return
        val updatedText = stateHolder._editDialogInputText.value.trim()
        viewModelScope.launch {
            val messageIndex = stateHolder.messages.indexOfFirst { it.id == messageIdToEdit }
            if (messageIndex != -1) {
                val originalMessage = stateHolder.messages[messageIndex]
                if (originalMessage.text != updatedText) {
                    val updatedMessage = originalMessage.copy(
                        text = updatedText,
                        timestamp = System.currentTimeMillis()
                    )
                    stateHolder.messages[messageIndex] = updatedMessage
                    if (stateHolder.messageAnimationStates[updatedMessage.id] != true) {
                        stateHolder.messageAnimationStates[updatedMessage.id] = true
                    }
                    viewModelScope.launch { historyManager.saveCurrentChatToHistoryIfNeeded(forceSave = true) }
                }
            }
            withContext(Dispatchers.Main.immediate) { dismissEditDialog() }
        }
    }

    fun dismissEditDialog() {
        _showEditDialog.value = false
        _editingMessageId.value = null
        stateHolder._editDialogInputText.value = ""
    }

    fun regenerateAiResponse(message: Message) {
        val messageToRegenerateFrom = if (message.sender == Sender.AI) {
            val aiMessageIndex = messages.indexOf(message)
            if (aiMessageIndex > 0) {
                messages.subList(0, aiMessageIndex).findLast { it.sender == Sender.User }
            } else {
                null
            }
        } else {
            message
        }

        if (messageToRegenerateFrom == null || messageToRegenerateFrom.sender != Sender.User) {
            showSnackbar("无法找到对应的用户消息来重新生成回答"); return
        }

        if (stateHolder._selectedApiConfig.value == null) {
            showSnackbar("请先选择 API 配置"); return
        }

        val originalUserMessageText = messageToRegenerateFrom.text
        val originalUserMessageId = messageToRegenerateFrom.id

        val originalAttachments = messageToRegenerateFrom.attachments?.mapNotNull {
            // We need to create new instances with new UUIDs because the underlying LazyColumn uses the ID as a key.
            // If we reuse the same ID, Compose might not recompose the item correctly.
            when (it) {
                is SelectedMediaItem.ImageFromUri -> it.copy(id = UUID.randomUUID().toString())
                is SelectedMediaItem.GenericFile -> it.copy(id = UUID.randomUUID().toString())
                is SelectedMediaItem.ImageFromBitmap -> it.copy(id = UUID.randomUUID().toString())
            }
        } ?: emptyList()

        viewModelScope.launch {
            val success = withContext(Dispatchers.Default) {
                val userMessageIndex =
                    stateHolder.messages.indexOfFirst { it.id == originalUserMessageId }
                if (userMessageIndex == -1) {
                    withContext(Dispatchers.Main) {
                        showSnackbar("无法重新生成：原始用户消息在当前列表中未找到。")
                    }
                    return@withContext false
                }

                val messagesToRemove = mutableListOf<Message>()
                var currentIndexToInspect = userMessageIndex + 1
                while (currentIndexToInspect < stateHolder.messages.size) {
                    val message = stateHolder.messages[currentIndexToInspect]
                    if (message.sender == Sender.AI) {
                        messagesToRemove.add(message)
                        currentIndexToInspect++
                    } else {
                        break
                    }
                }

                withContext(Dispatchers.Main.immediate) {
                    messagesToRemove.forEach { aiMessageToRemove ->
                        if (stateHolder._currentStreamingAiMessageId.value == aiMessageToRemove.id) {
                            apiHandler.cancelCurrentApiJob(
                                "为消息 '${originalUserMessageId.take(4)}' 重新生成回答，取消旧AI流",
                                isNewMessageSend = true
                            )
                        }
                        stateHolder.reasoningCompleteMap.remove(aiMessageToRemove.id)
                        stateHolder.expandedReasoningStates.remove(aiMessageToRemove.id)
                        stateHolder.messageAnimationStates.remove(aiMessageToRemove.id)
                        stateHolder.messages.remove(aiMessageToRemove)
                    }

                    val messageToDelete = stateHolder.messages.getOrNull(userMessageIndex)
                    if (messageToDelete?.id == originalUserMessageId) {
                        stateHolder.messageAnimationStates.remove(originalUserMessageId)
                        stateHolder.messages.removeAt(userMessageIndex)
                    }
                }
                true
            }

            if (success) {
                viewModelScope.launch { historyManager.saveCurrentChatToHistoryIfNeeded(forceSave = true) }
                onSendMessage(
                    messageText = originalUserMessageText,
                    isFromRegeneration = true,
                    attachments = originalAttachments
                )
            }
        }
    }

    fun triggerScrollToBottom() {
        viewModelScope.launch { stateHolder._scrollToBottomEvent.tryEmit(Unit) }
    }

    fun onCancelAPICall() {
        apiHandler.cancelCurrentApiJob("用户取消操作")
    }

    fun startNewChat() {
        dismissEditDialog(); dismissSourcesDialog()
        apiHandler.cancelCurrentApiJob("开始新聊天")
        viewModelScope.launch {
            historyManager.saveCurrentChatToHistoryIfNeeded()
            withContext(Dispatchers.Main.immediate) {
                stateHolder.clearForNewChat()
                triggerScrollToBottom()
                if (_isSearchActiveInDrawer.value) setSearchActiveInDrawer(false)
            }
        }
    }

    fun loadConversationFromHistory(index: Int) {
        dismissEditDialog(); dismissSourcesDialog()
        apiHandler.cancelCurrentApiJob("加载历史索引 $index")
        viewModelScope.launch {
            historyManager.saveCurrentChatToHistoryIfNeeded()

            val conversationList = stateHolder._historicalConversations.value
            if (index < 0 || index >= conversationList.size) {
                showSnackbar("无法加载对话：无效的索引"); return@launch
            }
            val conversationToLoad = conversationList[index]

            val (processedConversation, newReasoningMap, newAnimationStates) = withContext(Dispatchers.IO) {
                val newReasoning = mutableMapOf<String, Boolean>()
                val newAnimation = mutableMapOf<String, Boolean>()

                val processed = conversationToLoad.map { msg ->
                    val updatedContentStarted = msg.text.isNotBlank() || !msg.reasoning.isNullOrBlank() || msg.isError
                    msg.copy(contentStarted = updatedContentStarted)
                }

                processed.forEach { msg ->
                    val hasContentOrError = msg.contentStarted || msg.isError
                    val hasReasoning = !msg.reasoning.isNullOrBlank()
                    if (msg.sender == Sender.AI && hasReasoning) {
                        newReasoning[msg.id] = true
                    }
                    val animationPlayedCondition = hasContentOrError || (msg.sender == Sender.AI && hasReasoning)
                    if (animationPlayedCondition) {
                        newAnimation[msg.id] = true
                    }
                }
                Triple(processed, newReasoning, newAnimation)
            }

            withContext(Dispatchers.Main.immediate) {
                stateHolder.messages.clear()
                stateHolder.messages.addAll(processedConversation)
                stateHolder.reasoningCompleteMap.clear()
                stateHolder.reasoningCompleteMap.putAll(newReasoningMap)
                stateHolder.messageAnimationStates.clear()
                stateHolder.messageAnimationStates.putAll(newAnimationStates)

                stateHolder._loadedHistoryIndex.value = index
            }

            if (_isSearchActiveInDrawer.value) withContext(Dispatchers.Main.immediate) {
                setSearchActiveInDrawer(false)
            }
        }
    }

    fun deleteConversation(indexToDelete: Int) {
        val currentLoadedIndex = stateHolder._loadedHistoryIndex.value
        if (indexToDelete < 0 || indexToDelete >= stateHolder._historicalConversations.value.size) {
            showSnackbar("无法删除：无效的索引"); return
        }
        viewModelScope.launch {
            val wasCurrentChatDeleted = (currentLoadedIndex == indexToDelete)
            val idsInDeletedConversation =
                stateHolder._historicalConversations.value.getOrNull(indexToDelete)?.map { it.id }
                    ?: emptyList()
            historyManager.deleteConversation(indexToDelete)
            if (wasCurrentChatDeleted) {
                withContext(Dispatchers.Main.immediate) {
                    dismissEditDialog(); dismissSourcesDialog()
                    stateHolder.clearForNewChat(); triggerScrollToBottom()
                }
                apiHandler.cancelCurrentApiJob("当前聊天(#$indexToDelete)被删除，开始新聊天")
            } else {
                withContext(Dispatchers.Main.immediate) {
                    idsInDeletedConversation.forEach { id ->
                        stateHolder.reasoningCompleteMap.remove(id)
                        stateHolder.expandedReasoningStates.remove(id)
                        stateHolder.messageAnimationStates.remove(id)
                    }
                }
            }
            showSnackbar("对话已删除")
        }
    }

    fun clearAllConversations() {
        dismissEditDialog(); dismissSourcesDialog()
        apiHandler.cancelCurrentApiJob("清除所有历史记录")
        viewModelScope.launch {
            historyManager.clearAllHistory()
            withContext(Dispatchers.Main.immediate) {
                stateHolder.clearForNewChat(); triggerScrollToBottom()
            }
            showSnackbar("所有对话已清除")
        }
    }

    fun showSourcesDialog(sources: List<WebSearchResult>) {
        viewModelScope.launch {
            stateHolder._sourcesForDialog.value = sources; stateHolder._showSourcesDialog.value =
            true
        }
    }

    fun dismissSourcesDialog() {
        viewModelScope.launch {
            if (stateHolder._showSourcesDialog.value) stateHolder._showSourcesDialog.value = false
        }
    }

   fun showSelectableTextDialog(text: String) {
       _textForSelectionDialog.value = text
       _showSelectableTextDialog.value = true
   }

   fun dismissSelectableTextDialog() {
       _showSelectableTextDialog.value = false
       _textForSelectionDialog.value = ""
   }

   fun copyToClipboard(text: String) {
       val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
       val clip = android.content.ClipData.newPlainText("Copied Text", text)
       clipboard.setPrimaryClip(clip)
       showSnackbar("已复制到剪贴板")
   }

    fun exportMessageText(text: String) {
        viewModelScope.launch {
            val fileName = "conversation_export.md"
            _exportRequest.emit(fileName to text)
        }
    }
 
    fun addConfig(config: ApiConfig) = configManager.addConfig(config)
    fun updateConfig(config: ApiConfig) = configManager.updateConfig(config)
    fun deleteConfig(config: ApiConfig) = configManager.deleteConfig(config)
    fun deleteConfigGroup(apiKey: String, modalityType: com.example.everytalk.data.DataClass.ModalityType) {
        viewModelScope.launch(Dispatchers.IO) {
            val configsToDelete = stateHolder._apiConfigs.value.filter { it.key == apiKey && it.modalityType == modalityType }

            if (configsToDelete.isNotEmpty()) {
                configsToDelete.forEach { config ->
                    configManager.deleteConfig(config)
                }
                withContext(Dispatchers.Main) {
                    showSnackbar("配置组已删除")
                }
            }
        }
    }
    fun clearAllConfigs() = configManager.clearAllConfigs()
    fun selectConfig(config: ApiConfig) = configManager.selectConfig(config)
fun updateConfigGroup(representativeConfig: ApiConfig, newAddress: String, newKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmedAddress = newAddress.trim()
            val trimmedKey = newKey.trim()

            val originalKey = representativeConfig.key
            val modality = representativeConfig.modalityType

            val currentConfigs = stateHolder._apiConfigs.value
            val newConfigs = currentConfigs.map { config ->
                if (config.key == originalKey && config.modalityType == modality) {
                    config.copy(address = trimmedAddress, key = trimmedKey)
                } else {
                    config
                }
            }

            stateHolder._apiConfigs.value = newConfigs
            persistenceManager.saveApiConfigs(newConfigs)

            val currentSelectedConfig = stateHolder._selectedApiConfig.value
            if (currentSelectedConfig != null && currentSelectedConfig.key == originalKey && currentSelectedConfig.modalityType == modality) {
                val newSelectedConfig = currentSelectedConfig.copy(address = trimmedAddress, key = trimmedKey)
                stateHolder._selectedApiConfig.value = newSelectedConfig
            }

            withContext(Dispatchers.Main) {
                showSnackbar("配置已更新")
            }
        }
    }

    fun onAnimationComplete(messageId: String) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (stateHolder.messageAnimationStates[messageId] != true) {
                stateHolder.messageAnimationStates[messageId] = true
            }
        }
    }

    fun hasAnimationBeenPlayed(messageId: String): Boolean {
        return stateHolder.messageAnimationStates[messageId] ?: false
    }

    fun getConversationPreviewText(index: Int): String {
        val conversation = stateHolder._historicalConversations.value.getOrNull(index)
            ?: return "对话 ${index + 1}"
        val placeholderTitleMsg =
            conversation.firstOrNull { it.sender == Sender.System && it.isPlaceholderName && it.text.isNotBlank() }?.text?.trim()
        if (!placeholderTitleMsg.isNullOrBlank()) return placeholderTitleMsg
        val firstUserMsg =
            conversation.firstOrNull { it.sender == Sender.User && it.text.isNotBlank() }?.text?.trim()
        if (!firstUserMsg.isNullOrBlank()) return firstUserMsg
        val firstAiMsg =
            conversation.firstOrNull { it.sender == Sender.AI && it.text.isNotBlank() }?.text?.trim()
        if (!firstAiMsg.isNullOrBlank()) return firstAiMsg
        return "对话 ${index + 1}"
    }

    fun onRenameInputTextChange(newName: String) {
        stateHolder._renameInputText.value = newName
    }

    fun showRenameDialog(index: Int) {
        if (index >= 0 && index < stateHolder._historicalConversations.value.size) {
            _renamingIndexState.value = index
            val currentPreview = getConversationPreviewText(index)
            val isDefaultPreview = currentPreview.startsWith("对话 ") && runCatching {
                currentPreview.substringAfter("对话 ").toIntOrNull() == index + 1
            }.getOrElse { false }
            stateHolder._renameInputText.value = if (isDefaultPreview) "" else currentPreview
            _showRenameDialogState.value = true
        } else {
            showSnackbar("无法重命名：无效的对话索引")
        }
    }

    fun dismissRenameDialog() {
        _showRenameDialogState.value = false
        _renamingIndexState.value = null
        stateHolder._renameInputText.value = ""
    }

    fun renameConversation(index: Int, newName: String) {
        val trimmedNewName = newName.trim()
        if (trimmedNewName.isBlank()) {
            showSnackbar("新名称不能为空"); return
        }
        viewModelScope.launch {
            val success = withContext(Dispatchers.Default) {
                val currentHistoricalConvos = stateHolder._historicalConversations.value
                if (index < 0 || index >= currentHistoricalConvos.size) {
                    withContext(Dispatchers.Main) { showSnackbar("无法重命名：对话索引错误") }
                    return@withContext false
                }

                val originalConversationAtIndex = currentHistoricalConvos[index].toMutableList()
                var titleMessageUpdatedOrAdded = false
                val existingTitleIndex =
                    originalConversationAtIndex.indexOfFirst { it.sender == Sender.System && it.isPlaceholderName }

                if (existingTitleIndex != -1) {
                    originalConversationAtIndex[existingTitleIndex] =
                        originalConversationAtIndex[existingTitleIndex].copy(
                            text = trimmedNewName,
                            timestamp = System.currentTimeMillis()
                        )
                    titleMessageUpdatedOrAdded = true
                }

                if (!titleMessageUpdatedOrAdded) {
                    val titleMessage = Message(
                        id = "title_${UUID.randomUUID()}",
                        text = trimmedNewName,
                        sender = Sender.System,
                        timestamp = System.currentTimeMillis() - 1,
                        contentStarted = true,
                        isPlaceholderName = true
                    )
                    originalConversationAtIndex.add(0, titleMessage)
                }

                val updatedHistoricalConversationsList = currentHistoricalConvos.toMutableList()
                    .apply { this[index] = originalConversationAtIndex.toList() }

                withContext(Dispatchers.Main.immediate) {
                    stateHolder._historicalConversations.value = updatedHistoricalConversationsList.toList()
                }

                persistenceManager.saveChatHistory(stateHolder._historicalConversations.value)

                if (stateHolder._loadedHistoryIndex.value == index) {
                    val reloadedConversation = originalConversationAtIndex.toList().map { msg ->
                        val updatedContentStarted =
                            msg.text.isNotBlank() || !msg.reasoning.isNullOrBlank() || msg.isError
                        msg.copy(contentStarted = updatedContentStarted)
                    }
                    withContext(Dispatchers.Main.immediate) {
                        stateHolder.messages.clear()
                        stateHolder.messages.addAll(reloadedConversation)
                        reloadedConversation.forEach { msg ->
                            val hasContentOrError = msg.contentStarted || msg.isError
                            val hasReasoning = !msg.reasoning.isNullOrBlank()
                            if (msg.sender == Sender.AI && hasReasoning) {
                                stateHolder.reasoningCompleteMap[msg.id] = true
                            }
                            val animationPlayedCondition =
                                hasContentOrError || (msg.sender == Sender.AI && hasReasoning)
                            if (animationPlayedCondition) {
                                stateHolder.messageAnimationStates[msg.id] = true
                            }
                        }
                    }
                }
                true
            }
            if (success) {
                withContext(Dispatchers.Main) { dismissRenameDialog(); showSnackbar("对话已重命名") }
            }
        }
    }

    private fun onAiMessageFullTextChanged(messageId: String, currentFullText: String) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            val messageIndex = stateHolder.messages.indexOfFirst { it.id == messageId }
            if (messageIndex != -1) {
                val messageToUpdate = stateHolder.messages[messageIndex]
                if (messageToUpdate.text != currentFullText) {
                    stateHolder.messages[messageIndex] = messageToUpdate.copy(
                        text = currentFullText
                    )
                }
            }
        }
    }

    fun exportSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val settingsToExport = ExportedSettings(
                apiConfigs = stateHolder._apiConfigs.value,
                customProviders = _customProviders.value
            )
            val json = Gson().toJson(settingsToExport)
            _exportRequest.emit("eztalk_settings.json" to json)
        }
    }

    fun importSettings(jsonContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gson = Gson()
                val type = object : TypeToken<ExportedSettings>() {}.type
                val importedSettings = gson.fromJson<ExportedSettings>(jsonContent, type)

                // Basic validation
                if (importedSettings.apiConfigs.any { it.id.isBlank() || it.provider.isBlank() }) {
                    withContext(Dispatchers.Main) { showSnackbar("导入失败：配置文件格式无效。") }
                    return@launch
                }

                stateHolder._apiConfigs.value = importedSettings.apiConfigs
                _customProviders.value = importedSettings.customProviders

                persistenceManager.saveApiConfigs(importedSettings.apiConfigs)
                dataSource.saveCustomProviders(importedSettings.customProviders)

                // Reselect config if the old one is gone
                val currentSelectedId = stateHolder._selectedApiConfig.value?.id
                if (currentSelectedId == null || importedSettings.apiConfigs.none { it.id == currentSelectedId }) {
                    val firstConfig = importedSettings.apiConfigs.firstOrNull()
                    stateHolder._selectedApiConfig.value = firstConfig
                    persistenceManager.saveSelectedConfigIdentifier(firstConfig?.id)
                }

                withContext(Dispatchers.Main) {
                    showSnackbar("配置已成功导入")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showSnackbar("导入失败: ${e.message}")
                }
            }
        }
    }

fun getMessageById(id: String): Message? {
        return messages.find { it.id == id }
    }
    override fun onCleared() {
        try {
        } catch (e: Exception) {
        }
        dismissEditDialog(); dismissSourcesDialog()
        apiHandler.cancelCurrentApiJob("ViewModel cleared", isNewMessageSend = false)

        val finalApiConfigs = stateHolder._apiConfigs.value.toList()
        val finalSelectedConfigId = stateHolder._selectedApiConfig.value?.id
        val finalCurrentChatMessages = stateHolder.messages.toList()

        try {
            runBlocking(Dispatchers.IO) {
                launch { historyManager.saveCurrentChatToHistoryIfNeeded(forceSave = true) }
                persistenceManager.saveLastOpenChat(finalCurrentChatMessages)
                persistenceManager.saveApiConfigs(finalApiConfigs)
                persistenceManager.saveSelectedConfigIdentifier(finalSelectedConfigId)
                dataSource.saveCustomProviders(_customProviders.value)
            }
        } catch (e: Exception) {
        }
        super.onCleared()
    }

}