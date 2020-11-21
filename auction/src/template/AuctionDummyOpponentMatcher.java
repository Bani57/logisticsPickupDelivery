package template;

import java.io.File;
//the list of imports
import java.util.ArrayList;
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

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 * 
 */
@SuppressWarnings("unused")
public class AuctionDummyOpponentMatcher implements AuctionBehavior {

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private Random random;

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

		this.minBid = (long) Math.floor(minEdgeCost());
		this.topologyDiameter = this.topologyGraphDiameter();
		this.currentSolution = new VariablesSet(agent.vehicles(), new ArrayList<Task>());
		// Assume the opponent has the same vehicles as the player just to be able to
		// build a solution, however the choice of vehicles will not influence the
		// object value for the opponent anyway
		this.opponentSolution = new VariablesSet(agent.vehicles(), new ArrayList<Task>());

		this.player = new AuctionPlayer(agent.id(), topology, distribution);
		// We assume that only two agents will compete in the auction
		int opponentId = (this.agent.id() + 1) % 2;
		this.opponent = new AuctionPlayer(opponentId, topology, distribution);
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {

		this.player.updatePlayerStatus(previous, winner == agent.id(), bids[agent.id()]);
		this.opponent.updatePlayerStatus(previous, winner != agent.id(), bids[this.opponent.getId()]);

		if (winner == agent.id()) {
			this.currentSolution = this.updatedSolution;
		} else {
			this.opponentSolution = this.updatedSolutionOpponent;
		}
	}

	@Override
	public Long askPrice(Task task) {

		long time_start = System.currentTimeMillis();
		long time_current;

		double opponentBidLowerBound = this.opponent.estimateTaskPriceLowerBound(task, this.topologyDiameter);

		// TODO: check better why sometime marginalCost is negative
		// (hyp: either our optimization algorithm doesn't always find the optimum or we
		// have an error in the computation of the objective function)

		this.updatedSolution = this.getUpdatedSolution(this.currentSolution, task, time_start, true);
		// If it was not possible to transport the task because its weight was over the
		// maximum capacity, we have to surrender the task to the opponent
		if (this.updatedSolution == null)
			return null;

		// Estimate the current total cost of the opponent's plan and compute the
		// opponent's updated solution
		// With those we can compute another estimate of the lower bound of the
		// opponent's bid: a lower bound of the opponent's marginal cost
		Double currentCostOpponent = this.opponent.hasWonTasks() ? this.opponentSolution.computeObjective(false) : 0;
		this.updatedSolutionOpponent = this.getUpdatedSolution(this.opponentSolution, task, time_start, false);
		Double updatedCostOpponent = this.updatedSolutionOpponent.computeObjective(false);
		Double marginalCostOpponent = updatedCostOpponent - currentCostOpponent;

		// Update our lower bound opponent bid estimate with the new information if
		// necessary
		opponentBidLowerBound = Math.min(opponentBidLowerBound, marginalCostOpponent);

		Long actualBid = Math.max((long) opponentBidLowerBound, minBid);

		return actualBid;
	}

