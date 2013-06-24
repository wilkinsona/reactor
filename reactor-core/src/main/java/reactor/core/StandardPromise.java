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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.fn.*;
import reactor.fn.Observable;
import reactor.fn.dispatch.Dispatcher;
import reactor.fn.tuples.Tuple;
import reactor.fn.tuples.Tuple2;
import reactor.util.Assert;

import java.util.*;

/**
 * A {@literal Promise} is a {@link StandardStream} that can only be used once. When created, it is pending. If a value of
 * type {@link Throwable} is set, then the {@literal Promise} is completed {@link #isError in error} and the error
 * handlers are called. If a value of type <code>&lt;T&gt;</code> is set instead, the {@literal Promise} is completed
 * {@link #isSuccess successfully}.
 * <p/>
 * Calls to {@link reactor.core.StandardPromise#get()} are always non-blocking. If it is desirable to block the calling thread
 * until a result is available, though, call the {@link StandardPromise#await(long, java.util.concurrent.TimeUnit)} method.
 *
 * @param <T> The {@link StandardPromise} output type.
 *
 * @author Jon Brisbin
 * @author Stephane Maldini
 * @author Andy Wilkinson
 */
public class StandardPromise<T> extends StandardComposable<T> {

	private final Logger log = LoggerFactory.getLogger(getClass());

	StandardPromise(Environment env, Observable src) {
		super(env, src);
		setExpectedAcceptCount(1);
		getObservable().on(Functions.T(Throwable.class), new Consumer<Event<Throwable>>() {
			@Override
			public void accept(Event<Throwable> throwableEvent) {
				synchronized (monitor) {
					if (!isComplete()) {
						StandardPromise.this.set(throwableEvent.getData());
					} else {
						log.error(throwableEvent.getData().getMessage(), throwableEvent.getData());
					}
				}
			}
		});
	}


	/**
	 * Set the value of the {@literal Promise} so that subsequent calls to {@link reactor.core.StandardPromise#get()} will throw
	 * this exception instead of returning a value.
	 *
	 * @param error The exception to use.
	 * @return {@literal this}
	 */
	public StandardPromise<T> set(Throwable error) {
		synchronized (monitor) {
			assertPending();
			super.accept(error);
		}
		return this;
	}

	/**
	 * Set this {@literal Promise} to the given value.
	 *
	 * @param value The value to set.
	 * @return {@literal this}
	 */
	public StandardPromise<T> set(T value) {
		synchronized (monitor) {
			assertPending();
			super.accept(value);
		}
		return this;
	}

	/**
	 * Set a {@link Consumer} to invoke when this {@literal Promise} has either completed successfully or failed and set to
	 * an error.
	 *
	 * @param onComplete The {@link Consumer} to invoke on either failure or success.
	 * @return {@literal this}
	 */
	public StandardPromise<T> onComplete(final Consumer<StandardPromise<T>> onComplete) {
		Assert.notNull(onComplete);
		onSuccess(new Consumer<T>() {
			@Override
			public void accept(T t) {
				onComplete.accept(StandardPromise.this);
			}
		});
		onError(new Consumer<Throwable>() {
			@Override
			public void accept(Throwable t) {
				onComplete.accept(StandardPromise.this);
			}
		});
		return this;
	}

	/**
	 * Set a {@link Consumer} to invoke when this {@literal Promise} has completed successfully.
	 *
	 * @param onSuccess The {@link Consumer} to invoke on success.
	 * @return {@literal this}
	 */
	public StandardPromise<T> onSuccess(Consumer<T> onSuccess) {
		Assert.notNull(onSuccess);
		consume(onSuccess);
		return this;
	}

	/**
	 * Set a {@link Consumer} to invoke when this {@literal Promise} has failed.
	 *
	 * @param onError The {@link Consumer} to invoke on failure.
	 * @return {@literal this}
	 */
	public StandardPromise<T> onError(Consumer<Throwable> onError) {
		Assert.notNull(onError);
		when(Throwable.class, onError);
		return this;
	}

