/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
package org.labkey.targetedms.view.publish;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.view.ShortURLRecord;

import java.util.Set;

/**
 * User: vsharma
 * Date: 8/12/2014
 * Time: 12:49 PM
 */
public class ShortUrlDisplayColumnFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new ShortUrlDisplayColumn(colInfo);
    }

    public class ShortUrlDisplayColumn extends DataColumn
    {
        public ShortUrlDisplayColumn(ColumnInfo colInfo)
        {
            super(colInfo);
        }

        @Override
        public String renderURL(RenderContext ctx)
        {
            String shortUrl = getShortUrlDisplayValue(ctx);
            return shortUrl != null ? shortUrl : super.renderURL(ctx);
        }

        @Override @NotNull
        public String getFormattedValue(RenderContext ctx)
        {
            String shortUrl = getShortUrlDisplayValue(ctx);
            return shortUrl != null ? shortUrl : super.getFormattedValue(ctx);
        }

        private String getShortUrlDisplayValue(RenderContext ctx)
        {
            Object shortUrl = ctx.get(FieldKey.fromString(getColumnInfo().getFieldKey(), "ShortUrl"));
            if(shortUrl != null)
            {
                return ShortURLRecord.renderShortURL((String) shortUrl);
            }
            else
            {
                return null;
            }
        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            super.addQueryFieldKeys(keys);
            FieldKey parentFieldKey = getColumnInfo().getFieldKey();
            keys.add(FieldKey.fromString(parentFieldKey, "ShortUrl"));
        }
    }
}
