package template.comparators;

import java.util.Comparator;

import logist.task.Task;
import logist.topology.Topology.City;

/**
 * Comparator for the class City: the compare method takes into account only the
 * distances of the two cities from the task's pickup city
 * 
 * @author Andrej Janchevski
 * @author Orazio Rillo
 */
public class CityDistanceFromTaskComparator implements Comparator<City> {

	private Task task;

	public CityDistanceFromTaskComparator(Task task) {
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
