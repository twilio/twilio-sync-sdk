//
//  Twilio Conversations Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.conversations

/**
 * Attachment category
 */
enum class MediaCategory(val value: String) {
    /** This type means that a message contain attached media. */
    MEDIA("media"),

    /** This type means that media with this category replaces a body from a message. */
    BODY("body"),

    /** The type is email history. */
    HISTORY("history");

    companion object {

        private val map = values().associateBy { it.value }

        fun fromString(value: String): MediaCategory = map[value] ?: error("Unknown MediaCategory: $value")
    }
}
