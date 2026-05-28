/*
 * Copyright (c) 2018-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.testresults.model;

public class TestHandleLeakDetail extends TestLeakDetail
{
    double handles;

    public TestHandleLeakDetail() {
    }

    public TestHandleLeakDetail(int testRunId, String name, String type, double handles) {
        setTestRunId(testRunId);
        setTestName(name);
        setType(type);
        this.handles = handles;
    }

    public double getHandles()
    {
        return handles;
    }

    public void setHandles(double handles)
    {
        this.handles = handles;
    }
}
