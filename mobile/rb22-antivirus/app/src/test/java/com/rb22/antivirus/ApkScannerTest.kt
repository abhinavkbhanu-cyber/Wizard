package com.rb22.antivirus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkScannerTest {
    @Test fun flagsSensitiveSmsPermission() {
        assertTrue(ApkScanner.analyzeIndicators(setOf("android.permission.SEND_SMS")).any { it.title == "Sensitive capability" })
    }
    @Test fun flagsSmsPlusBootPermissionCombination() {
        assertTrue(ApkScanner.analyzeIndicators(setOf("android.permission.RECEIVE_SMS", "android.permission.RECEIVE_BOOT_COMPLETED")).any { it.title == "Permission combination" })
    }
    @Test fun flagsDynamicCodeLoadingIndicator() {
        assertTrue(ApkScanner.analyzeIndicators(emptySet(), "dalvik/system/DexClassLoader").any { it.detail.contains("dynamic-code-loading") })
    }
    @Test fun doesNotInventFindingsForEmptyIndicators() {
        assertTrue(ApkScanner.analyzeIndicators(emptySet()).isEmpty())
    }
    @Test fun flagsProcessExecutionReference() {
        assertFalse(ApkScanner.analyzeIndicators(emptySet(), "java/lang/Runtime;->exec").isEmpty())
    }
}
