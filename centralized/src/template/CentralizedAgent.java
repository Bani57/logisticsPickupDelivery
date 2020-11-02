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
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 *
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
		
		final double p = agent.readProperty("p", Double.class, 0.5); 
		if (p > 1.0 || p < 0.0) {
			System.out.println("The parameter p should be between 0.0 and 1.0");
			System.exit(0);
		}
		
		int initialSolutionId = agent.readProperty("initial-solutions-id", Integer.class, 1);		
		if (initialSolutionId != 1 && initialSolutionId != 2 && initialSolutionId != 3) {
			System.out.println("The initial solution id should be either 1, 2 or 3");
			System.exit(0);
		}
		
		long time_start = System.currentTimeMillis();

		VariablesSet initialSolution = new VariablesSet(vehicles, tasks);
		boolean success = initialSolution.init(this.topology, initialSolutionId);
		
		if (!success) {
			System.out.println("The problem has no solution");
			System.exit(0);
		}

		System.out.println(initialSolution.toString());

		VariablesSet tmpSolution = initialSolution;
		double tmpCost;
		VariablesSet optimalSolution = initialSolution;
		double optimalCost = initialSolution.computeObjective();
		
		for (int count=0; count<1000; count++) {
			tmpSolution = tmpSolution.localChoice(p);
			tmpCost = tmpSolution.computeObjective();
			if (tmpCost < optimalCost) {
				optimalSolution = tmpSolution;
				optimalCost = tmpCost;
			}
			System.out.println(optimalCost);
		}

		System.out.println(optimalSolution.inferPlans().toString());

		return optimalSolution.inferPlans();
	}
}
