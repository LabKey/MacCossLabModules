/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.panoramapublic.model.validation;

public enum PxStatus
{
    NotValid("Data is not valid for a ProteomeXchange submission"),
    Incomplete("Incomplete Submission"), // “Unsupported dataset by repository” (MS:1002857)
    IncompleteMetadata("Incomplete data and/or metadata"), // "supported by repository but incomplete data and/or metadata" (MS:1003087)
    Complete("Complete"); // "Supported dataset by repository" (MS:1002856)

    private final String _label;

    PxStatus(String label)
    {
        _label = label;
    }

    public String getLabel()
    {
        return _label;
    }

    public boolean incompleteSubmission()
    {
        return ordinal() < Complete.ordinal();
    }

    public boolean invalidSubmission()
    {
        return ordinal() < IncompleteMetadata.ordinal();
    }
}
