package org.quark.chores.entities;

import org.observe.collect.ObservableSet;
import org.observe.config.SyncValueSet;
import org.observe.util.Identified;
import org.observe.util.NamedEntity;
import org.observe.util.ObjectMethodOverride;
import org.observe.util.ObjectMethodOverride.ObjectMethod;

public interface Worker extends Identified, NamedEntity {
	int getAbility();
	Worker setAbility(int ability);

	long getExcessPoints();
	Worker setExcessPoints(long excessPoints);

	int getLevel();
	Worker setLevel(int level);

	ObservableSet<String> getLabels();

	// ObservableMap<Job, Integer> getJobPreferences();

	SyncValueSet<PointHistory> getPointHistory();

	@ObjectMethodOverride(ObjectMethod.hashCode)
	default int hashCode0() {
		return Long.hashCode(getId());
	}
}
