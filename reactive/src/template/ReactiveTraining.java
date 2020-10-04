package template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

public class ReactiveTraining {
	
	private Topology topology;
	private TaskDistribution taskDistribution;
	private int costPerKm;
	
	private List<City> cities;
	private ArrayList<State> stateSet;
	private int[] actionSet;
	private HashMap<RewardTableKey, Double> rewardsMap;
	private HashMap<TransitionProbabilityTableKey, Double> transitionProbabilityMap;
	
	//private double[][] rewardsMap;
	//private double[][][] transitionProbabilityMap;
	
	
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
	
	public void generateStateSpace() {
		this.stateSet = new ArrayList<State>();
		
		for (City location: this.cities) {
				for (City taskDestination: this.cities)
				{
					this.stateSet.add(new State(location, taskDestination));
				}
			this.stateSet.add(new State(location, null));
		}
	}
	
	public void generateActionSpace() {
		int numCities = this.cities.size();
		this.actionSet = new int[numCities+1];
		
		for(int i=0;i<numCities+1;i++)
			this.actionSet[i] = i;
	}
	
	public void generateRewardTable()
	{
		int numActions = this.actionSet.length;
		this.rewardsMap = new HashMap<RewardTableKey, Double>();
		
		for(State s: this.stateSet)
		{
			for(int action: this.actionSet)
			{
				RewardTableKey key = new RewardTableKey(s, action);
				
				City sLocation = s.getLocation(), sTaskDestination = s.getTaskDestination();
				
				// Profit from delivering a task
				if (action==numActions-1)
				{
					if(sTaskDestination != null)
						this.rewardsMap.put(key, this.taskDistribution.reward(sLocation, sTaskDestination)
								- this.costPerKm * sLocation.distanceTo(sTaskDestination));
				}
				// Cost from just moving
				else {
					City moveChoice = this.cities.get(action);
					if(sLocation.hasNeighbor(moveChoice))
						this.rewardsMap.put(key, - this.costPerKm * sLocation.distanceTo(moveChoice));
				}
			}
		}
	}
	
	public void generateTransitionTable() {
		int numActions = this.actionSet.length;
		this.transitionProbabilityMap = new HashMap<TransitionProbabilityTableKey, Double>();
		
		for(State s: this.stateSet)
		{
			for(int action: this.actionSet)
			{
				for(State sPrime: this.stateSet)
				{
					TransitionProbabilityTableKey key = new TransitionProbabilityTableKey(s, action, sPrime);
					
					City sLocation = s.getLocation(), sTaskDestination = s.getTaskDestination();
					City sPrimeLocation = sPrime.getLocation(), sPrimeTaskDestination = sPrime.getTaskDestination();
					
					if(action == numActions - 1) // Is it a PICKUP action?
					{
						if(sTaskDestination != null) // Is there a task to pickup?
						{
							// TODO: See if pathExists needed
							if(sPrimeLocation.equals(sTaskDestination))
								this.transitionProbabilityMap.put(key, this.taskDistribution.probability(sPrimeLocation, sPrimeTaskDestination));
						}
					}
					else { // It is a MOVE TO CITY C[i] action?
						City moveChoice = this.cities.get(action);
						if (sPrimeLocation.equals(moveChoice) && sLocation.hasNeighbor(moveChoice)) // Can you even move there?
							this.transitionProbabilityMap.put(key, this.taskDistribution.probability(sPrimeLocation, sPrimeTaskDestination));
					}
				}
			}
		}
	}
	
	public HashMap<State, Integer> trainMdpInfiniteHorizon(double discountFactor, double epsilon)
	{
		int numStates = this.stateSet.size(), numActions = this.actionSet.length;
		ArrayList<ArrayList<Double>> qTable = new ArrayList<>();
		ArrayList<Double> vVector = new ArrayList<>();
		ArrayList<Double> vVectorPrevious = new ArrayList<>(numStates);

		for(int i=0;i<numStates;i++)
		{
			qTable.add(new ArrayList<>());
			for(int j=0;j<numActions;j++)
				qTable.get(i).add(0.0);
			vVector.add(1.0);
			vVectorPrevious.add(1.0);
		}
		
		
		HashMap<State, Integer> policy = new HashMap<State, Integer>();
		int numIterations = 0;
		
		boolean converged;
		do {
			numIterations++;
			converged=true;
			
			for(int i=0;i<numStates;i++)
			{
				State s = this.stateSet.get(i);
				for(int j=0;j<numActions;j++)
				{
					int action = this.actionSet[j];
					
					RewardTableKey rewardKey = new RewardTableKey(s, action);
					double reward = this.rewardsMap.containsKey(rewardKey) ? this.rewardsMap.get(rewardKey) : Double.NEGATIVE_INFINITY;
					qTable.get(i).set(j, reward);
					
					for(int k=0;k<numStates;k++)
					{
						State sPrime = this.stateSet.get(k);
						TransitionProbabilityTableKey tpKey = new TransitionProbabilityTableKey(s, action, sPrime);
						double transitionProb = this.transitionProbabilityMap.containsKey(tpKey) ? this.transitionProbabilityMap.get(tpKey) : 0;
						qTable.get(i).set(j, qTable.get(i).get(j) + discountFactor * transitionProb * vVector.get(k));
					}
					
				}
				
				for(int k=0;k<numStates;k++)
					vVector.set(k, Collections.max(qTable.get(k)));
			}
			
			for(int k=0;k<numStates;k++)
			{
				if(Math.abs(vVector.get(k) - vVectorPrevious.get(k)) > epsilon)
				{
					converged=false;
					break;
				}
			}
			Collections.copy(vVectorPrevious, vVector);
			
		} while(!converged);
		
		for(int i=0;i<numStates;i++)
		{
			State s = this.stateSet.get(i);
			double bestQ = vVector.get(i);
			int bestAction = qTable.get(i).indexOf(bestQ);
			policy.put(s, bestAction);
		}
		
		System.out.println("Converged in " + numIterations + " iterations");
		return policy;
	}

	public ArrayList<State> getStateSet() {
		return stateSet;
	}

	public void setStateSet(ArrayList<State> stateSet) {
		this.stateSet = stateSet;
	}

	public int[] getActionSet() {
		return actionSet;
	}

	public void setActionSet(int[] actionSet) {
		this.actionSet = actionSet;
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
