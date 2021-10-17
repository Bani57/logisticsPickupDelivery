package template;

import java.util.ArrayList;
import java.util.HashSet;

import org.apache.commons.math.stat.descriptive.moment.StandardDeviation;

import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * Class that represents the state and parameters of an agent participating in
 * an auction. Contains methods to compute relevant statistics from the data
 * about the agent's past bids and won tasks
 * 
 * @author Andrej Janchevski
 * @author Orazio Rillo
 */
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
		this.id = id; // Id of the agent in the auction
		this.topology = topology; // The selected topology for the auction
		this.taskDistribution = td; // The selected task distribution for the auction
		this.wonTasks = new ArrayList<>(); // List of current won tasks during auction rounds
		this.currentReward = 0; // Current total reward from the auction
		this.pastBids = new ArrayList<>(); // List of all past bids presented during the auction
		this.meanBid = 0; // Current mean past bid
		this.bidStd = 0; // Current standard deviation of past bid values
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

	/**
	 * Method to update the player's data after obtaining the results from an
	 * auction round
	 * 
	 * @param t          Task presented during the auction round solution to use as
	 *                   a starting point
	 * @param wonAuction Whether the agent had won that auction round
	 * @param bid        The value the agent bid for the task
	 * 
	 */
	public void updatePlayerStatus(Task t, boolean wonAuction, Long bid) {

		// If the player had won the auction we can the won task to the list and
		// increase the total reward depending on the bid
		if (wonAuction) {
			this.wonTasks.add(t);
			this.currentReward += bid;
		}

		// In any case, store the bid and update the moments using the new data point
		this.pastBids.add(bid);
		int numPastBids = this.pastBids.size();
		this.meanBid = ((numPastBids * this.meanBid) + bid) / (double) (numPastBids + 1);
		this.bidStd = this.computeStdPastBids();
	}

	/**
	 * Helper method to compute the standard deviation of the past bid values
	 */
	private double computeStdPastBids() {
		StandardDeviation std = new StandardDeviation();
		return std.evaluate(this.pastBids.stream().mapToDouble(l -> l).toArray());
	}

	/**
	 * Method to compute the "dissimilarity" of an auctioned task's pickup and
	 * delivery cities.
	 * 
	 * We define the dissimilarity to be the average between:
	 * 
	 * - the minimum distance between any city in the current optimal transport path
	 * for the agent and the task's pickup city;
	 * 
	 * - the minimum distance between any city in the current optimal transport path
	 * for the agent including the task's pickup city and the task's delivery city;
	 *
	 * Note that the distances are normalized w.r.t. the diameter of the selected
	 * topology in order for the dissimilarity to be a value in [0, 1].
	 * 
	 * @param taskPickupCity:   The auctioned task's pickup City
	 * @param taskDeliveryCity: The auctioned task's delivery City
	 * @param topologyDiameter: Diameter of the selected topology, assumed to be
	 *                          already correctly computed
	 * 
	 * @return "dissimilarity" of the Task, decimal value in [0, 1]
	 */
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

	/**
	 * Method to compute the "expected future dissimilarity" of an auctioned task.
	 * We define the expected future "expected future dissimilarity" to be the
	 * expectation w.r.t. the given task distribution of the task "dissimilarity"
	 * for the agent after hypothetically winning the auctioned task.
	 * 
	 * @param auctionTask:      The auctioned task
	 * @param topologyDiameter: Diameter of the selected topology, assumed to be
	 *                          already correctly computed
	 * 
	 * @return "expected future dissimilarity" of the Task, decimal value in [0, 1]
	 */
	public double expectedFutureDissimilarity(Task auctionTask, double topologyDiameter) {

		// Construct a future version of the player that has hypothetically won the
		// auctioned task
		AuctionPlayer futurePlayer = new AuctionPlayer(this.id, this.topology, this.taskDistribution,
				new ArrayList<>(this.wonTasks), this.currentReward, new ArrayList<>(pastBids), this.meanBid,
				this.bidStd);
		futurePlayer.updatePlayerStatus(auctionTask, true, (long) 0);

		// Compute the expectation as a weighted sum over the support of the task
		// distribution, using the Law of the Unconscious Statistician
		// Note the usage of Bayes' theorem to convert the conditional into joint
		// probabilities
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

	/**
	 * Method to estimate the lower bound of the player's bid for a task using a
	 * linear combination of the task's "dissimilarity" and "expected future
	 * dissimilarity"
	 * 
	 * @param t:                 The auctioned task
	 * @param topologyDiameter:  Diameter of the selected topology, assumed to be
	 *                           already correctly computed
	 * @param discountForFuture: Discount factor for the expected future
	 *                           dissimilarity, a parameter of the agent
	 * 
	 * @return estimated lower bound for the task price, decimal value in a 2*sigma
	 *         interval around the mean bid value
	 */
	public double estimateTaskPriceLowerBound(Task t, double topologyDiameter, double discountForFuture) {

		// Compute the two quantities based on the idea of task dissimilarity
		double dissimilarity = this.computeDissimilarityOfTask(t.pickupCity, t.deliveryCity, topologyDiameter);
		double expectedFutureDissimilarity = this.expectedFutureDissimilarity(t, topologyDiameter);

		// Construct the final discounted dissimilarity as a linear combination of the
		// present and the future
		double discountedDissimilarity = (1 - discountForFuture) * dissimilarity
				+ discountForFuture * expectedFutureDissimilarity;

		// Assume the bid prices for the agent follow a Gaussian distribution and
		// construct the 2*sigma interval around the mean in order to have 95%
		// confidence
		double lowerBoundInterval = this.meanBid - 2 * this.bidStd;
		double upperBoundInterval = this.meanBid + 2 * this.bidStd;

		// Use the discounted dissimilarity to select the properly scaled value in the
		// interval
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

	public double getCurrentTotalReward() {
		return this.currentReward;
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
