package com.twilio.nativesyncjava.util;

import androidx.annotation.NonNull;

import com.google.common.util.concurrent.SettableFuture;
import com.twilio.sync.client.java.SyncDocumentJava;
import com.twilio.sync.client.java.utils.SubscriptionStateJava;
import com.twilio.sync.client.java.utils.SyncMutator;

public class TestSyncDocument {

    public final SyncDocumentJava syncDocument;

    TestSyncDocument(SyncDocumentJava syncDocument) {
        this.syncDocument = syncDocument;
    }

    public void waitSubscriptionState(SubscriptionStateJava targetSubscriptionState) throws Exception {
        SettableFuture<SubscriptionStateJava> future = SettableFuture.create();

        SyncDocumentJava.Listener listener = new SyncDocumentJava.Listener() {
            @Override
            public void onSubscriptionStateChanged(@NonNull SyncDocumentJava document, @NonNull SubscriptionStateJava subscriptionState) {
                if (subscriptionState == targetSubscriptionState) {
                    future.set(subscriptionState);
                }
            }
        };

        syncDocument.addListener(listener);
        future.get();
        syncDocument.removeListener(listener);
    }

    public SyncDocumentJava setTtl(Long ttlSeconds) throws Exception {
        SettableFuture<SyncDocumentJava> future = SettableFuture.create();

        syncDocument.setTtl(ttlSeconds, future::set);

        return future.get();
    }

    public SyncDocumentJava setData(@NonNull String jsonData) throws Exception {
        SettableFuture<SyncDocumentJava> future = SettableFuture.create();

        syncDocument.setData(jsonData, future::set);

        return future.get();
    }

    public SyncDocumentJava mutateData(@NonNull SyncMutator mutator) throws Exception {
        SettableFuture<SyncDocumentJava> future = SettableFuture.create();

        syncDocument.mutateData(mutator, future::set);

        return future.get();
    }
}
