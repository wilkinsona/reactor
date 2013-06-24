package reactor.core;

import reactor.fn.Function;
import reactor.fn.support.Reduction;

/**
 * A {@code Stream} provides access to a stream of values that will each become available at some
 * point in the future.
 *
 * @author Andy Wilkinson
 * @author Jon Brisbin
 * @author Stephane Maldini
 *
 */
public interface Stream<T> extends Composable<T, Stream<T>> {

	/**
	 * Sets the number of values that this Stream should expect to process
	 *
	 * @param expected The number of values to expect
	 *
	 * @return {@code this}
	 */
	// TODO Should this even be possible on the consuming side?
	Stream<T> setExpected(long expected);

	/**
	 * Returns a new {@link Future} that will provide access to the first value in the stream.
	 *
	 * @return A {@code Future} for the stream's first value
	 */
	Future<T> first();

	/**
	 * Returns a new {@link Future} that will provide access to the last value in the stream.
	 * <strong>Note:</strong> for a stream to have a last value, it must be of known length.
	 * If the stream's length is unknown the returned future will never make a value available.
	 *
	 * @return A {@code Future} for the stream's last value.
	 */
	// TODO Should the consumer be able to set the accept count? If so, how? What happens if
	// accept count is changed when a stream's in use? It would make it possible for last to
	// be reached multiple times:
	// 	1. Accept count is 10
	// 	2. 10 values pass through the stream, triggering last
	//  3. Accept count is set to 15
	//  4. 5 more values pass through the stream, triggering last again which shouldn't/can't
	//     happen as it's a Future which works with a single value.
	// Setting accept count also makes it possible for last to be missed:
	//  1. Accept count is 10
	//  2. 8 values pass through the stream
	//  3. Accept count is set to 5. What value is passed to last? The 5th value? The 8th value?
	//     The former's impossible without storing every value that passes through the stream.
	Future<T> last();

	/**
	 * Registers a reduction {@code function} that will be {@link Function#apply(Object) applied}
	 * to the stream's values.
	 *
	 * @param function The reduction function
	 *
	 * @return A new stream of the results of the reduction
	 */
	// TODO How should accept count affect this? Old implementation accumulated the result until
	// the accept count was reached and then passed the result of the reduction into the new stream.
	// This meant that, if the accept count was set, only a single value was passed to the new
	// stream, i.e. a Future would have been a better return type. If the accept count wasn't set,
	// every reduced value is passed into the returned stream. Should this support batching? I.e.
	// when the accept count is set to n, the returned stream is passed a value every n items?
	// If so, should the reduction be reset at the start of every batch?
	<V> Stream<V> reduce(Function<Reduction<T, V>, V> function);

	/**
	 * Produces a new {@code Stream} that expects {@code count} values. The new stream will
	 * consume values from this stream.
	 *
	 * @param count The number of values to be expected by the new stream
	 *
	 * @return The new stream
	 */
	Stream<T> take(long count);

	/**
	 * Registers a {@link Function} that will be {@link Function#apply(Object) applied} to each
	 * value that becomes available. Returns a new {@code Stream} that will provide access to
	 * the function's output.
	 *
	 * @param function The {@code Function} to apply
	 *
	 * @return The new {@code Stream} that provides access to the function's output
	 */
	<V> Stream<V> map(Function<T, V> function);

}
