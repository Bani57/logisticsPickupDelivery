package template;

import java.util.ArrayList;
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
		this.vehicle = new ArrayList<>(numTasks);
		this.pickupTime = new ArrayList<>(numTasks);
		this.deliveryTime = new ArrayList<>(numTasks);
		this.nextAction = new ArrayList<>(numVehicles);
		this.nextActionAfterPickup = new ArrayList<>(numTasks);
		this.nextActionAfterDelivery = new ArrayList<>(numTasks);
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
	
	public void setToInitialSolutionLargestCapacity(List<Vehicle> vehicles, TaskSet tasks) {
		
		ArrayList<Vehicle> sortedVehicles = new ArrayList<>(vehicles);
		sortedVehicles.sort(new VehicleCapacityComparator());
		
		ArrayList<Task> sortedTasks = new ArrayList<>(tasks);
		sortedTasks.sort(new TaskWeightComparator());
		
		
		int currentVehicleIndex = 0;
		Vehicle currentVehicle;
		ArrayList<Integer> times = new ArrayList<>(vehicles.size()); 
		ActionRep previousAction = null;
		for(Task task: sortedTasks) {
			currentVehicle = sortedVehicles.get(currentVehicleIndex);
			
			if(currentVehicle.capacity() >= task.weight) {
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

			}
			else {
				currentVehicleIndex++;
			}
		}
		
	}
	
	public void setToInitialSolutionClosestToHome(List<Vehicle> vehicles, TaskSet tasks) {
		
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
	
	@Override
	public String toString(){
		StringBuilder str = new StringBuilder(); 
		
		// Append vehicle
		str.append("vehicle : [ t0 -> v").append(vehicle.get(0).id());
		for (int i=1; i<vehicle.size(); i++) {
			str.append(", t").append(i).append(" -> ").append(vehicle.get(i).id());
		}
		str.append("]\n");
		
		// Append pickupTime
		str.append("pickupTime : [ t0 -> ").append(pickupTime.get(0));
		for (int i=1; i<pickupTime.size(); i++) {
			str.append(", t").append(i).append(" -> ").append(pickupTime.get(i));
		}
		str.append("]\n");
		
		// Append deliveryTime
		str.append("deliveryTime : [ t0 -> ").append(deliveryTime.get(0));
		for (int i=1; i<deliveryTime.size(); i++) {
			str.append(", t").append(i).append(" -> ").append(deliveryTime.get(i));
		}
		str.append("]\n");
		
		// Append nextAction
		str.append("nextAction : [ v0 -> ").append(nextAction.get(0).toString());
		for (int i=1; i<nextAction.size(); i++) {
			str.append(", t").append(i).append(" -> ").append(nextAction.get(i).toString());
		}
		str.append("]\n");
		
		// Append nextActionAfterPickup
		str.append("nextActionAfterPickup : [ t0 -> ").append(nextActionAfterPickup.get(0).toString());
		for (int i=1; i<nextActionAfterPickup.size(); i++) {
			str.append(", t").append(i).append(" -> ").append(nextActionAfterPickup.get(1).toString());
		}
		str.append("]\n");
		
		// Append nextActionAfterDelivery
		str.append("nextActionAfterDelivery : [ t0 -> ").append(nextActionAfterDelivery.get(0).toString());
		for (int i=1; i<nextActionAfterDelivery.size(); i++) {
			str.append(", t").append(i).append(" -> ").append(nextActionAfterDelivery.get(1).toString());
		}
		str.append("]\n");
				
				
		return null;
		
	}
	
	
}
