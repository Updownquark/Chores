package org.quark.chores.ui;

import java.awt.Color;
import java.awt.EventQueue;
import java.time.Duration;
import java.time.Instant;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.config.SyncValueSet;
import org.observe.util.TypeTokens;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.ObservableCellRenderer;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.TableBuilder;
import org.qommons.Nameable;
import org.qommons.StringUtils;
import org.qommons.TimeUtils;
import org.qommons.TimeUtils.DurationComponentType;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;
import org.quark.chores.entities.AssignedJob;
import org.quark.chores.entities.Assignment;
import org.quark.chores.entities.Job;
import org.quark.chores.entities.JobHistory;
import org.quark.chores.entities.PointHistory;
import org.quark.chores.entities.PointHistory.PointChangeType;
import org.quark.chores.entities.PointResource;
import org.quark.chores.entities.Worker;

public class WorkersPanel {
	private final ChoresUI theUI;

	public WorkersPanel(ChoresUI ui) {
		theUI = ui;
	}

	public void addPanel(PanelPopulator<?, ?> panel) {
		panel.addSplit(true,
				split -> split.fill().fillV()//
						.withSplitProportion(theUI.getConfig().asValue(double.class).at("workers-split")
								.withFormat(Format.doubleFormat("0.0#"), () -> .5).buildValue(null))//
						.firstV(top -> top.addTable(theUI.getWorkers().getValues(), table -> {
							table.fill().dragSourceRow(null).dragAcceptRow(null)// Re-orderable by drag
									.withNameColumn(Worker::getName, Worker::setName, true, null)//
									.withColumn("Level", int.class, Worker::getLevel,
											col -> col.withHeaderTooltip("Affects the type of jobs a worker may be assigned")//
													.withMutation(mut -> mut.mutateAttribute(Worker::setLevel).asText(SpinnerFormat.INT)))//
									.withColumn("Ability", int.class, Worker::getAbility,
											col -> col.withHeaderTooltip("The amount of work expected of the worker")
													.withMutation(mut -> mut.mutateAttribute(Worker::setAbility).asText(SpinnerFormat.INT)))//
									.withColumn("Labels", ChoreUtils.LABEL_SET_TYPE, Worker::getLabels, col -> {
										col.withHeaderTooltip("Labels that affect which jobs may apply to the worker")
												.formatText(ChoreUtils.LABEL_SET_FORMAT::format)
												.withMutation(mut -> mut.mutateAttribute((worker, labels) -> {
													worker.getLabels().retainAll(labels);
													worker.getLabels().addAll(labels);
												}).filterAccept((workerEl, label) -> {
													if (workerEl.get().getLabels().contains(label)) {
														return workerEl.get().getName() + " is already labeled " + label;
													}
													return null;
												}).asText(ChoreUtils.LABEL_SET_FORMAT));
									})//
									.withColumn("Excess Points", long.class, Worker::getExcessPoints, col -> {
										col.withHeaderTooltip("The number of points the worker has accumulated beyond expectations");
									})//
									.withSelection(theUI.getSelectedWorker(), false)//
									.withAdd(() -> {
										return theUI.getWorkers().create()//
												.with(Worker::getName,
														StringUtils.getNewItemName(theUI.getWorkers().getValues(), Worker::getName,
																"Worker", StringUtils.PAREN_DUPLICATES))//
												.with(Worker::getAbility, 100)//
												.create().get();
									}, null)//
									.withRemove(null,
											action -> action.confirmForItems("Remove workers?", "Permanently delete ", "?", true));
						}))//
						.lastV(bottom -> bottom.addVPanel(p -> populateWorkerEditor(p.fill().fillV()))));
	}

	void populateWorkerEditor(PanelPopulator<?, ?> bottom) {
		bottom.visibleWhen(theUI.getSelectedWorker().map(w -> w != null))
				.addTabs(tabs -> tabs.fill().fillV()//
						.withSelectedTab(theUI.getConfig().asValue(String.class).at("worker-editor/tab").buildValue(null))//
						.withVTab("preferences",
								prefsPanel -> prefsPanel.decorate(deco -> deco.withTitledBorder("Job Preferences", Color.black))//
										.addTable(theUI.getJobs().getValues().flow().refresh(theUI.getSelectedWorker().noInitChanges())
												.collect(), this::configurePreferenceTable),
								tab -> tab.setName("Preferences"))//
						.withVTab("assignments", this::configureAssignmentPanel, tab -> tab.setName("Assignments"))//
						.withVTab("points", this::configurePointsPanel, tab -> tab.setName("Points"))//
		);
	}

