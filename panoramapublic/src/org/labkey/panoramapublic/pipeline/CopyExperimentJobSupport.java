package org.labkey.targetedms.pipeline;

import org.labkey.targetedms.model.ExperimentAnnotations;
import org.labkey.targetedms.model.Journal;

import java.io.File;

/**
 * User: vsharma
 * Date: 8/28/2014
 * Time: 7:37 AM
 */
public interface CopyExperimentJobSupport
{
    ExperimentAnnotations getExpAnnotations();

    Journal getJournal();

    File getExportDir();

    File getImportDir();

    String getExportZipFileName();
}
