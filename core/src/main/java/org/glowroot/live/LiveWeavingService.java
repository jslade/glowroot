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
package org.glowroot.live;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import org.immutables.value.Value;

public interface LiveWeavingService {

    GlobalMeta getGlobalMeta();

    void preloadClasspathCache();

    List<String> getMatchingClassNames(String partialClassName, int limit);

    List<String> getMatchingMethodNames(String className, String partialMethodName, int limit);

    List<MethodSignature> getMethodSignatures(String className, String methodName);

    int reweave() throws Exception;

    // null means unknown
    @Nullable
    Boolean isTimerWrapperMethodsActive();

    @Value.Immutable
    public interface GlobalMeta {
        boolean jvmOutOfSync();
        boolean jvmRetransformClassesSupported();
    }

    @Value.Immutable
    public interface MethodSignature {
        String name();
        ImmutableList<String> parameterTypes();
        String returnType();
        ImmutableList<String> modifiers();
    }

    public class LiveWeavingServiceNop implements LiveWeavingService {

        @Override
        public GlobalMeta getGlobalMeta() {
            return ImmutableGlobalMeta.builder()
                    .jvmOutOfSync(false)
                    .jvmRetransformClassesSupported(false)
                    .build();
        }

        @Override
        public void preloadClasspathCache() {}

        @Override
        public List<String> getMatchingClassNames(String partialClassName, int limit) {
            return ImmutableList.of();
        }

        @Override
        public List<String> getMatchingMethodNames(String className, String partialMethodName,
                int limit) {
            return ImmutableList.of();
        }

        @Override
        public List<MethodSignature> getMethodSignatures(String className, String methodName) {
            return ImmutableList.of();
        }

        @Override
        public int reweave() throws Exception {
            return 0;
        }

        @Override
        public @Nullable Boolean isTimerWrapperMethodsActive() {
            return null;
        }
    }
}
