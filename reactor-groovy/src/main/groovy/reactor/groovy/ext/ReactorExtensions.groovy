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
import reactor.Fn
import reactor.P
import reactor.core.StandardPromise
import reactor.core.Promises
import reactor.fn.Observable
import reactor.groovy.support.ClosureConsumer
import reactor.groovy.support.ClosureSupplier
/**
 * @author Stephane Maldini
 */
@CompileStatic
class ReactorExtensions {

	/**
	 * Closure converters
	 */
	static <T> void schedule(final Fn selfType, final T value, final Observable observable, final Closure closure) {
		Fn.schedule new ClosureConsumer(closure), value, observable
	}

	static <T> Promise.Spec<T> task(final Promises selfType, Closure<T> callback) {
		P.task new ClosureSupplier<T>(callback)
	}

	static <T> Promise.Spec<T> from(final StandardPromise<T> selfType, Closure<T> callback) {
		task null, callback
	}
}
