package template;

import logist.task.Task;
import logist.task.TaskSet;

public class VehicleState {
	private int time;
	private TaskSet carriedTasks;
	private ActionRep previousAction;
	
	public VehicleState(int time, TaskSet carriedTasks, ActionRep previousAction) {
		super();
		this.time = time;
		this.carriedTasks = carriedTasks;
		this.previousAction = previousAction;
	}

	public int getTime() {
		return time;
	}

	public void setTime(int time) {
		this.time = time;
	}

	public TaskSet getCarriedTasks() {
		return carriedTasks;
	}

	public void addCarriedTask(Task task) {
		this.carriedTasks.add(task);
	}
	
	public void removeCarriedTask(Task task) {
		this.carriedTasks.remove(task);
	}

	public ActionRep getPreviousAction() {
		return previousAction;
	}

	public void setPreviousAction(ActionRep previousAction) {
		this.previousAction = previousAction;
	}
	
	
}
