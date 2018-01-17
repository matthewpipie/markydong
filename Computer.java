import bc.*;

import java.util.*;

public class Computer {
    static Random rand = new Random();
    List<Direction> directions = Arrays.asList(Direction.North, Direction.Northeast, Direction.East, Direction.Southeast, Direction.South, Direction.Southwest, Direction.West, Direction.Northwest);
    final long WORKER_BUILD_RANGE_SQUARED = 100;
    final long KARBONITE_SHORTAGE = 50;
    final double FACTORY_SHORTAGE_COEFF = .01; // every 100 rounds, a new factory will be prioritized
    final double ROCKET_SHORTAGE_COEFF = .0066; // every ~150 rounds, a new factory will be prioritized
    //final int MAX_STRUCTURES_IN_PROGRESS_AT_ONCE = 2;
    final long DANGEROUS_RANGE_SQUARED_HEALER = 64;
    final long DANGEROUS_RANGE_SQUARED_WORKER = 16;

    GameController gc = new GameController();
    int factoriesInProgress = 0;
    int factoryCount = 0;
    int rocketsInProgress = 0;
    int rocketCount = 0;
    Map<Integer, WorkerTask> workerTaskMap = new HashMap<>();
    Map<Integer, Integer> workersWorkingOnStructure = new HashMap<>(); // worker id -> structure id
    ArrayList<MapLocation> gettableKarboniteLocations = new ArrayList<>();
    ArrayList<Integer> structuresInProgress = new ArrayList<>();
    Map<Integer, Unit> enemyUnits = new HashMap<>();
    Map<Integer, Travel> travels = new HashMap<>();
    Computer() {
        init();
        while (true) {
            mainLoop();
        }
    }
    void init() {
        // find karbonite
        // i hate this
        PlanetMap planetMap = gc.startingMap(gc.planet());
        MapLocation test;
        long width = planetMap.getWidth();
        long height = planetMap.getHeight();
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                test = new MapLocation(gc.planet(), i, j);
                if (planetMap.initialKarboniteAt(test) != 0) {
                    gettableKarboniteLocations.add(test);
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                debug(directions.get(i).toString() + " rotated right " + j + " is " + rotateRight(directions.get(i), (int) Math.floor((j + 1) / 2) * (j % 2 == 1 ? -1 : 1)).toString());
            }
        }
    }
    void mainLoop() {
        //System.out.println("Current round: " + gc.round());
        VecUnit units = gc.myUnits();
        updateEnemyUnitsAndStructures();
        Direction moveDir;
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            moveDir = Direction.Center;

            if (travels.containsKey(unit.id())) {
                if (unit.location().mapLocation().equals(travels.get(unit.id()).destination) && travels.get(unit.id()).status != TravelStatus.AT_DESTINATION) {
                    Travel temp = travels.get(unit.id());
                    temp.status = TravelStatus.AT_DESTINATION;
                    travels.put(unit.id(), temp);
                }
            }

            switch (unit.unitType()) {
                case Worker:
                    if (travels.containsKey(unit.id())) {
                        Travel t = travels.get(unit.id());
                        //debug(unit.id() + ", " + t.status.toString() + ", " + t.directionOfInterest.toString() + ", " + t.destination.getX() + ", " + t.destination.getY());
                    } else {
                        //debug(unit.id() + "");
                    }
                    moveDir = worker(unit);
                    //debug("moved " + moveDir.toString());
                    break;
                case Knight:
                    break;
                case Ranger:
                    break;
                case Mage:
                    break;
                case Healer:
                    break;
                case Factory:
                    if (gc.canProduceRobot(unit.id(), UnitType.Worker)) {
                        gc.produceRobot(unit.id(), UnitType.Worker);
                    }
                    break;
                case Rocket:
                    break;
            }

            // Most methods on gc take unit IDs, instead of the unit objects themselves.
            if (moveDir != Direction.Center) {
                if (gc.isMoveReady(unit.id())) {
                    for (int j = 0; j < 8; j++) {
                        if (gc.canMove(unit.id(), rotateRight(moveDir, (int) Math.floor((j + 1) / 2) * (j % 2 == 1 ? -1 : 1)))) {
                            gc.moveRobot(unit.id(), rotateRight(moveDir, (int) Math.floor((j + 1) / 2) * (j % 2 == 1 ? -1 : 1)));
                            break;
                        }
                    }
                }
            }
        }

        // Submit the actions we've done, and wait for our next turn.
        gc.nextTurn();
    }

    private Direction rotateRight(Direction moveDir, int num) {
        int index = (directions.indexOf(moveDir) + num) % directions.size();
        if (index < 0) {
            index += directions.size();
        }
        return directions.get(index);
    }

    private Direction worker(Unit unit) {
        Direction ret = Direction.Center;
        int enemyID = -1;
        // if enemy, run away
        if ((enemyID = isEnemyUnitInRange(unit, DANGEROUS_RANGE_SQUARED_WORKER)) != -1) {
            return opposite(unit.location().mapLocation().directionTo(enemyUnits.get(enemyID).location().mapLocation()));
        }
        // give workers a job, status, and destination
        if (!workerTaskMap.containsKey(unit.id())) {
            giveNewWorkerJob(unit);
        }
        switch (travels.get(unit.id()).status) {
            case IN_PROGRESS:
                ret = unit.location().mapLocation().directionTo(travels.get(unit.id()).destination);
                break;
            case AT_DESTINATION:
                switch (workerTaskMap.get(unit.id())) {
                    case HARVEST:
                        if (gc.canHarvest(unit.id(), travels.get(unit.id()).directionOfInterest)) {
                            gc.harvest(unit.id(), travels.get(unit.id()).directionOfInterest);
                            //debug(unit.id() + " can harvest");
                        }
                        else {
                            //debug(unit.id() + " can  NOT  harvest");
                        //}
                        //if (gc.karboniteAt(travels.get(unit.id()).pointOfInterest()) < 0.1) {
                            gettableKarboniteLocations.remove(travels.get(unit.id()).pointOfInterest());
                            giveNewWorkerJob(unit);
                            return worker(unit);
                        }
                        break;
                    case START_BUILD_FACTORY:
                        if (gc.canBlueprint(unit.id(), UnitType.Factory, travels.get(unit.id()).directionOfInterest)) {
                            gc.blueprint(unit.id(), UnitType.Factory, travels.get(unit.id()).directionOfInterest);
                            workerTaskMap.put(unit.id(), WorkerTask.BUILD);
                        }
                        break;
                    case START_BUILD_ROCKET:
                        if (gc.canBlueprint(unit.id(), UnitType.Rocket, travels.get(unit.id()).directionOfInterest)) {
                            gc.blueprint(unit.id(), UnitType.Rocket, travels.get(unit.id()).directionOfInterest);
                            workerTaskMap.put(unit.id(), WorkerTask.BUILD);
                        }
						break;
                    case BUILD:
                        Unit thingToBuild = gc.senseUnitAtLocation(travels.get(unit.id()).pointOfInterest());
                        if (gc.canBuild(unit.id(), thingToBuild.id())) {
                            gc.build(unit.id(), thingToBuild.id());
                        }
                        if (thingToBuild.structureIsBuilt() > 0.5) { // dont know why it returns a short but 0=false, 1=true
                            giveNewWorkerJob(unit);
                            return worker(unit);
                        }
                        break;
                }
                break;
            case NEEDS_NEW_JOB:
                giveNewWorkerJob(unit);
                return worker(unit);
        }

        return ret;
    }

    private void giveNewWorkerJob(Unit worker) {
        workerTaskMap.put(worker.id(), getNewWorkerTask(worker));
        travels.put(worker.id(), getWorkerTravel(worker));
    }

    private Direction getRandomDir(Boolean center) {
        ArrayList<Direction> values2 = new ArrayList<Direction>(Arrays.asList(Direction.values()));
        if (!center) {
            values2.remove(Direction.Center);
        }
        return values2.get(rand.nextInt(values2.size()));
    }

    private Travel getWorkerTravel(Unit worker) {
        Travel ret = new Travel();
        ret.status = TravelStatus.IN_PROGRESS;
        switch (workerTaskMap.get(worker.id())) {
            case HARVEST:
                int smallestIndex = 0;
                for (int i = 0; i < gettableKarboniteLocations.size(); i++) {
                    if (gettableKarboniteLocations.get(i).distanceSquaredTo(worker.location().mapLocation()) <
                            gettableKarboniteLocations.get(smallestIndex).distanceSquaredTo(worker.location().mapLocation())) {
                        smallestIndex = i;
                    }
                }
                MapLocation karbonite = gettableKarboniteLocations.get(smallestIndex);
                ret.directionOfInterest = opposite(findNearestOpenDirectionToSpace(karbonite, worker.location().mapLocation()));
                ret.destination = karbonite.addMultiple(opposite(ret.directionOfInterest), 1);
                //debug("closest karbonite: " + ret.destination.getX() + ", " + ret.destination.getY());
                break;
            case START_BUILD_FACTORY:
            case START_BUILD_ROCKET:
                MapLocation loc = worker.location().mapLocation();
                Direction dirT = findNearestOpenDirectionToSpace(loc, worker.location().mapLocation());
                ret.directionOfInterest = opposite(dirT);
                ret.destination = loc.addMultiple(dirT, 1);
                break;
            case BUILD:
                int closestIndex = 0;
                for (int i = 0; i < structuresInProgress.size(); i++) {
                    if (gc.unit(structuresInProgress.get(i)).location().mapLocation().distanceSquaredTo(worker.location().mapLocation()) <
                            gc.unit(structuresInProgress.get(closestIndex)).location().mapLocation().distanceSquaredTo(worker.location().mapLocation())) {
                        closestIndex = i;
                    }
                }
                MapLocation loc2 = gc.unit(structuresInProgress.get(closestIndex)).location().mapLocation();
                Direction dirT2 = findNearestOpenDirectionToSpace(loc2, worker.location().mapLocation());
                ret.directionOfInterest = opposite(dirT2);
                ret.destination = loc2.addMultiple(dirT2, 1);
                break;
        }
        return ret;
    }

    private Direction findNearestOpenDirectionToSpace(MapLocation space, MapLocation loc) {
        Direction dirT = space.directionTo(loc);
        for (int i = 0; i < 8; i++) {
            dirT = rotateRight(space.directionTo(loc), (int)Math.floor((i+1)/2)*(i % 2 == 1 ? -1 : 1));
            if (gc.startingMap(gc.planet()).isPassableTerrainAt(space.addMultiple(dirT, 1)) > 0.5) {
                break;
            }
        }
        return dirT;
    }

    //private Direction findOpenDirectionToSpace(MapLocation space) {
        //MapLocation locT;
        //Direction dirT;
        //do {
            //dirT = getRandomDir(false);
            //locT = space.addMultiple(dirT, 1);
        //} while (gc.startingMap(gc.planet()).isPassableTerrainAt(locT) > 0.5);
        //return dirT;
    //}

    private int isEnemyUnitInRange(Unit unit, long rangeSquared) {
        for (int i = 0; i < enemyUnits.size(); i++) {
            if (enemyUnits.get(i).location().mapLocation().distanceSquaredTo(unit.location().mapLocation()) <= rangeSquared) {
                return i;
            }
        }
        return -1;
    }

    private void updateEnemyUnitsAndStructures() {
        factoriesInProgress = 0;
        factoryCount = 0;
        rocketsInProgress = 0;
        rocketCount = 0;
        VecUnit units = gc.units();
        structuresInProgress = new ArrayList<>();
        for (int i = 0; i < units.size(); i++) {
            if (units.get(i).team() != gc.team()) {
                enemyUnits.put(units.get(i).id(), units.get(i));
                continue;
            }
            switch (units.get(i).unitType()) {
                case Factory:
                    if (units.get(i).structureIsBuilt() < 0.5) {
                        factoriesInProgress++;
                        structuresInProgress.add(units.get(i).id());
                    } else {
                        factoryCount++;
                    }
                    break;
                case Rocket:
                    if (units.get(i).structureIsBuilt() < 0.5) {
                        rocketsInProgress++;
                        structuresInProgress.add(units.get(i).id());
                    } else {
                        rocketCount++;
                    }
                    break;
            }
        }
    }

    private WorkerTask getNewWorkerTask(Unit unit) {
        /*if (gc.planet() == Planet.Mars) {
            return WorkerTask.HARVEST;
        }
        if (gc.karbonite() < KARBONITE_SHORTAGE) {
            return WorkerTask.HARVEST;
        }
        if (gc.round() * FACTORY_SHORTAGE_COEFF < factoryCount + factoriesInProgress) {
            return WorkerTask.START_BUILD_FACTORY; // later needs to inc factoriesInProgress
        }
        if (gc.round() * ROCKET_SHORTAGE_COEFF < rocketCount + rocketsInProgress) {
            return WorkerTask.START_BUILD_FACTORY; // later needs to inc factoriesInProgress
        }
        for (int i = 0; i < structuresInProgress.size(); i++) {
            Integer structureInProgress = structuresInProgress.get(i);
            if (unit.location().mapLocation().distanceSquaredTo(gc.unit(structureInProgress).location().mapLocation()) < WORKER_BUILD_RANGE_SQUARED) {
                workersWorkingOnStructure.put(unit.id(), structureInProgress);
                return WorkerTask.BUILD;
            }
        }*/
        return WorkerTask.HARVEST;
    }
    private Direction opposite(Direction dir) {
        switch (dir) {
            case North:
                return Direction.South;
            case Northeast:
                return Direction.Southwest;
            case East:
                return Direction.West;
            case Southeast:
                return Direction.Northwest;
            case South:
                return Direction.North;
            case Southwest:
                return Direction.Northeast;
            case West:
                return Direction.East;
            case Northwest:
                return Direction.Southeast;
            case Center:
            default:
                return Direction.Center;
        }
    }
    void debug(String num) {
        System.out.println("DEBUG " + num);
    }
}
