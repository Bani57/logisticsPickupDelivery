package template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;
import template.ActionRep.ActionName;
import template.comparators.TaskWeightComparator;
import template.comparators.VehicleCapacityComparator;
import template.comparators.CityDistanceFromVehicleComparator;

import java.util.Random;

public class VariablesSet {

	private ArrayList<Vehicle> vehicles;
	private ArrayList<Task> tasks;

	private ArrayList<Vehicle> vehicle;
	private ArrayList<Integer> pickupTime;
	private ArrayList<Integer> deliveryTime;
	private ArrayList<ActionRep> nextAction;
	private ArrayList<ActionRep> nextActionAfterPickup;
	private ArrayList<ActionRep> nextActionAfterDelivery;

	private StringBuilder descr;

	public VariablesSet(List<Vehicle> vehicles, TaskSet tasks) {
		super();

		this.vehicles = new ArrayList<>(vehicles);
		this.tasks = new ArrayList<>(tasks);

		int numTasks = tasks.size();
		int numVehicles = vehicles.size();

		this.vehicle = new ArrayList<>(Collections.nCopies(numTasks, (Vehicle) null));
		this.pickupTime = new ArrayList<>(Collections.nCopies(numTasks, 0));
		this.deliveryTime = new ArrayList<>(Collections.nCopies(numTasks, 0));
		this.nextAction = new ArrayList<>(Collections.nCopies(numVehicles, (ActionRep) null));
		this.nextActionAfterPickup = new ArrayList<>(Collections.nCopies(numTasks, (ActionRep) null));
		this.nextActionAfterDelivery = new ArrayList<>(Collections.nCopies(numTasks, (ActionRep) null));
	}

	public VariablesSet(ArrayList<Vehicle> vehicles, ArrayList<Task> tasks, ArrayList<Vehicle> vehicle,
			ArrayList<Integer> pickupTime, ArrayList<Integer> deliveryTime, ArrayList<ActionRep> nextAction,
			ArrayList<ActionRep> nextActionAfterPickup, ArrayList<ActionRep> nextActionAfterDelivery) {
		super();
		this.vehicles = vehicles;
		this.tasks = tasks;
		this.vehicle = vehicle;
		this.pickupTime = pickupTime;
		this.deliveryTime = deliveryTime;
		this.nextAction = nextAction;
		this.nextActionAfterPickup = nextActionAfterPickup;
		this.nextActionAfterDelivery = nextActionAfterDelivery;
	}

	public boolean init(Topology topology, int initialSolutionId) {
		switch (initialSolutionId) {
		case 1:
			return initLargestCapacityHeaviestTasks();
		case 2:
			return initClosestVehicle(topology);
		case 3:
			return initCheapestCostLightestTasks();
		default:
			return false;
		}
	}

	public boolean initLargestCapacityHeaviestTasks() {

		ArrayList<Vehicle> sortedVehicles = new ArrayList<>(vehicles);
		sortedVehicles.sort(new VehicleCapacityComparator().reversed());

		ArrayList<Task> sortedTasks = new ArrayList<>(tasks);
		sortedTasks.sort(new TaskWeightComparator().reversed());

		// If there is a task with weight greater than the capacity of all vehicles,
		// there is no solution
		if (sortedTasks.get(0).weight > sortedVehicles.get(0).capacity())
			return false;

		int currentVehicleIndex = 0; // index of the currently analyzed vehicle in sortedVehicles
		int currentTaskIndex = 0; // index of the currently analyzed task in sortedTasks

		ArrayList<VehicleState> vehicleStates = new ArrayList<>(sortedVehicles.size());
		for (int i = 0; i < sortedVehicles.size(); i++)
			vehicleStates.add(new VehicleState(0, new ArrayList<Task>(), null));
		Vehicle currentVehicle = sortedVehicles.get(0);
		VehicleState currentVehicleState = vehicleStates.get(0);

		while (currentTaskIndex < sortedTasks.size()) {

			Task task = sortedTasks.get(currentTaskIndex);

			if (currentVehicleState.weightSum() + task.weight <= currentVehicle.capacity()) {

				this.pickupTask(task, currentVehicle, currentVehicleState);
				currentTaskIndex++;
			}

			else {

				// If not, first deliver all picked-up tasks
				this.deliverCarriedTasks(currentVehicleState);

				currentVehicleIndex++;

				// If total weight of all tasks > total capacity of all vehicles,
				// reset the whole procedure for the remaining subset of tasks,
				// as all the vehicles are free again after they have delivered all of their
				// tasks
				if (currentVehicleIndex == sortedVehicles.size())
					currentVehicleIndex = 0;

				// Proceed to the next vehicle and reset all vehicle-related variables
				currentVehicle = sortedVehicles.get(currentVehicleIndex);
				currentVehicleState = vehicleStates.get(currentVehicleIndex);
			}
		}

		// Deliver any remaining tasks
		this.deliverCarriedTasks(currentVehicleState);

		return true;
	}

