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
package org.glowroot.server.ui;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.common.config.GaugeConfig;
import org.glowroot.common.config.GaugeConfig.MBeanAttribute;
import org.glowroot.common.config.ImmutableGaugeConfig;
import org.glowroot.common.config.ImmutableMBeanAttribute;
import org.glowroot.common.util.ObjectMappers;
import org.glowroot.live.LiveJvmService;
import org.glowroot.live.LiveJvmService.MBeanMeta;
import org.glowroot.server.repo.ConfigRepository;
import org.glowroot.server.repo.ConfigRepository.DuplicateMBeanObjectNameException;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.netty.handler.codec.http.HttpResponseStatus.CONFLICT;

@JsonService
class GaugeJsonService {

    private static final Logger logger = LoggerFactory.getLogger(GaugeJsonService.class);
    private static final ObjectMapper mapper = ObjectMappers.create();

    static {
        SimpleModule module = new SimpleModule();
        module.addAbstractTypeMapping(MBeanAttribute.class, ImmutableMBeanAttribute.class);
        mapper.registerModule(module);
    }

    private final ConfigRepository configRepository;
    private final LiveJvmService liveJvmService;

    GaugeJsonService(ConfigRepository configRepository, LiveJvmService liveJvmService) {
        this.configRepository = configRepository;
        this.liveJvmService = liveJvmService;
    }

    @GET("/backend/config/gauges")
    String getGaugeConfigs() throws JsonProcessingException {
        List<GaugeConfigWithWarningMessages> responses = Lists.newArrayList();
        List<GaugeConfig> gaugeConfigs = configRepository.getGaugeConfigs();
        gaugeConfigs = GaugeConfig.orderingByName.immutableSortedCopy(gaugeConfigs);
        for (GaugeConfig gaugeConfig : gaugeConfigs) {
            responses.add(ImmutableGaugeConfigWithWarningMessages.builder()
                    .config(GaugeConfigDto.fromConfig(gaugeConfig))
                    .build());
        }
        return mapper.writeValueAsString(responses);
    }

    @GET("/backend/config/gauges/([0-9a-f]{40})")
    String getGaugeConfig(String version) throws Exception {
        GaugeConfig gaugeConfig = configRepository.getGaugeConfig(version);
        if (gaugeConfig == null) {
            throw new JsonServiceException(HttpResponseStatus.NOT_FOUND);
        }
        return mapper.writeValueAsString(buildResponse(gaugeConfig));
    }

    @GET("/backend/config/matching-mbean-objects")
    String getMatchingMBeanObjects(String queryString) throws Exception {
        MBeanObjectNameRequest request =
                QueryStrings.decode(queryString, MBeanObjectNameRequest.class);
        return mapper.writeValueAsString(liveJvmService
                .getMatchingMBeanObjectNames(request.partialMBeanObjectName(), request.limit()));
    }

    @GET("/backend/config/mbean-attributes")
    String getMBeanAttributes(String queryString) throws Exception {
        MBeanAttributeNamesRequest request =
                QueryStrings.decode(queryString, MBeanAttributeNamesRequest.class);
        boolean duplicateMBean = false;
        for (GaugeConfig gaugeConfig : configRepository.getGaugeConfigs()) {
            if (gaugeConfig.mbeanObjectName().equals(request.mbeanObjectName())
                    && !gaugeConfig.version().equals(request.gaugeVersion())) {
                duplicateMBean = true;
                break;
            }
        }
        MBeanMeta mbeanMeta = liveJvmService.getMBeanMeta(request.mbeanObjectName());
        return mapper.writeValueAsString(ImmutableMBeanAttributeNamesResponse.builder()
                .duplicateMBean(duplicateMBean)
                .mbeanUnmatched(mbeanMeta.unmatched())
                .mbeanUnavailable(mbeanMeta.unavailable())
                .addAllMbeanAttributes(mbeanMeta.attributeNames())
                .build());
    }

