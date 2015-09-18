/**
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
package org.glowroot.plugin.httpclient;

import java.util.List;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.Containers;
import org.glowroot.container.AppUnderTest;
import org.glowroot.container.Container;
import org.glowroot.container.TraceMarker;
import org.glowroot.container.trace.Trace;

import static org.assertj.core.api.Assertions.assertThat;

public class ApacheHttpClient3xPluginTest {

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
    public void shouldCaptureHttpGet() throws Exception {
        container.executeAppUnderTest(ExecuteHttpGet.class);
        Trace.Header header = container.getTraceService().getLastTrace();
        List<Trace.Entry> entries = container.getTraceService().getEntries(header.id());
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).message())
                .isEqualTo("http client request: GET http://www.example.com/hello1");
    }

    @Test
    public void shouldCaptureHttpPost() throws Exception {
        container.executeAppUnderTest(ExecuteHttpPost.class);
        Trace.Header header = container.getTraceService().getLastTrace();
        List<Trace.Entry> entries = container.getTraceService().getEntries(header.id());
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).message())
                .isEqualTo("http client request: POST http://www.example.com/hello3");
    }

    public static class ExecuteHttpGet implements AppUnderTest, TraceMarker {
        @Override
        public void executeApp() throws Exception {
            traceMarker();
        }
        @Override
        public void traceMarker() throws Exception {
            HttpClient httpClient = new HttpClient();
            GetMethod httpGet = new GetMethod("http://www.example.com/hello1");
            httpClient.executeMethod(httpGet);
            httpGet.releaseConnection();
        }
    }

    public static class ExecuteHttpPost implements AppUnderTest, TraceMarker {
        @Override
        public void executeApp() throws Exception {
            traceMarker();
        }
        @Override
        public void traceMarker() throws Exception {
            HttpClient httpClient = new HttpClient();
            PostMethod httpPost = new PostMethod("http://www.example.com/hello3");
            httpClient.executeMethod(httpPost);
            httpPost.releaseConnection();
        }
    }
}
