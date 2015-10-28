package io.ably.lib.rest;

import io.ably.lib.http.Http;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.http.PaginatedQuery;
import io.ably.lib.http.Http.ResponseHandler;
import io.ably.lib.rest.Channel;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.types.Stats;
import io.ably.lib.types.StatsReader;
import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;

import java.io.IOException;
import java.util.Collection;

/**
 * AblyRest
 * The top-level class to be instanced for the Ably REST library.
 *
 */
public class AblyRest {
	
	public final ClientOptions options;
	final String clientId;
	public final Http http;

	public final Auth auth;
	public final Channels channels;

	/**
	 * Instance the Ably library using a key only.
	 * This is simply a convenience constructor for the
	 * simplest case of instancing the library with a key
	 * for basic authentication and no other options.
	 * @param key; String key (obtained from application dashboard)
	 * @throws AblyException
	 */
	public AblyRest(String key) throws AblyException {
		this(new ClientOptions(key));
	}

	/**
	 * Instance the Ably library with the given options.
	 * @param options: see {@link io.ably.lib.types.ClientOptions} for options
	 * @throws AblyException
	 */
	public AblyRest(ClientOptions options) throws AblyException {
		/* normalise options */
		if(options == null) {
			String msg = "no options provided";
			Log.e(getClass().getName(), msg);
			throw new AblyException(msg, 400, 40000);
		}
		this.options = options;

		/* process options */
		Log.setLevel(options.logLevel);
		Log.setHandler(options.logHandler);
		Log.i(getClass().getName(), "started");
		this.clientId = options.clientId;

		http = new Http(this, options);
		auth = new Auth(this, options);
		channels = new Channels();
	}

	/**
	 * A collection of Channels associated with an Ably instance.
	 *
	 */
	public class Channels {
		public Channel get(String channelName) {
			try {
				return get(channelName, null);
			} catch (AblyException e) { return null; }
		}
		public Channel get(String channelName, ChannelOptions channelOptions) throws AblyException {
			return new Channel(AblyRest.this, channelName, channelOptions);
		}
	}

	/**
	 * Obtain the time from the Ably service.
	 * This may be required on clients that do not have access
	 * to a sufficiently well maintained time source, to provide 
	 * timestamps for use in token requests
	 * @return time in millis since the epoch
	 * @throws AblyException
	 */
	public long time() throws AblyException {
		return ((Long)http.get("/time", HttpUtils.defaultAcceptHeaders(false), null, new ResponseHandler() {
			@Override
			public Object handleResponse(int statusCode, String contentType, Collection<String> linkHeaders, byte[] body) throws AblyException {
				try {
					return (Long)Serialisation.jsonObjectMapper.readValue(body, Long[].class)[0];
				} catch (IOException e) {
					throw AblyException.fromThrowable(e);
				}
			}})).longValue();
	}

	/**
	 * Request usage statistics for this application. Returned stats
	 * are application-wide and not just relating to this instance.
	 * @param params query options: see Ably REST API documentation
	 * for available options
	 * @return a PaginatedResult of Stats records for the requested params
	 * @throws AblyException
	 */
	public PaginatedResult<Stats> stats(Param[] params) throws AblyException {
		return new PaginatedQuery<Stats>(http, "/stats", HttpUtils.defaultAcceptHeaders(false), params, StatsReader.statsResponseHandler).get();
	}

}
