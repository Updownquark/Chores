package org.quark.chores.ui;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import javax.swing.JPanel;

import org.observe.SettableValue;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfigFormat;
import org.observe.config.ObservableConfigFormatSet;
import org.observe.config.SyncValueSet;
import org.observe.ext.util.GitHubApiHelper;
import org.observe.ext.util.GitHubApiHelper.Release;
import org.observe.util.TypeTokens;
import org.observe.util.swing.AppPopulation.ObservableUiBuilder;
import org.observe.util.swing.ObservableSwingUtils;
import org.observe.util.swing.PanelPopulation;
import org.qommons.io.Format;
import org.quark.chores.entities.AssignedJob;
import org.quark.chores.entities.Assignment;
import org.quark.chores.entities.Job;
import org.quark.chores.entities.PointResource;
import org.quark.chores.entities.Worker;

public class ChoresUI extends JPanel {
	private final SyncValueSet<Job> theJobs;
	private final SyncValueSet<Worker> theWorkers;
	private final SyncValueSet<Assignment> theAssignments;
	private final SyncValueSet<PointResource> thePointResources;
	private final ObservableConfig theConfig;

	private final SettableValue<Worker> theSelectedWorker;
	private final SettableValue<Job> theSelectedJob;
	private final SettableValue<Assignment> theSelectedAssignment;
	private final SettableValue<PointResource> theSelectedPointResource;

	private final AssignmentPanel theAssignmentPanel;
	private final WorkersPanel theWorkersPanel;
	private final JobsPanel theJobsPanel;
	private final RedemptionPanel theRedemptionPanel;

	public ChoresUI(SyncValueSet<Job> jobs, SyncValueSet<Worker> workers, SyncValueSet<Assignment> assignments,
			SyncValueSet<PointResource> pointResources, ObservableConfig config) {
		theJobs = jobs;
		theWorkers = workers;
		theAssignments = assignments;
		thePointResources = pointResources;
		theConfig = config;

		theSelectedWorker = SettableValue.build(Worker.class).safe(false).build();
		theSelectedJob = SettableValue.build(Job.class).safe(false).build();
		theSelectedAssignment = SettableValue.build(Assignment.class).safe(false).build();
		theSelectedPointResource = SettableValue.build(PointResource.class).safe(false).build();

		// Select the last assignment initially
		theSelectedAssignment.set(getLastAssignment(theAssignments.getValues()), null);
		theAssignments.getValues().changes().act(evt -> {
			switch (evt.type) {
			case add:
				// When a new assignment is created (after the current one), select it
				Assignment last = getLastAssignment(evt.getValues());
				if (last != null
						&& (theSelectedAssignment.get() == null || last.getDate().compareTo(theSelectedAssignment.get().getDate()) > 0)) {
					theSelectedAssignment.set(last, evt);
				}
				break;
			case remove:
				// If the selected assignment is deleted, select the last assignment
				if (evt.getValues().contains(theSelectedAssignment.get())) {
					theSelectedAssignment.set(getLastAssignment(theAssignments.getValues()), evt);
				}
				break;
			case set:
				// If the selected assignment is changed, fire an update
				if (evt.getValues().contains(theSelectedAssignment.get())) {
					theSelectedAssignment.set(theSelectedAssignment.get(), evt);
				}
				break;
			}
		});

		theAssignmentPanel = new AssignmentPanel(this);
		theWorkersPanel = new WorkersPanel(this);
		theJobsPanel = new JobsPanel(this);
		theRedemptionPanel = new RedemptionPanel(this);

		initComponents();
	}

	private static Assignment getLastAssignment(List<Assignment> assignments) {
		Assignment lastAssignment = null;
		for (Assignment assign : assignments) {
			if (lastAssignment == null || assign.getDate().compareTo(lastAssignment.getDate()) > 0) {
				lastAssignment = assign;
			}
		}
		return lastAssignment;
	}

	public ObservableConfig getConfig() {
		return theConfig;
	}

	public SyncValueSet<Job> getJobs() {
		return theJobs;
	}

	public SyncValueSet<Worker> getWorkers() {
		return theWorkers;
	}

	public SyncValueSet<Assignment> getAssignments() {
		return theAssignments;
	}

	public SettableValue<Assignment> getSelectedAssignment() {
		return theSelectedAssignment;
	}

	public SyncValueSet<PointResource> getPointResources() {
		return thePointResources;
	}

	public SettableValue<Worker> getSelectedWorker() {
		return theSelectedWorker;
	}

	public SettableValue<Job> getSelectedJob() {
		return theSelectedJob;
	}

	public SettableValue<PointResource> getSelectedPointResource() {
		return theSelectedPointResource;
	}

	private void initComponents() {
		SettableValue<String> selectedTab = theConfig.asValue(String.class).at("selected-tab").withFormat(Format.TEXT, () -> "settings")
				.buildValue(null);

		PanelPopulation.populateVPanel(this, null)//
				.addTabs(tabs -> {
					tabs.fill().fillV().withSelectedTab(selectedTab);
					tabs.withVTab("assignments", theAssignmentPanel::addPanel, tab -> tab.setName("Assignments"));
					tabs.withVTab("workers", theWorkersPanel::addPanel, tab -> tab.setName("Workers"));
					tabs.withVTab("jobs", theJobsPanel::addPanel, tab -> tab.setName("Jobs"));
					tabs.withVTab("points", theRedemptionPanel::addPanel, tab -> tab.setName("Redemption"));
				});
	}

