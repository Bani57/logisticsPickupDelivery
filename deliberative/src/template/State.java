package template;

import logist.task.TaskSet;
import logist.topology.Topology.City;

public class State {
	
	private City location;
	private TaskSet tasksToPickup;
	private TaskSet tasksToDeliver;
	
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
	
	public boolean isGoalState()
	{
		return this.tasksToPickup.isEmpty() && this.tasksToDeliver.isEmpty();
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
