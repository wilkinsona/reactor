package reactor.core;

import reactor.fn.Consumer;
import reactor.fn.Event;
import reactor.fn.Function;
import reactor.fn.Observable;

interface Composable<T, D extends Composable<T, D>> {

	/**
	 * Registers a {@link Consumer} that will be called to {@link Consumer#accept accept}
	 * values when they become available.
	 *
	 * @param consumer The consumer to register
	 *
	 * @return {@code this}
	 */
	D consume(Consumer<T> consumer);

	/**
	 * Registers an {@link Observable} to consume the values from this {@code Composable}. When the
	 * values become available the {@link Observable} will be {@link Observable#notify(Object, Event)
	 * notified} using the given {@code key}.
	 *
	 * @param key The notification key
	 * @param observable The {@code Observable} to notify
	 *
	 * @return {@code this}
	 */
	D consume(Object key, Observable observable);

	/**
	 * Registers a predicate {@link Function} that will be {@link Function#apply(Object) applied}
	 * to filter the values when they becomes available. Returns a new {@code Composable} that will
	 * provide access to the filtered values.
	 *
	 * @param function The filter {@code Function}
	 *
	 * @return The new {@code Delayed} that provides access to the filtered values
	 */
	D filter(Function<T, Boolean> function);

	/**
	 * Registers a {@link Consumer} that will be called to {@link Consumer#accept accept} any
	 * exceptional values that become available and are assignable to {@code exceptionType}.
	 *
	 * @param exceptionType The type of the exception
	 * @param onError The consumer to register
	 *
	 * @return {@code this}
	 */
	<E extends Throwable> D when(Class<E> exceptionType, Consumer<E> onError);
}
