package com.example.everytalk.ui.screens.BubbleMain.Main // 您的实际包名

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ContentPaste // MyCodeBlockComposable 需要
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight // MyCodeBlockComposable 需要
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.everytalk.data.DataClass.Message
import com.example.everytalk.ui.screens.BubbleMain.ThreeDotsLoadingAnimation

// 从 BubbleMain.kt 移过来的常量 (用于上下文菜单)
private const val CONTEXT_MENU_ANIMATION_DURATION_MS = 150 // 上下文菜单动画持续时间（毫秒）
private val CONTEXT_MENU_CORNER_RADIUS = 16.dp             // 上下文菜单圆角半径
private val CONTEXT_MENU_ITEM_ICON_SIZE = 20.dp            // 上下文菜单项图标大小
private val CONTEXT_MENU_FINE_TUNE_OFFSET_X = (-120).dp    // 上下文菜单X轴微调偏移量
private val CONTEXT_MENU_FINE_TUNE_OFFSET_Y = (-8).dp     // 上下文菜单Y轴微调偏移量
private val CONTEXT_MENU_FIXED_WIDTH = 120.dp              // 上下文菜单固定宽度


/**
 * Composable 用于渲染用户消息或 AI 错误消息的内容。
 */
@Composable
internal fun UserOrErrorMessageContent(
    message: Message,                // 完整消息对象，用于上下文菜单等
    displayedText: String,           // 当前要显示的文本
    showLoadingDots: Boolean,        // 是否显示加载点（通常仅用于用户消息发送前的短暂状态）
    bubbleColor: Color,              // 气泡背景色
    contentColor: Color,             // 内容颜色（普通用户文本色或AI错误文本色）
    isError: Boolean,                // 指示此消息是否为AI的错误消息
    maxWidth: Dp,                    // 气泡最大宽度
    onUserInteraction: () -> Unit,   // 用户交互回调
    onEditRequest: (Message) -> Unit, // 请求编辑消息的回调
    onRegenerateRequest: (Message) -> Unit, // 请求重新生成AI回答的回调
    modifier: Modifier = Modifier      // 应用于此Composable根Box的修饰符
) {
    // 控制上下文菜单的可见性
    var isContextMenuVisible by remember(message.id) { mutableStateOf(false) }
    // 记录长按位置，用于定位上下文菜单
    var pressOffset by remember(message.id) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current // 获取当前屏幕密度
    val context = LocalContext.current // 获取当前上下文
    val clipboardManager = LocalClipboardManager.current // 获取剪贴板管理器

    Box( // UserOrErrorMessageContent 的根是一个 Box
        modifier = modifier // 应用外部传入的修饰符 (通常不包含 align，对齐由 MessageBubble 控制)
            .widthIn(max = maxWidth) // 限制最大宽度
            .padding(horizontal = 8.dp, vertical = 2.dp) // 外边距
    ) {
        Surface( // 消息内容的 Surface
            color = bubbleColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(18.dp), // 圆角形状
            tonalElevation = if (isError) 0.dp else 1.dp, // 错误消息通常无抬高，用户消息略有抬高
            modifier = Modifier
                .pointerInput(message.id) { // 添加触摸输入处理器
                    detectTapGestures( // 检测手势
                        onLongPress = { offset -> // 长按手势
                            onUserInteraction() // 触发用户交互回调
                            if (!isError) { // 仅非AI错误的用户消息显示上下文菜单
                                pressOffset = offset // 记录按压位置
                                isContextMenuVisible = true // 显示上下文菜单
                            }
                        }
                    )
                }
        ) {
            Box( // 内容容器，用于对齐和最小尺寸
                contentAlignment = Alignment.CenterStart, // 内容向起始位置对齐
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp) // 内边距
                    .wrapContentWidth() // 包裹内容宽度
                    .defaultMinSize(minHeight = 28.dp) // 最小高度
            ) {
                if (showLoadingDots && !isError) { // 如果是用户消息且需要显示加载点（例如，发送中）
                    ThreeDotsLoadingAnimation( // 三点加载动画
                        dotColor = contentColor,
                        modifier = Modifier
                            .align(Alignment.Center) // 在Box内居中
                            .offset(y = (-6).dp)     // 视觉微调
                    )
                } else if (displayedText.isNotBlank() || isError) { // 如果有文本内容或这是一个错误消息
                    Text(
                        text = displayedText.trim(), // 显示裁剪后的文本
                        textAlign = TextAlign.Start, // 文本左对齐
                        color = contentColor         // 应用指定的文本颜色
                    )
                }
            }
        }

        // 上下文菜单 (仅用户消息且非错误时显示)
        if (isContextMenuVisible && !isError) {
            val dropdownMenuOffsetX =
                with(density) { pressOffset.x.toDp() } + CONTEXT_MENU_FINE_TUNE_OFFSET_X // 计算X轴偏移
            val dropdownMenuOffsetY =
                with(density) { pressOffset.y.toDp() } + CONTEXT_MENU_FINE_TUNE_OFFSET_Y // 计算Y轴偏移

            Popup( // 弹出层实现上下文菜单
                alignment = Alignment.TopStart, // 对齐方式
                offset = IntOffset( // 精确偏移
                    x = with(density) { dropdownMenuOffsetX.roundToPx() },
                    y = with(density) { dropdownMenuOffsetY.roundToPx() }),
                onDismissRequest = { isContextMenuVisible = false }, // 关闭请求
                properties = PopupProperties( // 弹出层属性
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    clippingEnabled = false // 通常设为false以允许阴影等效果超出边界
                )
            ) {
                Surface( // 菜单背景
                    shape = RoundedCornerShape(CONTEXT_MENU_CORNER_RADIUS), // 圆角
                    color = Color.White, // 背景色
                    tonalElevation = 0.dp, // 通常上下文菜单本身不需要色调抬高，由阴影提供层次
                    modifier = Modifier
                        .width(CONTEXT_MENU_FIXED_WIDTH) // 固定宽度
                        .shadow( // 添加阴影
                            elevation = 8.dp,
                            shape = RoundedCornerShape(CONTEXT_MENU_CORNER_RADIUS)
                        )
                        .padding(1.dp) // 轻微内边距，避免内容紧贴边缘
                ) {
                    Column { // 菜单项垂直排列
                        val menuVisibility =
                            remember { MutableTransitionState(false) } // 菜单项动画可见性状态
                        LaunchedEffect(isContextMenuVisible) { // 当isContextMenuVisible变化时更新动画目标状态
                            menuVisibility.targetState = isContextMenuVisible
                        }

                        // 封装的带动画的下拉菜单项
                        @Composable
                        fun AnimatedDropdownMenuItem(
                            visibleState: MutableTransitionState<Boolean>,
                            delay: Int = 0, // 动画延迟
                            text: @Composable () -> Unit, // 文本内容
                            onClick: () -> Unit, // 点击回调
                            leadingIcon: @Composable (() -> Unit)? = null // 前置图标
                        ) {
                            AnimatedVisibility( // 控制菜单项的动画显示/隐藏
                                visibleState = visibleState,
                                enter = fadeIn( // 进入动画：淡入 + 缩放
                                    animationSpec = tween(
                                        CONTEXT_MENU_ANIMATION_DURATION_MS,
                                        delayMillis = delay,
                                        easing = LinearOutSlowInEasing
                                    )
                                ) +
                                        scaleIn(
                                            animationSpec = tween(
                                                CONTEXT_MENU_ANIMATION_DURATION_MS,
                                                delayMillis = delay,
                                                easing = LinearOutSlowInEasing
                                            ), transformOrigin = TransformOrigin(0f, 0f) // 缩放起始点
                                        ),
                                exit = fadeOut( // 退出动画：淡出 + 缩放
                                    animationSpec = tween(
                                        CONTEXT_MENU_ANIMATION_DURATION_MS,
                                        easing = FastOutLinearInEasing
                                    )
                                ) +
                                        scaleOut(
                                            animationSpec = tween(
                                                CONTEXT_MENU_ANIMATION_DURATION_MS,
                                                easing = FastOutLinearInEasing
                                            ), transformOrigin = TransformOrigin(0f, 0f)
                                        )
                            ) {
                                DropdownMenuItem( // Material 3 下拉菜单项
                                    text = text, onClick = onClick, leadingIcon = leadingIcon,
                                    colors = MenuDefaults.itemColors( // 自定义颜色
                                        textColor = MaterialTheme.colorScheme.onSurface,
                                        leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        // 复制操作
                        AnimatedDropdownMenuItem(
                            menuVisibility,
                            text = { Text("复制") },
                            onClick = {
                                clipboardManager.setText(AnnotatedString(message.text)) // 复制消息文本
                                Toast.makeText(context, "内容已复制", Toast.LENGTH_SHORT).show()
                                isContextMenuVisible = false // 关闭菜单
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    "复制",
                                    Modifier.size(CONTEXT_MENU_ITEM_ICON_SIZE)
                                )
                            })

                        // 编辑操作
                        AnimatedDropdownMenuItem(
                            menuVisibility,
                            delay = 30, // 延迟出现，形成交错动画
                            text = { Text("编辑") },
                            onClick = {
                                onEditRequest(message) // 请求编辑
                                isContextMenuVisible = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Edit,
                                    "编辑",
                                    Modifier.size(CONTEXT_MENU_ITEM_ICON_SIZE)
                                )
                            })

                        // 重新回答操作
                        AnimatedDropdownMenuItem(
                            menuVisibility,
                            delay = 60,
                            text = { Text("重新回答") },
                            onClick = {
                                onRegenerateRequest(message) // 请求重新生成
                                isContextMenuVisible = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Refresh,
                                    "重新回答",
                                    Modifier.size(CONTEXT_MENU_ITEM_ICON_SIZE)
                                )
                            })
                    }
                }
            }
        }
    }
}





