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

import com.hpe.caf.services.db.client.DatabaseConnectionProvider;
import com.hpe.caf.services.job.api.generated.model.Failure;
import com.hpe.caf.services.job.api.generated.model.Job;
import com.hpe.caf.services.configuration.AppConfig;
import com.hpe.caf.services.job.exceptions.BadRequestException;
import com.hpe.caf.services.job.exceptions.NotFoundException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The DatabaseHelper class is responsible for database operations.
 */
public final class DatabaseHelper
{
    private static final String FAILURE_PROPERTY_MISSING = "Unknown";

    private static AppConfig appConfig;

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseHelper.class);

    /**
     * Instantiates a new DBUtil
     *
     * @param appConfig PostgreSQL database connection properties incl url (i.e. "jdbc:postgresql://PostgreSQLHost:portNumber/databaseName"), username and password
     */
    public DatabaseHelper(AppConfig appConfig)
    {
        DatabaseHelper.appConfig = appConfig;
    }

    /**
     * Returns a list of job definitions in the system.
     */
    public Job[] getJobs(String jobIdStartsWith, String statusType, Integer limit, Integer offset) throws Exception {

        List<Job> jobs=new ArrayList<>();

        try (
                Connection conn = DatabaseConnectionProvider.getConnection(appConfig);
                CallableStatement stmt = conn.prepareCall("{call get_jobs(?,?,?,?)}")
        ) {
            if (jobIdStartsWith == null) {
                jobIdStartsWith = "";
            }
            if (statusType == null) {
                statusType = "";
            }
            if (limit == null) {
                limit = 0;
            }
            if (offset == null) {
                offset = 0;
            }
            stmt.setString(1, jobIdStartsWith);
            stmt.setString(2, statusType);
            stmt.setInt(3, limit);
            stmt.setInt(4, offset);

            //  Execute a query to return a list of all job definitions in the system.
            LOG.debug("Calling get_jobs() database function...");
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Job job = new Job();
                job.setId(rs.getString("job_id"));
                job.setName(rs.getString("name"));
                job.setDescription(rs.getString("description"));
                job.setExternalData(rs.getString("data"));
                job.setCreateTime(getDate(rs.getString("create_date")));
                job.setStatus(Job.StatusEnum.valueOf(rs.getString("status").toUpperCase()));
                job.setPercentageComplete(rs.getFloat("percentage_complete"));

                //  Parse JSON failure sub-strings.
                String failureDetails = rs.getString("failure_details");
                if (ApiServiceUtil.isNotNullOrEmpty(failureDetails)) {
                    job.setFailures(getFailuresAsList(failureDetails));
                }

                jobs.add(job);
            }
            rs.close();
        }

        //  Convert arraylist to array of jobs.
        Job[] jobArr = new Job[jobs.size()];
        jobArr = jobs.toArray(jobArr);

        return jobArr;
    }

    /**
     * Returns the number of job definitions in the system.
     */
    public long getJobsCount(String jobIdStartsWith, String statusType) throws Exception {

        long jobsCount = 0;

        try (
                Connection conn = DatabaseConnectionProvider.getConnection(appConfig);
                CallableStatement stmt = conn.prepareCall("{call get_jobs_count(?,?)}")
        ) {
            if (jobIdStartsWith == null) {
                jobIdStartsWith = "";
            }
            if (statusType == null) {
                statusType = "";
            }
            stmt.setString(1, jobIdStartsWith);
            stmt.setString(2, statusType);

            //  Execute a query to return a count of all job definitions in the system.
            LOG.debug("Calling get_jobs_count() database function...");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                jobsCount = rs.getLong(1);
            }

            rs.close();
        }

        return jobsCount;
    }

    /**
     * Returns the job definition for the specified job.
     */
    public Job getJob(String jobId) throws Exception {

        Job job = null;

        try (
                Connection conn = DatabaseConnectionProvider.getConnection(appConfig);
                CallableStatement stmt = conn.prepareCall("{call get_job(?)}")
        ) {
            stmt.setString(1,jobId);

            //  Execute a query to return a list of all job definitions in the system.
            LOG.debug("Calling get_job() database function...");
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                job = new Job();
                job.setId(rs.getString("job_id"));
                job.setName(rs.getString("name"));
                job.setDescription(rs.getString("description"));
                job.setExternalData(rs.getString("data"));
                job.setCreateTime(getDate(rs.getString("create_date")));
                job.setStatus(Job.StatusEnum.valueOf(rs.getString("status").toUpperCase()));
                job.setPercentageComplete(rs.getFloat("percentage_complete"));

                //  Parse JSON failure sub-strings.
                String failureDetails = rs.getString("failure_details");
                if (ApiServiceUtil.isNotNullOrEmpty(failureDetails)) {
                    job.setFailures(getFailuresAsList(failureDetails));
                }
            }
            rs.close();
        } catch (SQLException se) {
            //  Determine source of SQL exception and throw appropriate error.
            String sqlState = se.getSQLState();

            switch (sqlState) {
                case "02000":
                    //  Job id has not been provided.
                    throw new BadRequestException(se.getMessage());
                case "P0002":
                    //  No data found for the specified job id.
                    throw new NotFoundException(se.getMessage());
                default:
                    throw se;
            }
        }

        return job;
    }

    /**
     * Creates the specified job.
     */
    public void createJob(String jobId, String name, String description, String data, int jobHash) throws Exception {

        try (
                Connection conn = DatabaseConnectionProvider.getConnection(appConfig);
                CallableStatement stmt = conn.prepareCall("{call create_job(?,?,?,?,?)}")
        ) {
            stmt.setString(1,jobId);
            stmt.setString(2,name);
            stmt.setString(3,description);
            stmt.setString(4,data);
            stmt.setInt(5,jobHash);

            LOG.debug("Calling create_job() database function...");
            stmt.execute();
        } catch (SQLException se) {
            //  Determine source of SQL exception and throw appropriate error.
            String sqlState = se.getSQLState();

            if (sqlState.equals("02000")) {
                //  Job id has not been provided.
                throw new BadRequestException(se.getMessage());
            }
            else {
                throw se;
            }
        }

    }

    /**
     * Creates the specified job.
     */
    public void createJobWithDependencies(final String jobId, final String name, final String description,
                                          final String data, final int jobHash, final String taskClassifier,
                                          final int taskApiVersion, final byte[] taskData, final String taskPipe,
                                          final String targetPipe, final List<String> prerequisiteJobIds,
                                          final int delay) throws Exception {

        try (
                Connection conn = DatabaseConnectionProvider.getConnection(appConfig);
                CallableStatement stmt = conn.prepareCall("{call create_job(?,?,?,?,?,?,?,?,?,?,?,?)}")
        ) {
            final String[] prerequisiteJobIdStringArray = prerequisiteJobIds.toArray(new String[prerequisiteJobIds.size()]);
            Array prerequisiteJobIdSQLArray = conn.createArrayOf("varchar", prerequisiteJobIdStringArray);

            stmt.setString(1,jobId);
            stmt.setString(2,name);
            stmt.setString(3,description);
            stmt.setString(4,data);
            stmt.setInt(5,jobHash);
            stmt.setString(6,taskClassifier);
            stmt.setInt(7,taskApiVersion);
            stmt.setBytes(8,taskData);
            stmt.setString(9,taskPipe);
            stmt.setString(10,targetPipe);
            stmt.setArray(11,prerequisiteJobIdSQLArray);
            stmt.setInt(12,delay);

            LOG.debug("Calling create_job() database function...");
            stmt.execute();
        } catch (SQLException se) {
            //  Determine source of SQL exception and throw appropriate error.
            String sqlState = se.getSQLState();

            if (sqlState.equals("02000")) {
                //  Job id has not been provided.
                throw new BadRequestException(se.getMessage());
            }
            else {
                throw se;
            }
        }

    }

    /**
     * Deletes the specified job.
     */
    public void deleteJob(String jobId) throws Exception {

        try (
                Connection conn = DatabaseConnectionProvider.getConnection(appConfig);
                CallableStatement stmt = conn.prepareCall("{call delete_job(?)}")
        ) {
            stmt.setString(1,jobId);
            LOG.debug("Calling delete_job() database function...");
            stmt.execute();
        } catch (SQLException se) {
            //  Determine source of SQL exception and throw appropriate error.
            String sqlState = se.getSQLState();

            switch (sqlState) {
                case "02000":
                    //  Job id has not been provided.
                    throw new BadRequestException(se.getMessage());
                case "P0002":
                    //  No data found for the specified job id.
                    throw new NotFoundException(se.getMessage());
                default:
                    throw se;
            }
        }
    }

    /**
     * Check if a matching job identifier with the specified hash already exists.
     */
    public boolean doesJobAlreadyExist(String jobId, int jobHash) throws Exception {

        boolean exists = false;

        String rowExistsSQL = "select 1 as rowExists from job where job_id = ? and job_hash=?";

        try (
                Connection conn = DatabaseConnectionProvider.getConnection(appConfig);
                PreparedStatement stmt = conn.prepareStatement(rowExistsSQL)
        ) {
            stmt.setString(1, jobId);
            stmt.setLong(2, jobHash);

            //  Execute a query to determine if a matching job row already exists.
            LOG.debug("Checking if a row in the job table with a matching job_id and job_hash already exists...");
            ResultSet rs = stmt.executeQuery();
            if(rs.next()){
                exists = rs.getInt("rowExists") > 0;
            }
        }

        return exists;
    }

    /**
     * Returns TRUE if the specified job id can be progressed, otherwise FALSE.
     */
    public boolean canJobBeProgressed(final String jobId) throws Exception
    {

        boolean canBeProgressed = true;

        String rowExistsSQL = "select 1 as taskDataExists from job_task_data where job_id = ?";

        try (
                Connection conn = DatabaseConnectionProvider.getConnection(appConfig);
                PreparedStatement stmt = conn.prepareStatement(rowExistsSQL)
        ) {
            stmt.setString(1, jobId);

            //  Execute a query to determine if the specified job can be progressed.
            ResultSet rs = stmt.executeQuery();
            if(rs.next()){
                canBeProgressed = rs.getInt("taskDataExists") != 1;
            }
        }

        return canBeProgressed;
    }

    /**
     * Returns TRUE if the specified job id is active, otherwise FALSE.
     */
    public boolean isJobActive(String jobId) throws Exception {

        boolean active = false;

        String rowExistsSQL = "select 1 as isActive from job where job_id = ? and status IN ('Active', 'Waiting')";

        try (
                Connection conn = DatabaseConnectionProvider.getConnection(appConfig);
                PreparedStatement stmt = conn.prepareStatement(rowExistsSQL)
        ) {
            stmt.setString(1, jobId);

            //  Execute a query to determine if the specified job is active or not.
            LOG.debug("Checking if the job is active...");
            ResultSet rs = stmt.executeQuery();
            if(rs.next()){
                active = rs.getInt("isActive") > 0;
            }
        }

        return active;
    }

    /**
     * Cancels the specified job.
     */
    public void cancelJob(String jobId) throws Exception {

        try (
                Connection conn = DatabaseConnectionProvider.getConnection(appConfig);
                CallableStatement stmt = conn.prepareCall("{call cancel_job(?)}")
        ) {
            stmt.setString(1,jobId);
            LOG.debug("Calling cancel_job() database function...");
            stmt.execute();
        } catch (SQLException se) {
            //  Determine source of SQL exception and throw appropriate error.
            String sqlState = se.getSQLState();

            switch (sqlState) {
                case "02000":
                    //  Job id has not been provided.
                    throw new BadRequestException(se.getMessage());
                case "P0002":
                    //  No data found for the specified job id.
                    throw new NotFoundException(se.getMessage());
                default:
                    throw se;
            }
        }
    }

    /**
     * Creates the specified job.
     */
    public void reportFailure(String jobId, String failureDetails) throws Exception {

        try (
                Connection conn = DatabaseConnectionProvider.getConnection(appConfig);
                CallableStatement stmt = conn.prepareCall("{call report_failure(?,?)}")
        ) {
            stmt.setString(1,jobId);
            stmt.setString(2,failureDetails);

            LOG.debug("Calling report_failure() database function...");
            stmt.execute();
        }

    }

    /**
     * Parses the failure details string returned from the database and returns as a list.
     */
    private static List<Failure> getFailuresAsList (String failureDetails) throws Exception {

        List<Failure> failures = new ArrayList<>();

        //  Split on newline character.
        for (String failure: failureDetails.split("\\r?\\n")){
            if (failure.startsWith("{")) {
                JSONObject jFailure = new JSONObject(failure);
                Failure f = new Failure();
                f.setFailureId(jFailure.getString("failureId"));
                f.setFailureTime(getDate(jFailure.getString("failureTime")));
                f.failureSource(jFailure.getString("failureSource"));
                f.failureMessage(jFailure.getString("failureMessage"));
                failures.add(f);
            } else {
                //  Valid failure JSON not detected.
                Failure f = new Failure();
                f.setFailureId(FAILURE_PROPERTY_MISSING);
                f.setFailureTime(new Date());
                f.failureSource(FAILURE_PROPERTY_MISSING);
                f.failureMessage(failure);
                failures.add(f);
            }
        }

        return failures;

    }

    /**
     * Returns java.util.date from a string.
     */
    private static Date getDate(String dateString) throws ParseException {

        Instant instant = Instant.parse ( dateString );
        return java.util.Date.from( instant );

    }

}

