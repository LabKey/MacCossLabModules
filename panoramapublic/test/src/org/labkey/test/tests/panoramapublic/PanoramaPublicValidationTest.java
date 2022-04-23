package org.labkey.test.tests.panoramapublic;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.pages.panoramapublic.DataValidationPage;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.TextSearcher;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 7)
public class PanoramaPublicValidationTest extends PanoramaPublicBaseTest
{
    private static final String SKY_FILE_1 = "Study9S_Site52_v1.sky.zip";
    private static final String WIFF_1 = "Site52_041009_Study9S_Phase-I.wiff";
    private static final String WIFF_SCAN_1 = WIFF_1 + ".scan";
    private static final String SKY_FILE_2 = "Olga_srm_course_heavy_light_w_maxquant_lib.sky.zip";
    private static final String WIFF_2 = "olgas_S130501_010_StC-DosR_C4.wiff";
    private static final String WIFF_SCAN_2 = WIFF_2 + ".scan";
    private static final String WIFF_3 = "olgas_S130501_009_StC-DosR_B4.wiff";
    private static final String WIFF_SCAN_3 = WIFF_3 + ".scan";
    private static final String SKY_FILE_3 = "heavy_light_spectrum_matches_missing_blib.zip";
    private static final String SKY_FILE_4 = "heavy_light_spectrum_matches_w_NIST_BSA.sky.zip";
    private static final String SKY_FILE_5 = "ambiguous_sample_files1.sky.zip";
    private static final String SKY_FILE_6 = "ambiguous_sample_files2.sky.zip";
    private static final String SKY_FILE_7 = "ambiguous_sample_files_replicates.sky.zip";
    private static final String AGILENT_DATA_1 = "SIS_Data 1.d";
    private static final String AGILENT_DATA_1_ZIP = AGILENT_DATA_1 + ".zip";
    private static final String AGILENT_DATA_2 = "File1 A1.d";
    private static final String AGILENT_DATA_2_ZIP = AGILENT_DATA_2 + ".zip";

    @Override
    public String getSampleDataFolder()
    {
        return PanoramaPublicTest.SAMPLEDATA_FOLDER;
    }

    @Test
    public void testValidation()
    {
        // Set up our source folder.
        String projectName = getProjectName();
        String folderName = "Folder 1";
        String experimentTitle = "This is an experiment to test data validation";
        setupSourceFolder(projectName, folderName, SUBMITTER);

        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");

        // Upload document
        int jobCount = 0;
        importData(SKY_FILE_1, ++jobCount);

        // Add the "Targeted MS Experiment" webpart
        createExperimentCompleteMetadata(experimentTitle);

        // Upload a document, not any raw files. Status should indicate that a PXD cannot be assigned.
        jobCount = verifyInvalidStatus(jobCount);

        // Upload missing raw files. The Carboxymethylcysteine modification in the document does not have a Unimod Id.
        // Status should indicate that data is valid for an "incomplete" PX submission.
        jobCount = uploadRawFilesVerifyInCompleteStatus(jobCount);

        // Save the Unimod match for Carboxymethylcysteine. Status should indicate that data is valid for a "complete" PX submission.
        saveUnimodMatchVerifyCompleteStatus();

        // Import another document and upload its raw files. This document has a library. Since we have not uploaded
        // any of the source files used to build the library, the status should indicate that the data can be
        // assigned a PXD but it will be marked as "incomplete data and/or metadata"
        jobCount = verifyIncompleteStatus(jobCount);

        // Import a file with a missing .blib in the .sky.zip.
        // Import a file with a NIST library. We only support BiblioSpec and EncyclopeDIA libraries so this should be marked as "INCOMPLETE"
        verifyMissingBlibAndUnsupportedLibrary(jobCount);
    }

    @Test
    public void testSampleFileValidation()
    {
        // Set up our source folder.
        String projectName = getProjectName();
        String folderName = "Folder 2";
        String experimentTitle = "This is an experiment to test sample file validation";
        setupSourceFolder(projectName, folderName, SUBMITTER);
        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");

        // Upload documents
        int jobCount = 0;
        importData(SKY_FILE_5, ++jobCount);
        importData(SKY_FILE_6, ++jobCount);
        importData(SKY_FILE_7, ++jobCount);

        // Add the "Targeted MS Experiment" webpart
        createExperimentCompleteMetadata(experimentTitle);

        // Upload raw data
        uploadRawFiles(AGILENT_DATA_1_ZIP, AGILENT_DATA_2_ZIP);

        // Run validation job and verify the results
        DataValidationPage validationPage = submitValidationJob();
        jobCount++;

        validationPage.verifyInvalidStatus();
        validationPage.verifySampleFileStatus(SKY_FILE_5,
                List.of(AGILENT_DATA_2), // File1 A1.d is imported into two documents from different paths but the
                                         // acquired times of the two files are the same. Will not be marked as "ambiguous".
                List.of("File1 A10.d"),  // Missing
                List.of(AGILENT_DATA_1)); // SIS_Data 1.d is imported from different paths into two Skyline documents.
                                          // The acquired times are also different in the two documents. This will be marked "ambiguous".
        validationPage.verifySampleFileStatus(SKY_FILE_6,
                Collections.emptyList(),
                List.of("File2 A1.d"),    // Missing
                List.of(AGILENT_DATA_1)); // Marked a ambiguous.
        validationPage.verifySampleFileStatus(SKY_FILE_7,
                List.of(AGILENT_DATA_2), // File1 A1.d also imported into another document from a different path. But not marked
                                         // as ambiguous since the acquired times of the two files are the same.
                List.of("File2 A1.d"),   // Missing
                List.of(AGILENT_DATA_1)); // Marked as ambiguous
    }

