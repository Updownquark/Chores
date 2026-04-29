<?xml version="1.0" encoding="UTF-8"?>

<quick xmlns:quick="Quick-X v0.1" xmlns:config="Expresso-Config v0.1" with-extension="window"
	title="`Chore Champ`" window-icon="`/icons/broom.jpg`" close-action="exit"
	x="config.windowX" y="config.windowY" width="config.windowW" height="config.windowH">
	<head>
		<imports>
			<import>org.quark.chores.ui.*</import>
			<import>org.quark.chores.entities.*</import>
			<import>org.qommons.BiTuple</import>
		</imports>
		<models>
			<config name="config" config-name="Chores">
				<value name="windowX" type="int" config-path="x" />
				<value name="windowY" type="int" config-path="y" />
				<value name="windowW" type="int" config-path="width" default="1000" />
				<value name="windowH" type="int" config-path="height" default="800"/>
			</config>
			<entity-data-set name="data" config-name="Chores" migrations="`/org/quark/chores/entities/Schema History.xml`">
				<sorted-set name="jobs" type="Job" />
				<sorted-set name="workers" type="Worker" />
				<sorted-set name="assignments" type="Assignment" />
				<sorted-set name="resources" type="PointResource" />

				<value-set name="jobsVS" type="Job" />
				<value-set name="workersVS" type="Worker" />
				<value-set name="assignmentsVS" type="Assignment" />
				<value-set name="resourcesVS" type="PointResource" />
				
				<!-- A smaller set of backup ages than the default -->
				<data-backup ages="{`15s`, `5m`, `1h`, `1d`, `1w`, `1mo`}" />
			</entity-data-set>
			<model name="app">
				<constant name="ui">new QuickChores(data.jobsVS, data.workersVS, data.assignmentsVS, data.resourcesVS, config.$CONFIG$)</constant>
				
				<transform name="jobs" source="data.jobs">
					<sort sort-value-as="job">
						<sort-by>job.getName()</sort-by>
					</sort>
				</transform>
				<transform name="workers" source="data.workers">
					<sort sort-value-as="worker" ascending="false">
						<sort-by>worker.getAbility()</sort-by>
					</sort>
				</transform>
				<transform name="resources" source="data.resources">
					<sort sort-value-as="resource">
						<sort-by>resource.getName()</sort-by>
					</sort>
				</transform>
				
				<transform name="currentAssignment" source="data.assignments">
					<terminal first="true" />
				</transform>
				<list name="currentAssignments" type="AssignedJob">currentAssignment.getAssignments().getValues()</list>
				<transform name="currentJobAssignments" source="currentAssignments">
					<group-by source-as="job" key="job.getJob()" />
				</transform>
				<transform name="currentWorkerAssignments" source="currentAssignments">
					<group-by source-as="job" key="job.getWorker()" />
				</transform>
				<transform name="currentWorkerJobAssignments" source="currentAssignments">
					<group-by source-as="job" key="new BiTuple&lt;>(job.getWorker(), job.getJob())" />
					<map-transform type="value">
						<terminal first="true" />
					</map-transform>
				</transform>
				<value name="selectedJob" type="Job" />
				<value name="selectedWorker" type="Worker" />
				<value name="selectedResource" type="PointResource" />
				<transform name="allJobAssignments" source="data.assignments">
					<map-to source-as="assn">assn.getAssignments().getValues()</map-to>
					<flatten to="list" />
					<filter source-as="assn" test="assn.getJob()==selectedJob" />
					<sort sort-value-as="assn">
						<sort-by ascending="false">assn.getAssignment().getDate()</sort-by>
						<sort-by>data.assignments.indexOf(assn.getWorker())</sort-by>
					</sort>
				</transform>
				<transform name="allWorkerAssignments" source="data.assignments">
					<map-to source-as="assn">assn.getAssignments().getValues()</map-to>
					<flatten to="list" />
					<filter source-as="assn" test="assn.getWorker()==selectedWorker" />
					<sort sort-value-as="assn">
						<sort-by ascending="false">assn.getAssignment().getDate()</sort-by>
						<sort-by>assn.getJob().getName()</sort-by>
					</sort>
				</transform>
				<instant-format name="timeFormat" />
				
				<map name="usage" key-type="BiTuple&lt;Worker, PointResource>" type="Integer" />
				
				<value name="confirmTitle" type="String" />
				<value name="confirmText" type="String" />
				<value name="confirmed" init="false" />
				<hook name="resetConfirmed" on="confirmText">confirmText!=null ? confirmed=false : null</hook>
			</model>
		</models>
		<style-sheet>
			<!--<import-style-sheet name="searcher" ref="quick-testing.qss" />-->
		</style-sheet>
	</head>

	<tabs>
		<confirm visible="app.confirmText!=null" title="app.confirmTitle" on-confirm="app.confirmed=true">
			<label value="app.confirmText" />
		</confirm>
		
		<box tab-id="`assignment`" tab-name="`Assignments`"
			layout="inline-layout" orientation="vertical" main-align="justify" cross-align="justify">
			<super-table rows="app.jobs" active-value-name="job" selection="app.selectedJob">
				<column name="`Job`" value="job.getName()" column-value-name="name">
					<label value="name" tooltip="`&lt;html>`
						+(job.getLastDone()==null ? `Never Done` : (`Last done `+app.timeFormat.format(job.getLastDone())))">
						<style attr="font-color" if="!job.isActive()">`dark-gray`</style>
					</label>
					<column-edit column-edit-value-name="newName" commit="job.setName(newName)">
						<text-field />
					</column-edit>
				</column>
				<column name="`Points`" value="job.getDifficulty()" pref-width="65">
					<column-edit column-edit-value-name="newPoints" commit="job.setDifficulty(newPoints)">
						<text-field />
					</column-edit>
				</column>
				<variable-columns for-each="app.workers" column-element-as="worker" name="columnName"
					value="assignment==null ? -1 : assignment.getCompletion()" column-value-name="completion">
					<model>
						<value name="columnName">worker.getName()+` (`+worker.getExcessPoints()+`)`</value>
						<value name="assignment" type="AssignedJob">app.currentWorkerJobAssignments.observe(new BiTuple&lt;>(worker, job))</value>
						<value name="shouldAssign">app.ui.shouldAssign(worker, job)</value>
					</model>
					<label value="completion==-1 ? `` : String.valueOf(completion)" tooltip="shouldAssign">
						<style attr="color" if="shouldAssign!=null">`light-gray`</style>
					</label>
					<column-edit column-edit-value-name="newCompletion"
						commit="app.ui.getAssignment(assignment, app.currentAssignment, worker, job).setCompletion(newCompletion)">
						<text-field />
					</column-edit>
				</variable-columns>
			</super-table>
			<box layout="inline-layout" orientation="horizontal" main-align="center">
				<button action="app.ui.submit(app.currentAssignment)">`Submit`</button>
			</box>
		</box>
		<box tab-id="`workers`" tab-name="`Workers`"
			layout="inline-layout" orientation="vertical" main-align="justify" cross-align="justify">
			<model>
				<value name="workerToDelete" type="Worker" />
				<action name="create" always-enabled="true">app.selectedWorker=app.ui.createWorker()</action>
			</model>
			<confirm visible="workerToDelete!=null" title="`Delete Worker '`+workerToDelete.getName()+`?`"
				on-confirm="{app.ui.deleteWorker(workerToDelete), reset}" on-cancel="reset">
				<model>
					<action name="reset" on-thread="ANY">workerToDelete=null</action>
				</model>
				<label value="`Are you sure you want to delete this worker? This cannot be undone.`" />
			</confirm>
			<table rows="app.workers" active-value-name="worker" selection="app.selectedWorker">
				<column name="`Name`" value="worker.getName()">
					<column-edit column-edit-value-name="newName" commit="worker.setName(newName)">
						<text-field />
					</column-edit>
				</column>
				<column name="`Points`" value="worker.getExcessPoints()" />
				<column name="`Level`" value="worker.getLevel()">
					<column-edit column-edit-value-name="newLevel" commit="worker.setLevel(newLevel)">
						<text-field />
					</column-edit>
				</column>
				<column name="`Labels`" value="worker.getLabels()" column-value-name="labels">
					<label value="labels" format="QuickChores.LABELS_FORMAT" />
					<column-edit column-edit-value-name="newLabels" commit="app.ui.replace(worker.getLabels(), newLabels)">
						<text-field format="QuickChores.LABELS_FORMAT" />
					</column-edit>
				</column>
				<multi-value-action icon="`/icons/add.png$16x16`" allow-for-empty="true">create</multi-value-action>
				<value-action icon="`/icons/remove.png$16x16`" value-name="worker">workerToDelete=worker</value-action>
			</table>
			<box visible="app.selectedWorker!=null" layout="inline-layout" orientation="horizontal">
				<model>
					<value name="points" type="int" />
					<action name="_doWork">app.ui.reportWork(app.selectedWorker, app.selectedJob, points)</action>
					<transform name="doWork" source="_doWork">
						<disable with="app.selectedJob==null ? `No job selected` : null" />
						<disable with="points==0 ? `Enter points` : null" />
					</transform>
				</model>
				<label>Freelance:</label>
				<text-field value="points" columns="4" />
				<combo value="app.selectedJob" values="app.jobs" />
				<button action="doWork">`Report Work`</button>
			</box>
			<tabs visible="app.selectedWorker!=null">
				<table tab-id="`usage`" tab-name="`Point Usage`" rows="resources" active-value-name="resource">
					<model>
						<transform name="resources" source="app.resources">
							<refresh on="app.selectedWorker" />
						</transform>
						<field-value name="usagePoints" source="app.usage.observe(new BiTuple&lt;>(app.selectedWorker, resource))"
							target-as="newUsage" save="app.usage.put(new BiTuple&lt;>(app.selectedWorker, resource), newUsage)" />
					</model>
					<column name="`Resource`" value="resource.getName()">
						<column-edit column-edit-value-name="newName" commit="resource.setName(newName)">
							<text-field />
						</column-edit>
					</column>
					<column name="`Redeem Points`" value="usagePoints">
						<column-edit column-edit-value-name="newUsage" commit="usagePoints=newUsage">
							<text-field />
						</column-edit>
					</column>
					<column name="`For`" value="usagePoints*resource.getRate()" column-value-name="usageAmount">
						<model>
							<value name="absUsage">Math.abs(usageAmount)</value>
							<value name="withUnit">app.ui.renderWithUnit(usageAmount, resource.getUnit())</value>
						</model>
						<label value="usagePoints==0 ? `` : withUnit" />
						<column-edit column-edit-value-name="newUsage" commit="usagePoints=(int) Math.round(newUsage/resource.getRate())">
							<text-field />
						</column-edit>
					</column>
					<column name="``" value="null">
						<model>
							<action name="_renderAction">null</action>
							<transform name="renderAction" source="_renderAction">
								<disable with="usagePoints==null || usagePoints==0 ? `Enter points to redeem` : null" />
							</transform>
						</model>
						<button action="renderAction">usagePoints&lt;0 ? `Refund Points` : `Redeem Points`</button>
						<column-edit column-edit-value-name="newNull" commit="null" clicks="0"
							editable-if="usagePoints!=null &amp; usagePoints!=0">
							<button action="app.ui.usePoints(app.selectedWorker, resource, usagePoints)">`Redeem Points`</button>
						</column-edit>
					</column>
				</table>
				<table tab-id="`history`" tab-name="`History`" rows="historyItems" active-value-name="history">
					<model>
						<list name="_historyItems" type="PointHistory">app.selectedWorker.getPointHistory().getValues()</list>
						<transform name="historyItems" source="_historyItems">
							<sort sort-value-as="h" ascending="false">
								<sort-by>h.getTime()</sort-by>
							</sort>
						</transform>
						<value name="sourceItem">app.ui.getHistorySource(history)</value>
						<value name="unit">sourceItem instanceof PointResource ? ((PointResource) sourceItem).getUnit() : null</value>
						<value name="historyToDelete" type="PointHistory" />
					</model>
					<confirm visible="historyToDelete!=null" title="`Remove History?`" on-confirm="{
							app.selectedWorker.getPointHistory().getValues().remove(historyToDelete),
							app.selectedWorker.setExcessPoints(app.selectedWorker.getExcessPoints()-historyToDelete.getPointChange()),
							historyToDelete=null
						}" on-cancel="historyToDelete=null">
						<label value="`Are you sure you want to undo this piece of the worker's history?`" />
					</confirm>
					<column name="`Time`" value="history.getTime()" />
					<column name="`Type`" value="history.getChangeType()" column-value-name="type">
						<model>
							<transform name="typeName" source="type">
								<switch default="type.toString()">
									<return case="Job">`Job Done`</return>
									<return case="Redemption">history.getPointChange()>0 ? `Points Earned` : `Points Redeemed`</return>
								</switch>
							</transform>
						</model>
					</column>
					<column name="`Job/Resource`" value="sourceItem.getName()" />
					<column name="`Amount`" value="app.ui.renderWithUnit(history.getQuantity(), unit)" />
					<column name="`Points Before`" value="history.getBeforePoints()" />
					<column name="`Point Change`" value="history.getPointChange()" />
					<column name="`Points After`" value="history.getBeforePoints()+history.getPointChange()" />
					<value-action icon="`/icons/remove.png$16x16`" value-name="h">historyToDelete=h</value-action>
				</table>
			</tabs>
		</box>
		<super-table tab-id="`jobs`" tab-name="`Jobs`" rows="app.jobs" selection="app.selectedJob" active-value-name="job">
			<model>
				<value name="jobToDelete" type="Job" />
				<action name="create" always-enabled="true">app.selectedJob=app.ui.createJob()</action>
			</model>
			<confirm visible="jobToDelete!=null" title="`Delete Job '`+jobToDelete.getName()+`?`"
				on-confirm="{app.ui.deleteJob(jobToDelete), reset}" on-cancel="reset">
				<model>
					<action name="reset" on-thread="ANY">jobToDelete=null</action>
				</model>
				<label value="`Are you sure you want to delete this job? This cannot be undone.`" />
			</confirm>
			<column name="`Name`" value="job.getName()">
				<column-edit column-edit-value-name="newName" commit="job.setName(newName)">
					<text-field />
				</column-edit>
			</column>
			<column name="`Points`" value="job.getDifficulty()">
				<column-edit column-edit-value-name="newDifficulty" commit="job.setDifficulty(newDifficulty)">
					<text-field />
				</column-edit>
			</column>
			<column name="`Assigned`" value="assignedStr">
				<model>
					<list name="assignments" type="AssignedJob">app.currentJobAssignments.get(job)</list>
					<transform name="assigned" source="assignments">
						<map-to source-as="assn">assn.getWorker().getName()</map-to>
					</transform>
					<value name="_assignedStr">assigned.toString()</value>
					<value name="assignedStr">_assignedStr.substring(1, _assignedStr.length()-1)</value>
				</model>
			</column>
			<column name="`Last Done`" value="job.getLastDone()" />
			<column name="`Min Level`" value="job.getMinLevel()">
				<column-edit column-edit-value-name="newLevel" commit="job.setMinLevel(newLevel)">
					<text-field />
				</column-edit>
			</column>
			<column name="`Max Level`" value="job.getMaxLevel()">
				<column-edit column-edit-value-name="newLevel" commit="job.setMaxLevel(newLevel)">
					<text-field />
				</column-edit>
			</column>
			<column name="`Inclusion Labels`" value="job.getInclusionLabels()" column-value-name="labels">
				<label value="labels" format="QuickChores.LABELS_FORMAT" />
				<column-edit column-edit-value-name="newLabels" commit="app.ui.replace(job.getInclusionLabels(), newLabels)">
					<text-field format="QuickChores.LABELS_FORMAT" />
				</column-edit>
			</column>
			<column name="`Exclusion Labels`" value="job.getExclusionLabels()" column-value-name="labels">
				<label value="labels" format="QuickChores.LABELS_FORMAT" />
				<column-edit column-edit-value-name="newLabels" commit="app.ui.replace(job.getExclusionLabels(), newLabels)">
					<text-field format="QuickChores.LABELS_FORMAT" />
				</column-edit>
			</column>
			<multi-value-action icon="`/icons/add.png$16x16`" allow-for-empty="true">create</multi-value-action>
			<value-action icon="`/icons/remove.png$16x16`" value-name="job">jobToDelete=job</value-action>
		</super-table>
		<table tab-id="`resources`" tab-name="`Resources`" rows="app.resources" active-value-name="resource" selection="app.selectedResource">
			<model>
				<value name="resourceToDelete" type="PointResource" />
				<action name="create" always-enabled="true">app.selectedResource=app.ui.createResource()</action>
			</model>
			<confirm visible="resourceToDelete!=null" title="`Delete Resource '`+resourceToDelete.getName()+`?`"
				on-confirm="{data.resources.remove(resourceToDelete), reset}" on-cancel="reset">
				<model>
					<action name="reset" on-thread="ANY">resourceToDelete=null</action>
				</model>
				<label value="`Are you sure you want to delete this resource? This cannot be undone.`" />
			</confirm>
			<column name="`Name`" value="resource.getName()">
				<column-edit column-edit-value-name="newName" commit="resource.setName(newName)">
					<text-field />
				</column-edit>
			</column>
			<column name="`Rate`" value="resource.getRate()">
				<column-edit column-edit-value-name="newRate" commit="resource.setRate(newRate)">
					<text-field />
				</column-edit>
			</column>
			<column name="`Unit`" value="resource.getUnit()">
				<column-edit column-edit-value-name="newUnit" commit="resource.setUnit(newUnit)">
					<text-field />
				</column-edit>
			</column>
			<multi-value-action icon="`/icons/add.png$16x16`" allow-for-empty="true">create</multi-value-action>
			<value-action icon="`/icons/remove.png$16x16`" value-name="resource">resourceToDelete=resource</value-action>
		</table>
	</tabs>
</quick>
