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
package org.labkey.test.tests.panoramapublic;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.components.panoramapublic.TargetedMsExperimentWebPart;
import org.labkey.test.pages.panoramapublic.DataValidationPage;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.TextSearcher;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    private static final String SKY_FILE_8 = "Study9S_Site52_v1_with_library.sky.zip";

    // Raw files used to build the maxquant.blib library used with Olga_srm_course_heavy_light_w_maxquant_lib.sky.zip
    private static final List<String> maxQuantLibRawSources = List.of("BBM_332_P110_C04_PRM_007.raw",
            "BBM_332_P110_C04_PRM_006.raw",
            "BBM_332_P110_C04_PRM_005.raw",
            "BBM_332_P110_C04_PRM_004.raw",
            "BBM_332_P110_C04_PRM_003.raw");
    // Peptide Id files used to build the maxquant.blib library used with Olga_srm_course_heavy_light_w_maxquant_lib.sky.zip
    private static final List<String> maxQuantLibPeptideIdSources = List.of("evidence.txt", "mqpar.xml", "msms.txt");

    private static final String SKY_FILE_9 = "Telomerase_HCMV_PRM_Skyline-NO-RESULTS.sky.zip";

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
        jobCount = uploadRawFilesVerifyInCompleteStatus(jobCount, SKY_FILE_1);

        // Save the Unimod match for Carboxymethylcysteine. This will make the validation job outdated. After running a new
        // validation job, status should indicate that data is valid for a "complete" PX submission.
        jobCount = saveUnimodMatchVerifyCompleteStatus(jobCount);

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
        uploadToRawFiles(AGILENT_DATA_1_ZIP, AGILENT_DATA_2_ZIP);

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

    @Test
    public void testValidationUpdate()
    {
        // Set up our source folder.
        String projectName = getProjectName();
        String folderName = "Folder 3";
        String experimentTitle = "This is a test for validation status getting updated on changes to modification and speclib info";
        setupSourceFolder(projectName, folderName, SUBMITTER);
        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");

        // Import Skyline document
        int jobCount = 0;
        importData(SKY_FILE_8, ++jobCount);

        // Add the "Targeted MS Experiment" webpart
        createExperimentCompleteMetadata(experimentTitle);

        // Upload raw data, submit and verify incomplete status
        uploadRawFilesVerifyInCompleteStatus(jobCount, SKY_FILE_8);

        // Save Unimod match for Carboxymethylcysteine
        saveUnimodMatchForCarboxymethylcysteine();
        goToValidationDetails().verifyIncompleteStatus();

        // Add information for the spectral library in the document so that the library validation is considered "complete"
        addSpecLibInfo("Source files unavailable", "Irrelevant to results", false);
        goToValidationDetails().verifyCompleteStatus();

        // Deleted the saved Unimod match
        deleteUnimodMatch();
        goToValidationDetails().verifyIncompleteStatus();
        saveUnimodMatchForCarboxymethylcysteine();
        goToValidationDetails().verifyCompleteStatus();

        // Edit the spectral library information so that it would no longer be considered "complete"
        addSpecLibInfo("Source files unavailable", "Used for choosing targets and fragments", true);
        goToValidationDetails().verifyIncompleteStatus();

        // Add information for the spectral library in the document so that the library validation is considered "complete"
        addSpecLibInfo("Source files unavailable", "Used only as supporting information", true);
        goToValidationDetails().verifyCompleteStatus();
    }

    @Test
    public void testLibraryValidationWithSubfolders()
    {
        // Set up our source folder.
        log("Creating experiment folder");
        String projectName = getProjectName();
        String folderName = "Library Validation With Subfolders";
        setupSourceFolder(projectName, folderName, SUBMITTER);
        log("Creating subfolder where Skyline documents will be uploaded");
        String subfolderName = "Skyline Documents Folder";
        setupSubfolder(projectName, folderName, subfolderName, FolderType.Experiment, SUBMITTER);

        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");

        String testFilesFolder = "LibraryTest-telomerasehcmvprm";
        String testSkyZip = testFilesFolder + "/" + SKY_FILE_9;

        // Upload document and raw data files
        log("Uploading and importing Skyline document " + testSkyZip + " into folder " + folderName + "/" + subfolderName);
        goToProjectFolder(projectName, folderName + "/" + subfolderName);
        importData(testSkyZip, 1);

        // Add the "Targeted MS Experiment" webpart
        log("Creating TargetedMS Experiment in folder " + folderName);
        goToProjectFolder(projectName, folderName);
        String experimentTitle = "This is an experiment to test validation of Bibliospec library source files";
        TargetedMsExperimentWebPart expWebPart = createExperimentCompleteMetadata(experimentTitle);
        // Include subfolders
        log("Including subfolders in experiment");
        goToDashboard();
        expWebPart.clickMoreDetails();
        clickButton("Include Subfolders");

        String libraryName = "test_library.blib";
        List<String> rawSources = List.of("20210719_SIRT-PRM_non-SIRT4_unsched_01.raw",
                "20210719_SIRT-PRM_non-SIRT4_unsched_02.raw",
                "20210719_SIRT-PRM_non-SIRT4_unsched_03.raw",
                "20210719_SIRT-PRM_non-SIRT4_unsched_04.raw",
                "20210719_SIRT-PRM_non-SIRT4_unsched_05.raw",
                "20210719_SIRT-PRM_SIRT4pep_unsched.raw");
        List<String> peptideIdSources = List.of("20210719_SIRT-PRM_non-SIRT4_unsched_01.msf",
                "20210719_SIRT-PRM_non-SIRT4_unsched_02.msf",
                "20210719_SIRT-PRM_non-SIRT4_unsched_03.msf",
                "20210719_SIRT-PRM_non-SIRT4_unsched_04.msf",
                "20210719_SIRT-PRM_non-SIRT4_unsched_05.msf",
                "20210719_SIRT-PRM_SIRT4pep_unsched.msf");

        goToDashboard();
        // Upload the source files used to build test_library.blib to the parent experiment folder.
        // Since spectral libraries can be used with multiple documents that may be uploaded to different subfolders,
        // we check all subfolders, as well as the parent experiment folder for library source files.
        log("Uploading library source files to parent experiment folder - " + folderName);
        uploadToRawFiles(rawSources.stream()
                .map(file -> testFilesFolder + "/" + file)
                .toArray(String[]::new));
        uploadToRawFiles(peptideIdSources.stream()
                .map(file -> testFilesFolder + "/" + file)
                .toArray(String[]::new));

        // Run validation job and verify the results
        log("Running data validation job; expect COMPLETE status");
        DataValidationPage validationPage = submitValidationJob();
        validationPage.verifyCompleteStatus();
        verifySpecLibSourceFiles(validationPage, libraryName, SKY_FILE_9, "100 KB", true, rawSources, peptideIdSources);

        // Delete the "RawFiles" folder in the parent experiment folder
        log("Deleting RawFiles directory from the parent experiment folder");
        goToModule("FileContent");
        _fileBrowserHelper.deleteFile("RawFiles");

        log("Running data validation job; expect INCOMPLETE status");
        validationPage = submitValidationJob();
        validationPage.verifyIncompleteStatus();
        verifySpecLibSourceFiles(validationPage, libraryName, SKY_FILE_9, "100 KB", false, rawSources, peptideIdSources);
    }

    @Test
    public void testDiannLibrarySources()
    {
        // Set up our source folder.
        log("Creating experiment folder");
        String projectName = getProjectName();
        String folderName = "Test DIA-NN Library Source Validation";
        setupSourceFolder(projectName, folderName, SUBMITTER);


        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");

        String skylineDoc = "DiaNNLibrary.sky.zip";
        String testFilesFolder = "LibraryTest-DiaNN";
        String testSkyZip = testFilesFolder + "/" + skylineDoc;

        // Upload document and raw data files
        log("Uploading and importing Skyline document " + testSkyZip + " into folder " + folderName);
        goToProjectFolder(projectName, folderName);
        importData(testSkyZip, 1);

        // Add the "Targeted MS Experiment" webpart
        log("Creating TargetedMS Experiment in folder " + folderName);
        goToProjectFolder(projectName, folderName);
        String experimentTitle = "This is an experiment to test validation of peptide Id source files for spectral library built with DIA-NN results";
        createExperimentCompleteMetadata(experimentTitle);

        String libraryName = "test_diann_library.blib";
        String librarySize = "132 KB";
        List<String> rawSources = List.of(
                "D0_rep1_DIA.mzML",
                "D0_rep2_DIA.mzML",
                "D2_rep2_DIA.mzML",
                "D4_rep2_DIA.mzML",
                "D6_rep1_DIA.mzML",
                "D8_rep1_DIA.mzML",
                "D8_rep2_DIA.mzML",
                "D10_rep2_DIA.mzML",
                "D11_rep2_DIA.mzML",
                "D2_rep1_DIA.mzML",
                "D6_rep2_DIA.mzML",
                "D10_rep1_DIA.mzML",
                "D11_rep1_DIA.mzML",
                "D12_rep1_DIA.mzML",
                "D4_rep1_DIA.mzML",
                "D12_rep2_DIA.mzML",
                "D14_rep1_DIA.mzML",
                "D14_rep2_DIA.mzML"
        );
        List<String> peptideIdSources = List.of("report-lib.parquet.skyline-for-test.speclib",
                "DIA-NN report file" // We don't know the name of report file.  This is a placeholder
        );

        goToDashboard();
        log("Running data validation job. All library sources should be \"Missing\" since source files have not been uploaded");
        DataValidationPage validationPage = submitValidationJob();
        validationPage.verifyInvalidStatus();
        verifySpecLibSourceFiles(validationPage, libraryName, skylineDoc, librarySize,
                Collections.emptyList(), rawSources,
                Collections.emptyList(), peptideIdSources);

        // Upload the .speclib file.
        log("Uploading file " + peptideIdSources.getFirst() + " to folder - " + folderName);
        uploadToRawFiles(testFilesFolder + "/" + peptideIdSources.getFirst());
        log("Running data validation job; " + peptideIdSources.get(0) + " uploaded");
        validationPage = submitValidationJob();
        verifySpecLibSourceFiles(validationPage, libraryName, skylineDoc, librarySize,
                Collections.emptyList(), rawSources,
                List.of(peptideIdSources.get(0)), List.of(peptideIdSources.get(1)));

        // Upload the Parquet files
        log("Uploading Parquet files");
        List<String> parquetFiles = List.of(
                "report.parquet",
                "report-lib-for-test.parquet"
        );
        uploadToRawFiles(parquetFiles.stream().map(file -> testFilesFolder + "/" + file).toArray(String[]::new));

        // Upload the TSV files
        log("Uploading TSV files");
        List<String> tsvFiles = List.of(
                "no-prefix-match-report-for-test.tsv",
                "report.tsv",
                "report-lib-for-test.tsv",
                "report-lib.parquet-missing-headers.tsv"
                );
        uploadToRawFiles(tsvFiles.stream().map(file -> testFilesFolder + "/" + file).toArray(String[]::new));

        log("Running data validation job; Parquet and TSV files uploaded");
        validationPage = submitValidationJob();
        verifySpecLibSourceFiles(validationPage, libraryName, skylineDoc, librarySize,
                Collections.emptyList(), rawSources,
                peptideIdSources, Collections.emptyList());
        // The validator will look for Parquet files first. Expect to see report-lib-for-test.parquet as the value in the "Path" column
        validationPage.verifyPeptideIdFilePath(peptideIdSources.get(1),
                "RawFiles" + File.separator + "report-lib-for-test.parquet",
                libraryName,librarySize);

        // Move the .speclib file to a subdirectory
        log("Creating subdirectory and moving speclib file");
        String subdir = "DIA-NN Results";
        goToRawDataTab();
        _fileBrowserHelper.createFolder(subdir);
        _fileBrowserHelper.moveFile(peptideIdSources.getFirst(), subdir);
        log("Running data validation job; Speclib file moved to subdirectory");
        validationPage = submitValidationJob();
        verifySpecLibSourceFiles(validationPage, libraryName, skylineDoc, librarySize,
                Collections.emptyList(), rawSources,
                List.of(peptideIdSources.get(0)), List.of(peptideIdSources.get(1)));
        String statusDetails = "The DIA-NN report file (.parquet or .tsv) must be in the same directory as the .speclib, and share some leading characters in the file name";
        validationPage.verifyLibrarySourceFileStatusDetails(peptideIdSources.get(1), libraryName, librarySize, true, statusDetails);

        // Move the TSV file to the same subdirectory as the .speclib file
        goToRawDataTab();
        _fileBrowserHelper.moveFile("report-lib-for-test.tsv", subdir);
        log("Running data validation job; Moved report TSV to subdirectory");
        validationPage = submitValidationJob();
        verifySpecLibSourceFiles(validationPage, libraryName, skylineDoc, librarySize,
                Collections.emptyList(), rawSources,
                peptideIdSources, Collections.emptyList());

        validationPage.verifyPeptideIdFilePath(peptideIdSources.get(1),
                "RawFiles" + File.separator + "DIA-NN Results" + File.separator + "report-lib-for-test.tsv",
                libraryName,librarySize);


        // Test the second library.
        // - This library is build with DIA-NN 2.0 results.
        // - The report file is a Parquet file (V2_report.parquet)
        // - The raw file names in SpectrumSourceFiles table of the .blib do not have extensions.
        //   Since the DIA-NN 2.0 output only includes the base file names of the raw files.
        //   The validator will look for any valid mass spec file that matches the given base file name.
        libraryName = "test_diann_V2_library.blib";
        librarySize = "1 MB";
        List<String> rawV2Sources = List.of(
                "B_240207_IO5x75_HeLa_400ng_5min_synchro_6x40_100ms_Slot2-1_1_7489",
                "B_240207_IO5x75_HeLa_400ng_5min_synchro_6x40_100ms_Slot2-1_1_7491"
        );
        List<String> peptideIdV2Sources = List.of("V2_report-lib.parquet.skyline.speclib",
                "DIA-NN report file" // We don't know the name of report file.  This is a placeholder
        );
        log("Running data validation.  All source files should be marked as missing.");
        verifySpecLibSourceFiles(validationPage, libraryName, skylineDoc, librarySize,
                Collections.emptyList(), rawV2Sources,
                Collections.emptyList(), peptideIdV2Sources);

        log("Uploading speclib, Parquet, and raw files");
        List<String> allLibraryFiles = List.of(
                "V2_report.parquet",
                "V2_report-lib.parquet.skyline.speclib",
                "B_240207_IO5x75_HeLa_400ng_5min_synchro_6x40_100ms_Slot2-1_1_7491.raw",
                "B_240207_IO5x75_HeLa_400ng_5min_synchro_6x40_100ms_Slot2-1_1_7489.raw"
        );
        uploadToRawFiles(allLibraryFiles.stream().map(file -> testFilesFolder + "/" + file).toArray(String[]::new));
        log("Running data validation job; Parquet and raw file have been uploaded.");
        validationPage = submitValidationJob();
        verifySpecLibSourceFiles(validationPage, libraryName, skylineDoc, librarySize,
                rawV2Sources, Collections.emptyList(),
                peptideIdV2Sources, Collections.emptyList());
        validationPage.verifyPeptideIdFilePath(peptideIdSources.get(1),
                "RawFiles" + File.separator + "V2_report.parquet",
                libraryName,librarySize);
    }

    private static void verifySpecLibSourceFiles(DataValidationPage validationPage, String libraryFileName, String skyZipName, String libraryFileSize,
                                                 boolean allSourcesFound, List<String> rawFiles, List<String> peptideIdFiles)
    {
        String expectedStatus = allSourcesFound ? null : "Missing spectrum and peptide Id files";
        validationPage.verifySpectralLibraryStatus(libraryFileName, libraryFileSize, expectedStatus,
                List.of(skyZipName),
                allSourcesFound ? rawFiles : Collections.emptyList(),
                !allSourcesFound ? rawFiles : Collections.emptyList(),
                allSourcesFound ? peptideIdFiles : Collections.emptyList(),
                !allSourcesFound ? peptideIdFiles : Collections.emptyList()
                );
    }

    private static void verifySpecLibSourceFiles(DataValidationPage validationPage, String libraryFileName, String skyZipName, String libraryFileSize,
                                                 List<String> rawFilesFound, List<String> rawFilesMissing,
                                                 List<String> peptideIdFilesFound, List<String> peptideIdFilesMissing)
    {
        StringJoiner statusJoiner = new StringJoiner(" and ");
        if (!rawFilesMissing.isEmpty()) {
            statusJoiner.add("spectrum");
        }
        if (!peptideIdFilesMissing.isEmpty()) {
            statusJoiner.add("peptide Id");
        }
        String expectedStatus = statusJoiner.length() > 0 ? "Missing " + statusJoiner + " files" : null;

        validationPage.verifySpectralLibraryStatus(libraryFileName, libraryFileSize, expectedStatus,
                List.of(skyZipName),
                rawFilesFound, rawFilesMissing,
                peptideIdFilesFound, peptideIdFilesMissing
        );
    }

    private void clickExperimentDetailsLink()
    {
        var exptDetailsLink = Locator.XPathLocator.tag("a").withText("[View Experiment Details]").findElement(getDriver());
        exptDetailsLink.click();
    }

    private void addSpecLibInfo(String sourceType, String dependencyType, boolean edit)
    {
        goToExperimentDetailsPage();

        String buttonText = edit ? "Edit" : "Add";

        DataRegionTable specLibsTable = new DataRegionTable("Spectral Libraries",getDriver());
        assertEquals("Unexpected number of rows in Spectral Libraries table", 1, specLibsTable.getDataRowCount());
        var cell = specLibsTable.findCell(0, specLibsTable.getColumnIndex("Library Info"));
        var addInfoLink = Locator.XPathLocator.tag("a").withText(buttonText).findElementOrNull(cell);
        assertNotNull("Expected link to " + buttonText + " spectral library info", addInfoLink);
        addInfoLink.click();

        _ext4Helper.selectComboBoxItem(Ext4Helper.Locators.formItemWithInputNamed("sourceType"), sourceType);
        _ext4Helper.selectComboBoxItem(Ext4Helper.Locators.formItemWithInputNamed("dependencyType"), dependencyType);
        clickAndWait(Ext4Helper.Locators.ext4Button("Save"));

        specLibsTable = new DataRegionTable("Spectral Libraries",getDriver());
        cell = specLibsTable.findCell(0, specLibsTable.getColumnIndex("Library Source"));
        assertEquals(sourceType, cell.getText());
        cell = specLibsTable.findCell(0, specLibsTable.getColumnIndex("Dependency Type"));
        assertEquals(dependencyType, cell.getText());
    }

    private void deleteSpecLibInfo()
    {
        goToExperimentDetailsPage();

        DataRegionTable specLibsTable = new DataRegionTable("Spectral Libraries",getDriver());
        assertEquals("Unexpected number of rows in Spectral Libraries table", 1, specLibsTable.getDataRowCount());
        var cell = specLibsTable.findCell(0, specLibsTable.getColumnIndex("Library Info"));
        var deleteLink = Locator.XPathLocator.tag("a").withText("Delete").findElementOrNull(cell);
        assertNotNull("Expected link to delete spectral library info", deleteLink);
        doAndWaitForPageToLoad(() -> {
            deleteLink.click();
            assertAlert("Are you sure you want to delete the spectral library information?");
        });

        specLibsTable = new DataRegionTable("Spectral Libraries",getDriver());
        cell = specLibsTable.findCell(0, specLibsTable.getColumnIndex("Library Source"));
        assertEquals("", cell.getText().trim());
        cell = specLibsTable.findCell(0, specLibsTable.getColumnIndex("Dependency Type"));
        assertEquals("", cell.getText().trim());
        cell = specLibsTable.findCell(0, specLibsTable.getColumnIndex("Library Info"));
        assertEquals("ADD", cell.getText());
    }

    private void deleteUnimodMatch()
    {
        goToExperimentDetailsPage();

        DataRegionTable modsTable = new DataRegionTable("Structural Modifications",getDriver());
        assertEquals("Unexpected number of rows in Structural Modifications table", 1, modsTable.getDataRowCount());
        var cell = modsTable.findCell(0, modsTable.getColumnIndex("UnimodMatch"));
        var deleteMatchLink = Locator.XPathLocator.tag("a").withText("Delete Match").findElementOrNull(cell);
        assertNotNull("Expected to see a Delete Match link for saved Unimod match", deleteMatchLink);
        doAndWaitForPageToLoad(() -> {
                    deleteMatchLink.click();
                    assertAlert("Are you sure you want to delete the saved Unimod information for modification 'Carboxymethylcysteine'?");
                });

        assertTextPresent("Unimod information for", "modification", "Carboxymethylcysteine", "was successfully deleted");
        clickExperimentDetailsLink();
        modsTable = new DataRegionTable("Structural Modifications",getDriver());
        cell = modsTable.findCell(0, modsTable.getColumnIndex("UnimodMatch"));
        assertTrue("Unimod information for the modification was not deleted", cell.getText().contains("FIND MATCH"));
    }

    private void verifyValidationOutdated()
    {
        goToExperimentDetailsPage();
        var outdatedMsg = "The latest validation results are outdated. Please click the button below to re-run validation.";

        var validationSummaryWebPart = portalHelper.getBodyWebPart("Data Validation for ProteomeXchange");
        var panelText = validationSummaryWebPart.getComponentElement().getText();
        assertTextPresent(new TextSearcher(panelText), outdatedMsg);

        var detailsLink = Locator.XPathLocator.tag("a").withText("[Details]").findElementOrNull(validationSummaryWebPart);
        assertNull("Unexpected link to view validation details", detailsLink);

        assertElementNotPresent(Locator.button("View All Validation Jobs")); // Non site-admin user should not see this button.
    }

    private int verifyInvalidStatus(int jobCount)
    {
        var validationPage = submitValidationJob();
        jobCount++;
        validationPage.verifyInvalidStatus();
        validationPage.verifySampleFileStatus(SKY_FILE_1, Collections.emptyList(), List.of(WIFF_1, WIFF_SCAN_1));
        return jobCount;
    }

    private int uploadRawFilesVerifyInCompleteStatus(int jobCount, String skylineDoc)
    {
        // Upload the missing raw files
        uploadToRawFiles(WIFF_1, WIFF_SCAN_1);
        // Run validation job and verify the results
        DataValidationPage validationPage = submitValidationJob();
        jobCount++;

        validationPage.verifyIncompleteStatus();
        validationPage.verifySampleFileStatus(skylineDoc, List.of(WIFF_1, WIFF_SCAN_1), Collections.emptyList());
        return jobCount;
    }

    private int saveUnimodMatchVerifyCompleteStatus(int jobCount)
    {
        var validationPage = goToValidationDetails();

        // The Carboxymethylcysteine modification in the document does not have a Unimod Id. We should see the "Continue with an Incomplete PX Submission" button
        validationPage.verifyIncompleteStatus();
        validationPage.verifyModificationStatus("Carboxymethylcysteine", false, null, null);

        saveUnimodMatchForCarboxymethylcysteine();
        // Validation should have updated to "complete" status.
        validationPage = goToValidationDetails();
        validationPage.verifyCompleteStatus();
        validationPage.verifyModificationStatus("Carboxymethylcysteine", true, "UNIMOD:6", "Carboxymethyl");

        return jobCount;
    }

    private void saveUnimodMatchForCarboxymethylcysteine()
    {
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
        assertTextPresent("Unimod information for structural modification", "Carboxymethylcysteine",  "was successfully saved",
                "View all the structural and isotope modifications in the experiment",
                "[View Experiment Details]");
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
        uploadToRawFiles(WIFF_2, WIFF_SCAN_2, WIFF_3, WIFF_SCAN_3);
        // Run validation job and verify the results
        DataValidationPage validationPage = submitValidationJob();
        jobCount++;
        validationPage.verifyIncompleteStatus();
        validationPage.verifySampleFileStatus(SKY_FILE_1, List.of(WIFF_1, WIFF_SCAN_1), Collections.emptyList());
        validationPage.verifySampleFileStatus(SKY_FILE_2, List.of(WIFF_2, WIFF_SCAN_2, WIFF_3, WIFF_SCAN_2), Collections.emptyList());

        // Verify library built with MaxQuant results. Expect to see evidence.xml, mqpar.xml in the Peptide ID files list
        // even though these files are not named in the .blib
        verifySpecLibSourceFiles(validationPage, "maxquant.blib", SKY_FILE_2,  "104 KB",
                false, maxQuantLibRawSources, maxQuantLibPeptideIdSources);

        // .blib does not have any source files in the SpectrumSourceFiles table.
        validationPage.verifySpectralLibraryStatus("Qtrap_DP-PA_cons_P0836.blib", "61 KB",
                "Missing spectrum and peptide ID file names in the .blib file.",
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

        // Verify library built with MaxQuant results. Expect to see evidence.xml, mqpar.xml in the Peptide ID files list
        // even though these files are not named in the .blib
        verifySpecLibSourceFiles(validationPage, "maxquant.blib", SKY_FILE_2, "104 KB",
                false, maxQuantLibRawSources, maxQuantLibPeptideIdSources);

        // .blib does not have any source files in the SpectrumSourceFiles table.
        validationPage.verifySpectralLibraryStatus("Qtrap_DP-PA_cons_P0836.blib", "61 KB",
                "Missing spectrum and peptide ID file names in the .blib file.",
                List.of(SKY_FILE_2),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        // RasPhos_20170125.blib is used with both SKY_FILE_3 and  SKY_FILE_4
        // The .blib is missing in SKY_FILE_3. We expect to see two rows for this library since the library key includes the file size of the library
        validationPage.verifySpectralLibraryStatus("RasPhos_20170125.blib", "-",
                "Library file is missing from the Skyline document ZIP file",
                List.of(SKY_FILE_3),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        validationPage.verifySpectralLibraryStatus("RasPhos_20170125.blib", "216 KB",
                "Missing spectrum files. Missing peptide ID file names in the .blib file.",
                List.of(SKY_FILE_4),
                Collections.emptyList(), List.of("MY20170124_ARC_RasPhosHmix_500fmol_01.mzXML"), Collections.emptyList(), Collections.emptyList());

        // SKY_FILE_4 contains a NIST library.  We only support BiblioSpec and EncyclopeDIA libraries.  So this will be marked as "INCOMPLETE"
        validationPage.verifySpectralLibraryStatus("NIST_bsa_IT_2011-04-01.msp", "3 MB",
                "nist library type is not supported",
                List.of(SKY_FILE_4),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        return jobCount;
    }

    private void uploadToRawFiles(String... files)
    {
        goToRawDataTab();
        for (String file: files)
        {
            _fileBrowserHelper.uploadFile(getSampleDataPath(file));
            _fileBrowserHelper.fileIsPresent(file);
        }
    }

    private void goToRawDataTab()
    {
        portalHelper.click(Locator.folderTab("Raw Data"));
    }
}