	void configurePreferenceTable(TableBuilder<Job, ?> prefTable) {
		prefTable.fill().fillV().withColumn("Job", String.class, Job::getName, col -> col.withWidths(50, 150, 250))//
				.withColumn("Preference", int.class, job -> {
					return theUI.getSelectedWorker().get().getJobPreferences().getOrDefault(job, ChoreUtils.DEFAULT_PREFERENCE);
				}, col -> col.withMutation(mut -> mut.mutateAttribute((job, pref) -> {
					theUI.getSelectedWorker().get().getJobPreferences().put(job, pref);
				}).asText(SpinnerFormat.validate(SpinnerFormat.INT, pref -> {
					if (pref < 0) {
						return "No negative preferences";
					} else if (pref > 10) {
						return "Preference must be between 0 and 10";
					}
					return null;
				})).clicks(1))//
		);
	}

	void configureAssignmentPanel(PanelPopulator<?, ?> panel) {
		ObservableCollection<AssignedJob> allAssignments = ObservableCollection
				.flattenValue(theUI.getSelectedAssignment().map(assn -> assn == null ? null : assn.getAssignments().getValues()));
		ObservableCollection<AssignedJob> assignments = allAssignments.flow().refresh(theUI.getSelectedWorker().noInitChanges())
				.filter(assn -> assn.getWorker() == theUI.getSelectedWorker().get() ? null : "Wrong worker").collect();
		Job totalJob=new Job(){
			@Override
			public String getName() {
				return "Total Difficulty";
			}

			@Override
			public Nameable setName(String name) {
				throw new IllegalStateException();
			}

			@Override
			public long getId() {
				throw new IllegalStateException();
			}

			@Override
			public int getDifficulty() {
				int total = 0;
				for (AssignedJob job : assignments) {
					total += job.getJob().getDifficulty();
				}
				return total;
			}

			@Override
			public Job setDifficulty(int difficulty) {
				throw new IllegalStateException();
			}

			@Override
			public Duration getFrequency() {
				return null;
			}

			@Override
			public Job setFrequency(Duration frequency) {
				throw new IllegalStateException();
			}

			@Override
			public int getMinLevel() {
				throw new IllegalStateException();
			}

			@Override
			public Job setMinLevel(int minLevel) {
				throw new IllegalStateException();
			}

			@Override
			public int getMaxLevel() {
				throw new IllegalStateException();
			}

			@Override
			public Job setMaxLevel(int maxLevel) {
				throw new IllegalStateException();
			}

			@Override
			public int getPriority() {
				throw new IllegalStateException();
			}

			@Override
			public Job setPriority(int priority) {
				throw new IllegalStateException();
			}

			@Override
			public boolean isActive() {
				throw new IllegalStateException();
			}

			@Override
			public Job setActive(boolean active) {
				throw new IllegalStateException();
			}

			@Override
			public ObservableCollection<String> getInclusionLabels() {
				return null;
			}

			@Override
			public ObservableCollection<String> getExclusionLabels() {
				return null;
			}

			@Override
			public Instant getLastDone() {
				return null;
			}

			@Override
			public Job setLastDone(Instant lastDone) {
				throw new IllegalStateException();
			}

			@Override
			public SyncValueSet<JobHistory> getHistory() {
				return null;
			}
		};
		AssignedJob totalAssignedJob = new AssignedJob() {
			@Override
			public Assignment getAssignment() {
				return null;
			}

			@Override
			public Worker getWorker() {
				return theUI.getSelectedWorker().get();
			}

			@Override
			public Job getJob() {
				return totalJob;
			}

			@Override
			public int getCompletion() {
				int total = 0;
				for (AssignedJob job : assignments) {
					total += job.getCompletion();
				}
				return total;
			}

			@Override
			public AssignedJob setCompletion(int completion) {
				throw new UnsupportedOperationException();
			}
		};
		ObservableValue<AssignedJob> totalJobValue = ObservableValue.of(TypeTokens.get().of(AssignedJob.class), () -> totalAssignedJob,
				assignments::getStamp, assignments.simpleChanges());
		ObservableValue<ObservableCollection<AssignedJob>> totalJobColl = totalJobValue
				.map(j -> ObservableCollection.of(AssignedJob.class, j));
		ObservableCollection<AssignedJob> assignmentsWithTotal = ObservableCollection
				.<AssignedJob> flattenCollections(TypeTokens.get().of(AssignedJob.class), assignments, //
						ObservableCollection.flattenValue(totalJobColl)//
				).collect();
		ObservableCollection<Job> availableJobs = theUI.getJobs().getValues().flow().refresh(theUI.getSelectedWorker().noInitChanges())//
				.whereContained(allAssignments.flow().map(Job.class, AssignedJob::getJob), false)//
				.filter(job -> {
					Worker worker = theUI.getSelectedWorker().get();
					if (worker == null) {
						return "No selected worker";
					} else if (!AssignmentPanel.shouldDo(worker, job, 1_000_000)) {
						return "Illegal assignment";
					} else {
						return null;
					}
				}).collect();
		SettableValue<Job> addJob = SettableValue.build(Job.class).onEdt().build()//
				.disableWith(theUI.getSelectedAssignment().map(a -> a == null ? "No assignment" : null));
		panel.decorate(deco -> deco.withTitledBorder("Current Assignments", Color.black))//
				.addTable(assignmentsWithTotal, this::configureAssignmentTable)//
				.addComboField("Add Assignment:", addJob, availableJobs, combo -> combo
						.renderWith(ObservableCellRenderer.<Job, Job> formatted(job -> job == null ? "Select New Job" : job.getName())//
								.decorate((cell, deco) -> ChoreUtils.decoratePotentialJobCell(cell.getModelValue(), deco,
										theUI.getSelectedWorker().get())))//
						.withValueTooltip(job -> ChoreUtils.getPotentialJobTooltip(job, theUI.getSelectedWorker().get())));
		addJob.noInitChanges().act(evt -> {
			if (evt.getNewValue() == null) {
				return;
			}
			theUI.getSelectedAssignment().get().getAssignments().create()//
					.with(AssignedJob::getWorker, theUI.getSelectedWorker().get())//
					.with(AssignedJob::getJob, evt.getNewValue())//
					.create();
			EventQueue.invokeLater(() -> addJob.set(null, null));
		});
	}

