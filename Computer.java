import bc.*;

import java.util.*;

public class Computer {
    static Random rand = new Random(0);
    List<Direction> directions = Arrays.asList(Direction.North, Direction.Northeast, Direction.East, Direction.Southeast, Direction.South, Direction.Southwest, Direction.West, Direction.Northwest);
    final long WORKER_BUILD_RANGE_SQUARED = 25;
    final long KARBONITE_SHORTAGE = 100;
    final double FACTORY_SHORTAGE_COEFF = .01; // every 100 rounds, a new factory will be prioritized
    final double ROCKET_SHORTAGE_COEFF = .0066; // every ~150 rounds, a new factory will be prioritized
    final double WORKER_SHORTAGE_COEFF_EARTH = .25;
    final double WORKER_SHORTAGE_COEFF_MARS = .5;
    final long WORKER_COUNT_MAX_EARTH = 10;
    final long WORKER_COUNT_MAX_MARS = 20;
    final long DANGEROUS_RANGE_SQUARED_HEALER = 49;
    final long DANGEROUS_RANGE_SQUARED_WORKER = 25;
    final long MAX_ROUNDS_IN_ONE_TRAVEL = 30;
    final long EARTH_DEATH = 750;
    final int MAX_UNIT_DEVIATION_SQUARED = 25;

