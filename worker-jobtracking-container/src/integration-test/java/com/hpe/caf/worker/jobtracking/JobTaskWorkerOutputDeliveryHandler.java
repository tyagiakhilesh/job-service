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
package com.hpe.caf.worker.jobtracking;

import com.hpe.caf.api.worker.TaskMessage;
import com.hpe.caf.api.worker.TrackingInfo;
import com.hpe.caf.worker.testing.ExecutionContext;
import com.hpe.caf.worker.testing.ResultHandler;
import com.hpe.caf.worker.testing.TestItem;

import java.text.MessageFormat;


public class JobTaskWorkerOutputDeliveryHandler implements ResultHandler {

    private JobDatabase database;
    private ExecutionContext context;
    private JobTrackingWorkerITExpectation expectation;

    public JobTaskWorkerOutputDeliveryHandler(JobDatabase database, ExecutionContext context, JobTrackingWorkerITExpectation expectation) {
        this.database = database;
        this.context = context;
        this.expectation = expectation;
    }

    @Override
    public void handleResult(TaskMessage taskMessage) {
        TrackingInfo tracking = taskMessage.getTracking();
        if (expectation.isTrackingInfoPresent() != (tracking != null)) {
            context.failed(new TestItem(taskMessage.getTaskId(), null, null),
                    MessageFormat.format(
                            "In the forwarded task message for job task {0}, expected tracking info to be {1} but it was {2}.",
                            expectation.getJobTaskId(),
                            expectation.isTrackingInfoPresent() ? "present" : "absent",
                            expectation.isTrackingInfoPresent() ? "absent" : "present"));
        }

        if (tracking != null) {
            String trackingJobTaskId = tracking.getJobTaskId();
            if (!expectation.getJobTaskId().equals(trackingJobTaskId)) {
                context.failed(new TestItem(taskMessage.getTaskId(), null, null),
                        MessageFormat.format(
                                "In the forwarded task message, expected job task ID {0} but found {1} in the tracking info.",
                                expectation.getJobTaskId(),
                                trackingJobTaskId));
            }
        }

        try {
            database.verifyJobStatus(expectation.getJobTaskId(), expectation.getJobReportingExpectation());
        } catch (Exception e) {
            context.failed(new TestItem(taskMessage.getTaskId(), null, null), e.getMessage());
        }

        context.finishedSuccessfully();
    }
}
