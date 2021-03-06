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

import java.lang.reflect.Method;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;

import org.glowroot.plugin.api.Logger;
import org.glowroot.plugin.api.Agent;

public class ResponseInvoker {

    private static final Logger logger = Agent.getLogger(ResponseInvoker.class);

    private final @Nullable Method getContentTypeMethod;

    public ResponseInvoker(Class<?> clazz) {
        Class<?> servletResponseClass = getServletResponseClass(clazz);
        getContentTypeMethod = Invokers.getMethod(servletResponseClass, "getContentType");
    }

    // ServletResponse.getContentType() was introduced in Servlet 2.4 (e.g. since Tomcat 5.5.x)
    public boolean hasGetContentTypeMethod() {
        return getContentTypeMethod != null;
    }

    public String getContentType(Object response) {
        return Invokers.invoke(getContentTypeMethod, response, "");
    }

    @VisibleForTesting
    static @Nullable Class<?> getServletResponseClass(Class<?> clazz) {
        try {
            return Class.forName("javax.servlet.ServletResponse", false, clazz.getClassLoader());
        } catch (ClassNotFoundException e) {
            logger.warn(e.getMessage(), e);
        }
        return null;
    }
}
