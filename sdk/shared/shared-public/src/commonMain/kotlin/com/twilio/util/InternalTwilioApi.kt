package com.twilio.util

/**
 * Marks declarations that are **internal**, which means that should not be used outside,
 * because their signatures and semantics will change between future releases without any
 * warnings and without providing any migration aids.
 */
@Retention(value = AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.TYPEALIAS, AnnotationTarget.PROPERTY)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR, message = "This is an internal twilio API that " +
            "should not be used from outside. No compatibility guarantees are provided."
)
annotation class InternalTwilioApi
