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

import java.io.IOException;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletConfig;

import org.glowroot.Containers;
import org.glowroot.container.AppUnderTest;
import org.glowroot.container.Container;
import org.glowroot.container.config.PluginConfig;
import org.glowroot.container.trace.Trace;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletPluginTest {

    private static final String PLUGIN_ID = "servlet";

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = Containers.getSharedContainer();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        container.close();
    }

    @After
    public void afterEachTest() throws Exception {
        container.checkAndReset();
    }

    @Test
    public void testServlet() throws Exception {
        // given
        // when
        container.executeAppUnderTest(ExecuteServlet.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        assertThat(header.headline()).isEqualTo("/testservlet");
        assertThat(header.transactionName()).isEqualTo("/testservlet");
        assertThat(header.detail().get("Request http method")).isEqualTo("GET");
        assertThat(header.entryCount()).isZero();
    }

    @Test
    public void testFilter() throws Exception {
        // given
        // when
        container.executeAppUnderTest(ExecuteFilter.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        assertThat(header.headline()).isEqualTo("/testfilter");
        assertThat(header.transactionName()).isEqualTo("/testfilter");
        assertThat(header.detail().get("Request http method")).isEqualTo("GET");
        assertThat(header.entryCount()).isZero();
    }

    @Test
    public void testCombination() throws Exception {
        // given
        // when
        container.executeAppUnderTest(ExecuteFilterWithNestedServlet.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        assertThat(header.headline()).isEqualTo("/testfilter");
        assertThat(header.transactionName()).isEqualTo("/testfilter");
        assertThat(header.detail().get("Request http method")).isEqualTo("GET");
        assertThat(header.entryCount()).isZero();
    }

    @Test
    public void testNoQueryString() throws Exception {
        // given
        // when
        container.executeAppUnderTest(TestNoQueryString.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        assertThat(header.detail()).doesNotContainKey("Request query string");
        assertThat(header.entryCount()).isZero();
    }

    @Test
    public void testEmptyQueryString() throws Exception {
        // given
        // when
        container.executeAppUnderTest(TestEmptyQueryString.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        assertThat(header.detail().get("Request query string")).isEqualTo("");
        assertThat(header.entryCount()).isZero();
    }

    @Test
    public void testNonEmptyQueryString() throws Exception {
        // given
        // when
        container.executeAppUnderTest(TestNonEmptyQueryString.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        assertThat(header.detail().get("Request query string")).isEqualTo("a=b&c=d");
        assertThat(header.entryCount()).isZero();
    }

    @Test
    public void testServletThrowsException() throws Exception {
        // given
        // when
        container.executeAppUnderTest(ServletThrowsException.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        assertThat(header.error().get().message()).isNotEmpty();
        assertThat(header.error().get().exception().isPresent()).isTrue();
        assertThat(header.entryCount()).isZero();
    }

    @Test
    public void testFilterThrowsException() throws Exception {
        // given
        // when
        container.executeAppUnderTest(FilterThrowsException.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        assertThat(header.error().get().message()).isNotEmpty();
        assertThat(header.error().get().exception().isPresent()).isTrue();
        assertThat(header.entryCount()).isZero();
    }

    @Test
    public void testSend500Error() throws Exception {
        // given
        // when
        container.executeAppUnderTest(Send500Error.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        assertThat(header.error().get().message()).isEqualTo("sendError, HTTP status code 500");
        assertThat(header.error().get().exception().isPresent()).isFalse();
        List<Trace.Entry> entries = container.getTraceService().getEntries(header.id());
        assertThat(entries).hasSize(1);
        Trace.Entry entry = entries.get(0);
        assertThat(entry.error().get().message()).isEqualTo("sendError, HTTP status code 500");
        assertThat(entry.error().get().exception().isPresent()).isFalse();
        assertThat(entry.stackTraceElements()).isNotEmpty();
        assertThat(entry.stackTraceElements().get(0)).contains("sendError");
    }

    @Test
    public void testSetStatus500Error() throws Exception {
        // given
        // when
        container.executeAppUnderTest(SetStatus500Error.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        assertThat(header.error().get().message()).isEqualTo("setStatus, HTTP status code 500");
        assertThat(header.error().get().exception().isPresent()).isFalse();
        List<Trace.Entry> entries = container.getTraceService().getEntries(header.id());
        assertThat(entries).hasSize(1);
        Trace.Entry entry = entries.get(0);
        assertThat(entry.error().get().message()).isEqualTo("setStatus, HTTP status code 500");
        assertThat(entry.error().get().exception().isPresent()).isFalse();
        assertThat(entry.stackTraceElements().get(0)).contains("setStatus");
    }

    @Test
    public void testPluginDisabled() throws Exception {
        // given
        PluginConfig pluginConfig = container.getConfigService().getPluginConfig(PLUGIN_ID);
        pluginConfig.setEnabled(false);
        container.getConfigService().updatePluginConfig(PLUGIN_ID, pluginConfig);
        // when
        container.executeAppUnderTest(ExecuteServlet.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        assertThat(header).isNull();
    }

    @Test
    public void testBizzareServletContainer() throws Exception {
        // given
        // when
        container.executeAppUnderTest(BizzareServletContainer.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        assertThat(header).isNull();
    }

    @Test
    public void testBizzareThrowingServletContainer() throws Exception {
        // given
        // when
        container.executeAppUnderTest(BizzareThrowingServletContainer.class);
        // then
        Trace.Header header = container.getTraceService().getLastTrace();
        assertThat(header).isNull();
    }

    @SuppressWarnings("serial")
    public static class ExecuteServlet extends TestServlet {}

    public static class ExecuteFilter extends TestFilter {}

    public static class ExecuteFilterWithNestedServlet extends TestFilter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            new TestFilter().doFilter(request, response, chain);
        }
    }

    public static class TestServletContextListener implements AppUnderTest, ServletContextListener {
        @Override
        public void executeApp() {
            contextInitialized(null);
        }
        @Override
        public void contextInitialized(ServletContextEvent sce) {}
        @Override
        public void contextDestroyed(ServletContextEvent sce) {}
    }

    @SuppressWarnings("serial")
    public static class TestServletInit extends HttpServlet implements AppUnderTest {
        @Override
        public void executeApp() throws ServletException {
            init(new MockServletConfig());
        }
        @Override
        public void init(ServletConfig config) throws ServletException {
            // calling super to make sure it doesn't end up in an infinite loop (this happened once
            // before due to bug in weaver)
            super.init(config);
        }
    }

    public static class TestFilterInit implements AppUnderTest, Filter {
        @Override
        public void executeApp() {
            init(new MockFilterConfig());
        }
        @Override
        public void init(FilterConfig filterConfig) {}
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {}
        @Override
        public void destroy() {}
    }

    @SuppressWarnings("serial")
    public static class TestNoQueryString extends TestServlet {
        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {
            ((MockHttpServletRequest) request).setQueryString(null);
        }
    }

    @SuppressWarnings("serial")
    public static class TestEmptyQueryString extends TestServlet {
        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {
            ((MockHttpServletRequest) request).setQueryString("");
        }
    }

    @SuppressWarnings("serial")
    public static class TestNonEmptyQueryString extends TestServlet {
        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {
            ((MockHttpServletRequest) request).setQueryString("a=b&c=d");
        }
    }

    @SuppressWarnings("serial")
    public static class InvalidateSession extends TestServlet {
        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {
            ((MockHttpServletRequest) request).setSession(new MockHttpSession(null, "1234"));
        }
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            request.getSession().invalidate();
        }
    }

    @SuppressWarnings("serial")
    public static class ServletThrowsException extends TestServlet {
        private final RuntimeException exception = new RuntimeException("Something happened");
        @Override
        public void executeApp() throws Exception {
            try {
                super.executeApp();
            } catch (RuntimeException e) {
                // only suppress expected exception
                if (e != exception) {
                    throw e;
                }
            }
        }
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            throw exception;
        }
    }

    public static class FilterThrowsException extends TestFilter {
        private final RuntimeException exception = new RuntimeException("Something happened");
        @Override
        public void executeApp() throws Exception {
            try {
                super.executeApp();
            } catch (RuntimeException e) {
                // only suppress expected exception
                if (e != exception) {
                    throw e;
                }
            }
        }
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
            throw exception;
        }
    }

    @SuppressWarnings("serial")
    public static class Send500Error extends TestServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            response.sendError(500);
        }
    }

    @SuppressWarnings("serial")
    public static class SetStatus500Error extends TestServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            response.setStatus(500);
        }
    }

    public static class BizzareServletContainer implements AppUnderTest, Servlet {
        @Override
        public void executeApp() throws Exception {
            service(null, null);
        }
        @Override
        public void init(ServletConfig config) {}
        @Override
        public ServletConfig getServletConfig() {
            return null;
        }
        @Override
        public void service(ServletRequest req, ServletResponse res) {}
        @Override
        public String getServletInfo() {
            return null;
        }
        @Override
        public void destroy() {}
    }

    public static class BizzareThrowingServletContainer implements AppUnderTest, Servlet {
        @Override
        public void executeApp() throws Exception {
            try {
                service(null, null);
            } catch (RuntimeException e) {
            }
        }
        @Override
        public void init(ServletConfig config) {}
        @Override
        public ServletConfig getServletConfig() {
            return null;
        }
        @Override
        public void service(ServletRequest req, ServletResponse res) {
            throw new RuntimeException();
        }
        @Override
        public String getServletInfo() {
            return null;
        }
        @Override
        public void destroy() {}
    }

    public static class NestedTwo {
        private final String two;
        public NestedTwo(String two) {
            this.two = two;
        }
        public String getTwo() {
            return two;
        }
    }
}