// --- MarkdownUtils.kt 内容 ---
// package com.example.com.aiwithtalk.com.talkwithai.everytalk.ui.screens.BubbleMain // 替换为你的实际包名基础 + .util.markdown

// (MarkdownUtils 的 import 已经在文件顶部处理)

// --- 文本片段数据类定义 ---
sealed class TextSegment { // 密封类，表示文本可以被分割成的不同类型的片段
    data class Normal(val text: String) : TextSegment() // 普通文本片段
    data class CodeBlock(val language: String?, val code: String) : TextSegment() // 代码块片段，包含语言和代码内容
}

// --- 预编译的正则表达式 ---
// GFM (GitHub Flavored Markdown) 风格的闭合代码块正则表达式：
// 捕获语言提示（可选）和代码块内容。
// ```([a-zA-Z0-9_.-]*)      : 捕获组1，语言提示，允许字母、数字、下划线、点、中横线
// [ \t]*\n                 : 匹配可选的空格或制表符后跟一个换行符
// ([\s\S]*?)               : 捕获组2，代码内容，非贪婪匹配任何字符（包括换行符）
// \n```                    : 匹配一个换行符后跟三个反引号作为代码块结束
private val GFM_CLOSED_CODE_BLOCK_REGEX =
    Regex("```([a-zA-Z0-9_.-]*)[ \t]*\\n([\\s\\S]*?)\\n```")

