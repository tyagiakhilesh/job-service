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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.FileAssert.fail;

import com.hpe.caf.api.BootstrapConfiguration;
import com.hpe.caf.api.ConfigurationSource;
import com.hpe.caf.config.system.SystemBootstrapConfiguration;
import com.hpe.caf.naming.ServicePath;
import com.hpe.caf.services.job.client.ApiClient;
import com.hpe.caf.services.job.client.ApiException;
import com.hpe.caf.services.job.client.api.JobsApi;
import com.hpe.caf.services.job.client.model.Job;
import com.hpe.caf.services.job.client.model.NewJob;
import com.hpe.caf.services.job.client.model.WorkerAction;
import com.hpe.caf.worker.queue.rabbit.RabbitWorkerQueueConfiguration;
import com.hpe.caf.worker.testing.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * Integration tests for the functionality of the Job Service.
 * (Not an end to end integration test.)
 */
public class JobServiceIT {

    private String connectionString;
    private String projectId;
    private ApiClient client = new ApiClient();
    private JobsApi jobsApi;

    private static ServicePath servicePath;
    private static WorkerServices workerServices;
    private static ConfigurationSource configurationSource;
    private static RabbitWorkerQueueConfiguration rabbitConfiguration;
    private static String jobServiceOutputQueue;
    private static final long defaultTimeOutMs = 120000; // 2 minutes
    
    final HashMap<String,Object> testDataObjectMap = new HashMap<>();
    final HashMap<String,String> taskMessageParams = new HashMap<>();
    
    @BeforeTest
    public void setup() throws Exception {
        projectId = UUID.randomUUID().toString();
        connectionString = System.getenv("webserviceurl");
        
        //Populate maps for testing    
        taskMessageParams.put("datastorePartialReference", "sample-files");
        taskMessageParams.put("documentDataInputFolder", "/mnt/caf-datastore-root/sample-files");
        taskMessageParams.put("documentDataOutputFolder", "/mnt/bla");
        
        testDataObjectMap.put("taskClassifier", "*.txt");
        testDataObjectMap.put("batchType", "WorkerDocumentBatchPlugin");
        testDataObjectMap.put("taskMessageType", "DocumentWorkerTaskBuilder");
        testDataObjectMap.put("taskMessageParams", taskMessageParams);
        testDataObjectMap.put("targetPipe", "languageidentification-in");
        

        //set up client to connect to the web service running on docker, and call web methods from correct address.
        client.setBasePath(connectionString);

        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        client.setDateFormat(f);
        jobsApi = new JobsApi(client);

        BootstrapConfiguration bootstrap = new SystemBootstrapConfiguration();
        servicePath = bootstrap.getServicePath();
        workerServices = WorkerServices.getDefault();
        configurationSource = workerServices.getConfigurationSource();
        rabbitConfiguration = configurationSource.getConfiguration(RabbitWorkerQueueConfiguration.class);
        rabbitConfiguration.getRabbitConfiguration().setRabbitHost(SettingsProvider.defaultProvider.getSetting(SettingNames.dockerHostAddress));
        rabbitConfiguration.getRabbitConfiguration().setRabbitPort(Integer.parseInt(SettingsProvider.defaultProvider.getSetting(SettingNames.rabbitmqNodePort)));
    }

    @Test
    public void testHealthCheck() throws NoSuchAlgorithmException, KeyManagementException, IOException
    {
        final String getRequestUrl = SettingsProvider.defaultProvider.getSetting("healthcheckurl");
        final HttpGet request = new HttpGet(getRequestUrl);
        // Set up HttpClient
        final HttpClient httpClient = HttpClients.createDefault();

        System.out.println("Sending GET to HealthCheck url: " + getRequestUrl);
        final HttpResponse response = httpClient.execute(request);
        request.releaseConnection();

        if (response.getEntity() == null) {
            fail("There was no content returned from the HealthCheck HTTP Get Request");
        }

        final String expectedHealthCheckResponseContent =
                "{\"database\":{\"healthy\":\"true\"},\"queue\":{\"healthy\":\"true\"}}";
        assertEquals(IOUtils.toString(response.getEntity().getContent(), Charset.forName("UTF-8")),
                expectedHealthCheckResponseContent, "Expected HealthCheck response should match the actual response");

        System.out.println("Response code from the HealthCheck request: " + response.getStatusLine().getStatusCode());
        assertTrue(response.getStatusLine().getStatusCode() == 200);
    }

