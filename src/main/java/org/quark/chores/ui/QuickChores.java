package org.quark.chores.ui;

import java.time.Instant;
import java.util.IdentityHashMap;
import java.util.Map;

import org.observe.assoc.ObservableMap;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfigFormat;
import org.observe.config.ObservableConfigFormatSet;
import org.observe.config.SyncValueSet;
import org.observe.util.TypeTokens;
import org.qommons.Named;
import org.qommons.Transaction;
import org.qommons.io.Format;
import org.quark.chores.entities.*;
import org.quark.chores.entities.PointHistory.PointChangeType;

public class QuickChores {
	public static final Format<ObservableCollection<String>> LABELS_FORMAT = new Format.CollectionFormat<>(Format.TEXT, ", ", null, null,
			ObservableCollection::create);

	private final ObservableConfig theConfig;
	public final SyncValueSet<Job> jobs;
	public final SyncValueSet<Worker> workers;
	public final SyncValueSet<Assignment> assignments;
	public final SyncValueSet<PointResource> resources;

	private final ObservableMap<Long, Job> theJobsById;
	private final ObservableMap<Long, PointResource> theResourcesById;

	public QuickChores(ObservableConfig config) {
		theConfig = config;
		ObservableConfigFormatSet formats = new ObservableConfigFormatSet();
		jobs = getJobs(config, formats, "jobs/job");
		workers = getWorkers(config, formats, "workers/worker", jobs);
		assignments = getAssignments(config, formats, "assignments/assignment", jobs, workers);
		resources = getPointResources(config, formats, "point-resources/point-resource");

		theJobsById = jobs.getValues().flow()//
				.groupBy(Job::getId, null)//
				.gather()//
				.singleMap(true);
		theResourcesById = resources.getValues().flow()//
				.groupBy(PointResource::getId, null)//
				.gather()//
				.singleMap(true);
	}

	public Worker createWorker() {
		return workers.create()//
				.with(Worker::getName, "New Worker")//
				.with(Worker::getAbility, 100)//
				.create().get();
	}

	public Job createJob() {
		return jobs.create()//
				.with(Job::getName, "New Job")//
				.with(Job::isActive, true)//
				.with(Job::getPriority, 5)//
				.with(Job::getDifficulty, 1)//
				.with(Job::getMaxLevel, 100)//
				.create().get();
	}

	public void deleteJob(Job job) {
		for (Assignment assn : assignments.getValues()) {
			assn.getAssignments().getValues().removeIf(assnJob -> assnJob.getJob() == job);
		}
		jobs.getValues().remove(job);
	}

	public PointResource createResource() {
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

	public void submit(Assignment currentAssignment) {
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
					job.getJob().setLastDone(currentAssignment.getDate());
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
		.with(JobHistory::getPoints, job.getDifficulty())//
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
		.with(PointHistory::getWorker, worker)//
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

	private static SyncValueSet<Job> getJobs(ObservableConfig config, ObservableConfigFormatSet formats, String path) {
		return config.asValue(Job.class).withFormatSet(formats).at(path).buildEntitySet(null);
	}

	private static SyncValueSet<Worker> getWorkers(ObservableConfig config, ObservableConfigFormatSet formats, String path,
			SyncValueSet<Job> jobs) {
		ObservableConfigFormat<Job> jobRefFormat = ObservableConfigFormat.<Job> buildReferenceFormat(jobs.getValues(), null)//
				.withField("id", Job::getId, ObservableConfigFormat.LONG).build();
		return config.asValue(Worker.class).withFormatSet(formats).asEntity(workerConfig -> {
			workerConfig.withFieldFormat(Worker::getJobPreferences, ObservableConfigFormat.ofMap(TypeTokens.get().of(Job.class),
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

	private static SyncValueSet<PointResource> getPointResources(ObservableConfig config, ObservableConfigFormatSet formats, String path) {
		return config.asValue(PointResource.class).withFormatSet(formats).at(path).buildEntitySet(null);
	}
}
