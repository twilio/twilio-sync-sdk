//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.twilsock.client.test.unit.commands

import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCoroutineScope
import com.twilio.test.util.waitAndVerify
import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.commands.BaseCommand
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.commands.CommandsScheduler
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

@ExcludeFromInstrumentedTests
internal class CommandsSchedulerTest {

    @RelaxedMockK
    lateinit var twilsock: Twilsock

    lateinit var commandsScheduler: CommandsScheduler

    val config = CommandsConfig()

    @BeforeTest
    fun setUp() {
        setupTestLogging()
        MockKAnnotations.init(this, relaxUnitFun = true)

        commandsScheduler = CommandsScheduler(testCoroutineScope, twilsock, config)
    }

    @Test
    fun executeOneCommand() = runTest {
        val command: BaseCommand<*> = mockk(relaxed = true)
        val expectedResult = object {}
        coEvery { command.awaitResult() } returns expectedResult
        val actualResult = commandsScheduler.post(command)

        waitAndVerify { command.execute(twilsock) }
        assertSame(expectedResult, actualResult)
    }

    @Test
    fun executeTwoCommands() = runTest {
        val command1: BaseCommand<*> = mockk(relaxed = true)
        val command2: BaseCommand<*> = mockk(relaxed = true)

        val signal1 = CompletableDeferred<Any>()
        val signal2 = CompletableDeferred<Any>()

        coEvery { command1.awaitResult() }.coAnswers { signal1.await() }
        coEvery { command2.awaitResult() }.coAnswers { signal2.await() }

        val deferredResult1 = async { commandsScheduler.post(command1) }
        val deferredResult2 = async { commandsScheduler.post(command2) }

        waitAndVerify { command1.execute(twilsock) }
        waitAndVerify { command2.execute(twilsock) }

        assertTrue(deferredResult1.isActive)
        assertTrue(deferredResult2.isActive) // both commands run in parallel

        val expectedResult1 = object {}
        val expectedResult2 = object {}

        signal1.complete(expectedResult1)
        signal2.complete(expectedResult2)

        val actualResult1 = deferredResult1.await()
        val actualResult2 = deferredResult2.await()

        assertSame(expectedResult1, actualResult1)
        assertSame(expectedResult2, actualResult2)
    }

    @Test
    fun executeMaxPlusOneCommands() = runTest {
        val count = config.maxParallelCommands + 1

        val commands = Array(count) { mockk<BaseCommand<*>>(relaxed = true) }
        val signals = Array(count) { CompletableDeferred<Any>() }

        commands.forEachIndexed { index, syncCommand ->
            coEvery { syncCommand.awaitResult() }.coAnswers { signals[index].await() }
        }

        val deferredResults = Array(count) { async { commandsScheduler.post(commands[it]) } }

        repeat(config.maxParallelCommands) { index -> // maxParallelCommands run in parallel
            waitAndVerify { commands[index].execute(twilsock) }
            assertTrue(deferredResults[index].isActive)
        }

        val lastCommand = commands.last()

        delay(1.seconds) // wait to be sure last command is not run
        coVerify(exactly = 0) { lastCommand.execute(twilsock) }

        val expectedResults = Array(count) { object {} }

        // first command completed
        signals.first().complete(expectedResults.first())

        // now last command is executed
        waitAndVerify(exactly = 1) { lastCommand.execute(twilsock) }

        // all commands completed
        signals.forEachIndexed { index, signal ->
            signal.complete(expectedResults[index])
        }

        val actualResults = Array(count) { deferredResults[it].await() }

        expectedResults.forEachIndexed { index, expectedResult ->
            assertSame(expectedResult, actualResults[index])
        }
    }
}
