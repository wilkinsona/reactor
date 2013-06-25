package reactor.core;

import java.util.concurrent.TimeUnit;

import reactor.fn.Function;
import reactor.fn.Supplier;

/**
 * A {@code Promise} provides access to a single value that will become available at some point in
 * the future.
 *
 * @author Andy Wilkinson
 * @author Jon Brisbin
 * @author Stephane Maldini
 *
 * @param <T> The type of the value(s) that will become available
 */
public interface Promise<T> extends Composable<T, Promise<T>>, Supplier<T> {

	/**
	 * Registers a {@link Function} that will be {@link Function#apply(Object) applied} to the
	 * value when it becomes available. Returns a new {@code Promise} that will provide access to
	 * the function's output.
	 *
	 * @param function The {@code Function} to apply
	 *
	 * @return The new {@code Future} that provides access to the function's output
	 */
	<V> Promise<V> map(Function<T, V> function);

	/**
	 * Causes the current thread to wait for a value to become available, unless the thread is {@link
	 * Thread#interrupt() interrupted} or the default timeout period elapses.
	 *
	 * @return The value that became available.
	 *
	 * @throws InterruptedException if the current thread is interrupted while waiting for a value
	 */
	T await() throws InterruptedException;

	/**
	 * Causes the current thread to wait for a value to become available, unless the thread is {@link
	 * Thread#interrupt() interrupted} or the specified timeout period elapses.
	 *
	 * @param timeout the timeout period
	 * @param unit the time unit of the {@code timeout} argument
	 *
	 * @return The value that became available.
	 *
	 * @throws InterruptedException if the current thread is interrupted while waiting for a value
	 */
	T await(long timeout, TimeUnit unit) throws InterruptedException;

}
