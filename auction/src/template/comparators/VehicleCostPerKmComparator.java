package template.comparators;

import java.util.Comparator;

import logist.simulation.Vehicle;

/**
 * Comparator for the class Vehicle: the compare method takes into account the
 * cost per km of the vehicle only
 * 
 * @author Andrej Janchevski
 * @author Orazio Rillo
 */
public class VehicleCostPerKmComparator implements Comparator<Vehicle> {

	@Override
	public int compare(Vehicle v1, Vehicle v2) {

		if (v1.costPerKm() > v2.costPerKm())
			return 1;
		else if (v1.costPerKm() < v2.costPerKm())
			return -1;
		else
			return 0;
	}

}
