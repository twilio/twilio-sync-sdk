//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.utils

import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject

/**
 * In order to not copy-paste logic between Sync and Map entities
 * we use this common data classes to represent collections and items.
 * So we can use the same code for both Sync and Map entities.
 *
 * Basically when we receive map or list data from backend - we convert the data to these collection data classes
 * (see collections operations implementation), then perform all logic at SyncRepository and SyncCache levels on these
 * classes, then convert collection data classes to XXXCacheData classes (which are different for Lists and Maps)
 * before store them in cache (see DatabaseExtensions.kt).
 */

internal enum class CollectionType {
    Map,
    List,
}

internal sealed interface CollectionItemId {
    val id: String

    data class Key(val key: String) : CollectionItemId {
        override val id: String get() = key
    }

    data class Index(val index: Long) : CollectionItemId {
        override val id: String get() = index.toString()
    }
}

internal val CollectionItemId.collectionType: CollectionType
    get() = when (this) {
        is CollectionItemId.Index -> CollectionType.List
        is CollectionItemId.Key -> CollectionType.Map
    }

internal data class CollectionMetadata(
    val collectionType: CollectionType,
    val sid: String,
    val uniqueName: String?,
    val dateCreated: Instant,
    val dateUpdated: Instant,
    val dateExpires: Instant?,
    val revision: String,
    val lastEventId: Long,
    val isEmpty: Boolean?,
    val beginId: CollectionItemId?,
    val endId: CollectionItemId?,
)

internal data class CollectionItemData(
    val collectionSid: EntitySid,
    val itemId: CollectionItemId,
    val dateCreated: Instant,
    val dateUpdated: Instant,
    val dateExpires: Instant?,
    val revision: String,
    val lastEventId: Long,
    val data: JsonObject,
    val isLeftBound: Boolean,
    val isRightBound: Boolean,
    val isRemoved: Boolean,
)

internal val CollectionItemData.collectionType: CollectionType get() = itemId.collectionType

internal data class CollectionItemRemovedEvent(
    val eventId: Long,
    val dateCreated: Instant,
    val collectionSid: String,
    val itemId: CollectionItemId
)
