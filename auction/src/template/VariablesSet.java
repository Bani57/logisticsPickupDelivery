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
import template.comparators.VehicleCostPerKmComparator;
import template.comparators.CityDistanceFromTaskComparator;

/**
 * Set of variables for the SLS algorithm.
 *
 * @author Andrej Janchevski
 * @author Orazio Rillo
 */
public class VariablesSet {

	private ArrayList<Vehicle> vehicles; //list of vehicles
	private ArrayList<Task> tasks; //list of tasks

	private ArrayList<Vehicle> vehicle; //at position i, contains the vehicle that carries the task with id i
	private ArrayList<Integer> pickupTime; //at position i, contains the pickup time of the task with id i 
	private ArrayList<Integer> deliveryTime; //at position i, contains the delivery time of the task with id i 
	private ArrayList<ActionRep> nextAction; //at position i, contains the first action performed by vehicle with id i 
	private ArrayList<ActionRep> nextActionAfterPickup; //at position i, contains the action performed after the pickup action of the task with id i
	private ArrayList<ActionRep> nextActionAfterDelivery; //at position i, contains the action performed after the delivery action of the task with id i

	private StringBuilder descr; //textual descriptor of the VariablesSet, only used for debugging

	/**
	 * Constructor which initializes a Variables set with empty arrays as attributes, 
	 * it only initializes the variables relative to the topology i.e. vehicles and tasks
	 */
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

	/**
	 * Constructor which initializes all the variables according to the given parameters
	 */
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

	/**
	 * This method builds the initial solution 
	 * @param topology Topology selected in the configuration file
	 * @param initialSolutionId int id of the selected initial solution (might be 1, 2, or 3)
	 * @return true if the solution has been build with success
	 * 		   false if there is no possible solution to the problem or the initial solution id is incorrect
	 */
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

	/**
	 * Method that builds the initial solution with id 1.
	 * It consists in assigning the heaviest tasks to the vehicle with largest capacity;
	 * once it is full, the vehicle with the second largest capacity is selected and so on
	 * 
	 * @return true for success, false for fail
	 */
	public boolean initLargestCapacityHeaviestTasks() {

		// Sort vehicles by capacity (largest first)
		ArrayList<Vehicle> sortedVehicles = new ArrayList<>(vehicles);
		sortedVehicles.sort(new VehicleCapacityComparator().reversed());

		// Sort tasks by weight (heaviest first)
		ArrayList<Task> sortedTasks = new ArrayList<>(tasks);
		sortedTasks.sort(new TaskWeightComparator().reversed());

		// If there is a task with weight greater than the capacity of all vehicles,
		// there is no solution
		if (sortedTasks.get(0).weight > sortedVehicles.get(0).capacity())
			return false;

		int currentVehicleIndex = 0; //index of the currently analyzed vehicle in sortedVehicles
		int currentTaskIndex = 0; //index of the currently analyzed task in sortedTasks

		// Build an array of vehicle states corresponding to the sortedVehicles
		ArrayList<VehicleState> vehicleStates = new ArrayList<>(sortedVehicles.size());
		for (int i = 0; i < sortedVehicles.size(); i++)
			vehicleStates.add(new VehicleState(0, new ArrayList<Task>(), null));
		
		Vehicle currentVehicle = sortedVehicles.get(0);
		VehicleState currentVehicleState = vehicleStates.get(0);

		while (currentTaskIndex < sortedTasks.size()) {

			Task task = sortedTasks.get(currentTaskIndex);
			
			if (currentVehicleState.weightSum() + task.weight <= currentVehicle.capacity()) {
				// If the task can be carried by the current vehicle assign that task to it
				this.assignPickupTask(task, currentVehicle, currentVehicleState);
				currentTaskIndex++;
			}

			else {

				// Otherwise, assign to the current vehicle the deliver of all picked-up tasks
				this.assignDeliverCarriedTasks(currentVehicleState);

				currentVehicleIndex++;

				// If total weight of all tasks > total capacity of all vehicles,
				// reset the whole procedure for the remaining subset of tasks,
				// as all the vehicles are free again after they have delivered all of their tasks
				if (currentVehicleIndex == sortedVehicles.size())
					currentVehicleIndex = 0;

				// Proceed to the next vehicle and reset all vehicle-related variables
				currentVehicle = sortedVehicles.get(currentVehicleIndex);
				currentVehicleState = vehicleStates.get(currentVehicleIndex);
			}
		}

		// Assign to the last considered vehicle a deliver action for every remaining tasks
		this.assignDeliverCarriedTasks(currentVehicleState);

		return true;
	}