    @Test
    public void testCreateJob() throws ApiException {
        //create a job
        String jobId = UUID.randomUUID().toString();
        String jobName = "Job_" +jobId;
        String jobDesc = jobName +" Descriptive Text.";
        String jobCorrelationId = "1";
        String jobExternalData = jobName +" External data.";
        int taskApiVer = 1;

        //create a WorkerAction task
        WorkerAction workerActionTask = new WorkerAction();
        workerActionTask.setTaskClassifier(jobName +"_testCancelJob");
        workerActionTask.setTaskApiVersion(taskApiVer);
        workerActionTask.setTaskData(jobName +"_TaskClassifier Sample Test Task Data.");
        workerActionTask.setTaskDataEncoding(WorkerAction.TaskDataEncodingEnum.UTF8);
        workerActionTask.setTaskPipe("TaskQueue_" + jobId);
        workerActionTask.setTargetPipe("Queue_" +jobId);

        NewJob newJob = new NewJob();
        newJob.setName(jobName);
        newJob.setDescription(jobDesc);
        newJob.setExternalData(jobExternalData);
        newJob.setTask(workerActionTask);

        jobsApi.createOrUpdateJob(jobId, newJob, jobCorrelationId);

        //retrieve job using web method
        Job retrievedJob = jobsApi.getJob(jobId, jobCorrelationId);

        assertEquals(retrievedJob.getId(), jobId);
        assertEquals(retrievedJob.getName(), newJob.getName());
        assertEquals(retrievedJob.getDescription(), newJob.getDescription());
        assertEquals(retrievedJob.getExternalData(), newJob.getExternalData());
    }

    @Test
    public void testJobIsActive() throws ApiException {
        //create a job
        String jobId = UUID.randomUUID().toString();
        String jobName = "Job_" +jobId;
        String jobDesc = jobName +" Descriptive Text.";
        String jobCorrelationId = "1";
        String jobExternalData = jobName +" External data.";
        int taskApiVer = 1;

        //create a WorkerAction task
        WorkerAction workerActionTask = new WorkerAction();
        workerActionTask.setTaskClassifier(jobName +"_testCancelJob");
        workerActionTask.setTaskApiVersion(taskApiVer);
        workerActionTask.setTaskData(jobName +"_TaskClassifier Sample Test Task Data.");
        workerActionTask.setTaskDataEncoding(WorkerAction.TaskDataEncodingEnum.UTF8);
        workerActionTask.setTaskPipe("TaskQueue_" + jobId);
        workerActionTask.setTargetPipe("Queue_" +jobId);

        NewJob newJob = new NewJob();
        newJob.setName(jobName);
        newJob.setDescription(jobDesc);
        newJob.setExternalData(jobExternalData);
        newJob.setTask(workerActionTask);

        jobsApi.createOrUpdateJob(jobId, newJob, jobCorrelationId);

        // Check if job is active.
        boolean isActive = jobsApi.getJobActive(jobId, jobCorrelationId);

        // Job will be in a 'Waiting' state, which is assumed as being Active.
        assertTrue(isActive);
    }

