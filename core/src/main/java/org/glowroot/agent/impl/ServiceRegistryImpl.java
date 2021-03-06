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
package org.glowroot.agent.impl;

import javax.annotation.Nullable;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import org.glowroot.api.internal.GlowrootService;
import org.glowroot.markers.OnlyUsedByTests;
import org.glowroot.markers.UsedByReflection;
import org.glowroot.plugin.api.config.ConfigService;
import org.glowroot.plugin.api.internal.ServiceRegistry;
import org.glowroot.plugin.api.transaction.TransactionService;

@UsedByReflection
public class ServiceRegistryImpl implements ServiceRegistry {

    private static volatile @MonotonicNonNull ServiceRegistryImpl INSTANCE;

    private final GlowrootService glowrootService;
    private final TransactionService transactionService;

    private final LoadingCache<String, ConfigService> configServices;

    ServiceRegistryImpl(GlowrootService glowrootService, TransactionService transactionService,
            final ConfigServiceFactory configServiceFactory) {
        this.glowrootService = glowrootService;
        this.transactionService = transactionService;
        configServices = CacheBuilder.newBuilder().build(new CacheLoader<String, ConfigService>() {
            @Override
            public ConfigService load(String pluginId) {
                return configServiceFactory.create(pluginId);
            }
        });
    }

    @Override
    public TransactionService getTransactionService() {
        return transactionService;
    }

    @Override
    public ConfigService getConfigService(String pluginId) {
        return configServices.getUnchecked(pluginId);
    }

    // called via reflection from org.glowroot.plugin.api.Agent
    // also called via reflection from generated pointcut config advice
    @UsedByReflection
    public static @Nullable ServiceRegistry getInstance() {
        return INSTANCE;
    }

    // called via reflection from org.glowroot.api.Glowroot
    // also called via reflection from generated pointcut config advice
    @UsedByReflection
    public static @Nullable GlowrootService getGlowrootService() {
        return INSTANCE == null ? null : INSTANCE.glowrootService;
    }

    public static ServiceRegistryImpl init(GlowrootService glowrootService,
            TransactionService transactionService, ConfigServiceFactory configServiceFactory)
                    throws Exception {
        INSTANCE =
                new ServiceRegistryImpl(glowrootService, transactionService, configServiceFactory);
        return INSTANCE;
    }

    @OnlyUsedByTests
    public static void reopen(ServiceRegistryImpl pluginServiceRegistry) throws Exception {
        INSTANCE = pluginServiceRegistry;
    }

    public interface ConfigServiceFactory {
        ConfigService create(String pluginId);
    }
}
