package template;

import java.util.Stack;

import logist.plan.Action;
import logist.plan.Plan;

public class AStarNode implements Comparable<AStarNode> {
	
	private State state;
	private AStarNode parent;
	private double gCost;
	private double hCost;
	private Action actionPerformed;
	
	public AStarNode(State state, AStarNode parent, double gCost, double hCost, Action actionPerformed) {
		super();
		this.state = state;
		this.parent = parent;
		this.gCost = gCost;
		this.hCost = hCost;
	}

	public State getState() {
		return state;
	}

	public void setState(State state) {
		this.state = state;
	}

	public AStarNode getParent() {
		return parent;
	}

	public void setParent(AStarNode parent) {
		this.parent = parent;
	}

	public double getgCost() {
		return gCost;
	}

	public void setgCost(double gCost) {
		this.gCost = gCost;
	}

	public double gethCost() {
		return hCost;
	}

	public void sethCost(double hCost) {
		this.hCost = hCost;
	}
	
	public Action getActionPerformed() {
		return actionPerformed;
	}

	public void setActionPerformed(Action actionPerformed) {
		this.actionPerformed = actionPerformed;
	}

	public double getfCost() {
		return this.gCost + this.hCost;
	}
	

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((parent == null) ? 0 : parent.hashCode());
		result = prime * result + ((state == null) ? 0 : state.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AStarNode other = (AStarNode) obj;
		if (parent == null) {
			if (other.parent != null)
				return false;
		} else if (!parent.equals(other.parent))
			return false;
		if (state == null) {
			if (other.state != null)
				return false;
		} else if (!state.equals(other.state))
			return false;
		return true;
	}

	@Override
	public int compareTo(AStarNode node) {
		double nodefCost = this.getfCost();
		double otherNodefCost = node.getfCost();
		if(nodefCost < otherNodefCost)
			return -1;
		else if (nodefCost > otherNodefCost)
			return 1;
		else return 0;
	}
	
	public void inferPlan(Plan plan)
	{
		Stack<Action> reversedPlan = new Stack<>();
		AStarNode tmp = this;
		
		while(tmp != null)
		{
			AStarNode tmpParent = tmp.getParent();
			if(tmpParent != null)
				reversedPlan.push(tmp.getActionPerformed());
			tmp = tmpParent;
		}
		
		while(!reversedPlan.empty())
			plan.append(reversedPlan.pop());
	}
}
