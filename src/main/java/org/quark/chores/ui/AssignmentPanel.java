package org.quark.chores.ui;

import java.awt.Color;
import java.awt.EventQueue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.ObservableCellRenderer;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.BiTuple;
import org.qommons.StringUtils;
import org.qommons.io.SpinnerFormat;
import org.quark.chores.entities.AssignedJob;
import org.quark.chores.entities.Assignment;
import org.quark.chores.entities.Job;
import org.quark.chores.entities.JobHistory;
import org.quark.chores.entities.PointHistory;
import org.quark.chores.entities.PointHistory.PointChangeType;
import org.quark.chores.entities.Worker;

public class AssignmentPanel {
	private final ChoresUI theUI;

	public AssignmentPanel(ChoresUI ui) {
		theUI = ui;
	}

	public void addPanel(PanelPopulator<?, ?> panel) {
		ObservableCollection<AssignedJob> jobs = ObservableCollection
				.flattenValue(theUI.getSelectedAssignment().map(assn -> assn == null ? null : assn.getAssignments().getValues()))//
				.flow()
				.refresh(Observable.or(theUI.getWorkers().getValues().simpleChanges(), //
						theUI.getJobs().getValues().simpleChanges()))//
				.sorted((assn1, assn2) -> {
					int index1 = theUI.getWorkers().getValues().indexOf(assn1.getWorker());
					int index2 = theUI.getWorkers().getValues().indexOf(assn2.getWorker());
					int comp = Integer.compare(index1, index2);
					if (comp == 0) {
						comp=StringUtils.compareNumberTolerant(assn1.getJob().getName(), assn2.getJob().getName(), true, true);
					}
					return comp;
				}).collect();
		panel.addLabel("Assignment Date:", theUI.getSelectedAssignment().transform(TypeTokens.get().of(Instant.class),
				tx -> tx.nullToNull(true).map(asn -> asn.getDate())), ChoreUtils.DATE_FORMAT, null);
		panel.addTable(jobs, table -> {
			table.fill().fillV()//
					.withItemName("Assignment")//
					.withColumn("Worker", String.class, assn -> assn.getWorker().getName(), null)//
					.withColumn("Job", String.class, assn -> assn.getJob().getName(), col -> {
						col.withWidths(50, 150, 250).decorate((cell, deco) -> {
							Color borderColor;
							if (cell.getModelValue().getCompletion() == 0) {
								borderColor = Color.red;
							} else if (cell.getModelValue().getCompletion() < cell.getModelValue().getJob().getDifficulty()) {
								borderColor = Color.yellow;
							} else {
								borderColor = Color.green;
							}
							deco.withLineBorder(borderColor, 2, false);
						});
					})//
					.withColumn("Points", int.class, entry -> entry.getJob().getDifficulty(),
							col -> col.withHeaderTooltip("The number of points the job is worth"))//
					.withColumn("Complete", int.class, entry -> entry.getCompletion(),
							col -> col.withHeaderTooltip("The amount of the job that is complete").withMutation(mut -> {
								mut.mutateAttribute((entry, complete) -> entry.setCompletion(complete))//
										.filterAccept((entry, completion) -> {
											if (completion < 0) {
												return "Completion cannot be negative";
												// If they do a great job or this doesn't get updated often, don't prevent more than the max
												// } else if (completion > entry.get().getJob().getDifficulty()) {
												// return "Max completion is the difficulty of the job ("
												// + entry.get().getJob().getDifficulty() + ")";
											} else {
												return null;
											}
										}).withRowUpdate(true).asText(SpinnerFormat.INT).clicks(1);
							}))//
					.withRemove(toRemove -> {
						theUI.getSelectedAssignment().get().getAssignments().getValues().removeAll(toRemove);
					}, removeAction -> removeAction//
							.confirmForItems("Delete Assignment(s)?", "Are you sure you want to delete ", "?", true)//
							.displayAsButton(true).displayAsPopup(false)//
			)//
			;
		});
		panel.addHPanel("Add Assignment:", "box", this::configureAddAssignmentPanel);
		panel.addHPanel(null, new JustifiedBoxLayout(false).mainCenter(), p -> {
			p.addButton("New Assignment", __ -> createNewAssignments(panel), btn -> btn.withTooltip("Creates a new set of assignments"));
			p.addButton("Clear", __ -> clearAssignments(panel),
					btn -> btn.withTooltip("Clears the current set of assignments with no consequences to the workers"));
		});
	}