	public boolean initClosestVehicle(Topology topology) {

		int maxCapacity = Collections.max(vehicles, new VehicleCapacityComparator()).capacity();
		int maxTaskWeight = Collections.max(tasks, new TaskWeightComparator()).weight;

		// If there is a task with weight greater than the capacity of all vehicles,
		// there is no solution
		if (maxTaskWeight > maxCapacity)
			return false;

		ArrayList<VehicleState> vehicleStates = new ArrayList<>(vehicles.size());
		for (int i = 0; i < vehicles.size(); i++)
			vehicleStates.add(new VehicleState(0, new ArrayList<Task>(), null));
		Vehicle currentVehicle = vehicles.get(0);
		VehicleState currentVehicleState = vehicleStates.get(0);

		HashMap<City, List<Vehicle>> homeCitiesMap = new HashMap<>();
		for (City c : topology.cities())
			homeCitiesMap.put(c, new ArrayList<Vehicle>());
		for (Vehicle v : vehicles)
			homeCitiesMap.get(v.homeCity()).add(v);

		for (Task task : tasks) {

			PriorityQueue<City> queue = new PriorityQueue<>(new CityDistanceFromVehicleComparator(task));
			queue.add(task.pickupCity);
			HashSet<City> visitedCities = new HashSet<>();

			boolean taskAssigned = false;

			while (!queue.isEmpty() && !taskAssigned) {
				City currentCity = queue.poll();

				List<Vehicle> closestVehicles = homeCitiesMap.get(currentCity);
				for (Vehicle v : closestVehicles) {

					currentVehicle = v;

					currentVehicleState = vehicleStates.get(currentVehicle.id());

					if (currentVehicleState.weightSum() + task.weight <= currentVehicle.capacity()) {

						this.pickupTask(task, currentVehicle, currentVehicleState);

						taskAssigned = true;
						break;
					}
				}

				for (City neighborCity : currentCity.neighbors()) {
					if (!visitedCities.contains(neighborCity)) {
						queue.add(neighborCity);
						visitedCities.add(neighborCity);
					}
				}
			}

			if (!taskAssigned) {

				// First, deliver all picked-up tasks for each vehicle
				for (VehicleState vehicleState : vehicleStates) {
					this.deliverCarriedTasks(vehicleState);
				}

				// Then make the last closest vehicle pickup the unassigned task
				this.pickupTask(task, currentVehicle, currentVehicleState);
			}
		}

		// Deliver any remaining picked-up tasks
		for (VehicleState vehicleState : vehicleStates) {
			this.deliverCarriedTasks(vehicleState);
		}

		return true;
	}

	public boolean initCheapestCostLightestTasks() {

		int maxCapacity = Collections.max(vehicles, new VehicleCapacityComparator()).capacity();
		int maxTaskWeight = Collections.max(tasks, new TaskWeightComparator()).weight;

		// If there is a task with weight greater than the capacity of all vehicles,
		// there is no solution
		if (maxTaskWeight > maxCapacity)
			return false;

		ArrayList<Vehicle> sortedVehicles = new ArrayList<>(vehicles);
		sortedVehicles.sort(new VehicleCapacityComparator());

		ArrayList<Task> sortedTasks = new ArrayList<>(tasks);
		sortedTasks.sort(new TaskWeightComparator());

		int currentVehicleIndex = 0; // index of the currently analyzed vehicle in sortedVehicles
		int currentTaskIndex = 0; // index of the currently analyzed task in sortedTasks

		ArrayList<VehicleState> vehicleStates = new ArrayList<>(sortedVehicles.size());
		for (int i = 0; i < sortedVehicles.size(); i++)
			vehicleStates.add(new VehicleState(0, new ArrayList<Task>(), null));
		Vehicle currentVehicle = sortedVehicles.get(0);
		VehicleState currentVehicleState = vehicleStates.get(0);

		while (currentTaskIndex < sortedTasks.size()) {

			Task task = sortedTasks.get(currentTaskIndex);

			if (currentVehicleState.weightSum() + task.weight <= currentVehicle.capacity()) {

				this.pickupTask(task, currentVehicle, currentVehicleState);
				currentTaskIndex++;
			}

			else {

				// If not, first deliver all picked-up tasks
				this.deliverCarriedTasks(currentVehicleState);

				currentVehicleIndex++;

				// If total weight of all tasks > total capacity of all vehicles,
				// reset the whole procedure for the remaining subset of tasks,
				// as all the vehicles are free again after they have delivered all of their
				// tasks
				if (currentVehicleIndex == sortedVehicles.size())
					currentVehicleIndex = 0;

				// Proceed to the next vehicle and reset all vehicle-related variables
				currentVehicle = sortedVehicles.get(currentVehicleIndex);
				currentVehicleState = vehicleStates.get(currentVehicleIndex);
			}
		}

		// Deliver any remaining tasks
		this.deliverCarriedTasks(currentVehicleState);

		return true;
	}

