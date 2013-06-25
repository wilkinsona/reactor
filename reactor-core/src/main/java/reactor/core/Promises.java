/*
 * Copyright (c) 2011-2013 GoPivotal, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.core;

import reactor.fn.Supplier;

import java.util.Arrays;
import java.util.Collection;

/**
 * A public factory to build {@link StandardPromise}
 *
 * @author Stephane Maldini
 */
public abstract class Promises {
	/**
	 * Create an empty {@link reactor.core.StandardPromise}.
	 *
	 * @param <T> The type of the object to be set on the {@link reactor.core.StandardPromise}.
	 * @return The new {@link reactor.core.StandardPromise}.
	 */
	public static <T> StandardPromise.Spec<T> defer() {
		return new StandardPromise.Spec<T>(null, null, null, null);
	}

	/**
	 * Create a new {@link reactor.core.StandardPromise} based on the given {@link Throwable}.
	 *
	 * @param reason The exception to set.
	 * @param <T>    The type of the expected {@link reactor.core.StandardPromise}.
	 * @return The new {@link reactor.core.StandardPromise}.
	 */
	@SuppressWarnings("unchecked")
	public static <T> StandardPromise.Spec<T> error(Throwable reason) {
		return (StandardPromise.Spec<T>) new StandardPromise.Spec<Throwable>(null, null, reason, null);
	}

	/**
	 * Create a {@literal Promise} based on the given value.
	 *
	 * @param value The value to use.
	 * @param <T>   The type of the value.
	 * @return a {@link reactor.core.StandardPromise.Spec}.
	 */
	@SuppressWarnings("unchecked")
	public static <T> StandardPromise.Spec<T> success(T value) {
		return new StandardPromise.Spec<T>(value, null, null, null);
	}

	/**
	 * Create a {@literal Promise} based on the given supplier.
	 *
	 * @param supplier The value to use.
	 * @param <T>      The type of the function result.
	 * @return a {@link reactor.core.StandardPromise.Spec}.
	 */
	public static <T> StandardPromise.Spec<T> task(Supplier<T> supplier) {
		return new StandardPromise.Spec<T>(null, supplier, null, null);
	}

	/**
	 * Merge given composable into a new a {@literal Promise}.
	 *
	 * @param composables The composables to use.
	 * @param <T>         The type of the function result.
	 * @return a {@link reactor.core.StandardPromise.Spec}.
	 */
	public static <T> StandardPromise.Spec<Collection<T>> when(AbstractComposable<T>... composables) {
		return when(Arrays.asList(composables));
	}

	/**
	 * Merge given composable into a new a {@literal Promise}.
	 *
	 * @param composables The composables to use.
	 * @param <T>         The type of the function result.
	 * @return a {@link reactor.core.StandardPromise.Spec}.
	 */
	public static <T> StandardPromise.Spec<Collection<T>> when(Collection<? extends AbstractComposable<T>> composables) {
		return new StandardPromise.Spec<Collection<T>>(null, null, null, composables);
	}
}
