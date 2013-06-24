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

import reactor.fn.Consumer;
import reactor.fn.Event;
import reactor.fn.Function;
import reactor.fn.Observable;
import reactor.fn.dispatch.Dispatcher;
import reactor.fn.selector.Selector;
import reactor.fn.support.Reduction;
import reactor.util.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static reactor.fn.Functions.$;

/**
 * A {@literal Stream} may be triggered several times, e.g. by processing a collection,
 * and have dedicated data group processing methods.
 *
 * @param <T> The  {@link StandardStream} output type.
 * @author Jon Brisbin
 * @author Andy Wilkinson
 * @author Stephane Maldini
 */
public class StandardStream<T> extends StandardComposable<T> {

	private final Object   firstKey      = new Object();
	private final Selector firstSelector = $(firstKey);

	private final Object   lastKey      = new Object();
	private final Selector lastSelector = $(lastKey);

	/**
	 * Create a {@link StandardStream} that uses the given {@link Reactor} for publishing events internally.
	 *
	 * @param observable The {@link Reactor} to use.
	 */
	StandardStream(Environment env, Observable observable) {
		super(env, observable);
	}

	/**
	 * Set the number of times to expect {@link #accept(Object)} to be called.
	 *
	 * @param expectedAcceptCount The number of times {@link #accept(Object)} will be called.
	 * @return {@literal this}
	 */
	@Override
	public StandardStream<T> setExpectedAcceptCount(long expectedAcceptCount) {
		boolean notifyLast = false;
		synchronized (monitor) {
			doSetExpectedAcceptCount(expectedAcceptCount);
			if (acceptCountReached()) {
				monitor.notifyAll();
				notifyLast = true;
			}
		}

		if (notifyLast) {
			getObservable().notify(lastKey, Event.wrap(getValue()));
		}

		return this;
	}

	/**
	 * Creates a new {@link StandardComposable} that will be triggered once, the first time {@link #accept(Object)} is called on
	 * the parent.
	 *
	 * @return A new {@link StandardComposable} that is linked to the parent.
	 */
	public StandardStream<T> first() {
		final StandardStream<T> c = (StandardStream<T>) this.assignComposable(getObservable());
		c.doSetExpectedAcceptCount(1);

		when(firstSelector, new Consumer<T>() {
			@Override
			public void accept(T t) {
				c.accept(t);
			}
		});

		return c;
	}

	/**
	 * Creates a new {@link StandardStream} that will be triggered once, the last time {@link #accept(Object)} is called on the
	 * parent.
	 *
	 * @return A new {@link StandardStream} that is linked to the parent.
	 * @see {@link #setExpectedAcceptCount(long)}
	 */
	public StandardStream<T> last() {
		final StandardStream<T> c = (StandardStream<T>) this.assignComposable(getObservable());
		c.doSetExpectedAcceptCount(1);

		when(lastSelector, new Consumer<T>() {
			@Override
			public void accept(T t) {
				c.accept(t);
			}
		});
		return c;
	}

	/**
	 * Accumulate a result until expected accept count has been reached - If this limit hasn't been set, each accumulated
	 * result will notify the returned {@link StandardStream}. A {@link Function} taking a {@link reactor.fn.support.Reduction}
	 * argument must be passed to process each pair formed of the last accumulated result and a new value to be processed.
	 *
	 * @param fn      The reduce function
	 * @param initial The initial accumulated result value e.g. an empty list.
	 * @param <V>     The type of the object returned by reactor reply.
	 * @return The new {@link StandardStream}.
	 */
	public <V> StandardStream<V> reduce(final Function<Reduction<T, V>, V> fn, V initial) {
		Assert.notNull(fn);
		final AtomicReference<V> lastValue = new AtomicReference<V>(initial);
		final StandardStream<V> c = (StandardStream<V>) this.assignComposable(getObservable());

		final long _expectedAcceptCount;
		synchronized (monitor) {
			_expectedAcceptCount = getExpectedAcceptCount();
		}

		c.setExpectedAcceptCount(_expectedAcceptCount < 0 ? _expectedAcceptCount : 1);
		when(lastSelector, new Consumer<T>() {
			@Override
			public void accept(T t) {
				c.accept(lastValue.get());
			}
		});

		consume(new Consumer<T>() {
			@Override
			public void accept(T value) {
				try {
					Reduction<T, V> r = new Reduction<T, V>(lastValue.get(), value);
					lastValue.set(fn.apply(r));
					if (_expectedAcceptCount < 0) {
						c.accept(lastValue.get());
					}
				} catch (Throwable t) {
					handleError(c, t);
				}
			}
		});

		return c;
	}