	/**
	 * Method that builds the initial solution with id 2.
	 * It consists in assigning every task to the vehicle which is the closest to its pickup city
	 * 
	 * @param topology Topology selected in the configuration file
	 * @return true for success, false for fail
	 */
	public boolean initClosestVehicle(Topology topology) {

		int maxCapacity = Collections.max(vehicles, new VehicleCapacityComparator()).capacity();
		int maxTaskWeight = Collections.max(tasks, new TaskWeightComparator()).weight;

		// If there is a task with weight greater than the capacity of all vehicles,
		// there is no solution
		if (maxTaskWeight > maxCapacity)
			return false;

		// Build an array of vehicle states
		ArrayList<VehicleState> vehicleStates = new ArrayList<>(vehicles.size());
		for (int i = 0; i < vehicles.size(); i++)
			vehicleStates.add(new VehicleState(0, new ArrayList<Task>(), null));
		
		Vehicle currentVehicle = vehicles.get(0);
		VehicleState currentVehicleState = vehicleStates.get(0);

		// Map every city in the topology to the list of vehicles starting from that home city
		HashMap<City, List<Vehicle>> homeCitiesMap = new HashMap<>();
		for (City c : topology.cities())
			homeCitiesMap.put(c, new ArrayList<Vehicle>());
		for (Vehicle v : vehicles)
			homeCitiesMap.get(v.homeCity()).add(v);

		for (Task task : tasks) {

			// For every task, find the closest vehicles using a modified Djikstra search algorithm
			// Use a PriorityQueue to automatically order all vehicles
			// by the distance from their home city to the pickup city of the task
			PriorityQueue<City> queue = new PriorityQueue<>(new CityDistanceFromTaskComparator(task));
			queue.add(task.pickupCity);
			
			// Used for cycle detection
			HashSet<City> visitedCities = new HashSet<>();

			// Initially every task is unassigned
			boolean taskAssigned = false;

			while (!queue.isEmpty() && !taskAssigned) {
				
				// For every city get all of the vehicles having that city as home
				City currentCity = queue.poll();
				List<Vehicle> closestVehicles = homeCitiesMap.get(currentCity);
				
				for (Vehicle v : closestVehicles) {
					currentVehicle = v;
					currentVehicleState = vehicleStates.get(currentVehicle.id());
					
					// If the task can be carried by the current vehicle assign that task to it
					if (currentVehicleState.weightSum() + task.weight <= currentVehicle.capacity()) {

						this.assignPickupTask(task, currentVehicle, currentVehicleState);

						taskAssigned = true;
						break;
					}
				}

				// Add all neighboring cities not already visited to the PriorityQueue in order to sort them
				for (City neighborCity : currentCity.neighbors()) {
					if (!visitedCities.contains(neighborCity)) {
						queue.add(neighborCity);
						visitedCities.add(neighborCity);
					}
				}
			}

			// If it was not possible to assign the task...
			if (!taskAssigned) {

				// First, deliver all picked-up tasks for each vehicle
				for (VehicleState vehicleState : vehicleStates)
					this.assignDeliverCarriedTasks(vehicleState);

				// Then make the last closest vehicle pickup the unassigned task
				this.assignPickupTask(task, currentVehicle, currentVehicleState);
			}
		}

		// Deliver any remaining picked-up tasks
		for (VehicleState vehicleState : vehicleStates) 
			this.assignDeliverCarriedTasks(vehicleState);

		return true;
	}