	public void deliverCarriedTasks(VehicleState currentVehicleState) {

		// Deliver any remaining tasks
		int i = 0;
		Task prevTask = null;
		ArrayList<Task> carriedTasks = currentVehicleState.getCarriedTasks();
		for (Task t : carriedTasks) {
			if (i == 0)
				this.setNextActionAfterPickup(currentVehicleState.getPreviousAction().getTask().id,
						new ActionRep(t, ActionName.DELIVER));
			else
				this.setNextActionAfterDelivery(prevTask.id, new ActionRep(t, ActionName.DELIVER));
			this.setDeliveryTime(t.id, currentVehicleState.getTime());

			currentVehicleState.setTime(currentVehicleState.getTime() + 1);

			prevTask = t;
			i++;
		}

		// Remove all the tasks from the vehicle state
		currentVehicleState.setCarriedTasks(new ArrayList<Task>());

		// Only need to update the previous action once after the final delivery
		currentVehicleState.setPreviousAction(new ActionRep(prevTask, ActionName.DELIVER));
	}

	public void pickupTask(Task task, Vehicle currentVehicle, VehicleState currentVehicleState) {

		this.setVehicle(task.id, currentVehicle);

		int currentVehicleTime = currentVehicleState.getTime();

		if (currentVehicleTime == 0) {
			// The first action is always to pickup
			this.setNextAction(currentVehicle.id(), new ActionRep(task, ActionName.PICKUP));
			this.setPickupTime(task.id, currentVehicleTime);

			currentVehicleState.setPreviousAction(new ActionRep(task, ActionName.PICKUP));
			currentVehicleState.addCarriedTask(task);
		} else {

			// Check the type of the previous action done by this vehicle
			switch (currentVehicleState.getPreviousAction().getAction()) {
			case PICKUP:
				// If it was pickup, update nextActionAfterPickup for the previous task
				this.setNextActionAfterPickup(currentVehicleState.getPreviousAction().getTask().id,
						new ActionRep(task, ActionName.PICKUP));
				break;
			case DELIVER:
				// If it was deliver, update nextActionAfterDelivery for the previous task
				this.setNextActionAfterDelivery(currentVehicleState.getPreviousAction().getTask().id,
						new ActionRep(task, ActionName.PICKUP));
				break;
			}
			this.setPickupTime(task.id, currentVehicleTime);

			currentVehicleState.setPreviousAction(new ActionRep(task, ActionName.PICKUP));
			currentVehicleState.addCarriedTask(task);
		}
		currentVehicleState.setTime(currentVehicleTime + 1);
	}

