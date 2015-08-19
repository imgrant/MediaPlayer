package org.rpi.player.events;
/**
 * The Source has been Changed
 * @author phoyle
 *
 */

public class EventSourceChanged implements EventBase {

	@Override
	public EnumPlayerEvents getType() {
		return EnumPlayerEvents.EVENTSOURCECHANGED;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	private String name = "";
	private String type = "";

	/**
	 * 
	 * @param type
	 */
	public void setSourceType(String type) {
		this.type  = type;	
	}
	
	/**
	 * 
	 * @return
	 */
	public String getSourceType()
	{
		return type;
	}
	

}
