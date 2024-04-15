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
import com.twilio.nativesyncjava.util.BaseTestCase;
import com.twilio.nativesyncjava.util.TestSyncClient;
import com.twilio.nativesyncjava.util.TestSyncMap;
import com.twilio.nativesyncjava.util.UtilsKt;
import com.twilio.sync.client.java.SyncClientFactory;
import com.twilio.sync.client.java.SyncIteratorJava;
import com.twilio.sync.client.java.SyncMapJava;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SyncMapTestCase extends BaseTestCase {
    private TestSyncClient syncClient;
    private TestSyncMap map;
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Before
    public void setUp() throws Exception {
        setupLogging();
        SyncClientFactory.clearAllCaches(context);
        syncClient = TestSyncClient.create(context, UtilsKt::getToken);

        map = syncClient.createMap(null, 3600);
        assertTrue(map.syncMap.getDateExpires() > 0);
    }

    @After
    public void tearDown() {
        syncClient.shutdown();
    }

    @Test
    public void setTtl() throws Exception {
        map.setTtl(0L);
        assertNull(map.syncMap.getDateExpires());

        SettableFuture<Void> ttlFuture = SettableFuture.create();
        syncClient.syncClient.getMaps().setTtl(map.syncMap.getSid(), 1000, value -> ttlFuture.set(null));
        ttlFuture.get();

        while (map.syncMap.getDateExpires() == null) {
            Thread.yield();
        }
        assertNotNull(map.syncMap.getDateExpires());
    }

    @Test
    public void remove() throws Exception {
        SettableFuture<SyncMapJava> removeFuture = SettableFuture.create();

        map.syncMap.addListener(new SyncMapJava.Listener() {

            @Override
            public void onRemoved(@NonNull SyncMapJava map) {
                removeFuture.set(map);
            }
        });
        map.waitSubscriptionState(Established);

        syncClient.syncClient.getMaps().remove(map.syncMap.getSid(), result -> {});

        assertThat(removeFuture.get()).isSameInstanceAs(map.syncMap);
        assertThat(map.syncMap.isRemoved()).isTrue();
    }

    @Test
    public void queryItems() throws Exception {
        map.setItem("key1", "{}");
        map.setItem("key2", "{}");

        List<SyncMapJava.Item> actualItems = new ArrayList<>();
        SyncIteratorJava<SyncMapJava.Item> iterator = map.syncMap.queryItems();

        try {
            SettableFuture<Boolean> hasNextFuture = SettableFuture.create();
            iterator.hasNext(hasNextFuture::set);

            while (hasNextFuture.get()) {
                SyncMapJava.Item item = iterator.next();
                actualItems.add(item);

                hasNextFuture = SettableFuture.create();
                iterator.hasNext(hasNextFuture::set);
            }
        } finally {
            iterator.close();
        }

        assertThat(actualItems.size()).isEqualTo(2);
        assertThat(actualItems.get(0).getKey()).isEqualTo("key1");
        assertThat(actualItems.get(1).getKey()).isEqualTo("key2");
    }

    @Test
    public void queryItemsWithoutOpening() throws Exception {
        map.setItem("key1", "{}");
        map.setItem("key2", "{}");

        List<SyncMapJava.Item> actualItems = new ArrayList<>();

        TestSyncClient client = TestSyncClient.create(context, UtilsKt::getToken);
        SyncIteratorJava<SyncMapJava.Item> iterator = client.syncClient.getMaps().queryMapItems(map.syncMap.getSid());

        try {
            SettableFuture<Boolean> hasNextFuture = SettableFuture.create();
            iterator.hasNext(hasNextFuture::set);

            while (hasNextFuture.get()) {
                SyncMapJava.Item item = iterator.next();
                actualItems.add(item);

                hasNextFuture = SettableFuture.create();
                iterator.hasNext(hasNextFuture::set);
            }
        } finally {
            iterator.close();
        }

        assertThat(actualItems.size()).isEqualTo(2);
        assertThat(actualItems.get(0).getKey()).isEqualTo("key1");
        assertThat(actualItems.get(1).getKey()).isEqualTo("key2");
    }
}
