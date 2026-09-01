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

import org.jetbrains.annotations.Nullable;

/**
 * The outcome of checking an NCBI API key. A rejected key is a configuration problem an admin has to
 * correct. A check that could not be completed is not, so callers need to tell the two apart.
 */
public class NcbiApiKeyCheck
{
    public enum Status { VALID, REJECTED, UNCONFIRMED }

    private final Status _status;
    private final String _message;

    private NcbiApiKeyCheck(Status status, @Nullable String message)
    {
        _status = status;
        _message = message;
    }

    public static NcbiApiKeyCheck valid()
    {
        return new NcbiApiKeyCheck(Status.VALID, null);
    }

    public static NcbiApiKeyCheck rejected(@Nullable String message)
    {
        return new NcbiApiKeyCheck(Status.REJECTED, message);
    }

    public static NcbiApiKeyCheck unconfirmed(@Nullable String message)
    {
        return new NcbiApiKeyCheck(Status.UNCONFIRMED, message);
    }

    public Status getStatus()
    {
        return _status;
    }

    /**
     * @return NCBI's reason, with the API key removed. Null when the key was accepted.
     */
    public @Nullable String getMessage()
    {
        return _message;
    }

    public boolean isValid()
    {
        return _status == Status.VALID;
    }

    public boolean isRejected()
    {
        return _status == Status.REJECTED;
    }
}
