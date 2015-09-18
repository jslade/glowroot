/*
 * Copyright 2013-2015 the original author or authors.
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
package org.glowroot;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.instrument.Instrumentation;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.FileLock;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import javax.annotation.Nullable;
import javax.management.MBeanServer;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.base.Ticker;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.agent.AgentModule;
import org.glowroot.agent.ViewerAgentModule;
import org.glowroot.agent.util.LazyPlatformMBeanServer;
import org.glowroot.agent.util.SpyingLogbackFilter;
import org.glowroot.collector.spi.Collector;
import org.glowroot.collector.spi.GaugePoint;
import org.glowroot.collector.spi.model.AggregateOuterClass.Aggregate;
import org.glowroot.collector.spi.model.TraceOuterClass.Trace;
import org.glowroot.common.util.Clock;
import org.glowroot.common.util.Tickers;
import org.glowroot.live.LiveAggregateRepository.LiveAggregateRepositoryNop;
import org.glowroot.live.LiveThreadDumpService.LiveThreadDumpServiceNop;
import org.glowroot.live.LiveTraceRepository.LiveTraceRepositoryNop;
import org.glowroot.live.LiveWeavingService.LiveWeavingServiceNop;
import org.glowroot.markers.OnlyUsedByTests;
import org.glowroot.server.repo.ConfigRepository;
import org.glowroot.server.simplerepo.PlatformMBeanServerLifecycle;
import org.glowroot.server.simplerepo.SimpleRepoModule;
import org.glowroot.server.ui.CreateUiModuleBuilder;
import org.glowroot.server.ui.UiModule;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.SECONDS;

@VisibleForTesting
public class GlowrootModule {

    private static final Logger logger = LoggerFactory.getLogger(GlowrootModule.class);

    private final Ticker ticker;
    private final Clock clock;
    // only null in viewer mode
    private final @Nullable ScheduledExecutorService scheduledExecutor;
    private final @Nullable SimpleRepoModule simpleRepoModule;
    private final @Nullable AgentModule agentModule;
    private final @Nullable ViewerAgentModule viewerAgentModule;
    private final File baseDir;

    private final RandomAccessFile baseDirLockFile;
    private final FileLock baseDirFileLock;

    // this is used by tests to check that no warnings/errors are logged during tests
    private final boolean loggingSpy;

    private final String bindAddress;
    private final String version;

    private final boolean h2MemDb;

    private volatile @MonotonicNonNull UiModule uiModule;

    GlowrootModule(File baseDir, Map<String, String> properties,
            @Nullable Instrumentation instrumentation, @Nullable File glowrootJarFile,
            String version, boolean viewerModeEnabled, boolean jbossModules) throws Exception {

        loggingSpy = Boolean.valueOf(properties.get("internal.logging.spy"));
        initStaticLoggerState(baseDir, loggingSpy);

        // lock data dir
        File tmpDir = new File(baseDir, "tmp");
        File lockFile = new File(tmpDir, ".lock");
        try {
            Files.createParentDirs(lockFile);
            Files.touch(lockFile);
        } catch (IOException e) {
            throw new BaseDirLockedException(e);
        }
        baseDirLockFile = new RandomAccessFile(lockFile, "rw");
        FileLock baseDirFileLock = baseDirLockFile.getChannel().tryLock();
        if (baseDirFileLock == null) {
            throw new BaseDirLockedException();
        }
        this.baseDirFileLock = baseDirFileLock;
        lockFile.deleteOnExit();

        ticker = Tickers.getTicker();
        clock = Clock.systemClock();

        // mem db is only used for testing (by glowroot-test-container)
        h2MemDb = Boolean.parseBoolean(properties.get("internal.h2.memdb"));

        if (viewerModeEnabled) {
            viewerAgentModule = new ViewerAgentModule(baseDir, glowrootJarFile);
            scheduledExecutor = null;
            agentModule = null;
            ConfigRepository configRepository = ConfigRepositoryImpl.create(baseDir,
                    viewerAgentModule.getPluginDescriptors(), viewerAgentModule.getConfigService());
            PlatformMBeanServerLifecycle platformMBeanServerLifecycle =
                    new PlatformMBeanServerLifecycleImpl(
                            viewerAgentModule.getLazyPlatformMBeanServer());
            simpleRepoModule = new SimpleRepoModule(baseDir, clock, ticker, configRepository, null,
                    platformMBeanServerLifecycle, h2MemDb, true);
        } else {
            ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true)
                    .setNameFormat("Glowroot-Background-%d").build();
            scheduledExecutor = Executors.newScheduledThreadPool(2, threadFactory);
            // trace module needs to be started as early as possible, so that weaving will be
            // applied to as many classes as possible
            // in particular, it needs to be started before StorageModule which uses shaded H2,
            // which loads java.sql.DriverManager, which loads 3rd party jdbc drivers found via
            // services/java.sql.Driver, and those drivers need to be woven
            CollectorProxy collectorProxy = new CollectorProxy();
            agentModule = new AgentModule(clock, ticker, collectorProxy, instrumentation, baseDir,
                    glowrootJarFile, scheduledExecutor, jbossModules);

            Collector collector = loadCustomCollector(baseDir);

            if (collector != null) {
                simpleRepoModule = null;
            } else {
                ConfigRepository configRepository = ConfigRepositoryImpl.create(baseDir,
                        agentModule.getPluginDescriptors(), agentModule.getConfigService());
                PlatformMBeanServerLifecycle platformMBeanServerLifecycle =
                        new PlatformMBeanServerLifecycleImpl(
                                agentModule.getLazyPlatformMBeanServer());
                simpleRepoModule = new SimpleRepoModule(baseDir, clock, ticker, configRepository,
                        scheduledExecutor, platformMBeanServerLifecycle, h2MemDb, false);
                collector = simpleRepoModule.getCollector();
            }
            // now inject the real collector into the proxy
            collectorProxy.setInstance(collector);
            viewerAgentModule = null;
        }

        bindAddress = getBindAddress(properties);
        this.baseDir = baseDir;
        this.version = version;
    }

    void initEmbeddedServerLazy(final Instrumentation instrumentation) {
        if (simpleRepoModule == null) {
            // using custom collector with no UI
            return;
        }
        // cannot start netty in premain otherwise can crash JVM
        // see https://github.com/netty/netty/issues/3233
        // and https://bugs.openjdk.java.net/browse/JDK-8041920
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    waitForMain(instrumentation);
                    initEmbeddedServer();
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                }
            }
        });
    }

    void initEmbeddedServer() throws Exception {
        if (simpleRepoModule == null) {
            // using custom collector with no UI
            return;
        }
        if (agentModule != null) {
            uiModule = new CreateUiModuleBuilder()
                    .ticker(ticker)
                    .clock(clock)
                    .baseDir(baseDir)
                    .liveJvmService(agentModule.getLiveJvmService())
                    .configRepository(simpleRepoModule.getConfigRepository())
                    .traceRepository(simpleRepoModule.getTraceRepository())
                    .aggregateRepository(simpleRepoModule.getAggregateRepository())
                    .gaugeValueRepository(simpleRepoModule.getGaugeValueRepository())
                    .repoAdmin(simpleRepoModule.getRepoAdmin())
                    .liveTraceRepository(agentModule.getLiveTraceRepository())
                    .liveThreadDumpService(agentModule.getLiveThreadDumpService())
                    .liveAggregateRepository(agentModule.getLiveAggregateRepository())
                    .liveWeavingService(agentModule.getLiveWeavingService())
                    .bindAddress(bindAddress)
                    .version(version)
                    .pluginDescriptors(agentModule.getPluginDescriptors())
                    .build();
        } else {
            checkNotNull(viewerAgentModule);
            uiModule = new CreateUiModuleBuilder()
                    .ticker(ticker)
                    .clock(clock)
                    .baseDir(baseDir)
                    .liveJvmService(viewerAgentModule.getLiveJvmService())
                    .configRepository(simpleRepoModule.getConfigRepository())
                    .traceRepository(simpleRepoModule.getTraceRepository())
                    .aggregateRepository(simpleRepoModule.getAggregateRepository())
                    .gaugeValueRepository(simpleRepoModule.getGaugeValueRepository())
                    .repoAdmin(simpleRepoModule.getRepoAdmin())
                    .liveTraceRepository(new LiveTraceRepositoryNop())
                    .liveThreadDumpService(new LiveThreadDumpServiceNop())
                    .liveAggregateRepository(new LiveAggregateRepositoryNop())
                    .liveWeavingService(new LiveWeavingServiceNop())
                    .bindAddress(bindAddress)
                    .version(version)
                    .pluginDescriptors(viewerAgentModule.getPluginDescriptors())
                    .build();
        }
    }

    private static @Nullable Collector loadCustomCollector(File baseDir)
            throws MalformedURLException {
        File servicesDir = new File(baseDir, "services");
        if (!servicesDir.exists()) {
            return null;
        }
        if (!servicesDir.isDirectory()) {
            return null;
        }
        File[] files = servicesDir.listFiles();
        if (files == null) {
            return null;
        }
        List<URL> urls = Lists.newArrayList();
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".jar")) {
                urls.add(file.toURI().toURL());
            }
        }
        if (urls.isEmpty()) {
            return null;
        }
        URLClassLoader servicesClassLoader = new URLClassLoader(urls.toArray(new URL[0]));
        ServiceLoader<Collector> serviceLoader =
                ServiceLoader.load(Collector.class, servicesClassLoader);
        Iterator<Collector> i = serviceLoader.iterator();
        if (!i.hasNext()) {
            return null;
        }
        return i.next();
    }

    private static void waitForMain(Instrumentation instrumentation) throws InterruptedException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        while (stopwatch.elapsed(SECONDS) < 60) {
            Thread.sleep(100);
            for (Class<?> clazz : instrumentation.getInitiatedClasses(null)) {
                if (clazz.getName().equals("sun.misc.Launcher")) {
                    return;
                }
            }
        }
        // something has gone wrong
        logger.error("sun.misc.Launcher was never loaded");
    }

    private static void initStaticLoggerState(File baseDir, boolean loggingSpy) {
        if (shouldOverrideLogging()) {
            overrideLogging(baseDir);
        }
        if (loggingSpy) {
            SpyingLogbackFilter.init();
        }
    }

    private static boolean shouldOverrideLogging() {
        // don't override glowroot.logback-test.xml
        return isShaded() && ClassLoader.getSystemResource("glowroot.logback-test.xml") == null;
    }

    private static void overrideLogging(File baseDir) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset();
            context.putProperty("glowroot.base.dir", baseDir.getPath());
            File logbackXmlFile = new File(baseDir, "glowroot.logback.xml");
            if (logbackXmlFile.exists()) {
                configurator.doConfigure(logbackXmlFile);
            } else {
                configurator.doConfigure(Resources.getResource("glowroot.logback-override.xml"));
            }
        } catch (JoranException je) {
            // any errors are printed below by StatusPrinter
        }
        StatusPrinter.printInCaseOfErrorsOrWarnings(context);
    }

    private static String getBindAddress(Map<String, String> properties) {
        // empty check to support parameterized script, e.g. -Dglowroot.ui.bind.address=${somevar}
        String bindAddress = properties.get("ui.bind.address");
        if (Strings.isNullOrEmpty(bindAddress)) {
            return "0.0.0.0";
        } else {
            return bindAddress;
        }
    }

    private static boolean isShaded() {
        try {
            Class.forName("org.glowroot.shaded.slf4j.Logger");
            return true;
        } catch (ClassNotFoundException e) {
            // log exception at trace level
            logger.trace(e.getMessage(), e);
            return false;
        }
    }

    @OnlyUsedByTests
    public SimpleRepoModule getSimpleRepoModule() {
        // simpleRepoModule is always used by tests
        checkNotNull(simpleRepoModule);
        return simpleRepoModule;
    }

    @OnlyUsedByTests
    public @Nullable AgentModule getAgentModule() {
        return agentModule;
    }

    @OnlyUsedByTests
    public UiModule getUiModule() throws InterruptedException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        while (stopwatch.elapsed(SECONDS) < 60) {
            if (uiModule != null) {
                return uiModule;
            }
            Thread.sleep(10);
        }
        throw new IllegalStateException("UI Module failed to start");
    }

    @OnlyUsedByTests
    public void reopen() throws Exception {
        initStaticLoggerState(baseDir, loggingSpy);
        // this is not called by viewer
        checkNotNull(agentModule);
        agentModule.reopen();
    }

    @OnlyUsedByTests
    public void close() throws Exception {
        if (uiModule != null) {
            uiModule.close();
        }
        if (agentModule != null) {
            agentModule.close();
        }
        // simpleRepoModule is always used by tests
        checkNotNull(simpleRepoModule);
        simpleRepoModule.close();
        if (scheduledExecutor != null) {
            // close scheduled executor last to prevent exceptions due to above modules attempting
            // to use a shutdown executor
            scheduledExecutor.shutdownNow();
        }
        // finally, close logger
        if (shouldOverrideLogging()) {
            ((LoggerContext) LoggerFactory.getILoggerFactory()).reset();
        }
        baseDirFileLock.release();
        baseDirLockFile.close();
    }

    @VisibleForTesting
    @SuppressWarnings("serial")
    public static class StartupFailedException extends Exception {

        private StartupFailedException() {
            super();
        }

        private StartupFailedException(Throwable cause) {
            super(cause);
        }
    }

    @SuppressWarnings("serial")
    static class BaseDirLockedException extends StartupFailedException {

        private BaseDirLockedException() {
            super();
        }

        private BaseDirLockedException(Throwable cause) {
            super(cause);
        }
    }

    @VisibleForTesting
    static class CollectorProxy implements Collector {

        private volatile @MonotonicNonNull Collector instance;

        @Override
        public void collectTrace(Trace trace) throws Exception {
            if (instance != null) {
                instance.collectTrace(trace);
            }
        }

        @Override
        public void collectAggregates(Map<String, ? extends Aggregate> overallAggregates,
                Map<String, ? extends Map<String, ? extends Aggregate>> transactionAggregates,
                long captureTime) throws Exception {
            if (instance != null) {
                instance.collectAggregates(overallAggregates, transactionAggregates, captureTime);
            }
        }

        @Override
        public void collectGaugePoints(Collection<? extends GaugePoint> gaugeValues)
                throws Exception {
            if (instance != null) {
                instance.collectGaugePoints(gaugeValues);
            }
        }

        @VisibleForTesting
        void setInstance(Collector instance) {
            this.instance = instance;
        }
    }

    private static class PlatformMBeanServerLifecycleImpl implements PlatformMBeanServerLifecycle {

        private final LazyPlatformMBeanServer lazyPlatformMBeanServer;

        private PlatformMBeanServerLifecycleImpl(LazyPlatformMBeanServer lazyPlatformMBeanServer) {
            this.lazyPlatformMBeanServer = lazyPlatformMBeanServer;
        }

        @Override
        public void addInitListener(final InitListener listener) {
            lazyPlatformMBeanServer.addInitListener(
                    new org.glowroot.agent.util.LazyPlatformMBeanServer.InitListener() {
                        @Override
                        public void postInit(MBeanServer mbeanServer) throws Exception {
                            listener.doWithPlatformMBeanServer(mbeanServer);
                        }
                    });
        }
    }
}