	void configureAssignmentTable(TableBuilder<AssignedJob, ?> table) {
		table.fill().fillV()//
				.withItemName("assignment").withNameColumn(assn -> assn.getJob().getName(), null, false, col -> {
					col.setName("Job").withWidths(50, 150, 250).decorate((cell, deco) -> {
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
				.withColumn("Difficulty", int.class, assn -> assn.getJob().getDifficulty(), null)//
				.withColumn("Complete", int.class, assn -> assn.getCompletion(), col -> col.withMutation(mut -> {
					mut.mutateAttribute((assn, complete) -> assn.setCompletion(complete))//
							.filterAccept((entry, completion) -> {
								if (entry.get().getAssignment() == null) {
									return "Total row cannot be modified";
								} else if (completion < 0) {
									return "Completion cannot be negative";
								} else if (completion > entry.get().getJob().getDifficulty()) {
									return "Max completion is the difficulty of the job (" + entry.get().getJob().getDifficulty() + ")";
								} else {
									return null;
								}
							}).withRowUpdate(true).asText(SpinnerFormat.INT).clicks(1);
				})//
				)//
				.withRemove(jobs -> {
					theUI.getSelectedAssignment().get().getAssignments().getValues().removeAll(jobs);
				}, removeAction -> {
					removeAction.confirmForItems("Delete Assignment(s)?", "Are you sure you want to delete ", "?", true);
				});

	}

	void configurePointsPanel(PanelPopulator<?, ?> panel) {
		SettableValue<PointResource> resource = theUI.getSelectedPointResource();
		ObservableValue<String> rate = resource.map(r -> {
			if (r == null) {
				return "";
			}
			StringBuilder str = new StringBuilder();
			if (r.getRate() > 0) {
				str.append(r.getRate()).append(' ');
				if (r.getUnit().length() > 0) {
					str.append(r.getUnit());
				}
				str.append("/point");
			} else {
				double rate2 = -r.getRate();
				str.append(rate2).append(' ').append(rate2 == 1 ? "point" : "points");
				if (r.getUnit().length() > 0) {
					str.append('/').append(r.getUnit());
				}
			}
			return str.toString();
		});
		SettableValue<Integer> points = SettableValue.build(int.class).onEdt().withValue(0).build();
		ObservableValue<String> redeemLabel = resource.map(r -> (r == null || r.getRate() > 0) ? "Redeem" : "Grant");
		Format<Double> dblFormat = Format.doubleFormat("0.###");
		ObservableValue<String> quantity = points.transform(String.class, tx -> tx.combineWith(resource).combine((p, res) -> {
			if (res == null) {
				return "";
			}
			double q = p * Math.abs(res.getRate());
			StringBuilder str = new StringBuilder();
			dblFormat.append(str, q);
			if (res.getUnit().length() > 0) {
				str.append(' ').append(res.getUnit());
			}
			return str.toString();
		}));
		ObservableValue<String> redemptionDisabled = resource.transform(String.class,
				tx -> tx.combineWith(theUI.getSelectedWorker()).combineWith(points).combine((res, worker, p) -> {
					if (res == null) {
						return "Select a resource to redeem points for";
					} else if (worker == null) {
						return "No selected worker";
					} else if (p == 0) {
						return "Select the number of points to redeem";
					} else if ((res.getRate() > 0) == (p > 0)) {
						if (worker.getExcessPoints() < p) {
							return worker.getName() + " only has " + worker.getExcessPoints() + " points available";
						}
					}
					return null;
				}));
		ObservableValue<String> redeemButtonName = resource.transform(tx -> tx.combineWith(points).combine((res, p) -> {
			if (res == null) {
				return "";
			} else if (res.getRate() > 0) {
				if (p > 0) {
					return "Redeem Points";
				} else {
					return "Refund Points";
				}
			} else {
				if (p > 0) {
					return "Add Points";
				} else {
					return "Return Points";
				}
			}
		}));
		panel.addHPanel("Redeem:", new JustifiedBoxLayout(false).mainLeading(), redeemPanel -> redeemPanel
				.addComboField(null, resource, theUI.getPointResources().getValues(), null)//
				.spacer(2).addLabel(null, redeemLabel, Format.TEXT, null)//
				.spacer(2)
				.addTextField(null, points, SpinnerFormat.INT, text -> text.modifyEditor(tf -> tf.withColumns(4).setCommitOnType(true)))//
				.addLabel(null, " points at ", null)//
				.addLabel(null, rate, Format.TEXT, lbl -> lbl.decorate(deco -> deco.bold()))//
				.addLabel(null, " for ", null)//
				.addLabel(null, quantity, Format.TEXT, null)//
				.addButton("Redeem", __ -> {
					Worker worker = theUI.getSelectedWorker().get();
					PointResource res = resource.get();
					worker.getPointHistory().create()//
							.with(PointHistory::getWorker, worker)//
							.with(PointHistory::getTime, Instant.now())//
							.with(PointHistory::getChangeType, PointChangeType.Redemption)//
							.with(PointHistory::getQuantity, points.get() * res.getRate())//
							.with(PointHistory::getChangeSourceId, res.getId())//
							.with(PointHistory::getChangeSourceName, res.getName())//
							.with(PointHistory::getBeforePoints, worker.getExcessPoints())//
							.with(PointHistory::getPointChange, -points.get())//
							.create();
					worker.setExcessPoints(worker.getExcessPoints() - points.get());
				}, btn -> btn.withText(redeemButtonName).disableWith(redemptionDisabled)));

		{
			SettableValue<Integer> amountDone = SettableValue.build(int.class).onEdt().withValue(1).build();
			SettableValue<Job> job = SettableValue.build(Job.class).onEdt().build();
			SettableValue<Instant> doneTime = SettableValue.build(Instant.class).onEdt().withValue(Instant.now()).build();
			ObservableValue<Integer> jobDifficulty = job.map(j -> j == null ? 0 : j.getDifficulty());
			TimeUtils.RelativeTimeFormat rtf = TimeUtils.relativeFormat()//
					.abbreviated(true, false)//
					.withMonthsAndYears()//
					.withJustNow("just now")//
					.withMaxElements(2)//
					.withAgo("ago")//
					.withMaxPrecision(DurationComponentType.Minute);
			ObservableValue<String> relativeDoneTime = doneTime.map(t -> rtf.printAsDuration(t, Instant.now()));
			ObservableCollection<Job> availableJobs = theUI.getJobs().getValues().flow().refresh(theUI.getSelectedWorker().noInitChanges())//
					.filter(j -> {
						Worker worker = theUI.getSelectedWorker().get();
						if (worker == null) {
							return "No selected worker";
						} else if (!AssignmentPanel.shouldDo(worker, j, 1_000_000)) {
							return "Illegal assignment";
						} else {
							return null;
						}
					}).collect();
			panel.addHPanel("Freelance:", new JustifiedBoxLayout(false).mainLeading(),
					freelancePanel -> freelancePanel
							.addTextField(null, amountDone, SpinnerFormat.INT, tf -> tf.modifyEditor(tf2 -> tf2.withColumns(3)))//
							.addLabel(null, "/", null)//
							.addLabel(null, jobDifficulty, Format.INT, null)//
							.addLabel(null, " of ", null)//
							.addComboField(null, job, availableJobs, null)//
							.addLabel(null, " done ", null)
							.addTextField(null, doneTime, ChoreUtils.DATE_FORMAT,
									tf -> tf.modifyEditor(tf2 -> tf2.withColumns(12).setReformatOnCommit(true)))//
							.addLabel(null, " (", null)//
							.addLabel(null, relativeDoneTime, Format.TEXT, null)//
							.addLabel(null, ")", null)//
							.addButton("Report Work", __ -> {
								Worker worker = theUI.getSelectedWorker().get();
								long oldPoints = worker.getExcessPoints();
								job.get().getHistory().create()//
										.with(JobHistory::getJob, job.get())//
										.with(JobHistory::getWorkerId, worker.getId())//
										.with(JobHistory::getWorkerName, worker.getName())//
										.with(JobHistory::getAmountComplete, amountDone.get())//
										.with(JobHistory::getPoints, job.get().getDifficulty())//
										.with(JobHistory::getTime, doneTime.get())//
										.create();
								worker.getPointHistory().create()//
										.with(PointHistory::getWorker, worker)//
										.with(PointHistory::getChangeType, PointChangeType.Job)//
										.with(PointHistory::getChangeSourceId, job.get().getId())//
										.with(PointHistory::getChangeSourceName, job.get().getName())//
										.with(PointHistory::getBeforePoints, oldPoints)//
										.with(PointHistory::getPointChange, amountDone.get())//
										.with(PointHistory::getQuantity, 1.0)//
										.with(PointHistory::getTime, doneTime.get())//
										.create();
								worker.setExcessPoints(oldPoints + amountDone.get());
								job.get().setLastDone(doneTime.get());
							}, button -> button.disableWith(job.map(j -> j == null ? "No job selected" : null))));
		}

		ObservableCollection<PointHistory> history = ObservableCollection
				.flattenValue(theUI.getSelectedWorker().map(w -> w == null ? null : w.getPointHistory().getValues().reverse()))//
				.flow().sorted((h1, h2) -> h2.getTime().compareTo(h1.getTime())).collect();
		panel.addTable(history, table -> {
			table.fill().fillV().decorate(deco -> deco.withTitledBorder("History", Color.black))//
					.withColumn("Time", Instant.class, PointHistory::getTime,
							col -> col.formatText(t -> ChoreUtils.DATE_FORMAT.format(t)).withWidths(80, 100, 200))//
					.withColumn("Type", PointChangeType.class, PointHistory::getChangeType, col -> col.formatText(type -> {
						switch (type) {
						case Job:
							return "Job Done";
						case Redemption:
							return "Points Redeemed/Earned";
						case Expectations:
							return "Expectations";
						case Cap:
							return "Negative Point Cap";
						}
						return type.name();
					}).withWidths(100, 150, 300))//
					.withColumn("Job/Resource", String.class, h -> {
						String currentName = null;
						switch (h.getChangeType()) {
						case Job:
							for (Job job : theUI.getJobs().getValues()) {
								if (job.getId() == h.getChangeSourceId()) {
									currentName = job.getName();
									break;
								}
							}
							break;
						case Redemption:
							for (PointResource res : theUI.getPointResources().getValues()) {
								if (res.getId() == h.getChangeSourceId()) {
									currentName = res.getName();
									break;
								}
							}
							break;
						default:
							break;
						}
						if (currentName != null) {
							if (!currentName.equals(h.getChangeSourceName())) {
								h.setChangeSourceName(currentName);
							}
							return currentName;
						} else if (h.getChangeSourceName() == null) {
							return "";
						} else {
							return h.getChangeSourceName();
						}
					}, col -> col.withWidths(50, 150, 500))//
					.withColumn("Amount", double.class, PointHistory::getQuantity, col -> col.formatText(dblFormat::format))//
					.withColumn("Points Before", long.class, PointHistory::getBeforePoints, null)//
					.withColumn("Point Change", int.class, PointHistory::getPointChange, null)//
					.withColumn("Points After", long.class, h -> h.getBeforePoints() + h.getPointChange(), null)//
			;
		});
	}
}
