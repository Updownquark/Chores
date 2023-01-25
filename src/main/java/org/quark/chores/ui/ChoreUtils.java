package org.quark.chores.ui;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

import org.observe.util.swing.ComponentDecorator;
import org.qommons.TimeUtils;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;
import org.quark.chores.entities.Job;
import org.quark.chores.entities.Worker;

import com.google.common.reflect.TypeToken;

public class ChoreUtils {
	public static SpinnerFormat<List<String>> LABEL_SET_FORMAT = new SpinnerFormat.ListFormat<>(SpinnerFormat.NUMERICAL_TEXT, ",", " ");
	public static final TypeToken<List<String>> LABEL_SET_TYPE = new TypeToken<List<String>>() {
	};
	public static final Format<Instant> DATE_FORMAT = SpinnerFormat.flexDate(Instant::now, "EEE MMM d, yyyy",
			opts -> opts.withMaxResolution(TimeUtils.DateElementType.Minute));
	public static final int DEFAULT_PREFERENCE = 5;

	public static final void decoratePotentialJobCell(Job job, ComponentDecorator deco, Worker worker) {
		if (job == null) {
			return;
		}
		Instant lastDone = job.getLastDone();
		Instant due = lastDone == null ? null : lastDone.plus(job.getFrequency());
		if (!job.isActive()) {
			deco.withForeground(Color.gray);
		} else if (due != null && due.compareTo(Instant.now()) > 0) {
			deco.withForeground(Color.gray);
		} else {
			deco.withForeground(Color.black);
		}
	}

	public static final String getPotentialJobTooltip(Job job, Worker worker) {
		String tt = "<html>";
		Instant lastDone = job.getLastDone();
		if (lastDone == null) {
			tt += "Never done";
		} else {
			Instant due = lastDone.plus(job.getFrequency());
			tt += "Due " + ChoreUtils.DATE_FORMAT.format(due);
		}
		if (!job.isActive()) {
			tt += "<br>" + job.getName() + " is marked inactive";
		}
		return tt;
	};
}
