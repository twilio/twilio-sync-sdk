//
//  Twilio Utils
//
//  Copyright © Twilio, Inc. All rights reserved.
package com.twilio.util

import kotlinx.coroutines.CoroutineDispatcher

expect fun currentThreadDispatcher(): CoroutineDispatcher
