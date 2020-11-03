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
 * Implementation of the centralized agent The optimal plan to assign to
 * vehicles is computed using the SLS algorithm.
 *
 * @author Andrej Janchevski
 * @author Orazio Rillo
 */
@SuppressWarnings("unused")
public class CentralizedAgent implements CentralizedBehavior {

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
		// @see template.VariablesSet#init() to know more about the 3 different initial solutions. 
		int initialSolutionId = agent.readProperty("initial-solution-id", Integer.class, 1);		
		if (initialSolutionId != 1 && initialSolutionId != 2 && initialSolutionId != 3) {
			System.out.println("The initial solution id should be either 1, 2 or 3");
			System.exit(0);
		}
		
		long time_start = System.currentTimeMillis();

		// Find an initial solution 
		VariablesSet initialSolution = new VariablesSet(vehicles, tasks);
		boolean success = initialSolution.init(this.topology, initialSolutionId);
		
		// If the problem has no solution (e.g. there is a task whose weight is higher than each vehicle's capacity),
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
		for (int count=0; count<numIterations; count++) {
			tmpSolution = tmpSolution.localChoice(p);
			tmpCost = tmpSolution.computeObjective();
			if (tmpCost < optimalCost) {
				optimalSolution = tmpSolution;
				optimalCost = tmpCost;
			}
		}
		
		// Build the string to output
		StringBuilder output = new StringBuilder();
		output.append("\nPARAMETERS\n")
			.append("p = ").append(p).append("\n")
			.append("# of tasks = ").append(tasks.size()).append("\n")
			.append("# of vehicles = ").append(vehicles.size()).append("\n")
			.append("# of iterations = ").append(numIterations).append("\n")
			.append("\n")
			.append("COST of the optimal plan = ").append(optimalCost);
		
		System.out.println(output);
		
		return optimalSolution.inferPlans();
	}
}
