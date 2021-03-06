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
package org.glowroot.plugin.api.weaving;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(TYPE)
@Retention(RUNTIME)
public @interface Pointcut {

    // target class name
    String className();
    // restrict pointcut to the given subclass and below
    // e.g. useful for pointcut on java.lang.Runnable.run(), but only for classes
    // matching com.yourcompany.*
    // also useful for pointcut on java.util.concurrent.Future.get(), but only for classes
    // under com.ning.http.client.ListenableFuture
    String declaringClassName() default "";
    /**
     * | and * can be used for limited regular expressions. Full regular expressions can be used by
     * starting and ending methodName with /.
     */
    // use "<init>" to weave constructors
    // patterns never match constructors
    // static initializers ("<clinit>") are not supported
    String methodName();
    // methodParameterTypes has no default since it's not obvious if default should be {} or {".."}
    String[] methodParameterTypes();
    String methodReturnType() default "";
    MethodModifier[] methodModifiers() default {};
    // the default is false since it costs a thread local lookup to ignore self nested calls, and
    // timers already handle self nested calls, so it is only needed for trace entries
    boolean ignoreSelfNested() default false;
    String timerName() default "";
    int priority() default 0;
}
