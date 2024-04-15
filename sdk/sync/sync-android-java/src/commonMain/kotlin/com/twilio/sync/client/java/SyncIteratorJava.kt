//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.client.java

import com.twilio.sync.client.java.utils.CancellationToken
import com.twilio.sync.client.java.utils.SuccessListener
import com.twilio.sync.utils.Closeable

/**
 * An iterator over a collection. Allows to sequentially access the elements.
 * Must be closed at the end to cleanup resources.
 *
 * Instances of this interface are *not thread-safe* and shall not be used
 * from concurrent coroutines.
 *
 * Example:
 *
 * ```
 * SyncIteratorJava<SyncMapJava.Item> iterator = syncMap.queryItems();
 *
 * try {
 *   SettableFuture<Boolean> hasNextFuture = SettableFuture.create();
 *   iterator.hasNext(hasNextFuture::set);
 *
 *   while (hasNextFuture.get()) {
 *     SyncMapJava.Item item = iterator.next();
 *     System.out.println(item);
 *
 *     hasNextFuture = SettableFuture.create();
 *     iterator.hasNext(hasNextFuture::set);
 *   }
 * } finally {
 *   iterator.close();
 * }
 * ```
 */
interface SyncIteratorJava<T> : Closeable {
    /**
     * Returns the next element in the iteration.
     *
     * @return The next element in the iteration.
     */
    fun next(): T

    /**
     * Returns `true` to callback if [SyncIteratorJava] has more elements (In other words, returns
     * `true` if next() would return an element rather than throwing an exception).
     *
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun hasNext(callback: SuccessListener<Boolean>): CancellationToken
}
