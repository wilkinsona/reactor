package reactor.core;

import reactor.fn.Function;
import reactor.fn.tuples.Tuple2;

/**
 * A {@code Stream} provides access to a stream of values that will each become available at some
 * point in the future. A stream works in batches.
 *
 * @author Andy Wilkinson
 * @author Jon Brisbin
 * @author Stephane Maldini
 *
 */
public interface Stream<T> extends Composable<T, Stream<T>> {

	/**
	 * Returns a new {@link Stream} that will provide access to the first value in each batch of
	 * values that is processed by the stream.
	 *
	 * @return A {@code Stream} of each batch's first value
	 *
	 * @see #batch(long)
	 * @see #isBatched()
	 */
	Stream<T> first();

	/**
	 * Returns a new {@link Stream} that will provide access to the last value in each batch of
	 * values that is processed by the stream.
	 * <strong>Note:</strong> if the stream does not have a batch size, the returned stream will
	 * always be empty
	 *
	 * @return A {@code Stream} of each batch's last value
	 *
	 * @see #batch(long)
	 * @see #isBatched()
	 */
	Promise<T> last();

	/**
	 * Registers a reduction {@code function} that will be {@link Function#apply(Object) applied}
	 * to the stream's values. The returned stream will be populated with the results of the
	 * reduction. If this stream is batched, the value of the reduction at the end of each batch
	 * will be passed into the returned stream. If this stream is not batched, every reduced
	 * value will be passed into the returned stream.
	 *
	 * @param function The reduction function
	 *
	 * @return A new stream of the results of the reduction
	 *
	 * @see #batch(long)
	 * @see #isBatched()
	 */
	<V> Stream<V> reduce(Function<Tuple2<T, V>, V> function);

	/**
	 * Produces a new {@code Stream} with the given {@code batchSize}. The new stream will be
	 * populated with the values from this stream.
	 *
	 * @param batchSize The number of values to include in each batch
	 *
	 * @return The new stream
	 */
	Stream<T> batch(long batchSize);

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

	/**
	 * Indicates whether or not this stream is batched.
	 *
	 * @return {@code true} if the stream is batched, otherwise {@code false}
	 *
	 * @see #batch(long)
	 * @see #first()
	 * @see #last()
	 * @see #reduce(Function)
	 */
	boolean isBatched();

}
