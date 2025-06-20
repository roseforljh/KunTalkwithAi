package com.example.everytalk.statecontroler

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.everytalk.data.DataClass.AbstractApiMessage
import com.example.everytalk.data.DataClass.ApiContentPart
import com.example.everytalk.data.DataClass.ChatRequest
import com.example.everytalk.data.DataClass.PartsApiMessage
import com.example.everytalk.data.DataClass.SimpleTextApiMessage
import com.example.everytalk.data.DataClass.Message as UiMessage
import com.example.everytalk.data.DataClass.Sender as UiSender
import com.example.everytalk.data.DataClass.ThinkingConfig
import com.example.everytalk.data.DataClass.GenerationConfig
import com.example.everytalk.model.SelectedMediaItem
import com.example.everytalk.ui.screens.viewmodel.HistoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private data class AttachmentProcessingResult(
    val success: Boolean,
    val processedAttachmentsForUi: List<SelectedMediaItem> = emptyList(),
    val imageUriStringsForUi: List<String> = emptyList(),
    val apiContentParts: List<ApiContentPart> = emptyList()
)
 class MessageSender(
     private val application: Application,
    private val viewModelScope: CoroutineScope,
    private val stateHolder: ViewModelStateHolder,
    private val apiHandler: ApiHandler,
    private val historyManager: HistoryManager,
    private val showSnackbar: (String) -> Unit,
    private val triggerScrollToBottom: () -> Unit
) {

    companion object {
        private const val MAX_IMAGE_SIZE_BYTES = 4 * 1024 * 1024
        private const val TARGET_IMAGE_WIDTH = 1024
        private const val TARGET_IMAGE_HEIGHT = 1024
        private const val JPEG_COMPRESSION_QUALITY = 80
        private const val CHAT_ATTACHMENTS_SUBDIR = "chat_attachments"
    }

    private suspend fun loadAndCompressBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return withContext(Dispatchers.IO) {
            var bitmap: Bitmap? = null
            try {
                if (uri == Uri.EMPTY) return@withContext null

                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }

                options.inSampleSize = calculateInSampleSize(options, TARGET_IMAGE_WIDTH, TARGET_IMAGE_HEIGHT)

                options.inJustDecodeBounds = false
                options.inMutable = true

                bitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }

                if (bitmap != null && (bitmap!!.width > TARGET_IMAGE_WIDTH || bitmap!!.height > TARGET_IMAGE_HEIGHT)) {
                    val aspectRatio = bitmap!!.width.toFloat() / bitmap!!.height.toFloat()
                    val newWidth: Int
                    val newHeight: Int
                    if (bitmap!!.width > bitmap!!.height) {
                        newWidth = TARGET_IMAGE_WIDTH
                        newHeight = (newWidth / aspectRatio).toInt()
                    } else {
                        newHeight = TARGET_IMAGE_HEIGHT
                        newWidth = (newHeight * aspectRatio).toInt()
                    }
                    if (newWidth > 0 && newHeight > 0) {
                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap!!, newWidth, newHeight, true)
                        if (scaledBitmap != bitmap) {
                            bitmap?.recycle()
                        }
                        bitmap = scaledBitmap
                    }
                }
                bitmap
            } catch (e: Exception) {
                bitmap?.recycle()
                null
            }
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private suspend fun copyUriToAppInternalStorage(
        context: Context,
        sourceUri: Uri,
        messageIdHint: String,
        attachmentIndex: Int,
        originalFileName: String?
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val MimeTypeMap = android.webkit.MimeTypeMap.getSingleton()
                val contentType = context.contentResolver.getType(sourceUri)
                val extension = MimeTypeMap.getExtensionFromMimeType(contentType)
                    ?: originalFileName?.substringAfterLast('.', "")
                    ?: "bin"

                val timeStamp: String =
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val safeOriginalName =
                    originalFileName?.replace("[^a-zA-Z0-9._-]".toRegex(), "_")?.take(30) ?: "file"
                val uniqueFileName =
                    "${safeOriginalName}_${messageIdHint}_${attachmentIndex}_${timeStamp}_${
                        UUID.randomUUID().toString().take(4)
                    }.$extension"

                val attachmentDir = File(context.filesDir, CHAT_ATTACHMENTS_SUBDIR)
                if (!attachmentDir.exists() && !attachmentDir.mkdirs()) {
                    return@withContext null
                }

                val destinationFile = File(attachmentDir, uniqueFileName)
                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    FileOutputStream(destinationFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: run {
                    return@withContext null
                }

                if (!destinationFile.exists() || destinationFile.length() == 0L) {
                    if (destinationFile.exists()) destinationFile.delete()
                    return@withContext null
                }

                destinationFile.absolutePath
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun saveBitmapToAppInternalStorage(
        context: Context,
        bitmapToSave: Bitmap,
        messageIdHint: String,
        attachmentIndex: Int,
        originalFileNameHint: String? = null
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (bitmapToSave.isRecycled) {
                    return@withContext null
                }

                val outputStream = ByteArrayOutputStream()
                val fileExtension: String
                val compressFormat = if (bitmapToSave.hasAlpha()) {
                    fileExtension = "png"; Bitmap.CompressFormat.PNG
                } else {
                    fileExtension = "jpg"; Bitmap.CompressFormat.JPEG
                }
                bitmapToSave.compress(compressFormat, JPEG_COMPRESSION_QUALITY, outputStream)
                val bytes = outputStream.toByteArray()
                if (!bitmapToSave.isRecycled) {
                    bitmapToSave.recycle()
                }

                val timeStamp: String =
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val baseName = originalFileNameHint?.substringBeforeLast('.')
                    ?.replace("[^a-zA-Z0-9._-]".toRegex(), "_")?.take(20) ?: "IMG"
                val uniqueFileName =
                    "${baseName}_${messageIdHint}_${attachmentIndex}_${timeStamp}_${
                        UUID.randomUUID().toString().take(4)
                    }.$fileExtension"

                val attachmentDir = File(context.filesDir, CHAT_ATTACHMENTS_SUBDIR)
                if (!attachmentDir.exists() && !attachmentDir.mkdirs()) {
                    return@withContext null
                }

                val destinationFile = File(attachmentDir, uniqueFileName)
                FileOutputStream(destinationFile).use { it.write(bytes) }

                if (!destinationFile.exists() || destinationFile.length() == 0L) {
                    if (destinationFile.exists()) destinationFile.delete()
                    return@withContext null
                }
                destinationFile.absolutePath
            } catch (e: Exception) {
                if (!bitmapToSave.isRecycled) {
                    bitmapToSave.recycle()
                }
                null
            }
        }
    }

    private suspend fun processAttachments(
        attachments: List<SelectedMediaItem>,
        shouldUsePartsApiMessage: Boolean,
        textToActuallySend: String
    ): AttachmentProcessingResult = withContext(Dispatchers.IO) {
        if (attachments.isEmpty()) {
            val apiParts = if (shouldUsePartsApiMessage && textToActuallySend.isNotBlank()) {
                listOf(ApiContentPart.Text(text = textToActuallySend))
            } else {
                emptyList()
            }
            return@withContext AttachmentProcessingResult(success = true, apiContentParts = apiParts)
        }

        val processedAttachmentsForUi = mutableListOf<SelectedMediaItem>()
        val imageUriStringsForUi = mutableListOf<String>()
        val apiContentParts = mutableListOf<ApiContentPart>()

        if (shouldUsePartsApiMessage) {
            if (textToActuallySend.isNotBlank() || attachments.isNotEmpty()) {
                apiContentParts.add(ApiContentPart.Text(text = textToActuallySend))
            }
        }

        val tempMessageIdForNaming = UUID.randomUUID().toString().take(8)

        for ((index, originalMediaItem) in attachments.withIndex()) {
            val itemUri = when (originalMediaItem) {
                is SelectedMediaItem.ImageFromUri -> originalMediaItem.uri
                is SelectedMediaItem.GenericFile -> originalMediaItem.uri
                is SelectedMediaItem.ImageFromBitmap -> Uri.EMPTY
            }
            val originalFileNameForHint = getFileName(application.contentResolver, itemUri)
                ?: (originalMediaItem as? SelectedMediaItem.GenericFile)?.displayName
                ?: (if (originalMediaItem is SelectedMediaItem.ImageFromBitmap) "camera_shot" else "attachment")

            val persistentFilePath: String? = when (originalMediaItem) {
                is SelectedMediaItem.ImageFromUri -> {
                    val bitmap = loadAndCompressBitmapFromUri(application, originalMediaItem.uri)
                    if (bitmap != null) {
                        saveBitmapToAppInternalStorage(application, bitmap, tempMessageIdForNaming, index, originalFileNameForHint)
                    } else {
                        showSnackbar("无法加载或压缩图片: $originalFileNameForHint")
                        return@withContext AttachmentProcessingResult(success = false)
                    }
                }
                is SelectedMediaItem.ImageFromBitmap -> {
                    saveBitmapToAppInternalStorage(application, originalMediaItem.bitmap, tempMessageIdForNaming, index, originalFileNameForHint)
                }
                is SelectedMediaItem.GenericFile -> {
                    copyUriToAppInternalStorage(application, originalMediaItem.uri, tempMessageIdForNaming, index, originalMediaItem.displayName)
                }
            }

            if (persistentFilePath == null) {
                showSnackbar("无法处理附件: $originalFileNameForHint")
                return@withContext AttachmentProcessingResult(success = false)
            }

            val persistentFile = File(persistentFilePath)
            val authority = "${application.packageName}.provider"
            val persistentFileProviderUri = FileProvider.getUriForFile(application, authority, persistentFile)

            val processedItemForUi: SelectedMediaItem = when (originalMediaItem) {
                is SelectedMediaItem.ImageFromUri, is SelectedMediaItem.ImageFromBitmap -> {
                    imageUriStringsForUi.add(persistentFileProviderUri.toString())
                    SelectedMediaItem.ImageFromUri(persistentFileProviderUri, persistentFilePath)
                }
                is SelectedMediaItem.GenericFile -> SelectedMediaItem.GenericFile(
                    uri = persistentFileProviderUri,
                    displayName = originalMediaItem.displayName,
                    mimeType = originalMediaItem.mimeType,
                    filePath = persistentFilePath
                )
            }
            processedAttachmentsForUi.add(processedItemForUi)

            if (shouldUsePartsApiMessage) {
                try {
                    val mimeTypeForApi = application.contentResolver.getType(persistentFileProviderUri)
                        ?: (processedItemForUi as? SelectedMediaItem.GenericFile)?.mimeType
                        ?: "application/octet-stream"

                    val supportedImageMimesForGemini = listOf("image/png", "image/jpeg", "image/webp", "image/heic", "image/heif")
                    val supportedAudioMimesForGemini = listOf("audio/mp3", "audio/mpeg", "audio/wav", "audio/x-wav", "audio/aac", "audio/ogg", "audio/opus", "audio/flac", "audio/amr", "audio/aiff", "audio/x-m4a")
                    val allSupportedInlineMimes = supportedImageMimesForGemini + supportedAudioMimesForGemini

                    if (mimeTypeForApi.lowercase() in allSupportedInlineMimes) {
                        val fileSize = application.contentResolver.openFileDescriptor(persistentFileProviderUri, "r")?.use { it.statSize } ?: -1L
                        if (fileSize != -1L && fileSize <= MAX_IMAGE_SIZE_BYTES) {
                            application.contentResolver.openInputStream(persistentFileProviderUri)?.use { inputStream ->
                                val bytes = inputStream.readBytes()
                                apiContentParts.add(ApiContentPart.InlineData(Base64.encodeToString(bytes, Base64.NO_WRAP), mimeTypeForApi))
                            }
                        } else {
                            apiContentParts.add(ApiContentPart.FileUri(uri = persistentFileProviderUri.toString(), mimeType = mimeTypeForApi))
                        }
                    } else if (processedItemForUi is SelectedMediaItem.GenericFile) {
                        apiContentParts.add(ApiContentPart.FileUri(uri = persistentFileProviderUri.toString(), mimeType = mimeTypeForApi))
                    }
                } catch (e: Exception) {
                }
            }
        }
        AttachmentProcessingResult(true, processedAttachmentsForUi, imageUriStringsForUi, apiContentParts)
    }

    fun sendMessage(
        messageText: String,
        isFromRegeneration: Boolean = false,
        attachments: List<SelectedMediaItem> = emptyList()
    ) {
        val textToActuallySend = messageText.trim()

        if (textToActuallySend.isBlank() && attachments.isEmpty()) {
            viewModelScope.launch { showSnackbar("请输入消息内容或选择项目") }
            return
        }
        val currentConfig = stateHolder._selectedApiConfig.value ?: run {
            viewModelScope.launch { showSnackbar("请先选择 API 配置") }
            return
        }

        viewModelScope.launch {
            val modelIsGeminiType = currentConfig.model.lowercase().startsWith("gemini")
            val shouldUsePartsApiMessage = currentConfig.provider.equals("google", ignoreCase = true) && modelIsGeminiType
            val providerForRequestBackend = currentConfig.provider

            val attachmentResult = processAttachments(attachments, shouldUsePartsApiMessage, textToActuallySend)
            if (!attachmentResult.success) {
                return@launch
            }

            val attachmentsForApiClient = if (shouldUsePartsApiMessage) {
                emptyList()
            } else {
                attachments.toList()
            }

            val newUserMessageForUi = UiMessage(
                id = "user_${UUID.randomUUID()}", text = textToActuallySend, sender = UiSender.User,
                timestamp = System.currentTimeMillis(), contentStarted = true,
                imageUrls = attachmentResult.imageUriStringsForUi.ifEmpty { null },
                attachments = attachmentResult.processedAttachmentsForUi.ifEmpty { null }
            )
 
            withContext(Dispatchers.Main.immediate) {
                stateHolder.messageAnimationStates[newUserMessageForUi.id] = true
                stateHolder.messages.add(newUserMessageForUi)
                if (!isFromRegeneration) {
                   stateHolder._text.value = ""
                   stateHolder.clearSelectedMedia()
                }
                triggerScrollToBottom()
            }

            withContext(Dispatchers.IO) {
                val apiMessagesForBackend = mutableListOf<AbstractApiMessage>()
                val messagesInChatUiSnapshot = stateHolder.messages.toList()
                val historyUiMessages =
                    if (messagesInChatUiSnapshot.lastOrNull()?.id == newUserMessageForUi.id) {
                        messagesInChatUiSnapshot.dropLast(1)
                    } else {
                        messagesInChatUiSnapshot
                    }

                var historyMessageCount = 0
                val maxHistoryMessages = 20
                for (uiMsg in historyUiMessages.asReversed()) {
                    if (historyMessageCount >= maxHistoryMessages) break
                    val roleForHistory = when (uiMsg.sender) {
                        UiSender.User -> "user"
                        UiSender.AI -> "assistant"
                        UiSender.System -> "system"
                        UiSender.Tool -> "tool"
                    }

                    val hasContent = uiMsg.text.isNotBlank() || !uiMsg.attachments.isNullOrEmpty()
                    if (!hasContent) continue

                    if (shouldUsePartsApiMessage) {
                        val parts = mutableListOf<ApiContentPart>()
                        if (uiMsg.text.isNotBlank()) {
                            parts.add(ApiContentPart.Text(text = uiMsg.text.trim()))
                        }
                        uiMsg.attachments?.forEach { attachment ->
                            if (attachment is SelectedMediaItem.ImageFromUri) {
                                try {
                                    application.contentResolver.openInputStream(attachment.uri)?.use { inputStream ->
                                        val bytes = inputStream.readBytes()
                                        if (bytes.isNotEmpty() && bytes.size <= MAX_IMAGE_SIZE_BYTES) {
                                            val mimeType = application.contentResolver.getType(attachment.uri) ?: "image/jpeg"
                                            val supportedImageMimesForGemini = listOf("image/png", "image/jpeg", "image/webp", "image/heic", "image/heif")
                                            if (mimeType.lowercase() in supportedImageMimesForGemini) {
                                                parts.add(ApiContentPart.InlineData(Base64.encodeToString(bytes, Base64.NO_WRAP), mimeType))
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                }
                            }
                        }

                        if (parts.isNotEmpty()) {
                            apiMessagesForBackend.add(0, PartsApiMessage(role = roleForHistory, parts = parts))
                            historyMessageCount++
                        }
                    } else {
                        if (uiMsg.text.isNotBlank()) {
                            apiMessagesForBackend.add(
                                0,
                                SimpleTextApiMessage(
                                    role = roleForHistory,
                                    content = uiMsg.text.trim()
                                )
                            )
                            historyMessageCount++
                        }
                    }
                }

                if (shouldUsePartsApiMessage) {
                    val currentUserParts = attachmentResult.apiContentParts
                    if (currentUserParts.isNotEmpty()) {
                        apiMessagesForBackend.add(PartsApiMessage("user", currentUserParts))
                    }
                } else {
                    if (textToActuallySend.isNotBlank() || attachments.isNotEmpty()) {
                        apiMessagesForBackend.add(SimpleTextApiMessage("user", textToActuallySend))
                    }
                }

                if (apiMessagesForBackend.isEmpty() || apiMessagesForBackend.lastOrNull()?.role != "user") {
                    withContext(Dispatchers.Main.immediate) {
                        stateHolder.messages.remove(newUserMessageForUi)
                        stateHolder.messageAnimationStates.remove(newUserMessageForUi.id)
                    }
                    return@withContext
                }

                val chatRequestForApi = ChatRequest(
                    messages = apiMessagesForBackend,
                    provider = providerForRequestBackend,
                    apiAddress = currentConfig.address,
                    apiKey = currentConfig.key,
                    model = currentConfig.model,
                    useWebSearch = stateHolder._isWebSearchEnabled.value,
                    generationConfig = GenerationConfig(
                        temperature = currentConfig.temperature,
                        topP = currentConfig.topP,
                        maxOutputTokens = currentConfig.maxTokens,
                        thinkingConfig = if (modelIsGeminiType) ThinkingConfig(
                            includeThoughts = true,
                            thinkingBudget = if (currentConfig.model.contains(
                                    "flash",
                                    ignoreCase = true
                                )
                            ) 1024 else null
                        ) else null
                    ).let { if (it.temperature != null || it.topP != null || it.maxOutputTokens != null || it.thinkingConfig != null) it else null },
                    qwenEnableSearch = if (currentConfig.model.lowercase()
                            .contains("qwen")
                    ) stateHolder._isWebSearchEnabled.value else null,
                )

                apiHandler.streamChatResponse(
                    requestBody = chatRequestForApi,
                    attachmentsToPassToApiClient = attachmentsForApiClient,
                    applicationContextForApiClient = application,
                    userMessageTextForContext = textToActuallySend,
                    afterUserMessageId = newUserMessageForUi.id,
                    onMessagesProcessed = { viewModelScope.launch { historyManager.saveCurrentChatToHistoryIfNeeded() } },
                    onRequestFailed = { error ->
                        viewModelScope.launch(Dispatchers.Main) {
                            val errorMessage = "发送失败: ${error.message ?: "未知错误"}"
                            showSnackbar(errorMessage)
                        }
                    }
                )
            }
        }
    }

    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String? {
        if (uri == Uri.EMPTY) return null
        var fileName: String? = null
        try {
            if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val displayNameIndex =
                                cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                            fileName = cursor.getString(displayNameIndex)
                        }
                    }
            }
            if (fileName == null) {
                fileName = uri.lastPathSegment
            }
        } catch (e: Exception) {
            fileName = uri.lastPathSegment
        }
        return fileName ?: "file_${System.currentTimeMillis()}"
    }
}