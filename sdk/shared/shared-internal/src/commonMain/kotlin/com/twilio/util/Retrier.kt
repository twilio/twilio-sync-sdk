//
//  Twilio Utils
//
//  Copyright © Twilio, Inc. All rights reserved.
//
@file:OptIn(ExperimentalTime::class)

package com.twilio.util

import com.twilio.util.ErrorReason.RetrierReachedMaxAttempsCount
import com.twilio.util.ErrorReason.RetrierReachedMaxTime
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlinx.coroutines.delay

private val logger by lazy { TwilioLogger.getLogger("Retrier") }

data class RetrierConfig(

    /** Delay before first attempt. */
    val startDelay: Duration = ZERO,

    /** Minimum delay between attempts attempt. Must be greater than ZERO. */
    val minDelay: Duration = 2.seconds,

    /** Maximum delay between attempts attempt. */
    val maxDelay: Duration = 10.seconds,

    /**
     * Is used to calculate random value which is added to each delay between attempts.
     *
     * val randomDelayToAdd = delay * config.randomizeFactor * Random.nextDouble(0.0, 1.0)
     *
     * Expected to be in range between 0 and 1.
     */
    val randomizeFactor: Double = 0.2,

    /** Maximum attempts count to be made before retrier fails. `null` value is "keep retrying". */
    val maxAttemptsCount: Int? = 10,

    /** Maximum total time before retrier fails. */
    val maxAttemptsTime: Duration = 65.seconds,
)

suspend fun retry(
    config: RetrierConfig = RetrierConfig(),
    onRetrierAttempt: suspend () -> Result<Unit>
) {
    logger.d { "Started with config: $config" }

    // otherwise our Fibonacci sequence is going to be constant zero
    require(config.minDelay != ZERO) { "minDelay must be positive value" }

    val startTime = TimeSource.Monotonic.markNow()
    var attemptCounter = 0

    logger.d { "next attempt after ${config.startDelay}" }
    delay(config.startDelay)

    var prevDelay = config.startDelay
    var currDelay = config.minDelay
    var nextDelay = config.minDelay

    while (true) {
        runCatching { onRetrierAttempt() }
            .onSuccess { result ->
                result
                    .onSuccess { logger.d("RetrierAttempt succeeded"); return }
                    .onFailure { logger.d("RetrierAttempt failed: $it") }
            }
            .onFailure { t ->
                logger.w("RetrierAttempt finished with exception, retrier stopped: ", t)
                throw t
            }

        config.maxAttemptsCount
            ?.takeIf { ++attemptCounter > it }
            ?.run { throw TwilioException(ErrorInfo(RetrierReachedMaxAttempsCount)) }

        val randomDelayToAdd = nextDelay * config.randomizeFactor * Random.nextDouble(0.0, 1.0)
        nextDelay += randomDelayToAdd

        val attemptsTime = startTime.elapsedNow() + nextDelay
        if (attemptsTime > config.maxAttemptsTime) {
            logger.d("stopped: max time reached: $attemptsTime")
            val errorInfo = ErrorInfo(RetrierReachedMaxTime, message = "maxAttemptsTime=${config.maxAttemptsTime}")
            throw TwilioException(errorInfo)
        }

        logger.d { "next attempt after $nextDelay" }
        delay(nextDelay)

        nextDelay = (prevDelay + currDelay).coerceIn(config.minDelay, config.maxDelay)

        prevDelay = currDelay
        currDelay = nextDelay
    }
}