	/**
	 * Method that builds the initial solution with id 1.
	 * It consists in assigning the heaviest tasks to the vehicle with largest capacity;
	 * once it is full, the vehicle with the second largest capacity is selected and so on
	 * 
	 * @return true for success, false for fail
	 */
	public boolean initCheapestCostLightestTasks() {

		int maxCapacity = Collections.max(vehicles, new VehicleCapacityComparator()).capacity();
		int maxTaskWeight = Collections.max(tasks, new TaskWeightComparator()).weight;

		// If there is a task with weight greater than the capacity of all vehicles,
		// there is no solution
		if (maxTaskWeight > maxCapacity)
			return false;

		// Sort vehicles according to their cost per km (cheapest cost first)
		ArrayList<Vehicle> sortedVehicles = new ArrayList<>(vehicles);
		sortedVehicles.sort(new VehicleCostPerKmComparator());

		// Sort tasks according to their weight (lightest first)
		ArrayList<Task> sortedTasks = new ArrayList<>(tasks);
		sortedTasks.sort(new TaskWeightComparator());

		int currentVehicleIndex = 0; //index of the currently analyzed vehicle in sortedVehicles
		int currentTaskIndex = 0; //index of the currently analyzed task in sortedTasks

		// Build an array of vehicle states corresponding to the sortedVehicles
		ArrayList<VehicleState> vehicleStates = new ArrayList<>(sortedVehicles.size());
		for (int i = 0; i < sortedVehicles.size(); i++)
			vehicleStates.add(new VehicleState(0, new ArrayList<Task>(), null));
		
		Vehicle currentVehicle = sortedVehicles.get(0);
		VehicleState currentVehicleState = vehicleStates.get(0);

		while (currentTaskIndex < sortedTasks.size()) {

			Task task = sortedTasks.get(currentTaskIndex);

			if (currentVehicleState.weightSum() + task.weight <= currentVehicle.capacity()) {
				// If the task can be carried by the current vehicle assign it to it
				this.assignPickupTask(task, currentVehicle, currentVehicleState);
				currentTaskIndex++;
			} else {

				// If not, assign to the vehicle a delivery action for allthe carried tasks
				this.assignDeliverCarriedTasks(currentVehicleState);

				currentVehicleIndex++;

				// If total weight of all tasks > total capacity of all vehicles,
				// reset the whole procedure for the remaining subset of tasks,
				// as all the vehicles are free again after they have delivered all of their tasks
				if (currentVehicleIndex == sortedVehicles.size())
					currentVehicleIndex = 0;

				// Proceed to the next vehicle and reset all vehicle-related variables
				currentVehicle = sortedVehicles.get(currentVehicleIndex);
				currentVehicleState = vehicleStates.get(currentVehicleIndex);
			}
		}

		// Assign to the last considered vehicle a deliver action for every remaining tasks
		this.assignDeliverCarriedTasks(currentVehicleState);

		return true;
	}
	
	/**
	 * Method that assigns a pickup action of the given task to the current vehicle
	 * In order to do this, it modifies the attributes of the current VariablesSet
	 * 
	 * @param task Task to pickup
	 * @param currentVehicle Vehicle to assign the task to
	 * @param currentVehicleState VehicleState of the vehicle
	 */
	public void assignPickupTask(Task task, Vehicle currentVehicle, VehicleState currentVehicleState) {

		this.setVehicle(task.id, currentVehicle);
		int currentVehicleTime = currentVehicleState.getTime();

		if (currentVehicleTime == 0) {
			// The first action is always to pickup
			this.setNextAction(currentVehicle.id(), new ActionRep(task, ActionName.PICKUP));
			this.setPickupTime(task.id, currentVehicleTime); 

			currentVehicleState.setPreviousAction(new ActionRep(task, ActionName.PICKUP));
			currentVehicleState.addCarriedTask(task);
		} else {

			// If the action is not the first one, update either nextActionAfterPickup or
			// nextActionAfterDelivery according to the type of the previous action done by this vehicle
			switch (currentVehicleState.getPreviousAction().getAction()) {
			case PICKUP:
				// If it is pickup, update nextActionAfterPickup for the previous task
				this.setNextActionAfterPickup(currentVehicleState.getPreviousAction().getTask().id,
						new ActionRep(task, ActionName.PICKUP));
				break;
			case DELIVER:
				// If it is deliver, update nextActionAfterDelivery for the previous task
				this.setNextActionAfterDelivery(currentVehicleState.getPreviousAction().getTask().id,
						new ActionRep(task, ActionName.PICKUP));
				break;
			}
			this.setPickupTime(task.id, currentVehicleTime);

			// Update vehicle state
			currentVehicleState.setPreviousAction(new ActionRep(task, ActionName.PICKUP));
			currentVehicleState.addCarriedTask(task);
		}
		currentVehicleState.setTime(currentVehicleTime + 1);
	}

