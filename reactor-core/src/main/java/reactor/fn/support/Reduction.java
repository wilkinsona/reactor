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

package reactor.fn.support;

/**
 * A {@link reactor.core.StandardStream#reduce(reactor.fn.Function)} operation needs a stateful object to pass as the argument,
 * which contains the
 * last accumulated value, as well as the next, just-accepted value.
 *
 * @param <VALUE> The type of the values that are being reduced.
 * @param <REDUCED> The type of the reduced value.
 *
 * @author Stephane Maldini
 * @author Andy Wilkinson
 */
public class Reduction<VALUE, REDUCED> {

	private final REDUCED reducedValue;

	private final VALUE nextValue;

	public Reduction(REDUCED reducedValue, VALUE nextValue) {
		this.reducedValue = reducedValue;
		this.nextValue = nextValue;
	}

	/**
	 * Get the reduced value.
	 *
	 * @return The reduced value
	 */
	public REDUCED getReducedValue() {
		return reducedValue;
	}

	/**
	 * Get the next input value.
	 *
	 * @return The next value
	 */
	public VALUE getNextValue() {
		return nextValue;
	}
}
