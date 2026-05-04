package org.quark.chores.ui;

import java.time.Instant;
import java.util.IdentityHashMap;
import java.util.Map;

import org.observe.assoc.ObservableMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableValueSet;
import org.observe.config.ValueCreator;
import org.observe.config.ValueOperationException;
import org.qommons.Named;
import org.qommons.Transaction;
import org.qommons.io.Format;
import org.quark.chores.entities.*;
import org.quark.chores.entities.PointHistory.PointChangeType;

public class QuickChores {
	public static final Format<ObservableSet<String>> LABELS_FORMAT = new Format.CollectionFormat<>(Format.TEXT, ", ", null, null,
			ObservableSet::create);

	private final ObservableConfig theConfig;
	public final ObservableValueSet<Job> jobs;
	public final ObservableValueSet<Worker> workers;
	public final ObservableValueSet<Assignment> assignments;
	public final ObservableValueSet<PointResource> resources;

	private final ObservableMap<Long, Job> theJobsById;
	private final ObservableMap<Long, PointResource> theResourcesById;

	public QuickChores(ObservableValueSet<Job> jobs, ObservableValueSet<Worker> workers, ObservableValueSet<Assignment> assignments,
			ObservableValueSet<PointResource> resources, ObservableConfig config) {
		theConfig = config;
		this.jobs = jobs;
		this.workers = workers;
		this.assignments = assignments;
		this.resources = resources;

		theJobsById = jobs.getValues().flow()//
				.groupBy(Job::getId, null)//
				.gather()//
				.singleMap(true);
		theResourcesById = resources.getValues().flow()//
				.groupBy(PointResource::getId, null)//
				.gather()//
				.singleMap(true);
	}

	public Worker createWorker() throws IllegalArgumentException, ValueOperationException {
		return workers.create()//
				.with(Worker::getName, "New Worker")//
				.with(Worker::getAbility, 100)//
				.create().get();
	}

	public Job createJob() throws IllegalArgumentException, ValueOperationException {
		return jobs.create()//
				.with(Job::getName, "New Job")//
				.with(Job::isActive, true)//
				.with(Job::getPriority, 5)//
				.with(Job::getPoints, 1)//
				.with(Job::getMaxLevel, 100)//
				.create().get();
	}

	public void deleteWorker(Worker worker) {
		for (Assignment assn : assignments.getValues()) {
			assn.getAssignments().getValues().removeIf(assnJob -> assnJob.getWorker() == worker);
		}
		workers.getValues().remove(worker);
	}

	public void deleteJob(Job job) {
		for (Assignment assn : assignments.getValues()) {
			assn.getAssignments().getValues().removeIf(assnJob -> assnJob.getJob() == job);
		}
		jobs.getValues().remove(job);
	}

	public PointResource createResource() throws IllegalArgumentException, ValueOperationException {
		return resources.create()//
				.with(PointResource::getName, "New Resource")//
				.with(PointResource::getRate, 1.0)//
				.create().get();
	}

	public void replace(ObservableCollection<String> labels, ObservableCollection<String> newLabels) {
		try (Transaction t = labels.lock(true, null)) {
			labels.clear();
			labels.addAll(newLabels);
		}
	}

	public AssignedJob getAssignment(AssignedJob current, Assignment currentAssignment, Worker worker, Job job) {
		if (current != null) {
			return current;
		}
		return currentAssignment.getAssignments().create()//
				.with(AssignedJob::getWorker, worker)//
				.with(AssignedJob::getJob, job)//
				.create().get();
	}

	public String shouldAssign(Worker worker, Job job) {
		if (worker.getLevel() < job.getMinLevel()) {
			return worker.getName() + " is level " + worker.getLevel() + ", " + job.getName() + " requires at least level "
					+ job.getMinLevel();
		} else if (worker.getLevel() > job.getMaxLevel()) {
			return worker.getName() + " is level " + worker.getLevel() + ", " + job.getName() + " requires at most level "
					+ job.getMaxLevel();
		}
		for (String label : worker.getLabels()) {
			if (job.getExclusionLabels().contains(label)) {
				return "Job excludes label " + label;
			}
		}
		for (String label : job.getInclusionLabels()) {
			if (!worker.getLabels().contains(label)) {
				return "Job requires label " + label;
			}
		}
		return null;
	}

