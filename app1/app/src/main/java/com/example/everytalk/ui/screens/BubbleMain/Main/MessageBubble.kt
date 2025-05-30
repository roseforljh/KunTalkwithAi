package com.example.everytalk.ui.screens.BubbleMain.Main // 确保包名正确

import android.net.Uri // 确保导入 Uri
import android.util.Log
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons // 导入 Icons
import androidx.compose.material.icons.outlined.Description // 导入文档图标
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.everytalk.data.DataClass.Message
import com.example.everytalk.data.DataClass.Sender
import com.example.everytalk.StateControler.AppViewModel
import com.example.everytalk.model.SelectedMediaItem // 导入 SelectedMediaItem
import com.example.everytalk.ui.screens.BubbleMain.AiMessageContent

// 确保这些组件也被正确导入或定义在同一个文件中
// import com.example.everytalk.ui.screens.BubbleMain.ReasoningToggleAndContent
// import com.example.everytalk.ui.screens.BubbleMain.UserOrErrorMessageContent
// import com.example.everytalk.ui.screens.BubbleMain.ThreeDotsWaveAnimation

fun Color.toHexCss(): String {
    return String.format("#%06X", 0xFFFFFF and this.toArgb())
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MessageBubble(
    message: Message, viewModel: AppViewModel,
    isMainContentStreaming: Boolean, isReasoningStreaming: Boolean, isReasoningComplete: Boolean,
    onUserInteraction: () -> Unit, maxWidth: Dp,
    onEditRequest: (Message) -> Unit, onRegenerateRequest: (Message) -> Unit,
    modifier: Modifier = Modifier, showLoadingBubble: Boolean = false
) {
    val aiBubbleColor = Color.White
    val aiContentColor = Color.Black
    val errorTextColor = Color.Red
    val userBubbleBackgroundColor = Color(0xFFF3F3F3)
    val userContentColor = Color.Black
    val reasoningTextColor = Color(0xFF444444)

    val aiMessageBlockMaxWidth = maxWidth
    val userMessageBlockMaxWidth = maxWidth * 0.85f

    Log.d(
        "MessageBubbleRecomp",
        "ID: ${message.id.take(8)}, Sender: ${message.sender}, ImageUrls: ${message.imageUrls?.size ?: 0}, Attachments: ${message.attachments?.size ?: 0}"
    )

    val isAI = message.sender == Sender.AI
    val currentMessageId = message.id
    val displayedMainTextForUserOrError = remember(message.text) { message.text.trim() }
    val displayedReasoningText = remember(message.reasoning) { message.reasoning?.trim() ?: "" }

    val animationInitiallyPlayedByVM =
        remember(currentMessageId) { viewModel.hasAnimationBeenPlayed(currentMessageId) }
    var localAnimationTriggeredOrCompleted by remember(currentMessageId) {
        mutableStateOf(animationInitiallyPlayedByVM)
    }
    LaunchedEffect(
        currentMessageId, message.text, message.reasoning, message.webSearchResults,
        message.isError, isMainContentStreaming, showLoadingBubble, message.contentStarted
    ) {
        if (showLoadingBubble) return@LaunchedEffect
        if (!localAnimationTriggeredOrCompleted) {
            val isStable =
                message.isError || !isMainContentStreaming || (isAI && message.contentStarted)
            val hasContent = message.text.trim()
                .isNotBlank() || (isAI && message.reasoning?.isNotBlank() == true) || (isAI && !message.webSearchResults.isNullOrEmpty())
            if (isStable && (hasContent || message.isError)) {
                localAnimationTriggeredOrCompleted = true
                if (!animationInitiallyPlayedByVM) viewModel.onAnimationComplete(currentMessageId)
            } else if (isAI && !isMainContentStreaming && message.contentStarted && message.text.isBlank() && message.reasoning.isNullOrBlank() && message.webSearchResults.isNullOrEmpty() && !message.isError) {
                localAnimationTriggeredOrCompleted = true
                if (!animationInitiallyPlayedByVM) viewModel.onAnimationComplete(currentMessageId)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isAI) Alignment.Start else Alignment.End
    ) {
        // --- 用户发送的图片显示区域 (保持您之前的逻辑) ---
        if (!isAI && !message.imageUrls.isNullOrEmpty()) {
            Column(
                modifier = Modifier
                    .widthIn(max = userMessageBlockMaxWidth)
                    .padding(bottom = 6.dp),
                horizontalAlignment = Alignment.End
            ) {
                message.imageUrls.forEachIndexed { index, imageUrlString ->
                    val imageUri = try {
                        Uri.parse(imageUrlString)
                    } catch (e: Exception) {
                        null
                    }
                    if (imageUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(imageUri)
                                .crossfade(true)
                                .listener(
                                    onSuccess = { _, result ->
                                        Log.i(
                                            "MessageBubbleUserImage",
                                            "Successfully loaded image: ${result.request.data}"
                                        )
                                    },
                                    onError = { _, result ->
                                        Log.e(
                                            "MessageBubbleUserImage",
                                            "Error loading image: ${result.request.data}",
                                            result.throwable
                                        )
                                    }
                                ).build(),
                            contentDescription = "用户发送的图片 ${index + 1}",
                            modifier = Modifier
                                .widthIn(max = userMessageBlockMaxWidth * 0.7f)
                                .heightIn(min = 50.dp, max = 300.dp)
                                .aspectRatio(1f, matchHeightConstraintsFirst = false)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.LightGray.copy(alpha = 0.3f)),
                            contentScale = ContentScale.Crop
                        )
                        if (index < message.imageUrls.size - 1) Spacer(Modifier.height(6.dp))
                    } else {
                        Text(
                            "图片加载失败: $imageUrlString",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        // --- 图片显示区域结束 ---

        // --- 用户发送的文档附件显示区域 (新增) ---
        val documentAttachments = remember(message.attachments) {
            message.attachments?.filterIsInstance<SelectedMediaItem.GenericFile>()?.filter {
                // 根据 MIME 类型判断是否为常见文档类型 (您可以扩展这个判断)
                val mime = it.mimeType?.lowercase()
                mime != null && (
                        mime.startsWith("application/pdf") ||
                                mime.startsWith("application/msword") ||
                                mime.startsWith("application/vnd.openxmlformats-officedocument.wordprocessingml.document") || // docx
                                mime.startsWith("text/") || // txt, csv, html etc.
                                mime.startsWith("application/vnd.ms-excel") ||
                                mime.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") || // xlsx
                                mime.startsWith("application/vnd.ms-powerpoint") ||
                                mime.startsWith("application/vnd.openxmlformats-officedocument.presentationml.presentation") // pptx
                        )
            } ?: emptyList()
        }

        if (!isAI && documentAttachments.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .widthIn(max = userMessageBlockMaxWidth) // 与图片和文本块宽度一致
                    .padding(bottom = if (message.text.isNotBlank() || message.imageUrls.isNullOrEmpty()) 6.dp else 0.dp), // 如果下方有文本，则加间距
                horizontalAlignment = Alignment.End // 文档附件也右对齐
            ) {
                documentAttachments.forEachIndexed { index, doc ->
                    Log.d(
                        "MessageBubbleUserDoc",
                        "Displaying document: ${doc.displayName}, MIME: ${doc.mimeType}"
                    )
                    // 简单的文档预览：图标 + 文件名
                    // 您可以根据需要使这个预览更复杂，例如添加点击下载/查看的逻辑
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(userBubbleBackgroundColor.copy(alpha = 0.7f)) // Slightly different background
                            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .widthIn(max = userMessageBlockMaxWidth * 0.8f) // 限制单个文档预览宽度
                            .clickable {
                                Log.d(
                                    "MessageBubbleUserDoc",
                                    "Clicked on document: ${doc.displayName}"
                                )
                                // TODO: 实现点击文档后的操作，例如打开或下载
                                viewModel.showSnackbar("点击了文档: ${doc.displayName}")
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description, // 通用文档图标
                            contentDescription = "文档图标",
                            modifier = Modifier.size(24.dp),
                            tint = userContentColor.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = doc.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = userContentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (index < documentAttachments.size - 1) {
                        Spacer(Modifier.height(4.dp)) // 多个文档之间的间距
                    }
                }
            }
        }
        // --- 文档附件显示区域结束 ---


        // --- AI 消息或用户文本消息的气泡主体 (您的原始代码结构) ---
        Column(
            modifier = Modifier.widthIn(max = if (isAI) aiMessageBlockMaxWidth else userMessageBlockMaxWidth),
        ) {
            // AI 加载占位符气泡 (你原来的逻辑)
            if (isAI && showLoadingBubble) {
                Box(modifier = Modifier.padding(start = 16.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp), color = aiBubbleColor,
                        shadowElevation = 4.dp, contentColor = aiContentColor,
                        modifier = Modifier
                            .fillMaxWidth(0.42f)
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            val loadingText = remember(
                                message.currentWebSearchStage,
                                isReasoningStreaming,
                                message.reasoning,
                                isReasoningComplete
                            ) {
                                when (message.currentWebSearchStage) {
                                    "web_indexing_started" -> "正在索引网页..."
                                    "web_analysis_started" -> "正在分析网页..."
                                    "web_analysis_complete" -> {
                                        if (isReasoningStreaming) "大模型思考中..."
                                        else if (!message.reasoning.isNullOrBlank() && !isReasoningComplete) "大模型思考中..."
                                        else if (!message.reasoning.isNullOrBlank() && isReasoningComplete) "思考完成"
                                        else "分析完成"
                                    }

                                    else -> {
                                        if (isReasoningStreaming) "大模型思考中..."
                                        else if (!message.reasoning.isNullOrBlank() && !isReasoningComplete) "大模型思考中..."
                                        else if (!message.reasoning.isNullOrBlank() && isReasoningComplete) "思考完成"
                                        else "正在连接大模型..."
                                    }
                                }
                            }
                            Text(
                                text = loadingText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = aiContentColor
                            )
                            Spacer(Modifier.height(5.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { scaleY = 0.5f; },
                                color = Color.Black, trackColor = Color(0xffd0d0d0)
                            )
                        }
                    }
                }
            }

            val shouldDisplayReasoningComponentBox =
                isAI && (!message.reasoning.isNullOrBlank() || isReasoningStreaming)
            if (shouldDisplayReasoningComponentBox) {
                ReasoningToggleAndContent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = if (isAI && showLoadingBubble) 6.dp else 0.dp,
                            bottom = 4.dp
                        ),
                    currentMessageId = currentMessageId,
                    displayedReasoningText = displayedReasoningText,
                    isReasoningStreaming = isReasoningStreaming,
                    isReasoningComplete = isReasoningComplete,
                    messageIsError = message.isError,
                    mainContentHasStarted = message.contentStarted,
                    reasoningTextColor = reasoningTextColor,
                    reasoningToggleDotColor = aiContentColor
                )
            }

            val shouldShowAiMessageComponent = isAI && !message.isError && !showLoadingBubble
            if (shouldShowAiMessageComponent) {
                val showDotsInsideAiContent =
                    isMainContentStreaming && message.text.isBlank() && !message.contentStarted && !showLoadingBubble
                if (message.text.isNotBlank() || (message.contentStarted && message.text.isBlank()) || showDotsInsideAiContent) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(
                            topStart = if (shouldDisplayReasoningComponentBox) 8.dp else 18.dp,
                            topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp
                        ),
                        color = aiBubbleColor,
                        contentColor = aiContentColor,
                        shadowElevation = 0.dp,
                    ) {
                        AiMessageContent(
                            message = message,
                            appViewModel = viewModel,
                            fullMessageTextToCopy = message.text,
                            showLoadingDots = showDotsInsideAiContent,
                            contentColor = aiContentColor,
                            onUserInteraction = onUserInteraction,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp)
                        )
                    }
                } else if (message.contentStarted && message.text.isBlank() && isMainContentStreaming) { /* Fallback or placeholder if needed */
                }

                val showSourcesButton =
                    !message.webSearchResults.isNullOrEmpty() && message.contentStarted && !isMainContentStreaming && !showLoadingBubble
                if (showSourcesButton) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { onUserInteraction(); viewModel.showSourcesDialog(message.webSearchResults!!) },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text("查看参考来源 (${message.webSearchResults?.size ?: 0})")
                    }
                }

                val showStreamingDotsBelowMainContent =
                    message.contentStarted && isMainContentStreaming && !message.isError
                if (showStreamingDotsBelowMainContent) {
                    Spacer(Modifier.height(8.dp))
                    ThreeDotsWaveAnimation(
                        modifier = Modifier
                            .padding(start = 12.dp, bottom = 4.dp)
                            .align(Alignment.Start),
                        dotColor = aiContentColor, dotSize = 7.dp, spacing = 5.dp
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (!isAI && message.text.isNotBlank() && !message.isError) { // 用户文本消息 (确保有文本才显示这个气泡)
                UserOrErrorMessageContent(
                    message = message,
                    displayedText = displayedMainTextForUserOrError,
                    showLoadingDots = false,
                    bubbleColor = userBubbleBackgroundColor,
                    contentColor = userContentColor,
                    isError = false,
                    maxWidth = userMessageBlockMaxWidth,
                    onUserInteraction = onUserInteraction,
                    onEditRequest = onEditRequest,
                    onRegenerateRequest = onRegenerateRequest,
                    modifier = Modifier // 您的原始 Modifier
                )
            } else if (isAI && message.isError && !showLoadingBubble) {
                Column(
                    modifier = Modifier
                        .widthIn(max = aiMessageBlockMaxWidth)
                        .align(Alignment.Start),
                    horizontalAlignment = Alignment.Start
                ) {
                    UserOrErrorMessageContent(
                        message = message,
                        displayedText = displayedMainTextForUserOrError,
                        showLoadingDots = false,
                        bubbleColor = aiBubbleColor,
                        contentColor = errorTextColor,
                        isError = true,
                        maxWidth = aiMessageBlockMaxWidth,
                        onUserInteraction = onUserInteraction,
                        onEditRequest = onEditRequest,
                        onRegenerateRequest = onRegenerateRequest,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