	private void configureAddAssignmentPanel(PanelPopulator<?, ?> panel) {
		ObservableCollection<AssignedJob> allAssignments = ObservableCollection
				.flattenValue(theUI.getSelectedAssignment().map(assn -> assn == null ? null : assn.getAssignments().getValues()));
		SettableValue<Worker> selectedWorker = theUI.getSelectedWorker();
		ObservableCollection<Job> availableJobs = theUI.getJobs().getValues().flow().refresh(selectedWorker.noInitChanges())//
				.whereContained(allAssignments.flow().map(Job.class, AssignedJob::getJob), false)//
				.filter(job -> {
					Worker worker = selectedWorker.get();
					if (worker == null) {
						return "No selected worker";
					} else if (!AssignmentPanel.shouldDo(worker, job, 1_000_000)) {
						return "Illegal assignment";
					} else {
						return null;
					}
				}).collect();
		SettableValue<Job> addJob = SettableValue.build(Job.class).onEdt().build()//
				.disableWith(selectedWorker.map(w -> w == null ? "First, select the worker to assign the job to" : null))//
				.disableWith(theUI.getSelectedAssignment().map(a -> a == null ? "No assignment" : null));
		panel.addComboField(null, selectedWorker, theUI.getWorkers().getValues(),
				combo -> combo.renderAs(w -> w == null ? "Select Worker" : w.getName()));
		panel.addComboField(null, addJob, availableJobs,
				combo -> combo
						.renderWith(ObservableCellRenderer.<Job, Job> formatted(job -> job == null ? "Select New Job" : job.getName())//
								.decorate((cell, deco) -> ChoreUtils.decoratePotentialJobCell(cell.getModelValue(), deco,
										selectedWorker.get())))//
						.withValueTooltip(job -> ChoreUtils.getPotentialJobTooltip(job, selectedWorker.get())))//
		;
		addJob.noInitChanges().act(evt -> {
			if (evt.getNewValue() == null) {
				return;
			}
			theUI.getSelectedAssignment().get().getAssignments().create()//
					.with(AssignedJob::getWorker, selectedWorker.get())//
					.with(AssignedJob::getJob, evt.getNewValue())//
					.create();
			EventQueue.invokeLater(() -> addJob.set(null, null));
		});
	}

