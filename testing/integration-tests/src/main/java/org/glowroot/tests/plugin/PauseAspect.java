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
package org.glowroot.tests.plugin;

import org.glowroot.plugin.api.Agent;
import org.glowroot.plugin.api.config.BooleanProperty;
import org.glowroot.plugin.api.config.ConfigService;
import org.glowroot.plugin.api.transaction.MessageSupplier;
import org.glowroot.plugin.api.transaction.TimerName;
import org.glowroot.plugin.api.transaction.TraceEntry;
import org.glowroot.plugin.api.transaction.TransactionService;
import org.glowroot.plugin.api.weaving.BindTraveler;
import org.glowroot.plugin.api.weaving.IsEnabled;
import org.glowroot.plugin.api.weaving.OnAfter;
import org.glowroot.plugin.api.weaving.OnBefore;
import org.glowroot.plugin.api.weaving.Pointcut;
import org.glowroot.tests.plugin.LogErrorAspect.LogErrorAdvice;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class PauseAspect {

    private static final TransactionService transactionService = Agent.getTransactionService();
    private static final ConfigService configService =
            Agent.getConfigService("glowroot-integration-tests");

    private static final BooleanProperty captureTraceEntryStackTraces =
            configService.getBooleanProperty("captureTraceEntryStackTraces");

    @Pointcut(className = "org.glowroot.tests.Pause", methodName = "pause*",
            methodParameterTypes = {}, timerName = "pause")
    public static class PauseAdvice {

        private static final TimerName timerName =
                transactionService.getTimerName(LogErrorAdvice.class);

        @IsEnabled
        public static boolean isEnabled() {
            return configService.isEnabled();
        }

        @OnBefore
        public static TraceEntry onBefore() {
            return transactionService.startTraceEntry(
                    MessageSupplier.from("Pause.pauseOneMillisecond()"), timerName);
        }

        @OnAfter
        public static void onAfter(@BindTraveler TraceEntry traceEntry) {
            if (captureTraceEntryStackTraces.value()) {
                traceEntry.endWithStackTrace(0, NANOSECONDS);
            } else {
                traceEntry.end();
            }
        }
    }

    // this is just to generate an additional $glowroot$ method to test that consecutive
    // $glowroot$ methods in an entry stack trace are stripped out correctly
    @Pointcut(className = "org.glowroot.tests.LogError", methodName = "pause",
            methodParameterTypes = {"int"}, timerName = "pause 2")
    public static class PauseAdvice2 {}
}