	/**
	 * Accumulate a result until expected accept count has been reached - If this limit hasn't been set, each accumulated
	 * result will notify the returned {@link StandardStream}. Will automatically generate a collection formed from composable
	 * streamed results, until accept count is reached.
	 *
	 * @return The new {@link StandardStream}.
	 */
	public StandardStream<List<T>> reduce() {
		return reduce(new Function<Reduction<T, List<T>>, List<T>>() {
			@Override
			public List<T> apply(Reduction<T, List<T>> reducer) {
				reducer.getReducedValue().add(reducer.getNextValue());
				return reducer.getReducedValue();
			}
		}, new ArrayList<T>());
	}

	/**
	 * Take {@param count} number of values and send lastSelector event after {@param count} iterations
	 *
	 * @param count Number of values to accept
	 * @return The new {@link StandardStream}.
	 */
	public StandardStream<T> take(long count) {
		final StandardStream<T> c = (StandardStream<T>) this.assignComposable(getObservable());
		c.setExpectedAcceptCount(count);
		consume(c);

		return c;
	}

	/**
	 * Accumulate a result until expected accept count has been reached - If this limit hasn't been set, each accumulated
	 * result will notify the returned {@link StandardStream}. A {@link Function} taking a {@link Reduction} argument must be
	 * passed to process each pair formed of the last accumulated result and a new value to be processed.
	 *
	 * @param fn  The reduce function
	 * @param <V> The type of the object returned by reactor reply.
	 * @return The new {@link StandardStream}.
	 */
	public <V> StandardStream<V> reduce(final Function<Reduction<T, V>, V> fn) {
		return reduce(fn, null);
	}

	/**
	 * Create a new {@link StandardStream} that is linked to the parent through the given {@code key} and {@link Observable}.
	 * When the parent's {@link #accept(Object)} is invoked, its value is wrapped into an {@link Event} and passed to
	 * {@link Observable#notify (reactor.Event.wrap)} along with the given {@code key}. After the event is being propagated
	 * to the reactor consumers, the new composition expects {@param <V>} replies to be returned.
	 *
	 * @param key        The key to notify
	 * @param observable The observable to notify
	 * @param <V>        The type of the object returned by reactor reply.
	 * @return The new {@link StandardStream}.
	 */
	public <V> StandardStream<V> map(final Object key, final Observable observable) {
		Assert.notNull(observable);
		final StandardStream<V> c = (StandardStream<V>) this.assignComposable(observable);
		c.setExpectedAcceptCount(-1);
		final Object replyTo = new Object();

		observable.on($(replyTo), new Consumer<Event<V>>() {
			@Override
			public void accept(Event<V> event) {
				try {
					c.accept(event.getData());
				} catch (Throwable t) {
					handleError(c, t);
				}
			}
		});

		consume(new Consumer<T>() {
			@Override
			public void accept(T value) {
				try {
					Event<?> event = Event.class.isAssignableFrom(value.getClass()) ? (Event<?>) value : Event.wrap(value);
					event.setReplyTo(replyTo);
					observable.send(key, event);
				} catch (Throwable t) {
					handleError(c, t);
				}
			}
		});

		return c;
	}

	@Override
	public <E extends Throwable> StandardStream<T> when(Class<E> exceptionType, Consumer<E> onError) {
		return (StandardStream<T>) super.when(exceptionType, onError);
	}

	@Override
	public StandardStream<T> consume(Consumer<T> consumer) {
		return (StandardStream<T>) super.consume(consumer);
	}

	@Override
	public StandardStream<T> consume(Object key, Observable observable) {
		return (StandardStream<T>) super.consume(key, observable);
	}

	@Override
	public StandardStream<T> consume(StandardComposable<T> composable) {
		return (StandardStream<T>) super.consume(composable);
	}

	@Override
	public <V> StandardStream<V> map(Function<T, V> fn) {
		return (StandardStream<V>) super.map(fn);
	}

	@Override
	public StandardStream<T> filter(Function<T, Boolean> fn) {
		return (StandardStream<T>) super.filter(fn);
	}

	@Override
	protected void internalAccept(T value) {
		synchronized (monitor) {
			setValue(value);
			Event<T> ev = Event.wrap(value);

			if (acceptCountReached()) {
				monitor.notifyAll();
			}

			if (getExpectedAcceptCount() < 0 || !isBeyondExceptedCount()) {
				notifyAccept(ev);
			}
			if (isComplete()) {
				notifyLast(ev);
			}

			if (isFirst()) {
				notifyFirst(ev);
			}

		}
	}

