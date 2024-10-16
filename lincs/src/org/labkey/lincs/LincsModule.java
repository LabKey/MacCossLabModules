/*
 * Copyright (c) 2015-2018 LabKey Corporation
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

package org.labkey.lincs;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.AdminLinkManager;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.module.SpringModule;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.lincs.view.LincsDataView;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class LincsModule extends SpringModule
{
    public static final String NAME = "LINCS";
    public static final String PSP_JOB_NAME_SUFFIX = "PSP job name suffix";
    public final ModuleProperty PSP_JOB_NAME_SUFFIX_PROPERTY;
    public final ModuleProperty LINCS_ASSAY_TYPE_PROPERTY;

    private static final String NO_SUFFIX = "";

    public LincsModule()
    {
        PSP_JOB_NAME_SUFFIX_PROPERTY = new ModuleProperty(this, PSP_JOB_NAME_SUFFIX);
        PSP_JOB_NAME_SUFFIX_PROPERTY.setDefaultValue(NO_SUFFIX);
        PSP_JOB_NAME_SUFFIX_PROPERTY.setCanSetPerContainer(true);
        PSP_JOB_NAME_SUFFIX_PROPERTY.setShowDescriptionInline(true);
        addModuleProperty(PSP_JOB_NAME_SUFFIX_PROPERTY);

        LINCS_ASSAY_TYPE_PROPERTY = new ModuleProperty(this, "LINCS Assay Type");
        LINCS_ASSAY_TYPE_PROPERTY.setCanSetPerContainer(true);
        LINCS_ASSAY_TYPE_PROPERTY.setShowDescriptionInline(true);
        addModuleProperty(LINCS_ASSAY_TYPE_PROPERTY);
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public @Nullable Double getSchemaVersion()
    {
        return 22.001;
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        //return Collections.emptyList();
        BaseWebPartFactory runsList = new BaseWebPartFactory(LincsDataView.WEB_PART_NAME)
        {
            @Override
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new LincsDataView(portalCtx);
            }
        };
        return Collections.singleton(runsList);
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    protected void init()
    {
        addController(LincsController.NAME, LincsController.class);
        LincsSchema.register(this);
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(LincsManager.get().getSchemaName());
    }

    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new LincsContainerListener());

        DocImportListener docImportListener = new DocImportListener();
        ExperimentService service = ExperimentService.get();
        service.addExperimentListener(docImportListener);

        TargetedMSService tmsService = TargetedMSService.get();
        tmsService.registerSkylineDocumentImportListener(docImportListener);

        AdminLinkManager.getInstance().addListener((adminNavTree, container, user) -> {
            if (container.hasPermission(user, AdminPermission.class) && container.getActiveModules().contains(LincsModule.this))
            {
                adminNavTree.addChild(new NavTree("Manage PSP Credentials", new ActionURL(LincsController.ManageLincsClueCredentials.class, container)));
                adminNavTree.addChild(new NavTree("Manage Cromwell Config", new ActionURL(LincsController.CromwellConfigAction.class, container)));
            }
        });
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    public enum LincsAssay
    {
        P100("GCT File P100", "p100_comprehensive_report.skyr"),
        GCP("GCT File GCP", "gcp_comprehensive_report.skyr");

        private final String _reportName;  // Name of the R report that will be run to generate the L2 GCT for a Skyline document
        private final String _skylineReport; // Name of the Skyline report template file that will be used in the Cromwell workflow

        LincsAssay(String reportName, String skylineReport)
        {
            _reportName = reportName;
            _skylineReport = skylineReport;
        }

        public String getReportName()
        {
            return _reportName;
        }

        public String getSkylineReport()
        {
            return _skylineReport;
        }

        public static String getReportName(String assayName)
        {
            LincsAssay assay = LincsAssay.valueOf(assayName);
            return assay == null ? "Unknown Assay" : assay.getReportName();
        }
    }

    public enum LincsLevel
    {
        Two,Three,Four,Config
    }

    public static String getExt(LincsLevel level)
    {
        switch(level)
        {
            case Two:
                return ".gct";
            case Three:
                return "_LVL3.gct";
            case Four:
                return "_LVL4.gct";
            case Config:
                return ".cfg";
        }
        return StringUtils.EMPTY;
    }
}