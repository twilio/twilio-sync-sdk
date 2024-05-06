//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.operations

import com.twilio.sync.utils.isPreConditionFailed
import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.commands.BaseCommand
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpMethod
import com.twilio.twilsock.util.HttpRequest
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.CommandPermanentError
import com.twilio.util.ErrorReason.CommandRecoverableError
import com.twilio.util.ErrorReason.MutateOperationAborted
import com.twilio.util.ErrorReason.Unknown
import com.twilio.util.TwilioException
import com.twilio.util.logger
import com.twilio.util.toTwilioException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Duration

internal object InvokeMutator {

    suspend inline operator fun invoke(crossinline block: suspend () -> JsonObject?): JsonObject {
        val newData = runCatching { withContext(Dispatchers.Default) { block() } }.getOrElse { e ->
            logger.w(e) { "Customer's code has aborted the mutate operation by throwing an exception" }

            throw TwilioException(
                errorInfo = ErrorInfo(
                    reason = CommandPermanentError,
                    message = "Mutate operation error: Mutator function has thrown an exception. See cause for details."
                ),
                cause = e
            )
        }

        if (newData == null) {
            logger.d { "Customer's code has aborted the mutate operation by returning null as new data" }

            throw TwilioException(
                errorInfo = ErrorInfo(
                    reason = CommandPermanentError,
                    message = "Mutate operation aborted: Mutator function has returned null as new data"
                ),
                cause = TwilioException(ErrorInfo(MutateOperationAborted))
            )
        }

        return newData
    }
}

internal abstract class BaseMutateOperation<T>(
    private val coroutineScope: CoroutineScope,
    private val config: CommandsConfig,
    private val url: String,
    private val currentData: CurrentData?,
    private val ttl: Duration?,
    private val mutateData: suspend (currentData: JsonObject) -> JsonObject?,
) : BaseCommand<T>(coroutineScope, config) {

    private var lastAttemptPreConditionFailed = false

    override suspend fun makeRequest(twilsock: Twilsock): T {

        val latestData = if (lastAttemptPreConditionFailed)
            requestCurrentData(twilsock)
        else
            currentData ?: requestCurrentData(twilsock)

        val result = runCatching {
            sendMutateRequest(
                twilsock = twilsock,
                newData = InvokeMutator { mutateData(latestData.data) },
                revision = latestData.revision,
            )
        }

        return result.getOrElse { t ->
            val errorInfo = t.toTwilioException(Unknown).errorInfo
            lastAttemptPreConditionFailed = errorInfo.isPreConditionFailed

            if (!errorInfo.isPreConditionFailed) throw t

            logger.d { "mutateData: failed due to precondition failure, Retrying after fetching latest data" }

            val recoverableError = errorInfo.copy(reason = CommandRecoverableError)
            throw TwilioException(recoverableError)
        }
    }

    private suspend fun sendMutateRequest(
        twilsock: Twilsock,
        newData: JsonObject,
        revision: String,
    ): T {
        val body = buildJsonObject {
            put("data", newData)
            ttl?.let { putTtl(it) }
        }

        val request = HttpRequest(
            url = url,
            method = HttpMethod.POST,
            timeout = config.httpTimeout,
            headers = generateHeaders().putRevision(revision),
            payload = body.toString(),
        )

        return sendRequest(twilsock, request, newData)
    }

    protected abstract suspend fun sendRequest(twilsock: Twilsock, request: HttpRequest, data: JsonObject): T

    protected abstract suspend fun requestCurrentData(twilsock: Twilsock): CurrentData

    data class CurrentData(
        val revision: String,
        val data: JsonObject,
    )
}
