package com.twilio.util

import java.util.logging.Level

internal sealed class LoggingLevel(name: String, value: Int) : Level(name, value) {
    object VERBOSE : LoggingLevel("VERBOSE", Level.INFO.intValue())
    object DEBUG : LoggingLevel("DEBUG", Level.INFO.intValue())
    object INFO : LoggingLevel("INFO", Level.INFO.intValue())
    object WARN : LoggingLevel("WARN", Level.WARNING.intValue())
    object ERROR : LoggingLevel("ERROR", Level.SEVERE.intValue())
}
