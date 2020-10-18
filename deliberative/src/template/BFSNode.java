package template;

import java.util.Stack;

import logist.plan.Action;
import logist.plan.Plan;

public class BFSNode implements Comparable<BFSNode> {
	
	private State state;
	private BFSNode parent;
	private double gCost;
	private Action actionPerformed;
	
	
	public BFSNode(State state, BFSNode parent, double gCost, Action actionPerformed) {
		super();
		this.state = state;
		this.parent = parent;
		this.gCost = gCost;
		this.actionPerformed = actionPerformed;
	}

	public State getState() {
		return state;
	}

	public void setState(State state) {
		this.state = state;
	}

	public BFSNode getParent() {
		return parent;
	}

	public void setParent(BFSNode parent) {
		this.parent = parent;
	}

	public double getgCost() {
		return gCost;
	}

	public void setgCost(double gCost) {
		this.gCost = gCost;
	}
	
	public Action getActionPerformed() {
		return actionPerformed;
	}

	public void setActionPerformed(Action actionPerformed) {
		this.actionPerformed = actionPerformed;
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
		BFSNode other = (BFSNode) obj;
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
	public int compareTo(BFSNode node) {
		double otherNodegCost = node.getgCost();
		if(this.gCost < otherNodegCost)
			return -1;
		else if (this.gCost > otherNodegCost)
			return 1;
		else return 0;
	}
	
	public void inferPlan(Plan plan)
	{
		Stack<Action> reversedPlan = new Stack<>();
		BFSNode tmp = this;
		
		while(tmp != null)
		{
			BFSNode tmpParent = tmp.getParent();
			if(tmpParent != null)
				reversedPlan.push(tmp.getActionPerformed());
			tmp = tmpParent;
		}
		
		while(!reversedPlan.empty())
			plan.append(reversedPlan.pop());
	}
	
	
}
