import bc.*;

import java.util.ArrayList;
import java.util.Map;

public class Computer {

    final long KARBONITE_SHORTAGE = 50;
    final double FACTORY_SHORTAGE_COEFF = .01; // every 100 rounds, a new factory will be prioritized
    final int MAX_STRUCTURES_IN_PROGRESS_AT_ONCE = 2;

    Direction[] directions = Direction.values();
    GameController gc = new GameController();
    Map<Integer, WorkerTask> workerTaskMap;
    int factoriesInProgress = 0;
    int factoryCount = 0;
    Map<Integer, ArrayList<Integer>> workersWorkingOnStructure;
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
                switch (unit.unitType()) {
                    case Worker:
                        if (!workerTaskMap.containsKey(unit.id())) {
                            workerTaskMap.put(unit.id(), getNewWorkerTask(unit));
                        }
                        if (gc.get)
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
        if (gc.senseNearbyUnitsByType(unit.location().mapLocation(), )) // if nearby factory in progress, build it - maybe make this a map with locations of stuff in progress so we dont have to requery?
        return WorkerTask.HARVEST;
    }

}
