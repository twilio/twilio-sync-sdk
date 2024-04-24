//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.repository

import com.twilio.sync.operations.ConfigurationLinks
import com.twilio.sync.utils.CollectionItemId
import com.twilio.sync.utils.CollectionType
import com.twilio.sync.utils.CollectionType.*
import io.ktor.http.encodeURLPathPart

internal fun ConfigurationLinks.getStreamUrl(sidOrUniqueName: String) =
    stream.replace("{streamId}", sidOrUniqueName.encodeURLPathPart())

internal fun ConfigurationLinks.getStreamMessagesUrl(sidOrUniqueName: String) =
    streamMessages.replace("{streamId}", sidOrUniqueName.encodeURLPathPart())

internal fun ConfigurationLinks.getDocumentUrl(sidOrUniqueName: String) = document
    .replace("{documentId}", sidOrUniqueName.encodeURLPathPart())
    .replace("{id}", sidOrUniqueName.encodeURLPathPart()) // TODO: remove this when fixed on backend

internal fun ConfigurationLinks.getMapUrl(sidOrUniqueName: String) =
    map.replace("{mapId}", sidOrUniqueName.encodeURLPathPart())

internal fun ConfigurationLinks.getMapItemsUrl(sidOrUniqueName: String) =
    mapItems.replace("{mapId}", sidOrUniqueName.encodeURLPathPart())

internal fun ConfigurationLinks.getMapItemUrl(sidOrUniqueName: String, key: String) = mapItem
    .replace("{mapId}", sidOrUniqueName.encodeURLPathPart())
    .replace("{itemId}", key.encodeURLPathPart())

internal fun ConfigurationLinks.getListUrl(sidOrUniqueName: String) =
    list.replace("{listId}", sidOrUniqueName.encodeURLPathPart())

internal fun ConfigurationLinks.getListItemsUrl(sidOrUniqueName: String) =
    listItems.replace("{listId}", sidOrUniqueName.encodeURLPathPart())

internal fun ConfigurationLinks.getListItemUrl(sidOrUniqueName: String, index: Long) = listItem
    .replace("{listId}", sidOrUniqueName.encodeURLPathPart())
    .replace("{itemId}", index.toString())

internal fun ConfigurationLinks.getCollectionsUrl(collectionType: CollectionType) = when (collectionType) {
    List -> lists
    Map -> maps
}

internal fun ConfigurationLinks.getCollectionUrl(collectionType: CollectionType, sidOrUniqueName: String) =
    when (collectionType) {
        List -> getListUrl(sidOrUniqueName)
        Map -> getMapUrl(sidOrUniqueName)
    }

internal fun ConfigurationLinks.getCollectionItemsUrl(collectionType: CollectionType, sidOrUniqueName: String) =
    when (collectionType) {
        List -> getListItemsUrl(sidOrUniqueName)
        Map -> getMapItemsUrl(sidOrUniqueName)
    }

internal fun ConfigurationLinks.getCollectionItemUrl(sidOrUniqueName: String, itemId: CollectionItemId) = when (itemId) {
    is CollectionItemId.Index -> getListItemUrl(sidOrUniqueName, itemId.index)
    is CollectionItemId.Key -> getMapItemUrl(sidOrUniqueName, itemId.key)
}
