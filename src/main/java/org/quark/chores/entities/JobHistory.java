package org.quark.chores.entities;

import java.time.Instant;

import org.observe.config.ParentReference;

public interface JobHistory {
	@ParentReference
	Job getJob();

	Instant getTime();

	String getWorkerName();

	JobHistory setWorkerName(String workerName);

	long getWorkerId(); // Make this an ID so we don't have to delete it if a worker is deleted

	int getAmountComplete();

	int getPoints(); // The job point setting can change, so it's useful to know what it was when this event occurred

	boolean isCompleted();
}
