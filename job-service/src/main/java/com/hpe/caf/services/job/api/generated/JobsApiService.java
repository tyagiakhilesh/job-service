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

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.hpe.caf.services.job.api.generated.model.NewJob;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-02-29T10:25:31.219Z")
public abstract class JobsApiService {

      public abstract Response getJobs(final String jobIdStartsWith, final String statusType, final Integer limit, final Integer offset, String cAFCorrelationId,SecurityContext securityContext)
              throws Exception;

      public abstract Response getJob(String jobId,String cAFCorrelationId,SecurityContext securityContext)
              throws Exception;

      public abstract Response createOrUpdateJob(String jobId,NewJob newJob,String cAFCorrelationId,SecurityContext securityContext,UriInfo uriInfo)
              throws Exception;

      public abstract Response deleteJob(String jobId,String cAFCorrelationId,SecurityContext securityContext)
              throws Exception;

      public abstract Response cancelJob(String jobId,String cAFCorrelationId,SecurityContext securityContext)
              throws Exception;

      public abstract Response getJobActive(String jobId,String cAFCorrelationId,SecurityContext securityContext)
              throws Exception;

}
