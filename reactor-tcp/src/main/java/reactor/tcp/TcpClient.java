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
import reactor.core.*;
import reactor.fn.Consumer;
import reactor.fn.Event;
import reactor.fn.registry.CachingRegistry;
import reactor.fn.registry.Registration;
import reactor.fn.registry.Registry;
import reactor.fn.selector.Selector;
import reactor.fn.tuples.Tuple2;
import reactor.io.Buffer;
import reactor.tcp.config.ClientSocketOptions;
import reactor.tcp.encoding.Codec;
import reactor.util.Assert;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.util.Iterator;

import static reactor.fn.Functions.$;

/**
 * @author Jon Brisbin
 */
public abstract class TcpClient<IN, OUT> {

	private final Tuple2<Selector, Object>         open        = $();
	private final Tuple2<Selector, Object>         close       = $();
	private final Registry<TcpConnection<IN, OUT>> connections = new CachingRegistry<TcpConnection<IN, OUT>>(null);

	private final Reactor                reactor;
	private final ClientSocketOptions    options;
	private final Codec<Buffer, IN, OUT> codec;

	protected final Environment env;

	protected TcpClient(@Nonnull Environment env,
											@Nonnull Reactor reactor,
											@Nonnull InetSocketAddress connectAddress,
											ClientSocketOptions options,
											@Nullable Codec<Buffer, IN, OUT> codec) {
		Assert.notNull(env, "A TcpClient cannot be created without a properly-configured Environment.");
		Assert.notNull(reactor, "A TcpClient cannot be created without a properly-configured Reactor.");
		Assert.notNull(connectAddress, "A TcpClient cannot be created without a properly-configure connect InetSocketAddress.");
		this.env = env;
		this.reactor = reactor;
		this.options = options;
		this.codec = codec;
	}

	/**
	 * Open a {@link TcpConnection} to the configured host:port and return a {@link StandardPromise} that will be fulfilled when
	 * the client is connected.
	 *
	 * @return A {@link StandardPromise} that will be filled with the {@link TcpConnection} when connected.
	 */
	public abstract StandardPromise<TcpConnection<IN, OUT>> open();

	/**
	 * Close any open connections and disconnect this client.
	 *
	 * @return A {@link StandardPromise} that will be fulfilled with {@literal null} when the connections have been closed.
	 */
	public StandardPromise<Void> close() {
		final StandardPromise<Void> p = Promises.<Void>defer().using(env).using(reactor).get();
		Fn.schedule(
				new Consumer<Void>() {
					@Override
					public void accept(Void v) {
						for (Registration<? extends TcpConnection<IN, OUT>> reg : connections) {
							reg.getObject().close();
							reg.cancel();
						}
						doClose(p);
					}
				},
				null,
				reactor
		);
		return p;
	}

	/**
	 * Subclasses should register the given channel and connection for later use.
	 *
	 * @param channel    The channel object.
	 * @param connection The {@link TcpConnection}.
	 * @param <C>        The type of the channel object.
	 * @return {@link reactor.fn.registry.Registration} of this connection in the {@link Registry}.
	 */
	protected <C> Registration<? extends TcpConnection<IN, OUT>> register(@Nonnull C channel,
																																				@Nonnull TcpConnection<IN, OUT> connection) {
		Assert.notNull(channel, "Channel cannot be null.");
		Assert.notNull(connection, "TcpConnection cannot be null.");
		return connections.register($(channel), connection);
	}

	/**
	 * Find the {@link TcpConnection} for the given channel object.
	 *
	 * @param channel The channel object.
	 * @param <C>     The type of the channel object.
	 * @return The {@link TcpConnection} associated with the given channel.
	 */
	protected <C> TcpConnection<IN, OUT> select(@Nonnull C channel) {
		Assert.notNull(channel, "Channel cannot be null.");
		Iterator<Registration<? extends TcpConnection<IN, OUT>>> conns = connections.select(channel).iterator();
		if (conns.hasNext()) {
			return conns.next().getObject();
		} else {
			TcpConnection<IN, OUT> conn = createConnection(channel);
			register(channel, conn);
			notifyOpen(conn);
			return conn;
		}
	}

