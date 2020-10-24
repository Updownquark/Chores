package org.quark.chores.entities;

import org.observe.util.Identified;
import org.observe.util.NamedEntity;

public interface PointResource extends Identified, NamedEntity {
	double getRate();
	PointResource setRate(double rate);

	String getUnit();
	PointResource setUnit(String unit);
}
