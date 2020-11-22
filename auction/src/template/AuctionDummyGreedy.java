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
 * Implementation of a self-interested auction agent. Differs from the main
 * implementation in that it does not utilize any information from the future or
 * the opponent's bids and only greedily seeks to maximize its own profit.
 * Computation of optimal plans during each bid price computation and the final
 * solution is based on our implementation of the SLS algorithm for the
 * centralized exercise.
 *
 * @author Andrej Janchevski
 * @author Orazio Rillo
 */
@SuppressWarnings("unused")
public class AuctionDummyGreedy implements AuctionBehavior {

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private Random random;

	private double p;
	private double minRelativeGain;
	private double maxRelativeGain;
	Long minBid;

	private AuctionPlayer player;

	private VariablesSet currentSolution;
	private VariablesSet updatedSolution;

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

		// Define the minimum possible bid the agent will make as the minimum cost for
		// traveling between two cities
		this.minBid = (long) Math.floor(minEdgeCost());

		// Initialize the agent's SLS solution
		this.currentSolution = new VariablesSet(agent.vehicles(), new ArrayList<Task>());

		// Initialize the agent as an AuctionPlayer object
		this.player = new AuctionPlayer(agent.id(), topology, distribution);
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {

		// Update the agent's statistics after knowing the result of the auction
		this.player.updatePlayerStatus(previous, winner == agent.id(), bids[agent.id()]);

		// Depending on the auction result update the SLS solution
		if (winner == agent.id())
			this.currentSolution = this.updatedSolution;
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

		// Compute the absolute and relative marginal cost of adding the task to our
		// plan
		// Achieved by analyzing how the value of the objective function changes before
		// and after adding the auctioned task to the plan, by performing SLS iterations
		// and computing the resulting objective value
		// Runs at most for half of the bid time available
		Double currentCost = this.player.hasWonTasks() ? this.currentSolution.computeObjective(true) : 0;
		this.updatedSolution = this.getUpdatedSolution(task, time_start);
		Double updatedCost = this.updatedSolution.computeObjective(true);
		Double marginalCost = updatedCost - currentCost;
		Double relativeMarginalCost = marginalCost / updatedCost;

		// Compute a tentative bid price to be asked for the task
		// Set to the marginal cost if still no tasks have been won at the auction and
		// computed using a risk-seeking utility formula otherwise
		Double relativeGain = this.player.hasWonTasks()
				? Math.pow(1 + maxRelativeGain - minRelativeGain, relativeMarginalCost) + minRelativeGain
				: 1.0;
		Long tentativeBid = (long) Math.ceil(relativeGain * marginalCost);

		// The bid cannot be below the minimum tolerated and to finalize the bid price,
		// it is adjusted to satisfy this constraint
		Long actualBid = Math.max(tentativeBid, minBid);

		return actualBid;
	}

	/**
	 * Helper method to run the SLS algorithm in order to obtain a new hypothetical
	 * optimal solution for the agent, assuming it has won a task at the auction
	 * 
	 * @param auctionedTask Task presented at the auction
	 * @param time_start    System time in milliseconds when the bid was started,
	 *                      needed for the stopping condition
	 * 
	 * @return Updated optimal solution
	 */
	public VariablesSet getUpdatedSolution(Task auctionedTask, long time_start) {

		// Set the initial solution as the given, modified by assigning the new task to
		// a random vehicle
		VariablesSet tmpSolution = (VariablesSet) this.currentSolution.clone();
		tmpSolution.assignTaskRandomly(auctionedTask, this.random);

		double tmpCost;
		VariablesSet optimalSolution = tmpSolution;
		double optimalCost = tmpSolution.computeObjective(true);
		long time_current;

		// Iterate the SLS until the termination condition is met
		while (true) {

			time_current = System.currentTimeMillis();

			// If the execution time is close to the timeout threshold by less than half a
			// second, stop the execution of the algorithm
			if (time_current - time_start > timeout_bid - 500)
				break;

			// Select the candidate neighbors
			List<VariablesSet> candidateNeighbors = chooseNeighbors(tmpSolution);

			// Choose the best candidate
			if (!candidateNeighbors.isEmpty())
				tmpSolution = localChoice(candidateNeighbors, p);

			// Computer the objective function of the chosen solution
			tmpCost = tmpSolution.computeObjective(true);

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
				tmpSolution = localChoice(candidateNeighbors, p);

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
	public VariablesSet localChoice(List<VariablesSet> candidateNeighbors, double p) {

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
			double neighborObjectiveValue = candidateNeighbor.computeObjective(true);
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
}
