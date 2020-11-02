package template;

import logist.task.Task;

public class ActionRep {

	enum ActionName {
		PICKUP, DELIVER
	};

	private Task task;
	private ActionName action;

	
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
		} else if (!task.equals(other.task))
			return false;
		return true;
	}


	@Override
	public String toString() {
		return "(t" + task.id + ", " + action.toString() + ")";
	}
}
