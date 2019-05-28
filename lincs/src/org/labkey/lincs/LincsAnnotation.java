/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.lincs;

/**
 * User: vsharma
 * Date: 12/8/2015
 * Time: 10:39 AM
 */
public class LincsAnnotation
{
    public static String PLATE_ANNOTATION = "det_plate";
    public static String PROVENANCE_CODE = "provenance_code";

    private String _name;
    private String _displayName;
    private boolean _advanced; // These annotations are displayed when the user clicks on "Show all annotations"
    private boolean _ignored;

    public LincsAnnotation() {}

    public LincsAnnotation(String name)
    {
        _name = name;
    }

    public LincsAnnotation(String name, String displayName, boolean advanced, boolean ignored)
    {
        this(name);
        _displayName = displayName;
        _advanced = advanced;
        _ignored = ignored;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getName()
    {
        return _name;
    }

    public String getDisplayName()
    {
        return _displayName;
    }

    public void setDisplayName(String displayName)
    {
        _displayName = displayName;
    }

    // Returns true if this annotation is displayed when the user clicks on "Show all annotations"
    public boolean isAdvanced()
    {
        return _advanced;
    }

    public void setAdvanced(boolean advanced)
    {
        _advanced = advanced;
    }

    public boolean isIgnored()
    {
        return _ignored;
    }

    public void setIgnored(boolean ignored)
    {
        _ignored = ignored;
    }
}
