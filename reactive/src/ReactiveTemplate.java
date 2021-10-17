package template;

import java.util.HashMap;
import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * Class that represents the reactive agent
 * 
 * @author Andrej Janchevski
 * @author Orazio Rillo
 */

public class ReactiveTemplate implements ReactiveBehavior {

	private Topology topology;
	private Agent myAgent;
	private int numActions; // total number of actions executed by the agent
	private HashMap<State, Integer> policy; // table that assigns to each state the optimal action in the current
											// topology

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		Double discount = agent.readProperty("discount-factor", Double.class, 0.95);

		// Checks that the discount factor is a legal value (i.e. between 0 and 1)
		if (discount > 1 || discount < 0) {
			System.out.printf("%s: Invalid discount factor %.2f. Valid values are in the range [0, 1].\n", agent.name(),
					discount);
			System.exit(0);
		}

		// Default precision value of the convergence test
		double epsilon = 1e-6;

		this.numActions = 0;
		this.topology = topology;
		this.myAgent = agent;

		int costPerKm = agent.vehicles().get(0).costPerKm();

		// Trains the agent (i.e. populate the policy table with the optimal action
		// choices for each state)
		ReactiveTraining training = new ReactiveTraining(topology, td, costPerKm);
		policy = training.trainMdpInfiniteHorizon(discount, epsilon);
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		Action action;
		City currentCity = vehicle.getCurrentCity();
		State currentState;

		// Initializes a variable currentState based on the information we can get by
		// the model; it represents the current state in our representation
		if (availableTask != null)
			currentState = new State(currentCity, availableTask.deliveryCity);
		else
			currentState = new State(currentCity, null);

		// Selects the optimal action for the current state using the policy
		int intAction = this.policy.get(currentState);

		if (intAction < this.topology.size())
			// Performs a movement action; the value of the action is the index of the city
			// the vehicle has to move to
			action = new Move(topology.cities().get(intAction));
		else
			// Performs a pickup action
			action = new Pickup(availableTask);

		// Reports the total profits and the average profit
		if (numActions >= 1) {
			System.out.println(this.myAgent.name() + ": The total profit after " + numActions + " actions is "
					+ myAgent.getTotalProfit() + " (average profit: " + (myAgent.getTotalProfit() / (double) numActions)
					+ ")");
		}
		numActions++;

		return action;
	}
}
