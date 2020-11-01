package template.comparators;

import java.util.Comparator;

import logist.task.Task;
import logist.topology.Topology.City;

public class VehicleDistanceComparator implements Comparator<City> {

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
