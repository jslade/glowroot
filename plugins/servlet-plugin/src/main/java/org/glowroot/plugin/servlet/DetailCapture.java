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
package org.glowroot.plugin.servlet;

import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.glowroot.plugin.servlet.ServletAspect.HttpServletRequest;

// shallow copies are necessary because request may not be thread safe, which may affect ability
// to see detail from active traces
//
// shallow copies are also necessary because servlet container may clear out the objects after the
// request is complete (e.g. tomcat does this) in order to reuse them, in which case this detail
// would need to be captured synchronously at end of request anyways (although then it could be
// captured only if trace met threshold for storage...)
class DetailCapture {

    private DetailCapture() {}

    static ImmutableMap<String, Object> captureRequestParameters(
            Map<String, String[]> requestParameters) {
        ImmutableList<Pattern> capturePatterns = ServletPluginProperties.captureRequestParameters();
        ImmutableList<Pattern> maskPatterns = ServletPluginProperties.maskRequestParameters();
        ImmutableMap.Builder<String, Object> map = ImmutableMap.builder();
        for (Entry<String, String[]> entry : requestParameters.entrySet()) {
            String name = entry.getKey();
            if (name == null) {
                // null check just to be safe in case this is a very strange servlet container
                continue;
            }
            // converted to lower case for case-insensitive matching (patterns are lower case)
            String keyLowerCase = name.toLowerCase(Locale.ENGLISH);
            if (!matchesOneOf(keyLowerCase, capturePatterns)) {
                continue;
            }
            if (matchesOneOf(keyLowerCase, maskPatterns)) {
                map.put(name, "****");
                continue;
            }
            String[] values = entry.getValue();
            if (values == null) {
                // just to be safe since ImmutableMap won't accept nulls
                map.put(name, "");
            } else if (values.length == 1) {
                map.put(name, values[0]);
            } else {
                map.put(name, ImmutableList.copyOf(values));
            }
        }
        return map.build();
    }

    static ImmutableMap<String, Object> captureRequestHeaders(HttpServletRequest request) {
        ImmutableList<Pattern> capturePatterns = ServletPluginProperties.captureRequestHeaders();
        if (capturePatterns.isEmpty()) {
            return ImmutableMap.of();
        }
        Map<String, Object> requestHeaders = Maps.newHashMap();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return ImmutableMap.of();
        }
        for (Enumeration<String> e = headerNames; e.hasMoreElements();) {
            String name = e.nextElement();
            if (name == null) {
                // null check just to be safe in case this is a very strange servlet container
                continue;
            }
            // converted to lower case for case-insensitive matching (patterns are lower case)
            String keyLowerCase = name.toLowerCase(Locale.ENGLISH);
            if (!matchesOneOf(keyLowerCase, capturePatterns)) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            if (values != null) {
                captureRequestHeader(name, values, requestHeaders);
            }
        }
        return ImmutableMap.copyOf(requestHeaders);
    }

    static boolean matchesOneOf(String key, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(key).matches()) {
                return true;
            }
        }
        return false;
    }

    private static void captureRequestHeader(String name, Enumeration<String> values,
            Map<String, Object> requestHeaders) {
        if (!values.hasMoreElements()) {
            requestHeaders.put(name, "");
        } else {
            String value = values.nextElement();
            if (!values.hasMoreElements()) {
                requestHeaders.put(name, Strings.nullToEmpty(value));
            } else {
                List<String> list = Lists.newArrayList();
                list.add(Strings.nullToEmpty(value));
                while (values.hasMoreElements()) {
                    list.add(Strings.nullToEmpty(values.nextElement()));
                }
                requestHeaders.put(name, ImmutableList.copyOf(list));
            }
        }
    }
}
