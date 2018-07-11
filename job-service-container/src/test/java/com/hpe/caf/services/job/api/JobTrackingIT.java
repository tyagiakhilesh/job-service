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

public class JobTrackingIT
{

    @Test(enabled=false)
    public void reportProgresOnCompletedJobTest() throws Exception
    {
        System.out.println("thread started!");
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
        System.out.println("New Job created for task: " + jobId);

        final JobTrackingWorkerReporter jobTrackingWorkerReporter = new JobTrackingWorkerReporter();

        final ConcurrentLinkedQueue<String> updates = new ConcurrentLinkedQueue<>();
        for(int index=0; index<10000; index++){
            updates.add(jobId + "." + 1 + "*");
        }
        
        final List<Thread> threads = new ArrayList<>();
        for(int index=0; index<10; index++){
            Thread t = new Thread(() -> {
               try{
                   String update = updates.poll();
                   while(update!=null){
                    jobTrackingWorkerReporter.reportJobTaskProgress(update, 0);                   
                   }
               }
               catch(final Exception ex){
                   System.out.println(ex);
                   throw new RuntimeException(ex);
               }
            });
            threads.add(t);
        }

        for(Thread t:threads){
            t.start();
        }
        
        Thread.sleep(5000);
        try{
            jobTrackingWorkerReporter.reportJobTaskComplete(jobId + "." + 1 + "*");
        }
        catch(final Exception ex){
            System.out.println(ex);
            return;
        }
        
        for(final Thread t:threads){
            t.join();
        }

        final Job job = jobsApi.getJob(jobId, correlationId);
        final Float complete = 100f;
        System.out.println("Percentage Complete = " + job.getPercentageComplete());
        Assert.assertEquals(job.getPercentageComplete(), complete);
    }
    
    @Test(enabled=true)
    public void twoSubTaskReportProgressTest() throws Exception
    {
        System.out.println("thread started!");
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
        System.out.println("New Job created for task: " + jobId);

        final JobTrackingWorkerReporter jobTrackingWorkerReporter = new JobTrackingWorkerReporter();

        final ConcurrentLinkedQueue<String> updates = new ConcurrentLinkedQueue<>();
        for(int index=0; index<10000; index++){
            updates.add(jobId + "." + 1);
            updates.add(jobId + "." + 2 + "*");
        }
        
        final List<Thread> threads = new ArrayList<>();
        for(int index=0; index<10; index++){
            final Thread t = new Thread(() -> {
               try{
                   final String update = updates.poll();
                   while(update!=null){
                    jobTrackingWorkerReporter.reportJobTaskProgress(update, 0);                   
                   }
               }
               catch(final Exception ex){
                   System.out.println(ex);
                   throw new RuntimeException(ex);
               }
            });
            threads.add(t);
        }

        for(final Thread t:threads){
            t.start();
        }
        
        Thread.sleep(5000);
        try{
            jobTrackingWorkerReporter.reportJobTaskComplete(jobId + "." + 1);
        }
        catch(final Exception ex){
            System.out.println(ex);
            return;
        }
        
        for(final Thread t:threads){
            t.join();
        }

        final Job job = jobsApi.getJob(jobId, correlationId);
        final Float complete = 100f;
        System.out.println("Percentage Complete = " + job.getPercentageComplete());
        Assert.assertEquals(job.getPercentageComplete(), complete);
    }
    
    @Test(enabled=false)
    public void test() throws Exception
    {
        System.out.println("thread started!");
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
        System.out.println("New Job created for task: " + jobId);

        final JobTrackingWorkerReporter jobTrackingWorkerReporter = new JobTrackingWorkerReporter();
        jobTrackingWorkerReporter.reportJobTaskComplete(jobId + ".2*");
        jobTrackingWorkerReporter.reportJobTaskProgress(jobId + ".1", 0);
        jobTrackingWorkerReporter.reportJobTaskComplete(jobId + ".1");
        final Job job = jobsApi.getJob(jobId, correlationId);
        final Float complete = 100f;
        System.out.println("Percentage Complete = " + job.getPercentageComplete());
        Assert.assertEquals(job.getPercentageComplete(), complete);
    }

    private static JobsApi createJobsApi()
    {
        final String connectionString = System.getenv("webserviceurl");
        final ApiClient client = new ApiClient();
        client.setBasePath(connectionString);
        final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        client.setDateFormat(f);
        return new JobsApi(client);
    }
}
