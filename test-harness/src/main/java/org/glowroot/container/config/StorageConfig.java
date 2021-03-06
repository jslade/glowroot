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
package org.glowroot.container.config;

import java.util.List;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import static org.glowroot.container.common.ObjectMappers.checkRequiredProperty;

public class StorageConfig {

    private List<Integer> rollupExpirationHours = Lists.newArrayList();
    private int traceExpirationHours;
    private List<Integer> rollupCappedDatabaseSizesMb = Lists.newArrayList();
    private int traceCappedDatabaseSizeMb;

    private final String version;

    private StorageConfig(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public List<Integer> getRollupExpirationHours() {
        return rollupExpirationHours;
    }

    public void setRollupExpirationHours(List<Integer> rollupExpirationHours) {
        this.rollupExpirationHours = rollupExpirationHours;
    }

    public int getTraceExpirationHours() {
        return traceExpirationHours;
    }

    public void setTraceExpirationHours(int traceExpirationHours) {
        this.traceExpirationHours = traceExpirationHours;
    }

    public List<Integer> getRollupCappedDatabaseSizesMb() {
        return rollupCappedDatabaseSizesMb;
    }

    public void setRollupCappedDatabaseSizesMb(List<Integer> rollupCappedDatabaseSizesMb) {
        this.rollupCappedDatabaseSizesMb = rollupCappedDatabaseSizesMb;
    }

    public int getTraceCappedDatabaseSizeMb() {
        return traceCappedDatabaseSizeMb;
    }

    public void setTraceCappedDatabaseSizeMb(int traceCappedDatabaseSizeMb) {
        this.traceCappedDatabaseSizeMb = traceCappedDatabaseSizeMb;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof StorageConfig) {
            StorageConfig that = (StorageConfig) obj;
            // intentionally leaving off version since it represents the prior version hash when
            // sending to the server, and represents the current version hash when receiving from
            // the server
            return Objects.equal(rollupExpirationHours, that.rollupExpirationHours)
                    && Objects.equal(traceExpirationHours, that.traceExpirationHours)
                    && Objects.equal(rollupCappedDatabaseSizesMb, that.rollupCappedDatabaseSizesMb)
                    && Objects.equal(traceCappedDatabaseSizeMb, that.traceCappedDatabaseSizeMb);
        }
        return false;
    }

    @Override
    public int hashCode() {
        // intentionally leaving off version since it represents the prior version hash when
        // sending to the server, and represents the current version hash when receiving from the
        // server
        return Objects.hashCode(rollupExpirationHours, traceExpirationHours,
                rollupCappedDatabaseSizesMb, traceCappedDatabaseSizeMb);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("rollupExpirationHours", rollupExpirationHours)
                .add("traceExpirationHours", traceExpirationHours)
                .add("rollupCappedDatabaseSizesMb", rollupCappedDatabaseSizesMb)
                .add("traceCappedDatabaseSizeMb", traceCappedDatabaseSizeMb)
                .add("version", version)
                .toString();
    }

    @JsonCreator
    static StorageConfig readValue(
            @JsonProperty("rollupExpirationHours") @Nullable List<Integer> rollupExpirationHours,
            @JsonProperty("traceExpirationHours") @Nullable Integer traceExpirationHours,
            @JsonProperty("rollupCappedDatabaseSizesMb") @Nullable List<Integer> rollupCappedDatabaseSizesMb,
            @JsonProperty("traceCappedDatabaseSizeMb") @Nullable Integer traceCappedDatabaseSizeMb,
            @JsonProperty("version") @Nullable String version) throws JsonMappingException {
        checkRequiredProperty(rollupExpirationHours, "rollupExpirationHours");
        checkRequiredProperty(traceExpirationHours, "traceExpirationHours");
        checkRequiredProperty(rollupCappedDatabaseSizesMb, "rollupCappedDatabaseSizesMb");
        checkRequiredProperty(traceCappedDatabaseSizeMb, "traceCappedDatabaseSizeMb");
        checkRequiredProperty(version, "version");
        StorageConfig config = new StorageConfig(version);
        config.setRollupExpirationHours(rollupExpirationHours);
        config.setTraceExpirationHours(traceExpirationHours);
        config.setRollupCappedDatabaseSizesMb(rollupCappedDatabaseSizesMb);
        config.setTraceCappedDatabaseSizeMb(traceCappedDatabaseSizeMb);
        return config;
    }
}
