# The purpose of this workflow is to create L2 GCT files for Skyline documents in the LINCS project on PanoramaWeb.
# This pipeline will
# 1. Download a Skyline .sky.zip from from PanoramaWeb.
# 2. Download the Skyline report template from PanoramaWeb
# 3. Use Skyline to export a report from the downloaded document using the report template.
# 4. Run the GCT Maker Perl script to create a GCT file from the exported report.
# 5. Upload the GCT file, exported report and logs to PanoramaWeb


workflow lincs_gct_workflow {

    String url_webdav_skyline_zip
	String url_webdav_skyr
    String url_webdav_gct_dir
    String url_webdav_cromwell_output_dir
    String panorama_apikey


	# Download the Skyline shared zip file
    call download_file as download_skyzip {
        input:
            file_url=url_webdav_skyline_zip,
            apikey=panorama_apikey
    }

	# Download the report template
    call download_file as download_report {
        input:
            file_url=url_webdav_skyr,
            apikey=panorama_apikey
    }

	# Export the report
    call skyline_export_report {
        input:
            skyzip=download_skyzip.downloaded_file,
            report_template=download_report.downloaded_file
    }

	# Run GCT Maker Perl script
    call gct_maker {
        input:
            report_file=skyline_export_report.report_file
    }

	# Upload the GCT, exported report and logs
    call upload_files {
        input:
            target_webdav_gct_dir=url_webdav_gct_dir,
            target_webdav_cromwell_dir=url_webdav_cromwell_output_dir,
			gctFile=gct_maker.gct,
			csvReport=skyline_export_report.report_file,
			csvReportTrimmed=gct_maker.report_trimmed,
			skyLog=skyline_export_report.skyline_log,
			gctMakerLog=gct_maker.task_log,
			apikey=panorama_apikey
    }
}


task download_file {
    String file_url
    String apikey

    command {
        java -jar /code/PanoramaClient.jar \
             -d \
             -w "${file_url}" \
             -k "${apikey}"
    }

    runtime {
        docker: "proteowizard/panorama-client-java:1.1"
    }

    output {
        File downloaded_file = basename("${file_url}")
    }

    parameter_meta {
        file_url: "WebDAV URL for file to be downloaded"
        apikey: "Panorama Server API key"
    }

    meta {
        author: "Vagisha Sharma"
        email: "vsharma@uw.edu"
        description: "Download file from a Panorama Server WebDAV url"
    }
}

task skyline_export_report {
    File skyzip
	File report_template

	String skyzip_name=basename(skyzip)

	# LINCS_P100_DIA_Plate70_annotated_minimized_2019-10-11_15-25-03.sky.zip -> LINCS_P100_DIA_Plate70_annotated_minimized
	String sky_basename=sub(skyzip_name, "minimized_.*$", "minimized")
	String skyzip_basename=basename(skyzip, ".sky.zip")

	String report_name=basename(report_template, ".skyr")

    command {
	    cp "${skyzip}" .
	    unzip "${skyzip_name}"
		rm "${skyzip_name}"
        wine SkylineCmd --in="${sky_basename}.sky" --report-add="${report_template}" --report-name="${report_name}" --report-conflict-resolution=overwrite  \
		--report-file="${skyzip_basename}.csv" --log-file="${skyzip_basename}.skyline.log"
    }

    runtime {
        docker: "proteowizard/pwiz-skyline-i-agree-to-the-vendor-licenses:skyline_20_2"
    }

    output {
        File report_file = "${skyzip_basename}.csv"
        File skyline_log = "${skyzip_basename}.skyline.log"
    }
}

task gct_maker {
    File report_file
    String report_file_name=basename(report_file)
	String gct_file_base_name=basename(report_file, ".csv")

    command {
        cp ${report_file} .
		perl /code/skyline_gct_maker.pl . "${gct_file_base_name}" "${report_file_name}" > "${gct_file_base_name}.gctmaker.log" 2>&1
    }

    runtime {
        docker: "proteowizard/panorama-skyline-gct:latest"
    }

    output {
	    File report_trimmed = "${report_file_name}_minus_blank_columns.csv"
        File gct = "${gct_file_base_name}.gct"
        File task_log = "${gct_file_base_name}.gctmaker.log"
    }
}

task upload_files {
    String target_webdav_gct_dir
    String target_webdav_cromwell_dir
    String apikey
    File csvReport
	File csvReportTrimmed
    File gctFile
    File skyLog
    File gctMakerLog

    command {
        java -jar /code/PanoramaClient.jar \
             -u \
			 -f "${gctFile}" \
             -w "${target_webdav_gct_dir}" \
             -k "${apikey}"
        java -jar /code/PanoramaClient.jar \
             -u \
             -f "${csvReport}" \
             -w "${target_webdav_cromwell_dir}" \
             -k "${apikey}"
		java -jar /code/PanoramaClient.jar \
             -u \
             -f "${csvReportTrimmed}" \
             -w "${target_webdav_cromwell_dir}" \
             -k "${apikey}"
        java -jar /code/PanoramaClient.jar \
             -u \
             -f "${skyLog}" \
             -w "${target_webdav_cromwell_dir}" \
             -k "${apikey}"
        java -jar /code/PanoramaClient.jar \
             -u \
             -f "${gctMakerLog}" \
             -w "${target_webdav_cromwell_dir}" \
             -k "${apikey}"
    }

    runtime {
        docker: "proteowizard/panorama-client-java:1.1"
    }

    meta {
        author: "Vagisha Sharma"
        email: "vsharma@uw.edu"
        description: "Upload files to a folder on Panorama Server"
    }
}