	public List<VariablesSet> chooseNeighbors() {

		VariablesSet n;
		ArrayList<VariablesSet> neighbors = new ArrayList<>();

		for (Task t : tasks) {
			for (Vehicle v : vehicles) {
				n = moveTaskToVehicle(t, v);
				if (n != null) {
					if (!n.isValid()) {
						System.out.println("\n>>>>> INVALID NEIGHBOR <<<<<\n" + n.toString());
						System.out.println("\n>>>>> Applied tranformation: " + n.descr());
						System.exit(0);
					}
					neighbors.add(n);
				}
			}
		}

		for (Vehicle v : vehicles) {
			ArrayList<Task> carriedTasks = new ArrayList<>();
			for (int tid = 0; tid < this.vehicle.size(); tid++) {
				if (getVehicle(tid).equals(v))
					carriedTasks.add(tasks.get(tid));
			}
			for (Task t : carriedTasks) {
				for (int newPickupTime = 0; newPickupTime < 2 * carriedTasks.size(); newPickupTime++) {
					n = changePickupTime(t, newPickupTime);
					if (n != null) {
						if (!n.isValid()) {
							System.out.println("\n>>>>> INVALID NEIGHBOR <<<<<\n" + n.toString());
							System.out.println("\n>>>>> Applied tranformation: " + n.descr());
							System.exit(0);
						}
						neighbors.add(n);
					}
				}
			}
		}

		for (Vehicle v : vehicles) {
			ArrayList<Task> carriedTasks = new ArrayList<>();
			for (int tid = 0; tid < this.vehicle.size(); tid++) {
				if (getVehicle(tid).equals(v))
					carriedTasks.add(tasks.get(tid));
			}

			for (Task t : carriedTasks) {
				for (int newDeliveryTime = 0; newDeliveryTime < 2 * carriedTasks.size(); newDeliveryTime++) {
					n = changeDeliveryTime(t, newDeliveryTime);
					if (n != null) {
						if (!n.isValid()) {
							System.out.println("\n>>>>> INVALID NEIGHBOR <<<<<\n" + n.toString());
							System.out.println("\n>>>>> Applied tranformation: " + n.descr());
							System.exit(0);
						}
						neighbors.add(n);
					}
				}
			}
		}

		return neighbors;
	}

	public VariablesSet moveTaskToVehicle(Task t, Vehicle v) {

		Vehicle oldVehicle = this.getVehicle(t.id);

		if (oldVehicle.id() == v.id())
			return null;

		ArrayList<ActionRep> actionsOldVehicle = this.inferActionSequenceForVehicle(oldVehicle);
		ArrayList<ActionRep> actionsNewVehicle = this.inferActionSequenceForVehicle(v);

		ActionRep pickupAction = new ActionRep(t, ActionName.PICKUP);
		ActionRep deliverAction = new ActionRep(t, ActionName.DELIVER);

		StringBuilder neighborDescr = new StringBuilder();

		neighborDescr.append("MOVE TASK TO VEHICLE\n")
				.append("t" + t.id + ": v" + oldVehicle.id() + " --> v" + v.id() + "\n")
				.append("v" + oldVehicle.id() + " BEFORE : " + actionsOldVehicle + "\n")
				.append("v" + v.id() + " BEFORE: " + actionsNewVehicle);

		actionsOldVehicle.remove(pickupAction);
		actionsOldVehicle.remove(deliverAction);
		actionsNewVehicle.add(pickupAction);
		actionsNewVehicle.add(deliverAction);

		neighborDescr.append("v" + oldVehicle.id() + " AFTER: " + actionsOldVehicle + "\n")
				.append("v" + v.id() + " AFTER: " + actionsNewVehicle + "\n\n");

		VariablesSet neighbor = (VariablesSet) this.clone();

		neighbor.updateVariablesForVehicle(oldVehicle, actionsOldVehicle);
		neighbor.updateVariablesForVehicle(v, actionsNewVehicle);

		neighborDescr.append("It turned\n").append(this.toString()).append("\n\ninto\n").append(neighbor.toString());

		neighbor.setDescr(neighborDescr);

		// Check load constraint for old vehicle
		if (!neighbor.isLoadConstraintSatisfied(oldVehicle))
			return null;

		// Check load constraint for new vehicle
		if (!neighbor.isLoadConstraintSatisfied(v))
			return null;

		return neighbor;
	}

