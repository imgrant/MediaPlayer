package org.rpi.main;

import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;

import org.apache.log4j.Logger;
import org.rpi.config.Config;
import org.rpi.utils.Utils;

public class StartMe {

	private static boolean stop = false;
	private static Logger log = Logger.getLogger(StartMe.class);

	// private static PluginManager pm = null;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// ResourceLeakDetector.setLevel(Level.PARANOID);
		Config.getInstance();
		boolean bInput = false;
		for (String s : args) {
			if (s.equalsIgnoreCase("-input")) {
				bInput = true;
			}
		}

		log.info("MediaPlayer version " + Config.getInstance().getVersion() + " started");
		// Attempt to load a custom product configuration here
		Config.getInstance().getCustomProductConfig();
		log.info("Room name: " + Config.getInstance().getProductRoom());
		log.info("Product name: " + Config.getInstance().getProductName());
		log.debug("UDN: " + Config.getInstance().getUdn());

		if (log.isDebugEnabled()) {
			Config.getInstance().printLoggingConfig();
			// to improve startup performance, if loglevel debug is not enabled,
			// this is not needed, right?
			log.debug("Available network interfaces:");
			try {
				Enumeration e = NetworkInterface.getNetworkInterfaces();
				while (e.hasMoreElements()) {
					NetworkInterface n = (NetworkInterface) e.nextElement();
					Enumeration ee = n.getInetAddresses();
					log.debug("  " + n.getDisplayName() + " (" + n.getName() + ")");
					while (ee.hasMoreElements()) {
						InetAddress i = (InetAddress) ee.nextElement();
						log.debug("    IP address: " + i.getHostAddress());
					}
				}
			} catch (Exception e) {
				log.error("Unable to get network interfaces");
			}
		}

		// Do we need to attempt to set the AudioCard
		if (Config.getInstance().isMediaplayerEnableReceiver() || Config.getInstance().isAirPlayEnabled()) {
			log.debug("Available audio devices:");
			try {
				Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
				List<String> endsWith = Config.getInstance().getJavaSoundcardSuffix();
				boolean bFoundSoundCard = false;
				for (int cnt = 0; cnt < mixerInfo.length; cnt++) {
					String mixer = mixerInfo[cnt].getName().trim();
					log.debug("  " + mixer);
					if (!bFoundSoundCard) {
						for (String endWith : endsWith) {
							if (mixer.trim().toUpperCase().endsWith(endWith.trim().toUpperCase())) {
								Config.getInstance().setJavaSoundcardName(mixer.trim());
								bFoundSoundCard = true;
							}
						}
					}
				}
				setAudioDevice();
			} catch (Exception e) {
				log.error("Unable to get audio devices");
			}
		}
		
		log.trace("Java VM version: " + System.getProperty("java.version"));
		if (log.isTraceEnabled()) {
		printSystemProperties();
		}
		
		SimpleDevice sd = new SimpleDevice();

		sd.attachShutDownHook();
		if (bInput) {
			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
			String line = "";

			try {
				while (line.equalsIgnoreCase("quit") == false) {
					line = in.readLine();
				}
				in.close();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}

		else {
			while (!stop) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					log.error("Error", e);
				}
			}
		}
		System.exit(0);
	}

	/***
	 * Print out the System Properties.
	 */
	private static void printSystemProperties() {
		log.trace("System properties:");
		Properties pr = System.getProperties();
		TreeSet propKeys = new TreeSet(pr.keySet());
		for (Iterator it = propKeys.iterator(); it.hasNext();) {
			String key = (String) it.next();
			log.trace("  " + key + "=" + pr.get(key).toString().replace("\r", "\\r").replace("\n", "\\n").replace("\t","\\t"));
		}
		Map<String, String> variables = System.getenv();
		log.trace("System variables:");
		for (Map.Entry<String, String> entry : variables.entrySet()) {
			String name = entry.getKey();
			String value = entry.getValue();
			log.trace("  " + name + "=" + value);
		}
	}

	/**
	 * Used to set the Songcast Audio Device
	 */
	private static void setAudioDevice() {
		Properties props = System.getProperties();
		String name = Config.getInstance().getJavaSoundcardName();
		if (!Utils.isEmpty(name)) {
			props.setProperty("javax.sound.sampled.SourceDataLine", name);
			log.info("Audio device: " + name);
		}
	}

}
