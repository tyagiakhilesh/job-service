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

import com.hpe.caf.services.configuration.AppConfig;
import com.hpe.caf.services.job.exceptions.BadRequestException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;

@RunWith(PowerMockRunner.class)
@PrepareForTest({JobsCancel.class, DatabaseHelper.class, AppConfig.class})
@PowerMockIgnore("javax.management.*")
public final class JobsCancelTest {

    @Mock
    private DatabaseHelper mockDatabaseHelper;

    @Before
    public void setup() throws Exception {
        //  Mock DatabaseHelper calls.
        Mockito.doNothing().when(mockDatabaseHelper).cancelJob(Mockito.anyString());
        PowerMockito.whenNew(DatabaseHelper.class).withArguments(Mockito.any()).thenReturn(mockDatabaseHelper);

        HashMap<String, String> newEnv  = new HashMap<>();
        newEnv.put("CAF_DATABASE_URL","testUrl");
        newEnv.put("CAF_DATABASE_USERNAME","testUserName");
        newEnv.put("CAF_DATABASE_PASSWORD","testPassword");
        TestUtil.setSystemEnvironmentFields(newEnv);
    }

    @Test
    public void testCancelJob_Success() throws Exception {
        //  Test successful run of job cancellation.
        JobsCancel.cancelJob("067e6162-3b6f-4ae2-a171-2470b63dff00");

        Mockito.verify(mockDatabaseHelper, Mockito.times(1)).cancelJob(Mockito.anyString());
    }

    @Test(expected = BadRequestException.class)
    public void testCancelJob_Failure_EmptyJobId() throws Exception {
        //  Test failed run of job cancellation with empty job id.
        JobsCancel.cancelJob("");

        Mockito.verify(mockDatabaseHelper, Mockito.times(0)).cancelJob(Mockito.anyString());
    }

    @Test(expected = BadRequestException.class)
    public void testCancelJob_Failure_InvalidJobId_Period() throws Exception {
        //  Test failed run of job cancellation with job id containing invalid characters.
        JobsCancel.cancelJob("067e6162-3b6f-4ae2-a171-2470b.3dff00");

        Mockito.verify(mockDatabaseHelper, Mockito.times(0)).cancelJob(Mockito.anyString());
    }

    @Test(expected = BadRequestException.class)
    public void testCancelJob_Failure_InvalidJobId_Asterisk() throws Exception {
        //  Test failed run of job cancellation with job id containing invalid characters.
        JobsCancel.cancelJob("067e6162-3b6f-4ae2-a171-2470b*3dff00");

        Mockito.verify(mockDatabaseHelper, Mockito.times(0)).cancelJob(Mockito.anyString());
    }

}
