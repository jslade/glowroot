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
package io.informant.core;

import io.informant.core.util.Static;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@Static
class Viewer {

    public static void main(String... args) throws InterruptedException {
        MainEntryPoint.startUsingSystemProperties();
        // Informant does not create any non-daemon threads, so need to block jvm from exiting
        Thread.sleep(Long.MAX_VALUE);
    }
}