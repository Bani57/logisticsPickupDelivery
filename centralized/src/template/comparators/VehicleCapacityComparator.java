package template.comparators;

import java.util.Comparator;

import logist.simulation.Vehicle;

public class VehicleCapacityComparator implements Comparator<Vehicle> {

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
