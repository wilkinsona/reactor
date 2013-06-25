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

import reactor.Fn;
import reactor.fn.Consumer;
import reactor.fn.Event;
import reactor.fn.Function;
import reactor.fn.Observable;
import reactor.fn.selector.Selector;

abstract class AbstractComposable<T, U extends AbstractComposable<T, U>> implements Composable<T, U>, Consumer<T> {

	private final Observable observable;

	private final Object acceptKey = new Object();
	private final Selector acceptSelector = Fn.$(acceptKey);

	private final Object errorKey = new Object();
	private final Selector errorSelector = Fn.$(errorKey);

	protected AbstractComposable(Observable observable) {
		this.observable = observable;
	}

	@SuppressWarnings("unchecked")
	public final U doConsume(final Consumer<T> consumer) {
		this.observable.on(acceptSelector, new Consumer<Event<T>>() {

			@Override
			public void accept(Event<T> event) {
				consumer.accept(event.getData());
			}
		});
		return (U) this;
	}

	@SuppressWarnings("unchecked")
	protected final U doConsume(final Object key, final Observable observable) {
		this.observable.on(acceptSelector, new Consumer<Event<T>>() {

			@Override
			public void accept(Event<T> event) {
				observable.notify(key, Event.class.isAssignableFrom(event.getClass()) ? (Event<?>) event : Event.wrap(event));
			}
		});
		return (U) this;
	}

	@Override
	public final U filter(final Function<T, Boolean> predicate) {
		final U u = create(this.observable);
		consume(new Consumer<T>() {

			@Override
			public void accept(T t) {
				if (predicate.apply(t)) {
					u.accept(t);
				} else {
					u.accept(new FilterException());
				}
			}

		});
		return u;
	}

	@SuppressWarnings("unchecked")
	protected final <E extends Throwable> U doWhen(final Class<E> exceptionType, final Consumer<E> onError) {
		this.observable.on(errorSelector, new Consumer<Event<Throwable>>() {
			public void accept(Event<Throwable> event) {
				if (exceptionType.isInstance(event.getData())) {
					onError.accept((E) event.getData());
				}
			}
		});
		return (U) this;
	}

	protected abstract void accept(Throwable throwable);

	protected abstract U create(Observable observable);

	protected final void doAccept(T value) {
		this.observable.notify(acceptKey, Event.wrap(value));
	}

	protected final void doAccept(Throwable error) {
		this.observable.notify(errorKey, Event.wrap(error));
	}

	protected final Observable getObservable() {
		return this.observable;
	}

	public static final class FilterException extends RuntimeException {

		private static final long serialVersionUID = 1244572252678542067L;

	}

//	/**
//	 * Register a {@link AbstractComposable} that will be invoked whenever {@link #accept(Object)} or {@link #accept (Throwable)}
//	 * are called.
//	 *
//	 * @param composable The composable to invoke.
//	 * @return {@literal this}
//	 * @see {@link #accept(Object)}
//	 */
//	public AbstractComposable<T> consume(AbstractComposable<T> composable) {
//		consume((Consumer<T>) composable);
//		forwardError(composable);
//		return this;
//	}
//
//
//	/**
//	 * Provide a read-only future that no longer accepts user values
//	 */
//	public StandardFuture<T> future() {
//		final StandardFuture<T> c = super.createFuture(getObservable());
//		synchronized (monitor) {
//			c.doSetExpectedAcceptCount(getExpectedAcceptCount());
//			if (null != getValue()) {
//				c.setValue(getValue());
//			}
//			if (null != getError()) {
//				c.setError(getError());
//			}
//		}
//		when(Throwable.class, new Consumer<Throwable>() {
//			@Override
//			public void accept(Throwable throwable) {
//				c.internalAccept(throwable);
//			}
//		})
//				.consume(new Consumer<T>() {
//					@Override
//					public void accept(T t) {
//						c.internalAccept(t);
//					}
//				});
//		return c;
//	}

//	/**
//	 * Trigger composition with an exception to be processed by dedicated consumers
//	 *
//	 * @param error The exception
//	 */
//	public void accept(Throwable error) {
//		internalAccept(error);
//		notifyError(error);
//	}

//	/**
//	 * Trigger composition with a value to be processed by dedicated consumers
//	 *
//	 * @param value The exception
//	 */
//	@Override
//	public void accept(T value) {
//		internalAccept(value);
//	}


//	protected AbstractComposable<T> forwardError(final AbstractComposable<?> composable) {
//		if (composable.getObservable() == getObservable()) {
//			return this;
//		}
//		when(Throwable.class, new Consumer<Throwable>() {
//			@Override
//			public void accept(Throwable t) {
//				composable.accept(t);
//				composable.decreaseAcceptLength();
//			}
//		});
//		return this;
//	}

}