	private void createNewAssignments(PanelPopulator<?, ?> panel) {
		StringBuilder message = null;
		if (theUI.getSelectedAssignment().get() != null) {
			for (AssignedJob job : theUI.getSelectedAssignment().get().getAssignments().getValues()) {
				if (job.getCompletion() < job.getJob().getDifficulty()) {
					message = append(message, job.getWorker().getName() + ": " + job.getJob().getName() + " "
							+ ((int) Math.round(job.getCompletion() * 100.0 / job.getJob().getDifficulty())) + "% complete");
				}
			}
		}
		if (message != null) {
			message.append("\n\nIf a new assignment is created, the incomplete jobs will be counted against the workers.\n"
					+ "Are you sure you want to create a new assignment now?");
			if (!panel.alert("Some jobs have not been completed", message.toString()).confirm(true)) {
				return;
			}
		}
		Instant assignmentTime = theUI.getSelectedAssignment().get().getDate();
		Instant now = Instant.now();
		if (theUI.getSelectedAssignment().get() != null) {
			Map<Worker, Long> excessPoints = new IdentityHashMap<>();
			for (Worker worker : theUI.getWorkers().getValues()) {
				excessPoints.put(worker, worker.getExcessPoints());
			}
			for (AssignedJob job : theUI.getSelectedAssignment().get().getAssignments().getValues()) {
				excessPoints.compute(job.getWorker(), (worker, excess) -> {
					worker.getPointHistory().create()//
							.with(PointHistory::getWorker, worker)//
							.with(PointHistory::getTime, now)//
							.with(PointHistory::getChangeType, PointChangeType.Job)//
							.with(PointHistory::getQuantity, 1.0)//
							.with(PointHistory::getChangeSourceId, job.getJob().getId())//
							.with(PointHistory::getChangeSourceName, job.getJob().getName())//
							.with(PointHistory::getBeforePoints, excess)//
							.with(PointHistory::getPointChange, job.getCompletion())//
							.create();
					return excess + job.getCompletion();
				});
				if (job.getCompletion() >= job.getJob().getDifficulty()) {
					job.getJob().setLastDone(theUI.getSelectedAssignment().get().getDate());
					job.getJob().getHistory().create()//
							.with(JobHistory::getJob, job.getJob())//
							.with(JobHistory::getWorkerId, job.getWorker().getId())//
							.with(JobHistory::getWorkerName, job.getWorker().getName())//
							.with(JobHistory::getAmountComplete, job.getCompletion())//
							.with(JobHistory::getPoints, job.getJob().getDifficulty())//
							.with(JobHistory::getTime, assignmentTime)//
							.create();
				}
			}
			for (Map.Entry<Worker, Long> entry : excessPoints.entrySet()) {
				long newPoints = entry.getValue();
				entry.getKey().setExcessPoints(newPoints);
				entry.setValue(newPoints);
			}
		}
		theUI.getAssignments().getValues().clear(); // For the moment, let's not care about history

		// Now create the new Assignment
		Assignment newAssignment = theUI.getAssignments().create()//
				.with(Assignment::getDate, Instant.now())//
				.create().get();
		List<Job> allJobs = new ArrayList<>(theUI.getJobs().getValues());
		{// Remove jobs that shouldn't be done yet
			Iterator<Job> jobIter = allJobs.iterator();
			while (jobIter.hasNext()) {
				Job job = jobIter.next();
				if (!job.isActive()) {
					jobIter.remove();
				} else if (job.getLastDone() == null) {
					continue;
				} else if (job.getFrequency() == null
						|| job.getLastDone().plus(job.getFrequency()).compareTo(newAssignment.getDate()) > 0) {
					jobIter.remove();
				}
			}
		}
		if (allJobs.isEmpty()) {
			panel.alert("No Applicable Jobs", "None of the workers are available to do any jobs that need done").display();
			return;
		}
		Collections.sort(allJobs, (job1, job2) -> {
			if (job1.getLastDone() == null) {
				if (job2.getLastDone() == null) {
					return 0;
				}
				return 1;
			} else if (job2.getLastDone() == null) {
				return -1;
			}
			Instant todo1 = job1.getLastDone().plus(job1.getFrequency());
			Instant todo2 = job2.getLastDone().plus(job2.getFrequency());
			boolean needsDone1 = todo1.compareTo(newAssignment.getDate()) <= 0;
			boolean needsDone2 = todo2.compareTo(newAssignment.getDate()) <= 0;
			if (needsDone1) {
				if (!needsDone2) {
					return -1;
				}
			} else if (needsDone2) {
				return 1;
			}
			int comp = -Integer.compare(job1.getPriority(), job2.getPriority()); // Higher priority first
			if (comp == 0) {
				comp = todo1.compareTo(todo2);
			}
			if (comp == 0) {
				comp = job1.getFrequency().compareTo(job2.getFrequency());
			}
			return comp;
		});
		Map<Worker, BiTuple<Integer, Integer>> preferenceRanges = new IdentityHashMap<>();
		for (Worker worker : theUI.getWorkers().getValues()) {
			int minPref = worker.getJobPreferences().getOrDefault(allJobs.get(0), ChoreUtils.DEFAULT_PREFERENCE);
			int maxPref = minPref;
			for (Job job : allJobs) {
				int pref = worker.getJobPreferences().getOrDefault(job, ChoreUtils.DEFAULT_PREFERENCE);
				if (pref < minPref) {
					minPref = pref;
				} else if (pref > maxPref) {
					maxPref = pref;
				}
			}
			preferenceRanges.put(worker, new BiTuple<>(minPref, maxPref));
		}
		Map<Worker, Integer> workers = new IdentityHashMap<>();
		for (Worker worker : theUI.getWorkers().getValues()) {
			workers.put(worker, worker.getAbility());
		}
		List<Worker> jobWorkers = new ArrayList<>(theUI.getWorkers().getValues().size());
		for (Job job : allJobs) {
			// Assemble the workers that can do the job
			for (Worker worker : workers.keySet()) {
				if (shouldDo(worker, job, workers.get(worker))) {
					jobWorkers.add(worker);
				}
			}
			int workerIndex;
			if (jobWorkers.isEmpty()) {
				continue;
			} else if (jobWorkers.size() == 1) {
				workerIndex = 0;
			} else {
				int[] prefs = new int[jobWorkers.size()];
				int i = 0;
				// Scale the preferences in a scale of 1 to 10
				int totalPreference = 0;
				for (Worker worker : jobWorkers) {
					BiTuple<Integer, Integer> prefRange = preferenceRanges.get(worker);
					int pref = worker.getJobPreferences().getOrDefault(job, ChoreUtils.DEFAULT_PREFERENCE);
					if (prefRange.getValue1().equals(prefRange.getValue2())) {
						pref = 5;
					} else {
						pref = 1 + (int) ((pref - prefRange.getValue1()) * 1.0 / (prefRange.getValue2() - prefRange.getValue1()) * 10);
					}
					prefs[i++] = pref;
					totalPreference += pref;
				}
				int prefIndex = (int) (Math.random() * totalPreference);
				for (i = 0; i < jobWorkers.size(); i++) {
					prefIndex -= prefs[i];
					if (prefIndex < 0) {
						break;
					}
				}
				workerIndex = i;
			}
			Worker worker = jobWorkers.get(workerIndex);
			newAssignment.getAssignments().create()//
					.with(AssignedJob::getWorker, worker)//
					.with(AssignedJob::getJob, job)//
					.with(AssignedJob::getCompletion, 0)//
					.create();
			if (workers.compute(worker, (__, work) -> work - job.getDifficulty()) <= 0) {
				workers.remove(worker);
			}

			jobWorkers.clear();
		}
	}

