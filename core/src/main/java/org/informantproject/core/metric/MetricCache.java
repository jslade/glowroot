/**
 * Copyright 2012 the original author or authors.
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
package org.informantproject.core.metric;

import org.informantproject.api.Metric;
import org.informantproject.api.weaving.Pointcut;
import org.informantproject.core.trace.MetricImpl;
import org.informantproject.core.trace.TraceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@Singleton
public class MetricCache {

    private static final Logger logger = LoggerFactory.getLogger(MetricCache.class);

    private final TraceRegistry traceRegistry;
    private final Ticker ticker;

    private final LoadingCache<String, MetricImpl> metrics = CacheBuilder.newBuilder().build(
            new CacheLoader<String, MetricImpl>() {
                @Override
                public MetricImpl load(String name) {
                    return new MetricImpl(name, traceRegistry, ticker);
                }
            });

    @Inject
    public MetricCache(TraceRegistry traceRegistry, Ticker ticker) {
        this.traceRegistry = traceRegistry;
        this.ticker = ticker;
    }

    public Metric getMetric(Class<?> adviceClass) {
        Pointcut pointcut = adviceClass.getAnnotation(Pointcut.class);
        if (pointcut == null) {
            logger.error("advice class '{}' has no @Pointcut", adviceClass.getName());
            return metrics.getUnchecked("unknown");
        } else if (pointcut.metricName().equals("")) {
            logger.error("@Pointcut on advice class '{}' has no metricName() attribute",
                    adviceClass.getName());
            return metrics.getUnchecked("unknown");
        } else {
            return metrics.getUnchecked(pointcut.metricName());
        }
    }

    public Metric getMetric(String name) {
        return metrics.getUnchecked(name);
    }
}