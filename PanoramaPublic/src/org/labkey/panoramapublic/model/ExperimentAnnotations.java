/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.targetedms.model;

import org.labkey.api.data.Container;

import java.util.Date;

/**
 * User: vsharma
 * Date: 2/18/13
 * Time: 3:42 PM
 */
public class ExperimentAnnotations
{
    private int _id;
    protected Container _container;
    private String _title;
    private String _experimentDescription;
    private String _sampleDescription;
    private String _organism;
    private String _instrument;
    private Boolean _spikeIn;
    private String _citation;
    private String _abstract;
    private String _publicationLink;
    private Date _created;
    private int _createdBy;

    public int getId()
    {
        return _id;
    }

    public void setId(int id)
    {
        _id = id;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public String getTitle()
    {
        return _title;
    }

    public void setTitle(String title)
    {
        _title = title;
    }

    public String getExperimentDescription()
    {
        return _experimentDescription;
    }

    public void setExperimentDescription(String experimentDescription)
    {
        _experimentDescription = experimentDescription;
    }

    public String getSampleDescription()
    {
        return _sampleDescription;
    }

    public void setSampleDescription(String sampleDescription)
    {
        _sampleDescription = sampleDescription;
    }

    public String getOrganism()
    {
        return _organism;
    }

    public void setOrganism(String organism)
    {
        _organism = organism;
    }

    public String getInstrument()
    {
        return _instrument;
    }

    public void setInstrument(String instrument)
    {
        _instrument = instrument;
    }

    public String getCitation()
    {
        return _citation;
    }

    public Boolean getSpikeIn()
    {
        return _spikeIn;
    }

    public void setSpikeIn(Boolean spikeIn)
    {
        _spikeIn = spikeIn;
    }

    public void setCitation(String citation)
    {
        _citation = citation;
    }

    public String getAbstract()
    {
        return _abstract;
    }

    public void setAbstract(String anAbstract)
    {
        _abstract = anAbstract;
    }

    public String getPublicationLink()
    {
        return _publicationLink;
    }

    public void setPublicationLink(String publicationLink)
    {
        _publicationLink = publicationLink;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        _createdBy = createdBy;
    }
}