	public void submit(Assignment currentAssignment) throws IllegalArgumentException, ValueOperationException {
		if (currentAssignment != null) {
			Map<Worker, Long> excessPoints = new IdentityHashMap<>();
			for (Worker worker : workers.getValues()) {
				excessPoints.put(worker, worker.getExcessPoints());
			}
			Instant assignmentTime = currentAssignment.getDate();
			Instant now = Instant.now();
			for (AssignedJob job : currentAssignment.getAssignments().getValues()) {
				excessPoints.compute(job.getWorker(), (worker, excess) -> {
					worker.getPointHistory().create()//
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
				job.getJob().setLastDone(currentAssignment.getDate());
				ValueCreator<JobHistory, JobHistory> jobHistory = job.getJob().getHistory().create()//
						.with(JobHistory::getWorkerId, job.getWorker().getId())//
						.with(JobHistory::getWorkerName, job.getWorker().getName())//
						.with(JobHistory::getAmountComplete, job.getCompletion())//
						.with(JobHistory::getPoints, job.getJob().getPoints())//
						.with(JobHistory::getTime, assignmentTime)//
						.with(JobHistory::isCompleted, job.getCompletion() >= job.getJob().getPoints());
				if (jobHistory.canCreate() == null) {
					jobHistory.create();
				}
			}
			for (Map.Entry<Worker, Long> entry : excessPoints.entrySet()) {
				long newPoints = entry.getValue();
				entry.getKey().setExcessPoints(newPoints);
				entry.setValue(newPoints);
			}
		}

		// Clear out the old assignment. We keep history on each worker
		assignments.getValues().clear();
		// Now create the new Assignment
		assignments.create()//
		.with(Assignment::getDate, Instant.now())//
		.create().get();
	}

	public void reportWork(Worker worker, Job job, int points) {
		Instant now = Instant.now();
		long oldPoints = worker.getExcessPoints();
		job.getHistory().create()//
		.with(JobHistory::getJob, job)//
		.with(JobHistory::getWorkerId, worker.getId())//
		.with(JobHistory::getWorkerName, worker.getName())//
		.with(JobHistory::getAmountComplete, points)//
		.with(JobHistory::getPoints, job.getPoints())//
		.with(JobHistory::getTime, now)//
		.create();
		worker.getPointHistory().create()//
		.with(PointHistory::getWorker, worker)//
		.with(PointHistory::getChangeType, PointChangeType.Job)//
		.with(PointHistory::getChangeSourceId, job.getId())//
		.with(PointHistory::getChangeSourceName, job.getName())//
		.with(PointHistory::getBeforePoints, oldPoints)//
		.with(PointHistory::getPointChange, points)//
		.with(PointHistory::getQuantity, 1.0)//
		.with(PointHistory::getTime, now)//
		.create();
		worker.setExcessPoints(oldPoints + points);
		job.setLastDone(now);
	}

	public String renderWithUnit(double value, String unit) {
		if (value == 0.0) {
			return "";
		} else if (unit == null) {
			return "" + value;
		} else if (unit.equals("$")) {
			return unit + value;
		} else {
			return value + " " + unit;
		}
	}

	public void usePoints(Worker worker, PointResource resource, int points) {
		worker.getPointHistory().create()//
		.with(PointHistory::getTime, Instant.now())//
		.with(PointHistory::getChangeType, PointChangeType.Redemption)//
		.with(PointHistory::getQuantity, points * resource.getRate())//
		.with(PointHistory::getChangeSourceId, resource.getId())//
		.with(PointHistory::getChangeSourceName, resource.getName())//
		.with(PointHistory::getBeforePoints, worker.getExcessPoints())//
		.with(PointHistory::getPointChange, -points)//
		.create();
		worker.setExcessPoints(worker.getExcessPoints() - points);
	}

	public Named getHistorySource(PointHistory history) {
		switch (history.getChangeType()) {
		case Job:
			return theJobsById.get(history.getChangeSourceId());
		case Redemption:
			return theResourcesById.get(history.getChangeSourceId());
		case Cap:
		case Expectations:
			return null;
		}
		return null;
	}

	/*
	private static SyncValueSet<Job> getConfigJobs(ObservableConfig config, ObservableConfigFormatSet formats, String path) {
		return config.asValue(Job.class).withFormatSet(formats).at(path).buildEntitySet(null);
	}

	private static SyncValueSet<Worker> getConfigWorkers(ObservableConfig config, ObservableConfigFormatSet formats, String path,
			SyncValueSet<Job> jobs) {
		ObservableConfigFormat<Job> jobRefFormat = ObservableConfigFormat.<Job> buildReferenceFormat(jobs.getValues(), null)//
				.withField("id", Job::getId, ObservableConfigFormat.LONG).build();
		return config.asValue(Worker.class).withFormatSet(formats).asEntity(workerConfig -> {
			workerConfig.withFieldFormat(Worker::getJobPreferences, ObservableConfigFormat.ofMap(TypeTokens.get().of(Job.class),
					TypeTokens.get().INT, "job", "preference", jobRefFormat, ObservableConfigFormat.INT));
		}).at(path).buildEntitySet(null);
	}

	private static SyncValueSet<Assignment> getConfigAssignments(ObservableConfig config, ObservableConfigFormatSet formats, String path,
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

	private static SyncValueSet<PointResource> getConfigPointResources(ObservableConfig config, ObservableConfigFormatSet formats,
			String path) {
		return config.asValue(PointResource.class).withFormatSet(formats).at(path).buildEntitySet(null);
	}
	 */
}
