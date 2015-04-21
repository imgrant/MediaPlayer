package org.rpi.airplay;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.rpi.utils.NetworkUtils;
import org.rpi.utils.SecUtils;
import org.rpi.utils.Utils;

/**
 * LaunchThread class which starts services
 * 
 * @author bencall
 * 
 */
public class AirPlayThread extends Thread {
	private Logger log = Logger.getLogger(this.getClass());

	private List<BonjourEmitter> emitter = new ArrayList<BonjourEmitter>();
	// private ServerSocket servSock = null;
	private String name;
	private String password;
	private byte[] hwAddr = null;
	
	private EventLoopGroup group = new NioEventLoopGroup();
	private EventLoopGroup workerGroup = new NioEventLoopGroup(1);

	// private static ChannelGroup s_allChannels = new DefaultChannelGroup();

	/**
	 * Global executor service. Used e.g. to initialize the various netty
	 * channel factories
	 */
	//public static final ExecutorService ExecutorService = Executors.newCachedThreadPool();



	/**
	 * Constructor
	 * 
	 * @param name
	 */
	public AirPlayThread(String name) {
		super();
		this.name = name;
	}

	/**
	 * Constructor
	 * 
	 * @param name
	 */
	public AirPlayThread(String name, String pass) {
		super();
		this.name = name;
		this.password = pass;
	}

	public void run() {
		log.info("Starting AirPlay receiver");
		ResourceLeakDetector.setLevel(Level.DISABLED);
		// For the Raspi we have to do this now, because for some reason it is
		// very slow the first time it is run and if we run it when we get an
		// AirPlay connection the connection times out.
		log.debug("Creating BouncyCastleProvider...");
		Security.addProvider(new BouncyCastleProvider());
		log.debug("  ...BouncyCastleProvider created");
		byte[] test = new byte[] { (byte) 0xe0, 0x4f, (byte) 0xd0, 0x20, (byte) 0xea, 0x3a, 0x69, 0x10, (byte) 0xa2, (byte) 0xd8, 0x08, 0x00, 0x2b, 0x30, 0x30, (byte) 0x9d };
		SecUtils.encryptRSA(test);

		int port = 5004;
		try {
			// DNS Emitter (Bonjour)
			hwAddr = NetworkUtils.getMacAddress();
			boolean bPassword = false;
			if (!Utils.isEmpty(password)) {
				log.debug("An AirPlay password is configured");
				bPassword = true;
			}
			AudioSessionHolder.getInstance().setHardWareAddress(hwAddr);

			ServerBootstrap b = new ServerBootstrap();
			
			b.group(group,workerGroup);

			b.channel(NioServerSocketChannel.class);
			b.option(ChannelOption.SO_REUSEADDR, true);
			b.option(ChannelOption.TCP_NODELAY, true);
			b.option(ChannelOption.SO_KEEPALIVE, true);
			b.childHandler(new RtspServerInitializer());
			ChannelFuture channel = b.bind(new InetSocketAddress(port)).sync();

			for (final NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (iface.isLoopback())
					continue;
				if (iface.isPointToPoint())
					continue;
				if (!iface.isUp())
					continue;

				for (final InetAddress addr : Collections.list(iface.getInetAddresses())) {
					if (!(addr instanceof Inet4Address) && !(addr instanceof Inet6Address))
						continue;

					try {
						/* Create mDNS responder for address */
						BonjourEmitter be = new BonjourEmitter(name, NetworkUtils.toHexString(hwAddr), port, bPassword, addr);
						emitter.add(be);
						log.debug("Registered AirPlay service '" + name + "' on " + addr);
					} catch (final Throwable e) {
						log.error("Failed to publish AirPlay service on " + addr.toString(), e);
					}
				}
			}
			
			while (!Thread.interrupted()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// ignore
				}
			}
		} catch (Exception e) {
			log.error(e);

		} finally {
			try {
				closeBonjourServices();
				closeRTSPServer();
			} catch (Exception e) {
				log.error(e);
			}
		}
	}

	private synchronized void closeBonjourServices() {
		try {
			for (BonjourEmitter be : emitter) {
				try {
					be.stop();
				} catch (Exception e) {
					log.error("Error stopping Bonjour service", e);
				}
			}

			log.info("AirPlay service stopped");
		} catch (Exception e) {
			log.error(e);
		}
	}

	private synchronized void closeRTSPServer() {
		log.debug("Closing RTSP server");
		
		try
		{
			workerGroup.shutdownGracefully();
		}
		catch(Exception e)
		{
			log.error("Error closing Worker Thread");
		}
		try
		{
			group.shutdownGracefully();
		}
		catch(Exception e)
		{
			log.error("Error closing Boss Thread");
		}
		
		try
		{
			workerGroup.terminationFuture().sync();
		}
		catch(Exception e)
		{
			log.error("Error closing Worker Future");
		}
		
		try
		{
			group.terminationFuture().sync();
		}
		catch(Exception e)
		{
			log.error("Error closing Boss Future");
		}
	}

	/**
	 * Stop Our Thread
	 */
	public synchronized void stopThread() {
		log.debug("Stopping AirPlay service thread");
		closeBonjourServices();
		closeRTSPServer();
		//this.interrupt();
	}
}