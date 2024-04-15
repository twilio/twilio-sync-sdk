package com.twilio.nativesyncjava.util;

import static org.junit.Assert.fail;

import androidx.annotation.NonNull;

import com.google.common.util.concurrent.SettableFuture;
import com.twilio.sync.client.java.SyncStreamJava;
import com.twilio.sync.client.java.utils.SubscriptionStateJava;
import com.twilio.sync.client.java.utils.SuccessListener;
import com.twilio.util.ErrorInfo;

public class TestSyncStream {

    public final SyncStreamJava syncStream;

    TestSyncStream(SyncStreamJava syncStream) {
        this.syncStream = syncStream;
    }

    public void waitSubscriptionState(SubscriptionStateJava targetSubscriptionState) throws Exception {
        SettableFuture<SubscriptionStateJava> future = SettableFuture.create();

        SyncStreamJava.Listener listener = new SyncStreamJava.Listener() {
            @Override
            public void onSubscriptionStateChanged(@NonNull SyncStreamJava stream, @NonNull SubscriptionStateJava subscriptionState) {
                if (subscriptionState == targetSubscriptionState) {
                    future.set(subscriptionState);
                }
            }
        };

        syncStream.addListener(listener);
        future.get();
        syncStream.removeListener(listener);
    }

    public SyncStreamJava.Message publishMessage(@NonNull String data) throws Exception {
        SettableFuture<SyncStreamJava.Message> future = SettableFuture.create();

        syncStream.publishMessage(data, new SuccessListener<SyncStreamJava.Message>() {
            @Override
            public void onSuccess(@NonNull SyncStreamJava.Message result) {
                future.set(result);
            }

            @Override
            public void onFailure(@NonNull ErrorInfo errorInfo) {
                fail("publishMessage onFailure: " + errorInfo);
            }
        });

        return future.get();
    }
}
