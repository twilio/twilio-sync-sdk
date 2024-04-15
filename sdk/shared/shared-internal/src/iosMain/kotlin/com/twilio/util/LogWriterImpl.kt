package com.twilio.util

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal actual object LogWriterImpl : LogWriter {

    actual override fun v(tag: String, msg: String, t: Throwable?) = writeLog(tag, msg, t)

    actual override fun d(tag: String, msg: String, t: Throwable?) = writeLog(tag, msg, t)

    actual override fun i(tag: String, msg: String, t: Throwable?) = writeLog(tag, msg, t)

    actual override fun w(tag: String, msg: String, t: Throwable?) = writeLog(tag, msg, t)

    actual override fun e(tag: String, msg: String, t: Throwable?) = writeLog(tag, msg, t)

    private fun writeLog(tag: String, msg: String, t: Throwable?) {
        val timestamp = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        println("[$timestamp] $tag $msg ${t?.stackTraceToString() ?: ""}")
    }
}
