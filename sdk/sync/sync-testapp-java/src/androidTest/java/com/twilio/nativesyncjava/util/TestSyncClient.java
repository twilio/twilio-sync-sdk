package com.twilio.nativesyncjava.util;

import static org.junit.Assert.fail;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.util.concurrent.SettableFuture;
import com.twilio.sync.client.java.SyncClientFactory;
import com.twilio.sync.client.java.SyncClientJava;
import com.twilio.sync.client.java.SyncDocumentJava;
import com.twilio.sync.client.java.SyncListJava;
import com.twilio.sync.client.java.SyncMapJava;
import com.twilio.sync.client.java.SyncStreamJava;
import com.twilio.sync.client.java.utils.SuccessListener;
import com.twilio.sync.client.java.utils.TokenProvider;
import com.twilio.sync.utils.ConnectionState;
import com.twilio.util.ErrorInfo;

public class TestSyncClient {

    public static TestSyncClient create(Context context, TokenProvider tokenProvider) throws Exception {
        SettableFuture<SyncClientJava> future = SettableFuture.create();
        Boolean useLastUserCache = false;

        SyncClientFactory.create(context, tokenProvider, useLastUserCache, new SuccessListener<SyncClientJava>() {

            @Override
            public void onSuccess(@NonNull SyncClientJava result) {
                future.set(result);
            }

            @Override
            public void onFailure(@NonNull ErrorInfo errorInfo) {
                fail("Cannot create SyncClient: " + errorInfo);
            }
        });

        SyncClientJava syncClient = future.get();
        return new TestSyncClient(syncClient);
    }

    public final SyncClientJava syncClient;

    private TestSyncClient(SyncClientJava syncClient) {
        this.syncClient = syncClient;
    }

    public void waitConnectionState(ConnectionState targetConnectionState) throws Exception {
        SettableFuture<ConnectionState> future = SettableFuture.create();

        SyncClientJava.Listener listener = new SyncClientJava.Listener() {

            @Override
            public void onConnectionStateChanged(@NonNull SyncClientJava client, @NonNull ConnectionState connectionState) {
                if (connectionState == targetConnectionState) {
                    future.set(connectionState);
                }
            }

            @Override
            public void onError(@NonNull SyncClientJava client, @NonNull ErrorInfo errorInfo) {
                fail("waitConnectionState onError: " + errorInfo);
            }
        };

        syncClient.addListener(listener);
        future.get();
        syncClient.removeListener(listener);
    }


    public TestSyncStream createStream(@Nullable String uniqueName, long ttl) throws Exception {
        SettableFuture<SyncStreamJava> future = SettableFuture.create();

        syncClient.getStreams().create(uniqueName, ttl, new SuccessListener<SyncStreamJava>() {
            @Override
            public void onSuccess(@NonNull SyncStreamJava result) {
                future.set(result);
            }

            @Override
            public void onFailure(@NonNull ErrorInfo errorInfo) {
                fail("createStream onError: " + errorInfo);
            }
        });

        SyncStreamJava stream = future.get();
        return new TestSyncStream(stream);
    }

    public TestSyncStream openExistingStream(@NonNull String sidOrUniqueName) throws Exception {
        SettableFuture<SyncStreamJava> future = SettableFuture.create();

        syncClient.getStreams().openExisting(sidOrUniqueName, new SuccessListener<SyncStreamJava>() {
            @Override
            public void onSuccess(@NonNull SyncStreamJava result) {
                future.set(result);
            }

            @Override
            public void onFailure(@NonNull ErrorInfo errorInfo) {
                fail("createStream onError: " + errorInfo);
            }
        });

        SyncStreamJava stream = future.get();
        return new TestSyncStream(stream);
    }

    public TestSyncDocument createDocument(@Nullable String uniqueName, long ttl) throws Exception {
        SettableFuture<SyncDocumentJava> future = SettableFuture.create();

        syncClient.getDocuments().create(uniqueName, ttl, new SuccessListener<SyncDocumentJava>() {
            @Override
            public void onSuccess(@NonNull SyncDocumentJava result) {
                future.set(result);
            }

            @Override
            public void onFailure(@NonNull ErrorInfo errorInfo) {
                fail("createStream onError: " + errorInfo);
            }
        });

        SyncDocumentJava document = future.get();
        return new TestSyncDocument(document);
    }

    public TestSyncDocument openExistingDocument(@NonNull String sidOrUniqueName) throws Exception {
        SettableFuture<SyncDocumentJava> future = SettableFuture.create();

        syncClient.getDocuments().openExisting(sidOrUniqueName, new SuccessListener<SyncDocumentJava>() {
            @Override
            public void onSuccess(@NonNull SyncDocumentJava result) {
                future.set(result);
            }

            @Override
            public void onFailure(@NonNull ErrorInfo errorInfo) {
                fail("createStream onError: " + errorInfo);
            }
        });

        SyncDocumentJava stream = future.get();
        return new TestSyncDocument(stream);
    }

    public TestSyncMap createMap(@Nullable String uniqueName, long ttl) throws Exception {
        SettableFuture<SyncMapJava> future = SettableFuture.create();

        syncClient.getMaps().create(uniqueName, ttl, new SuccessListener<SyncMapJava>() {
            @Override
            public void onSuccess(@NonNull SyncMapJava result) {
                future.set(result);
            }

            @Override
            public void onFailure(@NonNull ErrorInfo errorInfo) {
                fail("createStream onError: " + errorInfo);
            }
        });

        SyncMapJava map = future.get();
        return new TestSyncMap(map);
    }

    public TestSyncMap openExistingMap(@NonNull String sidOrUniqueName) throws Exception {
        SettableFuture<SyncMapJava> future = SettableFuture.create();

        syncClient.getMaps().openExisting(sidOrUniqueName, new SuccessListener<SyncMapJava>() {
            @Override
            public void onSuccess(@NonNull SyncMapJava result) {
                future.set(result);
            }

            @Override
            public void onFailure(@NonNull ErrorInfo errorInfo) {
                fail("createStream onError: " + errorInfo);
            }
        });

        SyncMapJava map = future.get();
        return new TestSyncMap(map);
    }


    public TestSyncList createList(@Nullable String uniqueName, long ttl) throws Exception {
        SettableFuture<SyncListJava> future = SettableFuture.create();

        syncClient.getLists().create(uniqueName, ttl, new SuccessListener<SyncListJava>() {
            @Override
            public void onSuccess(@NonNull SyncListJava result) {
                future.set(result);
            }

            @Override
            public void onFailure(@NonNull ErrorInfo errorInfo) {
                fail("createStream onError: " + errorInfo);
            }
        });

        SyncListJava list = future.get();
        return new TestSyncList(list);
    }

    public TestSyncList openExistingList(@NonNull String sidOrUniqueName) throws Exception {
        SettableFuture<SyncListJava> future = SettableFuture.create();

        syncClient.getLists().openExisting(sidOrUniqueName, new SuccessListener<SyncListJava>() {
            @Override
            public void onSuccess(@NonNull SyncListJava result) {
                future.set(result);
            }

            @Override
            public void onFailure(@NonNull ErrorInfo errorInfo) {
                fail("createStream onError: " + errorInfo);
            }
        });

        SyncListJava list = future.get();
        return new TestSyncList(list);
    }

    public void shutdown() {
        syncClient.shutdown();
    }
}
