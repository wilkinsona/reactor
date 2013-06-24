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

import reactor.fn.*;
import reactor.fn.registry.Registration;
import reactor.fn.selector.Selector;
import reactor.util.Assert;

import java.util.concurrent.TimeUnit;

import static reactor.fn.Functions.$;

/**
 * A {@literal Future} is a way to notify components to provide new data that must wait on the data to become
 * available.
 *
 * @param <T> The {@link StandardFuture}  output type.
 * @author Stephane Maldini
 */
public class StandardFuture<T> implements Supplier<T> {

	protected final Object monitor = new Object();

	private final Object   acceptKey      = new Object();
	private final Selector acceptSelector = $(acceptKey);

	private final Environment env;
	private final Observable  observable;

	private long acceptedCount       = 0L;
	private long expectedAcceptCount = -1L;

	private T         value;
	private Throwable error;

	private boolean hasBlockers = false;

	/**
	 * Create a {@link StandardFuture} that uses the given {@link Reactor} for publishing events internally.
	 *
	 * @param observable The {@link Reactor} to use.
	 */
	protected StandardFuture(Environment env, Observable observable) {
		Assert.notNull(observable, "Observable cannot be null.");
		this.env = env;
		this.observable = observable;
	}


	/**
	 * Set the number of times to expect {@link #acceptKey} to be called.
	 *
	 * @param expectedAcceptCount The number of times {@link #acceptKey} will be called.
	 * @return {@literal this}
	 */
	public StandardFuture<T> setExpectedAcceptCount(long expectedAcceptCount) {
		synchronized (monitor) {
			doSetExpectedAcceptCount(expectedAcceptCount);
			if (acceptCountReached()) {
				monitor.notifyAll();
			}
		}
		return this;
	}


	/**
	 * Register a {@link reactor.fn.Consumer} that will be invoked whenever {@link #acceptKey} is called.
	 *
	 * @param consumer The consumer to invoke.
	 * @return {@literal this}
	 */
	public StandardFuture<T> consume(Consumer<T> consumer) {
		when(acceptSelector, consumer);
		return this;
	}

	/**
	 * Register a {@code key} and {@link Reactor} on which to publish an event whenever {@link #acceptKey} is called.
	 *
	 * @param key        The key to use when publishing the {@link reactor.fn.Event}.
	 * @param observable The {@link reactor.fn.Observable} on which to publish the {@link reactor.fn.Event}.
	 * @return {@literal this}
	 */
	public StandardFuture<T> consume(final Object key, final Observable observable) {
		Assert.notNull(observable);
		when(acceptSelector, new Consumer<T>() {
			@Override
			public void accept(T event) {
				observable.notify(key, Event.class.isAssignableFrom(event.getClass()) ? (Event<?>) event : Event.wrap(event));
			}
		});
		return this;
	}

	/**
	 * Create a new {@link StandardFuture} that is linked to the parent through the given {@link reactor.fn.Function}. When the parent's
	 * {@link #acceptKey} is invoked, this {@link reactor.fn.Function} is invoked and the result is passed into the returned
	 * {@link StandardFuture}.
	 *
	 * @param fn  The transformation function to apply.
	 * @param <V> The type of the object returned when the given {@link reactor.fn.Function}.
	 * @return The new {@link StandardFuture}.
	 */
	public <V> StandardFuture<V> map(final Function<T, V> fn) {
		Assert.notNull(fn);
		final StandardFuture<V> c = this.assignComposable(observable);
		when(acceptSelector, new Consumer<T>() {
			@Override
			public void accept(T value) {
				try {
					c.internalAccept(fn.apply(value));
				} catch (Throwable t) {
					handleError(c, t);
				}
			}
		});
		return c;
	}

	/**
	 * Selectively call the returned {@link reactor.core.StandardStream} depending on the predicate {@link reactor.fn.Function} argument
	 *
	 * @param fn The filter function, taking argument {@param <T>} and returning a {@link Boolean}
	 * @return The new {@link reactor.core.StandardStream}.
	 */
	public StandardFuture<T> filter(final Function<T, Boolean> fn) {
		Assert.notNull(fn);
		final StandardFuture<T> c = this.assignComposable(observable);
		consume(new Consumer<T>() {
			@Override
			public void accept(T value) {
				try {
					if (fn.apply(value)) {
						c.internalAccept(value);
					} else {
						c.decreaseAcceptLength();
					}
				} catch (Throwable t) {
					handleError(c, t);
				}
			}
		});

		return c;
	}


	/**
	 * Register a {@link reactor.fn.Consumer} to be invoked whenever an exception that is assignable when the given exception type.
	 *
	 * @param exceptionType The type of exception to handle. Also matches an subclass of this type.
	 * @param onError       The {@link reactor.fn.Consumer} to invoke when this error occurs.
	 * @param <E>           The type of exception.
	 * @return {@literal this}
	 */
	@SuppressWarnings("unchecked")
	public <E extends Throwable> StandardFuture<T> when(Class<E> exceptionType, final Consumer<E> onError) {
		Assert.notNull(exceptionType);
		Assert.notNull(onError);

		if (!isComplete()) {
			observable.on(Functions.T(exceptionType), new Consumer<Event<E>>() {
				@Override
				public void accept(Event<E> ev) {
					onError.accept(ev.getData());
				}
			});
		} else if (isError()) {
			Functions.schedule(onError, (E) error, observable);
		}
		return this;
	}


