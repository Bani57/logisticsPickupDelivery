package template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;
import template.ActionRep.ActionName;

public class Variables {
	
	private ArrayList<Vehicle> vehicle;
	private ArrayList<Integer> pickupTime;
	private ArrayList<Integer> deliveryTime;
	private ArrayList<ActionRep> nextAction;
	private ArrayList<ActionRep> nextActionAfterPickup;
	private ArrayList<ActionRep> nextActionAfterDelivery;
	
	public Variables(int numVehicles, int numTasks) {
		super();
		this.vehicle = new ArrayList<>(Collections.nCopies(numTasks, (Vehicle)null));
		this.pickupTime = new ArrayList<>(Collections.nCopies(numTasks, 0));
		this.deliveryTime = new ArrayList<>(Collections.nCopies(numTasks, 0));
		this.nextAction = new ArrayList<>(Collections.nCopies(numVehicles, (ActionRep)null));
		this.nextActionAfterPickup = new ArrayList<>(Collections.nCopies(numTasks, (ActionRep)null));
		this.nextActionAfterDelivery = new ArrayList<>(Collections.nCopies(numTasks, (ActionRep)null));
	}
	
	public void setToInitialSolution(List<Vehicle> vehicles, TaskSet tasks, int initialSolutionId) {
		switch(initialSolutionId) {
		case 1:
			setToInitialSolutionLargestCapacity(vehicles, tasks);
			break;
		case 2:
			setToInitialSolutionClosestToHome(vehicles, tasks);
			break;
		case 3:
			setToInitialSolutionCheapestMore(vehicles, tasks);
			break;
		default:
			System.out.println("Invalid initial solution id.");
			break;
		}
	}
	
	class VehicleCapacityComparator implements Comparator<Vehicle> {

		@Override
		public int compare(Vehicle v1, Vehicle v2) {
			if(v1.capacity() > v2.capacity())
				return 1;
			else if(v1.capacity() < v2.capacity())
				return -1;
			else
				return 0;
		}
	};
	
	class TaskWeightComparator implements Comparator<Task> {

		@Override
		public int compare(Task t1, Task t2) {
			if(t1.weight > t2.weight)
				return 1;
			else if(t1.weight < t2.weight)
				return -1;
			else
				return 0;
		}
	};
	
	class VehicleDistanceComparator implements Comparator<Vehicle> {
		
		private Task task;
		
		public VehicleDistanceComparator(Task task) {
			super();
			this.task = task;
		}

		@Override
		public int compare(Vehicle v1, Vehicle v2) {
			double distanceToHome1 = v1.homeCity().distanceTo(task.pickupCity);
			double distanceToHome2 = v2.homeCity().distanceTo(task.pickupCity);

			if(distanceToHome1 > distanceToHome2)
				return 1;
			else if(distanceToHome1 < distanceToHome2)
				return -1;
			else
				return 0;
		}
	};
	
	public void setToInitialSolutionLargestCapacity(List<Vehicle> vehicles, TaskSet tasks) {
		
		ArrayList<Vehicle> sortedVehicles = new ArrayList<>(vehicles);
		sortedVehicles.sort(new VehicleCapacityComparator());
		
		ArrayList<Task> sortedTasks = new ArrayList<>(tasks);
		sortedTasks.sort(new TaskWeightComparator());
		
		
		int currentVehicleIndex = 0;
		int currentTaskIndex = 0;
		Vehicle currentVehicle = sortedVehicles.get(0);
		ArrayList<Integer> times = new ArrayList<>(Collections.nCopies(vehicles.size(), 0)); 
		ActionRep previousAction = null;
		int currentCapacity = currentVehicle.capacity();
		while(currentTaskIndex < tasks.size()) {
			
			Task task = sortedTasks.get(currentTaskIndex);
			
			if(currentCapacity >= task.weight) {
				// Assign a task to this vehicle
				this.vehicle.set(task.id, currentVehicle);
				int currentVehicleTime = times.get(currentVehicle.id());
				if(currentVehicleTime == 0)
				{
					this.nextAction.set(currentVehicle.id(), new ActionRep(task, ActionName.PICKUP));
					this.pickupTime.set(task.id, 0);
					this.nextActionAfterPickup.set(task.id, new ActionRep(task, ActionName.DELIVER));
					this.deliveryTime.set(task.id, 1);
					previousAction = new ActionRep(task, ActionName.DELIVER);
				}
				else {
					this.nextActionAfterDelivery.set(previousAction.getTask().id, new ActionRep(task, ActionName.PICKUP));
					this.pickupTime.set(task.id, currentVehicleTime);
					this.nextActionAfterPickup.set(task.id, new ActionRep(task, ActionName.DELIVER));
					this.deliveryTime.set(task.id, currentVehicleTime+1);
					previousAction = new ActionRep(task, ActionName.DELIVER);
				}
				times.set(currentVehicle.id(), times.get(currentVehicle.id())+2);
				currentCapacity -= task.weight;
				currentTaskIndex++;
			}
			else {
				currentVehicleIndex++;
				currentVehicle = sortedVehicles.get(currentVehicleIndex);
				currentCapacity = currentVehicle.capacity();
			}
		}
		
	}
	
	public void setToInitialSolutionClosestToHome(List<Vehicle> vehicles, TaskSet tasks) {
		
		ArrayList<Task> sortedTasks = new ArrayList<>(tasks);

		ArrayList<Vehicle> sortedVehicles = new ArrayList<>(vehicles);
		int currentTaskIndex = 0;
		ArrayList<Integer> times = new ArrayList<>(Collections.nCopies(vehicles.size(), 0)); 
		ActionRep previousAction = null;
		
		while(currentTaskIndex < tasks.size())
		{
			Task task = sortedTasks.get(currentTaskIndex);
			
			sortedVehicles.sort(new VehicleDistanceComparator(task));
			
			for(Vehicle vehicle: sortedVehicles) {
				if(vehicle.capacity() > task.weight) {
					int currentVehicleTime = times.get(vehicle.id());
					if(currentVehicleTime == 0)
					{
						this.nextAction.set(vehicle.id(), new ActionRep(task, ActionName.PICKUP));
						this.pickupTime.set(task.id, 0);
						this.nextActionAfterPickup.set(task.id, new ActionRep(task, ActionName.DELIVER));
						this.deliveryTime.set(task.id, 1);
						previousAction = new ActionRep(task, ActionName.DELIVER);
					}
					else {
						this.nextActionAfterDelivery.set(previousAction.getTask().id, new ActionRep(task, ActionName.PICKUP));
						this.pickupTime.set(task.id, currentVehicleTime);
						this.nextActionAfterPickup.set(task.id, new ActionRep(task, ActionName.DELIVER));
						this.deliveryTime.set(task.id, currentVehicleTime+1);
						previousAction = new ActionRep(task, ActionName.DELIVER);
					}
					currentTaskIndex++;
				}
			}
		}
	}
	
	public void setToInitialSolutionCheapestMore(List<Vehicle> vehicles, TaskSet tasks) {
		
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
	
	
}
