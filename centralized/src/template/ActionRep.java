package template;

import logist.task.Task;

/**
 * Class that represents an action in the model
 * 
 * @author Andrej Janchevski
 * @author Orazio Rillo
 */
public class ActionRep {

	enum ActionName {
		PICKUP, DELIVER
	};

	private Task task; // task to pickup/deliver
	private ActionName action; // can be PICKUP or DELIVER

	public ActionRep(Task task, ActionName action) {
		super();
		this.task = task;
		this.action = action;
	}

	public Task getTask() {
		return task;
	}

	public void setTask(Task task) {
		this.task = task;
	}

	public ActionName getAction() {
		return action;
	}

	public void setAction(ActionName action) {
		this.action = action;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((action == null) ? 0 : action.hashCode());
		result = prime * result + ((task == null) ? 0 : task.hashCode());
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
		ActionRep other = (ActionRep) obj;
		if (action != other.action)
			return false;
		if (task == null) {
			if (other.task != null)
				return false;
		} else if (task.id != other.getTask().id)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(t" + task.id + ", " + action.toString() + ")";
	}
}