package com.twilio.nativesyncjava.util

import com.twilio.sync.client.java.SyncClientJava
import com.twilio.sync.utils.LogLevel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.rules.Timeout
import timber.log.Timber

open class BaseTestCase {

    @Rule
    @JvmField
    val timeout = Timeout.seconds(60)

    private val blocks = mutableListOf<suspend () -> Unit>()

    @After
    fun executeTearDownBlocks() = runBlocking {
        Timber.i("begin")
        blocks.asReversed().forEach { it() }
        blocks.clear()
        Timber.i("end")
    }

    fun <T> T.onTearDown(block: suspend T.() -> Unit): T {
        blocks += { block() }
        return this
    }

    companion object {

        @BeforeClass
        @JvmStatic
        fun setupLogging() {
            Timber.plant(Timber.DebugTree())
            SyncClientJava.setLogLevel(LogLevel.Verbose)
        }
    }
}
