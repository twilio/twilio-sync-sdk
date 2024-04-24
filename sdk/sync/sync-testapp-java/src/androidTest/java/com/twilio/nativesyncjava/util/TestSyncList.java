package com.twilio.nativesyncjava.util;

import androidx.annotation.NonNull;

import com.google.common.util.concurrent.SettableFuture;
import com.twilio.sync.client.java.SyncListJava;
import com.twilio.sync.client.java.utils.SubscriptionStateJava;

public class TestSyncList {

    public final SyncListJava syncList;

    TestSyncList(SyncListJava syncList) {
        this.syncList = syncList;
    }

    public void waitSubscriptionState(SubscriptionStateJava targetSubscriptionState) throws Exception {
        SettableFuture<SubscriptionStateJava> future = SettableFuture.create();

        SyncListJava.Listener listener = new SyncListJava.Listener() {
            @Override
            public void onSubscriptionStateChanged(@NonNull SyncListJava list, @NonNull SubscriptionStateJava subscriptionState) {
                if (subscriptionState == targetSubscriptionState) {
                    future.set(subscriptionState);
                }
            }
        };

        syncList.addListener(listener);
        future.get();
        syncList.removeListener(listener);
    }

    public SyncListJava setTtl(Long ttlSeconds) throws Exception {
        SettableFuture<SyncListJava> future = SettableFuture.create();

        syncList.setTtl(ttlSeconds, future::set);

        return future.get();
    }

    public SyncListJava.Item getItem(Long itemIndex) throws Exception {
        SettableFuture<SyncListJava.Item> future = SettableFuture.create();

        syncList.getItem(itemIndex, future::set);

        return future.get();
    }

    public SyncListJava.Item addItem(String jsonData) throws Exception {
        SettableFuture<SyncListJava.Item> future = SettableFuture.create();

        syncList.addItem(jsonData, future::set);

        return future.get();
    }

    public SyncListJava.Item setItem(Long itemIndex, String jsonData) throws Exception {
        SettableFuture<SyncListJava.Item> future = SettableFuture.create();

        syncList.setItem(itemIndex, jsonData, future::set);

        return future.get();
    }
}