	/**
	 * Close the given channel.
	 *
	 * @param channel The channel object.
	 * @param <C>     The type of the channel object.
	 */
	protected <C> void close(@Nonnull C channel) {
		Assert.notNull(channel, "Channel cannot be null");
		for (Registration<? extends TcpConnection<IN, OUT>> reg : connections.select(channel)) {
			TcpConnection<IN, OUT> conn = reg.getObject();
			reg.getObject().close();
			notifyClose(conn);
			reg.cancel();
		}
	}

	/**
	 * Subclasses should implement this method and provide a {@link TcpConnection} object.
	 *
	 * @param channel The channel object to associate with this connection.
	 * @param <C>     The type of the channel object.
	 * @return The new {@link TcpConnection} object.
	 */
	protected abstract <C> TcpConnection<IN, OUT> createConnection(C channel);

	/**
	 * Notify this client's consumers than a global error has occurred.
	 *
	 * @param error The error to notify.
	 */
	protected void notifyError(@Nonnull Throwable error) {
		Assert.notNull(error, "Error cannot be null.");
		reactor.notify(error.getClass(), Event.wrap(error));
	}

	/**
	 * Notify this client's consumers that the connection has been opened.
	 *
	 * @param conn The {@link TcpConnection} that was opened.
	 */
	protected void notifyOpen(@Nonnull TcpConnection<IN, OUT> conn) {
		reactor.notify(open.getT2(), Event.wrap(conn));
	}

	/**
	 * Notify this clients's consumers that the connection has been closed.
	 *
	 * @param conn The {@link TcpConnection} that was closed.
	 */
	protected void notifyClose(@Nonnull TcpConnection<IN, OUT> conn) {
		reactor.notify(close.getT2(), Event.wrap(conn));
	}

	/**
	 * Get the {@link Codec} in use.
	 *
	 * @return The codec. May be {@literal null}.
	 */
	@Nullable
	protected Codec<Buffer, IN, OUT> getCodec() {
		return codec;
	}

	protected abstract void doClose(StandardPromise<Void> promise);

	public static class Spec<IN, OUT> extends ComponentSpec<Spec<IN, OUT>, TcpClient<IN, OUT>> {
		private final Constructor<? extends TcpClient<IN, OUT>> clientImplConstructor;

		private InetSocketAddress connectAddress;
		private ClientSocketOptions options = new ClientSocketOptions();
		private Codec<Buffer, IN, OUT> codec;

		/**
		 * Create a {@code TcpClient.Spec} using the given implementation class.
		 *
		 * @param clientImpl The concrete implementation of {@link TcpClient} to instantiate.
		 */
		@SuppressWarnings({"unchecked", "rawtypes"})
		public Spec(@Nonnull Class<? extends TcpClient> clientImpl) {
			Assert.notNull(clientImpl, "TcpClient implementation class cannot be null.");
			try {
				this.clientImplConstructor = (Constructor<? extends TcpClient<IN, OUT>>) clientImpl.getDeclaredConstructor(
						Environment.class,
						Reactor.class,
						InetSocketAddress.class,
						ClientSocketOptions.class,
						Codec.class
				);
				this.clientImplConstructor.setAccessible(true);
			} catch (NoSuchMethodException e) {
				throw new IllegalArgumentException("No public constructor found that matches the signature of the one found in the TcpClient class.");
			}
		}

		/**
		 * Set the common {@link ClientSocketOptions} for connections made in this client.
		 *
		 * @param options The socket options to apply to new connections.
		 * @return {@literal this}
		 */
		public Spec<IN, OUT> options(ClientSocketOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * The host and port to which this client should connect.
		 *
		 * @param host The host to connect to.
		 * @param port The port to connect to.
		 * @return {@literal this}
		 */
		public Spec<IN, OUT> connect(@Nonnull String host, int port) {
			Assert.isNull(connectAddress, "Connect address is already set.");
			this.connectAddress = new InetSocketAddress(host, port);
			return this;
		}

		/**
		 * The {@link Codec} to use to encode and decode data.
		 *
		 * @param codec The codec to use.
		 * @return {@literal this}
		 */
		public Spec<IN, OUT> codec(@Nullable Codec<Buffer, IN, OUT> codec) {
			Assert.isNull(this.codec, "Codec has already been set.");
			this.codec = codec;
			return this;
		}

		@Override
		protected TcpClient<IN, OUT> configure(Reactor reactor) {
			try {
				return clientImplConstructor.newInstance(
						env,
						reactor,
						connectAddress,
						options,
						codec
				);
			} catch (Throwable t) {
				throw new IllegalStateException(t);
			}
		}
	}

}
