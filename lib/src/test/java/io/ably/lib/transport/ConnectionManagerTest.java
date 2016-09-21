package io.ably.lib.transport;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Connection;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Setup;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;

import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Created by gokhanbarisaker on 3/9/16.
 */
public class ConnectionManagerTest {
	/**
	 * <p>
	 * Verifies that ably connects to default host,
	 * when everything is fine.
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void connectionmanager_fallback_none() throws AblyException {
		Setup.TestVars testVars = Setup.getTestVars();
		ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.connected);

		/* Verify that,
		 *   - connectionManager is connected
		 *   - connectionManager is connected to the host without any fallback
		 */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.connected));
		assertThat(connectionManager.getHost(), is(equalTo(opts.realtimeHost)));
	}

	/**
	 * Verifies that fallback behaviour is applied and is using given
	 * {@link ClientOptions#fallbackHosts} when host is unresolvable or unreachable.
	 * Spec: RTN17b, RTN17c
	 *
	 * @throws AblyException
	 */
	@Test
	public void connectionmanager_fallback_array() throws AblyException {
		final String[] expectedFallbackHosts = new String[]{"f.ably-realtime.com", "g.ably-realtime.com", "h.ably-realtime.com", "i.ably-realtime.com", "j.ably-realtime.com"};
		final List<String> fallbackHostsList = Arrays.asList(expectedFallbackHosts);
		final TestTransportFactory testFactory = new TestTransportFactory();

		/* Init ably with custom fallbackHosts array */
		Setup.TestVars testVars = Setup.getTestVars();
		DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
		testVars.fillInOptions(opts);
		opts.autoConnect = false;
		opts.fallbackHosts = expectedFallbackHosts;
		opts.environment = null;
		/* Use a non-reachable port number */
		opts.tls = true;
		opts.tlsPort = 1234;
		opts.debugTransportFactory = testFactory;
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		/* Connect */
		connectionManager.connect();
		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.failed);

		/* Verify that,
		 *   - connection state is failed
		 *   - connection attempts against given fallback hosts */
		assertThat("Unexpected connection state", connectionManager.getConnectionState().state, is(ConnectionState.failed));
		List<String> hostStack = testFactory.getHostArgumentStack();
		for (int i = 1; i < hostStack.size(); i++) {
			assertThat("Unexpected host fallback", fallbackHostsList.contains(hostStack.get(i)), is(true));
		}
	}

	/**
	 * Verifies that fallback behaviour doesn't apply,
	 * when the specific {@link ClientOptions#environment} is being used
	 * Spec: RTN17b
	 *
	 * @throws AblyException
	 */
	@Test
	public void connectionmanager_fallback_none_environment() throws AblyException {
		final String givenEnvironment = "staging";
		final TestTransportFactory testFactory = new TestTransportFactory();

		/* Init ably with given environment */
		Setup.TestVars testVars = Setup.getTestVars();
		DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
		testVars.fillInOptions(opts);
		opts.autoConnect = false;
		opts.environment = givenEnvironment;
		opts.debugTransportFactory = testFactory;
		/* Use a non-reachable port number */
		opts.tls = true;
		opts.tlsPort = 1234;
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		/* Connect */
		connectionManager.connect();
		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.failed);

		List<String> hostStack = testFactory.getHostArgumentStack();
		assertThat("Unexpected count of connect attempts", hostStack.size(), is(1));
		assertEquals("Unexpected host mismatch", String.format("%s-%s", givenEnvironment, Defaults.HOST_REALTIME), hostStack.get(0));
	}

	/**
	 * <p>
	 * Verifies that fallback behaviour doesn't apply, when the default
	 * custom endpoint is being used
	 * </p>
	 * <p>
	 * Spec: RTN17b
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void connectionmanager_fallback_none_customhost() throws AblyException {
		Setup.TestVars testVars = Setup.getTestVars();
		ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
		opts.realtimeHost = "un.reachable.host.example.com";
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.failed);

		/* Verify that,
		 *   - connectionManager is connected
		 *   - connectionManager is connected to the host without any fallback
		 */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.failed));
		assertThat(connectionManager.getHost(), is(equalTo(opts.realtimeHost)));
	}

	/**
	 * <p>
	 * Verifies that the {@code ConnectionManager} first checks if an internet connection is
	 * available by issuing a GET request to https://internet-up.ably-realtime.com/is-the-internet-up.txt,
	 * when In the case of an error necessitating use of an alternative host (see RTN17d).
	 * </p>
	 * <p>
	 * Spec: RTN17c
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void connectionmanager_fallback_none_withoutconnection() throws AblyException {
		Setup.TestVars testVars = Setup.getTestVars();
		ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
		opts.realtimeHost = "un.reachable.host";
		opts.autoConnect = false;
		AblyRealtime ably = new AblyRealtime(opts);
		Connection connection = Mockito.mock(Connection.class);

		ConnectionManager connectionManager = new ConnectionManager(ably, connection) {
			@Override
			protected boolean checkConnectivity() {
				return false;
			}
		};

		connectionManager.connect();

		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.failed);

		/* Verify that,
		 *   - connectionManager is failed
		 *   - connectionManager is didn't applied any fallback behavior
		 */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.failed));
		assertThat(connectionManager.getHost(), is(equalTo(opts.realtimeHost)));
	}

	/**
	 * Verifies that fallback behaviour doesn't apply,
	 * when {@link ClientOptions#fallbackHosts} array is empty.
	 * <p>
	 * Spec: RTN17c
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void connectionmanager_empty_fallback_array() throws AblyException {
		final TestTransportFactory testFactory = new TestTransportFactory();

		/* Init ably with empty array of fallbackHosts */
		Setup.TestVars testVars = Setup.getTestVars();
		DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
		testVars.fillInOptions(opts);
		opts.autoConnect = false;
		opts.environment = null;
		opts.fallbackHosts = new String[0];
		opts.debugTransportFactory = testFactory;
		/* Use a non-reachable port number */
		opts.tls = true;
		opts.tlsPort = 1234;
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		connectionManager.connect();
		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.failed);

		/* Verify that,
		 *   - connectionManager is failed
		 *   - connectionManager is didn't applied any fallback behavior
		 *   - connection attempt against default primary host
		 */
		List<String> hostStack = testFactory.getHostArgumentStack();
		assertThat("Unexpected connection state", connectionManager.getConnectionState().state, is(ConnectionState.failed));
		assertThat("Unexpected count of connect attempts", hostStack.size(), is(1));
		assertThat("Unexpected host", hostStack.get(0), is(equalTo(Defaults.HOST_REALTIME)));
	}

	/**
	 * <p>
	 * Verifies that fallback behaviour is applied and HTTP client is using same fallback
	 * endpoint, when the default realtime.ably.io endpoint is being used and has not been
	 * overridden, and a fallback is applied
	 * </p>
	 * <p>
	 * Spec: RTN17b, RTN17c
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void connectionmanager_fallback_applied() throws AblyException {
		Setup.TestVars testVars = Setup.getTestVars();
		ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
		// Use a host that supports fallback
		opts.realtimeHost = Defaults.HOST_REALTIME;
		// Use a non-reachable port number
		opts.tls = true;
		opts.tlsPort = 1234;
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.failed);

		/* Verify that,
		 *   - connectionManager is connected
		 *   - connectionManager is connected to a fallback host
		 *   - Ably http client is also using the same fallback host
		 */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.failed));
		assertThat(connectionManager.getHost(), is(not(equalTo(opts.realtimeHost))));
		assertThat(ably.http.getHost(), is(equalTo(connectionManager.getHost())));
	}

	/**
	 * Verifies that every connection attempt is first attempted to the default primary host,
	 * even if a previous connection attempt to that endpoint has failed.
	 * <p>
	 * Spec: RTN17a
	 * </p>
	 */
	@Test
	public void connectionmanager_reconnect_default_endpoint() throws AblyException {
		final TestTransportFactory testFactory = new TestTransportFactory();

		/* Init ably with default primary host */
		Setup.TestVars testVars = Setup.getTestVars();
		DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
		testVars.fillInOptions(opts);
		opts.autoConnect = false;
		opts.environment = null;
		/* Use a non-reachable port number */
		opts.tls = true;
		opts.tlsPort = 1234;
		opts.debugTransportFactory = testFactory;
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		/* Connect */
		connectionManager.connect();
		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.failed);

		/* Verify that,
		 *   - connectionState is failed
		 *   - first connection attempt against default primary host */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.failed));
		assertThat("Unexpected default primary host", testFactory.getHostArgumentStack().get(0), is(equalTo(Defaults.HOST_REALTIME)));

		/* Clear previously captured hosts */
		testFactory.getHostArgumentStack().clear();

		/* Reconnect */
		ably.connection.connect();
		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.failed);

		/* Verify that first connection attempt against default primary host */
		assertThat("Unexpected default primary host", testFactory.getHostArgumentStack().get(0), is(equalTo(Defaults.HOST_REALTIME)));
	}

	/**
	 * Verifies that every connection attempt is to overridden endpoint,
	 * even if a previous connection attempt to that endpoint has failed.
	 * <p>
	 * Spec: RTN17a
	 * </p>
	 */
	@Test
	public void connectionmanager_reconnect_overridden_endpoint() throws AblyException {
		final TestTransportFactory testFactory = new TestTransportFactory();
		final String givenHost = "un.reachable.host";

		/* Init ably with custom host */
		Setup.TestVars testVars = Setup.getTestVars();
		DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
		testVars.fillInOptions(opts);
		opts.realtimeHost = givenHost;
		opts.autoConnect = false;
		opts.environment = null;
		opts.debugTransportFactory = testFactory;
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		/* Connect */
		connectionManager.connect();
		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.failed);

		/* Verify that,
		 *   - connectionState is failed
		 *   - connection attempt against given host */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.failed));
		assertThat("Unexpected host", testFactory.getHostArgumentStack().get(0), is(equalTo(givenHost)));

		/* Clear previously captured hosts */
		testFactory.getHostArgumentStack().clear();

		/* Reconnect */
		ably.connection.connect();
		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.failed);

		/* Verify that connection attempt against given host */
		assertThat("Unexpected host", testFactory.getHostArgumentStack().get(0), is(equalTo(givenHost)));
	}
}