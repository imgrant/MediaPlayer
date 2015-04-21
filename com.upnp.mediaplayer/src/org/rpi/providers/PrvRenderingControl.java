package org.rpi.providers;

import org.apache.log4j.Logger;
import org.openhome.net.device.ActionError;
import org.openhome.net.device.DvDevice;
import org.openhome.net.device.IDvInvocation;
import org.openhome.net.device.providers.DvProviderUpnpOrgRenderingControl1;
import org.rpi.player.PlayManager;
import org.rpi.player.events.EventBase;
import org.rpi.player.events.EventMuteChanged;
import org.rpi.player.events.EventVolumeChanged;
import org.rpi.utils.Utils;

import java.util.Observable;
import java.util.Observer;

public class PrvRenderingControl extends DvProviderUpnpOrgRenderingControl1 implements Observer, IDisposableDevice {

	private Logger log = Logger.getLogger(PrvRenderingControl.class);
	private String isMute = "0";
	private String volume = "100";

	public PrvRenderingControl(DvDevice iDevice) {
		super(iDevice);
		log.debug("Creating custom DLNA RenderingControl service");
		enablePropertyLastChange();
		createEvent();
		// setPropertyLastChange("<Event xmlns = \"urn:schemas-upnp-org:metadata-1-0/RCS/\"> <InstanceID val=\"0\"> <Volume val=\"100\" Channel=\"RF\"/> <Volume val=\"100\" Channel=\"LF\"/> </InstanceID></Event>");
		enableActionSetLoudness();
		enableActionSetMute();
		enableActionSetVolume();
		enableActionSetVolumeDB();
		enableActionGetLoudness();
		enableActionGetMute();
		enableActionGetVolume();
		enableActionGetVolumeDB();
		enableActionGetVolumeDBRange();
		enableActionListPresets();
		enableActionSelectPreset();
		PlayManager.getInstance().observeVolumeEvents(this);

	}

	private void createEvent() {
		getMuteAsString(PlayManager.getInstance().getMute());
		long v = PlayManager.getInstance().getVolume();
		if(v < 0)
			v = 0;
		volume = "" + v;
		StringBuilder sb = new StringBuilder();
		sb.append("<Event xmlns=\"urn:schemas-upnp-org:metadata-1-0/RCS/\">");
		sb.append("<InstanceID val=\"0\">");
		sb.append("<Loudness val=\"\" />");
		sb.append("<Volume channel=\"Master\" val=\"" + volume + "\" />");
		sb.append("<Mute channel=\"Master\" val=\"" + isMute + "\" />");
		sb.append("<PresetNameList val=\"FactoryDefaults\" />");
		sb.append("<VolumeDB channel=\"Master\" val=\"0\" />");
		sb.append("</InstanceID>");
		sb.append("</Event>");
		setPropertyLastChange(sb.toString());
	}

	// #####################################################################################################################
	protected String listPresets(IDvInvocation paramIDvInvocation, long paramLong) {
		log.debug("listPresets: " + paramLong + Utils.getLogText(paramIDvInvocation));
		return "FactoryDefaults,InstallationDefaults";
	}

	@Override
	protected void selectPreset(IDvInvocation paramIDvInvocation, long paramLong, String paramString) {
		log.debug("SelectPreset: " + paramString + Utils.getLogText(paramIDvInvocation));
	}

	@Override
	protected int getVolumeDB(IDvInvocation paramIDvInvocation, long paramLong, String paramString) {
		log.debug("getVolumeDB" + Utils.getLogText(paramIDvInvocation));
		return 0;
	}

	@Override
	protected void setVolumeDB(IDvInvocation paramIDvInvocation, long paramLong, String paramString, int paramInt) {
		checkValue(paramInt);
		log.debug("setVolumeDB: " + paramInt + Utils.getLogText(paramIDvInvocation));
	}

	@Override
	protected GetVolumeDBRange getVolumeDBRange(IDvInvocation paramIDvInvocation, long paramLong, String paramString) {
		log.debug("getVolumeDBRange" + Utils.getLogText(paramIDvInvocation));
		GetVolumeDBRange dbr = new GetVolumeDBRange(0, 0);
		return dbr;
	}

