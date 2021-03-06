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
package org.glowroot.agent.util;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.markers.OnlyUsedByTests;

import static java.util.concurrent.TimeUnit.SECONDS;

// this is needed for jboss-modules because calling ManagementFactory.getPlatformMBeanServer()
// before jboss-modules has set up its own logger will trigger the default JUL LogManager to be
// used, and then jboss/wildfly will fail to start
public class LazyPlatformMBeanServer {

    private static final Logger logger = LoggerFactory.getLogger(LazyPlatformMBeanServer.class);

    private final boolean jbossModules;

    @GuardedBy("initListeners")
    private final List<InitListener> initListeners = Lists.newArrayList();

    private volatile @MonotonicNonNull MBeanServer mbeanServer;

    public LazyPlatformMBeanServer(boolean jbossModules) {
        this.jbossModules = jbossModules;
    }

    public ObjectInstance getObjectInstance(ObjectName name) throws Exception {
        ensureInit();
        return mbeanServer.getObjectInstance(name);
    }

    public void invoke(ObjectName name, String operationName, Object[] params, String[] signature)
            throws Exception {
        ensureInit();
        mbeanServer.invoke(name, operationName, params, signature);
    }

    public Set<ObjectName> queryNames(@Nullable ObjectName name, @Nullable QueryExp query)
            throws InterruptedException {
        ensureInit();
        return mbeanServer.queryNames(name, query);
    }

    public MBeanInfo getMBeanInfo(ObjectName name) throws Exception {
        ensureInit();
        return mbeanServer.getMBeanInfo(name);
    }

    public Object getAttribute(ObjectName name, String attribute) throws Exception {
        ensureInit();
        return mbeanServer.getAttribute(name, attribute);
    }

    public void addInitListener(InitListener initListener) {
        synchronized (initListeners) {
            if (mbeanServer == null) {
                initListeners.add(initListener);
            } else {
                try {
                    initListener.postInit(mbeanServer);
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                }
            }
        }
    }

    @OnlyUsedByTests
    public void unregisterMBean(ObjectName name) throws Exception {
        ensureInit();
        mbeanServer.unregisterMBean(name);
    }

    @EnsuresNonNull("mbeanServer")
    private void ensureInit() throws InterruptedException {
        if (needsDelayedInit()) {
            // if running under jboss-modules, wait for it to set up JUL before calling
            // getPlatformMBeanServer()
            waitForJBossModuleInitialization(Stopwatch.createUnstarted());
        }
        synchronized (initListeners) {
            if (mbeanServer == null) {
                mbeanServer = ManagementFactory.getPlatformMBeanServer();
                for (InitListener initListener : initListeners) {
                    try {
                        initListener.postInit(mbeanServer);
                    } catch (Throwable t) {
                        logger.error(t.getMessage(), t);
                    }
                }
            }
        }
    }

    private boolean needsDelayedInit() {
        return mbeanServer == null && jbossModules;
    }

    @VisibleForTesting
    public static void waitForJBossModuleInitialization(Stopwatch stopwatch)
            throws InterruptedException {
        stopwatch.start();
        while (stopwatch.elapsed(SECONDS) < 60) {
            if (System.getProperty("java.util.logging.manager") != null) {
                return;
            }
            Thread.sleep(100);
        }
        // something has gone wrong
        logger.error("this jvm appears to be running jboss-modules, but it did not set up"
                + " java.util.logging.manager");
    }

    public interface InitListener {
        void postInit(MBeanServer mbeanServer) throws Exception;
    }
}
