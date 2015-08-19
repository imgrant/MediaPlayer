package org.rpi.main;

import org.apache.log4j.Logger;
import org.openhome.net.core.DebugLevel;
import org.openhome.net.core.IMessageListener;
import org.rpi.config.Config;

public class OpenHomeLogger implements IMessageListener {
	
	private static Logger log = Logger.getLogger(OpenHomeLogger.class);
	
	@Override
	public void message(String message) {
		log.trace("[OpenHome]: " + message.replace("\r", "").replace("\n", ""));	
	}

}
