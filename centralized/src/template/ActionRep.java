package template;

import logist.task.Task;

public class ActionRep {
	
	enum ActionName {PICKUP, DELIVER};
	
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
	public String toString() {
		return "(t" + task.id + ", " + action.toString() + ")";
	}
	
	
	
	
}
