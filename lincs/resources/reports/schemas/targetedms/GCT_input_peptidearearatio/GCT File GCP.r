##
#  Copyright (c) 2015-2016 LabKey Corporation
# 
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
# 
#      http://www.apache.org/licenses/LICENSE-2.0
# 
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
##
# This sample code returns the query data in tab-separated values format, which LabKey then
# renders as HTML. Replace this code with your R script. See the Help tab for more details.
library(Rlabkey)
require(reshape2)

source("LabKeyInput.R");
source("p100_processing.R");

print("Updated 09/21/15 11:00PM");
  print("------------- URL Params ---------------");
  print(labkey.url.params);
  print("----------------------------------------");

# print(colnames(labkey.data));
# labkey.data has more columns that the query results.  Remove columns that we do not need.
# columns are:
# [1] "runid"                             "file"
# [3] "file_description"                  "replicateid"
# [5] "peptideid"                         "peptideid_peptidemodifiedsequence"
# [7] "probeid"                           "arearatio"
# [9] "isotopelabel"                      "isotopelabelstd"
labkey.data <- labkey.data[,c(1,4,5,7,8)];
# print(labkey.data[1:10,]);


# Replicate annotations
replicateAnnotations <- getReplicateAnnotations(labkey.url.path);
# Get the replication annotation count
numReplicateAnnotations <- nrow(replicateAnnotations);


# Area Ratios table pivoted by ReplicateId
# areaRatios <- getAreaRatios(labkey.url.path);
areaRatios <- processAreaRatioDF(labkey.data);

# Get the replicate count
numReplicates <- ncol(areaRatios);


# Peptide annotations pivoted by annotation name
peptideAnnotations <- getPeptideAnnotations(labkey.url.path);
# Get the peptide annotation count
numPeptideAnnotations <- ncol(peptideAnnotations);


# Merge Peptide Annotations and Area Ratios data frames.
pa_ar_merged <- merge(peptideAnnotations, areaRatios, by="row.names", all=TRUE);
rownames(pa_ar_merged) <- pa_ar_merged[,1];
pa_ar_merged <- pa_ar_merged[,-1];
print("Merged peptideAnnotations and areaRatios data frame size is: ");
print(dim(pa_ar_merged));

# Get the peptide count
numPeptides <- nrow(pa_ar_merged);

# Update the areaRatios dataframe to include peptides for which we have annotations but no area ratios
areaRatios <- pa_ar_merged[,((ncol(peptideAnnotations) + 1):ncol(pa_ar_merged))];


# Headers - top part of the GCT files containing replicate annotations
headers <- replicateAnnotations;
# Add peptide annotation columns
headers[,setdiff(colnames(pa_ar_merged), colnames(headers))] <- "";
# The new columns get added at the end. Move them before the replicate columns
headers <- headers[,c((numReplicates+1):ncol(headers),1:(numReplicates))];
headers[1,1:numPeptideAnnotations] <- colnames(headers)[1:numPeptideAnnotations];
# Get the replication annotation count
numReplicateAnnotations <- nrow(headers) - 1;



# Write the GCT file
print(paste(numPeptides, numReplicates, numPeptideAnnotations, numReplicateAnnotations, sep="\t"));
fileOut <- "${fileout:lincs.gct}"
write("#1.3", file=fileOut);
write(paste(numPeptides, numReplicates, numPeptideAnnotations, numReplicateAnnotations, sep="\t"), file=fileOut, append=TRUE)
write.table(headers, file = fileOut, sep="\t", row.names=TRUE, col.names=FALSE, quote=FALSE, append=TRUE);
write.table(pa_ar_merged, file = fileOut, sep="\t", row.names=TRUE, col.names=FALSE, quote=FALSE, append=TRUE);


# Write the processed GCT file
########## Processed GCT ##############################
GCPprocessGCTMaster(repAnnot=replicateAnnotations, probeAnnot=peptideAnnotations, dataTable=areaRatios, outputFileName="${fileout:lincs.processed.gct}", log2=FALSE);