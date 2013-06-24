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

package reactor.tcp;

import reactor.Fn;
import reactor.R;
import reactor.S;
import reactor.core.Environment;
import reactor.core.Reactor;
import reactor.core.StandardStream;
import reactor.fn.Consumer;
import reactor.fn.Event;
import reactor.fn.Function;
import reactor.fn.dispatch.Dispatcher;
import reactor.fn.selector.Selector;
import reactor.fn.support.NotifyConsumer;
import reactor.fn.tuples.Tuple2;
import reactor.io.Buffer;
import reactor.tcp.encoding.Codec;

import static reactor.Fn.$;

/**
 * Implementations of this class should provide concrete functionality for doing real IO.
 *
 * @author Jon Brisbin
 */
public abstract class AbstractTcpConnection<IN, OUT> implements TcpConnection<IN, OUT> {

	private final long                     created = System.currentTimeMillis();
	private final Tuple2<Selector, Object> read    = $();
	private final Function<Buffer, IN>  decoder;
	private final Function<OUT, Buffer> encoder;

	protected final Dispatcher  ioDispatcher;
	protected final Reactor     ioReactor;
	protected final Reactor     eventsReactor;
	protected final Environment env;

	protected AbstractTcpConnection(Environment env,
																	Codec<Buffer, IN, OUT> codec,
																	Dispatcher ioDispatcher,
																	Reactor eventsReactor) {
		this.env = env;
		this.ioDispatcher = ioDispatcher;
		this.ioReactor = R.reactor()
											.using(env)
											.using(ioDispatcher)
											.get();
		this.eventsReactor = eventsReactor;
		if (null != codec) {
			this.decoder = codec.decoder(new NotifyConsumer<IN>(read.getT2(), eventsReactor));
			this.encoder = codec.encoder();
		} else {
			this.decoder = null;
			this.encoder = null;
		}
	}

	/**
	 * Get the {@code System.currentTimeMillis()} this connection was created.
	 *
	 * @return The age in milliseconds.
	 */
	public long getCreated() {
		return created;
	}

	@Override
	public void close() {
		eventsReactor.getConsumerRegistry().unregister(read.getT2());
	}


	@Override
	public StandardStream<IN> in() {
		StandardStream<IN> c = S.<IN>defer()
										.using(env)
										.using(eventsReactor.getDispatcher())
										.get();
		consume(c);
		return c;
	}

	@Override
	public StandardStream<OUT> out() {
		StandardStream<OUT> s = S.<OUT>defer()
										 .using(env)
										 .using(eventsReactor.getDispatcher())
										 .get();
		s.consume(new Consumer<OUT>() {
			@Override
			public void accept(OUT out) {
				send(out, null);
			}
		});
		return s;
	}

	@Override
	public TcpConnection<IN, OUT> consume(final Consumer<IN> consumer) {
		eventsReactor.on(read.getT1(), new Consumer<Event<IN>>() {
			@Override
			public void accept(Event<IN> ev) {
				consumer.accept(ev.getData());
			}
		});
		return this;
	}

	@Override
	public TcpConnection<IN, OUT> receive(final Function<IN, OUT> fn) {
		consume(new Consumer<IN>() {
			@Override
			public void accept(IN in) {
				send(fn.apply(in));
			}
		});
		return this;
	}

	@Override
	public TcpConnection<IN, OUT> send(StandardStream<OUT> data) {
		data.consume(new Consumer<OUT>() {
			@Override
			public void accept(OUT out) {
				send(out, null);
			}
		});
		return this;
	}

	@Override
	public TcpConnection<IN, OUT> send(OUT data) {
		return send(data, null);
	}

	@Override
	public TcpConnection<IN, OUT> send(OUT data, final Consumer<Boolean> onComplete) {
		Fn.schedule(
				new Consumer<OUT>() {
					@Override
					public void accept(OUT data) {
						if (null != encoder) {
							Buffer bytes = encoder.apply(data);
							if (bytes.remaining() > 0) {
								write(bytes, onComplete);
							}
						} else {
							if (Buffer.class.isInstance(data)) {
								write((Buffer) data, onComplete);
							} else {
								write(data, onComplete);
							}
						}
					}
				},
				data,
				ioReactor
		);
		return this;
	}

	/**
	 * Perfoming necessary decoding on the data and notify the internal {@link Reactor} of any results.
	 *
	 * @param data The data to decode.
	 * @return {@literal true} if any more data is remaining to be consumed in the given {@link Buffer}, {@literal false}
	 *         otherwise.
	 */
	public boolean read(Buffer data) {
		if (null != decoder && null != data.byteBuffer()) {
			decoder.apply(data);
		} else {
			eventsReactor.notify(read.getT2(), Event.wrap(data));
		}

		return data.remaining() > 0;
	}

	/**
	 * Subclasses should override this method to perform the actual IO of writing data to the connection.
	 *
	 * @param data       The data to write, as a {@link Buffer}.
	 * @param onComplete The callback to invoke when the write is complete.
	 */
	protected abstract void write(Buffer data, Consumer<Boolean> onComplete);

	/**
	 * Subclasses should override this method to perform the actual IO of writing data to the connection.
	 *
	 * @param data       The data to write.
	 * @param onComplete The callback to invoke when the write is complete.
	 */
	protected abstract void write(Object data, Consumer<Boolean> onComplete);

}
