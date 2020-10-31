package template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;
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
	
	public void setToInitialSolution(List<Vehicle> vehicles, TaskSet tasks, Topology topology, int initialSolutionId) {
		switch(initialSolutionId) {
		case 1:
			setToInitialSolutionLargestCapacity(vehicles, tasks);
			break;
		case 2:
			setToInitialSolutionClosestToHome(vehicles, tasks, topology);
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
		
		// TODO: If there is a task with weight greater than the capacity of all vehicles, return "no solution"
		
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
		ArrayList<Task> currentTasksToDeliver = new ArrayList<>();
		
		while(currentTaskIndex < tasks.size()) {
			
			Task task = sortedTasks.get(currentTaskIndex);
			
			// Check whether the current selected vehicle has enough space to carry one more task
			if(currentCapacity >= task.weight) {
				
				// Assign a task to this vehicle
				this.vehicle.set(task.id, currentVehicle);
				
				int currentVehicleTime = times.get(currentVehicle.id());
				// Check whether this is the first time assigning an action to this vehicle
				if(currentVehicleTime == 0)
				{
					// The first action is always to pickup
					this.nextAction.set(currentVehicle.id(), new ActionRep(task, ActionName.PICKUP));
					this.pickupTime.set(task.id, currentVehicleTime);
					
					previousAction = new ActionRep(task, ActionName.PICKUP);
					currentTasksToDeliver.add(task);
				}
				else {
					
					// Check the type of the previous action done by this vehicle
					switch(previousAction.getAction()) {
					case PICKUP:
						// If it was pickup, update nextActionAfterPickup for the previous task
						this.nextActionAfterPickup.set(previousAction.getTask().id, new ActionRep(task, ActionName.PICKUP));
						break;
					case DELIVER:
						// If it was deliver, update nextActionAfterDelivery for the previous task
						this.nextActionAfterDelivery.set(previousAction.getTask().id, new ActionRep(task, ActionName.PICKUP));
					}
					this.pickupTime.set(task.id, currentVehicleTime);
					
					previousAction = new ActionRep(task, ActionName.PICKUP);
					currentTasksToDeliver.add(task);
				}
				
				times.set(currentVehicle.id(), currentVehicleTime+1);
				
				// Reduce the available capacity for the vehicle by the task weight
				currentCapacity -= task.weight;
				
				
				currentTaskIndex++;
			}

			else {
				
				// If not, first deliver all picked-up tasks
				for(int i=0; i<currentTasksToDeliver.size();i++)
				{
					Task t = currentTasksToDeliver.get(i);
					if(i==0)
						this.nextActionAfterPickup.set(previousAction.getTask().id, new ActionRep(t, ActionName.DELIVER));
					else
						this.nextActionAfterDelivery.set(currentTasksToDeliver.get(i-1).id, new ActionRep(t, ActionName.DELIVER));
					this.deliveryTime.set(t.id, times.get(currentVehicle.id()));

					times.set(currentVehicle.id(), times.get(currentVehicle.id())+1);

				}
				
				currentVehicleIndex++;
				
				// If total weight of all tasks > total capacity of all vehicles,
				// reset the whole procedure for the remaining subset of tasks,
				// as all the vehicles are free again after they have delivered all of their tasks
				if(currentVehicleIndex == vehicles.size())
					currentVehicleIndex = 0;
				
				// Proceed to the next vehicle and reset all vehicle-related variables
				currentVehicle = sortedVehicles.get(currentVehicleIndex);
				currentCapacity = currentVehicle.capacity();
				currentTasksToDeliver = new ArrayList<>();

			}
			

		}
		
		// Deliver any remaining tasks
		for(int i=0; i<currentTasksToDeliver.size();i++)
		{
			Task t = currentTasksToDeliver.get(i);
			if(i==0)
				this.nextActionAfterPickup.set(previousAction.getTask().id, new ActionRep(t, ActionName.DELIVER));
			else
				this.nextActionAfterDelivery.set(currentTasksToDeliver.get(i-1).id, new ActionRep(t, ActionName.DELIVER));

			this.deliveryTime.set(t.id, times.get(currentVehicle.id()));
			times.set(currentVehicle.id(), times.get(currentVehicle.id())+1);

		}
		
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

			if(distanceToHome1 > distanceToHome2)
				return 1;
			else if(distanceToHome1 < distanceToHome2)
				return -1;
			else
				return 0;
		}
	};
	
	public void setToInitialSolutionClosestToHome(List<Vehicle> vehicles, TaskSet tasks, Topology topology) {
		
		// TODO: If there is a task with weight greater than the capacity of all vehicles, return "no solution"
		
		ArrayList<Task> sortedTasks = new ArrayList<>(tasks);

		int currentTaskIndex = 0;	
		HashMap<City, List<Vehicle>> homeCitiesMap = new HashMap<>();
		for(City c: topology.cities())
			homeCitiesMap.put(c, new ArrayList<Vehicle>());
		for(Vehicle v: vehicles)
			homeCitiesMap.get(v.homeCity()).add(v);
		
		while(currentTaskIndex < tasks.size())
		{
			Task task = sortedTasks.get(currentTaskIndex);
			
			PriorityQueue<City> queue = new PriorityQueue<>(new VehicleDistanceComparator(task));
			queue.add(task.pickupCity);
			HashSet<City> visitedCities = new HashSet<>();
			
			while(!queue.isEmpty())
			{
				City currentCity = queue.poll();
				
				List<Vehicle> closestVehicles = homeCitiesMap.get(currentCity);
				for(Vehicle v: closestVehicles)
				{
					// TODO: Figure out how to check that you can assign this task to this closest vehicle
					// If no vehicle has room, then deliver everything for each vehicle or just for the first
					
				}
				
				for(City neighborCity: currentCity.neighbors())
				{
					if(!visitedCities.contains(neighborCity))
					{
						queue.add(neighborCity);
						visitedCities.add(neighborCity);
					}
				}
			}
		}
	}
	
	public void setToInitialSolutionCheapestMore(List<Vehicle> vehicles, TaskSet tasks) {
		// TODO: If there is a task with weight greater than the capacity of all vehicles, return "no solution"
	}
	
	public List<Variables> chooseNeighbors(){
		// TODO:
		return null;
	}
	
	public Variables changeVehicleOfTask(Task t, Vehicle v){
		// TODO:
		return null;
	}
	
	public Variables changePickupTime(Task t, int newPickupTime){
		// TODO:
		return null;
	}
	
	public Variables changeDeliveryTime(Task t, int newDeliveryTime){
		// TODO:
		return null;
	}
	
	public Variables localChoice(){
		// TODO:
		return null;
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
		str.append("vehicle : [t0 -> v").append(vehicle.get(0).id());
		for (int i=1; i<vehicle.size(); i++) {
			str.append(", t").append(i).append(" -> v").append(vehicle.get(i).id());
		}
		str.append("]\n");
		
		// Append pickupTime
		str.append("pickupTime : [t0 -> ").append(pickupTime.get(0));
		for (int i=1; i<pickupTime.size(); i++) {
			str.append(", t").append(i).append(" -> ").append(pickupTime.get(i));
		}
		str.append("]\n");
		
		// Append deliveryTime
		str.append("deliveryTime : [t0 -> ").append(deliveryTime.get(0));
		for (int i=1; i<deliveryTime.size(); i++) {
			str.append(", t").append(i).append(" -> ").append(deliveryTime.get(i));
		}
		str.append("]\n");
		
		// Append nextAction
		if(nextAction.get(0) != null)
			str.append("nextAction : [v0 -> ").append(nextAction.get(0).toString());
		for (int i=1; i<nextAction.size(); i++) {
			if(nextAction.get(i) != null)
				str.append(", v").append(i).append(" -> ").append(nextAction.get(i).toString());
		}
		str.append("]\n");
		
		// Append nextActionAfterPickup
		if(nextActionAfterPickup.get(0)!=null)
			str.append("nextActionAfterPickup : [t0 -> ").append(nextActionAfterPickup.get(0).toString());
		for (int i=1; i<nextActionAfterPickup.size(); i++) {
			if(nextActionAfterPickup.get(i) != null)
				str.append(", t").append(i).append(" -> ").append(nextActionAfterPickup.get(i).toString());
		}
		str.append("]\n");
		
		// Append nextActionAfterDelivery
		if(nextActionAfterDelivery.get(0)!=null)
			str.append("nextActionAfterDelivery : [t0 -> ").append(nextActionAfterDelivery.get(0).toString());
		for (int i=1; i<nextActionAfterDelivery.size(); i++) {
			if(nextActionAfterDelivery.get(i)!=null)
				str.append(", t").append(i).append(" -> ").append(nextActionAfterDelivery.get(i).toString());
		}
		str.append("]\n");
				
				
		return str.toString();
		
	}
	
	
}
