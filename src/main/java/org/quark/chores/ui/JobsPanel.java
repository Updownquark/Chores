package org.quark.chores.ui;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;

import javax.swing.JPanel;

import org.observe.Observable;
import org.observe.assoc.ObservableMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.StringUtils;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;
import org.quark.chores.entities.AssignedJob;
import org.quark.chores.entities.Job;
import org.quark.chores.entities.JobHistory;
import org.quark.chores.entities.Worker;

public class JobsPanel {
	private final ChoresUI theUI;

	public JobsPanel(ChoresUI ui) {
		theUI = ui;
	}

	public void addPanel(PanelPopulator<?, ?> panel) {
		ObservableMultiMap<Job, AssignedJob> assignedWorkers = ObservableCollection
				.flattenValue(theUI.getSelectedAssignment().map(a -> a == null ? null : a.getAssignments().getValues()))//
				.flow().groupBy(Job.class, AssignedJob::getJob, null).gather();
		// assignedWorkers.onChange(evt -> {
		// System.out.println(evt);
		// });
		ObservableCollection<Job> jobs = theUI.getJobs().getValues().flow().refresh(Observable.flatten(//
				theUI.getSelectedAssignment().value().map(a -> a == null ? null : a.getAssignments().getValues().simpleChanges())))
				.sorted((j1, j2) -> StringUtils.compareNumberTolerant(j1.getName(), j2.getName(), true, true))//
				.collect();
		panel.addSplit(true, split -> split.fill().fillV()//
				.withSplitProportion(theUI.getConfig().asValue(double.class).at("jobs-split")
						.withFormat(Format.doubleFormat("0.0#"), () -> .5).buildValue(null))//
				.firstV(top -> top.addTable(jobs, table -> {
					table.fill().fillV().dragSourceRow(null).dragAcceptRow(null)// Drag reordering
							.withNameColumn(Job::getName, Job::setName, true, col -> col.withWidths(50, 150, 250))//
							.withColumn("Points", int.class, Job::getDifficulty,
									col -> col.withWidths(25, 50, 100)
											.withMutation(mut -> mut.mutateAttribute(Job::setDifficulty).asText(SpinnerFormat.INT)))//
							.withColumn(
									"Assigned", TypeTokens.get().keyFor(ObservableCollection.class)
											.<ObservableCollection<AssignedJob>> parameterized(AssignedJob.class),
									job -> assignedWorkers.get(job), col -> {
										col.withHeaderTooltip("The worker this job is currently assigned to");
										col.formatText(assns -> StringUtils.conversational(", ", null)
												.print(assns, (str, assn) -> str.append(assn.getWorker().getName())).toString());
									})//
							.withColumn("Min Level", int.class, Job::getMinLevel,
									col -> col.withHeaderTooltip("The minimum level of worker that this job may be assigned to")//
											.withMutation(mut -> mut.mutateAttribute(Job::setMinLevel).asText(SpinnerFormat.INT)))//
							.withColumn("Max Level", int.class, Job::getMaxLevel,
									col -> col.withHeaderTooltip("The maximum level of worker that this job may be assigned to")//
											.withMutation(mut -> mut.mutateAttribute(Job::setMaxLevel).asText(SpinnerFormat.INT)))//
							.withColumn("Frequency", Duration.class, Job::getFrequency,
									col -> col.withHeaderTooltip("How often this job should be done").withMutation(
											mut -> mut.mutateAttribute(Job::setFrequency).asText(SpinnerFormat.flexDuration(true))))//
							.withColumn("Priority", int.class, Job::getPriority,
									col -> col.withHeaderTooltip("The priority this job should take over other jobs")//
											.withWidths(25, 50, 100)//
											.withMutation(mut -> mut.mutateAttribute(Job::setPriority).asText(SpinnerFormat.INT)))//
							.withColumn("Active", boolean.class, Job::isActive,
									col -> col.withHeaderTooltip("Whether this job is available for automatic assignment")//
											.withWidths(25, 50, 100)//
											.withMutation(mut -> mut.mutateAttribute(Job::setActive).asCheck()).withWidths(25, 60, 80))//
							.withColumn("Last Done", Instant.class, Job::getLastDone,
									col -> col.withHeaderTooltip("The data of the assignment during which this chore was last completed")//
											.withWidths(60, 120, 200).formatText(ChoreUtils.DATE_FORMAT::format)
											.withMutation(mut -> mut.mutateAttribute(Job::setLastDone).asText(//
													ChoreUtils.DATE_FORMAT)))//
							.withColumn("Inclusion Labels", ChoreUtils.LABEL_SET_TYPE, Job::getInclusionLabels,
									col -> col.withHeaderTooltip(
											"If given, a worker MUST be assigned one of these labels in order to be assigned the job")
											.formatText(ChoreUtils.LABEL_SET_FORMAT::format)
											.withMutation(mut -> mut.mutateAttribute((job, labels) -> {
												job.getInclusionLabels().retainAll(labels);
												job.getInclusionLabels().addAll(labels);
											}).filterAccept((jobEl, label) -> {
												if (jobEl.get().getInclusionLabels().contains(label)) {
													return label + " is already included";
												}
												return null;
											}).asText(ChoreUtils.LABEL_SET_FORMAT)))//
							.withColumn("Exclusion Labels", ChoreUtils.LABEL_SET_TYPE, Job::getExclusionLabels,
									col -> col.withHeaderTooltip(
											"If given, a worker CANNOT be assigned one of these labels in order to be assigned the job")
											.formatText(ChoreUtils.LABEL_SET_FORMAT::format)
											.withMutation(mut -> mut.mutateAttribute((job, labels) -> {
												job.getExclusionLabels().retainAll(labels);
												job.getExclusionLabels().addAll(labels);
											}).filterAccept((jobEl, label) -> {
												if (jobEl.get().getExclusionLabels().contains(label)) {
													return label + " is already excluded";
												}
												return null;
											}).asText(ChoreUtils.LABEL_SET_FORMAT)))//
							.withSelection(theUI.getSelectedJob(), false)//
							.withAdd(() -> {
								return theUI.getJobs().create()//
										.with(Job::getName,
												StringUtils.getNewItemName(theUI.getJobs().getValues(), Job::getName, "Job",
														StringUtils.PAREN_DUPLICATES))//
										.with(Job::isActive, true)//
										.with(Job::getPriority, 5)//
										.with(Job::getDifficulty, 1)//
										.with(Job::getMaxLevel, 100)//
										.create().get();
							}, action -> action.displayAsButton(true))//
							.withRemove(null, action -> action//
									.confirmForItems("Remove jobs?", "Permanently delete ", "?", true)//
									.displayAsButton(true).displayAsPopup(false)//
					);
				}))//
				.lastV(bottom -> bottom.addVPanel(editor -> {
					editor.fill().fillV().visibleWhen(theUI.getSelectedJob().map(j -> j != null));
					populateJobEditor(editor);
				})//
				));
	}

