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

public class ReactiveTemplate implements ReactiveBehavior {
	
	private int numActions;
	private Topology topology;
	private Agent myAgent;
	private HashMap<State, Integer> policy;

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
		
		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		Double discount = agent.readProperty("discount-factor", Double.class,
				0.95);
		if(discount > 1 || discount < 0)
		{
			System.out.printf("%s: Invalid discount factor %.2f. Valid values are in the range [0, 1].\n", agent.name(), discount);
			System.exit(0);
		}
		double epsilon = 1e-6;

		this.numActions = 0;
		this.topology = topology;
		this.myAgent = agent;
		
		int costPerKm = agent.vehicles().get(0).costPerKm();
		
		ReactiveTraining training = new ReactiveTraining(topology, td, costPerKm);
		policy = training.trainMdpInfiniteHorizon(discount, epsilon);
		
//		for(State s: training.getStateSet())
//			System.out.println(s + " " + policy.get(s));
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		Action action;
		
		City currentCity = vehicle.getCurrentCity();
		State currentState;
		
		if (availableTask != null)
			currentState = new State(currentCity, availableTask.deliveryCity);
		
		else
			currentState = new State(currentCity, null);
		
		int intAction = this.policy.get(currentState);
		
		if (intAction < this.topology.size())
			action = new Move(topology.cities().get(intAction));
		else
			action = new Pickup(availableTask);
		
		if (numActions >= 1) {
			System.out.println(this.myAgent.name() + ": The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
		}
		numActions++;
		
		return action;
	}
}
