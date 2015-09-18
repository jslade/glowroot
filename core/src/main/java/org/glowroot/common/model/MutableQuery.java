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
package org.glowroot.common.model;

import org.glowroot.collector.spi.model.AggregateOuterClass.Aggregate;
import org.glowroot.markers.UsedByJsonBinding;

@UsedByJsonBinding
public class MutableQuery {

    private final String queryText;
    private double totalNanos;
    private long executionCount;
    private long totalRows;

    public MutableQuery(String queryText) {
        this.queryText = queryText;
    }

    public String getQueryText() {
        return queryText;
    }

    public double getTotalNanos() {
        return totalNanos;
    }

    public long getExecutionCount() {
        return executionCount;
    }

    public long getTotalRows() {
        return totalRows;
    }

    public void addToTotalNanos(double totalNanos) {
        this.totalNanos += totalNanos;
    }

    public void addToExecutionCount(long executionCount) {
        this.executionCount += executionCount;
    }

    public void addToTotalRows(long totalRows) {
        this.totalRows += totalRows;
    }

    public Aggregate.Query toProtobuf() {
        return Aggregate.Query.newBuilder()
                .setText(queryText)
                .setTotalNanos(totalNanos)
                .setExecutionCount(executionCount)
                .setTotalRows(totalRows)
                .build();
    }
}
