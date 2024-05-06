//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.entities

import com.twilio.sync.utils.CollectionItemData
import com.twilio.sync.utils.CollectionItemId

internal fun CollectionItemData.toMapItem(): SyncMap.Item {
    check(itemId is CollectionItemId.Key) { "CollectionItemData must be of type Map" }

    return SyncMap.Item(
        key = itemId.key,
        data = data,
        dateCreated = dateCreated,
        dateUpdated = dateUpdated,
        dateExpires = dateExpires,
    )
}

internal fun CollectionItemData.toListItem(): SyncList.Item {
    check(itemId is CollectionItemId.Index) { "CollectionItemData must be of type List" }

    return SyncList.Item(
        index = itemId.index,
        data = data,
        dateCreated = dateCreated,
        dateUpdated = dateUpdated,
        dateExpires = dateExpires,
    )
}
