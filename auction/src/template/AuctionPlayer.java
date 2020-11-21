package template;

import java.util.ArrayList;
import java.util.HashSet;

import org.apache.commons.math.stat.descriptive.moment.StandardDeviation;

import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

public class AuctionPlayer {

	private int id;
	private Topology topology;
	private TaskDistribution taskDistribution;
	private ArrayList<Task> wonTasks;
	private long currentReward;
	private ArrayList<Long> pastBids;
	private double meanBid;
	private double bidStd;

	public AuctionPlayer(int id, Topology topology, TaskDistribution td) {
		super();
		this.id = id;
		this.topology = topology;
		this.taskDistribution = td;
		this.wonTasks = new ArrayList<>();
		this.currentReward = 0;
		this.pastBids = new ArrayList<>();
		this.meanBid = 0;
		this.bidStd = 0;
	}

	private AuctionPlayer(int id, Topology topology, TaskDistribution taskDistribution, ArrayList<Task> wonTasks,
			long currentReward, ArrayList<Long> pastBids, double meanBid, double bidStd) {
		super();
		this.id = id;
		this.topology = topology;
		this.taskDistribution = taskDistribution;
		this.wonTasks = wonTasks;
		this.currentReward = currentReward;
		this.pastBids = pastBids;
		this.meanBid = meanBid;
		this.bidStd = bidStd;
	}

	public void updatePlayerStatus(Task t, boolean wonAuction, Long bid) {
		if (wonAuction) {
			this.wonTasks.add(t);
			this.currentReward += bid;
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

	public double getCurrentTotalReward() {
		return this.currentReward;
	}

	private double computeDissimilarityOfTask(City taskPickupCity, City taskDeliveryCity, double topologyDiameter) {
		// If the agent still hasn't won any task, in the best case there will be
		// vehicles with a homeCity equal to t.pickupCity and t.deliveryCity
		if (!hasWonTasks())
			return 0;

		// Get all of the cities in the current path of the agent
		HashSet<City> citiesInPath = new HashSet<>();
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

			relativeDistanceToTaskCity = taskPickupCity.distanceTo(cityInPath) / topologyDiameter;
			if (relativeDistanceToTaskCity < minRelativeDistanceToTaskPickupCity)
				minRelativeDistanceToTaskPickupCity = relativeDistanceToTaskCity;
		}
		citiesInPath.add(taskPickupCity);
		for (City cityInPath : citiesInPath) {
			relativeDistanceToTaskCity = taskDeliveryCity.distanceTo(cityInPath) / topologyDiameter;
			if (relativeDistanceToTaskCity < minRelativeDistanceToTaskDeliveryCity)
				minRelativeDistanceToTaskDeliveryCity = relativeDistanceToTaskCity;
		}

		// The dissimilarity is the average of these relative minimum distances
		return (minRelativeDistanceToTaskPickupCity + minRelativeDistanceToTaskDeliveryCity) / 2;

	}

	public double expectedFutureDissimilarity(Task auctionTask, double topologyDiameter) {
		AuctionPlayer futurePlayer = new AuctionPlayer(this.id, this.topology, this.taskDistribution,
				new ArrayList<>(this.wonTasks), this.currentReward, new ArrayList<>(pastBids), this.meanBid,
				this.bidStd);
		futurePlayer.updatePlayerStatus(auctionTask, true, (long) 0);

		double expectedFutureDissimilarity = 0.0;
		double futureTaskProbability;
		double futureTaskDissimilarity;

		for (City from : this.topology.cities()) {
			for (City to : this.topology.cities()) {
				if (!from.equals(to)) {
					futureTaskProbability = this.taskDistribution.probability(from, to) / this.topology.size();
					futureTaskDissimilarity = futurePlayer.computeDissimilarityOfTask(from, to, topologyDiameter);
					expectedFutureDissimilarity += futureTaskProbability * futureTaskDissimilarity;
				}
			}
		}
		return expectedFutureDissimilarity;
	}

	public double estimateTaskPriceLowerBound(Task t, double topologyDiameter) {
		double lowerBoundInterval = this.meanBid - 2 * this.bidStd;
		double upperBoundInterval = this.meanBid + 2 * this.bidStd;
		double dissimilarity = this.computeDissimilarityOfTask(t.pickupCity, t.deliveryCity, topologyDiameter);
		double expectedFutureDissimilarity = this.expectedFutureDissimilarity(t, topologyDiameter);

		// TODO: Add attribute for weight of future
		double weightOfFuture = 0.2;
		double discountedDissimilarity = (1 - weightOfFuture) * dissimilarity
				+ weightOfFuture * expectedFutureDissimilarity;

		return discountedDissimilarity * (upperBoundInterval - lowerBoundInterval) + lowerBoundInterval;
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
