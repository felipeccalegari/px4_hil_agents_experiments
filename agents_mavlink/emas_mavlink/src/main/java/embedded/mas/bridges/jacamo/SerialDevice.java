package embedded.mas.bridges.jacamo;

import java.util.Collection;
import java.util.HashMap;

import embedded.mas.exception.EmbeddedActionException;
import embedded.mas.exception.EmbeddedActionNotFoundException;
import embedded.mas.exception.PerceivingException;
import jason.asSemantics.Unifier;
import jason.asSyntax.Atom;
import jason.asSyntax.Literal;

public class SerialDevice extends DefaultDevice {
	
	private HashMap<Literal, Literal> beliefMap = new HashMap<Literal, Literal>();

	public SerialDevice(Atom id, IPhysicalInterface microcontroller) {
		super(id, microcontroller);
	}

	@Override
	public Collection<Literal> getPercepts() throws PerceivingException {
		return null;
	}


	@Override
	public IPhysicalInterface getMicrocontroller() {
		return (IPhysicalInterface) this.microcontroller;
	}

	@Override
	public boolean execEmbeddedAction(Atom actionName, Object[] args, Unifier un) {
		try {
			EmbeddedAction action = getEmbeddedAction(actionName);

			if (action instanceof SerialEmbeddedAction) {
				String actuationName = ((SerialEmbeddedAction) action).getActuationName().toString();

				// Build the parameter string if args are present
				String message;
				if (args != null && args.length > 0) {
					// Join args with commas and remove brackets/spaces if any
					StringBuilder sb = new StringBuilder();
					for (int i = 0; i < args.length; i++) {
						sb.append(args[i].toString().trim());
						if (i < args.length - 1) sb.append(",");
					}
					message = actuationName + "(" + sb.toString() + ")";
				} else {
					message = actuationName;
				}

				return this.getMicrocontroller().write(message);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}


	@Override
	public boolean execEmbeddedAction(String actionName, Object[] args, Unifier un)
			throws EmbeddedActionNotFoundException, EmbeddedActionException {
		return false;
	}
	
	
	public void addBeliefCustomizator(Literal functorOrigin, Literal functorTarget) {
		this.beliefMap.put(functorOrigin, functorTarget);	
	}
	
	public Literal customizeBelief(Literal belief) {
		Literal bel = beliefMap.get(belief.getFunctor());
		if(bel==null) return belief;
		return bel;
	}

	@Override
	public boolean doExecActuation(Atom actuatorId, Atom actuationId, Object[] args, Unifier un) {
		// TODO Auto-generated method stub
		return false;
	}
	
}


