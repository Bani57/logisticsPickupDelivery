package template;

/* import table */
import logist.simulation.Vehicle;

import java.awt.Desktop.Action;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Stack;

import logist.agent.Agent;
import logist.behavior.DeliberativeBehavior;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.plan.Action.Delivery;
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
	int costPerKm;
	TaskSet initCarriedTasks;

	/* the planning class */
	Algorithm algorithm;
	
	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
		this.topology = topology;
		this.td = td;
		this.agent = agent;
		
		// initialize the planner
		this.capacity = agent.vehicles().get(0).capacity();
		this.costPerKm = agent.vehicles().get(0).costPerKm();
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
			plan = aStarPlan(vehicle, tasks);
			break;
		case BFS:
			plan = bfsPlan(vehicle, tasks);
			break;
		default:
			throw new AssertionError("Should not happen.");
		}		
		return plan;
	}

	
	private Plan bfsPlan(Vehicle vehicle, TaskSet tasks)
	{		
		City vehicleStartCity = vehicle.getCurrentCity();
		Plan plan = new Plan(vehicleStartCity);
		
		LinkedList<BFSNode> queue = new LinkedList<>();
		
		State initialState;
		if (this.initCarriedTasks == null)
			initialState = new State(vehicleStartCity, tasks);
		else 
			initialState = new State(vehicleStartCity, tasks, this.initCarriedTasks);
		BFSNode root = new BFSNode(initialState, null, 0, null);
		
		queue.add(root);
		
		HashSet<State> visitedStates = new HashSet<>();
		visitedStates.add(initialState);
		
		PriorityQueue<BFSNode> goalNodes = new PriorityQueue<>();
				
		while(!queue.isEmpty())
		{
						
			BFSNode currentNode = queue.poll();
			State currentState = currentNode.getState();
			
			if(currentState.isGoalState())
				goalNodes.add(currentNode);
			
			City currentLocation = currentState.getLocation();
			TaskSet currentTasksToPickup = currentState.getTasksToPickup();
			TaskSet currentTasksToDeliver = currentState.getTasksToDeliver();
			
			State nextState;
			BFSNode childNode;
						
			// All possible MOVE actions
			for(City neighborCity: currentLocation.neighbors())
			{
				nextState = new State(neighborCity, currentTasksToPickup, currentTasksToDeliver);
				double updatedCost = currentNode.getgCost()
						+ this.costPerKm * currentLocation.distanceTo(neighborCity);
				childNode = new BFSNode(nextState, currentNode, updatedCost, new Move(neighborCity));
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
					childNode = new BFSNode(nextState, currentNode, updatedCost, new Pickup(t));
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
					childNode = new BFSNode(nextState, currentNode, updatedCost, new Delivery(t));
					if(!visitedStates.contains(nextState))
					{
						queue.add(childNode);
						visitedStates.add(nextState);
					}
				}
			}
			
		}
		
		System.out.println("Total number of states considered: " + visitedStates.size());
		
		if (!goalNodes.isEmpty())
			goalNodes.peek().inferPlan(plan);
		
		return plan;

	}
	
	private Plan aStarPlan(Vehicle vehicle, TaskSet tasks)
	{				
		City vehicleStartCity = vehicle.getCurrentCity();
		Plan plan = new Plan(vehicleStartCity);
		
		PriorityQueue<AStarNode> queue = new PriorityQueue<>();
		
		State initialState;
		if (this.initCarriedTasks == null)
			initialState = new State(vehicleStartCity, tasks);
		else 
			initialState = new State(vehicleStartCity, tasks, this.initCarriedTasks);
		
		AStarNode root = new AStarNode(initialState, null, 0, initialState.getHCost(costPerKm), null);
		queue.add(root);
		
		HashSet<State> visitedStates = new HashSet<>();
		visitedStates.add(initialState);
				
		while(!queue.isEmpty())
		{
						
			AStarNode currentNode = queue.poll();
			State currentState = currentNode.getState();
			
			if(currentState.isGoalState())
			{ 
				System.out.println("Total number of states considered: " + visitedStates.size());
				currentNode.inferPlan(plan);
				
				int counter = 0;
				for (logist.plan.Action a : plan) {
					if (a != null)
						System.out.println(counter + " --> " + a.toString());
					else 
						System.out.println(counter + " --> null");
					counter++;
				}
				
				return plan;
			}
			
			City currentLocation = currentState.getLocation();
			TaskSet currentTasksToPickup = currentState.getTasksToPickup();
			TaskSet currentTasksToDeliver = currentState.getTasksToDeliver();
			
			State nextState;
			AStarNode childNode;
						
			// All possible MOVE actions
			for(City neighborCity: currentLocation.neighbors())
			{
				nextState = new State(neighborCity, currentTasksToPickup, currentTasksToDeliver);
				double updatedCost = currentNode.getgCost()
						+ this.costPerKm * currentLocation.distanceTo(neighborCity);
				childNode = new AStarNode(nextState, currentNode, updatedCost, nextState.getHCost(costPerKm), new Move(neighborCity));
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
					childNode = new AStarNode(nextState, currentNode, updatedCost, nextState.getHCost(costPerKm), new Pickup(t));
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
					childNode = new AStarNode(nextState, currentNode, updatedCost, nextState.getHCost(costPerKm), new Delivery(t));
					if(!visitedStates.contains(nextState))
					{
						queue.add(childNode);
						visitedStates.add(nextState);
					}
				}
			}
		}
		
		return plan;

	}

	@Override
	public void planCancelled(TaskSet carriedTasks) {
		
		if (!carriedTasks.isEmpty()) {
			// This cannot happen for this simple agent, but typically
			// you will need to consider the carriedTasks when the next
			// plan is computed.
			this.initCarriedTasks = carriedTasks;
		}
	}
}
