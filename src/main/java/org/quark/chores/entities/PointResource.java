package org.quark.chores.entities;

import org.observe.util.Identified;
import org.observe.util.NamedEntity;
import org.observe.util.ObjectMethodOverride;
import org.observe.util.ObjectMethodOverride.ObjectMethod;

public interface PointResource extends Identified, NamedEntity {
	double getRate();
	PointResource setRate(double rate);

	String getUnit();
	PointResource setUnit(String unit);

	@ObjectMethodOverride(ObjectMethod.hashCode)
	default int hashCode0() {
		return Long.hashCode(getId());
	}
}