	public VariablesSet changePickupTime(Task t, int newPickupTime) {
		VariablesSet neighbor;

		int tPickupTime = this.getPickupTime(t.id);
		int tDeliveryTime = this.getDeliveryTime(t.id);

		// If the new pickup time is equal to the previous one for the task, there is no
		// valid neighbor
		if (tPickupTime == newPickupTime)
			return null;

		// If the new pickup time is after the current delivery time of the task,
		// then there is no valid neighbor
		if (newPickupTime >= tDeliveryTime)
			return null;

		neighbor = (VariablesSet) this.clone();

		Vehicle v = getVehicle(t.id);

		ArrayList<ActionRep> actionsVehicle = this.inferActionSequenceForVehicle(v);

		StringBuilder neighborDescr = new StringBuilder();

		neighborDescr.append("CHANGE PICKUP v" + getVehicle(t.id).id() + "\n")
				.append("(t" + t.id + ", PICKUP): " + tPickupTime + " --> " + newPickupTime + "\n")
				.append("BEFORE" + actionsVehicle + "\n");

		ActionRep pickupAction = actionsVehicle.get(tPickupTime);

		actionsVehicle.remove(tPickupTime);
		actionsVehicle.add(newPickupTime, pickupAction);

		neighborDescr.append("AFTER" + actionsVehicle + "\n\n");

		neighbor.updateVariablesForVehicle(v, actionsVehicle);

		neighborDescr.append("It turned\n").append(this.toString()).append("\n\ninto\n").append(neighbor.toString());

		neighbor.setDescr(neighborDescr);

		// Check load constraint for new vehicle
		if (!neighbor.isLoadConstraintSatisfied(v))
			return null;

		return neighbor;
	}

	public VariablesSet changeDeliveryTime(Task t, int newDeliveryTime) {
		VariablesSet neighbor;

		int tPickupTime = this.getPickupTime(t.id);
		int tDeliveryTime = this.getDeliveryTime(t.id);

		// If the new pickup time is equal to the previous one for the task, there is no
		// valid neighbor
		if (tDeliveryTime == newDeliveryTime)
			return null;

		// If the new pickup time is after the current delivery time of the task,
		// then there is no valid neighbor
		if (newDeliveryTime <= tPickupTime)
			return null;

		neighbor = (VariablesSet) this.clone();

		Vehicle v = getVehicle(t.id);

		ArrayList<ActionRep> actionsVehicle = this.inferActionSequenceForVehicle(v);

		ActionRep deliveryAction = actionsVehicle.get(tDeliveryTime);

		StringBuilder neighborDescr = new StringBuilder();

		neighborDescr.append("CHANGE DELIVERY v" + getVehicle(t.id).id() + "\n")
				.append("t" + t.id + ", DELIVERY): " + tDeliveryTime + " --> " + newDeliveryTime + "\n")
				.append("BEFORE" + actionsVehicle + "\n");

		actionsVehicle.remove(tDeliveryTime);
		if (newDeliveryTime == actionsVehicle.size())
			actionsVehicle.add(deliveryAction);
		else
			actionsVehicle.add(newDeliveryTime, deliveryAction);

		neighborDescr.append("AFTER" + actionsVehicle + "\n\n");

		neighbor.updateVariablesForVehicle(v, actionsVehicle);

		neighborDescr.append("It turned\n").append(this.toString()).append("\n\ninto\n").append(neighbor.toString());

		neighbor.setDescr(neighborDescr);

		// Check load constraint for new vehicle
		if (!neighbor.isLoadConstraintSatisfied(v))
			return null;

		return neighbor;
	}

	public VariablesSet localChoice(double p) {

		// Random sample a Bernoulli(p) distribution to know whether to return the old
		// solution or the best neighbor

		int chooseBestNeighbor;

		if (p == 0)
			chooseBestNeighbor = 0;
		else if (p == 1)
			chooseBestNeighbor = 1;
		else {
			uchicago.src.sim.util.Random.createBinomial(1, p);
			chooseBestNeighbor = uchicago.src.sim.util.Random.binomial.nextInt(1, p);
		}

		List<VariablesSet> candidateNeighbors = this.chooseNeighbors();

		if (candidateNeighbors.isEmpty())
			return this;

		if (chooseBestNeighbor == 0) {
			// Pick uniformly at random one of the solutions
			Random rng = new Random();
			int candidateNeighborIndex = rng.nextInt(candidateNeighbors.size());
			VariablesSet candidateNeighbor = candidateNeighbors.get(candidateNeighborIndex);
//			System.out.println("\n" + candidateNeighbor.toString());
			return candidateNeighbor;
		}

		double bestObjectiveValue = Double.POSITIVE_INFINITY;
		ArrayList<VariablesSet> bestCandidateNeighbors = new ArrayList<>();

		// Compute the objective function for every candidate neighbor solution and find
		// the collection of solutions with equal lowest cost
		for (VariablesSet candidateNeighbor : candidateNeighbors) {
			double neighborObjectiveValue = candidateNeighbor.computeObjective();
			if (neighborObjectiveValue < bestObjectiveValue) {
				bestObjectiveValue = neighborObjectiveValue;
				bestCandidateNeighbors = new ArrayList<>();
				bestCandidateNeighbors.add(candidateNeighbor);
			} else if (neighborObjectiveValue == bestObjectiveValue)
				bestCandidateNeighbors.add(candidateNeighbor);
		}

		// Pick uniformly at random one of the solutions with the same optimal cost
		Random rng = new Random();
		int bestCandidateNeighborIndex = rng.nextInt(bestCandidateNeighbors.size());
		VariablesSet bestCandidateNeighbor = bestCandidateNeighbors.get(bestCandidateNeighborIndex);

//		System.out.println("\n" + bestCandidateNeighbor.toString());
		return bestCandidateNeighbor;
	}

