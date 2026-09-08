package com.android.everytalk.data.computer

/** 受保护服务器环境变量写入的本地参数与脚本构造，绝不记录 Secret 值。 */
internal object ComputerSecretEnvWriter {
    fun requireEnvPath(path: String): String {
        val value = path.trim()
        require(value.isNotEmpty() && value.length <= 4096 && '\u0000' !in value && '\n' !in value && '\r' !in value) {
            "环境文件路径无效"
        }
        require(value == ".env" || value.endsWith("/.env")) { "只能写入 .env 文件" }
        require(!value.split('/').any { it == ".." }) { "环境文件路径不能包含父目录" }
        return value
    }

    /** 固定脚本只从 stdin 读取值，Secret 不会出现在命令行或日志中。 */
    fun buildUpsertCommand(path: String, name: String): String {
        val safePath = shellQuote(requireEnvPath(path))
        ComputerEnvironmentName.requireValid(name)
        val safeName = shellQuote(name)
        return "umask 077; f=$safePath; n=$safeName; d=\"${'$'}f.tmp.${'$'}${'$'}\"; " +
            "v=\"\$(cat)\"; " +
            "if [ -f \"${'$'}f\" ]; then grep -v -E \"^[[:space:]]*\${'$'}n=\" \"${'$'}f\" > \"${'$'}d\"; else : > \"${'$'}d\"; fi; " +
            "printf '%s=%s\\n' \"${'$'}n\" \"${'$'}v\" >> \"${'$'}d\"; " +
            "chmod 600 \"${'$'}d\"; mv -f \"${'$'}d\" \"${'$'}f\""
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}
