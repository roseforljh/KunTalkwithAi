package com.example.everytalk.ui.screens.MainScreen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.everytalk.data.DataClass.Message
import com.example.everytalk.data.DataClass.Sender
import com.example.everytalk.navigation.Screen
import com.example.everytalk.statecontroller.AppViewModel
import com.example.everytalk.statecontroller.ConversationScrollState
import com.example.everytalk.ui.components.AppTopBar
import com.example.everytalk.ui.components.ScrollToBottomButton
import com.example.everytalk.ui.components.WebSourcesDialog
import com.example.everytalk.ui.screens.MainScreen.chat.ChatInputArea
import com.example.everytalk.ui.screens.MainScreen.chat.ChatMessagesList
import com.example.everytalk.ui.screens.MainScreen.chat.EditMessageDialog
import com.example.everytalk.ui.screens.MainScreen.chat.EmptyChatView
import com.example.everytalk.ui.screens.MainScreen.chat.ModelSelectionBottomSheet
import com.example.everytalk.ui.screens.MainScreen.chat.rememberChatScrollStateManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: AppViewModel,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val messages: List<Message> = viewModel.messages
    val text by viewModel.text.collectAsState()
    val selectedApiConfig by viewModel.selectedApiConfig.collectAsState()
    val isApiCalling by viewModel.isApiCalling.collectAsState()
    val currentStreamingAiMessageId by viewModel.currentStreamingAiMessageId.collectAsState()
    val isWebSearchEnabled by viewModel.isWebSearchEnabled.collectAsState()
    val selectedMediaItems = viewModel.selectedMediaItems
    val isLoadingHistory by viewModel.isLoadingHistory.collectAsState()
    val isLoadingHistoryData by viewModel.isLoadingHistoryData.collectAsState()
    val conversationId by viewModel.currentConversationId.collectAsState()
    val latestReleaseInfo by viewModel.latestReleaseInfo.collectAsState()
 
     val coroutineScope = rememberCoroutineScope()
     val loadedHistoryIndex by viewModel.loadedHistoryIndex.collectAsState()


    val listState = remember(conversationId) {
        LazyListState(0, 0)
    }

    LaunchedEffect(conversationId) {
        val savedState = viewModel.getScrollState(conversationId)
        if (savedState != null && savedState.firstVisibleItemIndex > 0) {
            snapshotFlow { isLoadingHistory }
                .filter { !it }
                .first()

            listState.scrollToItem(
                index = savedState.firstVisibleItemIndex,
                scrollOffset = savedState.firstVisibleItemScrollOffset
            )
        }
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val keyboardController = LocalSoftwareKeyboardController.current


    val scrollStateManager = rememberChatScrollStateManager(listState, coroutineScope)
    val isAtBottom by scrollStateManager.isAtBottom



    DisposableEffect(conversationId, isAtBottom) {
        val idToSaveFor = conversationId
        onDispose {
            val stateToSave = ConversationScrollState(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                userScrolledAway = !isAtBottom,
            )
            viewModel.saveScrollState(idToSaveFor, stateToSave)
        }
    }

    LaunchedEffect(isApiCalling) {
        if (isApiCalling) {
            scrollStateManager.onStreamingStarted()
        } else {
            // Add a small delay to allow the UI to settle before finishing streaming mode
            delay(100)
            scrollStateManager.onStreamingFinished()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.scrollToBottomEvent.collect {
            scrollStateManager.jumpToBottom()
        }
    }

    val focusRequester = remember { FocusRequester() }


    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    var showModelSelectionBottomSheet by remember { mutableStateOf(false) }
    val availableModels by viewModel.apiConfigs.collectAsState()

    var showAiMessageOptionsBottomSheet by remember { mutableStateOf(false) }
    var selectedMessageForOptions by remember { mutableStateOf<Message?>(null) }
    val aiMessageOptionsBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filteredModelsForBottomSheet by remember(availableModels, selectedApiConfig) {
        derivedStateOf {
            selectedApiConfig?.key?.takeIf { it.isNotBlank() }?.let { key ->
                availableModels.filter { it.key == key }.ifEmpty {
                    listOfNotNull(selectedApiConfig)
                }
            } ?: availableModels
        }
    }





    val screenWidth = configuration.screenWidthDp.dp
    val bubbleMaxWidth = remember(screenWidth) { screenWidth.coerceAtMost(600.dp) }




    val showEditDialog by viewModel.showEditDialog.collectAsState()
    val editDialogInputText by viewModel.editDialogInputText.collectAsState()
    val showSourcesDialog by viewModel.showSourcesDialog.collectAsState()
    val sourcesForDialog by viewModel.sourcesForDialog.collectAsState()
    val showSelectableTextDialog by viewModel.showSelectableTextDialog.collectAsState()
    val textForSelectionDialog by viewModel.textForSelectionDialog.collectAsState()
    val imeInsets = WindowInsets.ime
    
    // 获取输入法高度用于整体布局偏移
    val imeHeightPx by remember {
        derivedStateOf { imeInsets.getBottom(density) }
    }
    val imeHeightDp = with(density) { imeHeightPx.toDp() }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                // TODO: 权限授予后开始录音
                viewModel.showSnackbar("录音权限已授予")
            } else {
                viewModel.showSnackbar("需要录音权限才能使用此功能")
            }
        }
    )

    if (showSelectableTextDialog) {
        SelectableTextDialog(
            textToDisplay = textForSelectionDialog,
            onDismissRequest = { viewModel.dismissSelectableTextDialog() }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.White,
        topBar = {
            AppTopBar(
                selectedConfigName = selectedApiConfig?.name?.takeIf { it.isNotBlank() }
                    ?: selectedApiConfig?.model ?: "选择配置",
                onMenuClick = { coroutineScope.launch { viewModel.drawerState.open() } },
                onSettingsClick = {
                    navController.navigate(Screen.SETTINGS_SCREEN) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onTitleClick = {
                    coroutineScope.launch {
                        if (filteredModelsForBottomSheet.isNotEmpty()) {
                            showModelSelectionBottomSheet = true
                        } else {
                            viewModel.showSnackbar("当前无可用模型配置")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ScrollToBottomButton(
                scrollStateManager = scrollStateManager
            )
        },
        floatingActionButtonPosition = FabPosition.End
    ) { scaffoldPaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = scaffoldPaddingValues.calculateTopPadding())
                .offset(y = -imeHeightDp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    isLoadingHistory -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    messages.isEmpty() -> {
                        EmptyChatView(density = density)
                    }
                    else -> {
                        val chatListItems by viewModel.chatListItems.collectAsState()

                        ChatMessagesList(
                            chatItems = chatListItems,
                            viewModel = viewModel,
                            listState = listState,
                            scrollStateManager = scrollStateManager,
                            bubbleMaxWidth = bubbleMaxWidth,
                            onShowAiMessageOptions = { msg ->
                                selectedMessageForOptions = msg
                                showAiMessageOptionsBottomSheet = true
                            },
                            onImageLoaded = {
                                if (!scrollStateManager.userInteracted) {
                                    scrollStateManager.jumpToBottom()
                                }
                            },
                            onThinkingBoxVisibilityChanged = {
                                if (!scrollStateManager.userInteracted) {
                                    scrollStateManager.jumpToBottom()
                                }
                            },
                        )
                    }
                }

            }
                ChatInputArea(
                    text = text,
                    onTextChange = {
                        viewModel.onTextChange(it)
                    },
                    onSendMessageRequest = { messageText, _, attachments, mimeType ->
                        viewModel.onSendMessage(messageText = messageText, attachments = attachments, audioBase64 = null, mimeType = mimeType)
                        keyboardController?.hide()
                        coroutineScope.launch {
                            // 等待键盘关闭
                            snapshotFlow { imeInsets.getBottom(density) > 0 }
                                .filter { isVisible -> !isVisible }
                                .first()
                            // 滚动到底部
                            scrollStateManager.jumpToBottom()
                        }
                    },
                    selectedMediaItems = selectedMediaItems,
                    onAddMediaItem = { viewModel.addMediaItem(it) },
                    onRemoveMediaItemAtIndex = { viewModel.removeMediaItemAtIndex(it) },
                    onClearMediaItems = { viewModel.clearMediaItems() },
                    isApiCalling = isApiCalling,
                    isWebSearchEnabled = isWebSearchEnabled,
                    onToggleWebSearch = {
                        viewModel.toggleWebSearchMode(!isWebSearchEnabled)
                    },
                    onStopApiCall = { viewModel.onCancelAPICall() },
                    focusRequester = focusRequester,
                    selectedApiConfig = selectedApiConfig,
                    onShowSnackbar = { viewModel.showSnackbar(it) },
                    imeInsets = imeInsets,
                    density = density,
                    keyboardController = keyboardController,
                    onFocusChange = {
                        scrollStateManager.jumpToBottom()
                    },
                    onSendMessage = { messageText, isFromRegeneration, attachments, audioBase64, mimeType ->
                        viewModel.onSendMessage(
                            messageText = messageText,
                            isFromRegeneration = isFromRegeneration,
                            attachments = attachments,
                            audioBase64 = audioBase64,
                            mimeType = mimeType
                        )
                    }
                )
        }

        if (showEditDialog) {
            EditMessageDialog(
                editDialogInputText = editDialogInputText,
                onDismissRequest = { viewModel.dismissEditDialog() },
                onEditDialogTextChanged = viewModel::onEditDialogTextChanged,
                onConfirmMessageEdit = { viewModel.confirmMessageEdit() }
            )
        }

        if (showSourcesDialog) {
            WebSourcesDialog(
                sources = sourcesForDialog,
                onDismissRequest = { viewModel.dismissSourcesDialog() }
            )
        }

        if (showModelSelectionBottomSheet) {
            ModelSelectionBottomSheet(
                onDismissRequest = { showModelSelectionBottomSheet = false },
                sheetState = bottomSheetState,
                availableModels = filteredModelsForBottomSheet,
                selectedApiConfig = selectedApiConfig,
                onModelSelected = { modelConfig ->
                    viewModel.selectConfig(modelConfig)
                    coroutineScope.launch {
                        bottomSheetState.hide()
                    }.invokeOnCompletion {
                        if (!bottomSheetState.isVisible) {
                            showModelSelectionBottomSheet = false
                        }
                    }
                },
                allApiConfigs = availableModels,
                onPlatformSelected = { platformConfig ->
                    viewModel.selectConfig(platformConfig)
                }
            )
        }

        if (showAiMessageOptionsBottomSheet && selectedMessageForOptions != null) {
            AiMessageOptionsBottomSheet(
                onDismissRequest = { showAiMessageOptionsBottomSheet = false },
                sheetState = aiMessageOptionsBottomSheetState,
                onOptionSelected = { option ->
                    when (option) {
                        AiMessageOption.SELECT_TEXT -> viewModel.showSelectableTextDialog(selectedMessageForOptions!!.text)
                        AiMessageOption.COPY_FULL_TEXT -> viewModel.copyToClipboard(selectedMessageForOptions!!.text)
                        AiMessageOption.REGENERATE -> {
                            // 确保键盘隐藏，避免重新回答时弹出输入法
                            keyboardController?.hide()
                            viewModel.regenerateAiResponse(selectedMessageForOptions!!)
                            // 不立即滚动，让regenerateAiResponse内部的逻辑处理滚动
                        }
                        AiMessageOption.EXPORT_TEXT -> viewModel.exportMessageText(selectedMessageForOptions!!.text)
                    }
                    coroutineScope.launch {
                        aiMessageOptionsBottomSheetState.hide()
                    }.invokeOnCompletion {
                        if (!aiMessageOptionsBottomSheetState.isVisible) {
                            showAiMessageOptionsBottomSheet = false
                        }
                    }
                }
            )
        }
    }
 
    val showAboutDialog by viewModel.showAboutDialog.collectAsState()

    if (showAboutDialog) {
        AboutDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.dismissAboutDialog() }
        )
    }

    if (latestReleaseInfo != null) {
        val uriHandler = LocalUriHandler.current
        UpdateAvailableDialog(
            releaseInfo = latestReleaseInfo!!,
            onDismiss = { viewModel.clearUpdateInfo() },
            onUpdate = {
                uriHandler.openUri(it)
                viewModel.clearUpdateInfo()
            }
        )
    }
}
 