	@Override
	protected <U> StandardFuture<U> createFuture(Observable src) {
		return new StandardStream<U>(getEnvironment(), createReactor(src));
	}


	protected final void notifyFirst(Event<?> event) {
		getObservable().notify(firstKey, event);
	}

	protected final void notifyLast(Event<?> event) {
		getObservable().notify(lastKey, event);
	}

	/**
	 * Build a {@link StandardStream} based on the given values, {@link Dispatcher dispatcher}, and {@link Reactor reactor}.
	 *
	 * @param <T> The type of the values.
	 */
	public static class Spec<T> extends ComponentSpec<Spec<T>, StandardStream<T>> {

		protected final Iterable<T> values;

		public Spec(Iterable<T> values) {
			this.values = values;
		}

		@Override
		protected StandardStream<T> configure(final Reactor reactor) {

			final StandardStream<T> comp;
			if (values != null) {
				comp = new DeferredStream<T>(env, reactor, values);
			} else {
				comp = new DeferredStream<T>(env, reactor, -1);
			}
			return comp;
		}
	}

	protected static class DeferredStream<T> extends StandardStream<T> {
		private final Object stateMonitor = new Object();
		protected final Iterable<T> values;
		protected AcceptState acceptState = AcceptState.DELAYED;

		protected DeferredStream(Environment env, Observable src, Iterable<T> values) {
			super(env, src);
			this.values = values;
			if (values instanceof Collection) {
				setExpectedAcceptCount((((Collection<?>) values).size()));
			}
		}

		protected DeferredStream(Environment env, Observable src, long length) {
			super(env, src);
			this.values = null;
			setExpectedAcceptCount(length);
		}

		@Override
		public void accept(Throwable error) {
			setError(error);
			notifyError(error);
		}

		private void acceptValues(Iterable<T> values) {
			for (T initValue : values) {
				internalAccept(initValue);
			}
		}

		@Override
		public void accept(T value) {
			boolean init = false;

			synchronized (this.stateMonitor) {
				if (acceptState == AcceptState.DELAYED) {
					acceptState = AcceptState.ACCEPTING;
					init = true;
				}
			}

			if (values != null) {
				if (init) {
					acceptValues(values);
				}
			} else {
				internalAccept(value);
			}

			synchronized (this.stateMonitor) {
				acceptState = AcceptState.ACCEPTED;
			}

		}

		@Override
		public T await(long timeout, TimeUnit unit) throws InterruptedException {
			delayedAccept();
			return super.await(timeout, unit);
		}

		@Override
		public T get() {
			delayedAccept();
			return super.get();
		}

		@Override
		protected <U> StandardStream<U> createFuture(Observable src) {
			final DeferredStream<T> self = this;
			final DeferredStream<U> c =
					new DeferredStream<U>(getEnvironment(), createReactor(src), self.getExpectedAcceptCount()) {
						@Override
						protected void delayedAccept() {
							self.delayedAccept();
						}
					};
			forwardError(c);
			return c;
		}

		protected void delayedAccept() {
			doAccept(null, null, null);
		}

		protected void doAccept(Throwable localError, Iterable<T> localValues, T localValue) {

			boolean acceptRequired = false;

			synchronized (this.stateMonitor) {
				if (acceptState == AcceptState.ACCEPTED) {
					return;
				} else if (acceptState == AcceptState.DELAYED) {
					if (localError == null && localValue == null && localValues == null) {
						synchronized (this.monitor) {
							localError = getError();
							localValue = getValue();
							localValues = values;
						}
					}
					if (localError != null || localValue != null || localValues != null) {
						acceptState = AcceptState.ACCEPTING;
						acceptRequired = true;
					}
				} else {
					while (acceptState == AcceptState.ACCEPTING) {
						try {
							stateMonitor.wait();
						} catch (InterruptedException ie) {
							Thread.currentThread().interrupt();
							break;
						}
					}
				}
			}

			if (acceptRequired) {
				if (null != localError) {
					accept(localError);
				} else if (null != localValues) {
					acceptValues(localValues);
				} else  {
					accept(localValue);
				}
				synchronized (stateMonitor) {
					acceptState = AcceptState.ACCEPTED;
					stateMonitor.notifyAll();
				}
			}
		}

		private static enum AcceptState {
			DELAYED, ACCEPTING, ACCEPTED
		}
	}

}