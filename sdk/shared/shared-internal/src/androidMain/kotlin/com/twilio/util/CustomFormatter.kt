package com.twilio.util

import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Formatter
import java.util.logging.LogRecord

internal class CustomFormatter : Formatter() {
    private val format = "[%1\$s][%2\$s] %3\$s: %4\$s%5\$s%n"

    @Synchronized
    override fun format(
        record: LogRecord
    ): String {
        val dateTimeFormat = SimpleDateFormat(
            "dd MMM yyyy HH:mm:ss:SSS Z"
        )
        val dat = (dateTimeFormat.format(Date(record.millis))).toString()
        val threadId = Thread.currentThread().id
        val message = formatMessage(record)
        val stackTrace = record.thrown?.let { ":\n${it.stackTraceToString()}" } ?: ""

        return String.format(
            format,
            dat,
            threadId,
            record.level,
            message,
            stackTrace
        )
    }
}