    private int verifyInvalidStatus(int jobCount)
    {
        var validationPage = submitValidationJob();
        jobCount++;
        validationPage.verifyInvalidStatus();
        validationPage.verifySampleFileStatus(SKY_FILE_1, Collections.emptyList(), List.of(WIFF_1, WIFF_SCAN_1));
        return jobCount;
    }

    private int uploadRawFilesVerifyInCompleteStatus(int jobCount)
    {
        // Upload the missing raw files
        uploadRawFiles(WIFF_1, WIFF_SCAN_1);
        // Run validation job and verify the results
        DataValidationPage validationPage = submitValidationJob();
        jobCount++;

        validationPage.verifyIncompleteStatus();
        validationPage.verifySampleFileStatus(SKY_FILE_1, List.of(WIFF_1, WIFF_SCAN_1), Collections.emptyList());
        return jobCount;
    }

    private void saveUnimodMatchVerifyCompleteStatus()
    {
        var validationPage = goToValidationDetails();

        // The Carboxymethylcysteine modification in the document does not have a Unimod Id. We should see the "Continue with an Incomplete PX Submission" button
        validationPage.verifyIncompleteStatus();
        validationPage.verifyModificationStatus("Carboxymethylcysteine", false, null, null);

        goToExperimentDetailsPage();

        DataRegionTable modsTable = new DataRegionTable("Structural Modifications",getDriver());
        assertEquals("Unexpected number of rows in Structural Modifications table", 1, modsTable.getDataRowCount());
        var row = modsTable.findRow(0);
        var findMatchLink = Locator.XPathLocator.tag("a").withText("Find Match").findElement(row);
        clickAndWait(findMatchLink);
        assertTextPresent("Unimod Match Options ");
        clickButton("Unimod Match");
        var unimodMatchWebPart = portalHelper.getBodyWebPart("Unimod Match");
        assertTextPresent(new TextSearcher(unimodMatchWebPart.getComponentElement().getText()),
                "The modification matches 1 Unimod modification", "Carboxymethyl", "UNIMOD:6");
        clickButton("Save Match");

        validationPage = goToValidationDetailsFromExpDetails();
        validationPage.verifyCompleteStatus();
        validationPage.verifyModificationStatus("Carboxymethylcysteine", true, "UNIMOD:6", "Carboxymethyl");
    }

    private DataValidationPage goToValidationDetails()
    {
        goToExperimentDetailsPage();
        return goToValidationDetailsFromExpDetails();
    }

    private DataValidationPage goToValidationDetailsFromExpDetails()
    {
        var validationSumaryWebPart = portalHelper.getBodyWebPart("Data Validation for ProteomeXchange");
        var detailsLink = Locator.XPathLocator.tag("a").withText("[Details]").findElement(validationSumaryWebPart);
        clickAndWait(detailsLink);
        return new DataValidationPage(this);
    }

