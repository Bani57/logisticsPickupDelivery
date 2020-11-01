package template;

import java.util.ArrayList;

import logist.task.Task;

public class VehicleState {
	private int time;
	private ArrayList<Task> carriedTasks;
	private ActionRep previousAction;

	public VehicleState(int time, ArrayList<Task> carriedTasks, ActionRep previousAction) {
		super();
		this.time = time;
		this.carriedTasks = carriedTasks;
		this.previousAction = previousAction;
	}

	
	public int weightSum() {
		int weightSum = 0;
		for (Task t : carriedTasks)
			weightSum += t.weight;
		return weightSum;
	}

	
	public int getTime() {
		return time;
	}

	
	public void setTime(int time) {
		this.time = time;
	}

	
	public ArrayList<Task> getCarriedTasks() {
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
