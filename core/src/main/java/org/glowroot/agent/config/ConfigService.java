/*
 * Copyright 2011-2015 the original author or authors.
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
package org.glowroot.agent.config;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import org.immutables.builder.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.agent.util.JavaVersion;
import org.glowroot.common.config.AdvancedConfig;
import org.glowroot.common.config.GaugeConfig;
import org.glowroot.common.config.GaugeConfig.MBeanAttribute;
import org.glowroot.common.config.ImmutableAdvancedConfig;
import org.glowroot.common.config.ImmutableGaugeConfig;
import org.glowroot.common.config.ImmutableInstrumentationConfig;
import org.glowroot.common.config.ImmutableMBeanAttribute;
import org.glowroot.common.config.ImmutablePluginConfig;
import org.glowroot.common.config.ImmutableTransactionConfig;
import org.glowroot.common.config.ImmutableUserRecordingConfig;
import org.glowroot.common.config.InstrumentationConfig;
import org.glowroot.common.config.PluginConfig;
import org.glowroot.common.config.PluginDescriptor;
import org.glowroot.common.config.PropertyDescriptor;
import org.glowroot.common.config.PropertyValue;
import org.glowroot.common.config.TransactionConfig;
import org.glowroot.common.config.UserRecordingConfig;
import org.glowroot.common.util.ObjectMappers;
import org.glowroot.markers.OnlyUsedByTests;
import org.glowroot.plugin.api.config.ConfigListener;

import static com.google.common.base.Preconditions.checkNotNull;

public class ConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);
    private static final ObjectMapper mapper = ObjectMappers.create();

    // 5 seconds
    private static final long GAUGE_COLLECTION_INTERVAL_MILLIS =
            Long.getLong("glowroot.internal.gaugeCollectionIntervalMillis", 5000);

    static {
        SimpleModule module = new SimpleModule();
        module.addAbstractTypeMapping(TransactionConfig.class, ImmutableTransactionConfig.class);
        module.addAbstractTypeMapping(UserRecordingConfig.class,
                ImmutableUserRecordingConfig.class);
        module.addAbstractTypeMapping(AdvancedConfig.class, ImmutableAdvancedConfig.class);
        module.addAbstractTypeMapping(PluginConfig.class, ImmutablePluginConfig.class);
        module.addAbstractTypeMapping(InstrumentationConfig.class,
                ImmutableInstrumentationConfig.class);
        module.addAbstractTypeMapping(GaugeConfig.class, ImmutableGaugeConfig.class);
        module.addAbstractTypeMapping(MBeanAttribute.class, ImmutableMBeanAttribute.class);
        mapper.registerModule(module);
    }

    private final ConfigFile configFile;

    private final ImmutableList<PluginDescriptor> pluginDescriptors;

    private final Set<ConfigListener> configListeners = Sets.newCopyOnWriteArraySet();
    private final Multimap<String, ConfigListener> pluginConfigListeners =
            Multimaps.synchronizedMultimap(ArrayListMultimap.<String, ConfigListener>create());

    private volatile TransactionConfig transactionConfig;
    private volatile UserRecordingConfig userRecordingConfig;
    private volatile AdvancedConfig advancedConfig;
    private volatile ImmutableList<PluginConfig> pluginConfigs;
    private volatile ImmutableList<GaugeConfig> gaugeConfigs;
    private volatile ImmutableList<InstrumentationConfig> instrumentationConfigs;

    // memory barrier is used to ensure memory visibility of config values
    private volatile boolean memoryBarrier;

    @Builder.Factory
    public static ConfigService create(File baseDir, List<PluginDescriptor> pluginDescriptors) {
        ConfigService configService = new ConfigService(baseDir, pluginDescriptors);
        // it's nice to update config.json on startup if it is missing some/all config
        // properties so that the file contents can be reviewed/updated/copied if desired
        try {
            configService.writeAll();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return configService;
    }

    private ConfigService(File baseDir, List<PluginDescriptor> pluginDescriptors) {
        configFile = new ConfigFile(new File(baseDir, "config.json"));
        this.pluginDescriptors = ImmutableList.copyOf(pluginDescriptors);
        TransactionConfig transactionConfig =
                configFile.getNode("transaction", TransactionConfig.class, mapper);
        if (transactionConfig == null) {
            this.transactionConfig = ImmutableTransactionConfig.builder().build();
        } else {
            this.transactionConfig = transactionConfig;
        }
        UserRecordingConfig userRecordingConfig =
                configFile.getNode("userRecording", UserRecordingConfig.class, mapper);
        if (userRecordingConfig == null) {
            this.userRecordingConfig = ImmutableUserRecordingConfig.builder().build();
        } else {
            this.userRecordingConfig = userRecordingConfig;
        }
        AdvancedConfig advancedConfig =
                configFile.getNode("advanced", AdvancedConfig.class, mapper);
        if (advancedConfig == null) {
            this.advancedConfig = ImmutableAdvancedConfig.builder().build();
        } else {
            this.advancedConfig = advancedConfig;
        }
        List<PluginConfig> pluginConfigs =
                configFile.getNode("plugins", new TypeReference<List<PluginConfig>>() {}, mapper);
        this.pluginConfigs = fixPluginConfigs(pluginConfigs, pluginDescriptors);

        List<GaugeConfig> gaugeConfigs =
                configFile.getNode("gauges", new TypeReference<List<GaugeConfig>>() {}, mapper);
        if (gaugeConfigs == null) {
            this.gaugeConfigs = getDefaultGaugeConfigs();
        } else {
            this.gaugeConfigs = ImmutableList.copyOf(gaugeConfigs);
        }
        List<InstrumentationConfig> instrumentationConfigs =
                configFile.getNode("instrumentation",
                        new TypeReference<List<InstrumentationConfig>>() {}, mapper);
        if (instrumentationConfigs == null) {
            this.instrumentationConfigs = ImmutableList.of();
        } else {
            this.instrumentationConfigs = ImmutableList.copyOf(instrumentationConfigs);
        }

        for (InstrumentationConfig instrumentationConfig : this.instrumentationConfigs) {
            ImmutableList<String> errors = instrumentationConfig.validationErrors();
            if (!errors.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Invalid instrumentation config: ");
                sb.append(Joiner.on(", ").join(errors));
                sb.append(" ");
                try {
                    sb.append(ObjectMappers.create().writeValueAsString(instrumentationConfig));
                } catch (JsonProcessingException e) {
                    logger.error(e.getMessage(), e);
                }
                logger.error(sb.toString());
            }
        }
    }

    public TransactionConfig getTransactionConfig() {
        return transactionConfig;
    }

    public UserRecordingConfig getUserRecordingConfig() {
        return userRecordingConfig;
    }

    public AdvancedConfig getAdvancedConfig() {
        return advancedConfig;
    }

    public ImmutableList<PluginConfig> getPluginConfigs() {
        return pluginConfigs;
    }

    public @Nullable PluginConfig getPluginConfig(String pluginId) {
        for (PluginConfig pluginConfig : pluginConfigs) {
            if (pluginId.equals(pluginConfig.id())) {
                return pluginConfig;
            }
        }
        return null;
    }

    public List<GaugeConfig> getGaugeConfigs() {
        return gaugeConfigs;
    }

    public List<InstrumentationConfig> getInstrumentationConfigs() {
        return instrumentationConfigs;
    }

    public long getGaugeCollectionIntervalMillis() {
        return GAUGE_COLLECTION_INTERVAL_MILLIS;
    }

    public void addConfigListener(ConfigListener listener) {
        configListeners.add(listener);
        listener.onChange();
    }

    public void addPluginConfigListener(String pluginId, ConfigListener listener) {
        pluginConfigListeners.put(pluginId, listener);
    }

    public void updateTransactionConfig(TransactionConfig updatedConfig) throws IOException {
        configFile.write("transaction", updatedConfig, mapper);
        transactionConfig = updatedConfig;
    }

    public void updateUserRecordingConfig(UserRecordingConfig updatedConfig) throws IOException {
        configFile.write("userRecording", updatedConfig, mapper);
        userRecordingConfig = updatedConfig;
    }

    public void updateAdvancedConfig(AdvancedConfig updatedConfig) throws IOException {
        configFile.write("advanced", updatedConfig, mapper);
        advancedConfig = updatedConfig;
    }

    public void updatePluginConfigs(List<PluginConfig> updatedConfigs) throws IOException {
        configFile.write("plugins", updatedConfigs, mapper);
        pluginConfigs = ImmutableList.copyOf(updatedConfigs);
    }

    public void updateInstrumentationConfigs(List<InstrumentationConfig> updatedConfigs)
            throws IOException {
        configFile.write("instrumentation", updatedConfigs, mapper);
        instrumentationConfigs = ImmutableList.copyOf(updatedConfigs);
    }

    public void updateGaugeConfigs(List<GaugeConfig> updatedConfigs) throws IOException {
        configFile.write("gauges", updatedConfigs, mapper);
        gaugeConfigs = ImmutableList.copyOf(updatedConfigs);
    }

    public <T extends /*@NonNull*/Object> /*@Nullable*/T getOtherConfig(String key, Class<T> clazz,
            ObjectMapper mapper) {
        return configFile.getNode(key, clazz, mapper);
    }

    public <T extends /*@NonNull*/Object> /*@Nullable*/T getOtherConfig(String key,
            TypeReference<T> typeReference, ObjectMapper mapper) {
        return configFile.getNode(key, typeReference, mapper);
    }

    public void updateOtherConfig(String key, Object config, ObjectMapper mapper)
            throws IOException {
        configFile.write(key, config, mapper);
    }

    public void updateOtherConfigs(Map<String, Object> configs, ObjectMapper mapper)
            throws IOException {
        configFile.write(configs, mapper);
    }

    public boolean readMemoryBarrier() {
        return memoryBarrier;
    }

    public void writeMemoryBarrier() {
        memoryBarrier = true;
    }

    // the updated config is not passed to the listeners to avoid the race condition of multiple
    // config updates being sent out of order, instead listeners must call get*Config() which will
    // never return the updates out of order (at worst it may return the most recent update twice
    // which is ok)
    public void notifyConfigListeners() {
        for (ConfigListener configListener : configListeners) {
            configListener.onChange();
        }
    }

    public void notifyPluginConfigListeners(String pluginId) {
        // make copy first to avoid possible ConcurrentModificationException while iterating
        Collection<ConfigListener> listeners =
                ImmutableList.copyOf(pluginConfigListeners.get(pluginId));
        for (ConfigListener listener : listeners) {
            listener.onChange();
        }
    }

    @OnlyUsedByTests
    public void resetAllConfig() throws IOException {
        transactionConfig = ImmutableTransactionConfig.builder().build();
        userRecordingConfig = ImmutableUserRecordingConfig.builder().build();
        advancedConfig = ImmutableAdvancedConfig.builder().build();
        pluginConfigs = fixPluginConfigs(ImmutableList.<PluginConfig>of(), pluginDescriptors);
        gaugeConfigs = getDefaultGaugeConfigs();
        instrumentationConfigs = ImmutableList.of();
        writeAll();
        notifyConfigListeners();
        notifyAllPluginConfigListeners();
    }

    private void writeAll() throws IOException {
        // linked hash map to preserve ordering when writing to config file
        Map<String, Object> configs = Maps.newLinkedHashMap();
        configs.put("transaction", transactionConfig);
        configs.put("userRecording", userRecordingConfig);
        configs.put("advanced", advancedConfig);
        configs.put("plugins", this.pluginConfigs);
        configs.put("gauges", this.gaugeConfigs);
        configs.put("instrumentation", this.instrumentationConfigs);
        configFile.write(configs, mapper);
    }

    private void notifyAllPluginConfigListeners() {
        // make copy first to avoid possible ConcurrentModificationException while iterating
        Collection<ConfigListener> listeners = ImmutableList.copyOf(pluginConfigListeners.values());
        for (ConfigListener configListener : listeners) {
            configListener.onChange();
        }
    }

    private static ImmutableList<GaugeConfig> getDefaultGaugeConfigs() {
        List<GaugeConfig> defaultGaugeConfigs = Lists.newArrayList();
        defaultGaugeConfigs.add(ImmutableGaugeConfig.builder()
                .mbeanObjectName("java.lang:type=Memory")
                .addMbeanAttributes(ImmutableMBeanAttribute.of("HeapMemoryUsage/used", false))
                .build());
        defaultGaugeConfigs.add(ImmutableGaugeConfig.builder()
                .mbeanObjectName("java.lang:type=GarbageCollector,name=*")
                .addMbeanAttributes(ImmutableMBeanAttribute.of("CollectionCount", true))
                .addMbeanAttributes(ImmutableMBeanAttribute.of("CollectionTime", true))
                .build());
        defaultGaugeConfigs.add(ImmutableGaugeConfig.builder()
                .mbeanObjectName("java.lang:type=MemoryPool,name=*")
                .addMbeanAttributes(ImmutableMBeanAttribute.of("Usage/used", false))
                .build());
        ImmutableGaugeConfig.Builder operatingSystemMBean = ImmutableGaugeConfig.builder()
                .mbeanObjectName("java.lang:type=OperatingSystem")
                .addMbeanAttributes(ImmutableMBeanAttribute.of("FreePhysicalMemorySize", false));
        if (!JavaVersion.isJava6()) {
            // these are only available since 1.7
            operatingSystemMBean
                    .addMbeanAttributes(ImmutableMBeanAttribute.of("ProcessCpuLoad", false));
            operatingSystemMBean
                    .addMbeanAttributes(ImmutableMBeanAttribute.of("SystemCpuLoad", false));
        }
        defaultGaugeConfigs.add(operatingSystemMBean.build());
        return ImmutableList.copyOf(defaultGaugeConfigs);
    }

    private static ImmutableList<PluginConfig> fixPluginConfigs(
            @Nullable List<PluginConfig> filePluginConfigs,
            List<PluginDescriptor> pluginDescriptors) {

        // sorted by id for writing to config file
        List<PluginDescriptor> sortedPluginDescriptors =
                new PluginDescriptorOrdering().immutableSortedCopy(pluginDescriptors);

        Map<String, PluginConfig> filePluginConfigMap = Maps.newHashMap();
        if (filePluginConfigs != null) {
            for (PluginConfig pluginConfig : filePluginConfigs) {
                filePluginConfigMap.put(pluginConfig.id(), pluginConfig);
            }
        }

        List<PluginConfig> accruatePluginConfigs = Lists.newArrayList();
        for (PluginDescriptor pluginDescriptor : sortedPluginDescriptors) {
            PluginConfig filePluginConfig = filePluginConfigMap.get(pluginDescriptor.id());
            ImmutablePluginConfig.Builder builder = ImmutablePluginConfig.builder()
                    .id(pluginDescriptor.id());
            if (filePluginConfig == null) {
                builder.enabled(true);
            } else {
                builder.enabled(filePluginConfig.enabled());
            }
            for (PropertyDescriptor propertyDescriptor : pluginDescriptor.properties()) {
                builder.putProperties(propertyDescriptor.name(),
                        getPropertyValue(filePluginConfig, propertyDescriptor));
            }
            accruatePluginConfigs.add(builder.build());
        }
        return ImmutableList.copyOf(accruatePluginConfigs);
    }

    private static PropertyValue getPropertyValue(@Nullable PluginConfig pluginConfig,
            PropertyDescriptor propertyDescriptor) {
        if (pluginConfig == null) {
            return propertyDescriptor.getValidatedNonNullDefaultValue();
        }
        PropertyValue propertyValue = pluginConfig
                .getValidatedPropertyValue(propertyDescriptor.name(), propertyDescriptor.type());
        if (propertyValue == null) {
            return propertyDescriptor.getValidatedNonNullDefaultValue();
        }
        return propertyValue;
    }

    private static final class PluginDescriptorOrdering extends Ordering<PluginDescriptor> {
        @Override
        public int compare(@Nullable PluginDescriptor left, @Nullable PluginDescriptor right) {
            checkNotNull(left);
            checkNotNull(right);
            return left.id().compareToIgnoreCase(right.id());
        }
    }
}
