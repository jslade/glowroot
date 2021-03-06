/*
 * Copyright 2012-2015 the original author or authors.
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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.glowroot.container.AppUnderTest;

@SuppressWarnings("serial")
class TestServlet extends HttpServlet implements AppUnderTest {

    @Override
    public void executeApp() throws Exception {
        MockHttpServletRequest request = new MockCatalinaHttpServletRequest("GET", "/testservlet");
        MockHttpServletResponse response = new PatchedMockHttpServletResponse();
        before(request, response);
        service(request, response);
    }

    // hook for subclasses
    protected void before(@SuppressWarnings("unused") HttpServletRequest request,
            @SuppressWarnings("unused") HttpServletResponse response) {}

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {}

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {}

    static class PatchedMockHttpServletResponse extends MockHttpServletResponse {

        @Override
        public String getContentType() {
            return getHeader("Content-Type");
        }
    }
}
