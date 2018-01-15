import bc.*;

import java.util.ArrayList;
import java.util.Map;

public class Computer {

    final long WORKER_BUILD_RANGE_SQUARED = 100;
    final long KARBONITE_SHORTAGE = 50;
    final double FACTORY_SHORTAGE_COEFF = .01; // every 100 rounds, a new factory will be prioritized
    final double ROCKET_SHORTAGE_COEFF = .0066; // every ~150 rounds, a new factory will be prioritized
    //final int MAX_STRUCTURES_IN_PROGRESS_AT_ONCE = 2;
    final long DANGEROUS_RANGE_SQUARED_HEALER = 64;
    final long DANGEROUS_RANGE_SQUARED_WORKER = 100;

    GameController gc = new GameController();
    Map<Integer, WorkerTask> workerTaskMap;
    int factoriesInProgress = 0;
    int factoryCount = 0;
    int rocketsInProgress = 0;
    int rocketCount = 0;
    Map<Integer, Integer> workersWorkingOnStructure; // worker id -> structure id
    ArrayList<MapLocation> gettableKarboniteLocations;
    ArrayList<Integer> structuresInProgress;
    Map<Integer, Unit> enemyUnits;
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
        updateEnemyUnits();
        Direction moveDir;
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            moveDir = Direction.Center;

            switch (unit.unitType()) {
                case Worker:
                    if (!workerTaskMap.containsKey(unit.id())) {
                        workerTaskMap.put(unit.id(), getNewWorkerTask(unit));
                    }
                    int enemyID = -1;
                    if ((enemyID = isEnemyUnitInRange(unit, DANGEROUS_RANGE_SQUARED_WORKER)) != -1) {
                        moveDir = opposite(unit.location().mapLocation().directionTo(enemyUnits.get(enemyID).location().mapLocation()));
                        break;
                    }
                    // temp
                    moveDir = Direction.Northeast;
                    switch (workerTaskMap.get(unit.id())) {
                        case HARVEST:
                            break;
                        case START_BUILD_FACTORY:
                            break;
                        case START_BUILD_ROCKET:
                            break;
                        case BUILD:
                            break;
                    }
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
            }
        }
        // Submit the actions we've done, and wait for our next turn.
        gc.nextTurn();
    }

    private int isEnemyUnitInRange(Unit unit, long rangeSquared) {
        for (int i = 0; i < enemyUnits.size(); i++) {
            if (enemyUnits.get(i).location().mapLocation().distanceSquaredTo(unit.location().mapLocation()) <= rangeSquared) {
                return i;
            }
        }
        return -1;
    }

    private void updateEnemyUnits() {
        VecUnit units = gc.units();
        for (int i = 0; i < units.size(); i++) {
            if (units.get(i).team() != gc.team()) {
                enemyUnits.put(units.get(i).id(), units.get(i));
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
}
