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
package org.glowroot.local.ui;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Lists;

import org.glowroot.markers.UsedByJsonBinding;

import static org.glowroot.local.ui.ObjectMappers.checkRequiredProperty;
import static org.glowroot.local.ui.ObjectMappers.orEmpty;

@UsedByJsonBinding
@JsonSerialize(using = AggregateProfileNode.Serializer.class)
class AggregateProfileNode {

    // null for synthetic root only
    private final @Nullable String stackTraceElement;
    private final @Nullable String leafThreadState;
    private int sampleCount;
    private List<String> metricNames;
    private final List<AggregateProfileNode> childNodes;
    private boolean ellipsed;

    static AggregateProfileNode createSyntheticRootNode() {
        return new AggregateProfileNode("<multiple root nodes>");
    }

    private AggregateProfileNode(@Nullable String stackTraceElement,
            @Nullable String leafThreadState, int sampleCount, List<String> metricNames,
            List<AggregateProfileNode> childNodes) {
        this.stackTraceElement = stackTraceElement;
        this.leafThreadState = leafThreadState;
        this.sampleCount = sampleCount;
        this.metricNames = metricNames;
        this.childNodes = childNodes;
    }

    private AggregateProfileNode(String stackTraceElement) {
        this.stackTraceElement = stackTraceElement;
        leafThreadState = null;
        metricNames = Lists.newArrayList();
        childNodes = Lists.newArrayList();
    }

    void setMetricNames(List<String> metricNames) {
        this.metricNames = metricNames;
    }

    void incrementSampleCount(int num) {
        sampleCount += num;
    }

    void setEllipsed() {
        ellipsed = true;
    }

    // null for synthetic root only
    public @Nullable String getStackTraceElement() {
        return stackTraceElement;
    }

    public @Nullable String getLeafThreadState() {
        return leafThreadState;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public List<String> getMetricNames() {
        return metricNames;
    }

    public List<AggregateProfileNode> getChildNodes() {
        return childNodes;
    }

    public boolean isEllipsed() {
        return ellipsed;
    }

    @JsonCreator
    static AggregateProfileNode readValue(
            @JsonProperty("stackTraceElement") @Nullable String stackTraceElement,
            @JsonProperty("leafThreadState") @Nullable String leafThreadState,
            @JsonProperty("sampleCount") @Nullable Integer sampleCount,
            @JsonProperty("metricNames") @Nullable List</*@Nullable*/String> uncheckedMetricNames,
            @JsonProperty("childNodes") @Nullable List</*@Nullable*/AggregateProfileNode> uncheckedChildNodes)
            throws JsonMappingException {
        List<String> metricNames = orEmpty(uncheckedMetricNames, "metricNames");
        List<AggregateProfileNode> childNodes = orEmpty(uncheckedChildNodes, "childNodes");
        checkRequiredProperty(sampleCount, "sampleCount");
        return new AggregateProfileNode(stackTraceElement, leafThreadState, sampleCount,
                metricNames, childNodes);
    }

    // optimized serializer, don't output unnecessary false booleans and empty collections
    static class Serializer extends JsonSerializer<AggregateProfileNode> {
        @Override
        public void serialize(AggregateProfileNode value, JsonGenerator gen,
                SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("stackTraceElement", value.getStackTraceElement());
            String leafThreadState = value.getLeafThreadState();
            if (leafThreadState != null) {
                gen.writeStringField("leafThreadState", value.getLeafThreadState());
            }
            gen.writeNumberField("sampleCount", value.getSampleCount());
            List<String> metricNames = value.getMetricNames();
            if (!metricNames.isEmpty()) {
                gen.writeArrayFieldStart("metricNames");
                for (String metricName : metricNames) {
                    gen.writeString(metricName);
                }
                gen.writeEndArray();
            }
            List<AggregateProfileNode> childNodes = value.getChildNodes();
            if (!childNodes.isEmpty()) {
                gen.writeArrayFieldStart("childNodes");
                for (AggregateProfileNode childNode : childNodes) {
                    serialize(childNode, gen, serializers);
                }
                gen.writeEndArray();
            }
            boolean ellipsed = value.isEllipsed();
            if (ellipsed) {
                gen.writeBooleanField("ellipsed", ellipsed);
            }
            gen.writeEndObject();
        }
    }
}
