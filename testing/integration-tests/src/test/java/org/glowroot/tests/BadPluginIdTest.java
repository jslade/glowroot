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
package org.glowroot.tests;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.Containers;
import org.glowroot.container.AppUnderTest;
import org.glowroot.container.Container;
import org.glowroot.plugin.api.Agent;

public class BadPluginIdTest {

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
    public void shouldNotReadTrace() throws Exception {
        // given
        container.addExpectedLogMessage("org.glowroot.agent.impl.ConfigServiceImpl",
                "unexpected plugin id: not-to-be-found (available plugin ids are"
                        + " glowroot-integration-tests, glowroot-test-container)");
        // when
        container.executeAppUnderTest(BadPluginId.class);
        // then
    }

    public static class BadPluginId implements AppUnderTest {
        @Override
        public void executeApp() {
            Agent.getConfigService("not-to-be-found");
        }
    }
}