	private void populateJobEditor(PanelPopulator<JPanel, ?> editor) {
		ObservableCollection<JobHistory> history = ObservableCollection
				.flattenValue(theUI.getSelectedJob().map(j -> j == null ? null : j.getHistory().getValues().reverse()))//
				.flow().sorted((h1, h2) -> h2.getTime().compareTo(h1.getTime())).collect();
		editor.addTable(history, table -> {
			table.fill().fillV().decorate(deco -> deco.withTitledBorder("History", Color.black))//
					.withColumn("Time", Instant.class, JobHistory::getTime,
							col -> col.formatText(t -> ChoreUtils.DATE_FORMAT.format(t)).withWidths(80, 100, 200))//
					.withColumn("Worker", String.class, h -> {
						String currentName = null;
						for (Worker worker : theUI.getWorkers().getValues()) {
							if (worker.getId() == h.getWorkerId()) {
								currentName = worker.getName();
								break;
							}
						}
						if (currentName != null) {
							if (!currentName.equals(h.getWorkerName())) {
								h.setWorkerName(currentName);
							}
							return currentName;
						} else if (h.getWorkerName() == null) {
							return "";
						} else {
							return h.getWorkerName();
						}
					}, col -> col.withWidths(50, 150, 500))//
					.withColumn("Completed", int.class, JobHistory::getAmountComplete, null)//
					.withColumn("Available Points", String.class, h -> {
						int points = h.getPoints();
						if (points == 0) {
							return "?";
						}
						return "" + points;
					}, null)//
			;
		});
	}
}
