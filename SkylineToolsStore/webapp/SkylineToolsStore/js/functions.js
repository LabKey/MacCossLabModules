function setButtonsEnabled(enable) {
    var action = enable ? "enable" : "disable";
    $(".ui-dialog-buttonpane button:contains('Ok')").button(action);
    $(".ui-dialog-buttonpane button:contains('Cancel')").button(action);
}

function autocomplete(txtbox, tags) {
    txtbox.bind("keydown", function(e) {
        if (e.keyCode === $.ui.keyCode.TAB && $(this).data("ui-autocomplete").menu.active)
            e.preventDefault();
    })
            .autocomplete({
                minLength: 0,
                source: function(request, response) {
                    response($.ui.autocomplete.filter(tags, request.term.split(",").pop().trim()));
                },
                focus: function() { return false; },
                open: function(e, ui) { $(".ui-autocomplete").css("z-index", 90001); },
                select: function(event, ui) {
                    var terms = this.value.replace(/^\s+|\s+$/g,"").split(/\s*,\s*/);
                    terms.pop();
                    if ($.inArray(ui.item.value, terms) === -1)
                        terms.push(ui.item.value);
                    terms.push("");
                    this.value = terms.join(", ");
                    return false;
                }
            });
}

function getCookie(name) {
    var parts = document.cookie.split(name + "=");
    if (parts.length == 2)
        return parts.pop().split(";").shift();
    return null;
}

function fixDlg(dlg) {
    dlg.parent().css("position", "fixed");
}

function initJqueryUiImages(dir) {
    function setBg(elements, img) {
        $(elements).each(function() {$(this).css("background-image", "url(" + dir + "/" + img + ")");});
    }
    setBg(".ui-progressbar > .ui-progressbar-overlay", "animated-overlay.gif");
    setBg(".ui-widget-content", "ui-bg_flat_75_ffffff_40x100.png");
    setBg(".ui-widget-header", "ui-bg_highlight-soft_75_cccccc_1x100.png");
    setBg(".ui-state-default", "ui-bg_glass_75_e6e6e6_1x400.png");
    setBg(".ui-state-hover, .ui-state-focus", "ui-bg_glass_75_dadada_1x400.png");
    setBg(".ui-state-active", "ui-bg_glass_65_ffffff_1x400.png");
    setBg(".ui-state-highlight", "ui-bg_glass_55_fbf9ee_1x400.png");
    setBg(".ui-state-error", "ui-bg_glass_95_fef1ec_1x400.png");
    setBg(".ui-icon", "ui-icons_222222_256x240.png");
    setBg(".ui-state-default > .ui-icon", "ui-icons_888888_256x240.png");
    setBg(".ui-state-hover > .ui-icon, .ui-state-focus > .ui-icon, .ui-state-active > .ui-icon", "ui-icons_454545_256x240.png");
    setBg(".ui-state-highlight > .ui-icon", "ui-icons_2e83ff_256x240.png");
    setBg(".ui-state-error > .ui-icon, .ui-state-error-text > .ui-icon", "ui-icons_cd0a0a_256x240.png");
    setBg(".ui-widget-overlay", "ui-bg_flat_0_aaaaaa_40x100.png");
    setBg(".ui-widget-shadow", "ui-bg_flat_0_aaaaaa_40x100.png");
}

function initRatingSlider(sliderElement, sliderOverElement, valueInput) {
    var sliderWidth = $(sliderElement).width();
    var starWidth = sliderWidth / 5;
    $(sliderElement).mousemove(function(e) {
        var value = Math.ceil(((e.pageX - $(this).offset().left) / sliderWidth) * 5);
        var newWidth = value * starWidth;
        if (newWidth != $(sliderOverElement).width()) {
            $(sliderOverElement).fadeTo(250, 0.5);
            $(sliderOverElement).width(newWidth);
        }
    }).click(function() {
        $(valueInput).val($(sliderOverElement).width() / starWidth);
        $(sliderOverElement).fadeTo(250, 1.0);
    }).mouseleave(function() {
        $(sliderOverElement).width($(valueInput).val() * starWidth);
        $(sliderOverElement).fadeTo(250, 1.0);
    });
}
