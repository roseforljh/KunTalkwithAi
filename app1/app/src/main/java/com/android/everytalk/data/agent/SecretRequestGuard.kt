package com.android.everytalk.data.agent

/**
 * 拦截模型通过普通文本索要凭据的明显行为。
 * 这是协议兜底，不尝试从文本中提取 Secret，也不自动猜测用户意图。
 */
internal object SecretRequestGuard {
    private val requestWords = listOf("发给我", "发送给我", "粘贴", "贴出来", "提供给我", "告诉我", "直接发")
    private val secretWords = listOf("api key", "apikey", "token", "密码", "口令", "私钥", "secret", "密钥", "key")

    fun isPlainTextSecretRequest(text: String): Boolean {
        val normalized = text.lowercase()
        return requestWords.any(normalized::contains) && secretWords.any(normalized::contains)
    }
}
