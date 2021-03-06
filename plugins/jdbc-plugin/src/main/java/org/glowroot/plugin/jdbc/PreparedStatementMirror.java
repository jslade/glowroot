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
package org.glowroot.plugin.jdbc;

import java.util.Collection;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Queues;
import com.google.common.hash.HashCode;

import org.glowroot.plugin.jdbc.message.BindParameterList;

// used to capture and mirror the state of prepared statements since the underlying
// PreparedStatement values cannot be inspected after they have been set
class PreparedStatementMirror extends StatementMirror {

    private static final int PARAMETERS_INITIAL_CAPACITY = 20;

    private final String sql;
    // ok for this field to be non-volatile since it is only temporary storage for a single thread
    // while that thread is setting parameter values into the prepared statement and executing it
    private BindParameterList parameters;
    private boolean parametersCopied;
    // ok for this field to be non-volatile since it is only temporary storage for a single thread
    // while that thread is setting parameter values into the prepared statement and executing it
    private @Nullable Collection<BindParameterList> batchedParameters;

    public PreparedStatementMirror(String sql) {
        this.sql = sql;
        // TODO delay creation to optimize case when bind parameter capture is disabled
        parameters = new BindParameterList(PARAMETERS_INITIAL_CAPACITY);
    }

    public void addBatch() {
        // synchronization isn't an issue here as this method is called only by the monitored thread
        if (batchedParameters == null) {
            batchedParameters = Queues.newConcurrentLinkedQueue();
        }
        batchedParameters.add(parameters);
        parametersCopied = true;
    }

    public Collection<BindParameterList> getBatchedParameters() {
        if (batchedParameters == null) {
            return ImmutableList.of();
        } else {
            return batchedParameters;
        }
    }

    public @Nullable BindParameterList getParametersCopy() {
        parametersCopied = true;
        return parameters;
    }

    public String getSql() {
        return sql;
    }

    int getBatchSize() {
        return batchedParameters == null ? 0 : batchedParameters.size();
    }

    // remember parameterIndex starts at 1 not 0
    public void setParameterValue(int parameterIndex, @Nullable Object object) {
        if (parametersCopied) {
            // separate method for less common path to not impact inlining budget of fast(er) path
            copyParameters();
        }
        parameters.set(parameterIndex - 1, object);
    }

    private void copyParameters() {
        parameters = BindParameterList.copyOf(parameters);
        parametersCopied = false;
    }

    public void clearParameters() {
        if (parametersCopied) {
            parameters = new BindParameterList(parameters.size());
            parametersCopied = false;
        } else {
            parameters.clear();
        }
    }

    @Override
    public void clearBatch() {
        if (parametersCopied) {
            parameters = new BindParameterList(parameters.size());
            parametersCopied = false;
        } else {
            parameters.clear();
        }
        batchedParameters = null;
    }

    static class ByteArrayParameterValue {
        private final int length;
        private final byte /*@Nullable*/[] bytes;
        public ByteArrayParameterValue(byte[] bytes, boolean displayAsHex) {
            length = bytes.length;
            // only retain bytes if needed for displaying as hex
            this.bytes = displayAsHex ? bytes : null;
        }
        @Override
        public String toString() {
            if (bytes != null) {
                return "0x" + HashCode.fromBytes(bytes).toString();
            } else {
                return "{" + length + " bytes}";
            }
        }
    }

    static class StreamingParameterValue {
        private final Class<?> clazz;
        public StreamingParameterValue(Class<?> clazz) {
            this.clazz = clazz;
        }
        @Override
        public String toString() {
            return "{stream:" + clazz.getSimpleName() + "}";
        }
    }
}
