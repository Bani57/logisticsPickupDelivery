package template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * Class that generates a representation of the model in order to train the
 * agent and find the policy that optimizes the obtained reward in the
 * pickup-and-delivery problem on the current topology
 * 
 * @author Andrej Janchevski
 * @author Orazio Rillo
 */

public class ReactiveTraining {

	private Topology topology;
	private TaskDistribution taskDistribution;
	private int costPerKm; // cost to pay for each km the agent covers

	private List<City> cities;
	private ArrayList<State> states;
	private int[] actions;
	private HashMap<RewardTableKey, Double> rewardsMap;
	private HashMap<TransitionProbabilityTableKey, Double> transitionProbabilityMap;

	public ReactiveTraining(Topology topology, TaskDistribution taskDistribution, int costPerKm) {
		super();
		this.topology = topology;
		this.taskDistribution = taskDistribution;
		this.costPerKm = costPerKm;
		this.cities = this.topology.cities();

		generateStateSpace();
		generateActionSpace();
		generateRewardTable();
		generateTransitionTable();
	}

	/**
	 * Method that creates a new State for each possible combination of current city
	 * and destination city and then adds them to the list of states
	 */
	public void generateStateSpace() {
		this.states = new ArrayList<State>();

		for (City location : this.cities) {
			for (City taskDestination : this.cities) {
				this.states.add(new State(location, taskDestination));
			}
			this.states.add(new State(location, null));
		}
	}

	/**
	 * Method that add all the possible actions to the array of actions
	 */
	public void generateActionSpace() {
		int numCities = this.cities.size();
		this.actions = new int[numCities + 1];

		for (int i = 0; i < numCities + 1; i++)
			this.actions[i] = i;
	}

	/**
	 * Method that populates the reward table with the (expected) obtained reward
	 * for each legal state-action pair
	 */
	public void generateRewardTable() {
		int numActions = this.actions.length;
		this.rewardsMap = new HashMap<RewardTableKey, Double>();

		for (State s : this.states) {
			for (int action : this.actions) {
				RewardTableKey key = new RewardTableKey(s, action);

				City sLocation = s.getLocation(), sTaskDestination = s.getTaskDestination();

				// Checks whether the action is a pickup action...
				if (action == numActions - 1) {
					// in this case the profit is the difference between the reward obtained by
					// delivering the task and the covered distance multiplied by the specific cost
					// per km
					if (sTaskDestination != null)
						this.rewardsMap.put(key, this.taskDistribution.reward(sLocation, sTaskDestination)
								- this.costPerKm * sLocation.distanceTo(sTaskDestination));
				}
				// ...or a moveTo action
				else {
					// in this case the reward is negative and it is distance multiplied by the
					// specific cost per km
					City moveChoice = this.cities.get(action);
					if (sLocation.hasNeighbor(moveChoice))
						this.rewardsMap.put(key, -this.costPerKm * sLocation.distanceTo(moveChoice));
				}
			}
		}
	}

	/**
	 * Method that populates the transition probability table, the entry for the key
	 * (s,a,s') is the probability to reach the state s' starting from s and by
	 * choosing action a
	 */
	public void generateTransitionTable() {
		int numActions = this.actions.length;
		this.transitionProbabilityMap = new HashMap<TransitionProbabilityTableKey, Double>();

		for (State s : this.states) {
			for (int action : this.actions) {
				for (State sPrime : this.states) {
					TransitionProbabilityTableKey key = new TransitionProbabilityTableKey(s, action, sPrime);

					City sLocation = s.getLocation(), sTaskDestination = s.getTaskDestination();
					City sPrimeLocation = sPrime.getLocation(), sPrimeTaskDestination = sPrime.getTaskDestination();

					// TODO check again

					// Checks whether the action is a pickup action...
					if (action == numActions - 1) {
						// Checks that there is a task to pickup
						if (sTaskDestination != null) {
							// Checks that the current destination and s' location are the same city
							// TODO: See if pathExists needed
							if (sPrimeLocation.equals(sTaskDestination))
								// The corresponding table entry is the probability that some task from
								// s'.location to s'.destination exists
								this.transitionProbabilityMap.put(key,
										this.taskDistribution.probability(sPrimeLocation, sPrimeTaskDestination));
						}
					}
					// ...or a moveTo action
					else {
						City moveChoice = this.cities.get(action);
						// Checks that s'.location is exactly the destination of the moveTo action and
						// that this city is a neighbor of our current city
						if (sPrimeLocation.equals(moveChoice) && sLocation.hasNeighbor(moveChoice))
							// The corresponding table entry is the probability that some task from
							// s'.location to s'.destination exists
							this.transitionProbabilityMap.put(key,
									this.taskDistribution.probability(sPrimeLocation, sPrimeTaskDestination));
					}
				}
			}
		}
	}

