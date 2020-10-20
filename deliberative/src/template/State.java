package template;

import logist.task.Task;
import logist.task.TaskSet;
import logist.topology.Topology.City;

/**
 * Class implementing the state representation in our model. Contains also
 * methods for extracting useful information from the state.
 * 
 * @author Andrej Janchevski
 * @author Orazio Rillo
 */
public class State {

	private City location; // Current city of the vehicle
	private TaskSet tasksToPickup; // Set of tasks still waiting to be picked up
	private TaskSet tasksToDeliver; // Set of tasks currently carried by the vehicle, still waiting to be delivered

	private static int heuristicId; // Id of the chosen heuristic function for the A* algorithm

	/**
	 * Constructor for initial states when there are still no tasks carried by the
	 * vehicle.
	 */
	public State(City location, TaskSet tasksToPickup) {
		super();
		this.location = location;
		this.tasksToPickup = tasksToPickup;
		this.tasksToDeliver = TaskSet.noneOf(tasksToPickup);
	}

	/**
	 * Constructor for the general case.
	 */
	public State(City location, TaskSet tasksToPickup, TaskSet tasksToDeliver) {
		super();
		this.location = location;
		this.tasksToPickup = tasksToPickup;
		this.tasksToDeliver = tasksToDeliver;
	}

	/**
	 * Method implementing goal state detection. We are in a goal state if there are
	 * no more tasks to pickup or deliver.
	 */
	public boolean isGoalState() {
		return this.tasksToPickup.isEmpty() && this.tasksToDeliver.isEmpty();
	}

	/**
	 * Method returning the heuristic value of the state. Calls one of three
	 * possible heuristic implementations depending on the chosen heuristic id.
	 */
	public double getHCost(int costPerKm) {
		switch (State.heuristicId) {
		case 0:
			return this.getHCostMinDistNeighbor(costPerKm);
		case 1:
			return this.getHCostMinDistTaskCity(costPerKm);
		case 2:
			return this.getHCostTotalDistEstimate(costPerKm);
		default:
			throw new AssertionError("Invalid heuristic id. Can only be equal to 0, 1 or 2.");
		}

	}

	/**
	 * Method implementing the procedure for computing the heuristic using the first
	 * definition. Heuristic value is based on the distance to the closest neighbor
	 * city and the total reward possible.
	 */
	public double getHCostMinDistNeighbor(int costPerKm) {
		double minCost = Double.POSITIVE_INFINITY;

		// If there are no tasks left we should not move to a neighbor anymore
		if (tasksToPickup.isEmpty() && tasksToDeliver.isEmpty())
			return 0;
		else {
			// Compute the minimum
			double cost;
			for (City c : location.neighbors()) {
				cost = location.distanceTo(c);
				if (cost < minCost) {
					minCost = cost;
				}
			}
		}

		return costPerKm * minCost - tasksToPickup.rewardSum() - tasksToDeliver.rewardSum();
	}

	/**
	 * Method implementing the procedure for computing the heuristic using the
	 * second definition. Heuristic value is based on the distance to the closest
	 * pickup OR delivery city of the remaining tasks and the total reward possible.
	 */
	public double getHCostMinDistTaskCity(int costPerKm) {
		double minCostPickup = Double.POSITIVE_INFINITY;
		double minCostDeliver = Double.POSITIVE_INFINITY;

		// If there are no tasks left the minimum distance to all tasks should be 0
		if (tasksToPickup.isEmpty() && tasksToDeliver.isEmpty())
			return 0;
		else {
			// Compute the two minima
			double cost;
			for (Task t : tasksToPickup) {
				cost = location.distanceTo(t.pickupCity);
				if (cost < minCostPickup) {
					minCostPickup = cost;
				}
			}
			for (Task t : tasksToDeliver) {
				cost = location.distanceTo(t.deliveryCity);
				if (cost < minCostDeliver) {
					minCostDeliver = cost;
				}
			}
		}

		return costPerKm * Math.min(minCostPickup, minCostDeliver) - tasksToPickup.rewardSum()
				- tasksToDeliver.rewardSum();
	}

	/**
	 * Method implementing the procedure for computing the heuristic using the third
	 * definition. Heuristic value is based on a weighted sum of the distance to the
	 * closest pickup city AND the distance to the closest delivery city of the
	 * remaining tasks and the total reward possible.
	 */
	public double getHCostTotalDistEstimate(int costPerKm) {
		double minCostPickup = Double.POSITIVE_INFINITY;
		double minCostDeliver = Double.POSITIVE_INFINITY;
		double cost;

		// If there are no tasks left to pickup the minimum distance to all pickup tasks
		// should be 0
		if (tasksToPickup.isEmpty())
			minCostPickup = 0;
		else {
			// Compute the minimum
			for (Task t : tasksToPickup) {
				cost = location.distanceTo(t.pickupCity);
				if (cost < minCostPickup) {
					minCostPickup = cost;
				}
			}
		}

		// If there are no tasks left to deliver the minimum distance to all delivery
		// tasks should be 0
		if (tasksToDeliver.isEmpty())
			minCostDeliver = 0;
		else {
			// Compute the minimum
			for (Task t : tasksToDeliver) {
				cost = location.distanceTo(t.deliveryCity);
				if (cost < minCostDeliver) {
					minCostDeliver = cost;
				}
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
		return String.format("In city %s, tasks to pickup: %s, tasks to deliver: %s", this.location,
				this.tasksToPickup.toString(), this.tasksToDeliver.toString());
	}

}
