package com.twilio.nativesyncjava.util;

import androidx.annotation.NonNull;

import com.google.common.util.concurrent.SettableFuture;
import com.twilio.sync.client.java.SyncDocumentJava;
import com.twilio.sync.client.java.SyncMapJava;
import com.twilio.sync.client.java.utils.SubscriptionStateJava;
import com.twilio.sync.client.java.utils.SyncMutator;

public class TestSyncMap {

    public final SyncMapJava syncMap;

    TestSyncMap(SyncMapJava syncMap) {
        this.syncMap = syncMap;
    }

    public void waitSubscriptionState(SubscriptionStateJava targetSubscriptionState) throws Exception {
        SettableFuture<SubscriptionStateJava> future = SettableFuture.create();

        SyncMapJava.Listener listener = new SyncMapJava.Listener() {
            @Override
            public void onSubscriptionStateChanged(@NonNull SyncMapJava map, @NonNull SubscriptionStateJava subscriptionState) {
                if (subscriptionState == targetSubscriptionState) {
                    future.set(subscriptionState);
                }
            }
        };

        syncMap.addListener(listener);
        future.get();
        syncMap.removeListener(listener);
    }

    public SyncMapJava setTtl(Long ttlSeconds) throws Exception {
        SettableFuture<SyncMapJava> future = SettableFuture.create();

        syncMap.setTtl(ttlSeconds, future::set);

        return future.get();
    }

    public SyncMapJava.Item setItem(String itemKey, String jsonData) throws Exception {
        SettableFuture<SyncMapJava.Item> future = SettableFuture.create();

        syncMap.setItem(itemKey, jsonData, future::set);

        return future.get();
    }
}
