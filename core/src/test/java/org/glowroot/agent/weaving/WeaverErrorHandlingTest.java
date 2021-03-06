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
package org.glowroot.agent.weaving;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import org.glowroot.agent.weaving.SomeAspect.BindPrimitiveBooleanTravelerBadAdvice;
import org.glowroot.agent.weaving.SomeAspect.BindPrimitiveTravelerBadAdvice;
import org.glowroot.agent.weaving.SomeAspect.MoreVeryBadAdvice;
import org.glowroot.agent.weaving.SomeAspect.MoreVeryBadAdvice2;
import org.glowroot.agent.weaving.SomeAspect.VeryBadAdvice;
import org.glowroot.agent.weaving.WeavingTimerService.WeavingTimer;
import org.glowroot.plugin.api.weaving.Mixin;

import static org.assertj.core.api.Assertions.assertThat;

public class WeaverErrorHandlingTest {

    @Test
    public void shouldHandleVoidPrimitiveTravelerGracefully() throws Exception {
        // given
        SomeAspectThreadLocals.resetThreadLocals();
        Misc test =
                newWovenObject(BasicMisc.class, Misc.class, BindPrimitiveTravelerBadAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspectThreadLocals.onReturnTraveler.get()).isEqualTo(0);
        assertThat(SomeAspectThreadLocals.onThrowTraveler.get()).isNull();
        assertThat(SomeAspectThreadLocals.onAfterTraveler.get()).isEqualTo(0);
    }

    @Test
    public void shouldHandleVoidPrimitiveBooleanTravelerGracefully() throws Exception {
        // given
        SomeAspectThreadLocals.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class,
                BindPrimitiveBooleanTravelerBadAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspectThreadLocals.onReturnTraveler.get()).isEqualTo(false);
        assertThat(SomeAspectThreadLocals.onThrowTraveler.get()).isNull();
        assertThat(SomeAspectThreadLocals.onAfterTraveler.get()).isEqualTo(false);
    }

    @Test
    public void shouldNotCallOnThrowForOnBeforeException() throws Exception {
        // given
        SomeAspectThreadLocals.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, VeryBadAdvice.class);
        // when
        try {
            test.executeWithArgs("one", 2);
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).isEqualTo("Sorry");
            assertThat(SomeAspectThreadLocals.onBeforeCount.get()).isEqualTo(1);
            assertThat(SomeAspectThreadLocals.onThrowCount.get()).isEqualTo(0);
            assertThat(SomeAspectThreadLocals.onAfterCount.get()).isEqualTo(0);
            return;
        }
        throw new AssertionError("Expecting IllegalStateException");
    }

    @Test
    public void shouldNotCallOnThrowForOnReturnException() throws Exception {
        // given
        SomeAspectThreadLocals.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, MoreVeryBadAdvice.class);
        // when
        try {
            test.executeWithArgs("one", 2);
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).isEqualTo("Sorry");
            assertThat(SomeAspectThreadLocals.onReturnCount.get()).isEqualTo(1);
            assertThat(SomeAspectThreadLocals.onThrowCount.get()).isEqualTo(0);
            assertThat(SomeAspectThreadLocals.onAfterCount.get()).isEqualTo(0);
            return;
        }
        throw new AssertionError("Expecting IllegalStateException");
    }

    // same as MoreVeryBadAdvice, but testing weaving a method with a non-void return type
    @Test
    public void shouldNotCallOnThrowForOnReturnException2() throws Exception {
        // given
        SomeAspectThreadLocals.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, MoreVeryBadAdvice2.class);
        // when
        try {
            test.executeWithReturn();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).isEqualTo("Sorry");
            assertThat(SomeAspectThreadLocals.onReturnCount.get()).isEqualTo(1);
            assertThat(SomeAspectThreadLocals.onThrowCount.get()).isEqualTo(0);
            assertThat(SomeAspectThreadLocals.onAfterCount.get()).isEqualTo(0);
            return;
        }
        throw new AssertionError("Expecting IllegalStateException");
    }

    public static <S, T extends S> S newWovenObject(Class<T> implClass, Class<S> bridgeClass,
            Class<?> adviceClass, Class<?>... extraBridgeClasses) throws Exception {

        IsolatedWeavingClassLoader.Builder loader = IsolatedWeavingClassLoader.builder();
        loader.setAdvisors(ImmutableList.of(new AdviceBuilder(adviceClass, false).build()));
        Mixin mixin = adviceClass.getAnnotation(Mixin.class);
        if (mixin != null) {
            loader.setMixinTypes(ImmutableList.of(MixinType.from(mixin, adviceClass)));
        }
        loader.setWeavingTimerService(NopWeavingTimerService.INSTANCE);
        // adviceClass is passed as bridgeable so that the static threadlocals will be accessible
        // for test verification
        loader.addBridgeClasses(bridgeClass, adviceClass);
        loader.addBridgeClasses(extraBridgeClasses);
        return loader.build().newInstance(implClass, bridgeClass);
    }

    private static class NopWeavingTimerService implements WeavingTimerService {
        private static final NopWeavingTimerService INSTANCE = new NopWeavingTimerService();
        @Override
        public WeavingTimer start() {
            return NopWeavingTimer.INSTANCE;
        }
    }

    private static class NopWeavingTimer implements WeavingTimer {
        private static final NopWeavingTimer INSTANCE = new NopWeavingTimer();
        @Override
        public void stop() {}
    }
}
