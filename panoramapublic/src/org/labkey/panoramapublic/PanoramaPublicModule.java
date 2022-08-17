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
import org.jetbrains.annotations.Nullable;
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
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ShortURLService;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.speclib.SpecLibKey;
import org.labkey.panoramapublic.pipeline.CopyExperimentPipelineProvider;
import org.labkey.panoramapublic.pipeline.PxValidationPipelineProvider;
import org.labkey.panoramapublic.proteomexchange.ExperimentModificationGetter;
import org.labkey.panoramapublic.proteomexchange.Formula;
import org.labkey.panoramapublic.proteomexchange.SkylineVersion;
import org.labkey.panoramapublic.proteomexchange.UnimodUtil;
import org.labkey.panoramapublic.proteomexchange.validator.SkylineDocValidator;
import org.labkey.panoramapublic.proteomexchange.validator.SpecLibValidator;
import org.labkey.panoramapublic.query.ContainerJoin;
import org.labkey.panoramapublic.query.ExperimentTitleDisplayColumn;
import org.labkey.panoramapublic.query.JournalManager;
import org.labkey.panoramapublic.query.modification.ModificationsView;
import org.labkey.panoramapublic.query.speclib.SpecLibView;
import org.labkey.panoramapublic.security.CopyTargetedMSExperimentRole;
import org.labkey.panoramapublic.security.PanoramaPublicSubmitterPermission;
import org.labkey.panoramapublic.security.PanoramaPublicSubmitterRole;
import org.labkey.panoramapublic.view.expannotations.TargetedMSExperimentWebPart;
import org.labkey.panoramapublic.view.expannotations.TargetedMSExperimentsWebPart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.labkey.api.util.DOM.DIV;

public class PanoramaPublicModule extends SpringModule
{
    public static final String NAME = "PanoramaPublic";
    public static final String DOWNLOAD_DATA_INFO_WP = "Download Data";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public @Nullable Double getSchemaVersion()
    {
        return 22.005;
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
        service.registerPipelineProvider(new PxValidationPipelineProvider(this));

        // Register Panorama Public specific roles and permissions
        RoleManager.registerRole(new CopyTargetedMSExperimentRole());
        RoleManager.registerRole(new PanoramaPublicSubmitterRole());
        RoleManager.registerPermission(new PanoramaPublicSubmitterPermission());

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
            @Override
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new TargetedMSExperimentWebPart(portalCtx);
            }
        };

        BaseWebPartFactory proteinSearchFactory = new BaseWebPartFactory("Panorama Public Protein Search")
        {
            @Override
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
            @Override
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

        BaseWebPartFactory dataDownloadInfoFactory = new BaseWebPartFactory(DOWNLOAD_DATA_INFO_WP)
        {
            @Override
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                Container project = portalCtx.getContainer().getProject();
                if (project != null)
                {
                    Journal journal = JournalManager.getJournal(project);
                    if (journal != null)
                    {
                        JournalManager.PublicDataUser publicDataUser = JournalManager.getPublicDataUser(journal);
                        if (publicDataUser != null)
                        {
                            JspView view = new JspView("/org/labkey/panoramapublic/view/publish/dataDownloadInfo.jsp", publicDataUser);
                            view.setTitle(DOWNLOAD_DATA_INFO_WP);
                            return view;
                        }
                        else
                        {
                            HtmlView view = new HtmlView(DIV("Public data download user is not configured for " + journal.getName()));
                            return view;
                        }
                    }
                }
                return new HtmlView(DIV(DOWNLOAD_DATA_INFO_WP + " webpart can only be added in projects setup as a Journal"));
            }
            @Override
            public boolean isAvailable(Container c, String scope, String location)
            {
                // This webpart should be available only in subfolders of projects configured as Journal projects (e.g. Panorama Public)
                Container project = c.getProject();
                return project != null && JournalManager.getJournal(project) != null;
            }
        };

        BaseWebPartFactory spectralLibrariesFactory = new BaseWebPartFactory("Spectral Libraries")
        {
            @Override
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new SpecLibView(portalCtx);
            }
        };

        BaseWebPartFactory structuralModsFactory = new BaseWebPartFactory("Structural Modifications")
        {
            @Override
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new ModificationsView.StructuralModsView(portalCtx);
            }
        };

        BaseWebPartFactory isotopeModsFactory = new BaseWebPartFactory("Isotope Modifications")
        {
            @Override
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new ModificationsView.IsotopeModsView(portalCtx);
            }
        };

        List<WebPartFactory> webpartFactoryList = new ArrayList<>();
        webpartFactoryList.add(experimentAnnotationsListFactory);
        webpartFactoryList.add(containerExperimentFactory);
        webpartFactoryList.add(proteinSearchFactory);
        webpartFactoryList.add(peptideSearchFactory);
        webpartFactoryList.add(dataDownloadInfoFactory);
        webpartFactoryList.add(spectralLibrariesFactory);
        webpartFactoryList.add(structuralModsFactory);
        webpartFactoryList.add(isotopeModsFactory);
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
                PanoramaPublicController.TestCase.class,
                ExperimentModificationGetter.TestCase.class,
                UnimodUtil.TestCase.class
        );
    }

    @NotNull
    @Override
    public Set<Class> getUnitTests()
    {
        Set<Class> set = new HashSet<>();
        set.add(PanoramaPublicController.TestCase.class);
        set.add(PanoramaPublicNotification.TestCase.class);
        set.add(SkylineVersion.TestCase.class);
        set.add(SpecLibKey.TestCase.class);
        set.add(SkylineDocValidator.TestCase.class);
        set.add(SpecLibValidator.TestCase.class);
        set.add(ContainerJoin.TestCase.class);
        set.add(Formula.TestCase.class);
        return set;

    }
}