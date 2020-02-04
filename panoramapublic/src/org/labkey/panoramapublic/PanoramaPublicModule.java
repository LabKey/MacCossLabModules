/*
 * Copyright (c) 2019 LabKey Corporation
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

package org.labkey.panoramapublic;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SpringModule;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.protein.ProteinService;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ShortURLService;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.panoramapublic.pipeline.CopyExperimentPipelineProvider;
import org.labkey.panoramapublic.proteomexchange.SubmissionDataValidator;
import org.labkey.panoramapublic.query.ExperimentTitleDisplayColumn;
import org.labkey.panoramapublic.security.CopyTargetedMSExperimentRole;
import org.labkey.panoramapublic.view.expannotations.TargetedMSExperimentWebPart;
import org.labkey.panoramapublic.view.expannotations.TargetedMSExperimentsWebPart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PanoramaPublicModule extends SpringModule
{
    public static final String NAME = "PanoramaPublic";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 19.31;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    protected void init()
    {
        addController(PanoramaPublicController.NAME, PanoramaPublicController.class);
        PanoramaPublicSchema.register(this);
    }

    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        PipelineService service = PipelineService.get();
        service.registerPipelineProvider(new CopyExperimentPipelineProvider(this));

        // Register the CopyExperimentRole
        RoleManager.registerRole(new CopyTargetedMSExperimentRole());

        // Add a link in the admin console to manage journals.
        ActionURL url = new ActionURL(PanoramaPublicController.JournalGroupsAdminViewAction.class, ContainerManager.getRoot());
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Configuration, "panorama public", url, AdminPermission.class);

        ActionURL addModuleLink = new ActionURL(PanoramaPublicController.AddPanoramaPublicModuleAction.class, ContainerManager.getRoot());
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Configuration, "Panorama Public - Add Module", addModuleLink, AdminPermission.class);

        PanoramaPublicListener listener = new PanoramaPublicListener();
        ExperimentService.get().addExperimentListener(listener);
        ContainerManager.addContainerListener(listener);

        ShortURLService shortUrlService = ShortURLService.get();
        shortUrlService.addListener(listener);

        TargetedMSService tmsService = TargetedMSService.get();
        tmsService.registerSkylineDocumentImportListener(listener);
        tmsService.registerTargetedMSFolderTypeListener(listener);

        TargetedMSService.get().addModificationSearchResultCustomizer(ExperimentTitleDisplayColumn.getModSearchTableCustomizer());
        TargetedMSService.get().addPeptideSearchResultCustomizers(ExperimentTitleDisplayColumn.getPeptideSearchTableCustomizer());
        TargetedMSService.get().addProteinSearchResultCustomizer(ExperimentTitleDisplayColumn.getProteinSearchTableCustomizer());
    }

    @NotNull
    @Override
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        BaseWebPartFactory experimentAnnotationsListFactory = new BaseWebPartFactory(TargetedMSExperimentsWebPart.WEB_PART_NAME)
        {
            @Override
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new TargetedMSExperimentsWebPart(portalCtx);
            }
        };

        BaseWebPartFactory containerExperimentFactory = new BaseWebPartFactory(TargetedMSExperimentWebPart.WEB_PART_NAME)
        {
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new TargetedMSExperimentWebPart(portalCtx);
            }
        };

        BaseWebPartFactory proteinSearchFactory = new BaseWebPartFactory("Panorama Public Protein Search")
        {
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                JspView view = new JspView("/org/labkey/panoramapublic/view/search/panoramaPublicProteinSearch.jsp", getDefaultProteinSearchForm());
                view.setTitle("Panorama Public Protein Search");
                return view;
            }
            @Override
            public boolean isAvailable(Container c, String scope, String location)
            {
                return false;
            }
        };

        BaseWebPartFactory peptideSearchFactory = new BaseWebPartFactory("Panorama Public Peptide Search")
        {
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                JspView view = new JspView("/org/labkey/panoramapublic/view/search/panoramaPublicPeptideSearch.jsp", getDefaultPeptideSearchForm());
                view.setTitle("Panorama Public Peptide Search");
                return view;
            }
            @Override
            public boolean isAvailable(Container c, String scope, String location)
            {
                return false;
            }
        };

        List<WebPartFactory> webpartFactoryList = new ArrayList<>();
        webpartFactoryList.add(experimentAnnotationsListFactory);
        webpartFactoryList.add(containerExperimentFactory);
        webpartFactoryList.add(proteinSearchFactory);
        webpartFactoryList.add(peptideSearchFactory);
        return webpartFactoryList;
    }

    private ProteinService.ProteinSearchForm getDefaultProteinSearchForm()
    {
        return new ProteinService.ProteinSearchForm()
        {
            @Override
            public int[] getSeqId()
            {
                return new int[0];
            }

            @Override
            public boolean isExactMatch()
            {
                return true;
            }

            @Override
            public boolean isIncludeSubfolders()
            {
                return true;
            }
            @Override
            public boolean isShowProteinGroups()
            {
                return false;
            }
        };
    }

    private ProteinService.PeptideSearchForm getDefaultPeptideSearchForm()
    {
        return  new ProteinService.PeptideSearchForm()
        {
            @Override
            public boolean isExact()
            {
                return true;
            }
            @Override
            public boolean isSubfolders()
            {
                return true;
            }
            @Override
            public SimpleFilter.FilterClause createFilter(String sequenceColumnName)
            {
                return null;
            }
        };
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton(PanoramaPublicSchema.SCHEMA_NAME);
    }

    @NotNull
    @Override
    public Set<Class> getIntegrationTests()
    {
        return Set.of(
                PanoramaPublicController.TestCase.class
        );
    }

    @NotNull
    @Override
    public Set<Class> getUnitTests()
    {
        Set<Class> set = new HashSet<>();
        set.add(PanoramaPublicController.TestCase.class);
        set.add(SubmissionDataValidator.TestCase.class);
        return set;

    }
}