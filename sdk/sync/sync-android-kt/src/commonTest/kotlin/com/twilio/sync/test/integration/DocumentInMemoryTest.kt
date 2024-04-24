//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.integration

import com.twilio.sync.utils.CacheConfig
import com.twilio.sync.utils.SyncConfig

class DocumentInMemoryTest : DocumentTest() {

    override val config = SyncConfig(
        cacheConfig = CacheConfig(usePersistentCache = false)
    )
}
