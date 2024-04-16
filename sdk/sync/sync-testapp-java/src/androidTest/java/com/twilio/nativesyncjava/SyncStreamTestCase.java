package com.twilio.nativesyncjava;

import static com.google.common.truth.Truth.assertThat;
import static com.twilio.sync.client.java.utils.SubscriptionStateJava.Established;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.util.concurrent.SettableFuture;
import com.twilio.nativesyncjava.util.BaseTestCase;
import com.twilio.nativesyncjava.util.TestSyncClient;
import com.twilio.nativesyncjava.util.TestSyncStream;
import com.twilio.nativesyncjava.util.UtilsKt;
import com.twilio.sync.client.java.SyncClientFactory;
import com.twilio.sync.client.java.SyncStreamJava;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SyncStreamTestCase extends BaseTestCase {
    private TestSyncClient syncClient;
    private TestSyncStream stream;
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Before
    public void setUp() throws Exception {
        setupLogging();
        SyncClientFactory.clearAllCaches(context);
        syncClient = TestSyncClient.create(context, UtilsKt::getToken);

        stream = syncClient.createStream(null, 3600);
        assertTrue(stream.syncStream.getDateExpires() > 0);
    }

    @After
    public void tearDown() {
        syncClient.shutdown();
    }

    @Test
    public void sendMessage() throws Exception {
        TestSyncClient syncClient2 = TestSyncClient.create(context, UtilsKt::getToken);
        TestSyncStream stream2 = syncClient2.openExistingStream(stream.syncStream.getSid());

        SettableFuture<SyncStreamJava.Message> messageFuture1 = SettableFuture.create();
        SettableFuture<SyncStreamJava.Message> messageFuture2 = SettableFuture.create();

        stream.syncStream.addListener(new SyncStreamJava.Listener() {
            @Override
            public void onMessagePublished(@NonNull SyncStreamJava stream, @NonNull SyncStreamJava.Message message) {
                messageFuture1.set(message);
            }
        });
        stream2.syncStream.addListener(new SyncStreamJava.Listener() {
            @Override
            public void onMessagePublished(@NonNull SyncStreamJava stream, @NonNull SyncStreamJava.Message message) {
                messageFuture2.set(message);
            }
        });

        stream.waitSubscriptionState(Established);
        stream2.waitSubscriptionState(Established);

        JSONObject data = new JSONObject("{data:value}");
        SyncStreamJava.Message message = stream.publishMessage(data.toString());

        assertThat(message.getJsonData()).isEqualTo(data.toString());

        SyncStreamJava.Message message1 = messageFuture1.get();
        SyncStreamJava.Message message2 = messageFuture2.get();

        assertThat(message1.getSid()).isEqualTo(message.getSid());
        assertThat(message2.getSid()).isEqualTo(message.getSid());


        assertThat(message1.getJsonData()).isEqualTo(message.getJsonData());
        assertThat(message2.getJsonData()).isEqualTo(message.getJsonData());
    }
}
