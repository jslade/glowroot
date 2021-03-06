/*
 * Copyright 2012-2015 the original author or authors.
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
package org.glowroot.sandbox.ui;

import java.util.Map;
import java.util.Random;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.glowroot.plugin.api.Agent;
import org.glowroot.plugin.api.config.ConfigService;
import org.glowroot.plugin.api.transaction.Message;
import org.glowroot.plugin.api.transaction.MessageSupplier;
import org.glowroot.plugin.api.transaction.QueryEntry;
import org.glowroot.plugin.api.transaction.TimerName;
import org.glowroot.plugin.api.transaction.TraceEntry;
import org.glowroot.plugin.api.transaction.TransactionService;
import org.glowroot.plugin.api.weaving.BindClassMeta;
import org.glowroot.plugin.api.weaving.BindReceiver;
import org.glowroot.plugin.api.weaving.BindTraveler;
import org.glowroot.plugin.api.weaving.IsEnabled;
import org.glowroot.plugin.api.weaving.OnAfter;
import org.glowroot.plugin.api.weaving.OnBefore;
import org.glowroot.plugin.api.weaving.Pointcut;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class ExpensiveCallAspect {

    private static final TransactionService transactionService = Agent.getTransactionService();
    private static final ConfigService configService =
            Agent.getConfigService("glowroot-ui-sandbox");

    private static final Random random = new Random();
    private static final Exception nestedCause =
            new IllegalArgumentException("A cause with a different stack trace");
    private static final Exception cause =
            new IllegalStateException("A cause with a different stack trace", nestedCause);

    @Pointcut(className = "org.glowroot.sandbox.ui.ExpensiveCall", methodName = "execute0",
            methodParameterTypes = {}, timerName = "expensive 0")
    public static class ExpensiveCallAdvice0 {
        private static final TimerName timerName =
                transactionService.getTimerName(ExpensiveCallAdvice0.class);
        @IsEnabled
        public static boolean isEnabled() {
            return configService.isEnabled();
        }
        @OnBefore
        public static QueryEntry onBefore(@BindReceiver Object expensiveCall,
                @BindClassMeta ExpensiveCallInvoker expensiveCallInvoker) {
            // not delegating to onBeforeInternal(), this pointcut returns message supplier with
            // detail
            MessageSupplier messageSupplier =
                    getMessageSupplierWithDetail(expensiveCall, expensiveCallInvoker);
            char randomChar = (char) ('a' + random.nextInt(26));
            String queryText = "this is a query " + randomChar;
            return transactionService.startQueryEntry("EQL", queryText, messageSupplier, timerName);
        }
        @OnAfter
        public static void onAfter(@BindTraveler QueryEntry query) {
            query.incrementCurrRow();
            query.incrementCurrRow();
            query.incrementCurrRow();
            if (random.nextDouble() < 0.05) {
                // TraceEntry.endWithStackTrace() must be called directly from @On.. method so it
                // can
                // strip back the stack trace to the method picked out by the @Pointcut
                query.endWithStackTrace(0, NANOSECONDS);
            } else {
                onAfterInternal(query, 0);
            }
        }
    }

    @Pointcut(className = "org.glowroot.sandbox.ui.ExpensiveCall", methodName = "execute1",
            methodParameterTypes = {}, timerName = "expensive 1")
    public static class ExpensiveCallAdvice1 {
        private static final TimerName timerName =
                transactionService.getTimerName(ExpensiveCallAdvice1.class);
        @IsEnabled
        public static boolean isEnabled() {
            return configService.isEnabled();
        }
        @OnBefore
        public static TraceEntry onBefore(@BindReceiver Object expensiveCall,
                @BindClassMeta ExpensiveCallInvoker expensiveCallInvoker) {
            return onBeforeInternal(expensiveCall, expensiveCallInvoker, timerName);
        }
        @OnAfter
        public static void onAfter(@BindTraveler TraceEntry traceEntry) {
            if (traceEntry != null && random.nextDouble() < 0.05) {
                // TraceEntry.endWithStackTrace() must be called directly from @On.. method so it
                // can
                // strip back the stack trace to the method picked out by the @Pointcut
                traceEntry.endWithStackTrace(0, NANOSECONDS);
            } else {
                onAfterInternal(traceEntry, 1);
            }
        }
    }

    @Pointcut(className = "org.glowroot.sandbox.ui.ExpensiveCall", methodName = "execute2",
            methodParameterTypes = {}, timerName = "expensive 2")
    public static class ExpensiveCallAdvice2 {
        private static final TimerName timerName =
                transactionService.getTimerName(ExpensiveCallAdvice2.class);
        @IsEnabled
        public static boolean isEnabled() {
            return configService.isEnabled();
        }
        @OnBefore
        public static TraceEntry onBefore(@BindReceiver Object expensiveCall,
                @BindClassMeta ExpensiveCallInvoker expensiveCallInvoker) {
            return onBeforeInternal(expensiveCall, expensiveCallInvoker, timerName);
        }
        @OnAfter
        public static void onAfter(@BindTraveler TraceEntry traceEntry) {
            if (traceEntry != null && random.nextDouble() < 0.05) {
                // TraceEntry.endWithStackTrace() must be called directly from @On.. method so it
                // can
                // strip back the stack trace to the method picked out by the @Pointcut
                traceEntry.endWithStackTrace(0, NANOSECONDS);
            } else {
                onAfterInternal(traceEntry, 2);
            }
        }
    }

    @Pointcut(className = "org.glowroot.sandbox.ui.ExpensiveCall", methodName = "execute3",
            methodParameterTypes = {}, timerName = "expensive 3")
    public static class ExpensiveCallAdvice3 {
        private static final TimerName timerName =
                transactionService.getTimerName(ExpensiveCallAdvice3.class);
        @IsEnabled
        public static boolean isEnabled() {
            return configService.isEnabled();
        }
        @OnBefore
        public static TraceEntry onBefore(@BindReceiver Object expensiveCall,
                @BindClassMeta ExpensiveCallInvoker expensiveCallInvoker) {
            return onBeforeInternal(expensiveCall, expensiveCallInvoker, timerName);
        }
        @OnAfter
        public static void onAfter(@BindTraveler TraceEntry traceEntry) {
            if (traceEntry != null && random.nextDouble() < 0.05) {
                // TraceEntry.endWithStackTrace() must be called directly from @On.. method so it
                // can
                // strip back the stack trace to the method picked out by the @Pointcut
                traceEntry.endWithStackTrace(0, NANOSECONDS);
            } else {
                onAfterInternal(traceEntry, 3);
            }
        }
    }

    @Pointcut(className = "org.glowroot.sandbox.ui.ExpensiveCall", methodName = "execute4",
            methodParameterTypes = {}, timerName = "expensive 4")
    public static class ExpensiveCallAdvice4 {
        private static final TimerName timerName =
                transactionService.getTimerName(ExpensiveCallAdvice4.class);
        @IsEnabled
        public static boolean isEnabled() {
            return configService.isEnabled();
        }
        @OnBefore
        public static TraceEntry onBefore(@BindReceiver Object expensiveCall,
                @BindClassMeta ExpensiveCallInvoker expensiveCallInvoker) {
            return onBeforeInternal(expensiveCall, expensiveCallInvoker, timerName);
        }
        @OnAfter
        public static void onAfter(@BindTraveler TraceEntry traceEntry) {
            if (traceEntry != null && random.nextDouble() < 0.05) {
                // TraceEntry.endWithStackTrace() must be called directly from @On.. method so it
                // can
                // strip back the stack trace to the method picked out by the @Pointcut
                traceEntry.endWithStackTrace(0, NANOSECONDS);
            } else {
                onAfterInternal(traceEntry, 4);
            }
        }
    }

    @Pointcut(className = "org.glowroot.sandbox.ui.ExpensiveCall", methodName = "execute5",
            methodParameterTypes = {}, timerName = "expensive 5")
    public static class ExpensiveCallAdvice5 {
        private static final TimerName timerName =
                transactionService.getTimerName(ExpensiveCallAdvice5.class);
        @IsEnabled
        public static boolean isEnabled() {
            return configService.isEnabled();
        }
        @OnBefore
        public static TraceEntry onBefore(@BindReceiver Object expensiveCall,
                @BindClassMeta ExpensiveCallInvoker expensiveCallInvoker) {
            return onBeforeInternal(expensiveCall, expensiveCallInvoker, timerName);
        }
        @OnAfter
        public static void onAfter(@BindTraveler TraceEntry traceEntry) {
            if (traceEntry != null && random.nextDouble() < 0.05) {
                // TraceEntry.endWithStackTrace() must be called directly from @On.. method so it
                // can
                // strip back the stack trace to the method picked out by the @Pointcut
                traceEntry.endWithStackTrace(0, NANOSECONDS);
            } else {
                onAfterInternal(traceEntry, 5);
            }
        }
    }

    @Pointcut(className = "org.glowroot.sandbox.ui.ExpensiveCall", methodName = "execute6",
            methodParameterTypes = {}, timerName = "expensive 6")
    public static class ExpensiveCallAdvice6 {
        private static final TimerName timerName =
                transactionService.getTimerName(ExpensiveCallAdvice6.class);
        @IsEnabled
        public static boolean isEnabled() {
            return configService.isEnabled();
        }
        @OnBefore
        public static TraceEntry onBefore(@BindReceiver Object expensiveCall,
                @BindClassMeta ExpensiveCallInvoker expensiveCallInvoker) {
            return onBeforeInternal(expensiveCall, expensiveCallInvoker, timerName);
        }
        @OnAfter
        public static void onAfter(@BindTraveler TraceEntry traceEntry) {
            if (traceEntry != null && random.nextDouble() < 0.05) {
                // TraceEntry.endWithStackTrace() must be called directly from @On.. method so it
                // can
                // strip back the stack trace to the method picked out by the @Pointcut
                traceEntry.endWithStackTrace(0, NANOSECONDS);
            } else {
                onAfterInternal(traceEntry, 6);
            }
        }
    }

    @Pointcut(className = "org.glowroot.sandbox.ui.ExpensiveCall", methodName = "execute7",
            methodParameterTypes = {}, timerName = "expensive 7")
    public static class ExpensiveCallAdvice7 {
        private static final TimerName timerName =
                transactionService.getTimerName(ExpensiveCallAdvice7.class);
        @IsEnabled
        public static boolean isEnabled() {
            return configService.isEnabled();
        }
        @OnBefore
        public static TraceEntry onBefore(@BindReceiver Object expensiveCall,
                @BindClassMeta ExpensiveCallInvoker expensiveCallInvoker) {
            return onBeforeInternal(expensiveCall, expensiveCallInvoker, timerName);
        }
        @OnAfter
        public static void onAfter(@BindTraveler TraceEntry traceEntry) {
            if (traceEntry != null && random.nextDouble() < 0.05) {
                // TraceEntry.endWithStackTrace() must be called directly from @On.. method so it
                // can
                // strip back the stack trace to the method picked out by the @Pointcut
                traceEntry.endWithStackTrace(0, NANOSECONDS);
            } else {
                onAfterInternal(traceEntry, 7);
            }
        }
    }

    @Pointcut(className = "org.glowroot.sandbox.ui.ExpensiveCall", methodName = "execute8",
            methodParameterTypes = {}, timerName = "expensive 8")
    public static class ExpensiveCallAdvice8 {
        private static final TimerName timerName =
                transactionService.getTimerName(ExpensiveCallAdvice8.class);
        @IsEnabled
        public static boolean isEnabled() {
            return configService.isEnabled();
        }
        @OnBefore
        public static TraceEntry onBefore(@BindReceiver Object expensiveCall,
                @BindClassMeta ExpensiveCallInvoker expensiveCallInvoker) {
            return onBeforeInternal(expensiveCall, expensiveCallInvoker, timerName);
        }
        @OnAfter
        public static void onAfter(@BindTraveler TraceEntry traceEntry) {
            if (traceEntry != null && random.nextDouble() < 0.05) {
                // TraceEntry.endWithStackTrace() must be called directly from @On.. method so it
                // can
                // strip back the stack trace to the method picked out by the @Pointcut
                traceEntry.endWithStackTrace(0, NANOSECONDS);
            } else {
                onAfterInternal(traceEntry, 8);
            }
        }
    }

    @Pointcut(className = "org.glowroot.sandbox.ui.ExpensiveCall", methodName = "execute9",
            methodParameterTypes = {}, timerName = "expensive 9 really long to test wrapping")
    public static class ExpensiveCallAdvice9 {
        private static final TimerName timerName =
                transactionService.getTimerName(ExpensiveCallAdvice9.class);
        @IsEnabled
        public static boolean isEnabled() {
            return configService.isEnabled();
        }
        @OnBefore
        public static TraceEntry onBefore(@BindReceiver Object expensiveCall,
                @BindClassMeta ExpensiveCallInvoker expensiveCallInvoker) {
            return onBeforeInternal(expensiveCall, expensiveCallInvoker, timerName);
        }
        @OnAfter
        public static void onAfter(@BindTraveler TraceEntry traceEntry) {
            if (traceEntry != null && random.nextDouble() < 0.05) {
                // TraceEntry.endWithStackTrace() must be called directly from @On.. method so it
                // can
                // strip back the stack trace to the method picked out by the @Pointcut
                traceEntry.endWithStackTrace(0, NANOSECONDS);
            } else {
                onAfterInternal(traceEntry, 9);
            }
        }
    }

    private static TraceEntry onBeforeInternal(Object expensiveCall,
            ExpensiveCallInvoker expensiveCallInvoker, TimerName timerName) {
        if (random.nextDouble() < 0.05) {
            return null;
        }
        MessageSupplier messageSupplier =
                MessageSupplier.from(expensiveCallInvoker.getTraceEntryMessage(expensiveCall));
        return transactionService.startTraceEntry(messageSupplier, timerName);
    }

    private static void onAfterInternal(TraceEntry traceEntry, int num) {
        double value = random.nextDouble();
        if (traceEntry == null) {
            if (value < 0.5) {
                transactionService.addErrorEntry(new IllegalStateException(
                        "Exception in execute" + num
                                + "\nwith no trace entry text and no custom error message",
                        getRandomCause()));
            } else {
                transactionService.addErrorEntry("randomized error\nwith no trace entry text",
                        new IllegalStateException(
                                "Exception in execute" + num + "\nwith no trace entry text",
                                getRandomCause()));
            }
            return;
        }
        if (value < 0.94) {
            traceEntry.end();
        } else if (value < 0.96) {
            traceEntry.endWithError(new IllegalStateException(
                    "Exception in execute" + num + "\nwith no custom error message",
                    getRandomCause()));
        } else {
            traceEntry.endWithError("randomized error",
                    new IllegalStateException("Exception in execute" + num, getRandomCause()));
        }
    }

    private static MessageSupplier getMessageSupplierWithDetail(final Object expensiveCall,
            final ExpensiveCallInvoker expensiveCallInvoker) {
        return new MessageSupplier() {
            @Override
            public Message get() {
                Map<String, ?> detail =
                        ImmutableMap.of("attr1", "value1\nwith newline", "attr2", "value2", "attr3",
                                ImmutableMap.of("attr31",
                                        ImmutableMap.of("attr311",
                                                ImmutableList.of("v311aa", "v311bb")),
                                        "attr32", "value32\nwith newline", "attr33", "value33"));
                String traceEntryMessage = expensiveCallInvoker.getTraceEntryMessage(expensiveCall);
                return Message.from(traceEntryMessage, detail);
            }
        };
    }

    private static Exception getRandomCause() {
        if (random.nextBoolean()) {
            return cause;
        } else {
            return null;
        }
    }
}