	/**
	 * Method that assigns a deliver action for each of the carried tasks to the current vehicle
	 * In order to do this, it modifies the attributes of the current VariablesSet
	 * 
	 * @param currentVehicleState VehicleState of the current vehicle
	 */
	public void assignDeliverCarriedTasks(VehicleState currentVehicleState) {

		int i = 0;
		Task prevTask = null;
		ArrayList<Task> carriedTasks = currentVehicleState.getCarriedTasks();
		for (Task t : carriedTasks) {
			if (i == 0)
				// The first task's previous action is for sure a pickup
				this.setNextActionAfterPickup(currentVehicleState.getPreviousAction().getTask().id,
						new ActionRep(t, ActionName.DELIVER));
			else
				// From the second carried task on, the previous action is always a deliver one
				this.setNextActionAfterDelivery(prevTask.id, new ActionRep(t, ActionName.DELIVER));
			this.setDeliveryTime(t.id, currentVehicleState.getTime());

			currentVehicleState.setTime(currentVehicleState.getTime() + 1);

			prevTask = t;
			i++;
		}

		// Remove all the tasks from the vehicle state
		currentVehicleState.setCarriedTasks(new ArrayList<Task>());

		// Only update the previous action once after the final delivery
		if(prevTask != null)
			currentVehicleState.setPreviousAction(new ActionRep(prevTask, ActionName.DELIVER));
	}

	/**
	 * First method to generate a neighbor. It consists in assigning the task t to vehicle v.
	 * All the attributes of the VariablesSet are consequently updated
	 * 
	 * @param t Task that has to be reassigned
	 * @param v Vehicle to which t has to be assigned
	 * @return VariablesSet the neighbor obtained with this transformation
	 */
	public VariablesSet moveTaskToVehicle(Task t, Vehicle v) {

		Vehicle oldVehicle = this.getVehicle(t.id);

		// If the id of the new vehicle is the same of the id of the vehicle that carries t, 
		// there is no valid neighbor
		if (oldVehicle.id() == v.id())
			return null;

		// Infer the lists of actions of both the vehicle that used to carry t and the new one
		ArrayList<ActionRep> actionsOldVehicle = this.inferActionSequenceForVehicle(oldVehicle);
		ArrayList<ActionRep> actionsNewVehicle = this.inferActionSequenceForVehicle(v);
		
		// Create a pair of actions to assign to v
		ActionRep pickupAction = new ActionRep(t, ActionName.PICKUP);
		ActionRep deliverAction = new ActionRep(t, ActionName.DELIVER);

		StringBuilder neighborDescr = new StringBuilder();

//		if (CentralizedAgent.DEBUG) {
//			neighborDescr.append("MOVE TASK TO VEHICLE\n")
//					.append("t" + t.id + ": v" + oldVehicle.id() + " --> v" + v.id() + "\n")
//					.append("v" + oldVehicle.id() + " BEFORE : " + actionsOldVehicle + "\n")
//					.append("v" + v.id() + " BEFORE: " + actionsNewVehicle);
//		}
		
		// Modify the lists of vehicles' action in order to assign t to v
		actionsOldVehicle.remove(pickupAction);
		actionsOldVehicle.remove(deliverAction);
		actionsNewVehicle.add(pickupAction);
		actionsNewVehicle.add(deliverAction);

//		if (CentralizedAgent.DEBUG) {
//			neighborDescr.append("v" + oldVehicle.id() + " AFTER: " + actionsOldVehicle + "\n")
//					.append("v" + v.id() + " AFTER: " + actionsNewVehicle + "\n\n");
//		}
		
		VariablesSet neighbor = (VariablesSet) this.clone();

		// Update the clone's variable set according to the new lists of actions
		neighbor.updateVariablesForVehicle(oldVehicle, actionsOldVehicle);
		neighbor.updateVariablesForVehicle(v, actionsNewVehicle);

//		if (CentralizedAgent.DEBUG) {
//			neighborDescr.append("It turned\n").append(this.toString()).append("\n\ninto\n")
//					.append(neighbor.toString());
//			neighbor.setDescr(neighborDescr);
//		}
		
		// If the load constraint is not satisfied for the old vehicle, the neighbor is not valid
		if (!neighbor.isLoadConstraintSatisfied(oldVehicle))
			return null;
		
		// If the load constraint is not satisfied for the new vehicle, the neighbor is not valid
		if (!neighbor.isLoadConstraintSatisfied(v))
			return null;

		return neighbor;
	}
	
	/**
	 * Second method to generate a neighbor. It consists in changing the pickup time of a task.
	 * All the attributes of the VariablesSet are consequently updated
	 * 
	 * @param t Task which pickup time has to be modified
	 * @param newPickupTime int 
	 * @return VariablesSet the neighbor obtained with this transformation
	 */
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
		ArrayList<ActionRep> actionsVehicle = this.inferActionSequenceForVehicle(v); //list of actions of v
		ActionRep pickupAction = actionsVehicle.get(tPickupTime);

//		StringBuilder neighborDescr = new StringBuilder();
//		if (CentralizedAgent.DEBUG) {
//			neighborDescr.append("CHANGE PICKUP v" + getVehicle(t.id).id() + "\n")
//					.append("(t" + t.id + ", PICKUP): " + tPickupTime + " --> " + newPickupTime + "\n")
//					.append("BEFORE" + actionsVehicle + "\n");
//		}

