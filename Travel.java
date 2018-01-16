import bc.MapLocation;
import bc.Direction;

public class Travel {
    public MapLocation destination;
    public TravelStatus status;
    public Direction directionOfInterest;

    public Travel() {
        status = TravelStatus.NEEDS_NEW_JOB;
        directionOfInterest = Direction.Center;
    }

    public Travel(MapLocation a, TravelStatus b, Direction c) {
        destination = a;
        status = b;
        directionOfInterest = c;
    }

    public MapLocation pointOfInterest() {
        return destination.addMultiple(directionOfInterest, 1);
    }

}
