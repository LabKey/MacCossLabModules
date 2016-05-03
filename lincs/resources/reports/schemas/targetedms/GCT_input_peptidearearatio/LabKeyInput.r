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
library(Rlabkey)
require(reshape2)


baseUrl <- function(){
  return(paste("https://panoramaweb.org:8443/labkey/"));
  # return (paste("http://localhost:8080/labkey"));
}

selectedReplicateIds <- vector('numeric');
getReplicateIds <- function(replicateAnnotations){

  replicateIds <- vector('numeric');

  # Example: "pert_desc:DMSO,JQ1;pert_dose:0.1,0.5";
  replAnnot <- as.character(labkey.url.params$replAnnot);

  if(is.null(replAnnot) || is.na(nchar(replAnnot)[1]) || (nchar(replAnnot)[1] == 0))
  {
    return(replicateIds);
  }

  replAnnotList <- strsplit(replAnnot, ";")

  for(i in 1:length(replAnnotList[[1]]))
  {
    ra <- strsplit(replAnnotList[[1]][i], ":");
    annotName = ra[[1]][1];
    annotVals <- strsplit(ra[[1]][2], ",");

    replIdsForAnnot <- vector('numeric');
    for(j in 1:length(annotVals[[1]])){

      annotVal <- annotVals[[1]][j]
      print(paste("Filter for annotation:", annotName, annotVal, sep=" "));
      print(replicateAnnotations[replicateAnnotations$Name==annotName & replicateAnnotations$Value==annotVal,]$ReplicateId);
      replIdsForAnnot <- c(replIdsForAnnot, replicateAnnotations[replicateAnnotations$Name==annotName & replicateAnnotations$Value==annotVal,]$ReplicateId)
    }
    if(length(replicateIds) == 0) {
      replicateIds <- replIdsForAnnot;
    }
    else {
      replicateIds <- intersect(replicateIds, replIdsForAnnot);
      print(replicateIds);
    }

  }
  selectedReplicateIds <<- replicateIds
  return (replicateIds);
}

filters <- function(fieldKey){

  runId <- labkey.url.params$"runId";

  if(is.null(runId))
  {
    runId <- labkey.url.params$"query.RunId~eq";
  }
  if(is.null(runId))
  {
    runId <- labkey.url.params$"query.PeptideChrominfoId/SampleFileId/ReplicateId/RunId/Id~eq";
  }

  if(!is.null(runId)){
    filter <- makeFilter(c(fieldKey, "EQUAL", runId));
    print(paste("Run id is ", runId, "; Filter is ", filter));
    return(filter);
  }
}


getPeptideAnnotations <- function(folderPath){

  print("Getting Peptide Annotations");
  # Query results from the PeptideAnnotation table
  peptideAnnotations <- labkey.selectRows(
    baseUrl=baseUrl(),
    folderPath=folderPath,
    schemaName="targetedms",
    queryName="GeneralMoleculeAnnotation",
    viewName="GCT_peptide_annotation",
    showHidden = TRUE,
    colFilter = filters("PeptideId/PeptideGroupId/RunId/File/Id"),
    containerFilter="Current" # labkey.url.params$"query.containerFilterName"
  );
  print("PeptideAnnotations");
  print(dim(peptideAnnotations));

  # Set the column names on the peptideAnnotations data frame
  colnames(peptideAnnotations) <- c("RunId", "PeptideId", "PeptideModifiedSequence", "PeptideSequence", "ProteinName", "Name", "Value");

  peptideAnnotations <- peptideAnnotations[,c(2,6,7)]; # Remove the "PeptideModifiedSequence", PeptideSequence" and "ProteinName" columns
  # Pivot by peptide annotation name
  peptideAnnotations <- dcast(peptideAnnotations, PeptideId ~ Name, value.var="Value");

  # Make "pr_id" the first column
  peptideAnnotations <- moveColumnToFirst(peptideAnnotations, "pr_id");
  # Remove the PeptideId column
  peptideAnnotations$PeptideId <- NULL;
  # Get annotations for unique probe ids
  # peptideAnnotations <- unique(peptideAnnotations);
  rs <- rowSums(is.na(peptideAnnotations));
  peptideAnnotations_o <- peptideAnnotations[order(rs),];
  peptideAnnotations <- subset(peptideAnnotations_o, !duplicated(peptideAnnotations_o$pr_id));
  print("Unique PeptideAnnotations--");
  print(dim(peptideAnnotations));

  # Order by ProbeId
  peptideAnnotations <- peptideAnnotations[order(peptideAnnotations$pr_id),]

  # Move the "pr_id" column to rownames rather than an actual column
  rownames(peptideAnnotations) <- peptideAnnotations$pr_id;
  peptideAnnotations <- peptideAnnotations[,-1];

  return (peptideAnnotations);
}

