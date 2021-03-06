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

public class AlertConfig {

    private @Nullable String transactionType;
    private double percentile;
    private int timePeriodMinutes;
    private int thresholdMillis;
    private int minTransactionCount;
    private List<String> emailAddresses = Lists.newArrayList();

    // null for new alert config records that haven't been sent to server yet
    private @Nullable final String version;

    // used to create new alert config records that haven't been sent to server yet
    public AlertConfig() {
        version = null;
    }

    private AlertConfig(String version) {
        this.version = version;
    }

    public @Nullable String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public double getPercentile() {
        return percentile;
    }

    public void setPercentile(double percentile) {
        this.percentile = percentile;
    }

    public int getTimePeriodMinutes() {
        return timePeriodMinutes;
    }

    public void setTimePeriodMinutes(int timePeriodMinutes) {
        this.timePeriodMinutes = timePeriodMinutes;
    }

    public int getThresholdMillis() {
        return thresholdMillis;
    }

    public void setThresholdMillis(int thresholdMillis) {
        this.thresholdMillis = thresholdMillis;
    }

    public int getMinTransactionCount() {
        return minTransactionCount;
    }

    public void setMinTransactionCount(int minTransactionCount) {
        this.minTransactionCount = minTransactionCount;
    }

    public List<String> getEmailAddresses() {
        return emailAddresses;
    }

    public void setEmailAddresses(List<String> emailAddresses) {
        this.emailAddresses = emailAddresses;
    }

    public @Nullable String getVersion() {
        return version;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof AlertConfig) {
            AlertConfig that = (AlertConfig) obj;
            // intentionally leaving off version since it represents the prior version hash when
            // sending to the server, and represents the current version hash when receiving from
            // the server
            return Objects.equal(transactionType, that.transactionType)
                    && Objects.equal(percentile, that.percentile)
                    && Objects.equal(timePeriodMinutes, that.timePeriodMinutes)
                    && Objects.equal(thresholdMillis, that.thresholdMillis)
                    && Objects.equal(minTransactionCount, that.minTransactionCount)
                    && Objects.equal(emailAddresses, that.emailAddresses);
        }
        return false;
    }

    @Override
    public int hashCode() {
        // intentionally leaving off version since it represents the prior version hash when
        // sending to the server, and represents the current version hash when receiving from the
        // server
        return Objects.hashCode(transactionType, percentile, timePeriodMinutes, thresholdMillis,
                minTransactionCount, emailAddresses);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("transactionType", transactionType)
                .add("percentile", percentile)
                .add("timePeriodMinutes", timePeriodMinutes)
                .add("thresholdMillis", thresholdMillis)
                .add("minTransactionCount", minTransactionCount)
                .add("emailAddresses", emailAddresses)
                .add("version", version)
                .toString();
    }

    @JsonCreator
    static AlertConfig readValue(
            @JsonProperty("transactionType") @Nullable String transactionType,
            @JsonProperty("percentile") @Nullable Double percentile,
            @JsonProperty("timePeriodMinutes") @Nullable Integer timePeriodMinutes,
            @JsonProperty("thresholdMillis") @Nullable Integer thresholdMillis,
            @JsonProperty("minTransactionCount") @Nullable Integer minTransactionCount,
            @JsonProperty("emailAddresses") @Nullable List<String> emailAddresses,
            @JsonProperty("version") @Nullable String version) throws JsonMappingException {
        checkRequiredProperty(transactionType, "transactionType");
        checkRequiredProperty(percentile, "percentile");
        checkRequiredProperty(timePeriodMinutes, "timePeriodMinutes");
        checkRequiredProperty(thresholdMillis, "thresholdMillis");
        checkRequiredProperty(minTransactionCount, "minTransactionCount");
        checkRequiredProperty(emailAddresses, "emailAddresses");
        checkRequiredProperty(version, "version");
        AlertConfig config = new AlertConfig(version);
        config.setTransactionType(transactionType);
        config.setPercentile(percentile);
        config.setTimePeriodMinutes(timePeriodMinutes);
        config.setThresholdMillis(thresholdMillis);
        config.setMinTransactionCount(minTransactionCount);
        config.setEmailAddresses(emailAddresses);
        return config;
    }
}
