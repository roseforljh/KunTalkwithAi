package com.android.everytalk.data.computer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputerSecretEnvWriterTest {
    @Test
    fun `脚本不包含 Secret 值且固定写入变量`() {
        val command = ComputerSecretEnvWriter.buildUpsertCommand("/opt/app/.env", "API_KEY")
        assertTrue(command.contains("grep -v"))
        assertTrue(command.contains("API_KEY") || command.contains("'API_KEY'"))
        assertFalse(command.contains("real-secret"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `禁止写入任意文件`() {
        ComputerSecretEnvWriter.requireEnvPath("/etc/passwd")
    }
}
