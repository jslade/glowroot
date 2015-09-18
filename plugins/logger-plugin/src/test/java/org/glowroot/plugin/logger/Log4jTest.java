/*
 * Copyright 2014-2015 the original author or authors.
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
package org.glowroot.plugin.logger;

import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.Containers;
import org.glowroot.container.AppUnderTest;
import org.glowroot.container.Container;
import org.glowroot.container.TraceMarker;
import org.glowroot.container.config.PluginConfig;
import org.glowroot.container.trace.Trace;

import static org.assertj.core.api.Assertions.assertThat;

public class Log4jTest {

    private static final String PLUGIN_ID = "logger";

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = Containers.getSharedContainer();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        container.close();
    }

    @After
    public void afterEachTest() throws Exception {
        container.checkAndReset();
    }

    @Test
    public void testLog() throws Exception {
        // given
        container.getConfigService().setPluginProperty(PLUGIN_ID,
                "traceErrorOnErrorWithoutThrowable", true);
        // when
        container.executeAppUnderTest(ShouldLog.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        List<Trace.Entry> entries = container.getTraceService().getEntries(header.id());
        assertThat(header.error().get().message()).isEqualTo("efg");
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).message()).isEqualTo("log warn: def");
        assertThat(entries.get(1).message()).isEqualTo("log error: efg");
        assertThat(entries.get(2).message()).isEqualTo("log fatal: fgh");
    }

    @Test
    public void testLogWithThrowable() throws Exception {
        // given
        container.getConfigService().setPluginProperty(PLUGIN_ID,
                "traceErrorOnErrorWithoutThrowable", true);
        // when
        container.executeAppUnderTest(ShouldLogWithThrowable.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        List<Trace.Entry> entries = container.getTraceService().getEntries(header.id());
        assertThat(header.error().get().message()).isEqualTo("efg_");
        assertThat(entries).hasSize(3);

        Trace.Entry warnEntry = entries.get(0);
        assertThat(warnEntry.message()).isEqualTo("log warn: def_");
        assertThat(warnEntry.error().get().message()).isEqualTo("456");
        assertThat(warnEntry.error().get().exception().get().stackTraceElements().get(0))
                .contains("traceMarker");

        Trace.Entry errorEntry = entries.get(1);
        assertThat(errorEntry.message()).isEqualTo("log error: efg_");
        assertThat(errorEntry.error().get().message()).isEqualTo("567");
        assertThat(errorEntry.error().get().exception().get().stackTraceElements().get(0))
                .contains("traceMarker");

        Trace.Entry fatalEntry = entries.get(2);
        assertThat(fatalEntry.message()).isEqualTo("log fatal: fgh_");
        assertThat(fatalEntry.error().get().message()).isEqualTo("678");
        assertThat(fatalEntry.error().get().exception().get().stackTraceElements().get(0))
                .contains("traceMarker");
    }

    @Test
    public void testLogWithNullThrowable() throws Exception {
        // given
        container.getConfigService().setPluginProperty(PLUGIN_ID,
                "traceErrorOnErrorWithoutThrowable", true);
        // when
        container.executeAppUnderTest(ShouldLogWithNullThrowable.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        List<Trace.Entry> entries = container.getTraceService().getEntries(header.id());
        assertThat(header.error().get().message()).isEqualTo("efg_");
        assertThat(entries).hasSize(3);

        Trace.Entry warnEntry = entries.get(0);
        assertThat(warnEntry.message()).isEqualTo("log warn: def_");
        assertThat(warnEntry.error().get().message()).isEqualTo("def_");
        Trace.Entry errorEntry = entries.get(1);
        assertThat(errorEntry.message()).isEqualTo("log error: efg_");
        assertThat(errorEntry.error().get().message()).isEqualTo("efg_");
        Trace.Entry fatalEntry = entries.get(2);
        assertThat(fatalEntry.message()).isEqualTo("log fatal: fgh_");
        assertThat(fatalEntry.error().get().message()).isEqualTo("fgh_");
    }

    @Test
    public void testLogWithPriority() throws Exception {
        // given
        container.getConfigService().setPluginProperty(PLUGIN_ID,
                "traceErrorOnErrorWithoutThrowable", true);
        // when
        container.executeAppUnderTest(ShouldLogWithPriority.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        List<Trace.Entry> entries = container.getTraceService().getEntries(header.id());
        assertThat(header.error().get().message()).isEqualTo("efg__");
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).message()).isEqualTo("log warn: def__");
        assertThat(entries.get(1).message()).isEqualTo("log error: efg__");
        assertThat(entries.get(2).message()).isEqualTo("log fatal: fgh__");
    }

    @Test
    public void testLogWithPriorityAndThrowable() throws Exception {
        // given
        // when
        container.executeAppUnderTest(ShouldLogWithPriorityAndThrowable.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        List<Trace.Entry> entries = container.getTraceService().getEntries(header.id());
        assertThat(header.error().get().message()).isEqualTo("efg___");
        assertThat(entries).hasSize(3);

        Trace.Entry warnEntry = entries.get(0);
        assertThat(warnEntry.message()).isEqualTo("log warn: def___");
        assertThat(warnEntry.error().get().message()).isEqualTo("456_");
        assertThat(warnEntry.error().get().exception().get().stackTraceElements().get(0))
                .contains("traceMarker");

        Trace.Entry errorEntry = entries.get(1);
        assertThat(errorEntry.message()).isEqualTo("log error: efg___");
        assertThat(errorEntry.error().get().message()).isEqualTo("567_");
        assertThat(errorEntry.error().get().exception().get().stackTraceElements().get(0))
                .contains("traceMarker");

        Trace.Entry fatalEntry = entries.get(2);
        assertThat(fatalEntry.message()).isEqualTo("log fatal: fgh___");
        assertThat(fatalEntry.error().get().message()).isEqualTo("678_");
        assertThat(fatalEntry.error().get().exception().get().stackTraceElements().get(0))
                .contains("traceMarker");
    }

    @Test
    public void testLogWithPriorityAndNullThrowable() throws Exception {
        // given
        container.getConfigService().setPluginProperty(PLUGIN_ID,
                "traceErrorOnErrorWithoutThrowable", true);
        // when
        container.executeAppUnderTest(ShouldLogWithPriorityAndNullThrowable.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        List<Trace.Entry> entries = container.getTraceService().getEntries(header.id());
        assertThat(header.error().get().message()).isEqualTo("efg___null");
        assertThat(entries).hasSize(3);

        Trace.Entry warnEntry = entries.get(0);
        assertThat(warnEntry.message()).isEqualTo("log warn: def___null");
        assertThat(warnEntry.error().get().message()).isEqualTo("def___null");
        Trace.Entry errorEntry = entries.get(1);
        assertThat(errorEntry.message()).isEqualTo("log error: efg___null");
        assertThat(errorEntry.error().get().message()).isEqualTo("efg___null");
        Trace.Entry fatalEntry = entries.get(2);
        assertThat(fatalEntry.message()).isEqualTo("log fatal: fgh___null");
        assertThat(fatalEntry.error().get().message()).isEqualTo("fgh___null");
    }

    @Test
    public void testLocalizedLog() throws Exception {
        // given
        // when
        container.executeAppUnderTest(ShouldLocalizedLog.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        List<Trace.Entry> entries = container.getTraceService().getEntries(header.id());
        assertThat(header.error().get().message()).isEqualTo("efg____");
        assertThat(entries).hasSize(3);

        Trace.Entry warnEntry = entries.get(0);
        assertThat(warnEntry.message()).isEqualTo("log warn (localized): def____");
        assertThat(warnEntry.error().get().message()).isEqualTo("456__");
        assertThat(warnEntry.error().get().exception().get().stackTraceElements().get(0))
                .contains("traceMarker");

        Trace.Entry errorEntry = entries.get(1);
        assertThat(errorEntry.message()).isEqualTo("log error (localized): efg____");
        assertThat(errorEntry.error().get().message()).isEqualTo("567__");
        assertThat(errorEntry.error().get().exception().get().stackTraceElements().get(0))
                .contains("traceMarker");

        Trace.Entry fatalEntry = entries.get(2);
        assertThat(fatalEntry.message()).isEqualTo("log fatal (localized): fgh____");
        assertThat(fatalEntry.error().get().message()).isEqualTo("678__");
    }

    @Test
    public void testLocalizedLogWithNullThrowable() throws Exception {
        // given
        container.getConfigService().setPluginProperty(PLUGIN_ID,
                "traceErrorOnErrorWithoutThrowable", true);
        // when
        container.executeAppUnderTest(ShouldLocalizedLogWithNullThrowable.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        List<Trace.Entry> entries = container.getTraceService().getEntries(header.id());
        assertThat(header.error().get().message()).isEqualTo("efg____null");
        assertThat(entries).hasSize(3);

        Trace.Entry warnEntry = entries.get(0);
        assertThat(warnEntry.message()).isEqualTo("log warn (localized): def____null");
        assertThat(warnEntry.error().get().message()).isEqualTo("def____null");
        Trace.Entry errorEntry = entries.get(1);
        assertThat(errorEntry.message()).isEqualTo("log error (localized): efg____null");
        assertThat(errorEntry.error().get().message()).isEqualTo("efg____null");
        Trace.Entry fatalEntry = entries.get(2);
        assertThat(fatalEntry.message()).isEqualTo("log fatal (localized): fgh____null");
        assertThat(fatalEntry.error().get().message()).isEqualTo("fgh____null");
    }

    @Test
    public void testLocalizedLogWithParameters() throws Exception {
        // given
        // when
        container.executeAppUnderTest(ShouldLocalizedLogWithParameters.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        List<Trace.Entry> entries = container.getTraceService().getEntries(header.id());
        assertThat(header.error().get().message()).isEqualTo("efg____");
        assertThat(entries).hasSize(3);

        Trace.Entry warnEntry = entries.get(0);
        assertThat(warnEntry.message()).isEqualTo("log warn (localized): def____ [d, e, f]");
        assertThat(warnEntry.error().get().message()).isEqualTo("456__");
        assertThat(warnEntry.error().get().exception().get().stackTraceElements().get(0))
                .contains("traceMarker");

        Trace.Entry errorEntry = entries.get(1);
        assertThat(errorEntry.message())
                .isEqualTo("log error (localized): efg____ [e, f, g]");
        assertThat(errorEntry.error().get().message()).isEqualTo("567__");
        assertThat(errorEntry.error().get().exception().get().stackTraceElements().get(0))
                .contains("traceMarker");

        Trace.Entry fatalEntry = entries.get(2);
        assertThat(fatalEntry.message())
                .isEqualTo("log fatal (localized): fgh____ [f, g, h]");
        assertThat(fatalEntry.error().get().message()).isEqualTo("678__");
        assertThat(fatalEntry.error().get().exception().get().stackTraceElements().get(0))
                .contains("traceMarker");
    }

    @Test
    public void testLocalizedLogWithEmptyParameters() throws Exception {
        // given
        // when
        container.executeAppUnderTest(ShouldLocalizedLogWithEmptyParameters.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        List<Trace.Entry> entries = container.getTraceService().getEntries(header.id());
        assertThat(header.error().get().message()).isEqualTo("efg____");
        assertThat(entries).hasSize(3);

        Trace.Entry warnEntry = entries.get(0);
        assertThat(warnEntry.message()).isEqualTo("log warn (localized): def____");
        assertThat(warnEntry.error().get().message()).isEqualTo("456__");
        assertThat(warnEntry.error().get().exception().get().stackTraceElements().get(0))
                .contains("traceMarker");

        Trace.Entry errorEntry = entries.get(1);
        assertThat(errorEntry.message()).isEqualTo("log error (localized): efg____");
        assertThat(errorEntry.error().get().message()).isEqualTo("567__");
        assertThat(errorEntry.error().get().exception().get().stackTraceElements().get(0))
                .contains("traceMarker");

        Trace.Entry fatalEntry = entries.get(2);
        assertThat(fatalEntry.message()).isEqualTo("log fatal (localized): fgh____");
        assertThat(fatalEntry.error().get().message()).isEqualTo("678__");
        assertThat(fatalEntry.error().get().exception().get().stackTraceElements().get(0))
                .contains("traceMarker");
    }

    @Test
    public void testLocalizedLogWithParametersAndNullThrowable() throws Exception {
        // given
        container.getConfigService().setPluginProperty(PLUGIN_ID,
                "traceErrorOnErrorWithoutThrowable", true);
        // when
        container.executeAppUnderTest(ShouldLocalizedLogWithParametersAndNullThrowable.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        List<Trace.Entry> entries = container.getTraceService().getEntries(header.id());
        assertThat(header.error().get().message()).isEqualTo("efg____null");
        assertThat(entries).hasSize(3);

        Trace.Entry warnEntry = entries.get(0);
        assertThat(warnEntry.message())
                .isEqualTo("log warn (localized): def____null [d_, e_, f_]");
        assertThat(warnEntry.error().get().message()).isEqualTo("def____null [d_, e_, f_]");
        Trace.Entry errorEntry = entries.get(1);
        assertThat(errorEntry.message())
                .isEqualTo("log error (localized): efg____null [e_, f_, g_]");
        assertThat(errorEntry.error().get().message()).isEqualTo("efg____null [e_, f_, g_]");
        Trace.Entry fatalEntry = entries.get(2);
        assertThat(fatalEntry.message())
                .isEqualTo("log fatal (localized): fgh____null [f_, g_, h_]");
        assertThat(fatalEntry.error().get().message()).isEqualTo("fgh____null [f_, g_, h_]");
    }

    @Test
    public void testPluginDisabled() throws Exception {
        // given
        PluginConfig pluginConfig = container.getConfigService().getPluginConfig(PLUGIN_ID);
        pluginConfig.setEnabled(false);
        container.getConfigService().updatePluginConfig(PLUGIN_ID, pluginConfig);
        // when
        container.executeAppUnderTest(ShouldLog.class);
        // then
        assertNoLogTraceEntries();
        // when
        container.executeAppUnderTest(ShouldLogWithThrowable.class);
        // then
        assertNoLogTraceEntries();
        // when
        container.executeAppUnderTest(ShouldLogWithNullThrowable.class);
        // then
        assertNoLogTraceEntries();
        // when
        container.executeAppUnderTest(ShouldLogWithPriority.class);
        // then
        assertNoLogTraceEntries();
        // when
        container.executeAppUnderTest(ShouldLogWithPriorityAndThrowable.class);
        // then
        assertNoLogTraceEntries();
        // when
        container.executeAppUnderTest(ShouldLogWithPriorityAndNullThrowable.class);
        // then
        assertNoLogTraceEntries();
        // when
        container.executeAppUnderTest(ShouldLocalizedLogWithNullThrowable.class);
        // then
        assertNoLogTraceEntries();
        // when
        container.executeAppUnderTest(ShouldLocalizedLogWithParameters.class);
        // then
        assertNoLogTraceEntries();
        // when
        container.executeAppUnderTest(ShouldLocalizedLogWithEmptyParameters.class);
        // then
        assertNoLogTraceEntries();
        // when
        container.executeAppUnderTest(ShouldLocalizedLogWithParametersAndNullThrowable.class);
        // then
        assertNoLogTraceEntries();
    }

    private static void assertNoLogTraceEntries() throws Exception {
        Trace.Header header = container.getTraceService().getLastTrace();
        assertThat(header.entryCount()).isZero();
    }

    public static class ShouldLog implements AppUnderTest, TraceMarker {
        private static final Logger logger = Logger.getLogger(ShouldLog.class);
        @Override
        public void executeApp() {
            traceMarker();
        }
        @Override
        public void traceMarker() {
            logger.trace("abc");
            logger.debug("bcd");
            logger.info("cde");
            logger.warn("def");
            logger.error("efg");
            logger.fatal("fgh");
        }
    }

    public static class ShouldLogWithThrowable implements AppUnderTest, TraceMarker {
        private static final Logger logger = Logger.getLogger(ShouldLogWithThrowable.class);
        @Override
        public void executeApp() {
            traceMarker();
        }
        @Override
        public void traceMarker() {
            logger.trace("abc_", new IllegalStateException("123"));
            logger.debug("bcd_", new IllegalStateException("234"));
            logger.info("cde_", new IllegalStateException("345"));
            logger.warn("def_", new IllegalStateException("456"));
            logger.error("efg_", new IllegalStateException("567"));
            logger.fatal("fgh_", new IllegalStateException("678"));
        }
    }

    public static class ShouldLogWithNullThrowable implements AppUnderTest, TraceMarker {
        private static final Logger logger = Logger.getLogger(ShouldLogWithNullThrowable.class);
        @Override
        public void executeApp() {
            traceMarker();
        }
        @Override
        public void traceMarker() {
            logger.trace("abc_", null);
            logger.debug("bcd_", null);
            logger.info("cde_", null);
            logger.warn("def_", null);
            logger.error("efg_", null);
            logger.fatal("fgh_", null);
        }
    }

    public static class ShouldLogWithPriority implements AppUnderTest, TraceMarker {
        private static final Logger logger = Logger.getLogger(ShouldLogWithPriority.class);
        @Override
        public void executeApp() {
            traceMarker();
        }
        @Override
        public void traceMarker() {
            try {
                logger.log(null, "abc__");
            } catch (NullPointerException e) {
                // re-throw if it does not originate from log4j
                if (!e.getStackTrace()[0].getClassName().startsWith("org.apache.log4j.")) {
                    throw e;
                }
            }
            logger.log(Level.TRACE, "abc__");
            logger.log(Level.DEBUG, "bcd__");
            logger.log(Level.INFO, "cde__");
            logger.log(Level.WARN, "def__");
            logger.log(Level.ERROR, "efg__");
            logger.log(Level.FATAL, "fgh__");
        }
    }

    public static class ShouldLogWithPriorityAndThrowable implements AppUnderTest, TraceMarker {
        private static final Logger logger =
                Logger.getLogger(ShouldLogWithPriorityAndThrowable.class);
        @Override
        public void executeApp() {
            traceMarker();
        }
        @Override
        public void traceMarker() {
            try {
                logger.log(null, "abc___", new IllegalStateException("123_"));
            } catch (NullPointerException e) {
                // re-throw if it does not originate from log4j
                if (!e.getStackTrace()[0].getClassName().startsWith("org.apache.log4j.")) {
                    throw e;
                }
            }
            logger.log(Level.TRACE, "abc___", new IllegalStateException("123_"));
            logger.log(Level.DEBUG, "bcd___", new IllegalStateException("234_"));
            logger.log(Level.INFO, "cde___", new IllegalStateException("345_"));
            logger.log(Level.WARN, "def___", new IllegalStateException("456_"));
            logger.log(Level.ERROR, "efg___", new IllegalStateException("567_"));
            logger.log(Level.FATAL, "fgh___", new IllegalStateException("678_"));
        }
    }

    public static class ShouldLogWithPriorityAndNullThrowable implements AppUnderTest, TraceMarker {
        private static final Logger logger =
                Logger.getLogger(ShouldLogWithPriorityAndNullThrowable.class);
        @Override
        public void executeApp() {
            traceMarker();
        }
        @Override
        public void traceMarker() {
            logger.log(Level.TRACE, "abc___null", null);
            logger.log(Level.DEBUG, "bcd___null", null);
            logger.log(Level.INFO, "cde___null", null);
            logger.log(Level.WARN, "def___null", null);
            logger.log(Level.ERROR, "efg___null", null);
            logger.log(Level.FATAL, "fgh___null", null);
        }
    }

    public static class ShouldLocalizedLog implements AppUnderTest, TraceMarker {
        private static final Logger logger = Logger.getLogger(ShouldLocalizedLog.class);
        @Override
        public void executeApp() {
            traceMarker();
        }
        @Override
        public void traceMarker() {
            try {
                logger.l7dlog(null, "abc____", new IllegalStateException("123__"));
            } catch (NullPointerException e) {
                // re-throw if it does not originate from log4j
                if (!e.getStackTrace()[0].getClassName().startsWith("org.apache.log4j.")) {
                    throw e;
                }
            }
            logger.l7dlog(Level.TRACE, "abc____", new IllegalStateException("123__"));
            logger.l7dlog(Level.DEBUG, "bcd____", new IllegalStateException("234__"));
            logger.l7dlog(Level.INFO, "cde____", new IllegalStateException("345__"));
            logger.l7dlog(Level.WARN, "def____", new IllegalStateException("456__"));
            logger.l7dlog(Level.ERROR, "efg____", new IllegalStateException("567__"));
            logger.l7dlog(Level.FATAL, "fgh____", new IllegalStateException("678__"));
        }
    }

    public static class ShouldLocalizedLogWithNullThrowable implements AppUnderTest, TraceMarker {
        private static final Logger logger =
                Logger.getLogger(ShouldLocalizedLogWithNullThrowable.class);
        @Override
        public void executeApp() {
            traceMarker();
        }
        @Override
        public void traceMarker() {
            logger.l7dlog(Level.TRACE, "abc____null", null);
            logger.l7dlog(Level.DEBUG, "bcd____null", null);
            logger.l7dlog(Level.INFO, "cde____null", null);
            logger.l7dlog(Level.WARN, "def____null", null);
            logger.l7dlog(Level.ERROR, "efg____null", null);
            logger.l7dlog(Level.FATAL, "fgh____null", null);
        }
    }

    public static class ShouldLocalizedLogWithParameters implements AppUnderTest, TraceMarker {
        private static final Logger logger =
                Logger.getLogger(ShouldLocalizedLogWithParameters.class);
        @Override
        public void executeApp() {
            traceMarker();
        }
        @Override
        public void traceMarker() {
            try {
                logger.l7dlog(null, "abc____", new Object[] {"a", "b", "c"},
                        new IllegalStateException("123__"));
            } catch (NullPointerException e) {
                // re-throw if it does not originate from log4j
                if (!e.getStackTrace()[0].getClassName().startsWith("org.apache.log4j.")) {
                    throw e;
                }
            }
            logger.l7dlog(Level.TRACE, "abc____", new Object[] {"a", "b", "c"},
                    new IllegalStateException("123__"));
            logger.l7dlog(Level.DEBUG, "bcd____", new Object[] {"b", "c", "d"},
                    new IllegalStateException("234__"));
            logger.l7dlog(Level.INFO, "cde____", new Object[] {"c", "d", "e"},
                    new IllegalStateException("345__"));
            logger.l7dlog(Level.WARN, "def____", new Object[] {"d", "e", "f"},
                    new IllegalStateException("456__"));
            logger.l7dlog(Level.ERROR, "efg____", new Object[] {"e", "f", "g"},
                    new IllegalStateException("567__"));
            logger.l7dlog(Level.FATAL, "fgh____", new Object[] {"f", "g", "h"},
                    new IllegalStateException("678__"));
        }
    }

    public static class ShouldLocalizedLogWithEmptyParameters implements AppUnderTest, TraceMarker {
        private static final Logger logger =
                Logger.getLogger(ShouldLocalizedLogWithEmptyParameters.class);
        @Override
        public void executeApp() {
            traceMarker();
        }
        @Override
        public void traceMarker() {
            try {
                logger.l7dlog(null, "abc____", new Object[] {"a", "b", "c"},
                        new IllegalStateException("123__"));
            } catch (NullPointerException e) {
                // re-throw if it does not originate from log4j
                if (!e.getStackTrace()[0].getClassName().startsWith("org.apache.log4j.")) {
                    throw e;
                }
            }
            logger.l7dlog(Level.TRACE, "abc____", new Object[] {},
                    new IllegalStateException("123__"));
            logger.l7dlog(Level.DEBUG, "bcd____", new Object[] {},
                    new IllegalStateException("234__"));
            logger.l7dlog(Level.INFO, "cde____", new Object[] {},
                    new IllegalStateException("345__"));
            logger.l7dlog(Level.WARN, "def____", new Object[] {},
                    new IllegalStateException("456__"));
            logger.l7dlog(Level.ERROR, "efg____", new Object[] {},
                    new IllegalStateException("567__"));
            logger.l7dlog(Level.FATAL, "fgh____", new Object[] {},
                    new IllegalStateException("678__"));
        }
    }

    public static class ShouldLocalizedLogWithParametersAndNullThrowable
            implements AppUnderTest, TraceMarker {
        private static final Logger logger =
                Logger.getLogger(ShouldLocalizedLogWithParametersAndNullThrowable.class);
        @Override
        public void executeApp() {
            traceMarker();
        }
        @Override
        public void traceMarker() {
            logger.l7dlog(Level.TRACE, "abc____null", new Object[] {"a_", "b_", "c_"}, null);
            logger.l7dlog(Level.DEBUG, "bcd____null", new Object[] {"b_", "c_", "d_"}, null);
            logger.l7dlog(Level.INFO, "cde____null", new Object[] {"c_", "d_", "e_"}, null);
            logger.l7dlog(Level.WARN, "def____null", new Object[] {"d_", "e_", "f_"}, null);
            logger.l7dlog(Level.ERROR, "efg____null", new Object[] {"e_", "f_", "g_"}, null);
            logger.l7dlog(Level.FATAL, "fgh____null", new Object[] {"f_", "g_", "h_"}, null);
        }
    }
}