	public static void main(String[] args) {
		ObservableUiBuilder builder = ObservableSwingUtils.buildUI();
		builder
				.withConfig("chores-config").withConfigAt("Chores.xml")//
				// .withConfig("chores-motivator").withConfigAt("ChoreMotivator.xml")//
				// .withOldConfig("chores-config").withOldConfigAt("Chores.xml")//
				.enableCloseWithoutSave()//
				.withErrorReporting("https://github.com/Updownquark/Chores/issues/new", (str, error) -> {
					if (error) {
						str.append("<ol><li>Describe your issue, what you did to produce it, what effects it had, etc.</li>");
					} else {
						str.append("<ol><li>Describe your issue or feature idea");
					}
					str.append("</li><li>Click \"Submit new issue\"</li></ol>");
				})
				.withIcon(ChoresUI.class, "/icons/broom.jpg")//
				.withConfigInit(ChoresUI.class, "/config/InitialConfig.xml")//
				.withAbout(ChoresUI.class, about -> about.withLatestVersion(() -> {
					Release r;
					try {
						r = new GitHubApiHelper("Updownquark", "Chores").getLatestRelease(ChoresUI.class);
					} catch (IOException e) {
						e.printStackTrace(System.out);
						return null;
					}
					return r == null ? null : r.getTagName();
				}).withUpgrade(version -> {
					try {
						new GitHubApiHelper("Updownquark", "Chores").upgradeToLatest(ChoresUI.class, builder.getTitle().get(),
								builder.getIcon().get());
					} catch (IllegalStateException | IOException e) {
						e.printStackTrace(System.out);
					}
				}))//
				.withBackups(backups -> backups.withBackupSize(1_000_000, 100_000_000).withDuration(Duration.ofDays(1), Duration.ofDays(30))
						.withBackupCount(10, 100))//
				.withTitle("Chore Motivator").systemLandF().build((config, onBuilt) -> {
					try {
						new GitHubApiHelper("Updownquark", "Chores").checkForNewVersion(ChoresUI.class, builder.getTitle().get(),
								builder.getIcon().get(), release -> {
									String declinedRelease = config.get("declined-release");
									return !release.getTagName().equals(declinedRelease);
								}, release -> config.set("declined-release", release.getTagName()), () -> {
									ObservableConfigFormatSet formats = new ObservableConfigFormatSet();
									SyncValueSet<Job> jobs = getJobs(config, formats, "jobs/job");
									SyncValueSet<Worker> workers = getWorkers(config, formats, "workers/worker", jobs);
									SyncValueSet<Assignment> assignments = getAssignments(config, formats, "assignments/assignment", jobs,
											workers);
									SyncValueSet<PointResource> pointResources = getPointResource(config, formats,
											"point-resources/point-resource");
									onBuilt.accept(new ChoresUI(jobs, workers, assignments, pointResources, config));
								});
					} catch (IOException e) {
						// Put this on System.out so we don't trigger the bug warning
						e.printStackTrace(System.out);
					}
				});
	}

	private static SyncValueSet<Job> getJobs(ObservableConfig config, ObservableConfigFormatSet formats, String path) {
		return config.asValue(Job.class).withFormatSet(formats).at(path).buildEntitySet(null);
	}

	private static SyncValueSet<Worker> getWorkers(ObservableConfig config, ObservableConfigFormatSet formats, String path,
			SyncValueSet<Job> jobs) {
		ObservableConfigFormat<Job> jobRefFormat = ObservableConfigFormat.<Job> buildReferenceFormat(jobs.getValues(), null)//
				.withField("id", Job::getId, ObservableConfigFormat.LONG).build();
		return config.asValue(Worker.class).withFormatSet(formats).asEntity(workerConfig -> {
			workerConfig.withFieldFormat(Worker::getJobPreferences, ObservableConfigFormat.ofMap(jobs.getValues().getType(),
					TypeTokens.get().INT, "job", "preference", jobRefFormat, ObservableConfigFormat.INT));
		}).at(path).buildEntitySet(null);
	}

	private static SyncValueSet<Assignment> getAssignments(ObservableConfig config, ObservableConfigFormatSet formats, String path,
			SyncValueSet<Job> jobs, SyncValueSet<Worker> workers) {
		ObservableConfigFormat<Job> jobRefFormat = ObservableConfigFormat.<Job> buildReferenceFormat(jobs.getValues(), null)//
				.withField("id", Job::getId, ObservableConfigFormat.LONG).build();
		ObservableConfigFormat<Worker> workerRefFormat = ObservableConfigFormat
				.<Worker> buildReferenceFormat(fv -> workers.getValues(), null)//
				.withField("id", Worker::getId, ObservableConfigFormat.LONG).build();
		ObservableConfigFormat.EntityConfigFormat<AssignedJob> assignedJobFormat = ObservableConfigFormat
				.buildEntities(TypeTokens.get().of(AssignedJob.class), formats)//
				.withFieldFormat(AssignedJob::getJob, jobRefFormat)//
				.withFieldFormat(AssignedJob::getWorker, workerRefFormat).build();
		return config.asValue(Assignment.class).withFormatSet(formats).asEntity(assignmentConfig -> {
			assignmentConfig.withFieldFormat(Assignment::getAssignments,
					ObservableConfigFormat.ofEntitySet(assignedJobFormat, "assignment"));
		}).at(path).buildEntitySet(null);
	}

	private static SyncValueSet<PointResource> getPointResource(ObservableConfig config, ObservableConfigFormatSet formats, String path) {
		return config.asValue(PointResource.class).withFormatSet(formats).at(path).buildEntitySet(null);
	}
}
