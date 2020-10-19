package template;

import logist.task.Task;
import logist.task.TaskSet;
import logist.topology.Topology.City;

public class State {
	
	private City location;
	private TaskSet tasksToPickup;
	private TaskSet tasksToDeliver;
	
	private static int heuristicId;
	
	public State(City location, TaskSet tasksToPickup) {
		super();
		this.location = location;
		this.tasksToPickup = tasksToPickup;
		this.tasksToDeliver = TaskSet.noneOf(tasksToPickup);
	}
	
	public State(City location, TaskSet tasksToPickup, TaskSet tasksToDeliver) {
		super();
		this.location = location;
		this.tasksToPickup = tasksToPickup;
		this.tasksToDeliver = tasksToDeliver;
	}

	public boolean isGoalState()
	{
		return this.tasksToPickup.isEmpty() && this.tasksToDeliver.isEmpty();
	}
	
	public double getHCost(int costPerKm) {		
		switch(State.heuristicId)
		{
		case 0:
			return this.getHCostMinDistNeighbor(costPerKm);
		case 1:
			return this.getHCostMinDistTaskCity(costPerKm);
		case 2:
			return this.getHCostTotalDistEstimate(costPerKm);
		default:
			throw new AssertionError("Invalid heuristic id. Can be only equal to 0, 1 or 2.");
		}
		
	}

	public double getHCostMinDistNeighbor(int costPerKm){
		double minCost = Double.POSITIVE_INFINITY;
		double cost;
		for (City c: location.neighbors()) {
			cost = location.distanceTo(c);
			if (cost < minCost) {
				minCost = cost;
			}
		}
		return costPerKm * minCost - tasksToPickup.rewardSum() - tasksToDeliver.rewardSum();
	}
	
	public double getHCostMinDistTaskCity(int costPerKm){
		double minCostPickup = Double.POSITIVE_INFINITY;
		double minCostDeliver = Double.POSITIVE_INFINITY;
		double cost;
		
		if (tasksToPickup.isEmpty() && tasksToDeliver.isEmpty())
			return 0;
		
		for (Task t: tasksToPickup){
			cost = location.distanceTo(t.pickupCity);
			if (cost < minCostPickup) {
				minCostPickup = cost;
			}
		}
		for (Task t: tasksToDeliver){
			cost = location.distanceTo(t.deliveryCity);
			if (cost < minCostDeliver) {
				minCostDeliver = cost;
			}
		}
		
		return costPerKm * Math.min(minCostPickup, minCostDeliver) - tasksToPickup.rewardSum() - tasksToDeliver.rewardSum();
	}

	public double getHCostTotalDistEstimate(int costPerKm){		
		double minCostPickup = Double.POSITIVE_INFINITY;
		double minCostDeliver = Double.POSITIVE_INFINITY;
		double cost;
		
		if (tasksToPickup.isEmpty())
			minCostPickup = 0;
		if (tasksToDeliver.isEmpty())
			minCostDeliver = 0;
		
		for (Task t: tasksToPickup){
			cost = location.distanceTo(t.pickupCity);
			if (cost < minCostPickup) {
				minCostPickup = cost;
			}
		}
		for (Task t: tasksToDeliver){
			cost = location.distanceTo(t.deliveryCity);
			if (cost < minCostDeliver) {
				minCostDeliver = cost;
			}
		}
		
		return costPerKm * (tasksToPickup.size() * minCostPickup + tasksToDeliver.size() * minCostDeliver) 
				- tasksToPickup.rewardSum() - tasksToDeliver.rewardSum();
	}
	
	public City getLocation() {
		return location;
	}

	public void setLocation(City location) {
		this.location = location;
	}

	public TaskSet getTasksToPickup() {
		return tasksToPickup;
	}

	public void setTasksToPickup(TaskSet tasksToPickup) {
		this.tasksToPickup = tasksToPickup;
	}

	public TaskSet getTasksToDeliver() {
		return tasksToDeliver;
	}

	public void setTasksToDeliver(TaskSet tasksToDeliver) {
		this.tasksToDeliver = tasksToDeliver;
	}

	public static int getHeuristicId() {
		return heuristicId;
	}

	public static void setHeuristicId(int heuristicId) {
		State.heuristicId = heuristicId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((location == null) ? 0 : location.hashCode());
		result = prime * result + ((tasksToDeliver == null) ? 0 : tasksToDeliver.hashCode());
		result = prime * result + ((tasksToPickup == null) ? 0 : tasksToPickup.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		State other = (State) obj;
		if (location == null) {
			if (other.location != null)
				return false;
		} else if (!location.equals(other.location))
			return false;
		if (tasksToDeliver == null) {
			if (other.tasksToDeliver != null)
				return false;
		} else if (!tasksToDeliver.equals(other.tasksToDeliver))
			return false;
		if (tasksToPickup == null) {
			if (other.tasksToPickup != null)
				return false;
		} else if (!tasksToPickup.equals(other.tasksToPickup))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return String.format("In city %s, tasks to pickup: %s, tasks to deliver: %s", this.location, this.tasksToPickup.toString(), this.tasksToDeliver.toString());
	}
	
	
	
	
}
