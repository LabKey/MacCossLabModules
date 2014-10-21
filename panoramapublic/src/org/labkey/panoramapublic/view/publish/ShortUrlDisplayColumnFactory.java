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