	public double computeObjective() {

		double objectiveValue = 0;

		// Add to the cost all travel costs between vehicles' starting cities and pickup
		// cities of first tasks
		for (Vehicle v : vehicles) {
			ActionRep vehicleFirstTask = this.getNextAction(v.id());
			if (vehicleFirstTask != null)
				objectiveValue += v.costPerKm() * vehicleFirstTask.getTask().pickupCity.distanceTo(v.homeCity());
		}

		// Add to the cost all travel costs between tasks' pickup cities and
		// pickup/delivery cities of next tasks
		for (Task t : tasks) {
			ActionRep taskNextActionAfterPickup = this.getNextActionAfterPickup(t.id);
			if (taskNextActionAfterPickup != null) {
				switch (taskNextActionAfterPickup.getAction()) {
				case PICKUP:
					objectiveValue += this.getVehicle(t.id).costPerKm()
							* t.pickupCity.distanceTo(taskNextActionAfterPickup.getTask().pickupCity);
					break;
				case DELIVER:
					objectiveValue += this.getVehicle(t.id).costPerKm()
							* t.pickupCity.distanceTo(taskNextActionAfterPickup.getTask().deliveryCity);
					break;
				}
			}
		}

		// Add to the cost all travel costs between tasks' delivery cities and
		// pickup/delivery cities of next tasks
		for (Task t : tasks) {
			ActionRep taskNextActionAfterDelivery = this.getNextActionAfterDelivery(t.id);
			if (taskNextActionAfterDelivery != null) {
				switch (taskNextActionAfterDelivery.getAction()) {
				case PICKUP:
					objectiveValue += this.getVehicle(t.id).costPerKm()
							* t.deliveryCity.distanceTo(taskNextActionAfterDelivery.getTask().pickupCity);
					break;
				case DELIVER:
					objectiveValue += this.getVehicle(t.id).costPerKm()
							* t.deliveryCity.distanceTo(taskNextActionAfterDelivery.getTask().deliveryCity);
					break;
				}
			}
		}

		return objectiveValue;
	}

	public boolean isLoadConstraintSatisfied(Vehicle v) {

		ActionRep currentVehicleAction = this.getNextAction(v.id());
		int currentLoad = 0;

		while (currentVehicleAction != null) {

			Task currentTask = currentVehicleAction.getTask();

			switch (currentVehicleAction.getAction()) {

			case PICKUP:
				currentLoad += currentTask.weight;
				if (currentLoad > v.capacity())
					return false;
				currentVehicleAction = this.getNextActionAfterPickup(currentTask.id);
				break;
			case DELIVER:
				currentLoad -= currentTask.weight;
				currentVehicleAction = this.getNextActionAfterDelivery(currentTask.id);
				break;
			}
		}

		return true;
	}

	public List<Plan> inferPlans() {

		ArrayList<Plan> plans = new ArrayList<>();

		for (Vehicle currentVehicle : vehicles) {

			City currentVehicleLocation = currentVehicle.homeCity();
			Plan plan = new Plan(currentVehicleLocation);

			ActionRep currentVehicleAction = this.getNextAction(currentVehicle.id());

			while (currentVehicleAction != null) {

				Task currentVehicleTask = currentVehicleAction.getTask();

				switch (currentVehicleAction.getAction()) {

				case PICKUP:
					List<City> shortestPathToPickup = currentVehicleLocation.pathTo(currentVehicleTask.pickupCity);
					for (City c : shortestPathToPickup)
						plan.appendMove(c);
					currentVehicleLocation = currentVehicleTask.pickupCity;
					plan.appendPickup(currentVehicleTask);
					currentVehicleAction = this.getNextActionAfterPickup(currentVehicleTask.id);
					break;

				case DELIVER:
					List<City> shortestPathToDelivery = currentVehicleLocation.pathTo(currentVehicleTask.deliveryCity);
					for (City c : shortestPathToDelivery)
						plan.appendMove(c);
					currentVehicleLocation = currentVehicleTask.deliveryCity;
					plan.appendDelivery(currentVehicleTask);
					currentVehicleAction = this.getNextActionAfterDelivery(currentVehicleTask.id);
					break;
				}
			}

			plans.add(plan);
		}

		return plans;
	}

