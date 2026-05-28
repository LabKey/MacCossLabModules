/*
 * Copyright (c) 2019-2026 LabKey Corporation
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
package org.labkey.lincs.psp;

public class LincsPspException extends Exception
{
    public static final String NO_PSP_CONFIG = "PSP endpoint configuration was not saved in this container. Skipping POST to PSP.";

    public LincsPspException(String message)
    {
        super(message);
    }

    public LincsPspException(String message, Throwable t)
    {
        super(message, t);
    }

    public LincsPspException(String message, String json)
    {
        super(buildMessage(message, json));
    }

    public LincsPspException(String message, String json, Throwable t)
    {
        super(buildMessage(message, json), t);
    }

    private static String buildMessage(String message, String json)
    {
        return message + ". Server returned JSON: " + json;
    }

    public boolean noSavedPspConfig()
    {
        return getMessage() != null && getMessage().contains(NO_PSP_CONFIG);
    }
}