	private static StringBuilder append(StringBuilder message, String err) {
		if (message == null) {
			message = new StringBuilder();
		} else {
			message.append("\n");
		}
		message.append(err);
		return message;
	}

	public static boolean shouldDo(Worker worker, Job job, int workLeft) {
		if (worker.getLevel() < job.getMinLevel() || worker.getLevel() > job.getMaxLevel()) {
			return false;
		}
		if (job.getDifficulty() > workLeft * 2) {
			return false;
		}
		for (String label : worker.getLabels()) {
			if (job.getExclusionLabels().contains(label)) {
				return false;
			}
		}
		if (!job.getInclusionLabels().isEmpty()) {
			boolean included = false;
			for (String label : worker.getLabels()) {
				included = job.getInclusionLabels().contains(label);
				if (included) {
					break;
				}
			}
			if (!included) {
				return false;
			}
		}
		return true;
	}

	private void clearAssignments(PanelPopulator<?, ?> panel) {
		StringBuilder message = null;
		if (theUI.getSelectedAssignment().get() != null) {
			for (AssignedJob job : theUI.getSelectedAssignment().get().getAssignments().getValues()) {
				if (job.getCompletion() > 0) {
					message = append(message, job.getWorker().getName() + ": " + job.getJob().getName() + " "
							+ ((int) Math.round(job.getCompletion() * 100.0 / job.getJob().getDifficulty())) + "% complete");
				}
			}
		}
		if (message != null) {
			message.append("\n\nIf the assignment is cleared, the workers' progress on them will not be counted.\n"
					+ "Are you sure you want to clear the assignment now?");
			if (!panel.alert("Some jobs have progress", message.toString()).confirm(true)) {
				return;
			}
		} else if (!panel.alert("Clear the Assignment?", "Are you sure you want to clear the assignment?").confirm(true)) {
			return;
		}
		theUI.getAssignments().getValues().clear();
	}
}