	public ArrayList<ActionRep> inferActionSequenceForVehicle(Vehicle v) {
		ArrayList<ActionRep> actionSequence = new ArrayList<>();

		ActionRep currentAction = this.getNextAction(v.id());

		while (currentAction != null) {
			actionSequence.add(currentAction);

			switch (currentAction.getAction()) {
			case PICKUP:
				currentAction = this.getNextActionAfterPickup(currentAction.getTask().id);
				break;
			case DELIVER:
				currentAction = this.getNextActionAfterDelivery(currentAction.getTask().id);
				break;
			}
		}

		return actionSequence;
	}

	public void updateVariablesForVehicle(Vehicle v, ArrayList<ActionRep> actionSequence) {
		int vehicleTime = 0;
		ActionRep prevAction = null;

		for (ActionRep currentAction : actionSequence) {

			if (vehicleTime == 0) {
				this.setNextAction(v.id(), currentAction);
				this.setPickupTime(currentAction.getTask().id, vehicleTime);

			} else {
				switch (prevAction.getAction()) {
				case PICKUP:
					this.setNextActionAfterPickup(prevAction.getTask().id, currentAction);
					break;
				case DELIVER:
					this.setNextActionAfterDelivery(prevAction.getTask().id, currentAction);
					break;
				}
				switch (currentAction.getAction()) {
				case PICKUP:
					this.setPickupTime(currentAction.getTask().id, vehicleTime);
					break;
				case DELIVER:
					this.setDeliveryTime(currentAction.getTask().id, vehicleTime);
					break;
				}
			}

			this.setVehicle(currentAction.getTask().id, v);
			vehicleTime++;
			prevAction = currentAction;
		}

		if (prevAction == null)
			// if prevAction is null, it means that there is no action in the sequence,
			// so set nextAction of the vehicle to null
			this.setNextAction(v.id(), null);

		else
			// if prevAction is not null, it means that the last updated action for v is a
			// delivery,
			// so set nextActionAfterDelivery of the last delivered task to null
			this.setNextActionAfterDelivery(prevAction.getTask().id, null);
	}

	public boolean isValid() {
		for (Vehicle v : this.vehicles) {
			List<ActionRep> actionSeq = this.inferActionSequenceForVehicle(v);
			if (actionSeq.size() % 2 != 0) {
				return false;
			}
		}
		return true;
	}

	public String descr() {
		return descr.toString();
	}

	public void setDescr(StringBuilder descr) {
		this.descr = descr;
	}

	public ArrayList<Vehicle> getVehicle() {
		return vehicle;
	}

	public Vehicle getVehicle(int tid) {
		return vehicle.get(tid);
	}

	public void setVehicle(ArrayList<Vehicle> vehicle) {
		this.vehicle = vehicle;
	}

	public void setVehicle(int tid, Vehicle v) {
		this.vehicle.set(tid, v);
	}

	public ArrayList<Integer> getPickupTime() {
		return pickupTime;
	}

	public Integer getPickupTime(int tid) {
		return pickupTime.get(tid);
	}

	public void setPickupTime(ArrayList<Integer> pickupTime) {
		this.pickupTime = pickupTime;
	}

	public void setPickupTime(int tid, Integer time) {
		this.pickupTime.set(tid, time);
	}

	public ArrayList<Integer> getDeliveryTime() {
		return deliveryTime;
	}

	public Integer getDeliveryTime(int tid) {
		return deliveryTime.get(tid);
	}

	public void setDeliveryTime(ArrayList<Integer> deliveryTime) {
		this.deliveryTime = deliveryTime;
	}

	public void setDeliveryTime(int tid, Integer time) {
		this.deliveryTime.set(tid, time);
	}

	public ArrayList<ActionRep> getNextAction() {
		return nextAction;
	}

	public ActionRep getNextAction(int vid) {
		return nextAction.get(vid);
	}

	public void setNextAction(ArrayList<ActionRep> nextAction) {
		this.nextAction = nextAction;
	}

