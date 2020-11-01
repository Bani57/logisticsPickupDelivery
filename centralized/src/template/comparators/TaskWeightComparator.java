package template.comparators;

import java.util.Comparator;

import logist.task.Task;

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