// 匹配代码块开始标记 "```" 的正则表达式，用于处理剩余文本中可能的未闭合代码块
private val CODE_BLOCK_START_REGEX = Regex("```")

/**
 * 优化版的 Markdown 解析函数。
 * 将输入的Markdown字符串分割成 TextSegment 对象列表。
 * 它能识别GFM风格的闭合代码块 (例如 ```python\ncode\n```) 以及在文本末尾可能存在的未闭合代码块。
 */
fun parseMarkdownSegments(markdownInput: String): List<TextSegment> {
    if (markdownInput.isBlank()) { // 如果输入为空或仅包含空白字符，则返回空列表
        return emptyList()
    }

    val segments = mutableListOf<TextSegment>() // 用于存储解析出的片段
    var currentIndex = 0 // 当前在输入字符串中的处理位置

    Log.d(
        "ParseMarkdownOpt", // 日志标签
        "输入 (长度 ${markdownInput.length}):\nSTART_MD\n${ // 日志：记录输入文本（部分）
            markdownInput.take(200).replace("\n", "\\n") // 预览前200字符，换行符转义
        }...\nEND_MD"
    )

    // 查找第一个GFM风格的闭合代码块
    var matchResult = GFM_CLOSED_CODE_BLOCK_REGEX.find(markdownInput, currentIndex)

    while (matchResult != null) { // 当找到闭合代码块时循环
        val matchStart = matchResult.range.first // 匹配到的代码块的起始索引
        val matchEnd = matchResult.range.last   // 匹配到的代码块的结束索引

        Log.d(
            "ParseMarkdownOpt",
            "找到闭合代码块. 范围: ${matchResult.range}. 当前索引(之前): $currentIndex"
        )

        // 1. 处理当前查找到的闭合代码块之前的普通文本部分
        if (matchStart > currentIndex) { // 如果代码块不是从当前索引开始，则它们之间是普通文本
            val normalText = markdownInput.substring(currentIndex, matchStart)
            if (normalText.isNotBlank()) { // 仅当普通文本非纯空白时才添加
                segments.add(TextSegment.Normal(normalText.trim())) // 添加裁剪过的普通文本片段
                Log.d(
                    "ParseMarkdownOpt",
                    "添加普通文本 (闭合块之前): '${ // 日志：记录添加的普通文本
                        normalText.trim().take(50).replace("\n", "\\n")
                    }'"
                )
            }
        }

        // 2. 处理当前找到的闭合代码块
        val language = matchResult.groups[1]?.value?.trim()
            ?.takeIf { it.isNotEmpty() } // 提取语言提示 (捕获组1)，并去除首尾空白，如果结果非空则使用
        val code = matchResult.groups[2]?.value ?: "" // 提取代码内容 (捕获组2)，代码内容保持原始格式（不trim）
        segments.add(TextSegment.CodeBlock(language, code)) // 添加代码块片段
        Log.d(
            "ParseMarkdownOpt",
            "添加闭合代码块: 语言='$language', 代码='${
                code.take(50).replace("\n", "\\n")
            }'" // 日志：记录添加的代码块
        )

        currentIndex = matchEnd + 1 // 更新当前处理位置到此代码块之后
        matchResult = GFM_CLOSED_CODE_BLOCK_REGEX.find(markdownInput, currentIndex) // 继续查找下一个闭合代码块
    }

    Log.d(
        "ParseMarkdownOpt",
        "闭合代码块循环结束. 当前索引: $currentIndex, Markdown长度: ${markdownInput.length}"
    )

    // 3. 处理最后一个闭合代码块之后剩余的文本
    if (currentIndex < markdownInput.length) { // 如果在整个输入字符串的末尾还有剩余文本
        val remainingText = markdownInput.substring(currentIndex) // 获取剩余文本
        Log.d(
            "ParseMarkdownOpt",
            "剩余文本 (长度 ${remainingText.length}): '${ // 日志：记录剩余文本
                remainingText.take(100).replace("\n", "\\n")
            }'"
        )

        // 尝试在剩余文本中查找未闭合的代码块开始标记 "```"
        val openBlockMatch = CODE_BLOCK_START_REGEX.find(remainingText)
        if (openBlockMatch != null) { // 如果找到了 "```"
            val openBlockStartInRemaining = openBlockMatch.range.first // "```" 在剩余文本中的起始位置

            // a. "```" 之前的部分是普通文本
            if (openBlockStartInRemaining > 0) {
                val normalPrefix = remainingText.substring(0, openBlockStartInRemaining)
                if (normalPrefix.isNotBlank()) {
                    segments.add(TextSegment.Normal(normalPrefix.trim())) // 添加普通文本片段
                    Log.d(
                        "ParseMarkdownOpt",
                        "添加普通文本 (开放代码块前缀): '${ // 日志
                            normalPrefix.trim().take(50).replace("\n", "\\n")
                        }'"
                    )
                }
            }

            // b. "```" 之后的部分被视为未闭合代码块的内容
            val codeBlockCandidate =
                remainingText.substring(openBlockStartInRemaining + 3) // 跳过 "```" 本身
            // 尝试从此部分提取语言提示 (通常是 "```" 后的第一行)
            val firstNewlineIndex = codeBlockCandidate.indexOf('\n')
            var lang: String? = null
            var codeContent: String

            if (firstNewlineIndex != -1) { // 如果 "```lang" 这种形式后面有换行符
                val langLine = codeBlockCandidate.substring(0, firstNewlineIndex).trim() // 提取语言行并裁剪
                // 验证语言提示（允许字母、数字、下划线、点、中横线，以及常见的+号如c++）
                if (langLine.all { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' || it == '+' }) {
                    lang = langLine.takeIf { it.isNotEmpty() } // 如果语言行有效且非空，则作为语言
                }
                codeContent = codeBlockCandidate.substring(firstNewlineIndex + 1) // 换行符之后的是代码内容
            } else { // "```lang" 后面没有换行，整行都可能是语言提示，代码内容为空或依赖后续流式输入
                val langLine = codeBlockCandidate.trim() // 整行作为语言候选并裁剪
                if (langLine.all { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' || it == '+' }) {
                    lang = langLine.takeIf { it.isNotEmpty() }
                }
                codeContent = "" // 对于没有换行符跟随的开放代码块，初始代码内容视为空
            }
            segments.add(TextSegment.CodeBlock(lang, codeContent)) // 添加开放代码块片段 (代码内容不trim)
            Log.d(
                "ParseMarkdownOpt",
                "从剩余文本添加开放代码块: 语言='$lang', 代码预览='${ // 日志
                    codeContent.take(50).replace("\n", "\\n")
                }'"
            )

        } else { // 剩余文本中没有 "```"，因此全部是普通文本
            if (remainingText.isNotBlank()) {
                segments.add(TextSegment.Normal(remainingText.trim()))
                Log.d(
                    "ParseMarkdownOpt",
                    "剩余文本是普通文本: '${ // 日志
                        remainingText.trim().take(50).replace("\n", "\\n")
                    }'"
                )
            }
        }
    }

    // 特殊处理：如果经过上述所有步骤后，segments 仍然为空，
    // 但原始 markdownInput 并非空白，这可能意味着整个输入是单一类型的文本。
    if (segments.isEmpty() && markdownInput.isNotBlank()) {
        Log.w(
            "ParseMarkdownOpt", // 警告日志
            "片段列表为空，但Markdown非空白. Markdown是否以 '```' 开头: ${ // 日志
                markdownInput.startsWith("```")
            }"
        )
        // 这种情况通常意味着整个输入是一个未闭合的代码块（且没有前导文本），
        // 或者整个输入就是一段普通文本。
        if (markdownInput.startsWith("```")) { // 如果整个输入以 "```" 开头
            val codeBlockCandidate = markdownInput.substring(3) // 获取 "```" 之后的内容
            val firstNewlineIndex = codeBlockCandidate.indexOf('\n')
            var lang: String? = null
            var codeContent: String
            if (firstNewlineIndex != -1) { // 同上，尝试解析语言和代码
                val langLine = codeBlockCandidate.substring(0, firstNewlineIndex).trim()
                if (langLine.all { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' || it == '+' }) {
                    lang = langLine.takeIf { it.isNotEmpty() }
                }
                codeContent = codeBlockCandidate.substring(firstNewlineIndex + 1)
            } else {
                val langLine = codeBlockCandidate.trim()
                if (langLine.all { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' || it == '+' }) {
                    lang = langLine.takeIf { it.isNotEmpty() }
                }
                codeContent = ""
            }
            segments.add(TextSegment.CodeBlock(lang, codeContent)) // 将整个输入视为一个开放代码块
            Log.d(
                "ParseMarkdownOpt",
                "将整个输入添加为开放代码块: 语言='$lang', 代码预览='${ // 日志
                    codeContent.take(50).replace("\n", "\\n")
                }'"
            )
        } else { // 否则，整个输入就是一段普通文本
            segments.add(TextSegment.Normal(markdownInput.trim()))
            Log.d(
                "ParseMarkdownOpt",
                "整个输入是普通文本 (片段为空且不以 ``` 开头)." // 日志
            )
        }
    }

    Log.i( // 信息日志
        "ParseMarkdownOpt",
        "最终片段数量: ${segments.size}, 类型: ${segments.map { it::class.simpleName }}" // 记录最终片段数量和类型
    )
    return segments // 返回解析出的片段列表
}

