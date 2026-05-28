/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.panoramapublic.speclib;

import org.jetbrains.annotations.NotNull;

public enum LibraryType
{
    bibliospec_lite("BiblioSpec", "blib", true),
    bibliospec("BiblioSpec", "blib", true),
    elib("EncyclopeDIA", "elib", true),
    hunter("X!Hunter", "mgf", false),
    midas("MIDAS", "midas", false),
    nist("NIST", "msp", false),
    spectrast("SpectraST", "sptxt", false),
    chromatogram("Panorama Chromatogram Library", "clib", false),
    unknown("Unknown Library Type", "unknown", false);

    private final String _name;
    private final String _extension;
    private final boolean _supported;

    LibraryType(String type, String extension, boolean supported)
    {
        _name = type;
        _extension = extension;
        _supported = supported;
    }

    public String getName()
    {
        return _name;
    }

    public String getExtension()
    {
        return _extension;
    }

    /**
     * @return true if the library can be read.
     */
    public boolean isSupported()
    {
        return _supported;
    }

    public static @NotNull LibraryType getType(String typeName)
    {
        try
        {
            return valueOf(typeName);
        }
        catch(IllegalArgumentException | NullPointerException e)
        {
            return unknown;
        }
    }
}
