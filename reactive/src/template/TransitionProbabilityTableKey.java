package template;

public class TransitionProbabilityTableKey {
	
	private State currentState;
	private int action;
	private State nextState;
	
	public TransitionProbabilityTableKey(State currentState, int action, State nextState) {
		super();
		this.currentState = currentState;
		this.action = action;
		this.nextState = nextState;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + action;
		result = prime * result + ((currentState == null) ? 0 : currentState.hashCode());
		result = prime * result + ((nextState == null) ? 0 : nextState.hashCode());
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
		TransitionProbabilityTableKey other = (TransitionProbabilityTableKey) obj;
		if (action != other.action)
			return false;
		if (currentState == null) {
			if (other.currentState != null)
				return false;
		} else if (!currentState.equals(other.currentState))
			return false;
		if (nextState == null) {
			if (other.nextState != null)
				return false;
		} else if (!nextState.equals(other.nextState))
			return false;
		return true;
	}

	public State getCurrentState() {
		return currentState;
	}

	public void setCurrentState(State currentState) {
		this.currentState = currentState;
	}

	public int getAction() {
		return action;
	}

	public void setAction(int action) {
		this.action = action;
	}

	public State getNextState() {
		return nextState;
	}

	public void setNextState(State nextState) {
		this.nextState = nextState;
	}
	
	
}
