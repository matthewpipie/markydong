import bc.*;

import java.util.*;

public class Computer {
    static Random rand = new Random();
    final long WORKER_BUILD_RANGE_SQUARED = 100;
    final long KARBONITE_SHORTAGE = 50;
    final double FACTORY_SHORTAGE_COEFF = .01; // every 100 rounds, a new factory will be prioritized
    final double ROCKET_SHORTAGE_COEFF = .0066; // every ~150 rounds, a new factory will be prioritized
    //final int MAX_STRUCTURES_IN_PROGRESS_AT_ONCE = 2;
    final long DANGEROUS_RANGE_SQUARED_HEALER = 64;
    final long DANGEROUS_RANGE_SQUARED_WORKER = 100;

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
    }
    void mainLoop() {
        System.out.println("Current round: " + gc.round());
        VecUnit units = gc.myUnits();
        updateEnemyUnitsAndStructures();
        Direction moveDir;
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            moveDir = Direction.Center;

            switch (unit.unitType()) {
                case Worker:
                    moveDir = worker(unit);
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
            if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), moveDir)) {
                gc.moveRobot(unit.id(), moveDir);
                if (travels.containsKey(unit.id())) {
                    // if he has made it to his target location, set it that way
                    if (unit.location().mapLocation().equals(travels.get(unit.id()).destination) && travels.get(unit.id()).status != TravelStatus.AT_DESTINATION) {
                        Travel temp = travels.get(unit.id());
                        temp.status = TravelStatus.AT_DESTINATION;
                        travels.put(unit.id(), temp);
                    }
                }
            }
        }

        // Submit the actions we've done, and wait for our next turn.
        gc.nextTurn();
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
                        }
                        if (gc.karboniteAt(travels.get(unit.id()).pointOfInterest()) < 0.1) {
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
                ret.directionOfInterest = getRandomDir(false);
                ret.destination = gettableKarboniteLocations.get(smallestIndex).addMultiple(opposite(ret.directionOfInterest), 1);
                break;
            case START_BUILD_FACTORY:
            case START_BUILD_ROCKET:
                MapLocation loc = worker.location().mapLocation();
                Direction dirT = findOpenDirectionToSpace(loc);
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
                Direction dirT2 = findOpenDirectionToSpace(loc2);
                ret.directionOfInterest = opposite(dirT2);
                ret.destination = loc2.addMultiple(dirT2, 1);
                break;
        }
        return ret;
    }

    private Direction findOpenDirectionToSpace(MapLocation space) {
        MapLocation locT;
        Direction dirT;
        do {
            dirT = getRandomDir(false);
            locT = space.addMultiple(dirT, 1);
        } while (gc.startingMap(gc.planet()).isPassableTerrainAt(locT) > 0.5);
        return dirT;
    }

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
            if (units.get(i).structureIsBuilt() < 0.5) {
                structuresInProgress.add(units.get(i).id());
            }
            switch (units.get(i).unitType()) {
                case Factory:
                    if (units.get(i).structureIsBuilt() < 0.5) {
                        factoriesInProgress++;
                    } else {
                        factoryCount++;
                    }
                    break;
                case Rocket:
                    if (units.get(i).structureIsBuilt() < 0.5) {
                        rocketsInProgress++;
                    } else {
                        rocketCount++;
                    }
                    break;
            }
        }
    }

    private WorkerTask getNewWorkerTask(Unit unit) {
        if (gc.planet() == Planet.Mars) {
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
        }
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
