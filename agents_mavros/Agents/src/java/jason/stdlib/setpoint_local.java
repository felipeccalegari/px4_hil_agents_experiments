package jason.stdlib; 

import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.Literal;
import jason.asSyntax.ListTermImpl;
import jason.asSyntax.NumberTerm;
import jason.asSyntax.Term;
import static jason.asSyntax.ASSyntax.createAtom;
import static jason.asSyntax.ASSyntax.createNumber;

public class setpoint_local extends embedded.mas.bridges.jacamo.defaultEmbeddedInternalAction {

        private RelativeSetpoint lastRelativeSetpoint;

        @Override
        public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
            ListTermImpl parameters = new ListTermImpl();
            if (args.length == 3) {
                double forward = numberArg(args[0], "forward");
                double right = numberArg(args[1], "right");
                double up = numberArg(args[2], "up");
                RelativeSetpoint setpoint = relativeSetpoint(ts, forward, right, up);
                parameters.add(createNumber(setpoint.targetX));
                parameters.add(createNumber(setpoint.targetY));
                parameters.add(createNumber(setpoint.targetZ));
            } else if (args.length == 1 && args[0] instanceof ListTermImpl && ((ListTermImpl) args[0]).size() == 3) {
                ListTermImpl values = (ListTermImpl) args[0];
                double forward = numberArg(values.get(0), "forward");
                double right = numberArg(values.get(1), "right");
                double up = numberArg(values.get(2), "up");
                RelativeSetpoint setpoint = relativeSetpoint(ts, forward, right, up);
                parameters.add(createNumber(setpoint.targetX));
                parameters.add(createNumber(setpoint.targetY));
                parameters.add(createNumber(setpoint.targetZ));
            } else {
                for(Term t:args) parameters.add(t);
            }
            Term[] arguments = new Term[3];
            arguments[0] =  createAtom("sample_roscore"); 
            arguments[1] =  createAtom( this.getClass().getSimpleName());
            arguments[2] = parameters;
            return super.execute(ts, un,  arguments);            
        }

        private RelativeSetpoint relativeSetpoint(TransitionSystem ts, double forward, double right, double up)
                throws Exception {
            if (lastRelativeSetpoint != null && lastRelativeSetpoint.matches(forward, right, up)) {
                return lastRelativeSetpoint;
            }

            LocalPose pose = currentLocalPose(ts);
            // MAVROS local_position/pose is typically ENU in the "map" frame:
            // x = east, y = north, z = up. A positive "right" body offset therefore
            // rotates with the opposite sign compared with the MAVLink NED case.
            double deltaX = (forward * Math.cos(pose.yaw)) + (right * Math.sin(pose.yaw));
            double deltaY = (forward * Math.sin(pose.yaw)) - (right * Math.cos(pose.yaw));
            double deltaZ = up;

            lastRelativeSetpoint = new RelativeSetpoint(
                forward,
                right,
                up,
                pose.x + deltaX,
                pose.y + deltaY,
                pose.z + deltaZ);
            return lastRelativeSetpoint;
        }

        private static LocalPose currentLocalPose(TransitionSystem ts) throws Exception {
            for (Literal belief : ts.getAg().getBB()) {
                if ("position".equals(belief.getFunctor()) && belief.getArity() >= 1) {
                    return poseFromBelief(belief);
                }
            }
            throw new IllegalStateException(
                "setpoint_local(Forward, Right, Up) requires position(pose(position(...), orientation(...)))");
        }

        private static LocalPose poseFromBelief(Literal belief) throws Exception {
            Literal pose = asLiteral(belief.getTerm(0), "pose");
            Literal position = asLiteral(pose.getTerm(0), "position");
            Literal orientation = asLiteral(pose.getTerm(1), "orientation");

            double x = numberFromAxis(position, 0);
            double y = numberFromAxis(position, 1);
            double z = numberFromAxis(position, 2);

            double qx = numberFromAxis(orientation, 0);
            double qy = numberFromAxis(orientation, 1);
            double qz = numberFromAxis(orientation, 2);
            double qw = numberFromAxis(orientation, 3);

            double yaw = Math.atan2(
                2.0 * ((qw * qz) + (qx * qy)),
                1.0 - (2.0 * ((qy * qy) + (qz * qz))));

            return new LocalPose(x, y, z, yaw);
        }

        private static Literal asLiteral(Term term, String name) {
            if (!(term instanceof Literal)) {
                throw new IllegalStateException("Expected " + name + " literal inside position belief");
            }
            return (Literal) term;
        }

        private static double numberFromAxis(Literal literal, int axisIndex) throws Exception {
            return numberArg(asLiteral(literal.getTerm(axisIndex), literal.getFunctor() + " axis").getTerm(0), literal.getFunctor());
        }

        private static double numberArg(Term arg, String name) throws Exception {
            if (!(arg instanceof NumberTerm)) {
                throw new IllegalArgumentException("setpoint_local requires numeric " + name + " argument");
            }
            return ((NumberTerm) arg).solve();
        }

        private static class LocalPose {
            final double x;
            final double y;
            final double z;
            final double yaw;

            LocalPose(double x, double y, double z, double yaw) {
                this.x = x;
                this.y = y;
                this.z = z;
                this.yaw = yaw;
            }
        }

        private static class RelativeSetpoint {
            final double forward;
            final double right;
            final double up;
            final double targetX;
            final double targetY;
            final double targetZ;

            RelativeSetpoint(double forward, double right, double up, double targetX, double targetY, double targetZ) {
                this.forward = forward;
                this.right = right;
                this.up = up;
                this.targetX = targetX;
                this.targetY = targetY;
                this.targetZ = targetZ;
            }

            boolean matches(double forward, double right, double up) {
                return Double.compare(this.forward, forward) == 0
                    && Double.compare(this.right, right) == 0
                    && Double.compare(this.up, up) == 0;
            }
        }
}
