/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.plugin.httpclient;

import java.net.URI;

import javax.annotation.Nullable;

import org.glowroot.plugin.api.Agent;
import org.glowroot.plugin.api.config.ConfigService;
import org.glowroot.plugin.api.transaction.Message;
import org.glowroot.plugin.api.transaction.MessageSupplier;
import org.glowroot.plugin.api.transaction.Timer;
import org.glowroot.plugin.api.transaction.TimerName;
import org.glowroot.plugin.api.transaction.TraceEntry;
import org.glowroot.plugin.api.transaction.TransactionService;
import org.glowroot.plugin.api.weaving.BindClassMeta;
import org.glowroot.plugin.api.weaving.BindParameter;
import org.glowroot.plugin.api.weaving.BindReceiver;
import org.glowroot.plugin.api.weaving.BindReturn;
import org.glowroot.plugin.api.weaving.BindThrowable;
import org.glowroot.plugin.api.weaving.BindTraveler;
import org.glowroot.plugin.api.weaving.IsEnabled;
import org.glowroot.plugin.api.weaving.Mixin;
import org.glowroot.plugin.api.weaving.OnAfter;
import org.glowroot.plugin.api.weaving.OnBefore;
import org.glowroot.plugin.api.weaving.OnReturn;
import org.glowroot.plugin.api.weaving.OnThrow;
import org.glowroot.plugin.api.weaving.Pointcut;

public class AsyncHttpClientAspect {

    private static final TransactionService transactionService = Agent.getTransactionService();
    private static final ConfigService configService = Agent.getConfigService("http-client");

    // the field and method names are verbose to avoid conflict since they will become fields
    // and methods in all classes that extend com.ning.http.client.ListenableFuture
    @Mixin("com.ning.http.client.ListenableFuture")
    public static class ListenableFutureImpl implements ListenableFuture {

        // only accessed by transaction thread
        private @Nullable TraceEntry glowroot$traceEntry;

        @Override
        public @Nullable TraceEntry glowroot$getTraceEntry() {
            return glowroot$traceEntry;
        }

        @Override
        public void glowroot$setTraceEntry(@Nullable TraceEntry traceEntry) {
            this.glowroot$traceEntry = traceEntry;
        }
    }

    // the method names are verbose to avoid conflict since they will become methods in all classes
    // that extend com.ning.http.client.ListenableFuture
    public interface ListenableFuture {

        @Nullable
        TraceEntry glowroot$getTraceEntry();

        void glowroot$setTraceEntry(@Nullable TraceEntry traceEntry);
    }

    @Pointcut(className = "com.ning.http.client.AsyncHttpClient", methodName = "executeRequest",
            methodParameterTypes = {"com.ning.http.client.Request", ".."},
            timerName = "http client request")
    public static class ExecuteRequestAdvice {
        private static final TimerName timerName =
                transactionService.getTimerName(ExecuteRequestAdvice.class);
        @IsEnabled
        public static boolean isEnabled() {
            return configService.isEnabled();
        }
        @OnBefore
        public static TraceEntry onBefore(@BindParameter Object request,
                @BindClassMeta RequestInvoker requestInvoker) {
            // need to start trace entry @OnBefore in case it is executed in a "same thread
            // executor" in which case will be over in @OnReturn
            String method = requestInvoker.getMethod(request);
            URI originalURI = requestInvoker.getOriginalURI(request);
            return transactionService
                    .startTraceEntry(new RequestMessageSupplier(method, originalURI), timerName);
        }
        @OnReturn
        public static void onReturn(@BindReturn ListenableFuture future,
                @BindTraveler TraceEntry traceEntry) {
            traceEntry.end();
            future.glowroot$setTraceEntry(traceEntry);
        }
        @OnThrow
        public static void onThrow(@BindThrowable Throwable throwable,
                @BindTraveler TraceEntry traceEntry) {
            traceEntry.endWithError(throwable);
        }
    }

    @Pointcut(className = "com.ning.http.client.ListenableFuture",
            declaringClassName = "java.util.concurrent.Future", methodName = "get",
            methodParameterTypes = {".."})
    public static class FutureGetAdvice {
        @OnBefore
        public static Timer onBefore(@BindReceiver ListenableFuture future) {
            TraceEntry traceEntry = future.glowroot$getTraceEntry();
            if (traceEntry != null) {
                return traceEntry.extend();
            }
            return null;
        }
        @OnReturn
        public static void onReturn(@BindReceiver ListenableFuture future) {
            future.glowroot$setTraceEntry(null);
        }
        @OnAfter
        public static void onAfter(@BindTraveler @Nullable Timer timer) {
            if (timer != null) {
                timer.stop();
            }
        }
    }

    private static class RequestMessageSupplier extends MessageSupplier {

        private final String method;
        private final @Nullable URI originalURI;

        private RequestMessageSupplier(String method, @Nullable URI originalURI) {
            this.method = method;
            this.originalURI = originalURI;
        }

        @Override
        public Message get() {
            String uri = originalURI == null ? "" : originalURI.toString();
            return Message.from("http client request: {} {}", method, uri);
        }
    }
}
