package org.rpi.http;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.logging.Level;

import org.apache.log4j.Logger;
import org.apache.logging.julbridge.JULLog4jBridge;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.comet.CometAddOn;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.StaticHttpHandler;
import org.glassfish.grizzly.servlet.ServletRegistration;
import org.glassfish.grizzly.servlet.WebappContext;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.rpi.web.longpolling.LongPollingServlet;

//http://localhost:8090/MainPage.html

/**
 * Main class.
 * 
 */
public class HttpServerGrizzly {

	private Logger log = Logger.getLogger(this.getClass());
	// Base URI the Grizzly HTTP server will listen on
	public static final String BASE_URI = "http://0.0.0.0:";
	boolean run = true;
	private HttpServer server = null;

	public static final String QUERY_PATH = "/grizzly-comet/playerstatus";

	public HttpServerGrizzly(String port) {
		if (port == null || port.equalsIgnoreCase("")) {
			port = "8088";
		}
		JULLog4jBridge.assimilate();
		Grizzly.logger(this.getClass()).getParent().setLevel(Level.FINEST);
		String uri = BASE_URI + port + "/myapp/";
		log.debug("HTTP server URI: " + uri);
		// server = HttpServer.createSimpleServer("./", 8088);
		server = startServer(uri);
		log.trace("HTTP server class loader: " + HttpServer.class.getClassLoader().toString());
		log.debug("Jersey app started");

		StaticHttpHandler handler = new StaticHttpHandler("./web", "/static");
		handler.setFileCacheEnabled(false);
		server.getServerConfiguration().addHttpHandler(handler);

		final WebappContext ctx = new WebappContext("Coment", "/grizzly-comet");
		final ServletRegistration servletRegistration = ctx.addServlet("PlayerStatus", LongPollingServlet.class);
		servletRegistration.addMapping("/playerstatus");
		ctx.deploy(server);

		final Collection<NetworkListener> listeners = server.getListeners();
		for (NetworkListener listener : listeners) {
			listener.registerAddOn(new CometAddOn());
		}

		try {
			server.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Starts Grizzly HTTP server exposing JAX-RS resources defined in this
	 * application.
	 * 
	 * @return Grizzly HTTP server.
	 */
	private HttpServer startServer(String uri) {
		// create a resource config that scans for JAX-RS resources and
		// providers
		// in org.rpi.web.rest package
		final ResourceConfig rc = new ResourceConfig().packages("org.rpi.web.rest");
		return GrizzlyHttpServerFactory.createHttpServer(URI.create(uri), rc, false);
	}

	public void shutdown() {
		if (server != null) {
			try {
				server.shutdownNow();
			} catch (Exception e) {
				log.error("Error Shutting Down HTTP Server", e);
			}
		}
	}
}