    private int verifyIncompleteStatus(int jobCount)
    {
        importData(SKY_FILE_2, ++jobCount);
        uploadRawFiles(WIFF_2, WIFF_SCAN_2, WIFF_3, WIFF_SCAN_3);
        // Run validation job and verify the results
        DataValidationPage validationPage = submitValidationJob();
        jobCount++;
        validationPage.verifyIncompleteStatus();
        validationPage.verifySampleFileStatus(SKY_FILE_1, List.of(WIFF_1, WIFF_SCAN_1), Collections.emptyList());
        validationPage.verifySampleFileStatus(SKY_FILE_2, List.of(WIFF_2, WIFF_SCAN_2, WIFF_3, WIFF_SCAN_2), Collections.emptyList());

        // Verify library built with MaxQuant results. Expect to see modifications.xml, evidence.xml, mqpar.xml in the Peptide ID files list
        // even though these files are not named in the .blib
        validationPage.verifySpectralLibraryStatus("maxquant.blib", "104 KB", "Status: Missing spectrum and peptide Id files",
                List.of(SKY_FILE_2),
                Collections.emptyList(),
                List.of("BBM_332_P110_C04_PRM_007.raw", "BBM_332_P110_C04_PRM_006.raw", "BBM_332_P110_C04_PRM_005.raw", "BBM_332_P110_C04_PRM_004.raw", "BBM_332_P110_C04_PRM_003.raw"),
                Collections.emptyList(),
                List.of("modifications.xml", "evidence.txt", "mqpar.xml", "msms.txt"));

        // .blib does not have any source files in the SpectrumSourceFiles table.
        validationPage.verifySpectralLibraryStatus("Qtrap_DP-PA_cons_P0836.blib", "61 KB",
                "Status: Missing spectrum and peptide ID file names in the .blib file. Library may have been built with an older version of Skyline",
                List.of(SKY_FILE_2),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        return jobCount;
    }

    private int verifyMissingBlibAndUnsupportedLibrary(int jobCount)
    {
        // Import a document with a missing .blib (RasPhos_20170125.blib)
        importData(SKY_FILE_3, ++jobCount);
        // Import a document with a NIST library. This document also has RasPhos_20170125.blib but it is included in the .sky.zip
        importData(SKY_FILE_4, ++jobCount);
        DataValidationPage validationPage = submitValidationJob();
        jobCount++;
        validationPage.verifyInvalidStatus(); // We have missing sample files now, so data cannot be assigned a PXD.
        validationPage.verifySampleFileStatus(SKY_FILE_1, List.of(WIFF_1, WIFF_SCAN_1), Collections.emptyList());
        validationPage.verifySampleFileStatus(SKY_FILE_2, List.of(WIFF_2, WIFF_SCAN_2, WIFF_3, WIFF_SCAN_2), Collections.emptyList());
        validationPage.verifySampleFileStatus(SKY_FILE_3, Collections.emptyList(), List.of("QQ180201_RAS_mAbmix1_Site2_plate1_A9_Blank_01_01.raw"));
        validationPage.verifySampleFileStatus(SKY_FILE_4, Collections.emptyList(), List.of("QQ180201_RAS_mAbmix1_Site2_plate1_A9_Blank_01_01.raw"));

        // Verify library built with MaxQuant results. Expect to see modifications.xml, evidence.xml, mqpar.xml in the Peptide ID files list
        // even though these files are not named in the .blib
        validationPage.verifySpectralLibraryStatus("maxquant.blib", "104 KB", "Status: Missing spectrum and peptide Id files",
                List.of(SKY_FILE_2),
                Collections.emptyList(),
                List.of("BBM_332_P110_C04_PRM_007.raw", "BBM_332_P110_C04_PRM_006.raw", "BBM_332_P110_C04_PRM_005.raw", "BBM_332_P110_C04_PRM_004.raw", "BBM_332_P110_C04_PRM_003.raw"),
                Collections.emptyList(),
                List.of("modifications.xml", "evidence.txt", "mqpar.xml", "msms.txt"));

        // .blib does not have any source files in the SpectrumSourceFiles table.
        validationPage.verifySpectralLibraryStatus("Qtrap_DP-PA_cons_P0836.blib", "61 KB",
                "Status: Missing spectrum and peptide ID file names in the .blib file. Library may have been built with an older version of Skyline",
                List.of(SKY_FILE_2),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        // RasPhos_20170125.blib is used with both SKY_FILE_3 and  SKY_FILE_4
        // The .blib is missing in SKY_FILE_3. We expect to see two rows for this library since the library key includes the file size of the library
        validationPage.verifySpectralLibraryStatus("RasPhos_20170125.blib", "-",
                "Status: Library file missing from Skyline .zip",
                List.of(SKY_FILE_3),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        validationPage.verifySpectralLibraryStatus("RasPhos_20170125.blib", "216 KB",
                "Status: Missing spectrum files. Missing peptide ID file names in the .blib file. Library may have been built with an older version of Skyline",
                List.of(SKY_FILE_4),
                Collections.emptyList(), List.of("MY20170124_ARC_RasPhosHmix_500fmol_01.mzXML"), Collections.emptyList(), Collections.emptyList());

        // SKY_FILE_4 contains a NIST library.  We only support BiblioSpec and EncyclopeDIA libraries.  So this will be marked as "INCOMPLETE"
        validationPage.verifySpectralLibraryStatus("NIST_bsa_IT_2011-04-01.msp", "3 MB",
                "Status: Unsupported library type: nist",
                List.of(SKY_FILE_4),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        return jobCount;
    }

    private void uploadRawFiles(String... files)
    {
        portalHelper.click(Locator.folderTab("Raw Data"));
        for (String file: files)
        {
            _fileBrowserHelper.uploadFile(getSampleDataPath(file));
            _fileBrowserHelper.fileIsPresent(file);
        }
    }
}
