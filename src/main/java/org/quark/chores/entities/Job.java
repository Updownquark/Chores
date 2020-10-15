package org.quark.chores.entities;

import java.time.Duration;
import java.time.Instant;

import org.observe.collect.ObservableCollection;
import org.observe.util.Identified;
import org.observe.util.NamedEntity;

public interface Job extends Identified, NamedEntity {
	/**
	 * Renamed "Points" in the app
	 * 
	 * @return The number of points the job is worth
	 */
	int getDifficulty();
	Job setDifficulty(int difficulty);

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

	ObservableCollection<String> getInclusionLabels();
	ObservableCollection<String> getExclusionLabels();

	Instant getLastDone();
	Job setLastDone(Instant lastDone);
}
