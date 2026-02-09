package org.quark.chores.entities;

import org.observe.assoc.ObservableMap;
import org.observe.collect.ObservableCollection;
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

	ObservableCollection<String> getLabels();

	ObservableMap<Job, Integer> getJobPreferences();

	SyncValueSet<PointHistory> getPointHistory();

	@ObjectMethodOverride(ObjectMethod.hashCode)
	default int hashCode0() {
		return Long.hashCode(getId());
	}
}
