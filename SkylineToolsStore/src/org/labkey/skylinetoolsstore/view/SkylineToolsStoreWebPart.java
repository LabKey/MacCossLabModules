/*
 * Copyright (c) 2017-2026 LabKey Corporation
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
package org.labkey.skylinetoolsstore.view;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.JspView;
import org.labkey.skylinetoolsstore.SkylineToolsStoreManager;
import org.labkey.skylinetoolsstore.model.SkylineTool;

import java.util.Arrays;
import java.util.List;

public class SkylineToolsStoreWebPart extends JspView<List<SkylineTool>>
{
    public SkylineToolsStoreWebPart()
    {
        super("/org/labkey/skylinetoolsstore/view/SkylineToolsStoreWebPart.jsp", null);
        setTitle("Skyline Tool Store");
        Container container = getViewContext().getContainer();
        User user = getViewContext().getUser();
        // Each tool version lives in a child folder of the store folder. Display only the tools contained in this store.
        setModelBean(Arrays.asList(SkylineToolsStoreManager.get().getToolsLatestForStoreListing(container)));
        setTitleHref(SkylineToolStoreUrls.getToolStoreHomeUrl(container, user));
    }
}
