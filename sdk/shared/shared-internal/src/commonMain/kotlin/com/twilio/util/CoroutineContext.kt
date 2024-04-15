//
//  Twilio Utils
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.twilio.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

fun newSerialCoroutineContext(): CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)

