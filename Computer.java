import bc.*;

import java.lang.ref.PhantomReference;
import java.util.*;

public class Computer {
    static Random rand = new Random(0);
    List<Direction> directions = Arrays.asList(Direction.North, Direction.Northeast, Direction.East, Direction.Southeast, Direction.South, Direction.Southwest, Direction.West, Direction.Northwest);
    List<UnitType> unitTypes = Arrays.asList(UnitType.values());
    final long WORKER_BUILD_RANGE_SQUARED = 25;
    final long KARBONITE_SHORTAGE = 100;
    final double FACTORY_SHORTAGE_COEFF = .01; // every 100 rounds, a new factory will be prioritized
    final double ROCKET_SHORTAGE_COEFF = .006; // every ~166 rounds, a new rocket will be prioritized
    final double WORKER_SHORTAGE_COEFF_EARTH = .25;
    final double WORKER_SHORTAGE_COEFF_MARS = .5;
    final long WORKER_COUNT_MAX_EARTH = 10;
    final long WORKER_COUNT_MAX_MARS = 20;
    final long DANGEROUS_RANGE_SQUARED_HEALER = 49;
    final long DANGEROUS_RANGE_SQUARED_WORKER = 25;
    final long MAX_ROUNDS_IN_ONE_TRAVEL = 30;
    final long EARTH_DEATH = 750;
    final int MAX_UNIT_DEVIATION_SQUARED = 25;
    final int ROCKET_LAUNCH_TURNS = 30;
    final int MAX_UNITS = 60;
    final long MAX_ROUND_TO_PRODUCE = EARTH_DEATH - 20;

    GameController gc = new GameController();
    int factoriesInProgress = 0;
    int rocketsInProgress = 0;
    int workersInProgress = 0;