	/**
	 * Implementation of <a href="#">Promises/A+</a> that sets a success and failure {@link Consumer} in one call.
	 *
	 * @param onSuccess The {@link Consumer} to invoke on success.
	 * @param onError   The {@link Consumer} to invoke on failure.
	 * @return {@literal this}
	 */
	public StandardPromise<T> then(Consumer<T> onSuccess, Consumer<Throwable> onError) {
		if (null != onSuccess) {
			onSuccess(onSuccess);
		}
		if (null != onError) {
			onError(onError);
		}
		return this;
	}

	/**
	 * Implementation of <a href="#">Promises/A+</a> that sets a success and failure {@link Consumer} in one call and
	 * transforms the initial result into something else using the given {@link Function}.
	 *
	 * @param onSuccess The {@link Function} to invoke on success.
	 * @param onError   The {@link Consumer} to invoke on failure.
	 * @return {@literal this}
	 */
	public <V> StandardPromise<V> then(Function<T, V> onSuccess, Consumer<Throwable> onError) {
		StandardPromise<V> c = map(onSuccess);
		if (null != onError) {
			onError(onError);
		}
		return c;
	}

	/**
	 * Indicates if this {@literal Promise} has been successfully completed
	 *
	 * @return {@literal true} if fulfilled successfully, otherwise {@literal false}.
	 */
	public boolean isSuccess() {
		return acceptCountReached();
	}

	/**
	 * Indicates if this {@literal Promise} has completed in error
	 *
	 * @return {@literal true} if the promise completed in error, otherwise {@literal false}.
	 */
	@Override
	public boolean isError() {
		return super.isError();
	}

	/**
	 * Indicates if this {@literal Promise} is still pending
	 *
	 * @return {@literal true} if pending, otherwise {@literal false}.
	 */
	public boolean isPending() {
		return !isComplete();
	}

	@Override
	public StandardPromise<T> consume(final Consumer<T> consumer) {
		synchronized (monitor) {
			if (isError()) {
				return this;
			} else if (acceptCountReached()) {
				Functions.schedule(consumer, getValue(), getObservable());
				return this;
			} else {
				return (StandardPromise<T>) super.consume(consumer);
			}
		}
	}

	@Override
	public StandardPromise<T> consume(Object key, Observable observable) {
		synchronized (monitor) {
			if (acceptCountReached()) {
				observable.notify(key, Event.wrap(getValue()));
				return this;
			} else {
				return (StandardPromise<T>) super.consume(key, observable);
			}
		}
	}

	@Override
	public <V> StandardPromise<V> map(final Function<T, V> fn) {
		synchronized (monitor) {
			if (acceptCountReached()) {
				final StandardPromise<V> c = (StandardPromise<V>) this.assignComposable(getObservable());
				Functions.schedule(new Consumer<T>() {
					@Override
					public void accept(T value) {
						try {
							c.accept(fn.apply(value));
						} catch (Throwable t) {
							handleError(c, t);
						}
					}
				}, getValue(), getObservable());
				return c;
			} else {
				return (StandardPromise<V>) super.map(fn);
			}
		}
	}



	@Override
	public StandardPromise<T> filter(final Function<T, Boolean> fn) {
		synchronized (monitor) {
			final StandardPromise<T> p = createFuture(getObservable());

			Consumer<T> consumer = new Consumer<T>() {
				@Override
				public void accept(T value) {
					try {
						if (fn.apply(value)) {
							p.accept(value);
						} else {
							p.accept(new FilterException());
						}
					} catch (Throwable t) {
						handleError(p, t);
					}
				}
			};

			if (acceptCountReached()) {
				Functions.schedule(consumer, getValue(), getObservable());
			} else {
				consume(consumer);
			}
			return p;
		}
	}

	@Override
	public <E extends Throwable> StandardPromise<T> when(Class<E> exceptionType, Consumer<E> onError) {
		return (StandardPromise<T>)super.when(exceptionType, onError);
	}

	@Override
	public void accept(Throwable error) {
		set(error);
	}

