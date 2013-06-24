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



package reactor.groovy.ext

import groovy.transform.CompileStatic
import reactor.core.StandardFuture
import reactor.core.StandardPromise
import reactor.core.StandardStream
import reactor.fn.Consumer
import reactor.fn.Function
import reactor.fn.Observable
import reactor.fn.support.Reduction
import reactor.groovy.support.ClosureConsumer
import reactor.groovy.support.ClosureFunction
import reactor.groovy.support.ClosureReduce
/**
 * @author Jon Brisbin
 * @author Stephane Maldini
 */
@CompileStatic
class FutureExtensions {

	/**
	 * Alias
	 */

	static <T, V> StandardFuture<V> to(final StandardFuture<T> selfType, final key, final Observable observable) {
		selfType.consume key, observable
	}

	/**
	 * Closure converters
	 */

	static <T, V> StandardFuture<V> map(final StandardFuture<T> selfType, final Closure<V> closure) {
		selfType.map new ClosureFunction<T, V>(closure)
	}

	static <T> StandardFuture<T> consume(final StandardFuture<T> selfType, final Closure closure) {
		selfType.consume new ClosureConsumer<T>(closure)
	}

	static <T> StandardStream<T> filter(final StandardStream<T> selfType, final Closure<Boolean> closure) {
		selfType.filter new ClosureFunction<T, Boolean>(closure)
	}

	static <T, V> StandardStream<V> reduce(final StandardStream<T> selfType, final Closure<V> closure, V initial = null) {
		selfType.reduce new ClosureReduce<T, V>(closure), initial
	}

	static <T, E> StandardFuture<T> when(final StandardFuture<T> selfType, final Class<E> exceptionType, final Closure closure) {
		selfType.when exceptionType, new ClosureConsumer<E>(closure)
	}

	static <T> StandardPromise<T> onError(final StandardPromise<T> selfType, final Closure closure) {
		selfType.onError new ClosureConsumer<Throwable>(closure)
	}

	static <T> StandardPromise<T> onComplete(final StandardPromise<T> selfType, final Closure closure) {
		selfType.onComplete new ClosureConsumer<StandardPromise<T>>(closure)
	}

	static <T> StandardPromise<T> onSuccess(final StandardPromise<T> selfType, final Closure closure) {
		selfType.onSuccess new ClosureConsumer<T>(closure)
	}

	static <T, V> StandardPromise<V> then(final StandardPromise<T> selfType, final Closure<V> closureSuccess,
	                              final Closure closureError = null) {
		selfType.then(new ClosureFunction<T, V>(closureSuccess), closureError ?
				new ClosureConsumer<Throwable>(closureError) : null)
	}

	/**
	 * Operator overloading
	 */

	static <T> StandardStream<T> leftShift(final StandardStream<T> selfType, final Consumer<T> other) {
		selfType.consume other
	}

	static <T> Promise<T> leftShift(final Promise<T> selfType, final Consumer<T> other) {
		selfType.onSuccess other
	}

	static <T, V> StandardStream<V> mod(final StandardStream<T> selfType, final Function<Reduction<T, V>, V> other) {
		selfType.reduce other
	}

	static <T, V> StandardFuture<V> or(final StandardFuture<T> selfType, final Function<T, V> other) {
		selfType.map other
	}

	static <T, V> Promise<V> or(final Promise<T> selfType, final Function<T, V> other) {
		selfType.then other,(Consumer<Throwable>) null
	}

	static <T, V> StandardStream<V> and(final StandardStream<T> selfType, final Function<T, Boolean> other) {
		selfType.filter other
	}

	static <T> Future<T> leftShift(final Future<T> selfType, final Closure other) {
		consume selfType, other
	}

	static <T> Promise<T> leftShift(final Promise<T> selfType, final Closure other) {
		onSuccess selfType, other
	}

	static <T, V> Stream<V> mod(final Stream<T> selfType, final Closure<V> other) {
		reduce selfType, other
	}

	static <T, V> Future<V> or(final Future<T> selfType, final Closure<V> other) {
		map selfType, other
	}


	static <T, V> Promise<V> or(final Promise<T> selfType, final Closure<V> other) {
		then selfType, other
	}

	static <T> Stream<T> and(final Stream<T> selfType, final Closure<Boolean> other) {
		filter selfType, other
	}


	static <T> Consumer<T> leftShift(final Consumer<T> selfType, T value) {
		selfType.accept value
		selfType
	}

	static <T> Promise<T> leftShift(final Promise<T> selfType, T value) {
		selfType.set value
	}
}
