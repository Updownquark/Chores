package org.quark.chores.ui;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfigFormat;
import org.observe.config.ObservableConfigFormatSet;
import org.observe.config.SyncValueCreator;
import org.observe.config.SyncValueSet;
import org.observe.data.ReflectedEntityMappingScheme;
import org.observe.data.ReflectedEntitySet;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.data.csv.CsvEntitySetPersistence;
import org.qommons.data.impl.VersionedDataScheme;
import org.qommons.io.BetterFile;
import org.qommons.io.NativeFileSource;
import org.qommons.io.TextParseException;
import org.qommons.threading.QommonsTimer;
import org.quark.chores.entities.*;

public class ChoreDataConverter {
	public static void main(String... args) throws IOException, TextParseException {
		// Read in the old data config. Don't do anything if this fails.
		ObservableConfig config = ObservableConfig.createRoot("chores", ThreadConstraint.ANY);
		BetterFile dir = new NativeFileSource().at(args[0]);
		try (InputStream in = dir.at(dir.getName() + ".xml").read()) {
			ObservableConfig.readXml(config, in, ObservableConfig.XmlEncoding.DEFAULT);
		}

		// Initialize the new (empty) QommonData-backed data set
		ReflectedEntityMappingScheme mapping = new ReflectedEntityMappingScheme();
		VersionedDataScheme.InitializedDataScheme qDataScheme = VersionedDataScheme.init(
				QommonsUtils.unmodifiableDistinctCopy(Job.class, Worker.class, Assignment.class, PointResource.class), mapping,
				Job.SCHEMA_HISTORY);
		ReflectedEntitySet qData = new ReflectedEntitySet(qDataScheme.mappedEntityTypes, mapping.getReflectorCache(), null, null);

		// Parse out the old config-backed entities
		ObservableConfigFormatSet formats = new ObservableConfigFormatSet();
		// This is important. The copy() methods in the creators below will fail if the config entities are using different reflectors
		// than the QommonData entities
		formats.getReflectors().putAll(mapping.getReflectorCache());
		SyncValueSet<Job> configJobs = getJobs(config, formats, "jobs/job");
		SyncValueSet<Worker> configWorkers = getWorkers(config, formats, "workers/worker", configJobs);
		SyncValueSet<Assignment> configAssignments = getAssignments(config, formats, "assignments/assignment", configJobs, configWorkers);
		SyncValueSet<PointResource> configPointResources = getPointResource(config, formats, "point-resources/point-resource");

		// Copy over the data
		SyncValueSet<Job> qdJobs = qData.observeEntities(Job.class);
		SyncValueSet<Worker> qdWorkers = qData.observeEntities(Worker.class);
		SyncValueSet<Assignment> qdAssignments = qData.observeEntities(Assignment.class);
		SyncValueSet<PointResource> qdPointResources = qData.observeEntities(PointResource.class);

		try (Transaction t = qData.lock(true, null)) {
			// Note: the copy() method in the entity creator skips ID fields,
			// but we want to preserve these because the history entities use them explicitly
			for (Job configJob : configJobs.getValues()) {
				Job qdJob = qdJobs.create().with(Job::getId, configJob.getId()).copy(configJob).create().get();
				// Copy over the history
				for (JobHistory history : configJob.getHistory().getValues()) {
					SyncValueCreator<JobHistory, JobHistory> creator = qdJob.getHistory().create()//
							.with(JobHistory::getTime, history.getTime())//
							.copy(history);
					if (creator.canCreate() == null) { // There may be duplicates
						creator.create();
					}
				}
			}
			for (Worker configWorker : configWorkers.getValues()) {
				Worker qdWorker = qdWorkers.create().with(Worker::getId, configWorker.getId()).copy(configWorker).create().get();
				// Copy over the history
				for (PointHistory history : configWorker.getPointHistory().getValues()) {
					SyncValueCreator<PointHistory, PointHistory> creator = qdWorker.getPointHistory().create()//
							.with(PointHistory::getTime, history.getTime())//
							.with(PointHistory::getChangeSourceId, history.getChangeSourceId())//
							.copy(history);
					if (creator.canCreate() == null) {
						creator.create();
					}
				}
			}
			for (Assignment configAssn : configAssignments.getValues()) {
				Assignment qdAssn = qdAssignments.create().with(Assignment::getDate, configAssn.getDate()).copy(configAssn).create().get();
				for (AssignedJob job : configAssn.getAssignments().getValues()) {
					qdAssn.getAssignments().create()//
					.with(AssignedJob::getWorker, qData.getEntity(Worker.class, job.getWorker().getId()))//
					.copy(job)//
					.create();
				}
			}
			for (PointResource configResource : configPointResources.getValues()) {
				qdPointResources.create()//
				.with(PointResource::getId, configResource.getId())//
				.copy(configResource).create()//
				.get();
			}
		}

		// Persist the data
		qDataScheme.createPersister(dir, new CsvEntitySetPersistence())//
		.saveSchema(qDataScheme.migrations)//
		.save(qData, null, new VersionedDataScheme.PersistenceMonitor() {
			@Override
			public void persistenceSucceeded(long stamp) {
				System.out.println("Chore Champ data converted to QommonData:");
				System.out.println("\t" + qdJobs.getValues().size() + " Jobs");
				System.out.println("\t" + qdWorkers.getValues().size() + " Workers");
				System.out.println("\t" + qdAssignments.getValues().size() + " Assignments");
				System.out.println("\t" + qdPointResources.getValues().size() + " Point Resources");
				dieSoon();
			}

			@Override
			public void persistenceAborted(long stamp) {
				System.err.println("Chore Champ data persistence aborted!");
				dieSoon();
			}

			@Override
			public void persistenceFailed(long stamp, String error, Throwable exception) {
				System.err.println("Chore Champ persistence failed: " + error);
				exception.printStackTrace();
				dieSoon();
			}
		});
	}

	static void dieSoon() {
		QommonsTimer.getCommonInstance().offload(() -> System.exit(0), Duration.ofMillis(100));
	}

	static SyncValueSet<Job> getJobs(ObservableConfig config, ObservableConfigFormatSet formats, String path) {
		return config.asValue(Job.class).withFormatSet(formats).at(path).buildEntitySet(null);
	}

	static SyncValueSet<Worker> getWorkers(ObservableConfig config, ObservableConfigFormatSet formats, String path,
			SyncValueSet<Job> jobs) {
		ObservableConfigFormat<Job> jobRefFormat = ObservableConfigFormat.<Job> buildReferenceFormat(jobs.getValues(), null)//
				.withField("id", Job::getId, ObservableConfigFormat.LONG).build();
		return config.asValue(Worker.class).withFormatSet(formats).asEntity(workerConfig -> {
			// workerConfig.withFieldFormat(Worker::getJobPreferences, ObservableConfigFormat.ofMap(jobs.getType().getType(),
			// TypeTokens.get().INT, "job", "preference", jobRefFormat, ObservableConfigFormat.INT));
		}).at(path).buildEntitySet(null);
	}

	static SyncValueSet<Assignment> getAssignments(ObservableConfig config, ObservableConfigFormatSet formats, String path,
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

	static SyncValueSet<PointResource> getPointResource(ObservableConfig config, ObservableConfigFormatSet formats, String path) {
		return config.asValue(PointResource.class).withFormatSet(formats).at(path).buildEntitySet(null);
	}
}
