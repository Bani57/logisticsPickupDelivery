package template;

import java.util.ArrayList;
import java.util.HashSet;

import org.apache.commons.math.stat.descriptive.moment.StandardDeviation;

import logist.task.Task;
import logist.topology.Topology.City;

public class AuctionPlayer {

	private int id;
	private ArrayList<Task> wonTasks;
	private long currentProfit;
	private ArrayList<Long> pastBids;
	private double meanBid;
	private double bidStd;

	public AuctionPlayer(int id) {
		super();
		this.id = id;
		this.wonTasks = new ArrayList<>();
		this.currentProfit = 0;
		this.pastBids = new ArrayList<>();
		this.meanBid = 0;
		this.bidStd = 0;
	}

	public void updatePlayerStatus(Task t, boolean wonAuction, Long bid) {
		if (wonAuction) {
			this.wonTasks.add(t);
			this.currentProfit += bid;
		}
		int numPastBids = this.pastBids.size();
		this.pastBids.add(bid);
		this.meanBid = ((numPastBids * this.meanBid) + bid) / (double) (numPastBids + 1);
		this.bidStd = this.computeStdPastBids();
	}

	private double computeStdPastBids() {
		StandardDeviation std = new StandardDeviation();
		return std.evaluate(this.pastBids.stream().mapToDouble(l -> l).toArray());
	}

	public double getCurrentTotalProfit() {
		return this.currentProfit;
	}

	private double computeDissimilarityOfTask(Task t, double topologyDiameter) {
		HashSet<City> citiesInPath = new HashSet<>();

		// If the agent still hasn't won any task, in the best case there will be
		// vehicles with a homeCity equal to t.pickupCity and t.deliveryCity
		if (!hasWonTasks())
			return 0;

		// Get all of the cities in the current path of the agent
		for (Task wonTask : this.wonTasks) {
			citiesInPath.add(wonTask.pickupCity);
			citiesInPath.add(wonTask.deliveryCity);
		}

		// Compute the minimum distance, relative to the topology diameter, the agent
		// needs to travel to pickup and deliver this task
		double minRelativeDistanceToTaskPickupCity = 1.0;
		double minRelativeDistanceToTaskDeliveryCity = 1.0;
		double relativeDistanceToTaskCity;
		for (City cityInPath : citiesInPath) {

			relativeDistanceToTaskCity = t.pickupCity.distanceTo(cityInPath) / topologyDiameter;
			if (relativeDistanceToTaskCity < minRelativeDistanceToTaskPickupCity)
				minRelativeDistanceToTaskPickupCity = relativeDistanceToTaskCity;

			relativeDistanceToTaskCity = t.deliveryCity.distanceTo(cityInPath) / topologyDiameter;
			if (relativeDistanceToTaskCity < minRelativeDistanceToTaskDeliveryCity)
				minRelativeDistanceToTaskDeliveryCity = relativeDistanceToTaskCity;
		}

		// The dissimilarity is the average of these relative minimum distances
		return (minRelativeDistanceToTaskPickupCity + minRelativeDistanceToTaskDeliveryCity) / 2;

	}

	public double estimateTaskPriceLowerBound(Task t, double topologyDiameter) {
		double lowerBoundInterval = this.meanBid - 2 * this.bidStd;
		double upperBoundInterval = this.meanBid + 2 * this.bidStd;
		double dissimilarity = this.computeDissimilarityOfTask(t, topologyDiameter);

		return dissimilarity * (upperBoundInterval - lowerBoundInterval) + lowerBoundInterval;
	}

	public int getId() {
		return id;
	}

	public ArrayList<Task> getWonTasks() {
		return wonTasks;
	}

	public boolean hasWonTasks() {
		return !wonTasks.isEmpty();
	}

	public ArrayList<Long> getPastBids() {
		return pastBids;
	}

	public double getMeanBid() {
		return meanBid;
	}

	public double getBidStd() {
		return bidStd;
	}

}
