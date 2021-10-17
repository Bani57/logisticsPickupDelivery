package template;

import logist.topology.Topology.City;

/**
 * State representation in our model
 * 
 * @author Andrej Janchevski
 * @author Orazio Rillo
 */

public class State {
	private City location; // current location
	private City taskDestination; // destination city of the carried task, null if no task is carried

	public State(City location, City taskDestination) {
		super();
		this.location = location;
		this.taskDestination = taskDestination;
	}

	public City getLocation() {
		return location;
	}

	public void setLocation(City location) {
		this.location = location;
	}

	public City getTaskDestination() {
		return taskDestination;
	}

	public void setTaskDestination(City taskDestination) {
		this.taskDestination = taskDestination;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((location == null) ? 0 : location.hashCode());
		result = prime * result + ((taskDestination == null) ? 0 : taskDestination.hashCode());
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
		if (taskDestination == null) {
			if (other.taskDestination != null)
				return false;
		} else if (!taskDestination.equals(other.taskDestination))
			return false;
		return true;
	}

	@Override
	public String toString() {
		if (this.taskDestination != null)
			return String.format("IN %s, TASK TO %s AVAILABLE", this.location, this.taskDestination);
		else
			return String.format("IN %s, NO TASK AVAILABLE", this.location);
	}

}
