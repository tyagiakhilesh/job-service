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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.sql.*;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * Implementation of job reporting to a job-tracking Job Database, specifically supporting only JDBC/PostgreSQL connections.
 */
public class JobTrackingWorkerReporter implements JobTrackingReporter {

    private static final String JDBC_POSTGRESQL_PREFIX = "jdbc:postgresql:";
    private static final String JDBC_DRIVER = "org.postgresql.Driver";

    private static final String FAILED_TO_CONNECT = "Failed to connect to database {}. ";
    private static final String FAILED_TO_REPORT_COMPLETION = "Failed to report the completion of job task {0}. {1}";
    private static final String FAILED_TO_REPORT_REJECTION = "Failed to report the failure and rejection of job task {0}. {1}";

    private static final Logger LOG = LoggerFactory.getLogger(JobTrackingWorkerReporter.class);

    @NotNull
    @Size(min = 1)
    private String jobDatabaseURL;

    /**
     * The username to use when connecting to the Job Database.
     */
    @NotNull
    @Size(min = 1)
    private String jobDatabaseUsername;

    /**
     * The password to use with the configured username when connecting to the Job Database.
     */
    @NotNull
    @Size(min = 1)
    private String jobDatabasePassword;


    public JobTrackingWorkerReporter() throws JobReportingException {
        this.jobDatabaseURL = Objects.requireNonNull(JobDatabaseProperties.getDatabaseUrl()).toLowerCase();
        if (!jobDatabaseURL.startsWith(JDBC_POSTGRESQL_PREFIX))
        {
            throw new JobReportingException("Invalid database url string format - must start with jdbc:postgresql:");
        }
        this.jobDatabaseUsername = Objects.requireNonNull(JobDatabaseProperties.getDatabaseUsername());
        this.jobDatabasePassword = Objects.requireNonNull(JobDatabaseProperties.getDatabasePassword());

        try {
            LOG.debug("Registering JDBC driver \"{}\" ...", JDBC_DRIVER);
            Class.forName(JDBC_DRIVER);
        } catch (final Exception e){
            LOG.error("Failed to register JDBC driver \"{}\" ...", JDBC_DRIVER);
            throw new JobReportingException(MessageFormat.format("Failed to register JDBC driver \"{0}\". {1}", 
                                                                 JDBC_DRIVER, e.getMessage()), e);
        }
    }

    /**
     * Reports the percentage completed of the specified task id.
     *
     * @param jobTaskId identifies the job task whose progress is to be reported
     * @param estimatedPercentageCompleted an indication of progress on the job task
     * @throws JobReportingException
     */
    @Override
    public void reportJobTaskProgress(final String jobTaskId, final int estimatedPercentageCompleted) throws JobReportingException
    {
        LOG.trace("Recieved progress update message for task {}; taking no action", jobTaskId);
    }

    /**
     * Reports the specified job task as complete.
     *
     * @param jobTaskId identifies the completed job task
     * @return JobTrackingWorkerDependency list containing any dependent jobs that are now available for processing
     * @throws JobReportingException
     */
    @Override
    public List<JobTrackingWorkerDependency> reportJobTaskComplete(final String jobTaskId) throws JobReportingException
    {
        LOG.debug(Thread.currentThread() + ": Reporting completion of job task {}...", jobTaskId);

        final List<JobTrackingWorkerDependency> jobDependencyList = new ArrayList<>();

        try (final Connection conn = getConnection()) {
            try (final CallableStatement stmt = conn.prepareCall("{call report_complete(?)}")) {
                stmt.setString(1, jobTaskId);
                stmt.execute();

                final ResultSet resultSet = stmt.getResultSet();

                if (resultSet != null) {
                    while (resultSet.next()) {
                        final JobTrackingWorkerDependency dependency = new JobTrackingWorkerDependency();
                        dependency.setJobId(stmt.getResultSet().getString(1));
                        dependency.setTaskClassifier(stmt.getResultSet().getString(2));
                        dependency.setTaskApiVersion(stmt.getResultSet().getInt(3));
                        dependency.setTaskData(stmt.getResultSet().getBytes(4));
                        dependency.setTaskPipe(stmt.getResultSet().getString(5));
                        dependency.setTargetPipe(stmt.getResultSet().getString(6));

                        jobDependencyList.add(dependency);
                    }
                }

                return jobDependencyList;
            }
        } catch (final SQLTransientException te) {
            throw new JobReportingTransientException(
                MessageFormat.format(FAILED_TO_REPORT_COMPLETION, jobTaskId, te.getMessage()), te);
        } catch (final SQLException se) {
            throw new JobReportingException(
                MessageFormat.format(FAILED_TO_REPORT_COMPLETION, jobTaskId, se.getMessage()), se);
        }
    }

