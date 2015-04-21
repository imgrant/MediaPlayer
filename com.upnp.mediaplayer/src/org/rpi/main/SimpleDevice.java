package org.rpi.main;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.apache.log4j.Logger;
import org.openhome.net.core.DebugLevel;
import org.openhome.net.core.DeviceStack;
import org.openhome.net.core.IMessageListener;
import org.openhome.net.core.InitParams;
import org.openhome.net.core.Library;
import org.openhome.net.device.DvDevice;
import org.openhome.net.device.DvDeviceFactory;
import org.openhome.net.device.IDvDeviceListener;
import org.openhome.net.device.IResourceManager;
import org.openhome.net.device.IResourceWriter;
import org.rpi.airplay.AirPlayThread;
import org.rpi.alarm.Alarm;
import org.rpi.config.Config;
import org.rpi.http.HttpServerGrizzly;
import org.rpi.os.OSManager;
import org.rpi.player.PlayManager;
import org.rpi.player.events.EventBase;
import org.rpi.player.events.EventSourceChanged;
import org.rpi.plugingateway.PluginGateWay;
import org.rpi.providers.IDisposableDevice;
import org.rpi.providers.PrvAVTransport;
import org.rpi.providers.PrvConnectionManager;
import org.rpi.providers.PrvCredentials;
import org.rpi.providers.PrvInfo;
import org.rpi.providers.PrvPlayList;
import org.rpi.providers.PrvProduct;
import org.rpi.providers.PrvRadio;
import org.rpi.providers.PrvReceiver;
import org.rpi.providers.PrvRenderingControl;
import org.rpi.providers.PrvSongcast;
import org.rpi.providers.PrvTime;
import org.rpi.providers.PrvVolume;
import org.rpi.radio.ChannelReaderJSON;
import org.rpi.sources.Source;
import org.rpi.sources.SourceReader;
import org.rpi.utils.NetworkUtils;
import org.rpi.web.longpolling.PlayerStatus;

public class SimpleDevice implements IResourceManager, IDvDeviceListener, IMessageListener, Observer {

	private Logger log = Logger.getLogger(SimpleDevice.class);
	private DvDevice iDevice = null;
	private Library lib = null;

	private PrvConnectionManager iConnectionManager = null;
	private PrvVolume iVolume = null;
	private PrvPlayList iPlayList = null;
	private PrvProduct iProduct = null;
	private PrvInfo iInfo = null;
	private PrvTime iTime = null;
	private PrvRadio iRadio = null;
	private PrvReceiver iReceiver = null;
	private PrvAVTransport iAVTransport = null;
	private PrvRenderingControl iRenderingControl = null;
	private PrvSongcast iSongcastSender = null;
	private PrvCredentials iCredentials = null;
	private HttpServerGrizzly httpServer = null;

	private AirPlayThread airplay = null;

	private PlayManager iPlayer = PlayManager.getInstance();

	// private PluginManager pm = null;