		// Update the list of actions by moving the pickup time of t
		actionsVehicle.remove(tPickupTime);
		actionsVehicle.add(newPickupTime, pickupAction);

		// Update the clone's variable set according to the new list of actions
		neighbor.updateVariablesForVehicle(v, actionsVehicle);

//		if (CentralizedAgent.DEBUG) {
//			neighborDescr.append("AFTER" + actionsVehicle + "\n\n");
//			neighborDescr.append("It turned\n").append(this.toString()).append("\n\ninto\n").append(neighbor.toString());
//			neighbor.setDescr(neighborDescr);
//		}

		// If the load constraint is not satisfied for the new vehicle, the neighbor is not valid
		if (!neighbor.isLoadConstraintSatisfied(v))
			return null;

		return neighbor;
	}

	/**
	 * Third method to generate a neighbor. It consists in changing the delivery time of a task.
	 * All the attributes of the VariablesSet are consequently updated
	 * 
	 * @param t Task which pickup time has to be modified
	 * @param newDeliveryTime int 
	 * @return VariablesSet the neighbor obtained with this transformation
	 */
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
		ArrayList<ActionRep> actionsVehicle = this.inferActionSequenceForVehicle(v); //list of actions of v
		ActionRep deliveryAction = actionsVehicle.get(tDeliveryTime);
		
//		StringBuilder neighborDescr = new StringBuilder();
//		if (CentralizedAgent.DEBUG) {
//			neighborDescr.append("CHANGE DELIVERY v" + getVehicle(t.id).id() + "\n")
//					.append("t" + t.id + ", DELIVERY): " + tDeliveryTime + " --> " + newDeliveryTime + "\n")
//					.append("BEFORE" + actionsVehicle + "\n");
//		}

		// Update the list of actions by moving the pickup time of t.
		// Note that a delivery time can be the last action of a vehicle,
		// so this edge case is handled by just appending the action at the end of the list 
		// instead of specifying an index which would be out of bound
		actionsVehicle.remove(tDeliveryTime);
		if (newDeliveryTime == actionsVehicle.size())
			actionsVehicle.add(deliveryAction);
		else
			actionsVehicle.add(newDeliveryTime, deliveryAction);

		// Update the clone's variable set according to the new list of actions
		neighbor.updateVariablesForVehicle(v, actionsVehicle);
		
