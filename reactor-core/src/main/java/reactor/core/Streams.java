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

import java.util.Arrays;

/**
 * A public factory to build {@link StandardStream}
 *
 * @author Stephane Maldini
 */
public abstract class Streams {
	/**
	 * Create a delayed {@link StandardStream} with no initial state, ready to accept values.
	 *
	 * @return A {@link StandardStream.Spec} to further refine the {@link StandardStream} and then build it.
	 */
	public static <T> StandardStream.Spec<T> defer() {
		return new StandardStream.Spec<T>(null);
	}

	/**
	 * Create a delayed {@link StandardStream} with initial state, ready to accept values.
	 *
	 * @return A {@link StandardStream.Spec} to further refine the {@link StandardStream} and then build it.
	 */
	@SuppressWarnings("unchecked")
	public static <T> StandardStream.Spec<T> defer(T value) {
		return new StandardStream.Spec<T>(Arrays.asList(value));
	}

	/**
	 * Create a {@link StandardStream} from the given list of values.
	 *
	 * @param values The values to use.
	 * @param <T>    The type of the values.
	 * @return A {@link StandardStream.Spec} to further refine the {@link StandardStream} and then build it.
	 */
	public static <T> StandardStream.Spec<T> each(Iterable<T> values) {
		return new StandardStream.Spec<T>(values);
	}
}
