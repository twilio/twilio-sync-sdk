package com.twilio.nativesyncjava;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.twilio.nativesyncjava.util.TestSyncClient;
import com.twilio.sync.client.java.SyncClientFactory;
import com.twilio.sync.util.BaseTestCase;
import com.twilio.sync.util.UtilsKt;
import com.twilio.sync.utils.ConnectionState;

import org.junit.Before;
import org.junit.Test;

public class SyncClientTestCase extends BaseTestCase {
    private TestSyncClient syncClient;

    private Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Before
    public void setUp() throws Exception {
        setupLogging();
        SyncClientFactory.clearAllCaches(context);
        syncClient = TestSyncClient.create(context, UtilsKt::getToken);
    }

    @Test
    public void connect() throws Exception {
        syncClient.waitConnectionState(ConnectionState.Connected);
    }

    @Test
    public void shutdown() {
        syncClient.shutdown();
    }
}
