/*
 * Copyright (c) 2018-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
// Autocompletion for Organism and Instrument fields in the ExperimentAnnotations form.
// Uses tagsinput (https://bootstrap-tagsinput.github.io/bootstrap-tagsinput/examples/)
// and
// typeahead (https://github.com/twitter/typeahead.js, http://twitter.github.io/typeahead.js/examples/)
+function($){

initAutoComplete = function (url, renderId, prefetch, allowFreeInput)
{
    var completionStore = createStore(url, prefetch);
    createInputElement(renderId, completionStore, !prefetch, allowFreeInput === true);
}

// Species with 20 or more datasets in Pride (8/8/18). Show these by default. They are roughly in order of dataset
// counts (e.g. most datasets for Homo sapiens) so we want to display them in this order.
var localOrgStore = [
    {value:'Homo sapiens (taxid:9606)', name:'Human, Homo sapiens (taxid:9606)'},
    {value:'Mus musculus (taxid:10090)', name:'Mouse, Mus musculus (taxid:10090)'},
    {value:'Saccharomyces cerevisiae (taxid:4932)', name:'Yeast, S. cerevisiae, Saccharomyces cerevisiae (taxid:4932)'},
    {value:'Rattus norvegicus (taxid:10116)', name:'Rat, Rattus norvegicus (taxid:10116)'},
    {value:'Arabidopsis thaliana (taxid:3702)', name:'Arabidopsis thaliana (taxid:3702)'},
    {value:'Escherichia coli (taxid:562)', name:'E. coli, Escherichia coli (taxid:562)'},
    {value:'Bos taurus (taxid:9913)', name: 'Cow, Bovine, Bos taurus (taxid:9913)'},
    {value:'Drosophila melanogaster (taxid:7227)', name:'Fruit fly, Drosophila melanogaster (taxid:7227)'},
    {value:'Caenorhabditis elegans (taxid:6239)', name:'Roundworm, Caenorhabditis elegans (taxid:6239}'},
    {value:'Sus scrofa (taxid:9823)', name:'Pig, Sus scrofa (taxid:9823)'},
    {value:'Sus scrofa domesticus (taxid:9825)', name:'Domestic pig, Sus scrofa domesticus (taxid:9825)'},
    {value:'Gallus gallus (taxid:9031)', name:'Chicken, Gallus gallus (taxid:9031}'},
    {value:'Oryza sativa (taxid:4530)', name:'Rice, Oryza sativa (taxid:4530)'},
    {value:'Trypanosoma brucei (taxid:5691)', name:'Trypanosoma brucei (taxid:5691)'},
    {value:'Glycine max (taxid:3847)', name:'Soybean, Glycine max (taxid:3847)'},
    {value:'Danio rerio (taxid:7955)', name:'Zebra fish, Danio rerio (taxid:7955)'},
    {value:'Mycobacterium tuberculosis (taxid:1773)', name:'Mycobacterium tuberculosis (taxid:1773)'},
    {value:'Zea mays (taxid:4577)', name:'Maize, Zea mays (taxid:4577)'},
    {value:'Solanum lycopersicum (taxid:4081)', name:'Tomato, Solanum lycopersicum (taxid:4081)'},
    {value:'Candida albicans (taxid:5476)', name:'Candida albicans (taxid:5476)'},
    {value:'Staphylococcus aureus (taxid:1280)', name:'Staphylococcus aureus (taxid:1280)'},
    {value:'Bacillus subtilis (taxid:1423)', name:'Bacillus subtilis (taxid:1423)'},
    {value:'Chlamydomonas reinhardtii (taxid:3055)', name:'Chlamydomonas reinhardtii (taxid:3055)'},
    {value:'Schizosaccharomyces pombe (taxid:4896)', name:'Schizosaccharomyces pombe (taxid:4896)'},
    {value:'Streptococcus pyogenes (taxid:1314)', name:'Streptococcus pyogenes (taxid:1314)'},
    {value:'Human immunodeficiency virus 1 (taxid:11676)', name:'HIV-1, Human immunodeficiency virus 1 (taxid:11676)'},
    {value:'Neisseria gonorrhoeae (taxid:485)', name:'Neisseria gonorrhoeae (taxid:485)'},
    {value:'Synechocystis (taxid:1142)', name:'Synechocystis (taxid:1142)'}
];

function createStore(url, prefetch)
{
    var completionStore;

    // prefetch will be false for the Organism field. We will use a default local store
    // and query NCBI if no matches found in the local store.
    if(!prefetch) {
        completionStore = new Bloodhound({
            datumTokenizer: Bloodhound.tokenizers.obj.nonword(['name']),
            queryTokenizer: Bloodhound.tokenizers.nonword,
            local: localOrgStore,
            remote: {
                url: url,
                wildcard: '__QUERY__',
                transform: function (response) {
                    // console.log(response.completions);
                    return response.completions;
                }
            }
        });
    }
    // prefetch is true for the Instrument field.
    else
    {
        completionStore = new Bloodhound({
            datumTokenizer: Bloodhound.tokenizers.obj.nonword(['name']),
            queryTokenizer: Bloodhound.tokenizers.nonword,
            prefetch: {
                url: url,
                cache: false,
                transform: function (response) {
                    // console.log(response.completions);
                    return response.completions;
                }
            }
        });
    }

    completionStore.initialize();

    return completionStore;
}

var getDefaults = function (store) {
    return function findMatches(q, sync, async)
    {
        if (q === '' || q.length < 3) {
            sync(localOrgStore);
        }
        else {
            store.search(q, sync, async);
        }
    }
}

function createInputElement(renderId, store, showDefaults, freeInput)
{
    const el = $("#" + renderId + " .tags");
    el.tagsinput(
            {
                typeaheadjs: [
                    {
                        highlight: true,
                        minLength: 0,
                        hint: false
                    },
                    {
                        name: 'completionStore',
                        displayKey: 'name',
                        valueKey: 'value',
                        limit: Infinity,
                        source: showDefaults === true ? getDefaults(store) : store
                    }
                    ],
                freeInput: freeInput,
                confirmKeys: [13],
                onTagExists: function(item, $tag) {
                    $tag.hide().fadeIn();
                    clearInputText(el);
                }
    });

    // https://stackoverflow.com/questions/37973713/bootstrap-tagsinput-form-submited-on-press-enter-key
    el.on('keypress', function(e){
        if (e.keyCode === 13){
            // e.keyCode = 188;
            e.preventDefault();
        }
    });

    el.on('itemAdded', function() {
        clearInputText(el);
    });

    if (freeInput === true)
    {
        const input = el.tagsinput('input');
        if (input)
        {
            // Trigger the 'Enter' keypress event so that the input is tag-ified. This should only be done
            // if we are accepting free input.
            // https://stackoverflow.com/questions/49429848/bootstrap-taginput-is-not-making-its-text-as-tag-after-focus-out-of-taginput-fie
            input.on('blur', function(){
                $(this).trigger(jQuery.Event('keypress', {which: 13}));
            });
        }
    }
}

function clearInputText(tagsInputEl)
{
    const input = tagsInputEl.tagsinput('input');
    if (input && input.typeahead)
    {
        // If we accept free input (not one of the values from the drop-down options backed by the store) then the
        // input does not get cleared automatically after then enter key is pressed. After the cursor is moved away
        // from the input field you can still see the entered text next to the new tag.
        // This may be a bug in bootstrap-tagsinput.  I see similar behavior in their "Typeahead" example
        // on this page: https://bootstrap-tagsinput.github.io/bootstrap-tagsinput/examples/.
        // If you enter the same tag twice, and click outside the input box, the entered text is still visible in the input field next to the tag.
        // Clear it here after the item has been added.
        input.typeahead('val', '');
    }
}
}(jQuery);