    Map<UnitType, Integer> unitCounts = new HashMap<>();
    Map<Integer, WorkerTask> workerTaskMap = new HashMap<>();
    Map<Integer, Integer> workersWorkingOnStructure = new HashMap<>(); // worker id -> structure id
    ArrayList<String> gettableKarboniteLocations = new ArrayList<>();
    ArrayList<Integer> structuresInProgress = new ArrayList<>();
    Map<Integer, Unit> enemyUnits = new HashMap<>();
    Map<Integer, Travel> travels = new HashMap<>();
    ArrayList<String> landableLocations = new ArrayList<>();
    Map<Integer, Long> rocketBuiltTimes = new HashMap<>();
    Map<Integer, Integer> unitsGoingToRockets = new HashMap<>();
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
            getLandableLocations();
        }
    }

    private void getLandableLocations() {
        PlanetMap planetMap = gc.startingMap(Planet.Mars);
        long w = planetMap.getWidth();
        long h = planetMap.getHeight();
        MapLocation t;
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                t = new MapLocation(Planet.Mars, i, j);
                if (planetMap.isPassableTerrainAt(t) > 0.5) {
                    landableLocations.add(encryptMapLocation(t));
                }
            }
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
            if (unit.unitType() == UnitType.Factory || unit.unitType() == UnitType.Rocket) {
                if (unit.structureIsBuilt() < 0.5) {
                    continue;
                }
            }

            if (travels.containsKey(unit.id()) && !unit.location().isInGarrison()) {
                if (unit.location().mapLocation().equals(travels.get(unit.id()).getDestination()) && travels.get(unit.id()).status != TravelStatus.AT_DESTINATION) {
                    Travel temp = travels.get(unit.id());
                    temp.status = TravelStatus.AT_DESTINATION;
                    travels.put(unit.id(), temp);
                }
            }

            if (!unit.location().isInGarrison()) {
                if (!unitsGoingToRockets.containsKey(unit.id())) {
                    switch (unit.unitType()) {
                        case Worker:
                            moveDir = worker(unit);
                            break;
                        case Knight:
			    moveDir = knight(unit);
                            break;
                        case Ranger:
			    moveDir = ranger(unit);
                            break;
                        case Mage:
			    moveDir = mage(unit);
                            break;
                        case Healer:
			    moveDir = healer(unit);
                            break;
                        case Factory:
                            factory(unit);
                            break;
                        case Rocket:
                            rocket(unit);
                            break;
                    }
                }
                else {
                    try {
                        Unit rocket = gc.unit(unitsGoingToRockets.get(unit.id()));
                        if (gc.canLoad(rocket.id(), unit.id())) {
                            gc.load(rocket.id(), unit.id());
                            unitsGoingToRockets.remove(unit.id());
                        } else {
                            moveDir = unit.location().mapLocation().directionTo(rocket.location().mapLocation());
                            if (moveDir == Direction.Center) {
                                unitsGoingToRockets.remove(unit.id());
                            }
                        }
                    } catch (RuntimeException e) {
                        unitsGoingToRockets.remove(unit.id());
                    }
                }
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

    private void knight(Unit knight) {
	if (!targets.containsKey(knight.id()) {
	    targets.put(knight.id(), getNewKnightTarget(knight));
	}
	Unit target = targets.get(knight.id());
	Direction moveDir = (target.location().mapLocation().distanceSquaredTo(knight.location().mapLocation()) > 1) ? knight.location().mapLocation().directionTo(target.location().mapLocation()) : Direction.Center;
	if (gc.isAttackReady(knight.id()) {
	    for {int i = 0; i < enemyUnits.size(); i++) {
	        if (enemyUnits.get(i).isInSpace() || enemyUnits.get(i).isInGarrison()) continue;
		if (gc.canAttack(knight.id(), enemyUnits.get(i).id())) {
		    gc.attack(knight.id(), enemyUnits.get(i).id());
		}
	    }
	}
	return moveDir;
    }
    private int getNewKnightTarget(Unit knight) {
	UnitType restrictTo = null;
	Unit target = null;
	for {int i = 0; i < enemyUnits.size(); i++) {
	    if (enemyUnits.get(i).isInSpace() || enemyUnits.get(i).isInGarrison()) continue;
	    if (enemyUnits.get(i).unitType() == UnitType.Healer && enemyUnits.get(i).location().mapLocation().distanceSquaredTo(knight.location().mapLocation()) < 60) {
		    restrictTo = UnitType.Healer;
	    }
	    if (restrictTo == UnitType.Healer) {
		    if (target.location().mapLocation().distanceSquaredTo(knight.location().mapLocation() > enemyUnits.get(i).location().mapLocation().distanceSquaredTo(knight.location().mapLocation()))) {
			target = enemyUnits.get(i);
		    }
	    }
	    if (restrictTo != UnitType.Healer) {
	        if (enemyUnits.get(i).unitType() == UnitType.Ranger && enemyUnits.get(i).location().mapLocation().distanceSquaredTo(knight.location().mapLocation()) < 50) {
		    restrictTo = UnitType.Ranger;
		}
	    }
	    if (restrictTo == UnitType.Ranger) { TODO
		    if (target.location().mapLocation().distanceSquaredTo(knight.location().mapLocation() > enemyUnits.get(i).location().mapLocation().distanceSquaredTo(knight.location().mapLocation()))) {
			target = enemyUnits.get(i);
		    }
	    }
	}
    }
    private void ranger(Unit ranger) {

    }
    private void mage(Unit mage) {

    }
    private void healer(Unit healer) {

    }

    private void factory(Unit factory) {
        UnitType robotToProduce = getRobotToProduceNext();
        if (robotToProduce != null) {
            if (gc.canProduceRobot(factory.id(), robotToProduce)) {
                gc.produceRobot(factory.id(), robotToProduce);
            }
        }
        for (int i = 0; i < directions.size(); i++) {
            if (gc.canUnload(factory.id(), directions.get(i))) {
                gc.unload(factory.id(), directions.get(i));
            }
        }
    }

    private UnitType getRobotToProduceNext() {
        if (unitCounts.get(UnitType.Worker) + workersInProgress < 2) {
            return UnitType.Worker;
        }
        if (gc.myUnits().size() > MAX_UNITS || gc.round() >= MAX_ROUND_TO_PRODUCE) {
            return null;
        }
        Map<UnitType, Integer> odds = new LinkedHashMap<>();
        double x;
        double a = -1; // GRAPH: (x/c-a)/(x/c-b)
        double b = -0.18;
        double c = 40;
        for (int i = 0; i < unitTypes.size(); i++) {
            x = unitCounts.get(unitTypes.get(i));
            if (unitTypes.get(i) == UnitType.Mage) {
                // i dont like mages, but we'll see
                odds.put(unitTypes.get(i), (int)Math.floor((x/c-a)/(x/c-b)) / 2);
            }
            else {
                odds.put(unitTypes.get(i), (int) Math.floor((x / c - a) / (x / c - b)));
            }
        }
        if (gettableKarboniteLocations.size() == 0) {
            odds.put(UnitType.Worker, 0);
        }
        double completeWeight = 0.0;
        for (UnitType key : odds.keySet()) {
            completeWeight += odds.get(key);
        }
        double r = rand.nextDouble() * completeWeight;
        double countWeight = 0.0;
        for (UnitType key : odds.keySet()) {
            countWeight += odds.get(key);
            if (countWeight >= r) {
                return key;
            }
        }
        return UnitType.Worker;
    }

    private void rocket(Unit rocket) {
        if (!rocketBuiltTimes.containsKey(rocket.id())) {
            rocketBuiltTimes.put(rocket.id(), gc.round());

            VecUnit units = gc.myUnits();
            ArrayList<Integer> unitsToBoard = new ArrayList<>();
            int workerCount = 0;
            for (int i = 0; i < units.size(); i++) {
                if (unitsToBoard.size() == rocket.structureMaxCapacity() + 1) {
                    break;
                }
                if (units.get(i).unitType() == UnitType.Factory || units.get(i).unitType() == UnitType.Healer || units.get(i).unitType() == UnitType.Rocket || units.get(i).unitType() == UnitType.Mage) {
                    continue;
                }
                if (units.get(i).unitType() == UnitType.Worker) {
                    WorkerTask t = workerTaskMap.get(units.get(i).id());
                    if (t != WorkerTask.START_BUILD_FACTORY && t != WorkerTask.START_BUILD_ROCKET && workerCount < 3) {
                        unitsToBoard.add(units.get(i).id());
                        workerCount++;
                    }
                    continue;
                }
                unitsToBoard.add(units.get(i).id());
            }
            if (workerCount == 0) {
                for (int i = 0; i < units.size(); i++) {
                    if (units.get(i).unitType() == UnitType.Worker) {
                        unitsToBoard.add(units.get(i).id());
                        break;
                    }
                }
            }
            for (int i = 0; i < unitsToBoard.size(); i++) {
                unitsGoingToRockets.put(unitsToBoard.get(i), rocket.id());
                debug(unitsToBoard.get(i) +" is boarding rocket " + rocket.id());
            }
        }
        if (rocket.rocketIsUsed() > 0.5) {
            debug("landed, on board: " + rocket.structureGarrison().size());
            if (rocket.structureGarrison().size() == 0) {
                gc.disintegrateUnit(rocket.id());
            }
            else {
                for (int i = 0; i < directions.size(); i++) {
                    if (gc.canUnload(rocket.id(), directions.get(i))) {
                        gc.unload(rocket.id(), directions.get(i));
                    }
                }
            }
        }
        else if (rocket.structureGarrison().size() != 0) {
            if (gc.round() - rocketBuiltTimes.get(rocket.id()) > ROCKET_LAUNCH_TURNS || gc.round() == EARTH_DEATH - 2 || rocket.structureGarrison().size() == rocket.structureMaxCapacity()) {
                MapLocation destination = decryptMapLocation(landableLocations.get(rand.nextInt(landableLocations.size())));
                if (gc.canLaunchRocket(rocket.id(), destination)) {
                    ArrayList<Integer> unitsToRemove = new ArrayList<>();
                    for (Integer unit : unitsGoingToRockets.keySet()) {
                        if (unitsGoingToRockets.get(unit) == rocket.id()) {
                            unitsToRemove.add(unitsGoingToRockets.get(unit));
                        }
                    }
                    for (int i = 0; i < unitsToRemove.size(); i++) {
                        unitsGoingToRockets.remove(unitsToRemove.get(i));
                    }
                    gc.launchRocket(rocket.id(), destination);
                    for (int i = 0; i < directions.size(); i++) {
                        try {
                            landableLocations.remove(encryptMapLocation(destination.add(directions.get(i))));
                        }
                        catch (Exception e) {}
                    }
                }
            }
        }
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
        if (gc.round() - travels.get(worker.id()).getRoundSinceDestinationChange() > MAX_ROUNDS_IN_ONE_TRAVEL) {
            giveNewWorkerJob(worker);
        }
        if (!tooManyWorkers()) {
            for (int i = 0; i < directions.size(); i++) {
                if (gc.canReplicate(worker.id(), directions.get(i))) {
                    gc.replicate(worker.id(), directions.get(i));
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
                    case WANDER:
                        giveNewWorkerJob(worker);
                        break;
                    case HARVEST:
                        if (gc.canHarvest(worker.id(), travels.get(worker.id()).directionOfInterest)) {
                            gc.harvest(worker.id(), travels.get(worker.id()).directionOfInterest);
                        }
                        else {
                            if (gc.canSenseLocation(travels.get(worker.id()).pointOfInterest())) {
                                if (gc.karboniteAt(travels.get(worker.id()).pointOfInterest()) < 0.1) {
                                    gettableKarboniteLocations.remove(encryptMapLocation(travels.get(worker.id()).pointOfInterest()));
                                    giveNewWorkerJob(worker);
                                    return worker(worker);
                                }
                            }
                            else {
                                giveNewWorkerJob(worker);
                            }
                        }
                        ret = Direction.Center;
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
        int workerCount = unitCounts.get(UnitType.Worker);
        if (gc.planet() == Planet.Mars) {
            if (gc.round() > 750) {
                return true;
            }
            return gc.round() * WORKER_SHORTAGE_COEFF_MARS < workerCount + workersInProgress || workerCount + workersInProgress > WORKER_COUNT_MAX_MARS;
        }
        else {
            if (gettableKarboniteLocations.size() == 0) {
                return true;
            }
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
                    if (worker.location().mapLocation().distanceSquaredTo(testLoc) <
                                    worker.location().mapLocation().distanceSquaredTo(smallest)) {
                        smallestIndex = i;
                    }
                    else if (worker.location().mapLocation().distanceSquaredTo(testLoc) ==
                            worker.location().mapLocation().distanceSquaredTo(smallest)) {
                        if (gc.canSenseLocation(testLoc) && gc.canSenseLocation(smallest)) {
                            if (gc.karboniteAt(testLoc) > gc.karboniteAt(smallest)) {
                                smallestIndex = i;
                            }
                        }
                        else {
                            if (gc.startingMap(gc.planet()).initialKarboniteAt(testLoc) >
                                    gc.startingMap(gc.planet()).initialKarboniteAt(smallest)) {
                                        smallestIndex = i;
                            }
                        }

                    }

                }
                MapLocation karbonite = decryptMapLocation(gettableKarboniteLocations.get(smallestIndex));
                ret.directionOfInterest = opposite(findNearestOpenDirectionToSpace(karbonite, worker.location().mapLocation()));
                if (ret.directionOfInterest == null) {
                    return wanderTravel();
                }
                ret.setDestination(karbonite.addMultiple(opposite(ret.directionOfInterest), 1), gc.round());
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
                if (enemyUnits.get(i).location().isInSpace() || enemyUnits.get(i).location().isInGarrison()) continue;
                if (enemyUnits.get(i).location().mapLocation().distanceSquaredTo(unit.location().mapLocation()) <= rangeSquared) {
                    return i;
                }
            } catch (NullPointerException e) {}
        }
        return -1;
    }

    private void updateEnemyUnitsAndStructures() {
        if (gc.planet() == Planet.Mars) {
            if (gc.asteroidPattern().hasAsteroid(gc.round())) {
                MapLocation loc = gc.asteroidPattern().asteroid(gc.round()).getLocation();
                //if (gc.startingMap(Planet.Mars).isPassableTerrainAt(loc) > 0.5) { TODO: uncomment this if you can't mine karbonite on the side of an ungettable loc
                    gettableKarboniteLocations.add(encryptMapLocation(loc));
                //}
            }
        }
        factoriesInProgress = 0;
        rocketsInProgress = 0;
        workersInProgress = 0;
        for (int i = 0; i < unitTypes.size(); i++) {
            unitCounts.put(unitTypes.get(i), 0);
        }
        VecUnit units = gc.units();
        structuresInProgress = new ArrayList<>();
        for (int i = 0; i < units.size(); i++) {
            if (units.get(i).team() != gc.team()) {
                enemyUnits.put(units.get(i).id(), units.get(i));
                continue;
            }
            unitCounts.put(units.get(i).unitType(), unitCounts.get(units.get(i).unitType()) + 1);
            switch (units.get(i).unitType()) {
                case Factory:
                    if (units.get(i).structureIsBuilt() < 0.5) {
                        factoriesInProgress++;
                        structuresInProgress.add(units.get(i).id());
                        unitCounts.put(units.get(i).unitType(), unitCounts.get(units.get(i).unitType()) - 1);
                    }
                    break;
                case Rocket:
                    if (units.get(i).structureIsBuilt() < 0.5) {
                        rocketsInProgress++;
                        structuresInProgress.add(units.get(i).id());
                        unitCounts.put(units.get(i).unitType(), unitCounts.get(units.get(i).unitType()) - 1);
                    }
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
        if (gc.round() * FACTORY_SHORTAGE_COEFF > unitCounts.get(UnitType.Factory) + factoriesInProgress) {
            factoriesInProgress++;
            return WorkerTask.START_BUILD_FACTORY;
        }
        if (gc.researchInfo().getLevel(UnitType.Rocket) != 0 && gc.round() * ROCKET_SHORTAGE_COEFF > unitCounts.get(UnitType.Rocket) + rocketsInProgress) {
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
        return (loc.getPlanet() == Planet.Mars ? "M" : "E") + " " + loc.getX() + " " + loc.getY();
    }
    MapLocation decryptMapLocation(String loc) {
        final String[] split = loc.split(" ");
        Planet p;
        if (split[0].equals("M")) {
            p = Planet.Mars;
        }
        else {
            p = Planet.Earth;
        }
        return new MapLocation(p, Integer.parseInt(split[1]), Integer.parseInt(split[2]));
    }
    void debug(String num) {
        System.out.println("DEBUG " + num);
    }
}
