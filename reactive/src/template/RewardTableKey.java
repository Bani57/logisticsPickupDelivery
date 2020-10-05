package template;

/**
 * Class used in the reward table as key
 * 
 * @author Andrej Janchevski
 * @author Orazio Rillo
 */

public class RewardTableKey {
	private State state;
	private int action;
	
	public RewardTableKey(State state, int action) {
		super();
		this.state = state;
		this.action = action;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + action;
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
		RewardTableKey other = (RewardTableKey) obj;
		if (action != other.action)
			return false;
		if (state == null) {
			if (other.state != null)
				return false;
		} else if (!state.equals(other.state))
			return false;
		return true;
	}

	public State getState() {
		return state;
	}

	public void setState(State state) {
		this.state = state;
	}

	public int getAction() {
		return action;
	}

	public void setAction(int action) {
		this.action = action;
	}
	
	
	

}
