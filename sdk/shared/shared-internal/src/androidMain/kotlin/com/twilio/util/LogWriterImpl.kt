package com.twilio.util

import android.util.Log
import java.io.File
import java.util.logging.FileHandler
import java.util.logging.LogRecord

internal actual object LogWriterImpl : LogWriter {
    private const val kCurrentLogFileName = "current.log"
    private val dir: File by lazy { ApplicationContextHolder.applicationContext.filesDir }
    private val fh: FileHandler by lazy {
        rotateLogFile()
        FileHandler(File(dir, kCurrentLogFileName).absolutePath).apply {
            formatter = CustomFormatter()
        }
    }

    actual override fun v(tag: String, msg: String, t: Throwable?) {
        Log.v(tag, msg, t)
        printLogInFile(LoggingLevel.VERBOSE, tag, msg, t)
    }

    actual override fun d(tag: String, msg: String, t: Throwable?) {
        Log.d(tag, msg, t)
        printLogInFile(LoggingLevel.DEBUG, tag, msg, t)
    }

    actual override fun i(tag: String, msg: String, t: Throwable?) {
        Log.i(tag, msg, t)
        printLogInFile(LoggingLevel.INFO, tag, msg, t)
    }

    actual override fun w(tag: String, msg: String, t: Throwable?) {
        Log.w(tag, msg, t)
        printLogInFile(LoggingLevel.WARN, tag, msg, t)
    }

    actual override fun e(tag: String, msg: String, t: Throwable?) {
        Log.e(tag, msg, t)
        printLogInFile(LoggingLevel.ERROR, tag, msg, t)
    }

    private fun rotateLogFile() {
        val backupFileName = "log.bak"
        val lastBackupFile = File(dir, backupFileName)
        if (lastBackupFile.exists()) {
            lastBackupFile.delete()
        }
        val lastLogFile = File(dir, kCurrentLogFileName)
        if (lastLogFile.exists()) {
            val newBackupFile = File(dir, backupFileName)
            lastLogFile.renameTo(newBackupFile)
        }
    }

    private fun printLogInFile(
        loggingLevel: LoggingLevel,
        tag: String,
        msg: String,
        t: Throwable?
    ) {
        val logRecord = LogRecord(loggingLevel, String.format("%s: %s", tag, msg))
        logRecord.thrown = t
        fh.publish(logRecord)
    }
}
