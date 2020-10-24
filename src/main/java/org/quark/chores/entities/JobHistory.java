package org.quark.chores.entities;

import java.time.Instant;

import org.observe.config.ParentReference;

public interface JobHistory {
	@ParentReference
	Job getJob();

	Instant getTime();

	String getWorkerName();

	long getWorkerId(); // Make this an ID so we don't have to delete it if a worker is deleted

	int getAmountComplete();

	boolean isCompleted();
}