getReplicateAnnotations <- function(folderPath) {

  print("Getting Replicate Annotations...");
  # Query results from the ReplicateAnnotation table
  replicateAnnotations <- labkey.selectRows(
    baseUrl=baseUrl(),
    folderPath=folderPath,
    schemaName="targetedms",
    queryName="replicateAnnotation",
    viewName="GCT_replicate_annotation",
    showHidden = TRUE,
    colFilter = filters("ReplicateId/RunId/Id"),
    colSort="+ReplicateId",
    containerFilter="Current"
    #containerFilter=labkey.url.params$"query.containerFilterName"
  );

  print("ReplicateAnnotations BEFORE FILTERING...");
  print(dim(replicateAnnotations));

  # Set the column names on the replicateAnnotations data frame
  colnames(replicateAnnotations) <- c("RunId", "ReplicateId", "Replicate", "Name", "Value");
  replicateAnnotations <- replicateAnnotations[,c(2,4,5)];

  # If we are filtering on replicate annotations, get a list of replicate Ids
  getReplicateIds(replicateAnnotations);
  # Filter for selected replicateIds if any
  if(length(selectedReplicateIds) > 0)
  {
    replicateAnnotations <- replicateAnnotations[replicateAnnotations$ReplicateId %in% selectedReplicateIds, ];
  }
  print("ReplicateAnnotations AFTER FILTERING");
  print(dim(replicateAnnotations));

   # Append L2X to the provenance_code annotation value
   # surviving_headers<-.updateProvenanceCode(static_headers,surviving_headers,"L2X");
   prov_code_annot <- paste(replicateAnnotations$Value[replicateAnnotations$Name == 'provenance_code'], "L2X", sep="+");
   replicateAnnotations$Value[replicateAnnotations$Name == 'provenance_code'] = prov_code_annot;

  # Pivot by replicate Id
  replicateAnnotations <- dcast(replicateAnnotations, Name ~ ReplicateId, value.var="Value");

  # Move the "Names" column to rownames rather than an actual column
  rownames(replicateAnnotations) <- replicateAnnotations$Name;
  replicateAnnotations <- replicateAnnotations[,-1];

  # Move the 'id' row to the top
  idRow <- which(rownames(replicateAnnotations) == "id");
  replicateAnnotations <- replicateAnnotations[c(idRow,1:(idRow-1),(idRow+1):nrow(replicateAnnotations)),];

  return (replicateAnnotations);
}

# ######################################
# This function is only used locally for testing
# ######################################
getAreaRatios <- function(folderPath) {

  # Query results from the custom query GCT_input_peptidearearatio
  areaRatios <- labkey.selectRows(
    baseUrl=baseUrl(),
    folderPath=folderPath,
    schemaName="targetedms",
    queryName="GCT_input_peptidearearatio",
    colFilter = filters("RunId"),
    containerFilter="Current" # labkey.url.params$"query.containerFilterName"
  );

  print(colnames(areaRatios));
  # Remove columns that we do not need.
  # columns are:
  # [1] "Run Id"            "File"              "Replicate Id"      "Peptide Id"        "Probe Id"          "Area Ratio"        "Isotope Label"
  # [8] "Isotope Label Std"
  areaRatios <- areaRatios[,c(1,3,4,5,6)];

  areaRatios <- processAreaRatioDF(areaRatios);
  return(areaRatios);
}

# This function is for use with the labkey.data frame that is returned from the
# 'GCT_input_peptidearearatio' query.
processAreaRatioDF <- function(areaRatios) {

  print("AreaRatios BEFORE PIVOTING");
  print(dim(areaRatios));

  print(colnames(areaRatios));
  # labkey.data contains results from the GCT_input_peptidearearatio table, filtered for light:heavy ratios
  colnames(areaRatios) <- c("RunId", "ReplicateId", "PeptideId", "ProbeId", "AreaRatio");
  areaRatios <- areaRatios[,c(2,4,5)]; # Exclude the "RunId" and "PeptideId" columns.

  # Filter for selected replicateIds if any
  if(length(selectedReplicateIds) > 0)
  {
    areaRatios <- areaRatios[areaRatios$ReplicateId %in% selectedReplicateIds, ];
  }
  print("AreaRatios AFTER FILTERING");
  print(dim(areaRatios));

  # If there are multiple rows for a replicateId and probeId combination, keep the row with the max. area ratio
  # This was done for the NPC_DrugTreatments_March2014.sky.  It has one peptide (LHS[+80.0]APNLSDLHVVRPK) that
  # was imported in two different Skyline documents that were then merged.  As a result, each replicate in the
  # Skyline document has two sample files. The light precursor of this peptide was imported in only one of the merged
  # Skyline documents whereas the heavy precursor was imported in both.  As a result, the GCT_input_peptidearearatio
  # query returns two rows per replicate for this peptide.
  print("Removing duplicate rows: replicate + peptide combination");
  # set NA values to 0 so that the max aggregating function returns the max value and not NA.
  areaRatios$AreaRatio[is.na(areaRatios$AreaRatio)]=0;
  areaRatios <- aggregate(AreaRatio ~ ReplicateId + ProbeId, areaRatios, max);
  print("AreaRatios AFTER REMOVING DUPLICATES");
  print(dim(areaRatios));


  # Round area ratios
  areaRatios$AreaRatio <- round(areaRatios$AreaRatio, 4);

  # log2 transform
  areaRatios$AreaRatio[areaRatios$AreaRatio==0]=NA;
  areaRatios$AreaRatio <- log(areaRatios$AreaRatio)/log(2);

  # Pivot the dataframe by replicate Id
  areaRatios <- dcast(areaRatios, ProbeId  ~ ReplicateId, value.var="AreaRatio");
  # areaRatios <- dcast(areaRatios, ProbeId  ~ ReplicateId, value.var="AreaRatio", fill = 0, fun.aggregate = min, na.rm=TRUE);
  print("AreaRatios AFTER PIVOTING");
  print(dim(areaRatios));

  # Sort by ProbeId.
  areaRatios <- areaRatios[order(areaRatios$ProbeId),]

  # Move the "ProbeId" column to rownames rather than an actual column
  rownames(areaRatios) <- areaRatios$ProbeId;
  areaRatios <- areaRatios[,-1];

  return(areaRatios);
}

moveColumnToFirst <- function(data, firstColumn) {
  data[c(firstColumn, setdiff(names(data), firstColumn))]
}