    GameController gc = new GameController();
    int factoriesInProgress = 0;
    int factoryCount = 0;
    int rocketsInProgress = 0;
    int rocketCount = 0;
    int workerCount = 0;
    int workersInProgress = 0;
    Map<Integer, WorkerTask> workerTaskMap = new HashMap<>();
    Map<Integer, Integer> workersWorkingOnStructure = new HashMap<>(); // worker id -> structure id
    ArrayList<String> gettableKarboniteLocations = new ArrayList<>();
    ArrayList<Integer> structuresInProgress = new ArrayList<>();
    Map<Integer, Unit> enemyUnits = new HashMap<>();
    Map<Integer, Travel> travels = new HashMap<>();
    MapLocation base;
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
                    gettableKarboniteLocations.add(encryptMapLocation(test));
                }
            }
        }
        if (gc.planet() == Planet.Mars) {
            queueResearch();
            base = null;
        }
        else {
            VecUnit myUnits = gc.myUnits();
            int x = 0;
            int y = 0;
            for (int i = 0; i < myUnits.size(); i++) {
                x += myUnits.get(i).location().mapLocation().getX();
                y += myUnits.get(i).location().mapLocation().getY();
            }
            base = new MapLocation(gc.planet(), x / (int) myUnits.size(), y / (int) myUnits.size());
            gc.disintegrateUnit(gc.myUnits().get(1).id()); //temp
            debug(gc.team() + ", unit=" + gc.myUnits().get(0).id());
        }
    }

    private void queueResearch() {
        gc.queueResearch(UnitType.Rocket);
    }

    void mainLoop() {
        debug("Current round: " + gc.round());
        VecUnit units = gc.myUnits();
        updateEnemyUnitsAndStructures();
        Direction moveDir;
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            moveDir = getRandomDir(false);

            if (unit.location().isInSpace()) {
                continue;
            }

            if (travels.containsKey(unit.id())) {
                if (unit.location().mapLocation().equals(travels.get(unit.id()).getDestination()) && travels.get(unit.id()).status != TravelStatus.AT_DESTINATION) {
                    Travel temp = travels.get(unit.id());
                    temp.status = TravelStatus.AT_DESTINATION;
                    travels.put(unit.id(), temp);
                }
            }

            if (!unit.location().isInGarrison()) {
                switch (unit.unitType()) {
                    case Worker:
                        if (travels.containsKey(unit.id())) {
                            if (unit.id() == 4) { // temp
                                Travel t = travels.get(unit.id());
                                debug(unit.location().mapLocation().getX() + ", " + unit.location().mapLocation().getY() + ", " + workerTaskMap.get(unit.id()).toString() + " / " + t.status.toString() + ", " + t.directionOfInterest.toString() + ", " + t.getDestination().getX() + ", " + t.getDestination().getY());
                            } // temp
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
            }

            // Most methods on gc take unit IDs, instead of the unit objects themselves.
            if (moveDir != Direction.Center) {
                if (gc.isMoveReady(unit.id())) {
                    for (int j = 0; j < 8; j++) {
                        if (gc.canMove(unit.id(), rotateRight(moveDir, (int) Math.floor((j + 1) / 2) * (j % 2 == 1 ? -1 : 1)))) {
                            gc.moveRobot(unit.id(), rotateRight(moveDir, (int) Math.floor((j + 1) / 2) * (j % 2 == 1 ? -1 : 1)));
                            if (unit.id() == 4) {
                                debug("moved " + moveDir.toString());
                            }
                            break;
                        }
                        else {
                            if (unit.id() == 4) {
                                debug("trying to move " + moveDir.toString() + ", but cant");
                            }
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

    private Direction worker(Unit worker) {
        Direction ret = Direction.Center;
        int enemyID = -1;
        // if enemy, run away
        if ((enemyID = isEnemyUnitInRange(worker, DANGEROUS_RANGE_SQUARED_WORKER)) != -1) {
            if (enemyUnits.get(enemyID).unitType() != UnitType.Worker) {
                debug("scared of enemy");
                return opposite(worker.location().mapLocation().directionTo(enemyUnits.get(enemyID).location().mapLocation()));
            }
        }
        // give workers a job, status, and destination
        if (!workerTaskMap.containsKey(worker.id())) {
            giveNewWorkerJob(worker);
        }
        if (workerTaskMap.get(worker.id()) == WorkerTask.HOLD_ONE_TURN) {
            giveNewWorkerJob(worker);
        }
        if (travels.get(worker.id()).getRoundSinceDestinationChange() - gc.round() > MAX_ROUNDS_IN_ONE_TRAVEL) {
            giveNewWorkerJob(worker);
        }
        if (!tooManyWorkers()) {
            for (int i = 0; i < directions.size(); i++) {
                if (gc.canReplicate(worker.id(), directions.get(i))) {
                    //gc.replicate(worker.id(), directions.get(i));
                    break;
                }
            }
        }
        switch (travels.get(worker.id()).status) {
            case IN_PROGRESS:
                ret = worker.location().mapLocation().directionTo(travels.get(worker.id()).getDestination());
                break;
            case AT_DESTINATION:
                switch (workerTaskMap.get(worker.id())) {
                    case HARVEST:
                        if (gc.canHarvest(worker.id(), travels.get(worker.id()).directionOfInterest)) {
                            gc.harvest(worker.id(), travels.get(worker.id()).directionOfInterest);
                            if (worker.id() == 4) {
                                debug("can harvest in " + travels.get(4).directionOfInterest);
                            }
                        }
                        else {
                            //debug(worker.id() + " can  NOT  harvest");
                        //}
                        //if (gc.karboniteAt(travels.get(worker.id()).pointOfInterest()) < 0.1) {
                            if (worker.id() == 4) {
                                //debug("can't harvest in " + travels.get(1).directionOfInterest + ", it has " + gc.karboniteAt(travels.get(1).pointOfInterest()) + " karbonite, so removing it (" +
                                        //decryptMapLocation(gettableKarboniteLocations.get(gettableKarboniteLocations.indexOf(encryptMapLocation(travels.get(1).pointOfInterest())))).getX() + ", " +
                                        //decryptMapLocation(gettableKarboniteLocations.get(gettableKarboniteLocations.indexOf(encryptMapLocation(travels.get(1).pointOfInterest())))).getY());
                            }
                            gettableKarboniteLocations.remove(encryptMapLocation(travels.get(worker.id()).pointOfInterest()));
                            giveNewWorkerJob(worker);
                            return worker(worker);
                        }
                        break;
                    case START_BUILD_FACTORY:
                        if (gc.canBlueprint(worker.id(), UnitType.Factory, travels.get(worker.id()).directionOfInterest)) {
                            gc.blueprint(worker.id(), UnitType.Factory, travels.get(worker.id()).directionOfInterest);
                            workerTaskMap.put(worker.id(), WorkerTask.BUILD);
                        }
                        break;
                    case START_BUILD_ROCKET:
                        if (gc.canBlueprint(worker.id(), UnitType.Rocket, travels.get(worker.id()).directionOfInterest)) {
                            gc.blueprint(worker.id(), UnitType.Rocket, travels.get(worker.id()).directionOfInterest);
                            workerTaskMap.put(worker.id(), WorkerTask.BUILD);
                        }
                        break;
                    case BUILD:
                        try {
                            Unit thingToBuild = gc.senseUnitAtLocation(travels.get(worker.id()).pointOfInterest());
                            if (gc.canBuild(worker.id(), thingToBuild.id())) {
                                gc.build(worker.id(), thingToBuild.id());
                            }
                            if (thingToBuild.structureIsBuilt() > 0.5) { // dont know why it returns a short but 0=false, 1=true
                                giveNewWorkerJob(worker);
                                return worker(worker);
                            }
                        } catch (RuntimeException e) {}

                        break;
                }
                break;
            case NEEDS_NEW_JOB:
                giveNewWorkerJob(worker);
                return worker(worker);
        }

        return ret;
    }

    private boolean tooManyWorkers() {
        if (gc.planet() == Planet.Mars) {
            if (gc.round() > 750) {
                return true;
            }
            return gc.round() * WORKER_SHORTAGE_COEFF_MARS < workerCount + workersInProgress || workerCount + workersInProgress > WORKER_COUNT_MAX_MARS;
        }
        else {
            return gc.round() * WORKER_SHORTAGE_COEFF_EARTH < workerCount + workersInProgress || workerCount + workersInProgress > WORKER_COUNT_MAX_EARTH;
        }
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
            case HOLD_ONE_TURN:
            case WANDER:
                return wanderTravel();
            case HARVEST:
                if (gettableKarboniteLocations.size() == 0) {
                    workerTaskMap.put(worker.id(), WorkerTask.WANDER);
                    return wanderTravel();
                }
                int smallestIndex = 0;
                for (int i = 0; i < gettableKarboniteLocations.size(); i++) {
                    MapLocation testLoc = decryptMapLocation(gettableKarboniteLocations.get(i));
                    MapLocation smallest = decryptMapLocation(gettableKarboniteLocations.get(smallestIndex));
                    if (gc.canSenseLocation(testLoc) && gc.canSenseLocation(smallest)) {
                        if (gc.karboniteAt(testLoc) > gc.karboniteAt(smallest)) {
                            if (worker.location().mapLocation().distanceSquaredTo(testLoc) <=
                                    worker.location().mapLocation().distanceSquaredTo(smallest)) {
                                smallestIndex = i;
                            }
                        }
                    }
                    else {
                        if (gc.startingMap(gc.planet()).initialKarboniteAt(testLoc) >
                                gc.startingMap(gc.planet()).initialKarboniteAt(smallest)) {
                            if (worker.location().mapLocation().distanceSquaredTo(testLoc) <=
                                    worker.location().mapLocation().distanceSquaredTo(smallest)) {
                                smallestIndex = i;
                            }
                        }
                        else {
                            if (worker.location().mapLocation().distanceSquaredTo(testLoc) <
                                    worker.location().mapLocation().distanceSquaredTo(smallest)) {
                                smallestIndex = i;
                        }
                    }
                }
                debug("smallestIndex is " + smallestIndex + ", and gKL is this long: " + gettableKarboniteLocations.size());
                MapLocation karbonite = decryptMapLocation(gettableKarboniteLocations.get(smallestIndex));
                ret.directionOfInterest = opposite(findNearestOpenDirectionToSpace(karbonite, worker.location().mapLocation()));
                if (ret.directionOfInterest == null) {
                    return wanderTravel();
                }
                ret.setDestination(karbonite.addMultiple(opposite(ret.directionOfInterest), 1), gc.round());
                //debug("closest karbonite: " + ret.destination.getX() + ", " + ret.destination.getY());
                break;
            case START_BUILD_FACTORY:
            case START_BUILD_ROCKET:
                VecMapLocation vecMapLocation = gc.allLocationsWithin(base, MAX_UNIT_DEVIATION_SQUARED);
                MapLocation loc;
                do {
                    loc = vecMapLocation.get(rand.nextInt((int) vecMapLocation.size()));
                } while (gc.startingMap(gc.planet()).isPassableTerrainAt(loc) < 0.5);
                Direction dirT = findNearestOpenDirectionToSpace(loc, worker.location().mapLocation());
                ret.directionOfInterest = opposite(dirT);
                if (ret.directionOfInterest == null) {
                    return wanderTravel();
                }
                ret.setDestination(loc.addMultiple(dirT, 1), gc.round());
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
                if (ret.directionOfInterest == null) {
                    return wanderTravel();
                }
                ret.setDestination(loc2.addMultiple(dirT2, 1), gc.round());
                break;
        }
        return ret;
    }

    private Travel wanderTravel() {
        Travel ret = new Travel();
        int x2 = rand.nextInt((int)gc.startingMap(gc.planet()).getWidth());
        int y2 = rand.nextInt((int)gc.startingMap(gc.planet()).getHeight());
        ret.status = TravelStatus.IN_PROGRESS;
        ret.setDestination(new MapLocation(gc.planet(), x2, y2), gc.round());
        ret.directionOfInterest = getRandomDir(true);
        return ret;
    }

    private Direction findNearestOpenDirectionToSpace(MapLocation space, MapLocation loc) {
        Direction dirT = space.directionTo(loc);
        for (int i = 0; i < 8; i++) {
            dirT = rotateRight(space.directionTo(loc), (int)Math.floor((i+1)/2)*(i % 2 == 1 ? -1 : 1));
            try {
                if (gc.startingMap(gc.planet()).isPassableTerrainAt(space.addMultiple(dirT, 1)) > 0.5) {
                    return dirT;
                }
            } catch (RuntimeException e) {}
        }
        return null;
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
            try {
                if (enemyUnits.get(i).location().mapLocation().distanceSquaredTo(unit.location().mapLocation()) <= rangeSquared) {
                    return i;
                }
            } catch (NullPointerException e) {}
        }
        return -1;
    }

    private void updateEnemyUnitsAndStructures() {
        factoriesInProgress = 0;
        factoryCount = 0;
        rocketsInProgress = 0;
        rocketCount = 0;
        workersInProgress = 0;
        workerCount = 0;
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
                case Worker:
                    workerCount++;
                    break;
            }
        }
    }

    private WorkerTask getNewWorkerTask(Unit unit) {
        if (gc.planet() == Planet.Mars) {
            return WorkerTask.HARVEST;
        }
        if (unit.location().isInGarrison() || unit.location().isInSpace()) {
            return WorkerTask.HOLD_ONE_TURN;
        }
        if (gc.karbonite() < KARBONITE_SHORTAGE && gettableKarboniteLocations.size() != 0) {
            return WorkerTask.HARVEST;
        }
        if (gc.round() * FACTORY_SHORTAGE_COEFF > factoryCount + factoriesInProgress) {
            factoriesInProgress++;
            return WorkerTask.START_BUILD_FACTORY;
        }
        if (gc.researchInfo().getLevel(UnitType.Rocket) != 0 && gc.round() * ROCKET_SHORTAGE_COEFF > rocketCount + rocketsInProgress) {
            rocketsInProgress++;
            return WorkerTask.START_BUILD_ROCKET;
        }
        for (int i = 0; i < structuresInProgress.size(); i++) {
            Integer structureInProgress = structuresInProgress.get(i);
            if (unit.location().mapLocation().distanceSquaredTo(gc.unit(structureInProgress).location().mapLocation()) < WORKER_BUILD_RANGE_SQUARED) {
                workersWorkingOnStructure.put(unit.id(), structureInProgress);
                return WorkerTask.BUILD;
            }
        }
        return WorkerTask.HARVEST;
    }
    private Direction opposite(Direction dir) {
        if (dir == null) {return null;}
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
    String encryptMapLocation(MapLocation loc) {
        return loc.getX() + "," + loc.getY();
    }
    MapLocation decryptMapLocation(String loc) {
        final String[] split = loc.split(",");
        return new MapLocation(gc.planet(), Integer.parseInt(split[0]), Integer.parseInt(split[1]));
    }
    void debug(String num) {
        //if (gc.round() > 1000 || gc.round() < 50) {
            System.out.println("DEBUG " + num);
        //}
    }
}
