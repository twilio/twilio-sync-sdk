//
//  Twilio Twilsock Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.util

import androidx.test.platform.app.InstrumentationRegistry
import com.twilio.test.util.runTest
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.fail

class LogWriterTest {
    private val testInfoLog = "test_info_log"
    private var logWriter: LogWriter

    init {
        ApplicationContextHolder.applicationContext =
            InstrumentationRegistry.getInstrumentation().targetContext

        logWriter = LogWriterImpl
    }

    @Test
    fun assertLogFileContent() = runTest {
        logWriter.i(testInfoLog)

        val fileStringList =
            File(ApplicationContextHolder.applicationContext.filesDir, "current.log").readText()
                .split("]")

        assertEquals("INFO: $testInfoLog:", fileStringList[2].trim())
    }

    @Test
    fun settingLoglevelToNonSlient_shouldNotCrashLogger() = runTest {
        TwilioLogger.setLogLevel(TwilioLogger.SILENT)

        try {
            TwilioLogger.setLogLevel(TwilioLogger.INFO)
            logger.i(testInfoLog)
        } catch (e: Exception) {
            fail("Logger crashed")
        }
    }
}