/**
 * 从已裁剪且以 "```" 开头的文本中提取流式代码内容和语言提示。
 * 注意：在“相对完美的解决方案”中，AiMessageContent 不再直接调用此函数，
 * 因为 parseMarkdownSegments 已能处理流式和最终的文本。
 * 此函数仍然保留，以防其他地方有特定用途，或者作为解析逻辑的参考。
 */
fun extractStreamingCodeContent(textAlreadyTrimmedAndStartsWithTripleQuote: String): Pair<String?, String> {
    Log.d(
        "ExtractStreamCode", // 日志标签
        "用于提取的输入: \"${ // 日志：记录输入文本（部分）
            textAlreadyTrimmedAndStartsWithTripleQuote.take(30).replace("\n", "\\n")
        }\""
    )
    val contentAfterTripleTicks =
        textAlreadyTrimmedAndStartsWithTripleQuote.substring(3) // 获取 "```" 之后的内容
    val firstNewlineIndex = contentAfterTripleTicks.indexOf('\n') // 查找第一个换行符

    if (firstNewlineIndex != -1) { // 如果找到了换行符 (即 ```lang\ncode 形式)
        val langHint = contentAfterTripleTicks.substring(0, firstNewlineIndex).trim() // 换行符之前的是语言提示
        val code = contentAfterTripleTicks.substring(firstNewlineIndex + 1) // 换行符之后的是代码内容
        // 验证语言提示
        val validatedLangHint =
            if (langHint.all { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' || it == '+' }) {
                langHint.takeIf { it.isNotBlank() } // 有效且非空则采纳
            } else {
                null // 否则视为无语言提示
            }
        return Pair(validatedLangHint, code)
    } else { // 如果 "```" 之后没有换行符 (即 ```lang 或 ```)
        val langLine = contentAfterTripleTicks.trim() // 整行作为语言提示候选
        val validatedLangHint =
            if (langLine.all { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' || it == '+' }) {
                langLine.takeIf { it.isNotBlank() }
            } else {
                null
            }
        return Pair(validatedLangHint, "") // 此时代码内容视为空字符串
    }
}