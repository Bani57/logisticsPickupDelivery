package template.comparators;

import java.util.Comparator;

import logist.task.Task;

/**
 * Comparator for the class Task: the compare method takes into account the task
 * weight only
 * 
 * @author Andrej Janchevski
 * @author Orazio Rillo
 */
public class TaskWeightComparator implements Comparator<Task> {

	@Override
	public int compare(Task t1, Task t2) {
		if (t1.weight > t2.weight)
			return 1;
		else if (t1.weight < t2.weight)
			return -1;
		else
			return 0;
	}
};
