package template;

import java.io.File;
//the list of imports
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.apache.commons.math.stat.descriptive.moment.StandardDeviation;

import cern.colt.Arrays;
import logist.LogistSettings;
import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.config.Parsers;
import logist.agent.Agent;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;
import template.comparators.VehicleCapacityComparator;

/**
 * Implementation of our final auction agent. Utilizes all developed techniques
 * to estimate marginal costs, opponent bids and future tasks. Computation of
 * optimal plans during each bid price computation and the final solution is
 * based on our implementation of the SLS algorithm for the centralized
 * exercise.
 *
 * @author Andrej Janchevski
 * @author Orazio Rillo
 */
@SuppressWarnings("unused")
public class AuctionAgent implements AuctionBehavior {

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private Random random;

	private double p;
	private double minRelativeGain;
	private double maxRelativeGain;
	private double discountForFuture;
	private Long minBid;
	private double topologyDiameter;

	private AuctionPlayer player;
	private AuctionPlayer opponent;

	private VariablesSet currentSolution;
	private VariablesSet updatedSolution;
	private VariablesSet opponentSolution;
	private VariablesSet updatedSolutionOpponent;

	private long timeout_setup;
	private long timeout_plan;
	private long timeout_bid;

	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;

		// this code is used to get the timeouts
		LogistSettings ls = null;
		try {
			ls = Parsers.parseSettings("config" + File.separator + "settings_auction.xml");
		} catch (Exception exc) {
			System.out.println("There was a problem loading the configuration file.");
		}

