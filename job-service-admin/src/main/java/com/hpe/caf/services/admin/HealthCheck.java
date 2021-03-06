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
package com.hpe.caf.services.admin;

import com.google.gson.Gson;
import com.hpe.caf.services.configuration.AppConfigProvider;
import com.hpe.caf.services.db.client.DatabaseConnectionProvider;
import com.hpe.caf.util.rabbitmq.RabbitUtil;
import com.rabbitmq.client.Channel;
import net.jodah.lyra.ConnectionOptions;
import net.jodah.lyra.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class HealthCheck extends HttpServlet
{

    private final static Logger LOG = LoggerFactory.getLogger(HealthCheck.class);

    @Override
    public void doGet(final HttpServletRequest req, final HttpServletResponse res) throws IOException
    {

        //  Construct response payload.
        final Map<String, Map<String, String>> statusResponseMap = new HashMap<>();

        // Health check that the DB and RabbitMQ can be contacted
        final boolean isDBHealthy = performDBHealthCheck(statusResponseMap);
        final boolean isRabbitMQHealthy = performRabbitMQHealthCheck(statusResponseMap);
        final boolean isHealthy = isDBHealthy && isRabbitMQHealthy;

        final Gson gson = new Gson();
        final String responseBody = gson.toJson(statusResponseMap);

        //  Get response body bytes.
        final byte[] responseBodyBytes = responseBody.getBytes(StandardCharsets.UTF_8);

        //  Set content type and length.
        res.setContentType("application/json");
        res.setContentLength(responseBodyBytes.length);

        //  Add CacheControl header to specify directives for caching mechanisms.
        res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        //  Set status code.
        if (isHealthy) {
            res.setStatus(200);
        } else {
            res.setStatus(500);
        }

        //  Output response body.
        try (ServletOutputStream out = res.getOutputStream())
        {
            out.write(responseBodyBytes);
            out.flush();
        }
    }

    private boolean performRabbitMQHealthCheck(final Map<String, Map<String, String>> statusResponseMap) throws IOException
    {
        LOG.debug("RabbitMQ Health Check: Starting...");

        final com.rabbitmq.client.Connection conn;
        final Channel channel;

        // Attempt to create a connection and channel to RabbitMQ. If an error occurs update the statueResponseMap and
        // return.
        try {
            conn = createConnection();
            channel = conn.createChannel();
        } catch (IOException | TimeoutException e) {
            LOG.error("RabbitMQ Health Check: Unhealthy, " + e.toString());
            return updateStatusResponseWithHealthOfComponent(statusResponseMap, false, e.toString(), "queue");
        }

        // Attempt to create a open a connection and channel to RabbitMQ. If an error occurs update the
        // statueResponseMap and return.
        try {
            if (!conn.isOpen()) {
                LOG.error("RabbitMQ Health Check: Unhealthy, unable to open connection");
                return updateStatusResponseWithHealthOfComponent(statusResponseMap, false,
                        "Attempt to open connection to RabbitMQ failed", "queue");
            } else if (!channel.isOpen()) {
                LOG.error("RabbitMQ Health Check: Unhealthy, unable to open channel");
                return updateStatusResponseWithHealthOfComponent(statusResponseMap, false,
                        "Attempt to open channel to RabbitMQ failed", "queue");
            }
        } catch (final Exception e) {
            LOG.error("RabbitMQ Health Check: Unhealthy, " + e.toString());
            return updateStatusResponseWithHealthOfComponent(statusResponseMap, false, e.toString(), "queue");
        } finally {
            conn.close();
        }

        // There where no issues in attempting to create and open a connection and channel to RabbitMQ.
        return updateStatusResponseWithHealthOfComponent(statusResponseMap, true, null, "queue");
    }

    private static boolean updateStatusResponseWithHealthOfComponent(
            final Map<String, Map<String, String>> statusResponseMap, final boolean isHealthy, final String message,
            final String component)
    {
        final Map<String, String> healthMap = new HashMap<>();
        if (isHealthy) {
            healthMap.put("healthy", "true");
        } else {
            healthMap.put("healthy", "false");
        }
        if (message != null) {
            healthMap.put("message", message);
        }
        statusResponseMap.put(component, healthMap);
        return isHealthy;
    }

    private static com.rabbitmq.client.Connection createConnection() throws IOException, TimeoutException
    {
        final ConnectionOptions lyraOpts = RabbitUtil.createLyraConnectionOptions(System.getenv("CAF_RABBITMQ_HOST"),
                Integer.parseInt(System.getenv("CAF_RABBITMQ_PORT")),
                System.getenv("CAF_RABBITMQ_USERNAME"),
                System.getenv("CAF_RABBITMQ_PASSWORD"));
        final Config lyraConfig = RabbitUtil.createLyraConfig(1, 30, -1);
        return RabbitUtil.createRabbitConnection(lyraOpts, lyraConfig);
    }

    private boolean performDBHealthCheck(final Map<String, Map<String, String>> statusResponseMap)
    {
        LOG.debug("Database Health Check: Starting...");
        try (final Connection conn = DatabaseConnectionProvider.getConnection(
                AppConfigProvider.getAppConfigProperties())) {

            LOG.debug("Database Health Check: Attempting to Contact Database");
            final Statement stmt = conn.createStatement();
            stmt.execute("SELECT 1");

            LOG.debug("Database Health Check: Healthy");
            return updateStatusResponseWithHealthOfComponent(statusResponseMap, true, null, "database");
        } catch (final Exception e) {
            LOG.error("Database Health Check: Unhealthy : " + e.toString());
            return updateStatusResponseWithHealthOfComponent(statusResponseMap, false, e.toString(), "database");
        }
    }
}