	public T await() throws InterruptedException {
		long defaultTimeout = 30000L;
		if (null != getEnvironment()) {
			defaultTimeout = getEnvironment().getProperty("reactor.await.defaultTimeout", Long.class, defaultTimeout);
		}
		return await(defaultTimeout, TimeUnit.MILLISECONDS);
	}

	public T await(long timeout, TimeUnit unit) throws InterruptedException {
		synchronized (monitor) {
			if (isComplete()) {
				return get();
			}
			if (timeout >= 0) {
				hasBlockers = true;
				long msTimeout = TimeUnit.MILLISECONDS.convert(timeout, unit);
				long endTime = System.currentTimeMillis() + msTimeout;
				long now;
				while (!isComplete() && (now = System.currentTimeMillis()) < endTime) {
					this.monitor.wait(endTime - now);
				}
			} else {
				while (!isComplete()) {
					this.monitor.wait();
				}
			}
			hasBlockers = false;
		}
		return get();
	}


	@Override
	public T get() {
		synchronized (this.monitor) {
			if (null != error) {
				throw new IllegalStateException(getError());
			}
			return value;
		}
	}


	protected Registration<Consumer<Event<T>>> when(Selector sel, final Consumer<T> consumer) {
		if (!isComplete()) {
			return observable.on(sel, new Consumer<Event<T>>() {
				@Override
				public void accept(Event<T> ev) {
					consumer.accept(ev.getData());
				}
			});
		} else if (!isError()) {
			Functions.schedule(consumer, value, observable);
		}
		return null;
	}


	protected boolean isComplete() {
		return isError() || acceptCountReached();
	}

	protected boolean isError() {
		synchronized (monitor) {
			return null != error;
		}
	}

	protected boolean acceptCountReached() {
		synchronized (monitor) {
			return expectedAcceptCount >= 0 && acceptedCount >= expectedAcceptCount;
		}
	}

	protected <U> StandardFuture<U> assignComposable(Observable src) {
		StandardFuture<U> c = this.createFuture(src);
		synchronized (monitor) {
			c.doSetExpectedAcceptCount(getExpectedAcceptCount());
		}
		return c;
	}

	protected <U> StandardFuture<U> createFuture(Observable src) {
		return new StandardFuture<U>(getEnvironment(), createReactor(getObservable()));
	}

	protected Reactor createReactor(Observable src) {
		Reactor.Spec rspec = Reactors.reactor().using(env);

		if (null != src && Reactor.class.isInstance(src)) {
			rspec.using((Reactor) src);
		}

		return rspec.sync().get();
	}

	protected void decreaseAcceptLength() {
		synchronized (monitor) {
			if (--expectedAcceptCount <= acceptedCount) {
				monitor.notifyAll();
			}
		}
	}

	protected final Environment getEnvironment() {
		return env;
	}

	protected final void doSetExpectedAcceptCount(long expectedAcceptCount) {
		synchronized (monitor) {
			this.expectedAcceptCount = expectedAcceptCount;
		}
	}

	protected long getExpectedAcceptCount() {
		synchronized (monitor) {
			return expectedAcceptCount;
		}
	}
	protected boolean isBeyondExceptedCount() {
		synchronized (monitor) {
			return acceptedCount > expectedAcceptCount;
		}
	}

	protected final Observable getObservable() {
		return this.observable;
	}

	protected final T getValue() {
		synchronized (this.monitor) {
			return this.value;
		}
	}

	protected void internalAccept(T value) {
		synchronized (monitor) {
			setValue(value);
			notifyAccept(Event.wrap(value));
			if (hasBlockers) {
				monitor.notifyAll();
			}

		}
	}

	protected void internalAccept(Throwable value) {
		synchronized (monitor) {
			//notifyError(value);
			setError(value);
			if (hasBlockers) {
				monitor.notifyAll();
			}
		}
	}

	protected final void setValue(T value) {
		synchronized (monitor) {
			this.value = value;
			acceptedCount++;
		}
	}

	protected final boolean isFirst() {
		synchronized (monitor) {
			return acceptedCount == 1;
		}
	}

	protected final Throwable getError() {
		synchronized (monitor) {
			return this.error;
		}
	}

	protected final void setError(Throwable error) {
		synchronized (monitor) {
			this.error = error;
		}
	}

	protected final void notifyAccept(Event<?> event) {
		observable.notify(acceptKey, event);
	}

	protected final void notifyError(Throwable error) {
		observable.notify(error.getClass(), Event.wrap(error));
	}

	protected void handleError(StandardFuture<?> c, Throwable t) {
		c.notifyError(t);
		c.decreaseAcceptLength();
	}
}
