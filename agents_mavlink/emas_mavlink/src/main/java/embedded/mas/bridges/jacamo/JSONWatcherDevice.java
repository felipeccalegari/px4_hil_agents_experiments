/**
 * This class represents an interface with a microcontroller whose sensor data follow the JSON format. 
 JSONWatcherDevice only receive data/beliefs when it sends a request to the microcontroller*/

package embedded.mas.bridges.jacamo;
import embedded.mas.bridges.javard.MicrocontrollerMonitor;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import embedded.mas.exception.PerceivingException;
import jason.asSemantics.Unifier;
import jason.asSyntax.Atom;
import jason.asSyntax.Literal;

public class JSONWatcherDevice extends SerialDevice implements IDevice {
	
	// Keep only the latest value for each belief functor so multiple MAVLink perceptions can coexist.
	private final Map<String, Literal> latestBeliefs = Collections.synchronizedMap(new LinkedHashMap<String, Literal>());
	
	public JSONWatcherDevice(Atom id, IPhysicalInterface microcontroller) {
		super(id, microcontroller);	
		// Changed to use the new direct-update constructor instead of sharing a belief list.
		MicrocontrollerMonitor microcontrollerMonitor = new MicrocontrollerMonitor(this, this.getMicrocontroller());
		microcontrollerMonitor.start();
	}
	
	@Override
	public Collection<Literal> getPercepts() throws PerceivingException{
		// Return the latest belief of each type, then clear the snapshot for the next cycle.
		synchronized (latestBeliefs) {
			Collection<Literal> percepts = new java.util.ArrayList<Literal>(latestBeliefs.values());
			latestBeliefs.clear();
			return percepts;
		}
	}

	// New helper used by MicrocontrollerMonitor to publish the newest parsed beliefs directly.
	public void updateLatestBeliefs(Collection<Literal> percepts) {
		if (percepts == null || percepts.isEmpty()) {
			return;
		}
		synchronized (latestBeliefs) {
			for (Literal percept : percepts) {
				if (percept != null) {
					latestBeliefs.put(percept.getFunctor(), percept);
				}
			}
		}
	}

	@Override
	public boolean execEmbeddedAction(String actionName, Object[] args, Unifier un) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public IPhysicalInterface getMicrocontroller() {
		return (IPhysicalInterface) this.microcontroller;
	}
	
	
}
