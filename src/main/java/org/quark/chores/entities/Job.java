package org.quark.chores.entities;

import java.time.Duration;
import java.time.Instant;

import org.observe.collect.ObservableSet;
import org.observe.config.SyncValueSet;
import org.observe.util.Identified;
import org.observe.util.NamedEntity;
import org.observe.util.ObjectMethodOverride;
import org.observe.util.ObjectMethodOverride.ObjectMethod;
import org.qommons.io.BetterFile;
import org.qommons.io.FileUtils;

public interface Job extends Identified, NamedEntity {
	public static final BetterFile SCHEMA_HISTORY = FileUtils.getClassFile(Job.class).getParent().at("Schema History.xml");

	int getPoints();
	Job setPoints(int points);

	Duration getFrequency();
	Job setFrequency(Duration frequency);

	int getMinLevel();
	Job setMinLevel(int minLevel);
	int getMaxLevel();
	Job setMaxLevel(int maxLevel);

	int getPriority();
	Job setPriority(int priority);

	boolean isActive();
	Job setActive(boolean active);

	ObservableSet<String> getInclusionLabels();
	ObservableSet<String> getExclusionLabels();

	Instant getLastDone();
	Job setLastDone(Instant lastDone);

	SyncValueSet<JobHistory> getHistory();

	@ObjectMethodOverride(ObjectMethod.hashCode)
	default int hashCode0() {
		return Long.hashCode(getId());
	}
}