	@Override
	protected boolean getLoudness(IDvInvocation paramIDvInvocation, long paramLong, String paramString) {
		log.debug("getLoudness" + Utils.getLogText(paramIDvInvocation));
		return false;
	}

	@Override
	protected void setLoudness(IDvInvocation paramIDvInvocation, long paramLong, String paramString, boolean paramBoolean) {
		log.debug("setLoudness: " + paramBoolean + Utils.getLogText(paramIDvInvocation));
	}

	// #####################################################################################################################

	@Override
	public long getVolume(IDvInvocation paramIDvInvocation, long paramLong, String paramString) {
		log.debug("GetVolume: " + paramString + Utils.getLogText(paramIDvInvocation));
		try {
			long v = PlayManager.getInstance().getVolume();
			if(v<0)
				v=0;
			volume = "" + v;
			log.debug("Returning Volume: " + v);
			return v;
		} catch (Exception e) {
			log.error("Error GetVolume", e);
		}
		return 100;
	}

	@Override
	protected void setVolume(IDvInvocation paramIDvInvocation, long paramLong1, String mixer, long volume) {
		checkValue(volume);
		log.debug("setVolume" + Utils.getLogText(paramIDvInvocation));
		PlayManager.getInstance().setVolume(volume);

	}


	@Override
	public boolean getMute(IDvInvocation paramIDvInvocation, long paramLong, String paramString) {
		log.debug("getMute" + Utils.getLogText(paramIDvInvocation));
		return PlayManager.getInstance().getMute();
	}

	@Override
	public void setMute(IDvInvocation paramIDvInvocation, long paramLong, String paramString, boolean bMute) {
		log.debug("setVolume" + Utils.getLogText(paramIDvInvocation));
		PlayManager.getInstance().setMute(bMute);
	}

	private void updateVolume() {
		StringBuffer sb = new StringBuffer();
		sb.append("<Event xmlns=\"urn:schemas-upnp-org:metadata-1-0/RCS/\">");
		sb.append("<InstanceID val=\"0\">");
		sb.append("<Volume Channel=\"Master\" val=\"");
		sb.append(volume);
		sb.append("\"/>");
		sb.append("<VolumeDB Channel=\"Master\" val=\"0\" />");
		sb.append("</InstanceID></Event>");
		log.debug("VolumeString: " + sb.toString());
		setPropertyLastChange(sb.toString());
	}

	private void updateMute() {
		StringBuffer sb = new StringBuffer();
		sb.append("<Event xmlns=\"urn:schemas-upnp-org:metadata-1-0/RCS/\">");
		sb.append("<InstanceID val=\"0\">");
		sb.append("<Mute Channel=\"Master\" val=\"");
		sb.append(isMute);
		sb.append("\"/>");
		sb.append("</InstanceID></Event>");
		log.debug("MuteString: " + sb.toString());
		setPropertyLastChange(sb.toString());
	}

	@Override
	public void update(Observable arg0, Object obj) {
		EventBase e = (EventBase) obj;
		switch (e.getType()) {
		case EVENTVOLUMECHANGED:
			EventVolumeChanged ev = (EventVolumeChanged) e;
			String mVolume = "" + ev.getVolume();
			if (!mVolume.equalsIgnoreCase(volume)) {
				volume = mVolume;
				// updateVolume();
				createEvent();
			}
			// updateVolume(ev.getVolume());
			// updateVolume();
			break;
		case EVENTMUTECHANGED:
			EventMuteChanged em = (EventMuteChanged) e;
			// updateMute();
			String test = getMuteAsString(em.isMute());
			if (!test.equalsIgnoreCase(isMute)) {
				isMute = test;
				// updateMute();
				createEvent();
			}

			break;
		}

	}

	private String getMuteAsString(boolean mute) {
		String mIsMute = "0";
		if (mute) {
			mIsMute = "1";
		}
		return mIsMute;
	}
	
	private void checkValue(int value)
	{
		long l = (long)value;
		checkValue(l);
	}
	private void checkValue(long value) {
		if (value < 0 || value > 100)
			throw new ActionError("Specified Value: " + value + " Must be &lt;= 100");
	}

    @Override
    public String getName() {
        return "RenderingControl";
    }

}
