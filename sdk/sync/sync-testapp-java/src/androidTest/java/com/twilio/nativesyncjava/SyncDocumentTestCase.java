package com.twilio.nativesyncjava;

import static com.google.common.truth.Truth.assertThat;
import static com.twilio.sync.client.java.utils.SubscriptionStateJava.Established;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.util.concurrent.SettableFuture;
import com.twilio.nativesyncjava.util.TestSyncClient;
import com.twilio.nativesyncjava.util.TestSyncDocument;
import com.twilio.sync.client.java.SyncClientFactory;
import com.twilio.sync.client.java.SyncDocumentJava;
import com.twilio.sync.util.BaseTestCase;
import com.twilio.sync.util.UtilsKt;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SyncDocumentTestCase extends BaseTestCase {
    private TestSyncClient syncClient;
    private TestSyncDocument document;
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Before
    public void setUp() throws Exception {
        setupLogging();
        SyncClientFactory.clearAllCaches(context);
        syncClient = TestSyncClient.create(context, UtilsKt::getToken);

        document = syncClient.createDocument(null, 3600);
        assertTrue(document.syncDocument.getDateExpires() > 0);
    }

    @After
    public void tearDown() {
        syncClient.shutdown();
    }

    @Test
    public void setTtl() throws Exception {
        document.setTtl(0L);
        assertNull(document.syncDocument.getDateExpires());

        SettableFuture<Void> ttlFuture = SettableFuture.create();
        syncClient.syncClient.getDocuments().setTtl(
                document.syncDocument.getSid(), 1000, value -> ttlFuture.set(null));
        ttlFuture.get();

        while (document.syncDocument.getDateExpires() == null) {
            Thread.yield();
        }
        assertNotNull(document.syncDocument.getDateExpires());
    }

    @Test
    public void setData() throws Exception {
        TestSyncClient syncClient2 = TestSyncClient.create(context, () -> UtilsKt.getToken("otherUser"));
        TestSyncDocument document2 = syncClient2.openExistingDocument(document.syncDocument.getSid());

        SettableFuture<SyncDocumentJava> documentFuture1 = SettableFuture.create();
        SettableFuture<SyncDocumentJava> documentFuture2 = SettableFuture.create();

        JSONObject data = new JSONObject("{data:value}");

        document.syncDocument.addListener(new SyncDocumentJava.Listener() {

            @Override
            public void onUpdated(@NonNull SyncDocumentJava document) {
                if (document.getJsonData().equals(data.toString())) {
                    documentFuture1.set(document);
                }
            }
        });
        document2.syncDocument.addListener(new SyncDocumentJava.Listener() {

            @Override
            public void onUpdated(@NonNull SyncDocumentJava document) {
                if (document.getJsonData().equals(data.toString())) {
                    documentFuture2.set(document);
                }
            }
        });

        document.waitSubscriptionState(Established);
        document2.waitSubscriptionState(Established);

        document.setData(data.toString());

        assertThat(document.syncDocument.getJsonData()).isEqualTo(data.toString());

        assertThat(documentFuture1.get().getSid()).isEqualTo(document.syncDocument.getSid());
        assertThat(documentFuture2.get().getSid()).isEqualTo(document2.syncDocument.getSid());

        assertThat(document2.syncDocument.getJsonData()).isEqualTo(data.toString());
    }

    @Test
    public void mutateData() throws Exception {
        TestSyncClient syncClient2 = TestSyncClient.create(context, UtilsKt::getToken);
        TestSyncDocument document2 = syncClient2.openExistingDocument(document.syncDocument.getSid());

        SettableFuture<SyncDocumentJava> documentFuture1 = SettableFuture.create();
        SettableFuture<SyncDocumentJava> documentFuture2 = SettableFuture.create();

        JSONObject data = new JSONObject("{data:value}");

        document.syncDocument.addListener(new SyncDocumentJava.Listener() {

            @Override
            public void onUpdated(@NonNull SyncDocumentJava document) {
                if (document.getJsonData().equals(data.toString())) {
                    documentFuture1.set(document);
                }
            }
        });
        document2.syncDocument.addListener(new SyncDocumentJava.Listener() {

            @Override
            public void onUpdated(@NonNull SyncDocumentJava document) {
                if (document.getJsonData().equals(data.toString())) {
                    documentFuture2.set(document);
                }
            }
        });

        document.waitSubscriptionState(Established);
        document2.waitSubscriptionState(Established);

        document.mutateData(currentJsonData -> data.toString());

        assertThat(document.syncDocument.getJsonData()).isEqualTo(data.toString());

        assertThat(documentFuture1.get().getSid()).isEqualTo(document.syncDocument.getSid());
        assertThat(documentFuture2.get().getSid()).isEqualTo(document2.syncDocument.getSid());

        assertThat(document2.syncDocument.getJsonData()).isEqualTo(data.toString());
    }

    @Test
    public void remove() throws Exception {
        SettableFuture<SyncDocumentJava> removeFuture = SettableFuture.create();

        document.syncDocument.addListener(new SyncDocumentJava.Listener() {

            @Override
            public void onRemoved(@NonNull SyncDocumentJava document) {
                removeFuture.set(document);
            }
        });
        document.waitSubscriptionState(Established);

        syncClient.syncClient.getDocuments().remove(document.syncDocument.getSid(), result -> {});

        assertThat(removeFuture.get()).isSameInstanceAs(document.syncDocument);
        assertThat(document.syncDocument.isRemoved()).isTrue();
    }
}
