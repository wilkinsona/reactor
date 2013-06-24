package reactor.core;

import reactor.fn.Function;

/**
 * A {@code Future} provides access to a single value that will become available at some point in
 * the future.
 *
 * @author Andy Wilkinson
 * @author Jon Brisbin
 * @author Stephane Maldini
 *
 * @param <T> The type of the value(s) that will become available
 */
public interface Future<T> extends Composable<T, Future<T>> {

	/**
	 * Registers a {@link Function} that will be {@link Function#apply(Object) applied} to the
	 * value when it becomes available. Returns a new {@code Delayed} that will provide access to
	 * the function's output.
	 *
	 * @param function The {@code Function} to apply
	 *
	 * @return The new {@code Future} that provides access to the function's output
	 */
	<V> Future<V> map(Function<T, V> function);

}
