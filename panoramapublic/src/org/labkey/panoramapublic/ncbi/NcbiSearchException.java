/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.panoramapublic.ncbi;

/**
 * Lets a caller distinguish a failed NCBI request from a search that found nothing.
 * searchForPublication throws it when no publication was found and at least one request failed.
 */
public class NcbiSearchException extends RuntimeException
{
    private final boolean _allRequestsFailed;

    public NcbiSearchException(String message, Throwable cause)
    {
        super(message, cause);
        _allRequestsFailed = false;
    }

    public NcbiSearchException(String message)
    {
        this(message, false);
    }

    public NcbiSearchException(String message, boolean allRequestsFailed)
    {
        super(message);
        _allRequestsFailed = allRequestsFailed;
    }

    /**
     * @return true if every NCBI request for the search failed, which suggests NCBI is unavailable.
     */
    public boolean isAllRequestsFailed()
    {
        return _allRequestsFailed;
    }
}
