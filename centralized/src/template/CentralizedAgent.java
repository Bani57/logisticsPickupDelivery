package template;

import java.io.File;
//the list of imports
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import logist.LogistSettings;

import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.behavior.CentralizedBehavior;
import logist.agent.Agent;
import logist.config.Parsers;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * Implementation of the centralized agent. The optimal plan to assign to
 * vehicles is computed using the SLS algorithm.
 *
 * @author Andrej Janchevski
 * @author Orazio Rillo
 */
@SuppressWarnings("unused")
public class CentralizedAgent implements CentralizedBehavior {

	static final boolean DEBUG = false;

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private long timeout_setup;
	private long timeout_plan;

	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

		// this code is used to get the timeouts
		LogistSettings ls = null;
		try {
			ls = Parsers.parseSettings("config" + File.separator + "settings_default.xml");
		} catch (Exception exc) {
			System.out.println("There was a problem loading the configuration file.");
		}

		// the setup method cannot last more than timeout_setup milliseconds
		timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
		// the plan method cannot execute more than timeout_plan milliseconds
		timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);

		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {

		int numIterations = 10000;

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

		// Find an initial solution
		VariablesSet initialSolution = new VariablesSet(vehicles, tasks);
		boolean success = initialSolution.init(this.topology, initialSolutionId);

		// If the problem has no solution (e.g. there is a task whose weight is higher
		// than each vehicle's capacity),
		// than exit
		if (!success) {
			System.out.println("The problem has no solution");
			System.exit(0);
		}

		VariablesSet tmpSolution = initialSolution;
		double tmpCost;
		VariablesSet optimalSolution = initialSolution;
		double optimalCost = initialSolution.computeObjective();

		// Iterate the SLS until the termination condition is met
		// TODO: add termination condition and write the comment properly
		for (int count = 0; count < numIterations; count++) {

			// Select the candidate neighbors
			List<VariablesSet> candidateNeighbors = chooseNeighbors(tmpSolution, vehicles, tasks);

			// Choose the best candidate
			if (!candidateNeighbors.isEmpty())
				tmpSolution = localChoice(candidateNeighbors, p);

			// Computer the objective function of the chosen solution
			tmpCost = tmpSolution.computeObjective();

			// If its cost is less then the current optimum, update the optimal solution
			if (tmpCost < optimalCost) {
				optimalSolution = tmpSolution;
				optimalCost = tmpCost;
			}
		}

		// Build the string to output
		StringBuilder output = new StringBuilder();
		output.append("\nPARAMETERS\n").append("p = ").append(p).append("\n").append("# of tasks = ")
				.append(tasks.size()).append("\n").append("# of vehicles = ").append(vehicles.size()).append("\n")
				.append("# of iterations = ").append(numIterations).append("\n").append("\n")
				.append("COST of the optimal plan = ").append(optimalCost);

		System.out.println(output);

		return optimalSolution.inferPlans();
	}

	/**
	 * Method that selects the neighbors of a given node in three ways: 
	 * 1) given a vehicle v1 and a vehicle v2 and a task t carried by v1,
	 *    assign t to v2 instead; 
	 * 2) given a task t and a time i, try to move the pickup time of t to i;
	 * 3) given a task t and a time i, try to move the delivery time of t to i;
	 * 
	 * @param node VariablesSet representing the starting point for the neighbors' search
	 * @param vehicles List of vehicles on the topology
	 * @param tasks TaskSet of tasks to pickup and deliver
	 * @return List of neighbors of node
	 */
	public List<VariablesSet> chooseNeighbors(VariablesSet node, List<Vehicle> vehicles, TaskSet tasks) {

		VariablesSet n;
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
				if (node.getVehicle(t.id).equals(v))
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
				if (node.getVehicle(t.id).equals(v))
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
	 * Method that choose a neighbor among the candidates one.
	 * This is done with a (1-p)-greedy policy i.e. 
	 *  - with probability p the best neighbor is selected, 
	 *  - with probability 1-p one neighbor is selected at random 
	 * 
	 * @requires candidateNeighbors is not empty
	 * 
	 * @param candidateNeighbors List of neighbors to choose among
	 * @param p (double) the probability of choosing the best among the neigbors
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
			Random rng = new Random();
			int candidateNeighborIndex = rng.nextInt(candidateNeighbors.size());
			VariablesSet candidateNeighbor = candidateNeighbors.get(candidateNeighborIndex);
			return candidateNeighbor;
		}

		double bestObjectiveValue = Double.POSITIVE_INFINITY;
		ArrayList<VariablesSet> bestCandidateNeighbors = new ArrayList<>();

		// Compute the objective function for every candidate neighbor solution and find
		// the collection of solutions with equal lowest cost
		for (VariablesSet candidateNeighbor : candidateNeighbors) {
			double neighborObjectiveValue = candidateNeighbor.computeObjective();
			if (neighborObjectiveValue < bestObjectiveValue) {
				bestObjectiveValue = neighborObjectiveValue;
				bestCandidateNeighbors = new ArrayList<>();
				bestCandidateNeighbors.add(candidateNeighbor);
			} else if (neighborObjectiveValue == bestObjectiveValue)
				bestCandidateNeighbors.add(candidateNeighbor);
		}

		// Pick uniformly at random one of the solutions with the same optimal cost
		Random rng = new Random();
		int bestCandidateNeighborIndex = rng.nextInt(bestCandidateNeighbors.size());
		VariablesSet bestCandidateNeighbor = bestCandidateNeighbors.get(bestCandidateNeighborIndex);

		return bestCandidateNeighbor;
	}

}
