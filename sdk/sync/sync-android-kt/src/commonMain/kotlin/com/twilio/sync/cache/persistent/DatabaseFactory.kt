//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.cache.persistent

import com.squareup.sqldelight.ColumnAdapter
import com.squareup.sqldelight.db.SqlDriver
import com.twilio.sync.sqldelight.cache.persistent.DocumentCacheMetadata
import com.twilio.sync.sqldelight.cache.persistent.ListCacheMetadata
import com.twilio.sync.sqldelight.cache.persistent.ListItemCacheData
import com.twilio.sync.sqldelight.cache.persistent.MapCacheMetadata
import com.twilio.sync.sqldelight.cache.persistent.MapItemCacheData
import com.twilio.sync.sqldelight.cache.persistent.StreamCacheMetadata
import com.twilio.util.json
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject

@Suppress("FunctionName")
internal fun SyncDatabase(driver: SqlDriver) = SyncDatabase(
    driver = driver,
    documentCacheMetadataAdapter = DocumentCacheMetadata.Adapter(
        dateCreatedAdapter = instantAdapter,
        dateUpdatedAdapter = instantAdapter,
        dateExpiresAdapter = instantAdapter,
        documentDataAdapter = jsonAdapter,
    ),
    listCacheMetadataAdapter = ListCacheMetadata.Adapter(
        dateCreatedAdapter = instantAdapter,
        dateUpdatedAdapter = instantAdapter,
        dateExpiresAdapter = instantAdapter,
    ),
    listItemCacheDataAdapter = ListItemCacheData.Adapter(
        dateCreatedAdapter = instantAdapter,
        dateUpdatedAdapter = instantAdapter,
        dateExpiresAdapter = instantAdapter,
        itemDataAdapter = jsonAdapter,
    ),
    mapCacheMetadataAdapter = MapCacheMetadata.Adapter(
        dateCreatedAdapter = instantAdapter,
        dateUpdatedAdapter = instantAdapter,
        dateExpiresAdapter = instantAdapter,
    ),
    mapItemCacheDataAdapter = MapItemCacheData.Adapter(
        dateCreatedAdapter = instantAdapter,
        dateUpdatedAdapter = instantAdapter,
        dateExpiresAdapter = instantAdapter,
        itemDataAdapter = jsonAdapter,
    ),
    streamCacheMetadataAdapter = StreamCacheMetadata.Adapter(
        dateExpiresAdapter = instantAdapter,
    ),
)

private val instantAdapter = object : ColumnAdapter<Instant, Long> {

    override fun decode(databaseValue: Long) = Instant.fromEpochMilliseconds(databaseValue)

    override fun encode(value: Instant): Long = value.toEpochMilliseconds()
}

private val jsonAdapter = object : ColumnAdapter<JsonObject, String> {

    override fun decode(databaseValue: String) = json.decodeFromString<JsonObject>(databaseValue)

    override fun encode(value: JsonObject): String = value.toString()
}