    @Test
    public void testDeleteJob() throws ApiException {
        //create a job
        String jobId = UUID.randomUUID().toString();
        String jobName = "Job_" +jobId;
        String jobDesc = jobName +" Descriptive Text.";
        String jobCorrelationId = "1";
        String jobExternalData = jobName +" External data.";
        int taskApiVer = 1;
        String taskClassifier = jobName +"_testCancelJob";

        //create a WorkerAction task
        WorkerAction workerActionTask = new WorkerAction();
        workerActionTask.setTaskClassifier(jobName +"_testCancelJob");
        workerActionTask.setTaskApiVersion(taskApiVer);
        workerActionTask.setTaskData(jobName +"_TaskClassifier Sample Test Task Data.");
        workerActionTask.setTaskDataEncoding(WorkerAction.TaskDataEncodingEnum.UTF8);
        workerActionTask.setTaskPipe("TaskQueue_" + jobId);
        workerActionTask.setTargetPipe("Queue_" +jobId);

        NewJob newJob = new NewJob();
        newJob.setName(jobName);
        newJob.setDescription(jobDesc);
        newJob.setExternalData(jobExternalData);
        newJob.setTask(workerActionTask);

        jobsApi.createOrUpdateJob(jobId, newJob, jobCorrelationId);

        //make sure the job is there
        Job retrievedJob = jobsApi.getJob(jobId, jobCorrelationId);
        assertEquals(retrievedJob.getId(), jobId);

        //delete the job
        jobsApi.deleteJob(jobId, jobCorrelationId);

        //make sure the job does not exist
        try {
            jobsApi.getJob(jobId, jobCorrelationId).getDescription();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("\"message\":\"ERROR: job_id {" +jobId +"} not found"),
                    "Exception Message should return JobId not found");
        }

    }
    
    @Test
    public void testCreateJobWithTaskData_Object() throws ApiException {
        //create a job
        String jobId = UUID.randomUUID().toString();
        String jobName = "Job_" +jobId;
        String jobDesc = jobName +" Descriptive Text.";
        String jobCorrelationId = "1";
        String jobExternalData = jobName +" External data.";
        int taskApiVer = 1;

        //create a WorkerAction task
        WorkerAction workerActionTask = new WorkerAction();
        workerActionTask.setTaskClassifier(jobName +"_testObjectJob");
        workerActionTask.setTaskApiVersion(taskApiVer);
        workerActionTask.setTaskData(testDataObjectMap);
        workerActionTask.setTaskPipe("TaskQueue_" + jobId);
        workerActionTask.setTargetPipe("Queue_" +jobId);

        NewJob newJob = new NewJob();
        newJob.setName(jobName);
        newJob.setDescription(jobDesc);
        newJob.setExternalData(jobExternalData);
        newJob.setTask(workerActionTask);

        jobsApi.createOrUpdateJob(jobId, newJob, jobCorrelationId);
        
         //retrieve job using web method
        Job retrievedJob = jobsApi.getJob(jobId, jobCorrelationId);

        assertEquals(retrievedJob.getId(), jobId);
        assertEquals(retrievedJob.getName(), newJob.getName());
        assertEquals(retrievedJob.getDescription(), newJob.getDescription());
        assertEquals(retrievedJob.getExternalData(), newJob.getExternalData());
    }

    @Test
    public void testRetrieveMultipleJobs() throws ApiException {
        String randomUUID = UUID.randomUUID().toString();
        //to test functionality of returning Jobs based on cafCorrelationId
        for(int i=0; i<10; i++){
            String jobId = randomUUID +"_"+i;
            String jobName = "Job_"+randomUUID +"_"+i;
            String jobDesc = jobName +" Descriptive Text.";
            String jobCorrelationId = "100";
            String jobExternalData = jobName +" External data.";

            WorkerAction workerActionTask = new WorkerAction();
            workerActionTask.setTaskClassifier(jobName +"_TaskClassifier");
            workerActionTask.setTaskApiVersion(1);
            workerActionTask.setTaskData("Sample Test Task Data.");
            workerActionTask.setTaskDataEncoding(WorkerAction.TaskDataEncodingEnum.UTF8);
            workerActionTask.setTaskPipe("TaskQueue_" + randomUUID);
            workerActionTask.setTargetPipe("Queue_" +randomUUID);

            NewJob newJob = new NewJob();
            newJob.setName(jobName);
            newJob.setDescription(jobDesc);
            newJob.setExternalData(jobExternalData);
            newJob.setTask(workerActionTask);

            jobsApi.createOrUpdateJob(jobId, newJob, jobCorrelationId);
        }

        //retrieve the jobs
        List<Job> retrievedJobs = jobsApi.getJobs("100",null,null,null,null);

        //test to make sure at least the 10 jobs created are returned. Unable to filter by cafCorrelationID
        assertTrue(retrievedJobs.size()>=10);

        for(int i=0; i<10; i++) {
            String expectedId = randomUUID +"_" +i;
            String expectedName = "Job_" +randomUUID +"_" +i;
            String expectedDescription = expectedName + " Descriptive Text.";
            String expectedExternalData = expectedName +" External data.";
            //only assert if the job is one of the jobs created above (the getJobs returns ALL jobs)
            if(retrievedJobs.get(i).getId().equals(""+i)) {
                assertEquals(retrievedJobs.get(i).getId(), expectedId);
                assertEquals(retrievedJobs.get(i).getName(), expectedName);
                assertEquals(retrievedJobs.get(i).getDescription(), expectedDescription);
                assertEquals(retrievedJobs.get(i).getExternalData(), expectedExternalData);
            }
        }
    }

    @Test
    public void testCancelJob() throws ApiException {
        //create a job
        String jobId = UUID.randomUUID().toString();
        String jobName = "Job_" +jobId;
        String jobDesc = jobName +" Descriptive Text.";
        String jobCorrelationId = "1";
        String jobExternalData = jobName +" External data.";
        int taskApiVer = 1;

        //create a WorkerAction task
        WorkerAction workerActionTask = new WorkerAction();
        workerActionTask.setTaskClassifier(jobName +"_testCancelJob");
        workerActionTask.setTaskApiVersion(taskApiVer);
        workerActionTask.setTaskData(jobName +"_TaskClassifier Sample Test Task Data.");
        workerActionTask.setTaskDataEncoding(WorkerAction.TaskDataEncodingEnum.UTF8);
        workerActionTask.setTaskPipe("TaskQueue_" + jobId);
        workerActionTask.setTargetPipe("Queue_" +jobId);

        NewJob newJob = new NewJob();
        newJob.setName(jobName);
        newJob.setDescription(jobDesc);
        newJob.setExternalData(jobExternalData);
        newJob.setTask(workerActionTask);

        jobsApi.createOrUpdateJob(jobId, newJob, jobCorrelationId);

        jobsApi.cancelJob(jobId, jobCorrelationId);

        Job retrievedJob = jobsApi.getJob(jobId, jobCorrelationId);

        assertEquals(retrievedJob.getStatus(), Job.StatusEnum.CANCELLED);
    }

    /**
     * This test will create a job with tracking info, and using the jobservice client it will create a new job using the
     * appropriate web method. It then consumes the message from the queue and asserts the result is as expected.
     * @throws Exception
     */
    @Test
    public void testMessagesOnRabbit() throws Exception {
        //Create a job first
        String jobId = UUID.randomUUID().toString();
        System.out.println("JobID: " +jobId);
        String jobName = "Job_" +jobId;
        String jobDesc = jobName + " Descriptive Text.";
        String jobCorrelationId = "1";
        String jobExternalData = jobName +" External data.";
        String testQueue = "jobservice-test-input-1";
        String trackingToQueue = "tracking-to-queue";

        //create the worker action including target pipe
        WorkerAction workerActionTask = new WorkerAction();
        workerActionTask.setTaskClassifier(jobName + "_TaskClassifier");
        workerActionTask.setTaskApiVersion(1);
        workerActionTask.setTaskData(jobName + "_TaskClassifier Sample Test Task Data.");
        workerActionTask.setTaskDataEncoding(WorkerAction.TaskDataEncodingEnum.UTF8);
        workerActionTask.setTaskPipe(testQueue);
        workerActionTask.setTargetPipe(trackingToQueue);

        //create a job
        NewJob newJob = new NewJob();
        newJob.setName(jobName);
        newJob.setDescription(jobDesc);
        newJob.setExternalData(jobExternalData);
        newJob.setTask(workerActionTask);

        //get values of environment variables stored in the task message, making sure
        String statusCheckUrl = System.getenv("CAF_WEBSERVICE_URL");
        if(statusCheckUrl!=null) {
            statusCheckUrl = statusCheckUrl + "/jobs/" + jobId + "/isActive";
        } else {
            throw new Exception("CAF_WEBSERVICE_URL environment variable is null.");
        }

        String trackingPipe = System.getenv("CAF_TRACKING_PIPE");
        if(trackingPipe==null)
            throw new Exception("CAF_TRACKING_PIPE environment variable is null.");

        String statusCheckTime = System.getenv("CAF_STATUS_CHECK_TIME");
        if(statusCheckTime==null)
            throw new Exception("CAF_TRACKING_PIPE environment variable is null.");

        //create expectation object for comparing message on RabbitMQ
        JobServiceTrackingInfoExpectation expectation = new JobServiceTrackingInfoExpectation(jobId, statusCheckTime, statusCheckUrl,
                trackingPipe, trackingToQueue, true);

        testMessagesPutOnQueue(testQueue, expectation, jobId, newJob, jobCorrelationId);
    }

    public void testMessagesPutOnQueue(final String taskQueue, final JobServiceTrackingInfoExpectation expectation, String jobId, NewJob newJob, String jobCorrelationId) throws Exception {
        try (QueueManager queueManager = getQueueManager(taskQueue)) {
            ExecutionContext context = new ExecutionContext(false);
            context.initializeContext();
            Timer timer = getTimer(context);
            Thread thread = queueManager.start(new JobServiceOutputDeliveryHandler(context, expectation));

            //call web method to create the new job and put message on queue
            jobsApi.createOrUpdateJob(jobId, newJob, jobCorrelationId);

            TestResult result = context.getTestResult();
            assertTrue(result.isSuccess());
        }
    }

    private QueueManager getQueueManager(final String queueName) throws IOException, TimeoutException {
        //Test messages are published to the target pipe specified in the test (jobservice-test-input-1).
        //The test will consume these messages and assert that the results are as expected.
        QueueServices queueServices = QueueServicesFactory.create(rabbitConfiguration, queueName, workerServices.getCodec());
        boolean debugEnabled = SettingsProvider.defaultProvider.getBooleanSetting(SettingNames.createDebugMessage,false);
        return new QueueManager(queueServices, workerServices, debugEnabled);
    }

    private Timer getTimer(ExecutionContext context) {
        String timeoutSetting = SettingsProvider.defaultProvider.getSetting(SettingNames.timeOutMs);
        long timeout = timeoutSetting == null ? defaultTimeOutMs : Long.parseLong(timeoutSetting);
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                context.testRunsTimedOut();
            }
        }, timeout);
        return timer;
    }
}
