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
import com.twilio.nativesyncjava.util.TestSyncList;
import com.twilio.sync.client.java.SyncClientFactory;
import com.twilio.sync.client.java.SyncIteratorJava;
import com.twilio.sync.client.java.SyncListJava;
import com.twilio.sync.util.BaseTestCase;
import com.twilio.sync.util.UtilsKt;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SyncListTestCase extends BaseTestCase {
    private TestSyncClient syncClient;
    private TestSyncList list;
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Before
    public void setUp() throws Exception {
        setupLogging();
        SyncClientFactory.clearAllCaches(context);
        syncClient = TestSyncClient.create(context, UtilsKt::getToken);

        list = syncClient.createList(null, 3600);
        assertTrue(list.syncList.getDateExpires() > 0);
    }

    @After
    public void tearDown() {
        syncClient.shutdown();
    }

    @Test
    public void setTtl() throws Exception {
        list.setTtl(0L);
        assertNull(list.syncList.getDateExpires());

        SettableFuture<Void> ttlFuture = SettableFuture.create();
        syncClient.syncClient.getLists().setTtl(list.syncList.getSid(), 1000, value -> ttlFuture.set(null));
        ttlFuture.get();

        while (list.syncList.getDateExpires() == null) {
            Thread.yield();
        }
        assertNotNull(list.syncList.getDateExpires());
    }

    @Test
    public void remove() throws Exception {
        SettableFuture<SyncListJava> removeFuture = SettableFuture.create();

        list.syncList.addListener(new SyncListJava.Listener() {

            @Override
            public void onRemoved(@NonNull SyncListJava list) {
                removeFuture.set(list);
            }
        });
        list.waitSubscriptionState(Established);

        syncClient.syncClient.getLists().remove(list.syncList.getSid(), result -> {});

        assertThat(removeFuture.get()).isSameInstanceAs(list.syncList);
        assertThat(list.syncList.isRemoved()).isTrue();
    }

    @Test
    public void queryItems() throws Exception {
        list.addItem("{}");
        list.addItem("{}");

        String jsonData = new JSONObject().put("key", "value").toString();
        list.setItem(1L, jsonData);

        List<SyncListJava.Item> actualItems = new ArrayList<>();
        SyncIteratorJava<SyncListJava.Item> iterator = list.syncList.queryItems();

        try {
            SettableFuture<Boolean> hasNextFuture = SettableFuture.create();
            iterator.hasNext(hasNextFuture::set);

            while (hasNextFuture.get()) {
                SyncListJava.Item item = iterator.next();
                actualItems.add(item);

                hasNextFuture = SettableFuture.create();
                iterator.hasNext(hasNextFuture::set);
            }
        } finally {
            iterator.close();
        }

        assertThat(actualItems.size()).isEqualTo(2);
        assertThat(actualItems.get(0).getJsonData()).isEqualTo("{}");
        assertThat(actualItems.get(1).getJsonData()).isEqualTo(jsonData);
    }

    @Test
    public void queryItemsWithoutOpening() throws Exception {
        list.addItem("{}");
        list.addItem("{}");

        String jsonData = new JSONObject().put("key", "value").toString();
        list.setItem(1L, jsonData);

        List<SyncListJava.Item> actualItems = new ArrayList<>();

        TestSyncClient client = TestSyncClient.create(context, UtilsKt::getToken);
        SyncIteratorJava<SyncListJava.Item> iterator = client.syncClient.getLists().queryListItems(list.syncList.getSid());

        try {
            SettableFuture<Boolean> hasNextFuture = SettableFuture.create();
            iterator.hasNext(hasNextFuture::set);

            while (hasNextFuture.get()) {
                SyncListJava.Item item = iterator.next();
                actualItems.add(item);

                hasNextFuture = SettableFuture.create();
                iterator.hasNext(hasNextFuture::set);
            }
        } finally {
            iterator.close();
        }

        assertThat(actualItems.size()).isEqualTo(2);
        assertThat(actualItems.get(0).getJsonData()).isEqualTo("{}");
        assertThat(actualItems.get(1).getJsonData()).isEqualTo(jsonData);
    }
}
