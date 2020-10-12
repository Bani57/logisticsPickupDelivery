package template;

/* import table */
import logist.simulation.Vehicle;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Stack;

import logist.agent.Agent;
import logist.behavior.DeliberativeBehavior;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * An optimal planner for one vehicle.
 */
@SuppressWarnings("unused")
public class DeliberativeAgent implements DeliberativeBehavior {

	enum Algorithm { BFS, ASTAR }
	
	/* Environment */
	Topology topology;
	TaskDistribution td;
	
	/* the properties of the agent */
	Agent agent;
	int capacity;
	int numCities;

	/* the planning class */
	Algorithm algorithm;
	
	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
		this.topology = topology;
		this.td = td;
		this.agent = agent;
		
		// initialize the planner
		this.capacity = agent.vehicles().get(0).capacity();
		String algorithmName = agent.readProperty("algorithm", String.class, "ASTAR");
		
		// Throws IllegalArgumentException if algorithm is unknown
		algorithm = Algorithm.valueOf(algorithmName.toUpperCase());
		
		// ...
	}
	
	@Override
	public Plan plan(Vehicle vehicle, TaskSet tasks) {
		Plan plan;

		// Compute the plan with the selected algorithm.
		switch (algorithm) {
		case ASTAR:
			// ...
			plan = naivePlan(vehicle, tasks);
			break;
		case BFS:
			plan = bfsPlan(vehicle, tasks);
			break;
		default:
			throw new AssertionError("Should not happen.");
		}		
		return plan;
	}
	
	private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
		City current = vehicle.getCurrentCity();
		Plan plan = new Plan(current);

		for (Task task : tasks) {
			// move: current city => pickup location
			for (City city : current.pathTo(task.pickupCity))
				plan.appendMove(city);

			plan.appendPickup(task);

			// move: pickup location => delivery location
			for (City city : task.path())
				plan.appendMove(city);

			plan.appendDelivery(task);

			// set current city
			current = task.deliveryCity;
		}
		return plan;
	}
	
	private Plan bfsPlan(Vehicle vehicle, TaskSet tasks)
	{
		int costPerKm = vehicle.costPerKm();
		
		City vehicleStartCity = vehicle.getCurrentCity();
		Plan plan = new Plan(vehicleStartCity);
		
		LinkedList<BFSNode> queue = new LinkedList<>();
		State initialState = new State(vehicleStartCity, tasks);
		BFSNode root = new BFSNode(initialState, null, 0);
		queue.add(root);
		
		HashSet<State> visitedStates = new HashSet<>();
		visitedStates.add(initialState);
//		HashSet<BFSNode> visitedNodes = new HashSet<>();
//		visitedNodes.add(root);
		
		PriorityQueue<BFSNode> goalNodes = new PriorityQueue<>();
		
		while(!queue.isEmpty())
		{
			
			System.out.println(visitedStates.size());
			
			BFSNode currentNode = queue.poll();
			State currentState = currentNode.getState();
			//System.out.println(currentState.toString());
			
			if(currentState.isGoalState())
				goalNodes.add(currentNode);
			
			City currentLocation = currentState.getLocation();
			TaskSet currentTasksToPickup = currentState.getTasksToPickup();
			TaskSet currentTasksToDeliver = currentState.getTasksToDeliver();
			
			State nextState;
			BFSNode childNode;
			
			// TODO: Check for possible optimizations in action choice
			
			// All possible MOVE actions
			for(City neighborCity: currentLocation.neighbors())
			{
				nextState = new State(neighborCity, currentTasksToPickup, currentTasksToDeliver);
				double updatedCost = currentNode.getgCost()
						+ costPerKm * currentLocation.distanceTo(neighborCity);
				childNode = new BFSNode(nextState, currentNode, updatedCost);
				if(!visitedStates.contains(nextState))
				{
					queue.add(childNode);
					visitedStates.add(nextState);
				}
			}
			
			// All possible PICKUP actions
			for(Task t: currentTasksToPickup)
			{
				if(t.pickupCity.equals(currentLocation)
						&& t.weight + currentTasksToDeliver.weightSum() <= this.capacity)
				{
					TaskSet leftTasksToPickup = TaskSet.copyOf(currentTasksToPickup);
					leftTasksToPickup.remove(t);
					TaskSet newTasksToDeliver = TaskSet.copyOf(currentTasksToDeliver);
					newTasksToDeliver.add(t);
					nextState = new State(currentLocation, leftTasksToPickup, newTasksToDeliver);
					double updatedCost = currentNode.getgCost();
					childNode = new BFSNode(nextState, currentNode, updatedCost);
					if(!visitedStates.contains(nextState))
					{
						queue.add(childNode);
						visitedStates.add(nextState);
					}
				}
			}
			
			
			// All possible DELIVER actions
			for(Task t: currentTasksToDeliver)
			{
				if(t.deliveryCity.equals(currentLocation))
				{
					TaskSet leftTasksToDeliver = TaskSet.copyOf(currentTasksToDeliver);
					leftTasksToDeliver.remove(t);
					nextState = new State(currentLocation, currentTasksToPickup, leftTasksToDeliver);
					double updatedCost = currentNode.getgCost() - t.reward;
					childNode = new BFSNode(nextState, currentNode, updatedCost);
					if(!visitedStates.contains(nextState))
					{
						queue.add(childNode);
						visitedStates.add(nextState);
					}
				}
			}
			
		}
		
		goalNodes.peek().inferPlan(plan);
		
		return plan;

	}

	@Override
	public void planCancelled(TaskSet carriedTasks) {
		
		if (!carriedTasks.isEmpty()) {
			// This cannot happen for this simple agent, but typically
			// you will need to consider the carriedTasks when the next
			// plan is computed.
		}
	}
}
