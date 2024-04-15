//
//  Twilio Conversations Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.twilsock.commands

import com.twilio.twilsock.client.Twilsock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class CommandsScheduler(
    private val coroutineScope: CoroutineScope,
    private val twilsock: Twilsock,
    val config: CommandsConfig,
) {
    private val commandsChannel = Channel<BaseCommand<*>>(capacity = Channel.UNLIMITED)

    init { startDispatch() }

    suspend fun <T> post(command: BaseCommand<T>): T {
        command.startTimer()

        val result = commandsChannel.trySend(command)
        check(result.isSuccess) { "trySend on UNLIMITED channel is always success" }

        return command.awaitResult()
    }

    private fun startDispatch() = coroutineScope.launch {
        val semaphore = Semaphore(config.maxParallelCommands)

        commandsChannel.consumeEach { command ->
            launch {
                semaphore.withPermit {
                    command.execute(twilsock)
                    runCatching { command.awaitResult() }
                }
            }
        }
    }
}
