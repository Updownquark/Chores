package org.quark.chores.entities;

import org.observe.config.ParentReference;
import org.observe.util.ObjectMethodOverride;
import org.observe.util.ObjectMethodOverride.ObjectMethod;

public interface AssignedJob {
	@ParentReference
	Assignment getAssignment();
	Worker getWorker();
	Job getJob();

	int getCompletion();
	AssignedJob setCompletion(int completion);

	@ObjectMethodOverride(ObjectMethod.toString)
	default String print() {
		return getWorker() + "->" + getJob();
	}
}