	/***
	 * Constructor for our Simple Device
	 */
	public SimpleDevice() {
		PluginGateWay.getInstance().setSimpleDevice(this);
		// System.loadLibrary("ohNetJni");
		// Call the OSManager to set our path to the libohNet libraries
		OSManager.getInstance();

		InitParams initParams = new InitParams();
		initParams.setLogOutput(new OpenHomeLogger());
		if (Config.getInstance().getOpenhomePort() > 0) {
			initParams.setDvServerPort(Config.getInstance().getOpenhomePort());
		}
		// initParams.setDvEnableBonjour();
		initParams.setFatalErrorHandler(this);

		lib = Library.create(initParams);
		lib.setDebugLevel(getDebugLevel(Config.getInstance().getOpenhomeLogLevel()));

		DeviceStack ds = lib.startDv();

		iDevice = new DvDeviceFactory(ds).createDeviceStandard(Config.getInstance().getUdn(), this);
		log.debug("Created OpenHome device with UDN: " + iDevice.getUdn());
		iDevice.setAttribute("Upnp.IconList", this.constructIconList(Config.getInstance().getUdn()));

		String friendly_name = Config.getInstance().getProductRoom() + ":" + Config.getInstance().getProductName();

		// iDevice.setAttribute("Upnp.Domain", "openhome-org");
		iDevice.setAttribute("Upnp.Domain", "upnp.org");
		iDevice.setAttribute("Upnp.Type", "MediaRenderer");
		iDevice.setAttribute("Upnp.Version", "1");
		iDevice.setAttribute("Upnp.FriendlyName", friendly_name);
		iDevice.setAttribute("Upnp.Manufacturer", Config.getInstance().getManufacturerName());
		iDevice.setAttribute("Upnp.ManufacturerUrl", Config.getInstance().getManufacturerUrl());
		iDevice.setAttribute("Upnp.ModelName", Config.getInstance().getProductName());
		iDevice.setAttribute("Upnp.ModelNumber", Config.getInstance().getVersion());
		iDevice.setAttribute("Upnp.ModelDescription", Config.getInstance().getProductInfo());
		iDevice.setAttribute("Upnp.ModelUrl", Config.getInstance().getProductUrl());
		iDevice.setAttribute("Upnp.SerialNumber", Config.getInstance().getSerialNumber());
		iDevice.setAttribute("Upnp.PresentationUrl", "http://" + NetworkUtils.getIPAddress() + ":" + Config.getInstance().getWebServerPort());
		iConnectionManager = new PrvConnectionManager(iDevice);
		iProduct = new PrvProduct(iDevice);
		iVolume = new PrvVolume(iDevice);
		iPlayList = new PrvPlayList(iDevice);
		iInfo = new PrvInfo(iDevice);
		iTime = new PrvTime(iDevice);
		iRadio = new PrvRadio(iDevice);

		if (Config.getInstance().isMediaplayerEnableReceiver()) {
			iReceiver = new PrvReceiver(iDevice);
		}
		if (Config.getInstance().isMediaplayerEnableAVTransport()) {
			iAVTransport = new PrvAVTransport(iDevice);
			iRenderingControl = new PrvRenderingControl(iDevice);
		}
		
		iSongcastSender = new PrvSongcast(iDevice);
		iCredentials = new PrvCredentials(iDevice);
		// updateRadioList();

		try {
			SourceReader sr = new SourceReader();
			ConcurrentHashMap<String, Source> sources = sr.getSources();
			if (sources.size() == 0) {
				Source playlist = new Source("PlayList", "Playlist", "-99", true);
				sources.put(playlist.getName(), playlist);
				Source radio = new Source("Radio", "Radio", "-99", true);
				sources.put(radio.getName(), radio);
				if (Config.getInstance().isMediaplayerEnableReceiver()) {
					Source reciever = new Source("Receiver", "Receiver", "-99", true);
					sources.put(reciever.getName(), reciever);
				}
				Source airplay = new Source("AirPlay", "NetAux", "-99", false);
				sources.put(airplay.getName(), airplay);
				Source upnp = new Source(friendly_name, "UpnpAv", "-99", false);
				sources.put(friendly_name, upnp);
			}
			PluginGateWay.getInstance().setSources(sources);
			PluginGateWay.getInstance().setDefaultSourcePin(sr.getDefaultPin());
			PluginGateWay.getInstance().setStandbyPin(sr.getStandbyPin());
			for (String key : sources.keySet()) {
				Source s = sources.get(key);
				log.debug("Adding source: " + s.toString());
				iProduct.addSource(Config.getInstance().getProductRoom(), s.getName(), s.getType(), s.isVisible());
			}
			iProduct.updateCurrentSource();

		} catch (Exception e) {
			log.error("Unable to load input sources");
		}

		iDevice.setEnabled();
		iProduct.setSourceByName("PlayList");

		if (Config.getInstance().isWebWerverEnabled()) {
			log.info("Starting web server on port " + Config.getInstance().getWebServerPort());
			httpServer = new HttpServerGrizzly(Config.getInstance().getWebServerPort());
		} else {
			log.warn("Web server option is set to false, not starting");
		}

		if (Config.getInstance().getMediaplayerStartupVolume() >= 0) {
			log.info("Setting initial volume to " + Config.getInstance().getMediaplayerStartupVolume());
			PlayManager.getInstance().setVolume(Config.getInstance().getMediaplayerStartupVolume());
		} else {
			log.info("Setting initial volume to 100");
			PlayManager.getInstance().setVolume(100);
		}
		
		try {
			//Force an updateRadioList to enable Kazoo to find Radio Stations..
			updateRadioList();
		} catch (Exception e) {
			log.error("Unable to update radio list");
		}

		if (Config.getInstance().isAirPlayEnabled()) {
			airplay = new AirPlayThread(friendly_name);
			airplay.start();
		}



		PlayerStatus.getInstance();
		Alarm.getInstance();
		OSManager.getInstance().loadPlugins();
	}

