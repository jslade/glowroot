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
package org.glowroot.common.config;

import org.junit.Test;

import org.glowroot.common.config.InstrumentationConfig.CaptureKind;

import static org.assertj.core.api.Assertions.assertThat;

public class InstrumentationConfigValidationTest {

    private final InstrumentationConfig baseConfig = ImmutableInstrumentationConfig.builder()
            .className("a")
            .methodName("n")
            .addMethodParameterTypes("java.lang.String")
            .methodReturnType("")
            .captureKind(CaptureKind.TIMER)
            .timerName("t")
            .traceEntryTemplate("")
            .traceEntryCaptureSelfNested(false)
            .transactionType("")
            .transactionNameTemplate("")
            .transactionUserTemplate("")
            .enabledProperty("")
            .traceEntryEnabledProperty("")
            .build();

    @Test
    public void testValid() {
        // given
        // when
        // then
        assertThat(baseConfig.validationErrors()).isEmpty();
    }

    @Test
    public void testInvalidClassNameAndMethodName() {
        // given
        InstrumentationConfig config = ImmutableInstrumentationConfig.builder().copyFrom(baseConfig)
                .className("").methodName("").build();
        // when
        // then
        assertThat(config.validationErrors()).containsExactly("className is empty",
                "methodName is empty");
    }

    @Test
    public void testInvalidEmptyTimerName() {
        // given
        InstrumentationConfig config =
                ImmutableInstrumentationConfig.builder().copyFrom(baseConfig).timerName("").build();
        // when
        // then
        assertThat(config.validationErrors()).containsExactly("timerName is empty");
    }

    @Test
    public void testInvalidCharactersInTimerName() {
        // given
        InstrumentationConfig config = ImmutableInstrumentationConfig.builder().copyFrom(baseConfig)
                .timerName("a_b").build();
        // when
        // then
        assertThat(config.validationErrors())
                .containsExactly("timerName contains invalid characters: a_b");
    }

    @Test
    public void testValidEmptyTimerName() {
        // given
        InstrumentationConfig config = ImmutableInstrumentationConfig.builder().copyFrom(baseConfig)
                .captureKind(CaptureKind.OTHER).timerName("").build();
        // when
        // then
        assertThat(config.validationErrors()).isEmpty();
    }

    @Test
    public void testInvalidTraceEntry() {
        // given
        InstrumentationConfig config = ImmutableInstrumentationConfig.builder().copyFrom(baseConfig)
                .captureKind(CaptureKind.TRACE_ENTRY).build();
        // when
        // then
        assertThat(config.validationErrors()).containsExactly("traceEntryTemplate is empty");
    }

    @Test
    public void testInvalidTransaction() {
        // given
        InstrumentationConfig config = ImmutableInstrumentationConfig.builder().copyFrom(baseConfig)
                .captureKind(CaptureKind.TRANSACTION).build();
        // when
        // then
        assertThat(config.validationErrors()).containsExactly("transactionType is empty",
                "transactionNameTemplate is empty");
    }
}
