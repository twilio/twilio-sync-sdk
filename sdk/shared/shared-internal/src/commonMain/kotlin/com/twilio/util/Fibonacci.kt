//
//  Twilio Utils
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.util

import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

private val kSqrt5 = sqrt(5.0)
private val kFi = (1 + kSqrt5) / 2

// See "Computation by rounding" https://en.wikipedia.org/wiki/Fibonacci_sequence
fun fibonacci(n: Int) = round(kFi.pow(n) / kSqrt5)
