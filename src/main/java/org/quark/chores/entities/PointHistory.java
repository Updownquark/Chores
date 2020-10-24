package org.quark.chores.entities;

import java.time.Instant;

import org.observe.config.ParentReference;

public interface PointHistory {
	public enum PointChangeType {
		Expectations, Job, Redemption, Cap
	}

	@ParentReference
	Worker getWorker();

	Instant getTime();

	PointChangeType getChangeType();

	long getChangeSourceId();

	String getChangeSourceName();
	PointHistory setChangeSourceName(String changeSourceName);

	double getQuantity();

	long getBeforePoints();

	int getPointChange();
}
