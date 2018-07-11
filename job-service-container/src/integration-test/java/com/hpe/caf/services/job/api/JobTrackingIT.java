/*
 * Copyright 2015-2018 Micro Focus or one of its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hpe.caf.services.job.api;

import com.hpe.caf.services.job.client.ApiClient;
import com.hpe.caf.services.job.client.api.JobsApi;
import com.hpe.caf.services.job.client.model.Job;
import com.hpe.caf.services.job.client.model.NewJob;
import com.hpe.caf.services.job.client.model.WorkerAction;
import com.hpe.caf.worker.jobtracking.JobTrackingWorkerReporter;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.text.SimpleDateFormat;
import java.util.UUID;

public class JobTrackingIT {

    @Test
    public void outOfOrderProgressReportingTest () throws Exception {
        JobsApi jobsApi = createJobsApi();

        WorkerAction workerAction = new WorkerAction();
        workerAction.setTaskApiVersion(1);
        workerAction.setTaskClassifier("some-task-classifier");
        workerAction.setTargetPipe("some-target-pipe");
        workerAction.setTaskPipe("some-task-pipe");
        workerAction.setTaskData("some-task-data");

        NewJob newJob = new NewJob();
        newJob.setName("Some Task");
        newJob.setTask(workerAction);

        String jobId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        jobsApi.createOrUpdateJob(jobId, newJob, correlationId);

        JobTrackingWorkerReporter jobTrackingWorkerReporter = new JobTrackingWorkerReporter();

        jobTrackingWorkerReporter.reportJobTaskProgress(jobId + ".1",1);
        jobTrackingWorkerReporter.reportJobTaskProgress(jobId + ".1",100);
        jobTrackingWorkerReporter.reportJobTaskProgress(jobId + ".1",2);
        jobTrackingWorkerReporter.reportJobTaskProgress(jobId + ".1",3);

        Job job = jobsApi.getJob(jobId, correlationId);

        Assert.assertEquals(job.getPercentageComplete(), 100);
    }

    private static JobsApi createJobsApi() {
        String connectionString = System.getenv("webserviceurl");;
        ApiClient client = new ApiClient();
        client.setBasePath(connectionString);
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        client.setDateFormat(f);
        return new JobsApi(client);
    }
}
