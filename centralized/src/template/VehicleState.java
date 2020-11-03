package template;

import java.util.ArrayList;

import logist.task.Task;

/**
 * Class that represents the snapshot of a vehicle state in a specific point it
 * time Note that this class is used in the construction of the initial solution
 * 
 * @author Andrej Janchevski
 * @author Orazio Rillo
 */
public class VehicleState {

	private int time; // virtual time of the vehicle; if time = i then i-1 actions have already been
						// assigned to the vehicle until now
	private ArrayList<Task> carriedTasks; // currently carried tasks
	private ActionRep previousAction; // last action assigned to the vehicle

	public VehicleState(int time, ArrayList<Task> carriedTasks, ActionRep previousAction) {
		super();
		this.time = time;
		this.carriedTasks = carriedTasks;
		this.previousAction = previousAction;
	}

	/**
	 * Method that computes the sum of the weights of the tasks carried by the
	 * vehicle at time time
	 * 
	 * @return int sum of the weights
	 */
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

	public void setCarriedTasks(ArrayList<Task> carriedTasks) {
		this.carriedTasks = carriedTasks;
	}

	public ActionRep getPreviousAction() {
		return previousAction;
	}

	public void setPreviousAction(ActionRep previousAction) {
		this.previousAction = previousAction;
	}
}
