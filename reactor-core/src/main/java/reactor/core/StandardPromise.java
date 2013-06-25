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

import java.util.concurrent.TimeUnit;

import reactor.fn.Consumer;
import reactor.fn.Event;
import reactor.fn.Function;
import reactor.fn.Functions;
import reactor.fn.Observable;

final class StandardPromise<T> extends AbstractComposable<T, StandardPromise<T>> implements Promise<T> {

	private final Object monitor = new Object();

	private T value;

	private Throwable error;

	StandardPromise(Observable src) {
		super(src);
	}

	/**
	 * Set the value of the {@literal Promise} so that subsequent calls to {@link reactor.core.StandardPromise#get()} will throw
	 * this exception instead of returning a value.
	 *
	 * @param error The exception to use.
	 * @return {@literal this}
	 */
	public void set(Throwable error) {
		synchronized (monitor) {
			assertPending();
			this.error = error;
			monitor.notifyAll();
		}
		doAccept(error);
	}

	/**
	 * Set this {@literal Promise} to the given value.
	 *
	 * @param value The value to set.
	 * @return {@literal this}
	 */
	public void set(T value) {
		synchronized (monitor) {
			assertPending();
			this.value = value;
			monitor.notifyAll();
		}
		doAccept(value);
	}

	@Override
	public Promise<T> consume(Object key, Observable observable) {
		synchronized (this.monitor) {
			if (isPending()) {
				doConsume(key, observable);
			} else if (isFulfilled()) {
				observable.notify(key, Event.wrap(value));
			}
		}
		return this;
	}

	/**
	 * Indicates if this {@literal Promise} is still pending
	 *
	 * @return {@literal true} if pending, otherwise {@literal false}.
	 */
	public boolean isPending() {
		synchronized(this.monitor) {
			return (!isFulfilled()) && (!isRejected());
		}
	}

	/**
	 * Indicates if this {@literal Promise} has been successfully completed
	 *
	 * @return {@literal true} if fulfilled successfully, otherwise {@literal false}.
	 */
	public boolean isFulfilled() {
		synchronized(this.monitor) {
			return this.value != null;
		}
	}

	/**
	 * Indicates if this {@literal Promise} has completed in error
	 *
	 * @return {@literal true} if the promise completed in error, otherwise {@literal false}.
	 */
	public boolean isRejected() {
		synchronized(this.monitor) {
			return this.error != null;
		}
	}

	public Promise<T> consume(Consumer<T> consumer) {
		synchronized (monitor) {
			if (isRejected()) {
				return this;
			} else if (isFulfilled()) {
				Functions.schedule(consumer, value, getObservable());
				return this;
			} else {
				return doConsume(consumer);
			}
		}
	}

	@Override
	public <V> StandardPromise<V> map(final Function<T, V> fn) {
		synchronized (monitor) {
			final StandardPromise<V> child = createMapped(getObservable());
			Consumer<T> consumer = new Consumer<T>() {
				@Override
				public void accept(T t) {
					child.accept(fn.apply(t));
				}
			};
			consume(consumer);
			return child;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <E extends Throwable> StandardPromise<T> when(Class<E> exceptionType, Consumer<E> onError) {
		synchronized(this.monitor) {
			if (isPending()) {
				doWhen(exceptionType, onError);
			} else if (isRejected()) {
				if (exceptionType.isInstance(error)) {
					Functions.schedule(onError, (E)error, getObservable());
				}
			}
		}
		return this;
	}

	@Override
	public void accept(Throwable error) {
		set(error);
	}

	@Override
	public void accept(T value) {
		set(value);
	}

	private void assertPending() {
		synchronized (monitor) {
			if (!isPending()) {
				throw new IllegalStateException("This Promise has already completed.");
			}
		}
	}

	@Override
	public T get() throws IllegalStateException {
		synchronized (this.monitor) {
			if (this.error != null) {
				throw new IllegalStateException(this.error);
			}
			return this.value;
		}
	}

	@Override
	public T await() throws InterruptedException {
		synchronized (this.monitor) {
			while (isPending()) {
				this.monitor.wait();
			}
			return get();
		}
	}

	@Override
	public T await(long timeout, TimeUnit unit) throws InterruptedException {
		long endTime = System.currentTimeMillis() + TimeUnit.MILLISECONDS.convert(timeout,  unit);
		synchronized (this.monitor) {
			while (isPending() && System.currentTimeMillis() < endTime) {
				this.monitor.wait();
			}
			return get();
		}
	}

	@Override
	protected StandardPromise<T> create(Observable observable) {
		return new StandardPromise<T>(observable);
	}

	private <V> StandardPromise<V> createMapped(Observable observable) {
		return new StandardPromise<V>(observable);
	}
}