//		if (CentralizedAgent.DEBUG) {
//			neighborDescr.append("AFTER" + actionsVehicle + "\n\n");
//			neighborDescr.append("It turned\n").append(this.toString()).append("\n\ninto\n").append(neighbor.toString());
//			neighbor.setDescr(neighborDescr);
//		}
		
		// Check load constraint for new vehicle
		if (!neighbor.isLoadConstraintSatisfied(v))
			return null;

		return neighbor;
	}

	/**
	 * Method that computes the objective function's value in the current set of variables
	 * 
	 * @return double objective function's value
	 */
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
		// next actions' task's city
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
		// next actions' task's city
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

	/**
	 * This method checks whether the carried tasks' total weight exceeds or not the vehicle's capacity
	 * 
	 * @param v Vehicle
	 * @return true for constraint satisfied, false for constraint not satisfied
	 */
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

	/**
	 * Method that infers a plan for each vehicle according to the set of variables
	 * 
	 * @return List of plans for each vehicle ordered by vehicle id
	 */
	public List<Plan> inferPlans() {

		ArrayList<Plan> plans = new ArrayList<>();
		
		for (Vehicle currentVehicle : vehicles) {

			City currentVehicleLocation = currentVehicle.homeCity();
			
			//Initialize a new plan for vehicle v
			Plan plan = new Plan(currentVehicleLocation); 

			ActionRep currentVehicleAction = this.getNextAction(currentVehicle.id());

			// Keep iterating until the current action is not set to null, 
			// this happens when it is assigned to action that follows the last delivery action
			while (currentVehicleAction != null) {

				Task currentVehicleTask = currentVehicleAction.getTask();

				switch (currentVehicleAction.getAction()) {

				case PICKUP:
					// First add to the plan the action of moving to the city of pickup of the task
					List<City> shortestPathToPickup = currentVehicleLocation.pathTo(currentVehicleTask.pickupCity);
					for (City c : shortestPathToPickup)
						plan.appendMove(c);
					// Update vehicle's location
					currentVehicleLocation = currentVehicleTask.pickupCity;
					// Add to the plan pickup action 
					plan.appendPickup(currentVehicleTask);
					// Update the current action
					currentVehicleAction = this.getNextActionAfterPickup(currentVehicleTask.id);
					break;

				case DELIVER:
					// First add to the plan the action of moving to the city of delivery of the task
					List<City> shortestPathToDelivery = currentVehicleLocation.pathTo(currentVehicleTask.deliveryCity);
					for (City c : shortestPathToDelivery)
						plan.appendMove(c);
					// Update vehicle's location
					currentVehicleLocation = currentVehicleTask.deliveryCity;
					// Add to the plan deliver action 
					plan.appendDelivery(currentVehicleTask);
					// Update the current action
					currentVehicleAction = this.getNextActionAfterDelivery(currentVehicleTask.id);
					break;
				}
			}
			
			// Add the constructed plan the list of plans
			plans.add(plan);
		}

		return plans;
	}

	/**
	 * Method that infers a list of action for a given vehicle according to the current configuration of the variables
	 * 
	 * @param v Vehicle for which the plan has to be inferred
	 * @return List of actions 
	 */
	public ArrayList<ActionRep> inferActionSequenceForVehicle(Vehicle v) {
		ArrayList<ActionRep> actionSequence = new ArrayList<>();
		ActionRep currentAction = this.getNextAction(v.id());

		// Keep iterating until the current action is not set to null, 
		// this happens when it is assigned to action that follows the last delivery action
		while (currentAction != null) {
			// Add the current action to the sequence
			actionSequence.add(currentAction);
			switch (currentAction.getAction()) {
			case PICKUP:
				// If the current action is a pickup one, 
				// the consequent one will be stored in the array nextActionAfterPickup
				currentAction = this.getNextActionAfterPickup(currentAction.getTask().id);
				break;
			case DELIVER:
				// If the current action is a deliver one, 
				// the consequent one will be stored in the array nextActionAfterDelivery
				currentAction = this.getNextActionAfterDelivery(currentAction.getTask().id);
				break;
			}
		}

		return actionSequence;
	}

	/**
	 * Method used to update the current variables set according to the new actions sequence of a vehicle
	 * 
	 * @param v Vehicle
	 * @param actionSequence List
	 */
	public void updateVariablesForVehicle(Vehicle v, ArrayList<ActionRep> actionSequence) {
		int vehicleTime = 0; //virtual time of the vehicle, it takes into account both pickup and deliver actions
		ActionRep prevAction = null;

		for (ActionRep currentAction : actionSequence) {

			if (vehicleTime == 0) {
				// For the first action it is enough to update the first action of the vehicle
				// and the pickup time of the corresponding task
				this.setNextAction(v.id(), currentAction);
				this.setPickupTime(currentAction.getTask().id, vehicleTime);

			} else {
				// For all the other actions the variable to update depends on both the previous and the current actions
				// The previous action's type is used to know whether to update nextActionAfterPickup or nextActionAfterDelivery 
				switch (prevAction.getAction()) {
				case PICKUP:
					this.setNextActionAfterPickup(prevAction.getTask().id, currentAction);
					break;
				case DELIVER:
					this.setNextActionAfterDelivery(prevAction.getTask().id, currentAction);
					break;
				}
				
				// The current action's type is used to know whether to update pickupTime or deliveryTime 
				switch (currentAction.getAction()) {
				case PICKUP:
					this.setPickupTime(currentAction.getTask().id, vehicleTime);
					break;
				case DELIVER:
					this.setDeliveryTime(currentAction.getTask().id, vehicleTime);
					break;
				}
			}
			
			// In any case the vehicle array must be updated
			this.setVehicle(currentAction.getTask().id, v);
			
			vehicleTime++;
			prevAction = currentAction;
		}

		if (prevAction == null)
			// If prevAction is null, it means that there is no action in the sequence,
			// so set nextAction of the vehicle to null
			this.setNextAction(v.id(), null);

		else
			// If prevAction is not null, it means that at least one iteration of the for loop 
			// has been done and since the last updated action for v must be a delivery,
			// set nextActionAfterDelivery of the last delivered task to null
			this.setNextActionAfterDelivery(prevAction.getTask().id, null);
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
