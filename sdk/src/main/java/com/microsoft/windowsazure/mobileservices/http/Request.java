/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
Apache 2.0 License
 
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
 
     http://www.apache.org/licenses/LICENSE-2.0
 
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 
See the Apache Version 2.0 License for specific language governing permissions and limitations under the License.
 */

/**
 * RequestAsyncTask.java
 */
package com.microsoft.windowsazure.mobileservices.http;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.microsoft.windowsazure.mobileservices.MobileServiceException;

import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public final class Request {
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    // We want at least 2 threads and at most 4 threads in the core pool,
    // preferring to have 1 less than the CPU count to avoid saturating
    // the CPU with background work
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE_SECONDS = 30;

    private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger(1);

        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, "AsyncTask #" + count.getAndIncrement());
        }
    };

    private static final BlockingQueue<Runnable> QUEUE = new LinkedBlockingQueue<>(128);

    private static final Executor THREAD_POOL_EXECUTOR;

    static {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, QUEUE, THREAD_FACTORY);
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        THREAD_POOL_EXECUTOR = threadPoolExecutor;
    }

    private static final Scheduler SCHEDULER = Schedulers.from(THREAD_POOL_EXECUTOR);

    /**
     * Connection to use for the request
     */
    private MobileServiceConnection connection;

    /**
     * Request to execute
     */
    private ServiceFilterRequest request;

    /**
     * Constructor that specifies request and connection
     *
     * @param request    Request to use
     * @param connection Connection to use
     */
    private Request(ServiceFilterRequest request, MobileServiceConnection connection) {
        this.request = request;
        this.connection = connection;
    }

    private Single<ServiceFilterResponse> request() {
        return connection
                .start(request)
                .onErrorResumeNext(throwable -> {
                    if (throwable.getCause() instanceof MobileServiceException) {
                        return Single.error(throwable.getCause());
                    } else {
                        return Single.error(new MobileServiceException(throwable));
                    }
                })
                .subscribeOn(SCHEDULER);
    }

    public static Single<ServiceFilterResponse> create(ServiceFilterRequest request, MobileServiceConnection connection) {
        return new Request(request, connection).request();
    }
}