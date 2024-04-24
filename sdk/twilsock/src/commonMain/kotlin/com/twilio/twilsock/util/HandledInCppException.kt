package com.twilio.twilsock.util

import io.ktor.utils.io.CancellationException

// This class is used in performance optimisation tricks. See JniFuture.onHandledInCpp().
// Should be removed together with JniFuture when Sync will be implemented in kotlin.
class HandledInCppException : CancellationException("Reply has been handled on CPP level")
