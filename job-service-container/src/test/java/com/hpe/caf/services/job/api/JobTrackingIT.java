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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class JobTrackingIT {

    @Test(enabled = true)
    public void reportProgresOnCompletedJobTest() throws Exception {
        System.out.println("--- Starting 'reportProgresOnCompletedJobTest'...");
        final JobsApi jobsApi = createJobsApi();

        final WorkerAction workerAction = new WorkerAction();
        workerAction.setTaskApiVersion(1);
        workerAction.setTaskClassifier("some-task-classifier");
        workerAction.setTargetPipe("some-target-pipe");
        workerAction.setTaskPipe("some-task-pipe");
        workerAction.setTaskData("some-task-data");

        final NewJob newJob = new NewJob();
        newJob.setName("Some Task");
        newJob.setTask(workerAction);

        final String jobId = UUID.randomUUID().toString();
        final String correlationId = UUID.randomUUID().toString();
        jobsApi.createOrUpdateJob(jobId, newJob, correlationId);
        System.out.println("---'reportProgresOnCompletedJobTest' New Job created for task: " + jobId);

        final JobTrackingWorkerReporter jobTrackingWorkerReporter = new JobTrackingWorkerReporter();

        final ConcurrentLinkedQueue<String> updates = new ConcurrentLinkedQueue<>();
        for (int index = 0; index < 5; index++) {
            updates.add(jobId + "." + 1 + "*");
        }
        System.out.println("--- reportProgresOnCompletedJobTest '" + updates.size() + "' Updates to be sent for task: " + jobId);
        // Threads marking job as 'Active'
        final List<Thread> threads = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            Thread t = new Thread(() -> {
                try {
                    int totalUpdates = updates.size();
                    int numberOfUpdatesProcessed = 0;
                    while (numberOfUpdatesProcessed < totalUpdates) {
                        final String update = updates.remove();
                        jobTrackingWorkerReporter.reportJobTaskProgress(update, 0);
                        numberOfUpdatesProcessed++;
                        System.out.println("--- reportProgresOnCompletedJobTest '" + updates.size() + "' Updates remaining to be sent for task: " + jobId);
                    }
                } catch (final Exception ex) {
                    System.out.println("---'reportProgresOnCompletedJobTest' Exception reporting progress : "+ ex);
                    throw new RuntimeException("reportProgresOnCompletedJobTest", ex);
                }
            });
            threads.add(t);
        }
        System.out.println("--- reportProgresOnCompletedJobTest Starting threads to update task: " + jobId);
        for (Thread t : threads) {
            t.start();
        }

        Thread.sleep(5000);
        // Main thread marking final job task as 'Complete'
        try {
            System.out.println("--- reportProgresOnCompletedJobTest update final job task as 'Complete' for: " + jobId + "." + 1 + "*");
            jobTrackingWorkerReporter.reportJobTaskComplete(jobId + "." + 1 + "*");
        } catch (final Exception ex) {
            System.out.println("reportProgresOnCompletedJobTest Exception updating final task as 'Complete' : " + ex);
            return;
        }

        for (final Thread t : threads) {
            t.join();
        }

        System.out.println("---'reportProgresOnCompletedJobTest' Checking status of job : " + jobId);
        final Job job = jobsApi.getJob(jobId, correlationId);
        final Float complete = 100f;
        System.out.println("---'reportProgresOnCompletedJobTest' job: " + jobId + " Percentage Complete = " + job.getPercentageComplete());
        Assert.assertEquals(job.getPercentageComplete(), complete);
    }

    @Test(enabled = true)
    public void twoSubTaskReportProgressTest() throws Exception {
        System.out.println("---Start test 'twoSubTaskReportProgressTest' ...");
        final JobsApi jobsApi = createJobsApi();

        final WorkerAction workerAction = new WorkerAction();
        workerAction.setTaskApiVersion(1);
        workerAction.setTaskClassifier("some-task-classifier");
        workerAction.setTargetPipe("some-target-pipe");
        workerAction.setTaskPipe("some-task-pipe");
        workerAction.setTaskData("some-task-data");

        final NewJob newJob = new NewJob();
        newJob.setName("Some Task");
        newJob.setTask(workerAction);

        final String jobId = UUID.randomUUID().toString();
        final String correlationId = UUID.randomUUID().toString();
        jobsApi.createOrUpdateJob(jobId, newJob, correlationId);
        System.out.println("--- twoSubTaskReportProgressTest New Job created for task: " + jobId);

        final JobTrackingWorkerReporter jobTrackingWorkerReporter = new JobTrackingWorkerReporter();

        final ConcurrentLinkedQueue<String> updates = new ConcurrentLinkedQueue<>();
        for (int index = 0; index < 5; index++) {
            updates.add(jobId + "." + 1);
            updates.add(jobId + "." + 2 + "*");
        }
        System.out.println("--- twoSubTaskReportProgressTest '" + updates.size() + "' Updates to be sent for task: " + jobId);
        final List<Thread> threads = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            final Thread t = new Thread(() -> {
                try {
                    int totalUpdates = updates.size();
                    int numberOfUpdatesProcessed = 0;
                    while (numberOfUpdatesProcessed < totalUpdates) {
                        final String update = updates.remove();
                        jobTrackingWorkerReporter.reportJobTaskProgress(update, 0);
                        numberOfUpdatesProcessed++;
                        System.out.println("--- twoSubTaskReportProgressTest '" + updates.size() + "' Updates remaining to be sent for task: " + jobId);
                    }
                } catch(java.util.NoSuchElementException e)
                {
                } catch (final Exception ex) {
                    System.out.println("--twoSubTaskReportProgressTest Exception : " + ex);
                    throw new RuntimeException("twoSubTaskReportProgressTest", ex);
                }
            });
            threads.add(t);
        }
        System.out.println("--- twoSubTaskReportProgressTest Starting threads to update task: " + jobId);
        for (final Thread t : threads) {
            t.start();
        }

        //Thread.sleep(5000);
        try {
            System.out.println("--- twoSubTaskReportProgressTest Sending status 'Completed' for task: " + jobId + "." + 1);
            jobTrackingWorkerReporter.reportJobTaskComplete(jobId + "." + 1);

            
        } catch (final Exception ex) {
            System.out.println("--twoSubTaskReportProgressTest Sending status 'Completed' for task: " + jobId + "." + 1 + " : " + ex);
            return;
        }

        try {
            System.out.println("--- twoSubTaskReportProgressTest Sending status 'Completed' for task: " + jobId + "." + 2 + "*");
            jobTrackingWorkerReporter.reportJobTaskComplete(jobId + "." + 2 + "*");
        } catch (final Exception ex) {
            System.out.println("--twoSubTaskReportProgressTest Sending status 'Completed' for task: " + jobId + "."  + 2 + "* : " + ex);
            return;
        }

        for (final Thread t : threads) {
            t.join();
        }
        System.out.println("---'twoSubTaskReportProgressTest' Checking status of job : " + jobId);
        final Job job = jobsApi.getJob(jobId, correlationId);
        final Float complete = 100f;
        System.out.println("---'twoSubTaskReportProgressTest Job: " + jobId + " Percentage Complete = " + job.getPercentageComplete());
        Assert.assertEquals(job.getPercentageComplete(), complete);
    }

    @Test(enabled = true)
    public void test() throws Exception {
        System.out.println(
                "--- Starting 'test' to check job status updates when status messages are received are out of order");
        final JobsApi jobsApi = createJobsApi();

        final WorkerAction workerAction = new WorkerAction();
        workerAction.setTaskApiVersion(1);
        workerAction.setTaskClassifier("some-task-classifier");
        workerAction.setTargetPipe("some-target-pipe");
        workerAction.setTaskPipe("some-task-pipe");
        workerAction.setTaskData("some-task-data");

        final NewJob newJob = new NewJob();
        newJob.setName("Some Task");
        newJob.setTask(workerAction);

        final String jobId = UUID.randomUUID().toString();
        final String correlationId = UUID.randomUUID().toString();
        jobsApi.createOrUpdateJob(jobId, newJob, correlationId);
        System.out.println("---'test' New Job created for task: " + jobId);

        final JobTrackingWorkerReporter jobTrackingWorkerReporter = new JobTrackingWorkerReporter();
        System.out.println("---'test' Sending status update messages for job : " + jobId);
        jobTrackingWorkerReporter.reportJobTaskComplete(jobId + ".2*");
        jobTrackingWorkerReporter.reportJobTaskProgress(jobId + ".1", 0);
        jobTrackingWorkerReporter.reportJobTaskComplete(jobId + ".1");
        System.out.println("---'test' Checking status of job : " + jobId);
        final Job job = jobsApi.getJob(jobId, correlationId);
        final Float complete = 100f;
        System.out.println("---'test' Job: " + jobId + " Percentage Complete = " + job.getPercentageComplete());
        Assert.assertEquals(job.getPercentageComplete(), complete);
    }

    @Test(enabled = true)
    public void testUpdateInReverse() throws Exception {
        System.out.println(
                "--- Starting 'testUpdateInReverse' to check job status updates when status messages are received in reverse order");
        final JobsApi jobsApi = createJobsApi();

        final WorkerAction workerAction = new WorkerAction();
        workerAction.setTaskApiVersion(1);
        workerAction.setTaskClassifier("some-task-classifier");
        workerAction.setTargetPipe("some-target-pipe");
        workerAction.setTaskPipe("some-task-pipe");
        workerAction.setTaskData("some-task-data");

        final NewJob newJob = new NewJob();
        newJob.setName("Some Task");
        newJob.setTask(workerAction);

        final String jobId = UUID.randomUUID().toString();
        final String correlationId = UUID.randomUUID().toString();
        jobsApi.createOrUpdateJob(jobId, newJob, correlationId);
        System.out.println("---'testUpdateInReverse' New Job created for task: " + jobId);

        final JobTrackingWorkerReporter jobTrackingWorkerReporter = new JobTrackingWorkerReporter();
        System.out.println("---'testUpdateInReverse' Sending status update messages for job : " + jobId);
        jobTrackingWorkerReporter.reportJobTaskComplete(jobId + ".2*");
        jobTrackingWorkerReporter.reportJobTaskComplete(jobId + ".1");
        jobTrackingWorkerReporter.reportJobTaskProgress(jobId + ".1", 0);
        System.out.println("---'testUpdateInReverse' Checking status of job : " + jobId);
        final Job job = jobsApi.getJob(jobId, correlationId);
        final Float complete = 100f;
        System.out.println("---'testUpdateInReverse' Job: " + jobId + " Percentage Complete = " + job.getPercentageComplete());
        Assert.assertEquals(job.getPercentageComplete(), complete);
    }

    private static JobsApi createJobsApi() {
        final String connectionString = System.getenv("webserviceurl");
        final ApiClient client = new ApiClient();
        client.setBasePath(connectionString);
        final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        client.setDateFormat(f);
        return new JobsApi(client);
    }
}