		// the setup method cannot last more than timeout_setup milliseconds
		timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
		// the plan method cannot execute more than timeout_plan milliseconds
		timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);
		// the askPrice method cannot execute more than timeout_bid milliseconds
		timeout_bid = ls.get(LogistSettings.TimeoutKey.BID);

		long seed = -9019554669489983951L * (agent.id() + 1);
		this.random = new Random(seed);

		// Read the parameters of the agent from agents.xml
		// The parameter for the SLS algorithm
		this.p = agent.readProperty("p", Double.class, 0.9);
		if (this.p > 1.0 || this.p < 0.0) {
			System.out.println("The parameter p should be between 0.0 and 1.0");
			System.exit(0);
		}
		// The minimum additive increase of our bid prices above the marginal cost
		this.minRelativeGain = agent.readProperty("min-relative-gain", Double.class, 0.2);
		if (this.minRelativeGain < 0.0) {
			System.out.println("The parameter min-relative-gain should be at least 0.0");
			System.exit(0);
		}
		// The maximum additive increase of our bid prices above the marginal cost
		this.maxRelativeGain = agent.readProperty("max-relative-gain", Double.class, 0.8);
		if (this.maxRelativeGain < this.minRelativeGain) {
			System.out.println("The parameter max-relative-gain should be at least equal to min-relative-gain");
			System.exit(0);
		}
		// How much weight to put on the expected relative cost of future tasks
		this.discountForFuture = agent.readProperty("discount-for-future", Double.class, 0.2);
		if (this.discountForFuture > 1.0 || this.discountForFuture < 0.0) {
			System.out.println("The parameter discount-for-future should be between 0.0 and 1.0");
			System.exit(0);
		}

		// Define the minimum possible bid the agent will make as the minimum cost for
		// traveling between two cities
		this.minBid = (long) Math.floor(minEdgeCost());
		// Compute the diameter of the chosen topology for the auction, defined as the
		// maximum shortest path length between two cities in the topology graph
		this.topologyDiameter = this.topologyGraphDiameter();

		// Initialize the agent's SLS solution
		this.currentSolution = new VariablesSet(agent.vehicles(), new ArrayList<Task>());
		// Initialize the estimated SLS solution for the opponent
		// Assume the opponent has the same vehicles as the player just to be able to
		// build a solution, however the choice of vehicles will not influence the
		// object value for the opponent
		this.opponentSolution = new VariablesSet(agent.vehicles(), new ArrayList<Task>());

		// Initialize the agent and its opponent as AuctionPlayer objects
		// We assume that only two agents will compete in the auction
		this.player = new AuctionPlayer(agent.id(), topology, distribution);
		int opponentId = (this.agent.id() + 1) % 2;
		this.opponent = new AuctionPlayer(opponentId, topology, distribution);
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {

		// For both participants in the auction update their statistics after knowing
		// the result of the auction
		this.player.updatePlayerStatus(previous, winner == agent.id(), bids[agent.id()]);
		this.opponent.updatePlayerStatus(previous, winner != agent.id(), bids[this.opponent.getId()]);

		// Depending on the auction result update the respective SLS solution
		if (winner == agent.id())
			this.currentSolution = this.updatedSolution;
		else
			this.opponentSolution = this.updatedSolutionOpponent;
	}

	@Override
	public Long askPrice(Task task) {

		// If it was not possible to transport the task because its weight was over the
		// maximum capacity, we have to surrender the task to the opponent
		int maxCapacity = Collections.max(agent.vehicles(), new VehicleCapacityComparator()).capacity();
		if (task.weight > maxCapacity)
			return null;

		// Start the bid timer
		long time_start = System.currentTimeMillis();

		// Estimate a lower bound value for the opponent bid price for this task
		// Utilizes the statistics of the opponent's bids as well as the expected future
		// value
		double opponentBidLowerBound = this.opponent.estimateTaskPriceLowerBound(task, this.topologyDiameter,
				this.discountForFuture);

		// Compute the absolute and relative marginal cost of adding the task to our
		// plan
		// Achieved by analyzing how the value of the objective function changes before
		// and after adding the auctioned task to the plan, by performing SLS iterations
		// and computing the resulting objective value
		// Runs at most for half of the bid time available
		Double currentCost = this.player.hasWonTasks() ? this.currentSolution.computeObjective(true) : 0;
		this.updatedSolution = this.getUpdatedSolution(this.currentSolution, task, time_start, true);
		Double updatedCost = this.updatedSolution.computeObjective(true);
		Double marginalCost = updatedCost - currentCost;
		Double relativeMarginalCost = marginalCost / updatedCost;

		// Restart the bid timer
		time_start = System.currentTimeMillis();

		// Similarly, estimate the absolute marginal cost of adding the task to the
		// opponent's plan, by using a crude lower bound on the objective value
		// Runs at most for the other half of the bid time available
		Double currentCostOpponent = this.opponent.hasWonTasks() ? this.opponentSolution.computeObjective(false) : 0;
		this.updatedSolutionOpponent = this.getUpdatedSolution(this.opponentSolution, task, time_start, false);
		Double updatedCostOpponent = this.updatedSolutionOpponent.computeObjective(false);
		Double marginalCostOpponent = updatedCostOpponent - currentCostOpponent;

		// Compute the expected future relative cost of the task for the agent
		Double expectedFutureDissimilarity = this.player.expectedFutureDissimilarity(task, this.topologyDiameter);

		// Compute a tentative bid price to be asked for the task
		// Based on the expected future cost if still no tasks have been won at the
		// auction and computed using a risk-seeking utility formula otherwise
		Double relativeGain = this.player.hasWonTasks()
				? Math.pow(1 + maxRelativeGain - minRelativeGain, relativeMarginalCost) + minRelativeGain
				: Math.sqrt(expectedFutureDissimilarity);
		double tentativeBid = relativeGain * marginalCost;

		// Compute the current total profit for both auction participants
		double totalProfitPlayer = this.player.getCurrentTotalReward() - currentCost;
		double totalProfitOpponent = this.opponent.getCurrentTotalReward() - currentCostOpponent;

		// Based on the values of all variables, perform the appropriate "business
		// practice" to adjust the tentative bid
		// Sampling from an exponential distribution with a modified support is
		// performed to introduce unpredictability
		double randomBid;

		if (tentativeBid <= opponentBidLowerBound) {
			// Case when the tentative bid price is lower than the estimated lower bound of
			// opponent bids
			// This means that the utility of the task was undervalued, and we should
			// increase the price to a random value close to the opponent's lower bound

			double maxIncrease;
			if (tentativeBid < marginalCost && marginalCost < opponentBidLowerBound) {
				maxIncrease = opponentBidLowerBound - marginalCost;
				randomBid = this.sampleExponentialIntervalIncreasing(marginalCost, marginalCost + maxIncrease);
			} else {
				maxIncrease = opponentBidLowerBound - tentativeBid;
				randomBid = this.sampleExponentialIntervalIncreasing(tentativeBid, tentativeBid + maxIncrease);
			}
		} else {
			if (marginalCost >= opponentBidLowerBound) {
				// Case when both the tentative bid and the agent's marginal cost was higher
				// than the estimated lower bound of opponent bids
				// This means that this task is estimated to be more costly for the agent than
				// the opponent

				double maxReduction = totalProfitPlayer - marginalCost - totalProfitOpponent + marginalCostOpponent;
				if (maxReduction > 0) {
					// If it is possible to still have the higher profit after bidding below the
					// marginalCost, reduce the price to a random value below the opponent's lower
					// bound, but close to it
					randomBid = this.sampleExponentialIntervalIncreasing(opponentBidLowerBound - maxReduction,
							opponentBidLowerBound);
				} else {
					// Otherwise, this task is estimated to be just too costly for the player
					// compared to the opponent, bid randomly in between the marginal cost and
					// tentative bid, biased to be closer to the smaller of the two
					randomBid = this.sampleExponentialIntervalDecreasing(Math.min(marginalCost, tentativeBid),
							Math.max(marginalCost, tentativeBid));
				}
			} else {
				// Case when the tentative bid was higher than the estimated lower bound of
				// opponent bids, but the marginal cost was lower
				// This means that the utility of the task was overvalued and the competition
				// might win
				// Offer a carefully picked discount to beat the competition, while still having
				// profit, by selecting a random value in (marginalCost, opponentBidLowerBound)

				double minDiscount = tentativeBid - opponentBidLowerBound;
				double maxDiscount = tentativeBid - marginalCost;
				randomBid = this.sampleExponentialIntervalIncreasing(tentativeBid - maxDiscount,
						tentativeBid - minDiscount);
			}
		}

		// The random bid cannot be below the minimum tolerated and to finalize the bid
		// price, it is adjusted to satisfy this constraint
		Long actualBid = Math.max((long) Math.ceil(randomBid), minBid);

		return actualBid;
	}

	/**
	 * Helper method to run the SLS algorithm in order to obtain a new hypothetical
	 * optimal solution for an agent, assuming it has won a task at the auction
	 * 
	 * @param solution         VariablesSet representing the previous optimal
	 *                         solution to use as a starting point
	 * @param auctionedTask    Task presented at the auction
	 * @param time_start       System time in milliseconds when the bid was started,
	 *                         needed for the stopping condition
	 * @param vehicleDependent Whether to optimize the classic vehicle dependent
	 *                         objective function (see
	 *                         VariablesSet.computeObjective)
	 * 
	 * @return Updated optimal solution
	 */
	public VariablesSet getUpdatedSolution(VariablesSet solution, Task auctionedTask, long time_start,
			boolean vehicleDependent) {

		// Set the initial solution as the given, modified by assigning the new task to
		// a random vehicle
		VariablesSet tmpSolution = (VariablesSet) solution.clone();
		tmpSolution.assignTaskRandomly(auctionedTask, this.random);

		double tmpCost;
		VariablesSet optimalSolution = tmpSolution;
		double optimalCost = tmpSolution.computeObjective(vehicleDependent);
		long time_current;

		// Iterate the SLS until the termination condition is met
		while (true) {

			time_current = System.currentTimeMillis();

			// If the execution time is close to the timeout threshold by less than half a
			// second, stop the execution of the algorithm
			if (time_current - time_start > timeout_bid / 2 - 250)
				break;

			// Select the candidate neighbors
			List<VariablesSet> candidateNeighbors = chooseNeighbors(tmpSolution);

			// Choose the best candidate
			if (!candidateNeighbors.isEmpty())
				tmpSolution = localChoice(candidateNeighbors, p, vehicleDependent);

			// Computer the objective function of the chosen solution
			tmpCost = tmpSolution.computeObjective(vehicleDependent);

			// If its cost is less then the current optimum, update the optimal solution
			if (tmpCost < optimalCost) {
				optimalSolution = tmpSolution;
				optimalCost = tmpCost;
			}
		}

		// Return the new optimal solution
		return optimalSolution;
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {

		// It's possible that the agent didn't win any tasks at the auction
		// In that case use the empty solution to return a list of empty plans
		if (tasks.size() == 0)
			return this.currentSolution.inferPlans();

		// Start the plan timer
		long time_start = System.currentTimeMillis();

		// Set the initial solution for the algorithm as the most recently updated
		// solution during the auction
		// First update the references to the tasks in the VariablesSet object w.r.t.
		// the given updated set of tasks
		ArrayList<Task> tasksList = new ArrayList<Task>(tasks);
		this.currentSolution.setTasks(tasksList);
		VariablesSet tmpSolution = (VariablesSet) this.currentSolution.clone();
		double tmpCost;
		VariablesSet optimalSolution = (VariablesSet) this.currentSolution.clone();
		double optimalCost = this.currentSolution.computeObjective(true);
		long time_current;

		// Iterate the SLS until the termination condition is met
		while (true) {

			time_current = System.currentTimeMillis();

			// If the execution time is close to the timeout threshold by less than half a
			// second, stop the execution of the algorithm
			if (time_current - time_start > timeout_plan - 500)
				break;

			// Select the candidate neighbors
			List<VariablesSet> candidateNeighbors = chooseNeighbors(tmpSolution);

			// Choose the best candidate
			if (!candidateNeighbors.isEmpty())
				tmpSolution = localChoice(candidateNeighbors, p, true);

			// Computer the objective function of the chosen solution
			tmpCost = tmpSolution.computeObjective(true);

			// If its cost is less then the current optimum, update the optimal solution
			if (tmpCost < optimalCost) {
				optimalSolution = tmpSolution;
				optimalCost = tmpCost;
			}
		}

		// Return the optimal plans
		return optimalSolution.inferPlans();
	}

	/**
	 * Method that selects the neighbors of a given node in three ways: 1) given a
	 * vehicle v1 and a vehicle v2 and a task t carried by v1, assign t to v2
	 * instead; 2) given a task t and a time i, try to move the pickup time of t to
	 * i; 3) given a task t and a time i, try to move the delivery time of t to i;
	 * 
	 * @param node VariablesSet representing the starting point for the neighbors'
	 *             search
	 * @return List of neighbors of node
	 */
	public List<VariablesSet> chooseNeighbors(VariablesSet node) {

		VariablesSet n;
		List<Vehicle> vehicles = node.getVehicles();
		ArrayList<Task> tasks = node.getTasks();
		ArrayList<VariablesSet> neighbors = new ArrayList<>();

		for (Task t : tasks) {
			for (Vehicle v : vehicles) {
				n = node.moveTaskToVehicle(t, v);
				if (n != null) {
					neighbors.add(n);
				}
			}
		}

		for (Vehicle v : vehicles) {
			ArrayList<Task> carriedTasks = new ArrayList<>();
			for (Task t : tasks) {
				if (node.getVehicle(node.getTaskIdx(t.id)).equals(v))
					carriedTasks.add(t);
			}
			for (Task t : carriedTasks) {
				for (int newPickupTime = 0; newPickupTime < 2 * carriedTasks.size(); newPickupTime++) {
					n = node.changePickupTime(t, newPickupTime);
					if (n != null) {
						neighbors.add(n);
					}
				}
			}
		}

		for (Vehicle v : vehicles) {
			ArrayList<Task> carriedTasks = new ArrayList<>();
			for (Task t : tasks) {
				if (node.getVehicle(node.getTaskIdx(t.id)).equals(v))
					carriedTasks.add(t);
			}
			for (Task t : carriedTasks) {
				for (int newDeliveryTime = 0; newDeliveryTime < 2 * carriedTasks.size(); newDeliveryTime++) {
					n = node.changeDeliveryTime(t, newDeliveryTime);
					if (n != null) {
						neighbors.add(n);
					}
				}
			}
		}

		return neighbors;
	}

	/**
	 * Method that choose a neighbor among the candidates one. This is done with a
	 * (1-p)-greedy policy i.e. - with probability p the best neighbor is selected,
	 * - with probability 1-p one neighbor is selected at random
	 * 
	 * @requires candidateNeighbors is not empty
	 * 
	 * @param candidateNeighbors List of neighbors to choose among
	 * @param p                  (double) the probability of choosing the best among
	 *                           the neighbors
	 * @return VariablesSet the selected neighbor
	 */
	public VariablesSet localChoice(List<VariablesSet> candidateNeighbors, double p, boolean vehicleDependent) {

		// Random sample a Bernoulli(p) distribution to know whether to return the old
		// solution or the best neighbor

		int chooseBestNeighbor;

		if (p == 0)
			chooseBestNeighbor = 0;
		else if (p == 1)
			chooseBestNeighbor = 1;
		else {
			uchicago.src.sim.util.Random.createBinomial(1, p);
			chooseBestNeighbor = uchicago.src.sim.util.Random.binomial.nextInt(1, p);
		}

		if (chooseBestNeighbor == 0) {
			// Pick uniformly at random one of the solutions
			int candidateNeighborIndex = this.random.nextInt(candidateNeighbors.size());
			VariablesSet candidateNeighbor = candidateNeighbors.get(candidateNeighborIndex);
			return candidateNeighbor;
		}

		double bestObjectiveValue = Double.POSITIVE_INFINITY;
		ArrayList<VariablesSet> bestCandidateNeighbors = new ArrayList<>();

		// Compute the objective function for every candidate neighbor solution and find
		// the collection of solutions with equal lowest cost
		for (VariablesSet candidateNeighbor : candidateNeighbors) {
			double neighborObjectiveValue = candidateNeighbor.computeObjective(vehicleDependent);
			if (neighborObjectiveValue < bestObjectiveValue) {
				bestObjectiveValue = neighborObjectiveValue;
				bestCandidateNeighbors = new ArrayList<>();
				bestCandidateNeighbors.add(candidateNeighbor);
			} else if (neighborObjectiveValue == bestObjectiveValue)
				bestCandidateNeighbors.add(candidateNeighbor);
		}

		// Pick uniformly at random one of the solutions with the same optimal cost
		int bestCandidateNeighborIndex = this.random.nextInt(bestCandidateNeighbors.size());
		VariablesSet bestCandidateNeighbor = bestCandidateNeighbors.get(bestCandidateNeighborIndex);

		return bestCandidateNeighbor;
	}

	/**
	 * Helper method to compute the minimum edge cost in the selected topology, to
	 * be used as the minimum possible bid price. For efficiency uses BFS with cycle
	 * detection to traverse the graph and process every edge only once
	 * 
	 * @return Minimum edge cost of the topology, defined as the average cost per KM
	 *         of the agent's vehicles times the shortest distance between two
	 *         neighboring cities
	 */
	private double minEdgeCost() {
		HashSet<City> visited = new HashSet<>();
		LinkedList<City> toVisit = new LinkedList<>();

		City currentCity = topology.cities().get(0);
		toVisit.add(currentCity);
		visited.add(currentCity);

		double avgCostPerKm = avgCostPerKm();
		double minDist = Double.POSITIVE_INFINITY;

		while (!toVisit.isEmpty()) {
			currentCity = toVisit.poll();
			for (City neighbor : currentCity.neighbors()) {
				double dist = currentCity.distanceTo(neighbor);
				if (dist < minDist)
					minDist = dist;
				if (!visited.contains(neighbor)) {
					toVisit.add(neighbor);
					visited.add(neighbor);
				}
			}
		}

		return minDist * avgCostPerKm;
	}

	/**
	 * Helper method to compute the graph diameter of the selected topology, to be
	 * used as a normalization constant for dissimilarities
	 * 
	 * @return Diameter of the topology, defined as the longest length of a shortest
	 *         path between two cities in the topology
	 */
	private double topologyGraphDiameter() {
		int numCities = this.topology.size();
		double maxShortestPathLength = 0;
		for (int i = 0; i < numCities; i++) {
			City city1 = this.topology.cities().get(i);
			for (int j = i + 1; j < numCities; j++) {
				City city2 = this.topology.cities().get(j);
				double shortestPathLength = city1.distanceTo(city2);
				if (shortestPathLength > maxShortestPathLength)
					maxShortestPathLength = shortestPathLength;
			}
		}
		return maxShortestPathLength;
	}

	/**
	 * Helper method to compute the mean cost per KM for the agent's vehicles
	 * 
	 * @return Average cost per KM, as a decimal value
	 */
	private double avgCostPerKm() {
		int sum = 0;
		for (Vehicle v : agent.vehicles()) {
			sum += v.costPerKm();
		}
		return (double) sum / agent.vehicles().size();
	}

	/**
	 * Helper method to sample a random value from the distribution with PDF p(x) =
	 * exp(x) / (exp(upper) - exp(lower)) on the support [lower, upper] Utilizes the
	 * Inverse Transform Method to map the Uniform(0, 1) random sample to the
	 * required distribution
	 * 
	 * @return Random decimal value in (lower, upper), biased to be closer to the
	 *         upper bound
	 */
	private double sampleExponentialIntervalIncreasing(double lower, double upper) {
		double normalizingConstant = Math.exp(1) - Math.exp(0);
		double relativeExpSample = Math.log(normalizingConstant * Math.random() + Math.exp(0));
		return relativeExpSample * (upper - lower) + lower;
	}

	/**
	 * Helper method to sample a random value from the distribution with PDF p(x) =
	 * exp(-x) / (exp(-lower) - exp(-upper)) on the support [lower, upper] Utilizes
	 * the Inverse Transform Method to map the Uniform(0, 1) random sample to the
	 * required distribution
	 * 
	 * @return Random decimal value in (lower, upper), biased to be closer to the
	 *         lower bound
	 */
	private double sampleExponentialIntervalDecreasing(double lower, double upper) {
		double normalizingConstant = Math.exp(-0) - Math.exp(-1);
		double relativeExpSample = -Math.log(Math.exp(-0) - normalizingConstant * Math.random());
		return relativeExpSample * (upper - lower) + lower;
	}
}
