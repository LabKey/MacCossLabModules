# The purpose of this workflow is to create L2 GCT files for Skyline documents in the LINCS project on PanoramaWeb.
# This pipeline will
# 1. Download a Skyline .sky.zip from from PanoramaWeb.
# 2. Download the Skyline report template from PanoramaWeb
# 3. Use Skyline to export a report from the downloaded document using the report template.
# 4. Run the GCT Maker Perl script to create a GCT file from the exported report.
# 3. Upload the GCT file to PanoramaWweb


workflow lincs_gct_workflow {

    String url_webdav_skyline_zip
	String url_webdav_skyr
    String url_webdav_gct_folder
    String url_webdav_cromwell_output_folder
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

	# Upload the GCT file
    call upload_files as upload_gct {
        input:
            target_webdav_gct_dir=url_webdav_gct_folder,
            target_webdav_cromwell_dir=url_webdav_cromwell_output_folder,
			gctFile=gct_maker.gct,
			csvReport=skyline_export_report.report_file,
			skyLog=skyline_export_report.skyline_log
			gctMakerLog=gct_maker.task_log
			apikey=panorama_apikey
    }

    # Upload the exported report
    call upload_file as upload_report_csv {
        input:
            target_webdav_dir=url_webdav_cromwell_output_folder,
            file=skyline_export_report.report_file,
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
        docker: "vagisha11/test-panorama-client-java:4.0"
    }

    output {
        File downloaded_file = basename("${file_url}")
        File task_log = stdout()
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
		--report-file="${skyzip_basename}.csv" --log-file="${skyzip_basename}.log"
    }

    runtime {
        docker: "proteowizard/pwiz-skyline-i-agree-to-the-vendor-licenses:latest"
    }

    output {
        File report_file = "${skyzip_basename}.csv"
        File skyline_log = "${skyzip_basename}.log"
        File task_log = stdout()
    }
}

task gct_maker {
    File report_file
	String gct_file_name=basename(report_file, ".csv")

    command {
		perl /code/skyline_gct_maker.pl . "${gct_file_name}" "${report_file}"
    }

    runtime {
        docker: "vagisha11/gct_maker:1.0"
    }

    output {
        File gct = "${gct_file_name}.gct"
        File task_log = stdout()
    }
}

task upload_files {
    String target_webdav_gct_dir
    String target_webdav_cromwell_dir
    String apikey
    File csvReport
    File gctFile
    File skyLog
    File gctMakerLog

    command {
        java -jar /code/PanoramaClient.jar \
             -u \
			 -f "${gctFile}" \
             -w "${target_webdav_dir}" \
             -k "${apikey}"
        java -jar /code/PanoramaClient.jar \
                     -u \
        			 -f "${csvReport}" \
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
        docker: "vagisha11/test-panorama-client-java:4.0"
    }

    output {
        File task_log = stdout()
    }
}