    @POST("/backend/config/gauges/add")
    String addGauge(String content) throws Exception {
        GaugeConfigDto gaugeConfigDto = mapper.readValue(content, ImmutableGaugeConfigDto.class);
        GaugeConfig gaugeConfig = gaugeConfigDto.toConfig();
        try {
            configRepository.insertGaugeConfig(gaugeConfig);
        } catch (DuplicateMBeanObjectNameException e) {
            // log exception at debug level
            logger.debug(e.getMessage(), e);
            throw new JsonServiceException(CONFLICT, "mbeanObjectName");
        }
        return mapper.writeValueAsString(buildResponse(gaugeConfig));
    }

    @POST("/backend/config/gauges/update")
    String updateGauge(String content) throws Exception {
        GaugeConfigDto gaugeConfigDto = mapper.readValue(content, ImmutableGaugeConfigDto.class);
        GaugeConfig gaugeConfig = gaugeConfigDto.toConfig();
        String version = gaugeConfigDto.version();
        checkNotNull(version, "Missing required request property: version");
        try {
            configRepository.updateGaugeConfig(gaugeConfig, version);
        } catch (DuplicateMBeanObjectNameException e) {
            // log exception at debug level
            logger.debug(e.getMessage(), e);
            throw new JsonServiceException(CONFLICT, "mbeanObjectName");
        }
        return mapper.writeValueAsString(buildResponse(gaugeConfig));
    }

    @POST("/backend/config/gauges/remove")
    void removeGauge(String content) throws IOException {
        String version = mapper.readValue(content, String.class);
        checkNotNull(version);
        configRepository.deleteGaugeConfig(version);
    }

    private GaugeResponse buildResponse(GaugeConfig gaugeConfig) throws Exception {
        MBeanMeta mbeanMeta = liveJvmService.getMBeanMeta(gaugeConfig.mbeanObjectName());
        return ImmutableGaugeResponse.builder()
                .config(GaugeConfigDto.fromConfig(gaugeConfig))
                .mbeanUnmatched(mbeanMeta.unmatched())
                .mbeanUnavailable(mbeanMeta.unavailable())
                .addAllMbeanAvailableAttributeNames(mbeanMeta.attributeNames())
                .build();
    }

    @Value.Immutable
    interface GaugeConfigWithWarningMessages {
        GaugeConfigDto config();
        ImmutableList<String> warningMessages();
    }

    @Value.Immutable
    interface MBeanObjectNameRequest {
        String partialMBeanObjectName();
        int limit();
    }

    @Value.Immutable
    interface MBeanAttributeNamesRequest {
        String mbeanObjectName();
        @Nullable
        String gaugeVersion();
    }

    @Value.Immutable
    interface MBeanAttributeNamesResponse {
        boolean mbeanUnavailable();
        boolean mbeanUnmatched();
        boolean duplicateMBean();
        ImmutableList<String> mbeanAttributes();
    }

    @Value.Immutable
    abstract static class GaugeResponse {
        abstract GaugeConfigDto config();
        @Value.Default
        boolean mbeanUnavailable() {
            return false;
        }
        @Value.Default
        boolean mbeanUnmatched() {
            return false;
        }
        abstract ImmutableList<String> mbeanAvailableAttributeNames();
    }

    @Value.Immutable
    abstract static class GaugeConfigDto {

        // name is only used in one direction since it is a derived attribute
        abstract @Nullable String display();
        abstract String mbeanObjectName();
        abstract ImmutableList<MBeanAttribute> mbeanAttributes();
        abstract @Nullable String version(); // null for insert operations

        private static GaugeConfigDto fromConfig(GaugeConfig gaugeConfig) {
            return ImmutableGaugeConfigDto.builder()
                    .display(GaugeConfig.display(gaugeConfig.mbeanObjectName()))
                    .mbeanObjectName(gaugeConfig.mbeanObjectName())
                    .addAllMbeanAttributes(gaugeConfig.mbeanAttributes())
                    .version(gaugeConfig.version())
                    .build();
        }

        private GaugeConfig toConfig() {
            return ImmutableGaugeConfig.builder()
                    .mbeanObjectName(mbeanObjectName())
                    .addAllMbeanAttributes(mbeanAttributes())
                    .build();
        }
    }
}