	public VariablesSet getUpdatedSolution(VariablesSet solution, Task auctionedTask, long time_start,
			boolean vehicleDependent) {

		// Read the number of iterations
		final int numIterations = agent.readProperty("num-iterations", Integer.class, 10000);

		// Read p, it has to be between 0 and 1
		final double p = agent.readProperty("p", Double.class, 0.9);
		if (p > 1.0 || p < 0.0) {
			System.out.println("The parameter p should be between 0.0 and 1.0");
			System.exit(0);
		}

		VariablesSet tmpSolution = (VariablesSet) solution.clone();
		boolean success = tmpSolution.assignTaskRandomly(auctionedTask, this.random);
		if (!success)
			return null;
		double tmpCost;
		VariablesSet optimalSolution = tmpSolution;
		double optimalCost = tmpSolution.computeObjective(vehicleDependent);
		long time_current;

		// Iterate the SLS until the termination condition is met
		for (int count = 0; count < numIterations; count++) {

			time_current = System.currentTimeMillis();

			// If the execution time is close to the timeout threshold by less than half a
			// second, stop the execution of the algorithm
			if (time_current - time_start > timeout_bid - 500)
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

//		long time_end = System.currentTimeMillis();
//
//		// Count the number of tasks assigned to each vehicle by the optimal solution
//		double[] tasksPerVehicle = new double[agent.vehicles().size()];
//		for (Task t : auctionTasks)
//			tasksPerVehicle[optimalSolution.getVehicle(optimalSolution.getTaskIdx(t.id)).id()]++;
//
//		// Compute the empirical standard deviation of the number of tasks per vehicle
//		StandardDeviation sd = new StandardDeviation();
//		double taskAssignmentSd = sd.evaluate(tasksPerVehicle);
//
//		// Build the string to output
//		StringBuilder output = new StringBuilder();
//		output.append("\nPARAMETERS\n").append("p = ").append(p).append("\n").append("# of tasks = ")
//				.append(auctionTasks.size()).append("\n").append("# of vehicles = ").append(agent.vehicles().size()).append("\n")
//				.append("# of iterations = ").append(numIterations).append("\n").append("\n")
//				.append("COST of the optimal plan = ").append(optimalCost).append("\tCOST PER TASK = ")
//				.append(optimalCost / ((double) auctionTasks.size())).append("\n").append("NUM TASKS PER VEHICLE = ")
//				.append(Arrays.toString(tasksPerVehicle)).append("\tSD = ").append(taskAssignmentSd).append("\n")
//				.append("\n").append("EXECUTION TIME (sec) = ").append((time_end - time_start) / 1000.0).append("\n");
//
//		System.out.println(output);

		// Update the optimal solution and return it
		return optimalSolution;
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {

		ArrayList<Task> tasksList = new ArrayList<Task>(tasks);

		// It's possible that the agent didn't win any tasks at the auction
		// In that case use the empty solution to return a list of empty plans
		if (tasksList.size() == 0)
			return this.currentSolution.inferPlans();

		// Read the number of iterations
		final int numIterations = agent.readProperty("num-iterations", Integer.class, 10000);

		// Read p, it has to be between 0 and 1
		final double p = agent.readProperty("p", Double.class, 1.0);
		if (p > 1.0 || p < 0.0) {
			System.out.println("The parameter p should be between 0.0 and 1.0");
			System.exit(0);
		}

		// Read initial solution id, valid values go from 1 to 3.
		// @see template.VariablesSet#init() to know more about the 3 different initial
		// solutions.
		int initialSolutionId = agent.readProperty("initial-solution-id", Integer.class, 1);
		if (initialSolutionId != 1 && initialSolutionId != 2 && initialSolutionId != 3) {
			System.out.println("The initial solution id should be either 1, 2 or 3");
			System.exit(0);
		}

		long time_start = System.currentTimeMillis();

//		// Find an initial solution
//		VariablesSet initialSolution = new VariablesSet(vehicles, tasksList);
//		boolean success = initialSolution.init(this.topology, initialSolutionId);
//
//		// If the problem has no solution (e.g. there is a task whose weight is higher
//		// than each vehicle's capacity),
//		// than exit
//		if (!success) {
//			System.out.println("The problem has no solution");
//			System.exit(0);
//		}

		this.currentSolution.setTasks(tasksList);

		VariablesSet tmpSolution = (VariablesSet) this.currentSolution.clone(); //initialSolution;
		double tmpCost;
		VariablesSet optimalSolution = (VariablesSet) this.currentSolution.clone();
		double optimalCost = this.currentSolution.computeObjective(true);
		long time_current;

		// Iterate the SLS until the termination condition is met
		for (int count = 0; count < numIterations; count++) {

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

		long time_end = System.currentTimeMillis();

		// Count the number of tasks assigned to each vehicle by the optimal solution
		double[] tasksPerVehicle = new double[vehicles.size()];
		for (Task t : tasks)
			tasksPerVehicle[optimalSolution.getVehicle(optimalSolution.getTaskIdx(t.id)).id()]++;

		// Compute the empirical standard deviation of the number of tasks per vehicle
		StandardDeviation sd = new StandardDeviation();
		double taskAssignmentSd = sd.evaluate(tasksPerVehicle);

		// Build the string to output
		StringBuilder output = new StringBuilder();
		output.append("AGENT ID = ").append(this.agent.id()).append("\n").append("NUM TASKS WON AT AUCTION = ")
				.append(tasks.size()).append("\n").append("COST of the optimal plan = ").append(optimalCost)
				.append("\n").append("TOTAL PROFIT OF THE AGENT = ")
				.append(this.player.getCurrentTotalReward() - optimalCost).append("\n");
		System.out.println(output);

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

	private double avgCostPerKm() {
		int weightSum = 0;
		for (Vehicle v : agent.vehicles()) {
			weightSum += v.costPerKm();
		}
		return (double) weightSum / agent.vehicles().size();
	}
}
