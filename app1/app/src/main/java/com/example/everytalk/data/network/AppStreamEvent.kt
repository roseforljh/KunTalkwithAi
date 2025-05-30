package com.example.everytalk.data.DataClass // 请替换为您的实际包名

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject // 如果 argumentsObj 确实是 JsonObject

/**
 * 代表从后端代理服务接收到的一个流式服务器发送事件 (SSE)。
 * 这个类现在更通用，以匹配后端自定义的SSE事件结构。
 */
@Serializable
data class AppStreamEvent(
    @SerialName("type")
    val type: String, // 事件类型，例如: "status_update", "web_search_results", "content", "reasoning", "tool_calls_chunk", "google_function_call_request", "finish", "error"

    // --- 对应 "status_update" 类型 ---
    @SerialName("stage")
    val stage: String? = null, // 例如: "web_indexing_started", "web_analysis_started", "web_analysis_complete"

    // --- 对应 "web_search_results" 类型 ---
    @SerialName("results")
    val results: List<WebSearchResult>? = null, // WebSearchResult 来自 Message.kt 或单独定义

    // --- 对应 "content" (最终答案) 和 "reasoning" (思考过程) 类型 ---
    @SerialName("text")
    val text: String? = null, // 用于 "content" 和 "reasoning" 事件的文本内容

    // --- 对应 "tool_calls_chunk" 类型 (来自OpenAI兼容流的工具调用) ---
    @SerialName("data") // 后端 process_openai_sse_line_standard 和引导式逻辑中用的是 "data" 字段
    val toolCallsData: List<OpenAiToolCall>? = null, // OpenAiToolCall 的定义应保持一致

    // --- 对应 "google_function_call_request" 类型 ---
    @SerialName("id") // 也可用于 google_function_call_request 的 id (后端已使用，例如 gemini_fc_...)
    val id: String? = null,
    @SerialName("name") // 用于 google_function_call_request 的 name (也可能用于OpenAiFunctionCall中的name)
    val name: String? = null,
    @SerialName("arguments_obj") // 用于 google_function_call_request 的 arguments_obj
    val argumentsObj: JsonObject? = null, // 假设参数是JSON对象

    // --- 新增：用于标记工具调用是否属于思考步骤 (由引导式推理逻辑添加) ---
    @SerialName("is_reasoning_step") // 对应后端 "is_reasoning_step"
    val isReasoningStep: Boolean? = null, // 可选布尔值

    // --- 对应 "finish" 类型 ---
    @SerialName("reason")
    val reason: String? = null, // 例如: "stop", "length", "tool_calls", "error", "timeout_error", "network_error"

    // --- 对应 "error" 类型 ---
    @SerialName("message")
    val message: String? = null, // 错误消息
    @SerialName("upstream_status")
    val upstreamStatus: Int? = null, // 上游API的错误状态码

    // --- 通用时间戳 (后端已为所有事件添加) ---
    @SerialName("timestamp")
    val timestamp: String? = null
)

// OpenAiToolCall 和 OpenAiFunctionCall 的定义保持不变 (确保字段名与后端一致)
@Serializable
data class OpenAiToolCall(
    @SerialName("index") // 后端原始OpenAI tool_call包含index, 但在delta中不一定有，取决于具体实现
    val index: Int? = null,
    @SerialName("id")
    val id: String? = null, // 工具调用的唯一ID
    @SerialName("type")
    val type: String? = null, // 通常是 "function"
    @SerialName("function")
    val function: OpenAiFunctionCall? = null // 函数调用详情
)

@Serializable
data class OpenAiFunctionCall(
    @SerialName("name")
    val name: String? = null, // 函数名称
    @SerialName("arguments")
    val arguments: String? = null // 函数参数，通常是原始JSON字符串
)