//
//  Twilio Utils
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.util

import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update

var logWriter: LogWriter = LogWriterImpl

interface LogWriter {
    fun v(tag: String, msg: String = "", t: Throwable? = null)

    fun d(tag: String, msg: String = "", t: Throwable? = null)

    fun i(tag: String, msg: String = "", t: Throwable? = null)

    fun w(tag: String, msg: String = "", t: Throwable? = null)

    fun e(tag: String, msg: String = "", t: Throwable? = null)
}

internal expect object LogWriterImpl : LogWriter {
    override fun v(tag: String, msg: String, t: Throwable?)

    override fun d(tag: String, msg: String, t: Throwable?)

    override fun i(tag: String, msg: String, t: Throwable?)

    override fun w(tag: String, msg: String, t: Throwable?)

    override fun e(tag: String, msg: String, t: Throwable?)
}

class TwilioLogger private constructor(private val name: String) {

    val isVerboseEnabled: Boolean
        get() = globalLogLevel <= VERBOSE
    val isDebugEnabled: Boolean
        get() = globalLogLevel <= DEBUG
    val isInfoEnabled: Boolean
        get() = globalLogLevel <= INFO
    val isWarnEnabled: Boolean
        get() = globalLogLevel <= WARN
    val isErrorEnabled: Boolean
        get() = globalLogLevel <= ERROR

    @JvmOverloads
    fun v(msg: String, t: Throwable? = null) {
        if (isVerboseEnabled) {
            logWriter.v(name, msg, t)
        }
    }

    @JvmOverloads
    fun d(msg: String, t: Throwable? = null) {
        if (isDebugEnabled) {
            logWriter.d(name, msg, t)
        }
    }

    @JvmOverloads
    fun i(msg: String, t: Throwable? = null) {
        if (isInfoEnabled) {
            logWriter.i(name, msg, t)
        }
    }

    @JvmOverloads
    fun info(msg: String, t: Throwable? = null) {
        logWriter.i(name, msg, t)
    }

    @JvmOverloads
    fun w(msg: String, t: Throwable? = null) {
        if (isWarnEnabled) {
            logWriter.w(name, msg, t)
        }
    }

    @JvmOverloads
    fun e(msg: String, t: Throwable? = null) {
        if (isErrorEnabled) {
            logWriter.e(name, msg, t)
        }
    }

    fun e(t: Throwable) {
        if (isErrorEnabled) {
            logWriter.e(name, t = t)
        }
    }

    inline fun v(t: Throwable? = null, buildMsg: () -> String) {
        if (isVerboseEnabled) {
            v(buildMsg(), t)
        }
    }

    inline fun d(t: Throwable? = null, buildMsg: () -> String) {
        if (isDebugEnabled) {
            d(buildMsg(), t)
        }
    }

    inline fun i(t: Throwable? = null, buildMsg: () -> String) {
        if (isInfoEnabled) {
            i(buildMsg(), t)
        }
    }

    inline fun w(t: Throwable? = null, buildMsg: () -> String) {
        if (isWarnEnabled) {
            w(buildMsg(), t)
        }
    }

    inline fun e(t: Throwable? = null, buildMsg: () -> String) {
        if (isErrorEnabled) {
            e(buildMsg(), t)
        }
    }

    companion object {
        /**
         * Priority constant for the println method; use Log.v.
         */
        const val VERBOSE = 2

        /**
         * Priority constant for the println method; use Log.d.
         */
        const val DEBUG = 3

        /**
         * Priority constant for the println method; use Log.i.
         */
        const val INFO = 4

        /**
         * Priority constant for the println method; use Log.w.
         */
        const val WARN = 5

        /**
         * Priority constant for the println method; use Log.e.
         */
        const val ERROR = 6

        /**
         * Priority constant for the println method.
         */
        const val ASSERT = 7

        /**
         * Set SDK log level to SILENT - it does not log anything, except SDK version on startup.
         * This is the default.
         */
        const val SILENT = ASSERT + 1

        /**
         * @internal
         * Set SDK log level to INHERIT - Java classes will inherit global log level configured by
         * Logger.setLogLevel(). All sub-loggers in classes use this by default, but you could configure
         * them per-class.
         */
        const val INHERIT = ASSERT + 2

        private var globalLogLevel by atomic(SILENT)

        @JvmStatic
        fun setLogLevel(level: Int) {
            globalLogLevel = level
        }

        @JvmStatic
        fun getLogLevel()  = globalLogLevel

        private val loggers = atomic(mapOf<KClass<*>, TwilioLogger>())

        @JvmStatic
        fun getLogger(cls: KClass<*>): TwilioLogger {
            loggers.value[cls]?.let { return it }

            val logger = TwilioLogger(cls.simpleName ?: "Unknown")
            loggers.update { map -> map + (cls to logger) }
            return logger
        }

        @JvmStatic
        fun getLogger(name: String) = TwilioLogger(name)
    }
}

val Any.logger: TwilioLogger get() = TwilioLogger.getLogger(this::class)