	@Override
	public void accept(T value) {
		set(value);
	}

	@Override
	protected <U> StandardPromise<U> createFuture(Observable src) {
		final StandardPromise<U> p = new StandardPromise<U>(getEnvironment(), src);
		forwardError(p);
		return p;
	}

	private void assertPending() {
		synchronized (monitor) {
			if (!isPending()) {
				throw new IllegalStateException("This Promise has already completed.");
			}
		}
	}


	@SuppressWarnings("unchecked")
	protected StandardPromise<T> merge(Collection<? extends StandardComposable<?>> composables) {

		final int size = composables.size();
		if (size < 1) {
			return this;
		} else if (composables.size() == 1) {
			composables.iterator().next().consume(new Consumer() {
				@Override
				public void accept(Object t) {
					StandardPromise.this.accept((T) Arrays.asList(t));
				}
			});
			return this;
		}

		final StandardStream<Tuple2<?, Integer>> reducer =
				new StandardStream.DeferredStream<Tuple2<?, Integer>>(getEnvironment(), getObservable(), size);
		reducer
				.reduce()
				.map(new Function<List<Tuple2<?, Integer>>, T>() {
					@Override
					public T apply(List<Tuple2<?, Integer>> collection) {
						Collections.sort(collection, new Comparator<Tuple2<?, Integer>>() {
							@Override
							public int compare(Tuple2<?, Integer> o1, Tuple2<?, Integer> o2) {
								return o1.getT2().compareTo(o2.getT2());
							}
						});
						List<Object> orderedResult = new ArrayList<Object>();
						for (Tuple2<?, Integer> element : collection) {
							orderedResult.add(element.getT1());
						}
						return (T) orderedResult;
					}
				}).consume(this);

		Consumer<Object> consumer = new Consumer<Object>() {
			int i = 0;

			@Override
			public void accept(Object o) {
				reducer.accept(Tuple.of(o, i++));
			}
		};

		for (final StandardComposable c : composables) {
			c.forwardError(reducer).consume(consumer);
			if (StandardStream.DeferredStream.class.isInstance(c)) {
				((StandardStream.DeferredStream) c).delayedAccept();
			}
		}

		return this;
	}

	/**
	 * Build a {@link StandardStream} based on the given values, {@link Dispatcher dispatcher}, and {@link Reactor reactor}.
	 *
	 * @param <T> The type of the values.
	 */
	public static class Spec<T> extends ComponentSpec<Spec<T>, StandardPromise<T>> {

		protected final T                                   value;
		protected final Throwable                           error;
		protected final Supplier<T>                         supplier;
		protected final Collection<? extends StandardComposable<?>> mergeWith;

		public Spec(T value, Supplier<T> supplier, Throwable error, Collection<? extends StandardComposable<?>> composables) {
			this.value = value;
			this.supplier = supplier;
			this.error = error;
			this.mergeWith = composables;
		}

		@Override
		protected StandardPromise<T> configure(Reactor reactor) {
			final StandardPromise<T> prom;
			if (null != error) {
				prom = new StandardPromise<T>(env, reactor).set(error);
			} else if (supplier != null) {
				prom = new StandardPromise<T>(env, reactor);
				Functions.schedule(new Consumer<Object>() {
					@Override
					public void accept(Object o) {
						try {
							prom.set(supplier.get());
						} catch (Throwable t) {
							prom.set(t);
						}
					}
				}, null, reactor);
			} else if (null != value) {
				prom = new StandardPromise<T>(env, reactor).set(value);
			} else {
				prom = new StandardPromise<T>(env, reactor);
				if (null != mergeWith) {
					prom.merge(mergeWith);
				}
			}
			return prom;
		}
	}


	/**
	 * A {@code FilteredException} is used to {@link StandardPromise#set(Throwable) complete} a {@link StandardPromise#filter(Function)
	 * filtered promise} when the filter rejects the value.
	 *
	 * @author Andy Wilkinson
	 */
	public static final class FilterException extends RuntimeException {

		private static final long serialVersionUID = 1244572252678542067L;

	}
}