	protected SimpleDevice(boolean test) {
		// this constructor is just for test purposes...
		// do not remove it
	}

	protected String constructIconList(String deviceName) {
		StringBuffer sb = new StringBuffer();
		sb.append(this.constructIconEntry(deviceName, "image/png", ".png", "240"));
		sb.append(this.constructIconEntry(deviceName, "image/jpeg", ".jpg", "240"));
		sb.append(this.constructIconEntry(deviceName, "image/png", ".png", "120"));
		sb.append(this.constructIconEntry(deviceName, "image/jpeg", ".jpg", "120"));
		sb.append(this.constructIconEntry(deviceName, "image/png", ".png", "50"));
		sb.append(this.constructIconEntry(deviceName, "image/jpeg", ".jpg", "50"));

		return sb.toString();
	}

	protected String constructIconEntry(String deviceName, String mimeType, String fileSuffix, String size) {
		StringBuffer sb = new StringBuffer();

		sb.append("<icon>");
		sb.append("<mimetype>");
		sb.append(mimeType);
		sb.append("</mimetype>");
		sb.append("<width>");
		sb.append(size);
		sb.append("</width>");
		sb.append("<height>");
		sb.append(size);
		sb.append("</height>");
		sb.append("<depth>24</depth>");
		sb.append("<url>/" + deviceName + "/Upnp/resource/org/rpi/image/");
		sb.append(Config.getInstance().getPackagedImageFilename());
		sb.append(size);
		sb.append(fileSuffix);
		sb.append("</url>");
		sb.append("</icon>");

		return sb.toString();
	}

	private int getDebugLevel(String sLevel) {

		if (sLevel.equalsIgnoreCase("NONE")) {
			return DebugLevel.None.intValue();
		} else if (sLevel.equalsIgnoreCase("TRACE")) {
			return DebugLevel.Trace.intValue();
		} else if (sLevel.equalsIgnoreCase("NETWORK")) {
			return DebugLevel.Network.intValue();
		} else if (sLevel.equalsIgnoreCase("TIMER")) {
			return DebugLevel.Timer.intValue();
		} else if (sLevel.equalsIgnoreCase("SsdpMulticast")) {
			return DebugLevel.SsdpMulticast.intValue();
		} else if (sLevel.equalsIgnoreCase("SsdpUnicast")) {
			return DebugLevel.SsdpUnicast.intValue();
		} else if (sLevel.equalsIgnoreCase("Http")) {
			return DebugLevel.Http.intValue();
		} else if (sLevel.equalsIgnoreCase("Device")) {
			return DebugLevel.Device.intValue();
		} else if (sLevel.equalsIgnoreCase("XmlFetch")) {
			return DebugLevel.XmlFetch.intValue();
		} else if (sLevel.equalsIgnoreCase("Service")) {
			return DebugLevel.Service.intValue();
		} else if (sLevel.equalsIgnoreCase("Event")) {
			return DebugLevel.Event.intValue();
		} else if (sLevel.equalsIgnoreCase("Topology")) {
			return DebugLevel.Topology.intValue();
		} else if (sLevel.equalsIgnoreCase("DvInvocation")) {
			return DebugLevel.DvInvocation.intValue();
		} else if (sLevel.equalsIgnoreCase("DvInvocation")) {
			return DebugLevel.DvInvocation.intValue();
		} else if (sLevel.equalsIgnoreCase("DvEvent")) {
			return DebugLevel.DvEvent.intValue();
		} else if (sLevel.equalsIgnoreCase("DvWebSocket")) {
			return DebugLevel.DvWebSocket.intValue();
		} else if (sLevel.equalsIgnoreCase("Bonjour")) {
			return DebugLevel.Bonjour.intValue();
		} else if (sLevel.equalsIgnoreCase("DvDevice")) {
			return DebugLevel.DvDevice.intValue();
		} else if (sLevel.equalsIgnoreCase("Error")) {
			return DebugLevel.Error.intValue();
		} else if (sLevel.equalsIgnoreCase("All")) {
			return DebugLevel.All.intValue();
		} else if (sLevel.equalsIgnoreCase("Verbose")) {
			return DebugLevel.Verbose.intValue();
		}
		return DebugLevel.None.intValue();
	}

