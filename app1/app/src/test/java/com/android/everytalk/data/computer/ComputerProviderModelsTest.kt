package com.android.everytalk.data.computer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputerProviderModelsTest {
    @Test
    fun `Cloudflare 只声明云资源能力`() {
        assertTrue(CloudflareComputerProviderContract.supports(ComputerCapability.WORKER_DEPLOY))
        assertTrue(CloudflareComputerProviderContract.supports(ComputerCapability.D1_READ))
        assertFalse(CloudflareComputerProviderContract.supports(ComputerCapability.LOCAL_SHELL_EXECUTE))
    }

    @Test
    fun `SSH 不声明 Cloudflare Worker 能力`() {
        assertTrue(SshComputerProviderContract.supports(ComputerCapability.LOCAL_SHELL_EXECUTE))
        assertFalse(SshComputerProviderContract.supports(ComputerCapability.WORKER_DEPLOY))
    }
}
