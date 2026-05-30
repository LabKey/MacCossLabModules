/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.test.components.panoramapublic;

import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.WebTestHelper;
import org.labkey.test.components.BodyWebPart;
import org.labkey.test.util.DataRegionTable;
import org.openqa.selenium.WebElement;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TargetedMsExperimentWebPart extends BodyWebPart <TargetedMsExperimentWebPart.ElementCache>
{
    public static final String DEFAULT_TITLE = "Targeted MS Experiment";
    private DataRegionTable _dataRegionTable;
    private final BaseWebDriverTest _test;

    public TargetedMsExperimentWebPart(BaseWebDriverTest test)
    {
        this(test, 0);
    }

    public TargetedMsExperimentWebPart(BaseWebDriverTest test, int index)
    {
        super(test.getDriver(), DEFAULT_TITLE, index);
        _test = test;
    }

    public DataRegionTable getDataRegion()
    {
        if (_dataRegionTable == null)
            _dataRegionTable = DataRegionTable.DataRegion(_test.getDriver()).find(getComponentElement());
        return _dataRegionTable;
    }

    public TargetedMsExperimentInsertPage startInsert()
    {
        findElement(Locator.linkContainingText("Create New Experiment")).click();
        return new TargetedMsExperimentInsertPage(_test.getDriver());
    }

    public void clickSubmit()
    {
        getWrapper().clickAndWait(Locator.linkWithText("Submit"));
    }

    public void clickResubmit()
    {
        clickLink(elementCache().resubmitLink, "Expected to see a \"Resubmit\" button");
    }

    public boolean hasResubmitLink()
    {
        return elementCache().resubmitLink.isDisplayed();
    }

    public void clickMoreDetails()
    {
        clickLink(elementCache().moreDetailsLink, "Expected to find a 'More Details' link");
    }

    public String getAccessLink()
    {
        WebElement element = elementCache().accessUrlTag;
        assertNotNull("Expected to find the accessUrl tag", element);
        return element.getAttribute("href");
    }

    public String getDataVersion()
    {
        WebElement element = elementCache().dataVersionTag;
        return element != null ? element.getText() : null;
    }

    public boolean hasMakePublicButton()
    {
        return elementCache().makePublicButton.isDisplayed();
    }

    public void clickMakePublic()
    {
        clickLink(elementCache().makePublicButton, "Expected to see a \"Make Public\" button");
    }

    public Integer getExperimentAnnotationsId()
    {
        WebElement moreDetailsLink = elementCache().moreDetailsLink;
        if (moreDetailsLink != null)
        {
            String href = moreDetailsLink.getAttribute("href");
            try
            {
                return Integer.parseInt(WebTestHelper.parseUrlQuery(new URL(href)).get("id"));
            }
            catch (MalformedURLException e)
            {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public void clickAddPublication()
    {
        clickLink(elementCache().addPublicationButton, "Expected to see a \"Add Publication\" button");
    }

    private void clickLink(WebElement link, String message)
    {
        assertTrue(message , link.isDisplayed());
        getWrapper().clickAndWait(link);
    }

    public void checkCatalogEntryLink(boolean expectLink)
    {
        if (expectLink)
        {
            assertNotNull("Expected to find the catalog entry link", elementCache().catalogEntryLink);
        }
        else
        {
            assertNull("Unexpected catalog entry link", elementCache().catalogEntryLink);
        }
    }

    @Override
    protected TargetedMsExperimentWebPart.ElementCache newElementCache()
    {
        return new TargetedMsExperimentWebPart.ElementCache();
    }

    protected class ElementCache extends BodyWebPart.ElementCache
    {
        private final WebElement resubmitLink = Locator.linkContainingText("Resubmit").findWhenNeeded(this);
        private final WebElement moreDetailsLink = Locator.linkContainingText("More Details").findWhenNeeded(this);
        private final WebElement accessUrlTag = Locator.tagWithAttribute("span", "id", "accessUrl").childTag("a").findWhenNeeded(this);
        private final WebElement dataVersionTag = Locator.tagWithAttribute("span", "id", "publishedDataVersion").descendant("span").findOptionalElement(this).orElse(null);
        private final WebElement makePublicButton = Locator.linkContainingText("Make Public").findWhenNeeded(this);
        private final WebElement addPublicationButton = Locator.linkContainingText("Add Publication").findWhenNeeded(this);
        private final WebElement catalogEntryLink = Locator.tagWithAttributeContaining("a", "class", "catalogIcon").findOptionalElement(this).orElse(null);
    }
}
