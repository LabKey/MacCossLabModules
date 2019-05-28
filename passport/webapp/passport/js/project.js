function Project(project)
{
    var container = project.container;
    var fileName = project.fileName;
    var peptideGroupId = project.pepGroupId;
    var runId = project.runId;
    var peptides = project.peptides;
    var id = "project" +  project.runId;
    var checkBoxId =  "checkbox" + id;

    project.getId = function() {
        return id;
    }


    var bar = new ProteinBar(project);

    $("#"+checkBoxId).change(function(){
        if($(this).is(':checked')){
            $("#"+project.runId + "-protbar").removeAttr("display");
        } else {
            $("#"+project.runId + "-protbar").attr("display", "none");

        }
    });
}