package jason.stdlib; 

import embedded.mas.bridges.jacamo.defaultEmbeddedInternalAction;
import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.ListTermImpl;
import jason.asSyntax.Term;
import static jason.asSyntax.ASSyntax.createAtom;

public class param_set extends embedded.mas.bridges.jacamo.defaultEmbeddedInternalAction {

        @Override
        public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
            ListTermImpl parameters = new ListTermImpl();
            if (args.length == 2) {
                parameters.add(createAtom("1"));
                parameters.add(createAtom("1"));
                parameters.add(args[0]);
                parameters.add(args[1]);
                parameters.add(createAtom("MAV_PARAM_TYPE_REAL32"));
            } else {
                for (Term t : args) parameters.add(t);
            }
            Term[] arguments = new Term[3];
            arguments[0] =  createAtom("arduino1"); 
            arguments[1] =  createAtom( this.getClass().getSimpleName());
            arguments[2] = parameters;
            return super.execute(ts, un,  arguments);            
        }
}