	public void attachShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				log.info("MediaPlayer shutdown initiated");
				dispose();
			}
		});
		log.trace("Shutdown hook attached");
	}

	public void dispose() {

		try {
			if (airplay != null) {
				airplay.stopThread();
			}
		} catch (Exception e) {
			log.error("Error shutting down AirPlay service");
		}

		try {
			if (httpServer != null) {
				httpServer.shutdown();
			}
		} catch (Exception e) {
			log.error("Error stopping web server");
		}

		try {
			OSManager.getInstance().dispose();
		} catch (Exception e) {

		}

		if (iPlayer != null) {
			log.info("Destroying IPlayer");
			try {
				iPlayer.destroy();
				log.info("Destroyed IPlayer");
			} catch (Exception e) {
				log.error("Error Destroying IPlayer", e);
			}
		}

		if (iDevice != null) {
			try {
				iDevice.destroy();
				log.trace("Device destroyed");
			} catch (Exception e) {
				log.error("Error destroying device", e);
			}
		}

		this.disposeDevice(iConnectionManager);
		this.disposeDevice(iPlayList);
		this.disposeDevice(iVolume);
		this.disposeDevice(iProduct);
		this.disposeDevice(iInfo);
		this.disposeDevice(iTime);
		this.disposeDevice(iRadio);
		this.disposeDevice(iReceiver);
		this.disposeDevice(iAVTransport);
		this.disposeDevice(iRenderingControl);
		this.disposeDevice(iSongcastSender);
		this.disposeDevice(iCredentials);

		if (lib != null) {
			try {
				lib.close();
				log.trace("Device stack closed");
			} catch (Exception e) {
				log.error("Error closing device stack", e);
			}
		}

	}

	private void disposeDevice(IDisposableDevice device) {
		if (device != null) {
			String name = device.getName();
			log.info("Dispose " + name);

			try {
				device.dispose();
				log.info("Disposed " + name);
			} catch (Exception e) {
				log.error("Error Disposing " + name, e);
			}

		}
	}

	@Override
	public void writeResource(String resource_name, int arg1, List<String> arg2, IResourceWriter writer) {
		log.info("writeResource Called: " + resource_name);
		try {
			resource_name = "/" + resource_name;
			URL url = this.getClass().getResource(resource_name);
			BufferedImage image = ImageIO.read(url);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			String fileType = "image/png";
			String format = "png";
			if (resource_name.toUpperCase().endsWith("JPG")) {
				format = "jpg";
				fileType = "image/jpeg";
			}
			ImageIO.write(image, format, baos);
			int length = baos.toByteArray().length;
			writer.writeResourceBegin(length, fileType);
			writer.writeResource(baos.toByteArray(), length);
			writer.writeResourceEnd();
		} catch (IOException e) {
			log.error("Error Writing Resource: " + resource_name, e);
		}

	}

	private void writeFile(String s) {
		try {
			File newTextFile = new File("C:/temp/thetextfile.txt");

			FileWriter fw = new FileWriter(newTextFile);
			fw.write(s);
			fw.close();

		} catch (IOException iox) {
			// do stuff with exception
			iox.printStackTrace();
		}
	}

	public PrvVolume getCustomVolume() {
		return iVolume;
	}

	@Override
	public void deviceDisabled() {
		log.info("Device has been Disabled");
		dispose();
	}

	@Override
	public void message(String paramString) {
		log.fatal("Fatal Error: " + paramString);
	}

	/**
	 * Get the Product Provider
	 * 
	 * @return
	 */
	public PrvProduct getProduct() {
		return iProduct;
	}

	private void updateRadioList() {
		try {
			// ChannelReaderJSON cr = new ChannelReaderJSON();
			// iRadio.addChannels(cr.getChannels());
			iRadio.getChannels();
		} catch (Exception e) {
			log.error("Error Reading Radio Channels");
		}
	}

	@Override
	public void update(Observable arg0, Object event) {
		EventBase base = (EventBase) event;
		switch (base.getType()) {
		case EVENTSOURCECHANGED:
			EventSourceChanged ev = (EventSourceChanged) event;
			if (ev.getSourceType().equalsIgnoreCase("RADIO")) {
				updateRadioList();
			} else {

			}
			break;
		}
	}
}
