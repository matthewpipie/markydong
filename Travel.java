import bc.MapLocation;
import bc.Direction;

public class Travel {
    private MapLocation destination;
    private long roundSinceDestinationChange;
    public TravelStatus status;
    public Direction directionOfInterest;

    public Travel() {
        status = TravelStatus.NEEDS_NEW_JOB;
        directionOfInterest = Direction.Center;
    }

    //public Travel(MapLocation a, TravelStatus b, Direction c) {
        //destination = a;
        //status = b;
        //directionOfInterest = c;
    //}

    public MapLocation getDestination() {
        return destination;
    }

    public void setDestination(MapLocation d, long round) {
        destination = d;
        roundSinceDestinationChange = round;
    }

    public long getRoundSinceDestinationChange() {
        return roundSinceDestinationChange;
    }

    public MapLocation pointOfInterest() {
        return destination.addMultiple(directionOfInterest, 1);
    }

}
