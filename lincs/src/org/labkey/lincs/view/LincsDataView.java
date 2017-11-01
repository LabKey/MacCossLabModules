/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.lincs.view;

import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.Sort;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ViewContext;
import org.labkey.lincs.LincsSchema;
import org.labkey.lincs.LincsDataTable;

/**
 * Created by vsharma on 8/18/2017.
 */
public class LincsDataView extends QueryView
{
    public static final String WEB_PART_NAME = "LINCS Data";

    public LincsDataView(ViewContext portalCtx)
    {
        super(new LincsSchema(portalCtx.getUser(), portalCtx.getContainer()));

        setTitle(WEB_PART_NAME);

        setSettings(createQuerySettings(portalCtx, WEB_PART_NAME));

        setShowDetailsColumn(false);
        setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
        setShowExportButtons(false);
        setShowBorders(true);
        setShadeAlternatingRows(true);

        setAllowableContainerFilterTypes(ContainerFilter.Type.Current,
                ContainerFilter.Type.CurrentAndSubfolders);

        setFrame(FrameType.PORTAL);
    }

    private QuerySettings createQuerySettings(ViewContext portalCtx, String dataRegionName)
    {
        QuerySettings settings = getSchema().getSettings(portalCtx, dataRegionName, LincsDataTable.NAME);
        settings.setShowReports(false);
        settings.setAllowCustomizeView(true);
        settings.setAllowChooseQuery(false);
        settings.setAllowChooseView(true);
        settings.setBaseSort(new Sort("-DetPlate"));
        return settings;
    }
}
