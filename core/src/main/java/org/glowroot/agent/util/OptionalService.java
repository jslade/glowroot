/*
 * Copyright 2013-2015 the original author or authors.
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
package org.glowroot.agent.util;

import javax.annotation.Nullable;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import org.glowroot.live.ImmutableAvailability;
import org.glowroot.live.LiveJvmService.Availability;

public abstract class OptionalService<T> {

    public static <T> OptionalService<T> available(T service) {
        return new PresentOptionalService<T>(service);
    }

    public static <T> OptionalService<T> unavailable(String reason) {
        return new AbsentOptionalService<T>(reason);
    }

    public static <T> OptionalService<T> lazy(Supplier<OptionalService<T>> supplier) {
        return new LazyOptionalService<T>(supplier);
    }

    public abstract Availability getAvailability();

    public abstract @Nullable T getService();

    private static class PresentOptionalService<T> extends OptionalService<T> {

        private final Availability availability;
        private final T service;

        public PresentOptionalService(T service) {
            this.availability = ImmutableAvailability.of(true, "");
            this.service = service;
        }

        @Override
        public Availability getAvailability() {
            return availability;
        }

        @Override
        public T getService() {
            return service;
        }
    }

    private static class AbsentOptionalService<T> extends OptionalService<T> {

        private final Availability availability;

        public AbsentOptionalService(String reason) {
            this.availability = ImmutableAvailability.of(false, reason);
        }

        @Override
        public Availability getAvailability() {
            return availability;
        }

        @Override
        public @Nullable T getService() {
            return null;
        }
    }

    private static class LazyOptionalService<T> extends OptionalService<T> {

        private final Supplier<OptionalService<T>> supplier;

        LazyOptionalService(Supplier<OptionalService<T>> supplier) {
            this.supplier = Suppliers.memoize(supplier);
        }

        @Override
        public Availability getAvailability() {
            return supplier.get().getAvailability();
        }

        @Override
        public @Nullable T getService() {
            return supplier.get().getService();
        }
    }
}
