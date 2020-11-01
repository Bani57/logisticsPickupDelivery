package template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import java.util.Random;

public class Variables {

	private ArrayList<Vehicle> vehicles;
	private ArrayList<Task> tasks;

	private ArrayList<Vehicle> vehicle;
	private ArrayList<Integer> pickupTime;
	private ArrayList<Integer> deliveryTime;
	private ArrayList<ActionRep> nextAction;
	private ArrayList<ActionRep> nextActionAfterPickup;
	private ArrayList<ActionRep> nextActionAfterDelivery;

	
	public Variables(List<Vehicle> vehicles, TaskSet tasks) {
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
	

	public Variables(ArrayList<Vehicle> vehicles, ArrayList<Task> tasks, ArrayList<Vehicle> vehicle,
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
	

	public void setToInitialSolution(Topology topology, int initialSolutionId) {
		switch (initialSolutionId) {
		case 1:
			setToInitialSolutionLargestCapacity();
			break;
		case 2:
			setToInitialSolutionClosestToHome(topology);
			break;
		case 3:
			setToInitialSolutionCheapestMore();
			break;
		default:
			System.out.println("Invalid initial solution id.");
			break;
		}
	}
	

	class VehicleCapacityComparator implements Comparator<Vehicle> {

		@Override
		public int compare(Vehicle v1, Vehicle v2) {
			if (v1.capacity() > v2.capacity())
				return 1;
			else if (v1.capacity() < v2.capacity())
				return -1;
			else
				return 0;
		}
	};

	
	class TaskWeightComparator implements Comparator<Task> {

		@Override
		public int compare(Task t1, Task t2) {
			if (t1.weight > t2.weight)
				return 1;
			else if (t1.weight < t2.weight)
				return -1;
			else
				return 0;
		}
	};
	

	public void setToInitialSolutionLargestCapacity() {

		// TODO: If there is a task with weight greater than the capacity of all
		// vehicles, return "no solution"

		// TODO: change all indexes with vehicles' and tasks' ids

		ArrayList<Vehicle> sortedVehicles = new ArrayList<>(vehicles);
		sortedVehicles.sort(new VehicleCapacityComparator().reversed());

		ArrayList<Task> sortedTasks = new ArrayList<>(tasks);
		sortedTasks.sort(new TaskWeightComparator().reversed());

		int currentVehicleIndex = 0;
		int currentTaskIndex = 0;

		ArrayList<VehicleState> vehicleStates = new ArrayList<>(sortedVehicles.size());
		for (int i = 0; i < sortedVehicles.size(); i++)
			vehicleStates.add(new VehicleState(0, new ArrayList<Task>(), null));
		Vehicle currentVehicle = sortedVehicles.get(0);
		VehicleState currentVehicleState = vehicleStates.get(0);

		while (currentTaskIndex < sortedTasks.size()) {

			Task task = sortedTasks.get(currentTaskIndex);

			if (currentVehicleState.weightSum() + task.weight <= currentVehicle.capacity()) {

				this.vehicle.set(task.id, currentVehicle);

				int currentVehicleTime = currentVehicleState.getTime();

				if (currentVehicleTime == 0) {
					// The first action is always to pickup
					this.nextAction.set(currentVehicleIndex, new ActionRep(task, ActionName.PICKUP));
					this.pickupTime.set(currentTaskIndex, currentVehicleTime);

					currentVehicleState.setPreviousAction(new ActionRep(task, ActionName.PICKUP));
					currentVehicleState.addCarriedTask(task);
				} else {

					// Check the type of the previous action done by this vehicle
					switch (currentVehicleState.getPreviousAction().getAction()) {
					case PICKUP:
						// If it was pickup, update nextActionAfterPickup for the previous task
						this.nextActionAfterPickup.set(currentVehicleState.getPreviousAction().getTask().id,
								new ActionRep(task, ActionName.PICKUP));
						break;
					case DELIVER:
						// If it was deliver, update nextActionAfterDelivery for the previous task
						this.nextActionAfterDelivery.set(currentVehicleState.getPreviousAction().getTask().id,
								new ActionRep(task, ActionName.PICKUP));
						break;
					}
					this.pickupTime.set(task.id, currentVehicleTime);

					currentVehicleState.setPreviousAction(new ActionRep(task, ActionName.PICKUP));
					currentVehicleState.addCarriedTask(task);
				}
				currentVehicleState.setTime(currentVehicleTime + 1);
				currentTaskIndex++;
			}

			else {

				// If not, first deliver all picked-up tasks
				int i = 0;
				Task prevTask = null;
				ArrayList<Task> currentCarriedTasks = currentVehicleState.getCarriedTasks();
				for (Task t : currentCarriedTasks) {
					if (i == 0)
						this.nextActionAfterPickup.set(currentVehicleState.getPreviousAction().getTask().id,
								new ActionRep(t, ActionName.DELIVER));
					else
						this.nextActionAfterDelivery.set(prevTask.id, new ActionRep(t, ActionName.DELIVER));
					this.deliveryTime.set(t.id, currentVehicleState.getTime());

					currentVehicleState.setTime(currentVehicleState.getTime() + 1);
					currentVehicleState.removeCarriedTask(t);

					prevTask = t;
					i++;
				}

				// Only need to update the previous action once after the final delivery
				currentVehicleState.setPreviousAction(new ActionRep(prevTask, ActionName.DELIVER));

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
		int i = 0;
		Task prevTask = null;
		ArrayList<Task> currentCarriedTasks = currentVehicleState.getCarriedTasks();
		for (Task t : currentCarriedTasks) {
			if (i == 0)
				this.nextActionAfterPickup.set(currentVehicleState.getPreviousAction().getTask().id,
						new ActionRep(t, ActionName.DELIVER));
			else
				this.nextActionAfterDelivery.set(prevTask.id, new ActionRep(t, ActionName.DELIVER));
			this.deliveryTime.set(t.id, currentVehicleState.getTime());

			currentVehicleState.setTime(currentVehicleState.getTime() + 1);
			currentVehicleState.removeCarriedTask(t);

			prevTask = t;
			i++;
		}
		// Only need to update the previous action once after the final delivery
		currentVehicleState.setPreviousAction(new ActionRep(prevTask, ActionName.DELIVER));
	}

	
	class VehicleDistanceComparator implements Comparator<City> {

		private Task task;

		public VehicleDistanceComparator(Task task) {
			super();
			this.task = task;
		}

		@Override
		public int compare(City c1, City c2) {
			double distanceToHome1 = c1.distanceTo(this.task.pickupCity);
			double distanceToHome2 = c2.distanceTo(this.task.pickupCity);

			if (distanceToHome1 > distanceToHome2)
				return 1;
			else if (distanceToHome1 < distanceToHome2)
				return -1;
			else
				return 0;
		}
	};
	

	public void setToInitialSolutionClosestToHome(Topology topology) {

		// TODO: If there is a task with weight greater than the capacity of all
		// vehicles, return "no solution"

		// TODO: change all indexes with vehicles' and tasks' ids

		int currentVehicleIndex = 0;
		int currentTaskIndex = 0;

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

		while (currentTaskIndex < tasks.size()) {
			Task task = tasks.get(currentTaskIndex);

			PriorityQueue<City> queue = new PriorityQueue<>(new VehicleDistanceComparator(task));
			queue.add(task.pickupCity);
			HashSet<City> visitedCities = new HashSet<>();

			boolean taskAssigned = false;

			while (!queue.isEmpty() && !taskAssigned) {
				City currentCity = queue.poll();

				List<Vehicle> closestVehicles = homeCitiesMap.get(currentCity);
				for (Vehicle v : closestVehicles) {

					currentVehicleIndex = vehicles.indexOf(v);
					currentVehicle = v;
					currentVehicleState = vehicleStates.get(currentVehicleIndex);

					if (currentVehicleState.weightSum() + task.weight <= currentVehicle.capacity()) {

						this.vehicle.set(task.id, currentVehicle);

						int currentVehicleTime = currentVehicleState.getTime();

						if (currentVehicleTime == 0) {
							// The first action is always to pickup
							this.nextAction.set(currentVehicleIndex, new ActionRep(task, ActionName.PICKUP));
							this.pickupTime.set(currentTaskIndex, currentVehicleTime);

							currentVehicleState.setPreviousAction(new ActionRep(task, ActionName.PICKUP));
							currentVehicleState.addCarriedTask(task);
						} else {

							// Check the type of the previous action done by this vehicle
							switch (currentVehicleState.getPreviousAction().getAction()) {
							case PICKUP:
								// If it was pickup, update nextActionAfterPickup for the previous task
								this.nextActionAfterPickup.set(currentVehicleState.getPreviousAction().getTask().id,
										new ActionRep(task, ActionName.PICKUP));
								break;
							case DELIVER:
								// If it was deliver, update nextActionAfterDelivery for the previous task
								this.nextActionAfterDelivery.set(currentVehicleState.getPreviousAction().getTask().id,
										new ActionRep(task, ActionName.PICKUP));
								break;
							}
							
							this.pickupTime.set(task.id, currentVehicleTime);

							currentVehicleState.setPreviousAction(new ActionRep(task, ActionName.PICKUP));
							currentVehicleState.addCarriedTask(task);
						}

						currentVehicleState.setTime(currentVehicleTime + 1);
						currentTaskIndex++;

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
					int i = 0;
					Task prevTask = null;
					ArrayList<Task> carriedTasks = vehicleState.getCarriedTasks();
					for (Task t : carriedTasks) {
						if (i == 0)
							this.nextActionAfterPickup.set(vehicleState.getPreviousAction().getTask().id,
									new ActionRep(t, ActionName.DELIVER));
						else
							this.nextActionAfterDelivery.set(prevTask.id, new ActionRep(t, ActionName.DELIVER));
						this.deliveryTime.set(t.id, vehicleState.getTime());

						vehicleState.setTime(vehicleState.getTime() + 1);
						vehicleState.removeCarriedTask(t);

						prevTask = t;
						i++;
					}
					
					// Only need to update the previous action once after the final delivery
					vehicleState.setPreviousAction(new ActionRep(prevTask, ActionName.DELIVER));
				}
				
				// Then make the last closest vehicle pickup the unassigned task
				this.vehicle.set(task.id, currentVehicle);

				int currentVehicleTime = currentVehicleState.getTime();

				if (currentVehicleTime == 0) {
					// The first action is always to pickup
					this.nextAction.set(currentVehicleIndex, new ActionRep(task, ActionName.PICKUP));
					this.pickupTime.set(currentTaskIndex, currentVehicleTime);

					currentVehicleState.setPreviousAction(new ActionRep(task, ActionName.PICKUP));
					currentVehicleState.addCarriedTask(task);
				} else {

					// Check the type of the previous action done by this vehicle
					switch (currentVehicleState.getPreviousAction().getAction()) {
					case PICKUP:
						// If it was pickup, update nextActionAfterPickup for the previous task
						this.nextActionAfterPickup.set(currentVehicleState.getPreviousAction().getTask().id,
								new ActionRep(task, ActionName.PICKUP));
						break;
					case DELIVER:
						// If it was deliver, update nextActionAfterDelivery for the previous task
						this.nextActionAfterDelivery.set(currentVehicleState.getPreviousAction().getTask().id,
								new ActionRep(task, ActionName.PICKUP));
						break;
					}
					
					this.pickupTime.set(task.id, currentVehicleTime);

					currentVehicleState.setPreviousAction(new ActionRep(task, ActionName.PICKUP));
					currentVehicleState.addCarriedTask(task);
				}

				currentVehicleState.setTime(currentVehicleTime + 1);
				currentTaskIndex++;
			}
		}

		// Deliver any remaining picked-up tasks
		for (VehicleState vehicleState : vehicleStates) {

			int i = 0;
			Task prevTask = null;
			ArrayList<Task> carriedTasks = vehicleState.getCarriedTasks();
			for (Task t : carriedTasks) {
				if (i == 0)
					this.nextActionAfterPickup.set(vehicleState.getPreviousAction().getTask().id,
							new ActionRep(t, ActionName.DELIVER));
				else
					this.nextActionAfterDelivery.set(prevTask.id, new ActionRep(t, ActionName.DELIVER));
				this.deliveryTime.set(t.id, vehicleState.getTime());

				vehicleState.setTime(vehicleState.getTime() + 1);
				vehicleState.removeCarriedTask(t);

				prevTask = t;
				i++;
			}
			
			// Only need to update the previous action once after the final delivery
			vehicleState.setPreviousAction(new ActionRep(prevTask, ActionName.DELIVER));
		}
	}

	
	public void setToInitialSolutionCheapestMore() {
		// TODO: If there is a task with weight greater than the capacity of all
		// vehicles, return "no solution"
	}
	

	public List<Variables> chooseNeighbors() {
		// TODO:
		return null;
	}

	
	public Variables changeVehicleOfTask(Task t, Vehicle v) {

		Variables newVariables = (Variables) this.clone();

		Vehicle oldVehicle = this.vehicle.get(t.id);

		if (oldVehicle.id() == v.id())
			return null;

		ActionRep oldVehicleOldFirstAction = this.nextAction.get(oldVehicle.id());
		ActionRep newVehicleOldFirstAction = this.nextAction.get(v.id());
		ActionRep taskOldNextActionAfterPickup = this.nextActionAfterPickup.get(t.id);
		ActionRep taskOldNextActionAfterDelivery = this.nextActionAfterDelivery.get(t.id);

		// Change the vehicle of the task t
		newVariables.vehicle.set(t.id, v);

		// Change the first action of the old vehicle if this task was the first
		if (oldVehicleOldFirstAction.getTask().id == t.id)
			newVariables.nextAction.set(oldVehicle.id(), taskOldNextActionAfterPickup);

		// Change the first action of the new vehicle
		newVariables.nextAction.set(v.id(), new ActionRep(t, ActionName.PICKUP));

		// Change the next actions of the task
		newVariables.nextActionAfterPickup.set(t.id, new ActionRep(t, ActionName.DELIVER));
		newVariables.nextActionAfterDelivery.set(t.id, newVehicleOldFirstAction);

		// Change the pickup and delivery times of the task
		newVariables.pickupTime.set(t.id, 0);
		newVariables.deliveryTime.set(t.id, 1);

		// Update pickup and delivery times in old vehicle up until the delivery of the
		// task
		ActionRep currentVehicleAction = taskOldNextActionAfterPickup;
		ActionRep prevAction = null;

		while (!(currentVehicleAction.getTask().id == t.id && currentVehicleAction.getAction() == ActionName.DELIVER)) {

			int currentTaskId = currentVehicleAction.getTask().id;

			prevAction = currentVehicleAction;

			switch (currentVehicleAction.getAction()) {

			case PICKUP:
				newVariables.pickupTime.set(currentTaskId, this.pickupTime.get(currentTaskId) - 1);
				currentVehicleAction = this.nextActionAfterPickup.get(currentTaskId);
				break;
			case DELIVER:
				newVariables.deliveryTime.set(currentTaskId, this.deliveryTime.get(currentTaskId) - 1);
				currentVehicleAction = this.nextActionAfterDelivery.get(currentTaskId);
				break;
			}
		}

		// Delete the delivery action from the old vehicle
		switch (prevAction.getAction()) {
		case PICKUP:
			newVariables.nextActionAfterPickup.set(prevAction.getTask().id, taskOldNextActionAfterDelivery);
			break;
		case DELIVER:
			newVariables.nextActionAfterDelivery.set(prevAction.getTask().id, taskOldNextActionAfterDelivery);
			break;
		}

		// Update pickup and delivery times in old vehicle after the deleted delivery of
		// the task
		currentVehicleAction = taskOldNextActionAfterDelivery;
		while (currentVehicleAction != null) {

			int currentTaskId = currentVehicleAction.getTask().id;

			switch (currentVehicleAction.getAction()) {

			case PICKUP:
				newVariables.pickupTime.set(currentTaskId, this.pickupTime.get(currentTaskId) - 2);
				currentVehicleAction = this.nextActionAfterPickup.get(currentTaskId);
				break;
			case DELIVER:
				newVariables.deliveryTime.set(currentTaskId, this.deliveryTime.get(currentTaskId) - 2);
				currentVehicleAction = this.nextActionAfterDelivery.get(currentTaskId);
				break;
			}
		}

		// Update pickup and delivery times in new vehicle
		currentVehicleAction = newVehicleOldFirstAction;
		while (currentVehicleAction != null) {

			int currentTaskId = currentVehicleAction.getTask().id;
			switch (currentVehicleAction.getAction()) {

			case PICKUP:
				newVariables.pickupTime.set(currentTaskId, this.pickupTime.get(currentTaskId) + 2);
				currentVehicleAction = this.nextActionAfterPickup.get(currentTaskId);
				break;
			case DELIVER:
				newVariables.deliveryTime.set(currentTaskId, this.deliveryTime.get(currentTaskId) + 2);
				currentVehicleAction = this.nextActionAfterDelivery.get(currentTaskId);
				break;
			}
		}

		// Check load constraint for old vehicle
		if (!newVariables.checkLoadConstraint(oldVehicle))
			return null;

		// Check load constraint for new vehicle
		if (!newVariables.checkLoadConstraint(v))
			return null;

		return newVariables;
	}
	

	/**
	 * @requires vehicle(t1) == vehicle(t2)
	 * 
	 * @param t1
	 * @param t2
	 * @return
	 */
	public Variables changePickupTime(Task t1, Task t2) {

		Variables neighbor;

		// If the pickup time of t1 is not greater than the delivery time of t2
		// or the pickup time of t2 is not greater than the delivery time of t1 there is
		// no valid neighbor
		if (pickupTime.get(t2.id) > deliveryTime.get(t1.id) || pickupTime.get(t1.id) > deliveryTime.get(t2.id))
			return null;

		neighbor = (Variables) this.clone();

		Vehicle vehicle = this.vehicles.get(t1.id); // vehicle carrying t1 (and t2 by the requirements)

		// Modify pickupTime by swapping the pickup time of t1 with pickup time of t2
		neighbor.getPickupTime().set(t1.id, pickupTime.get(t2.id));
		neighbor.getPickupTime().set(t2.id, pickupTime.get(t1.id));

		// Modify nextAction only if either the pickup time of t1 (or t2) is 0

		// If the pickup time of t1 is 0,
		// then nextAction of the current vehicle has to be changed into picking up t2
		if (pickupTime.get(t1.id) == 0)
			neighbor.getNextAction().set(vehicle.id(), new ActionRep(t2, ActionName.PICKUP));

		// If the pickup time of t2 is 0,
		// then nextAction of the current vehicle has to be changed into picking up t1
		if (pickupTime.get(t2.id) == 0)
			neighbor.getNextAction().set(vehicle.id(), new ActionRep(t1, ActionName.PICKUP));

		ActionRep t1PrevAction = this.getPredecessorOfAction(vehicle, new ActionRep(t1, ActionName.PICKUP));
		ActionRep t2PrevAction = this.getPredecessorOfAction(vehicle, new ActionRep(t2, ActionName.PICKUP));

		// Modify nextActionAfterPickup only if the action that precedes the pickup
		// action of t1 (or t2) is a pickup action
		// and modify nextActionAfterDelivery only if the action that precedes the
		// pickup action of t1 (or t2) is a deliver action

		switch (t1PrevAction.getAction()) {

		// If the action before picking up t1 is a pickup action,
		// then set its next action to be the action of picking up t2
		case PICKUP:
			neighbor.getNextActionAfterPickup().set(t1PrevAction.getTask().id, // id of the task picked up just before
																				// t1
					new ActionRep(t2, ActionName.PICKUP) // action of picking up t2
			);
			break;

		// If the action before picking up t1 is a deliver action,
		// then set its next action to be the action of picking up t2
		case DELIVER:
			neighbor.getNextActionAfterDelivery().set(t1PrevAction.getTask().id, // id of the task delivered just before
																					// picking up t1
					new ActionRep(t2, ActionName.PICKUP) // action of picking up t2
			);
		}

		switch (t2PrevAction.getAction()) {

		// If the action before picking up t2 is a pickup action,
		// then set its next action to be the action of picking up t1
		case PICKUP:
			neighbor.getNextActionAfterPickup().set(t2PrevAction.getTask().id, // id of the task picked up just before
																				// t2
					new ActionRep(t1, ActionName.PICKUP) // action of picking up task t1
			);

			// If the action before picking up t2 is a deliver action,
			// then set its next action to be the action of picking up t1
		case DELIVER:
			neighbor.getNextActionAfterDelivery().set(t2PrevAction.getTask().id, // id of the task delivered just before
																					// picking up t2
					new ActionRep(t1, ActionName.PICKUP) // action of picking up task t1
			);
		}

		// If the constraints of capacity are satisfied after the swap the neighbor is a
		// valid one and it can be returned
		if (neighbor.checkLoadConstraint(vehicle))
			return neighbor;

		// Otherwise the neighbor is not valid and null is returned
		return null;
	}
	

	public Variables changeDeliveryTime(Task t1, Task t2) {

		Variables neighbor;

		// If the delivery time of t1 is less than the pickup time of t2
		// or the delivery time of t2 is less than the pickup time of t1 there is no
		// valid neighbor
		if (deliveryTime.get(t2.id) < pickupTime.get(t1.id) || deliveryTime.get(t1.id) < pickupTime.get(t2.id))
			return null;

		neighbor = (Variables) this.clone();

		Vehicle vehicle = this.vehicles.get(t1.id); // vehicle carrying t1 (and t2 by the requirements)

		// Modify deliveryTime by swapping the delivery time of t1 with delivery time of
		// t2
		neighbor.getDeliveryTime().set(t1.id, deliveryTime.get(t2.id));
		neighbor.getDeliveryTime().set(t2.id, deliveryTime.get(t1.id));

		ActionRep t1PrevAction = this.getPredecessorOfAction(vehicle, new ActionRep(t1, ActionName.DELIVER));
		ActionRep t2PrevAction = this.getPredecessorOfAction(vehicle, new ActionRep(t2, ActionName.DELIVER));

		// Modify nextActionAfterPickup only if the action that precedes the delivery
		// action of t1 (or t2) is a pickup action
		// and modify nextActionAfterDelivery only if the action that precedes the
		// delivery action of t1 (or t2) is a deliver action

		switch (t1PrevAction.getAction()) {

		// If the action before delivering t1 is a pickup action,
		// then set its next action to be the action of delivering t2
		case PICKUP:
			neighbor.getNextActionAfterPickup().set(t1PrevAction.getTask().id, // id of the task picked up just before
																				// t1
					new ActionRep(t2, ActionName.DELIVER) // action of delivering t2
			);
			break;

		// If the action before delivering t1 is a deliver action,
		// then set its next action to be the action of delivering t2
		case DELIVER:
			neighbor.getNextActionAfterDelivery().set(t1PrevAction.getTask().id, // id of the task delivered just before
																					// picking up t1
					new ActionRep(t2, ActionName.DELIVER) // action of delivering t2
			);
		}

		switch (t2PrevAction.getAction()) {

		// If the action before delivering t2 is a pickup action,
		// then set its next action to be the action of delivering t1
		case PICKUP:
			neighbor.getNextActionAfterPickup().set(t2PrevAction.getTask().id, // id of the task picked up just before
																				// t2
					new ActionRep(t1, ActionName.DELIVER) // action of delivering task t1
			);

			// If the action before delivering t2 is a deliver action,
			// then set its next action to be the action of delivering t1
		case DELIVER:
			neighbor.getNextActionAfterDelivery().set(t2PrevAction.getTask().id, // id of the task delivered just before
																					// picking up t2
					new ActionRep(t1, ActionName.DELIVER) // action of delivering task t1
			);
		}

		// If the constraints of capacity are satisfied after the swap the neighbor is a
		// valid one and it can be returned
		if (neighbor.checkLoadConstraint(vehicle))
			return neighbor;

		// Otherwise the neighbor is not valid and null is returned
		return null;
	}

	
	public Variables localChoice(double p, List<Vehicle> vehicles, TaskSet tasks) {

		// Random sample a Bernoulli(p) distribution to know whether to return the old
		// solution or the best neighbor
		int chooseBestNeighbor = uchicago.src.sim.util.Random.binomial.nextInt(1, p);
		if (chooseBestNeighbor == 0)
			return this;

		List<Variables> candidateNeighbors = this.chooseNeighbors();
		double bestObjectiveValue = Double.POSITIVE_INFINITY;
		ArrayList<Variables> bestCandidateNeighbors = new ArrayList<>();

		// Compute the objective function for every candidate neighbor solution and find
		// the collection of solutions with equal lowest cost
		for (Variables candidateNeighbor : candidateNeighbors) {
			double neighborObjectiveValue = candidateNeighbor.computeObjective(vehicles, tasks);
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
		Variables bestCandidateNeighbor = bestCandidateNeighbors.get(bestCandidateNeighborIndex);

		return bestCandidateNeighbor;
	}
	

	public double computeObjective(List<Vehicle> vehicles, TaskSet tasks) {

		double objectiveValue = 0;

		// Add to the cost all travel costs between vehicles' starting cities and pickup
		// cities of first tasks
		for (Vehicle v : vehicles) {
			ActionRep vehicleFirstTask = this.nextAction.get(v.id());
			if (vehicleFirstTask != null)
				objectiveValue += v.costPerKm() * vehicleFirstTask.getTask().pickupCity.distanceTo(v.homeCity());
		}

		// Add to the cost all travel costs between tasks' pickup cities and
		// pickup/delivery cities of next tasks
		for (Task t : tasks) {
			ActionRep taskNextActionAfterPickup = this.nextActionAfterPickup.get(t.id);
			if (taskNextActionAfterPickup != null) {
				switch (taskNextActionAfterPickup.getAction()) {
				case PICKUP:
					objectiveValue += this.vehicle.get(t.id).costPerKm()
							* t.pickupCity.distanceTo(taskNextActionAfterPickup.getTask().pickupCity);
					break;
				case DELIVER:
					objectiveValue += this.vehicle.get(t.id).costPerKm()
							* t.pickupCity.distanceTo(taskNextActionAfterPickup.getTask().deliveryCity);
					break;
				}
			}
		}

		// Add to the cost all travel costs between tasks' delivery cities and
		// pickup/delivery cities of next tasks
		for (Task t : tasks) {
			ActionRep taskNextActionAfterDelivery = this.nextActionAfterDelivery.get(t.id);
			if (taskNextActionAfterDelivery != null) {
				switch (taskNextActionAfterDelivery.getAction()) {
				case PICKUP:
					objectiveValue += this.vehicle.get(t.id).costPerKm()
							* t.deliveryCity.distanceTo(taskNextActionAfterDelivery.getTask().pickupCity);
					break;
				case DELIVER:
					objectiveValue += this.vehicle.get(t.id).costPerKm()
							* t.deliveryCity.distanceTo(taskNextActionAfterDelivery.getTask().deliveryCity);
					break;
				}
			}
		}

		return objectiveValue;
	}

	
	public ActionRep getPredecessorOfAction(Vehicle v, ActionRep action) {

		ActionRep currentVehicleAction = this.nextAction.get(v.id());
		ActionRep prevAction = null;
		while (!(currentVehicleAction.getTask().id == action.getTask().id
				&& currentVehicleAction.getAction() == action.getAction())) {

			int currentTaskId = currentVehicleAction.getTask().id;

			prevAction = currentVehicleAction;

			switch (currentVehicleAction.getAction()) {

			case PICKUP:
				currentVehicleAction = this.nextActionAfterPickup.get(currentTaskId);
				break;
			case DELIVER:
				currentVehicleAction = this.nextActionAfterDelivery.get(currentTaskId);
				break;
			}
		}
		
		return prevAction;
	}
	

	public boolean checkLoadConstraint(Vehicle v) {

		ActionRep currentVehicleAction = this.nextAction.get(v.id());
		int currentLoad = 0;

		while (currentVehicleAction != null) {

			Task currentTask = currentVehicleAction.getTask();

			switch (currentVehicleAction.getAction()) {

			case PICKUP:
				currentLoad += currentTask.weight;
				if (currentLoad > v.capacity())
					return false;
				currentVehicleAction = this.nextActionAfterPickup.get(currentTask.id);
				break;
			case DELIVER:
				currentLoad -= currentTask.weight;
				currentVehicleAction = this.nextActionAfterDelivery.get(currentTask.id);
				break;
			}
		}

		return true;
	}

	
	public List<Plan> inferPlans() {

		ArrayList<Plan> plans = new ArrayList<>();

		ArrayList<Task> sortedTasks = new ArrayList<>(tasks);

		for (int currentVehicleIndex = 0; currentVehicleIndex < vehicles.size(); currentVehicleIndex++) {

			Vehicle currentVehicle = vehicles.get(currentVehicleIndex);
			City currentVehicleLocation = currentVehicle.homeCity();
			Plan plan = new Plan(currentVehicleLocation);

			ActionRep currentVehicleAction = this.nextAction.get(currentVehicleIndex);

			while (currentVehicleAction != null) {

				Task currentVehicleTask = currentVehicleAction.getTask();
				int currentTaskIndex = sortedTasks.indexOf(currentVehicleTask);

				switch (currentVehicleAction.getAction()) {

				case PICKUP:
					List<City> shortestPathToPickup = currentVehicleLocation.pathTo(currentVehicleTask.pickupCity);
					for (City c : shortestPathToPickup)
						plan.appendMove(c);
					currentVehicleLocation = currentVehicleTask.pickupCity;
					plan.appendPickup(currentVehicleTask);
					currentVehicleAction = this.nextActionAfterPickup.get(currentTaskIndex);
					break;

				case DELIVER:
					List<City> shortestPathToDelivery = currentVehicleLocation.pathTo(currentVehicleTask.deliveryCity);
					for (City c : shortestPathToDelivery)
						plan.appendMove(c);
					currentVehicleLocation = currentVehicleTask.deliveryCity;
					plan.appendDelivery(currentVehicleTask);
					currentVehicleAction = this.nextActionAfterDelivery.get(currentTaskIndex);
					break;
				}
			}

			plans.add(plan);
		}

		return plans;
	}
	

	public ArrayList<Vehicle> getVehicle() {
		return vehicle;
	}
	

	public void setVehicle(ArrayList<Vehicle> vehicle) {
		this.vehicle = vehicle;
	}

	
	public ArrayList<Integer> getPickupTime() {
		return pickupTime;
	}

	
	public void setPickupTime(ArrayList<Integer> pickupTime) {
		this.pickupTime = pickupTime;
	}
	

	public ArrayList<Integer> getDeliveryTime() {
		return deliveryTime;
	}

	
	public void setDeliveryTime(ArrayList<Integer> deliveryTime) {
		this.deliveryTime = deliveryTime;
	}
	

	public ArrayList<ActionRep> getNextAction() {
		return nextAction;
	}

	
	public void setNextAction(ArrayList<ActionRep> nextAction) {
		this.nextAction = nextAction;
	}

	
	public ArrayList<ActionRep> getNextActionAfterPickup() {
		return nextActionAfterPickup;
	}

	
	public void setNextActionAfterPickup(ArrayList<ActionRep> nextActionAfterPickup) {
		this.nextActionAfterPickup = nextActionAfterPickup;
	}

	
	public ArrayList<ActionRep> getNextActionAfterDelivery() {
		return nextActionAfterDelivery;
	}

	
	public void setNextActionAfterDelivery(ArrayList<ActionRep> nextActionAfterDelivery) {
		this.nextActionAfterDelivery = nextActionAfterDelivery;
	}

	
	@Override
	protected Object clone() {
		ArrayList<Vehicle> vehicleNew = new ArrayList<>(this.vehicle);
		ArrayList<Integer> pickupTimeNew = new ArrayList<>(this.pickupTime);
		ArrayList<Integer> deliveryTimeNew = new ArrayList<>(this.deliveryTime);
		ArrayList<ActionRep> nextActionNew = new ArrayList<>(this.nextAction);
		ArrayList<ActionRep> nextActionAfterPickupNew = new ArrayList<>(this.nextActionAfterPickup);
		ArrayList<ActionRep> nextActionAfterDeliveryNew = new ArrayList<>(this.nextActionAfterDelivery);

		return new Variables(vehicles, tasks, vehicleNew, pickupTimeNew, deliveryTimeNew, nextActionNew,
				nextActionAfterPickupNew, nextActionAfterDeliveryNew);
	}

	
	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();

		// Append vehicle
		str.append("vehicle : [t0 -> v").append(vehicle.get(0).id());
		for (int i = 1; i < vehicle.size(); i++) {
			str.append(", t").append(i).append(" -> v").append(vehicle.get(i).id());
		}
		
		str.append("]\n");

		// Append pickupTime
		str.append("pickupTime : [t0 -> ").append(pickupTime.get(0));
		for (int i = 1; i < pickupTime.size(); i++) {
			str.append(", t").append(i).append(" -> ").append(pickupTime.get(i));
		}
		
		str.append("]\n");

		// Append deliveryTime
		str.append("deliveryTime : [t0 -> ").append(deliveryTime.get(0));
		for (int i = 1; i < deliveryTime.size(); i++) {
			str.append(", t").append(i).append(" -> ").append(deliveryTime.get(i));
		}
		
		str.append("]\n");

		// Append nextAction
		if (nextAction.get(0) != null)
			str.append("nextAction : [v0 -> ").append(nextAction.get(0).toString());
		for (int i = 1; i < nextAction.size(); i++) {
			if (nextAction.get(i) != null)
				str.append(", v").append(i).append(" -> ").append(nextAction.get(i).toString());
		}
		
		str.append("]\n");

		// Append nextActionAfterPickup
		if (nextActionAfterPickup.get(0) != null)
			str.append("nextActionAfterPickup : [t0 -> ").append(nextActionAfterPickup.get(0).toString());
		for (int i = 1; i < nextActionAfterPickup.size(); i++) {
			if (nextActionAfterPickup.get(i) != null)
				str.append(", t").append(i).append(" -> ").append(nextActionAfterPickup.get(i).toString());
		}
		
		str.append("]\n");

		// Append nextActionAfterDelivery
		if (nextActionAfterDelivery.get(0) != null)
			str.append("nextActionAfterDelivery : [t0 -> ").append(nextActionAfterDelivery.get(0).toString());
		for (int i = 1; i < nextActionAfterDelivery.size(); i++) {
			if (nextActionAfterDelivery.get(i) != null)
				str.append(", t").append(i).append(" -> ").append(nextActionAfterDelivery.get(i).toString());
		}
		
		str.append("]\n");

		return str.toString();
	}
}