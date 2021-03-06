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
package com.hpe.caf.services.job.api.generated;

import com.hpe.caf.services.job.api.*;
import com.hpe.caf.services.job.exceptions.BadRequestException;
import com.hpe.caf.services.job.exceptions.NotFoundException;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-02-29T10:25:31.219Z")
public class JobStatsApiServiceImpl extends JobStatsApiService {

    @Override
    public Response getJobStatsCount(final String jobIdStartsWith, final String statusType, String cAFCorrelationId, SecurityContext securityContext)
            throws Exception {
        try {
            Long jobsCount = JobsStatsGetCount.getJobsCount(jobIdStartsWith, statusType);
            return Response.ok().entity(jobsCount).build();
        } catch (BadRequestException e){
            return Response.status(Response.Status.BAD_REQUEST).entity(new ApiResponseMessage(e.getMessage())).build();
        } catch (NotFoundException e){
            return Response.status(Response.Status.NOT_FOUND).entity(new ApiResponseMessage(e.getMessage())).build();
        } catch (Exception e){
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new ApiResponseMessage(e.getMessage())).build();
        }
    }

}
