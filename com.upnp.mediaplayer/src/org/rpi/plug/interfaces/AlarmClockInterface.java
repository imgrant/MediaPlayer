package org.rpi.plug.interfaces;

import net.xeoh.plugins.base.Plugin;

public interface AlarmClockInterface extends Plugin {
	public String createSleepTimer(String value);
	
	public String cancelSleepTimer();
	
	public String getSleepTimer();
}
