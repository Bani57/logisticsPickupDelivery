package template;

/* import table */
import logist.simulation.Vehicle;

import java.awt.Desktop.Action;
import java.time.Duration;
import java.time.Instant;
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
 * 
 * @author Andrej Janchevski
 * @author Orazio Rillo
 */
@SuppressWarnings("unused")
public class DeliberativeAgent implements DeliberativeBehavior {

	enum Algorithm {
		BFS, ASTAR
	}

	/* Environment */
	Topology topology;
	TaskDistribution td;

	/* the properties of the agent */
	Agent agent;
	int capacity; /* Total weight capacity of the vehicle */
	int costPerKm; /* Cost per km when moving the vehicle */
	int heuristicId; /* Id number of the chosen heuristic function for the A* algorithm */
	TaskSet initCarriedTasks; /*
								 * Helper variable for storing the carried tasks of the vehicle after a plan is
								 * cancelled
								 */

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

		// The id number of the chosen heuristic function is set in the agent's tag in
		// agents.xml
		this.heuristicId = agent.readProperty("heuristic-id", Integer.class, 1);

	}

	@Override
	public Plan plan(Vehicle vehicle, TaskSet tasks) {
		Plan plan;

		// Compute the plan with the selected algorithm.
		switch (algorithm) {
		case ASTAR:
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

	/**
	 * Method that computes the plan using the BFS algorithm.
	 * 
	 * @return a Plan object.
	 */
	private Plan bfsPlan(Vehicle vehicle, TaskSet tasks) {

		// Start the execution timer
		Instant start = Instant.now();

		// Initialize an empty plan
		City vehicleStartCity = vehicle.getCurrentCity();
		Plan plan = new Plan(vehicleStartCity);

		// Instantiate the FCFS queue as a LinkedList
		LinkedList<BFSNode> queue = new LinkedList<>();

		// Compute the initial state
		State initialState;
		if (this.initCarriedTasks == null)
			initialState = new State(vehicleStartCity, tasks);
		else {
			// It can happen that the two sets don't match because of concurrency issues,
			// only trust the intersection
			TaskSet tasksLeftToDeliver = TaskSet.intersect(this.initCarriedTasks, vehicle.getCurrentTasks());
			initialState = new State(vehicleStartCity, tasks, tasksLeftToDeliver);
		}

		// Create the root node and enqueue it
		BFSNode root = new BFSNode(initialState, null, 0, null);
		queue.add(root);

		// Create a set for storing visited states in order to detect loops
		HashSet<State> visitedStates = new HashSet<>();
		visitedStates.add(initialState);

		// Create a priority queue (min heap) for storing all goal nodes => they will be
		// automatically ordered by g(n) as compareTo is implemented
		PriorityQueue<BFSNode> goalNodes = new PriorityQueue<>();

		while (!queue.isEmpty()) {

			// Check whether we have timed out on the 1 minute available for execution
			Instant end = Instant.now();
			Duration timeElapsed = Duration.between(start, end);
			if (timeElapsed.getSeconds() >= 60) {
				System.out.println("Timed out when building plan.");
				System.exit(1);
			}

			// Dequeue a node from the FCFS queue and get its state
			BFSNode currentNode = queue.poll();
			State currentState = currentNode.getState();

			// All goal nodes should be collected and sorted
			if (currentState.isGoalState()) {
				goalNodes.add(currentNode);
				continue;
			}

			City currentLocation = currentState.getLocation();
			TaskSet currentTasksToPickup = currentState.getTasksToPickup();
			TaskSet currentTasksToDeliver = currentState.getTasksToDeliver();

			State nextState;
			BFSNode childNode;

			// Enqueue all new unique states created by possible MOVE actions
			// Can only MOVE to a neighbor node
			for (City neighborCity : currentLocation.neighbors()) {
				// Compute the new state, new node and the new g(n)
				nextState = new State(neighborCity, currentTasksToPickup, currentTasksToDeliver);
				double updatedCost = currentNode.getgCost() + this.costPerKm * currentLocation.distanceTo(neighborCity);
				childNode = new BFSNode(nextState, currentNode, updatedCost, new Move(neighborCity));

				// Loop detection
				if (!visitedStates.contains(nextState)) {
					queue.add(childNode);
					visitedStates.add(nextState);
				}
			}

			// Enqueue all new unique states created by possible PICKUP actions
			// Can only PICKUP a task if currently located in its pickup city and the weight
			// capacity limit is not breached
			for (Task t : currentTasksToPickup) {
				if (t.pickupCity.equals(currentLocation)
						&& t.weight + currentTasksToDeliver.weightSum() <= this.capacity) {
					// Compute the new state, new node and the new g(n)
					TaskSet leftTasksToPickup = TaskSet.copyOf(currentTasksToPickup);
					leftTasksToPickup.remove(t);
					TaskSet newTasksToDeliver = TaskSet.copyOf(currentTasksToDeliver);
					newTasksToDeliver.add(t);
					nextState = new State(currentLocation, leftTasksToPickup, newTasksToDeliver);
					double updatedCost = currentNode.getgCost();
					childNode = new BFSNode(nextState, currentNode, updatedCost, new Pickup(t));

					// Loop detection
					if (!visitedStates.contains(nextState)) {
						queue.add(childNode);
						visitedStates.add(nextState);
					}
				}
			}

			// Enqueue all new unique states created by possible DELIVER actions
			// Can only DELIVER a task if currently located in its delivery city
			for (Task t : currentTasksToDeliver) {
				if (t.deliveryCity.equals(currentLocation)) {
					// Compute the new state, new node and the new g(n)
					TaskSet leftTasksToDeliver = TaskSet.copyOf(currentTasksToDeliver);
					leftTasksToDeliver.remove(t);
					nextState = new State(currentLocation, currentTasksToPickup, leftTasksToDeliver);
					double updatedCost = currentNode.getgCost() - t.reward;
					childNode = new BFSNode(nextState, currentNode, updatedCost, new Delivery(t));

					// Loop detection
					if (!visitedStates.contains(nextState)) {
						queue.add(childNode);
						visitedStates.add(nextState);
					}
				}
			}

		}

		System.out.println("Total number of states considered: " + visitedStates.size());

		// Infer the plan for the optimal goal node, located at the top of the priority
		// queue
		if (!goalNodes.isEmpty())
			goalNodes.peek().inferPlan(plan);

		return plan;

	}

	/**
	 * Function that computes the plan using the A* algorithm. Returns a Plan
	 * object.
	 */
	private Plan aStarPlan(Vehicle vehicle, TaskSet tasks) {

		// Start the execution timer
		Instant start = Instant.now();

		// Set the static heuristic id for all State objects
		State.setHeuristicId(this.heuristicId);

		// Initialize an empty plan
		City vehicleStartCity = vehicle.getCurrentCity();
		Plan plan = new Plan(vehicleStartCity);

		// Create a priority queue (min heap) for storing all nodes => they will be
		// automatically ordered by f(n) as compareTo is implemented
		PriorityQueue<AStarNode> queue = new PriorityQueue<>();

		// Compute the initial state
		State initialState;
		if (this.initCarriedTasks == null)
			initialState = new State(vehicleStartCity, tasks);
		else {
			// It can happen that the two sets don't match because of concurrency issues,
			// only trust the intersection
			TaskSet tasksLeftToDeliver = TaskSet.intersect(this.initCarriedTasks, vehicle.getCurrentTasks());
			initialState = new State(vehicleStartCity, tasks, tasksLeftToDeliver);
		}

		// Create the root node and enqueue it
		AStarNode root = new AStarNode(initialState, null, 0, initialState.getHCost(costPerKm), null);
		queue.add(root);

		// Create a set for storing visited states in order to detect loops
		HashSet<State> visitedStates = new HashSet<>();
		visitedStates.add(initialState);

		while (!queue.isEmpty()) {

			// Check whether we have timed out on the 1 minute available for execution
			Instant end = Instant.now();
			Duration timeElapsed = Duration.between(start, end);
			if (timeElapsed.getSeconds() >= 60) {
				System.out.println("Timed out when building plan.");
				System.exit(1);
			}

			// Dequeue the current optimal node and get its state
			AStarNode currentNode = queue.poll();
			State currentState = currentNode.getState();

			// If the current optimal node is a goal node, infer the plan from it and return
			if (currentState.isGoalState()) {
				System.out.println("Total number of states considered: " + visitedStates.size());
				currentNode.inferPlan(plan);
				return plan;
			}

			City currentLocation = currentState.getLocation();
			TaskSet currentTasksToPickup = currentState.getTasksToPickup();
			TaskSet currentTasksToDeliver = currentState.getTasksToDeliver();

			State nextState;
			AStarNode childNode;

			// Enqueue all new unique states created by possible MOVE actions
			// Can only MOVE to a neighbor node
			for (City neighborCity : currentLocation.neighbors()) {

				// Compute the new state, new node and the new g(n) and h(n)
				nextState = new State(neighborCity, currentTasksToPickup, currentTasksToDeliver);
				double updatedCost = currentNode.getgCost() + this.costPerKm * currentLocation.distanceTo(neighborCity);
				childNode = new AStarNode(nextState, currentNode, updatedCost, nextState.getHCost(costPerKm),
						new Move(neighborCity));

				// Loop detection
				if (!visitedStates.contains(nextState)) {
					queue.add(childNode);
					visitedStates.add(nextState);
				} else {
					for (AStarNode n: queue) {
						if (n.getState().equals(nextState) && childNode.getfCost() < n.getfCost()) {
							queue.remove(n);
							queue.add(childNode);
							break;
						}
					}
				}
			}

			// Enqueue all new unique states created by possible PICKUP actions
			// Can only PICKUP a task if currently located in its pickup city and the weight
			// capacity limit is not breached
			for (Task t : currentTasksToPickup) {
				if (t.pickupCity.equals(currentLocation)
						&& t.weight + currentTasksToDeliver.weightSum() <= this.capacity) {
					// Compute the new state, new node and the new g(n) and h(n)
					TaskSet leftTasksToPickup = TaskSet.copyOf(currentTasksToPickup);
					leftTasksToPickup.remove(t);
					TaskSet newTasksToDeliver = TaskSet.copyOf(currentTasksToDeliver);
					newTasksToDeliver.add(t);
					nextState = new State(currentLocation, leftTasksToPickup, newTasksToDeliver);
					double updatedCost = currentNode.getgCost();
					childNode = new AStarNode(nextState, currentNode, updatedCost, nextState.getHCost(costPerKm),
							new Pickup(t));

					// Loop detection
					if (!visitedStates.contains(nextState)) {
						queue.add(childNode);
						visitedStates.add(nextState);
					} else {
						for (AStarNode n: queue) {
							if (n.getState().equals(nextState) && childNode.getfCost() < n.getfCost()) {
								queue.remove(n);
								queue.add(childNode);
								break;
							}
						}
					}
				}
			}

			// Enqueue all new unique states created by possible DELIVER actions
			// Can only DELIVER a task if currently located in its delivery city
			for (Task t : currentTasksToDeliver) {
				if (t.deliveryCity.equals(currentLocation)) {
					// Compute the new state, new node and the new g(n) and h(n)
					TaskSet leftTasksToDeliver = TaskSet.copyOf(currentTasksToDeliver);
					leftTasksToDeliver.remove(t);
					nextState = new State(currentLocation, currentTasksToPickup, leftTasksToDeliver);
					double updatedCost = currentNode.getgCost() - t.reward;
					childNode = new AStarNode(nextState, currentNode, updatedCost, nextState.getHCost(costPerKm),
							new Delivery(t));

					// Loop detection
					if (!visitedStates.contains(nextState)) {
						queue.add(childNode);
						visitedStates.add(nextState);
					} else {
						for (AStarNode n: queue) {
							if (n.getState().equals(nextState) && childNode.getfCost() < n.getfCost()) {
								queue.remove(n);
								queue.add(childNode);
								break;
							}
						}
					}
				}
			}
		}

		return plan;

	}

	@Override
	public void planCancelled(TaskSet carriedTasks) {

		if (!carriedTasks.isEmpty()) {

			// It is necessary to remember the remaining undelivered tasks, needed to build
			// the initial state for the new plan
			this.initCarriedTasks = carriedTasks;
		}
	}
}