@Composable
private fun AboutDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val versionName = packageInfo.versionName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于 EveryTalk") },
        text = {
            val uriHandler = LocalUriHandler.current
            val annotatedString = buildAnnotatedString {
                append("版本: $versionName\n\n一个开源的、可高度定制的 AI 聊天客户端。\n\nGitHub: ")
                pushStringAnnotation(tag = "URL", annotation = "https://github.com/roseforljh/KunTalkwithAi")
                withStyle(style = SpanStyle(color = Color(0xFF007eff))) {
                    append("EveryTalk")
                }
                pop()
            }

            ClickableText(
                text = annotatedString,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                onClick = { offset ->
                    annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            uriHandler.openUri(annotation.item)
                        }
                }
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.checkForUpdates()
                    onDismiss() // Immediately close the about dialog
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                )
            ) {
                Text("检查更新")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.DarkGray,
                    contentColor = Color.White
                )
            ) {
                Text("关闭")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White
    )
}

 @Composable
private fun UpdateAvailableDialog(
    releaseInfo: com.example.everytalk.data.DataClass.GithubRelease,
    onDismiss: () -> Unit,
    onUpdate: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本: ${releaseInfo.tagName}") },
        text = {
            Text("有新的更新可用。建议您更新到最新版本以获得最佳体验。\n\n更新日志:\n${releaseInfo.body}")
        },
        confirmButton = {
            Button(
                onClick = { onUpdate(releaseInfo.htmlUrl) },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                )
            ) {
                Text("立即更新")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.DarkGray,
                    contentColor = Color.White
                )
            ) {
                Text("稍后")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White
    )
}

 @Composable
 internal fun SelectableTextDialog(textToDisplay: String, onDismissRequest: () -> Unit) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.75f),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            SelectionContainer(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = textToDisplay,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiMessageOptionsBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    onOptionSelected: (AiMessageOption) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            AiMessageOption.values().forEach { option ->
                ListItem(
                    headlineContent = { Text(option.title) },
                    leadingContent = { Icon(option.icon, contentDescription = option.title) },
                    modifier = Modifier.clickable { onOptionSelected(option) },
                    colors = ListItemDefaults.colors(containerColor = Color.White)
                )
            }
        }
    }
}

private enum class AiMessageOption(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SELECT_TEXT("选择文本", Icons.Outlined.SelectAll),
    COPY_FULL_TEXT("复制全文", Icons.Filled.ContentCopy),
    REGENERATE("重新回答", Icons.Filled.Refresh),
    EXPORT_TEXT("导出文本", Icons.Filled.IosShare)
}