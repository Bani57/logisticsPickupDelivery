package template;

import java.util.Stack;

import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.plan.Action.Delivery;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskSet;

public class BFSNode implements Comparable<BFSNode> {
	
	private State state;
	private BFSNode parent;
	private double gCost;
	
	
	public BFSNode(State state, BFSNode parent, double gCost) {
		super();
		this.state = state;
		this.parent = parent;
		this.gCost = gCost;
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

	public void inferPlan(Plan plan)
	{
		Stack<Action> reversedPlan = new Stack<>();
		BFSNode tmp = this;
		
		while(tmp != null)
		{
			BFSNode tmpParent = tmp.getParent();
			if(tmpParent != null)
			{
				State tmpState = tmp.getState();
				State tmpParentState = tmpParent.getState();
				// MOVE 
				if(!tmpState.getLocation().equals(tmpParentState.getLocation()))
					reversedPlan.push(new Move(tmpState.getLocation()));
				else{
					TaskSet difference = TaskSet.intersectComplement(tmpParentState.getTasksToPickup(), tmpState.getTasksToPickup());
					// PICKUP
					if(!difference.isEmpty())
					{
						Task pickupTask = difference.iterator().next();
						reversedPlan.push(new Pickup(pickupTask));
					}
					// DELIVERY
					else {
						difference = TaskSet.intersectComplement(tmpParentState.getTasksToDeliver(), tmpState.getTasksToDeliver());
						Task deliverTask = difference.iterator().next();
						reversedPlan.push(new Delivery(deliverTask));
					}
				}
			}
			
			tmp = tmpParent;
		}
		
		while(!reversedPlan.empty())
			plan.append(reversedPlan.pop());
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
	
	
}
