import bc.*;

import java.util.ArrayList;
import java.util.Map;

public class Computer {

    private static final double WORKER_BUILD_RANGE_SQUARED = 100;
    final long KARBONITE_SHORTAGE = 50;
    final double FACTORY_SHORTAGE_COEFF = .01; // every 100 rounds, a new factory will be prioritized
    final double ROCKET_SHORTAGE_COEFF = .0066; // every ~150 rounds, a new factory will be prioritized
    //final int MAX_STRUCTURES_IN_PROGRESS_AT_ONCE = 2;

    GameController gc = new GameController();
    Map<Integer, WorkerTask> workerTaskMap;
    int factoriesInProgress = 0;
    int factoryCount = 0;
    int rocketsInProgress = 0;
    int rocketCount = 0;
    Map<Integer, Integer> workersWorkingOnStructure; // worker id -> structure id
    ArrayList<MapLocation> karboniteLocations;
    ArrayList<Integer> structuresInProgress;
    ArrayList<Unit> enemyUnits;
    Computer() {
        mainLoop();
    }
    void mainLoop() {
        while (true) {
            System.out.println("Current round: " + gc.round());
            // VecUnit is a class that you can think of as similar to ArrayList<Unit>, but immutable.
            VecUnit units = gc.myUnits();
            for (int i = 0; i < units.size(); i++) {
                Unit unit = units.get(i);
                gc.karboniteAt(new MapLocation(Planet.Earth, 1, 1)); // find karbonite
                gc.senseNearbyUnitsByTeam(new PlanetMap().getWidth(), oppositeTeam // use gc.units() instead

                switch (unit.unitType()) {
                    case Worker:
                        if (!workerTaskMap.containsKey(unit.id())) {
                            workerTaskMap.put(unit.id(), getNewWorkerTask(unit));
                        }
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
                        break;
                    case Rocket:
                        break;
                }

                // Most methods on gc take unit IDs, instead of the unit objects themselves.
                if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), Direction.Southeast)) {
                    gc.moveRobot(unit.id(), Direction.Southeast);
                }
            }
            // Submit the actions we've done, and wait for our next turn.
            gc.nextTurn();
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

}
