//
//  Twilio Utils
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
package com.twilio.util

import kotlinx.coroutines.CoroutineDispatcher

expect fun currentThreadDispatcher(): CoroutineDispatcher