	/**
	 * Method that trains the agent in order to find the optimal policy
	 * 
	 * @param discountFactor factor to discount future rewards
	 * @param epsilon        maximum value of the difference between two consecutive
	 *                       values of a state in order to establish that
	 *                       convergence is reached
	 * @return the optimal found policy
	 */
	public HashMap<State, Integer> trainMdpInfiniteHorizon(double discountFactor, double epsilon) {
		int numStates = this.states.size(), numActions = this.actions.length;
		ArrayList<ArrayList<Double>> qTable = new ArrayList<>(); // matrix of Q-values
		ArrayList<Double> vVector = new ArrayList<>(); // vector of V-values
		ArrayList<Double> vVectorPrevious = new ArrayList<>(numStates); // vector of V-values in the previous iteration
																		// of the training

		// Initializes V-values and Q-values
		for (int i = 0; i < numStates; i++) {
			vVector.add(1.0);
			vVectorPrevious.add(1.0);
			qTable.add(new ArrayList<>());
			for (int j = 0; j < numActions; j++)
				qTable.get(i).add(0.0);
		}

		HashMap<State, Integer> policy = new HashMap<State, Integer>();
		int numIterations = 0;

		boolean converged;
		do {
			numIterations++;
			converged = true;

			for (int i = 0; i < numStates; i++) {
				State s = this.states.get(i);
				for (int j = 0; j < numActions; j++) {
					int action = this.actions[j];

					// Gets the (expected) reward for the current state-action pair; if there is no
					// entry in the map, sets it to -inf
					RewardTableKey rewardKey = new RewardTableKey(s, action);
					double reward = this.rewardsMap.containsKey(rewardKey) ? this.rewardsMap.get(rewardKey)
							: Double.NEGATIVE_INFINITY;

					// Initializes the accumulator for the update of Q(s,a) with the reward of the
					// pair (s,a)
					qTable.get(i).set(j, reward);

					for (int k = 0; k < numStates; k++) {
						// Gets the transition probability for the tuple (s,a,s') and sets it to 0 if no
						// entry is found in the map
						State sPrime = this.states.get(k);
						TransitionProbabilityTableKey tpKey = new TransitionProbabilityTableKey(s, action, sPrime);
						double transitionProb = this.transitionProbabilityMap.containsKey(tpKey)
								? this.transitionProbabilityMap.get(tpKey)
								: 0;

						// Updates Q(s,a) with the discounted expected reward for each state s' one by
						// one
						qTable.get(i).set(j, qTable.get(i).get(j) + discountFactor * transitionProb * vVector.get(k));
					}

				}

				// Updates V-values
				for (int k = 0; k < numStates; k++)
					vVector.set(k, Collections.max(qTable.get(k)));
			}

			// Checks if convergence is reached
			for (int k = 0; k < numStates; k++) {
				if (Math.abs(vVector.get(k) - vVectorPrevious.get(k)) > epsilon) {
					converged = false;
					break;
				}
			}

			Collections.copy(vVectorPrevious, vVector);

		} while (!converged);

		// Eventually, the policy is made of the best Q-values we can get from the
		// Q-table for every state s
		for (int i = 0; i < numStates; i++) {
			State s = this.states.get(i);
			double bestQ = vVector.get(i);
			int bestAction = qTable.get(i).indexOf(bestQ);
			policy.put(s, bestAction);
		}

		System.out.println("Converged in " + numIterations + " iterations");
		return policy;
	}

	public ArrayList<State> getStates() {
		return states;
	}

	public void setStates(ArrayList<State> states) {
		this.states = states;
	}

	public int[] getActions() {
		return actions;
	}

	public void setActions(int[] actions) {
		this.actions = actions;
	}

	public HashMap<RewardTableKey, Double> getRewardsMap() {
		return rewardsMap;
	}

	public void setRewardsMap(HashMap<RewardTableKey, Double> rewardsMap) {
		this.rewardsMap = rewardsMap;
	}

	public HashMap<TransitionProbabilityTableKey, Double> getTransitionProbabilityMap() {
		return transitionProbabilityMap;
	}

	public void setTransitionProbabilityMap(HashMap<TransitionProbabilityTableKey, Double> transitionProbabilityMap) {
		this.transitionProbabilityMap = transitionProbabilityMap;
	}

}