    /**
     * Reports the job task as failure and to be retried.
     *
     * @param jobTaskId identifies the failed job task
     * @param retryDetails an explanation of the retry of this job task
     * @throws JobReportingException
     */
    @Override
    public void reportJobTaskRetry(final String jobTaskId, final String retryDetails) throws JobReportingException
    {
        LOG.trace("Recieved retry report message for task {}; taking no action", jobTaskId);
    }

    /**
     * Reports the job task as a rejected task.
     *
     * @param jobTaskId identifies the rejected job task
     * @param rejectionDetails an explanation of the failure and rejection of the job task
     * @throws JobReportingException
     */
    @Override
    public void reportJobTaskRejected(final String jobTaskId, final JobTrackingWorkerFailure rejectionDetails)
        throws JobReportingException
    {
        LOG.info(Thread.currentThread() + ": Reporting failure of job task {} ...", jobTaskId);

        final String failureDetails = getFailureDetailsString(rejectionDetails);

        try (final Connection conn = getConnection()) {
            try (final CallableStatement stmt = conn.prepareCall("{call report_failure(?,?)}")) {
                stmt.setString(1, jobTaskId);
                stmt.setString(2, failureDetails);
                stmt.execute();
            }
        } catch (final SQLTransientException te) {
            throw new JobReportingTransientException(
                MessageFormat.format(FAILED_TO_REPORT_REJECTION, jobTaskId, te.getMessage()), te);
        } catch (final SQLException se) {
            throw new JobReportingException(
                MessageFormat.format(FAILED_TO_REPORT_REJECTION, jobTaskId, se.getMessage()), se);
        }
    }


    /**
     * Try to connect to the Job Database using the connection info provided in ctor.
     */
    @Override
    public boolean verifyJobDatabase() {
        try (Connection conn = getConnection()) {
        } catch (Exception e) {
            LOG.error("Failed to verify connection to the Job Database. ", e);
            return false;
        }
        return true;
    }


    /**
     * Creates a connection to the (PostgreSQL) Job Database.
     */
    private Connection getConnection() throws JobReportingException
    {
        LOG.debug("Connecting to database {} ...", jobDatabaseURL);

        final Properties connectionProps = new Properties();
        connectionProps.put("user", jobDatabaseUsername);
        connectionProps.put("password", jobDatabasePassword);

        try {
            return DriverManager.getConnection(jobDatabaseURL, connectionProps);
        } catch (final SQLTransientException ex) {
            LOG.error(FAILED_TO_CONNECT, jobDatabaseURL, ex);
            throw new JobReportingTransientException(ex.getMessage(), ex);
        } catch (final SQLException ex) {
            LOG.error(FAILED_TO_CONNECT, jobDatabaseURL, ex);

            // Declare error code for issues like not enough connections, memory, disk, etc.
            final String CONNECTION_EXCEPTION = "08";
            final String INSUFFICIENT_RESOURCES = "53";

            if (isSqlStateIn(ex, CONNECTION_EXCEPTION, INSUFFICIENT_RESOURCES)) {
                throw new JobReportingTransientException(ex.getMessage(), ex);
            } else {
                throw new JobReportingException(ex.getMessage(), ex);
            }
        }
    }

    private static String getFailureDetailsString(final JobTrackingWorkerFailure rejectionDetails) throws JobReportingException
    {
        final ObjectMapper mapper = new ObjectMapper();
        final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        mapper.setDateFormat(df);

        try {
            return mapper.writeValueAsString(rejectionDetails);
        } catch (JsonProcessingException ex) {
            throw new JobReportingException("Cannot serialize job task failure details.", ex);
        }
    }

    /**
     * Checks wheter the "SQLSTATE" is the specified error condition.
     * <p>
     * The SQL Error Code should be a 5 letter code - the first 2 characters denote the class of the error and the final 3 indicate the
     * specific condition.
     */
    private static boolean isSqlStateIn(final SQLException ex, final String... errorClasses)
    {
        final String sqlState = ex.getSQLState();

        if (sqlState == null || sqlState.length() != 5) {
            return false;
        }

        for (final String errorClass : errorClasses) {
            assert errorClass != null
                && errorClass.length() == 2;

            if (sqlState.startsWith(errorClass)) {
                return true;
            }
        }

        return false;
    }
}