	public void setNextAction(int vid, ActionRep a) {
		this.nextAction.set(vid, a);
	}

	public ArrayList<ActionRep> getNextActionAfterPickup() {
		return nextActionAfterPickup;
	}

	public ActionRep getNextActionAfterPickup(int tid) {
		return nextActionAfterPickup.get(tid);
	}

	public void setNextActionAfterPickup(ArrayList<ActionRep> nextActionAfterPickup) {
		this.nextActionAfterPickup = nextActionAfterPickup;
	}

	public void setNextActionAfterPickup(int tid, ActionRep a) {
		this.nextActionAfterPickup.set(tid, a);
	}

	public ArrayList<ActionRep> getNextActionAfterDelivery() {
		return nextActionAfterDelivery;
	}

	public ActionRep getNextActionAfterDelivery(int tid) {
		return nextActionAfterDelivery.get(tid);
	}

	public void setNextActionAfterDelivery(ArrayList<ActionRep> nextActionAfterDelivery) {
		this.nextActionAfterDelivery = nextActionAfterDelivery;
	}

	public void setNextActionAfterDelivery(int tid, ActionRep a) {
		this.nextActionAfterDelivery.set(tid, a);
	}

	@Override
	protected Object clone() {
		ArrayList<Vehicle> vehicleNew = new ArrayList<>(this.vehicle);
		ArrayList<Integer> pickupTimeNew = new ArrayList<>(this.pickupTime);
		ArrayList<Integer> deliveryTimeNew = new ArrayList<>(this.deliveryTime);
		ArrayList<ActionRep> nextActionNew = new ArrayList<>(this.nextAction);
		ArrayList<ActionRep> nextActionAfterPickupNew = new ArrayList<>(this.nextActionAfterPickup);
		ArrayList<ActionRep> nextActionAfterDeliveryNew = new ArrayList<>(this.nextActionAfterDelivery);

		return new VariablesSet(vehicles, tasks, vehicleNew, pickupTimeNew, deliveryTimeNew, nextActionNew,
				nextActionAfterPickupNew, nextActionAfterDeliveryNew);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();

		// Append vehicle
		str.append("vehicle : [t0 -> v").append(getVehicle(0).id());
		for (int i = 1; i < vehicle.size(); i++) {
			str.append(", t").append(i).append(" -> v").append(getVehicle(i).id());
		}

		str.append("]\n");

		// Append pickupTime
		str.append("pickupTime : [t0 -> ").append(getPickupTime(0));
		for (int i = 1; i < pickupTime.size(); i++) {
			str.append(", t").append(i).append(" -> ").append(getPickupTime(i));
		}

		str.append("]\n");

		// Append deliveryTime
		str.append("deliveryTime : [t0 -> ").append(getDeliveryTime(0));
		for (int i = 1; i < deliveryTime.size(); i++) {
			str.append(", t").append(i).append(" -> ").append(getDeliveryTime(i));
		}

		str.append("]\n");

		// Append nextAction
		if (getNextAction(0) != null)
			str.append("nextAction : [v0 -> ").append(getNextAction(0).toString());
		for (int i = 1; i < nextAction.size(); i++) {
			if (getNextAction(i) != null)
				str.append(", v").append(i).append(" -> ").append(getNextAction(i).toString());
		}

		str.append("]\n");

		// Append nextActionAfterPickup
		if (getNextActionAfterPickup(0) != null)
			str.append("nextActionAfterPickup : [t0 -> ").append(getNextActionAfterPickup(0).toString());
		for (int i = 1; i < nextActionAfterPickup.size(); i++) {
			if (getNextActionAfterPickup(i) != null)
				str.append(", t").append(i).append(" -> ").append(getNextActionAfterPickup(i).toString());
		}

		str.append("]\n");

		// Append nextActionAfterDelivery, some entries might be null
		str.append("nextActionAfterDelivery : [t0 -> ");
		if (getNextActionAfterDelivery(0) == null)
			str.append("null");
		else
			str.append(getNextActionAfterDelivery(0).toString());

		for (int i = 1; i < nextActionAfterDelivery.size(); i++) {
			str.append(", t").append(i).append(" -> ");
			if (getNextActionAfterDelivery(i) == null)
				str.append("null");
			else
				str.append(getNextActionAfterDelivery(i).toString());
		}

		str.append("]");

		return str.toString();
	}
}
