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
package org.glowroot.plugin.servlet;

import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;

import org.glowroot.plugin.api.Agent;
import org.glowroot.plugin.api.config.ConfigService;
import org.glowroot.plugin.api.transaction.TransactionService;
import org.glowroot.plugin.api.weaving.BindParameter;
import org.glowroot.plugin.api.weaving.BindReceiver;
import org.glowroot.plugin.api.weaving.IsEnabled;
import org.glowroot.plugin.api.weaving.OnAfter;
import org.glowroot.plugin.api.weaving.Pointcut;
import org.glowroot.plugin.servlet.ServletAspect.HttpSession;

public class SessionAspect {

    private static final TransactionService transactionService = Agent.getTransactionService();
    private static final ConfigService configService = Agent.getConfigService("servlet");

    /*
     * ================== Http Session Attributes ==================
     */

    @Pointcut(className = "javax.servlet.http.HttpSession", methodName = "setAttribute|putValue",
            methodParameterTypes = {"java.lang.String", "java.lang.Object"})
    public static class SetAttributeAdvice {
        @IsEnabled
        public static boolean isEnabled() {
            return configService.isEnabled();
        }
        @OnAfter
        public static void onAfter(@BindReceiver HttpSession session,
                @BindParameter @Nullable String name, @BindParameter @Nullable Object value) {
            if (name == null) {
                // theoretically possible, so just ignore
                return;
            }
            // name is non-null per HttpSession.setAttribute() javadoc, but value may be null
            // (which per the javadoc is the same as calling removeAttribute())
            ServletMessageSupplier messageSupplier = ServletAspect.getServletMessageSupplier();
            if (messageSupplier != null) {
                updateUserIfApplicable(name, value, session);
                updateSessionAttributesIfApplicable(messageSupplier, name, value, session);
            }
        }
    }

    @Pointcut(className = "javax.servlet.http.HttpSession", methodName = "removeAttribute",
            methodParameterTypes = {"java.lang.String"})
    public static class RemoveAttributeAdvice {
        @IsEnabled
        public static boolean isEnabled() {
            return configService.isEnabled();
        }
        @OnAfter
        public static void onAfter(@BindReceiver HttpSession session,
                @BindParameter @Nullable String name) {
            // calling HttpSession.setAttribute() with null value is the same as calling
            // removeAttribute(), per the setAttribute() javadoc
            SetAttributeAdvice.onAfter(session, name, null);
        }
    }

    private static void updateUserIfApplicable(String name, @Nullable Object value,
            HttpSession session) {
        if (value == null) {
            // if user value is set to null, don't clear it
            return;
        }
        String sessionUserAttributePath = ServletPluginProperties.sessionUserAttributePath();
        if (!sessionUserAttributePath.isEmpty()) {
            // capture user now, don't use a lazy supplier
            if (sessionUserAttributePath.equals(name)) {
                transactionService.setTransactionUser(value.toString());
            } else if (sessionUserAttributePath.startsWith(name + ".")) {
                String user = HttpSessions.getSessionAttributeTextValue(session,
                        sessionUserAttributePath);
                if (user != null) {
                    // if user is null, don't clear it by setting Suppliers.ofInstance(null)
                    transactionService.setTransactionUser(user);
                }
            }
        }
    }

    private static void updateSessionAttributesIfApplicable(ServletMessageSupplier messageSupplier,
            String name, @Nullable Object value, HttpSession session) {
        if (ServletPluginProperties.captureSessionAttributeNames().contains(name)
                || ServletPluginProperties.captureSessionAttributeNames().contains("*")) {
            // update all session attributes (possibly nested) at or under the set attribute
            for (String capturePath : ServletPluginProperties.captureSessionAttributePaths()) {
                if (capturePath.equals(name) || capturePath.equals("*")) {
                    updateSessionAttribute(messageSupplier, name, value);
                } else if (capturePath.startsWith(name + ".")) {
                    updateNestedSessionAttributes(messageSupplier, capturePath, value, session);
                }
            }
        }
    }

    private static void updateSessionAttribute(ServletMessageSupplier messageSupplier, String name,
            @Nullable Object value) {
        if (value == null) {
            messageSupplier.putSessionAttributeChangedValue(name, null);
        } else {
            messageSupplier.putSessionAttributeChangedValue(name, value.toString());
        }
    }

    private static void updateNestedSessionAttributes(ServletMessageSupplier messageSupplier,
            String capturePath, @Nullable Object value, HttpSession session) {
        if (capturePath.endsWith(".*")) {
            String capturePathBase = capturePath.substring(0, capturePath.length() - 2);
            Object val = HttpSessions.getSessionAttribute(session, capturePathBase);
            if (val == null) {
                messageSupplier.putSessionAttributeChangedValue(capturePathBase, null);
            } else if (val instanceof Map<?, ?>) {
                for (Entry<?, ?> entry : ((Map<?, ?>) val).entrySet()) {
                    Object v = entry.getValue();
                    messageSupplier.putSessionAttributeChangedValue(
                            capturePathBase + "." + entry.getKey(),
                            v == null ? null : v.toString());
                }
            } else {
                for (Entry<String, String> entry : Beans.propertiesAsText(val).entrySet()) {
                    messageSupplier.putSessionAttributeChangedValue(
                            capturePathBase + "." + entry.getKey(), entry.getValue());
                }
            }
        } else if (value == null) {
            // no need to navigate path since it will always be null
            messageSupplier.putSessionAttributeChangedValue(capturePath, null);
        } else {
            String val = HttpSessions.getSessionAttributeTextValue(session, capturePath);
            messageSupplier.putSessionAttributeChangedValue(capturePath, val);
        }
    }
}
