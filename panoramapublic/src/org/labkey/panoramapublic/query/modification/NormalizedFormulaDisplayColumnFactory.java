package org.labkey.panoramapublic.query.modification;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.HtmlString;
import org.labkey.panoramapublic.proteomexchange.Formula;

/**
 * Used with the "NormalizedFormula" column of the custom query StructuralModifications.sql. The raw value in this column
 * is the formula from the Skyline document which may or may not be normalized. Displays the normalized formula if it
 * is parsed without errors. Otherwise displays the original formula.
 */
public class NormalizedFormulaDisplayColumnFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new DataColumn(colInfo)
        {
            @Override
            public Object getValue(RenderContext ctx)
            {
                String formula = ctx.get(colInfo.getFieldKey(), String.class);
                return Formula.normalizeFormula(formula);
            }

            @Override
            public Object getDisplayValue(RenderContext ctx)
            {
                return getValue(ctx);
            }
            @Override
            public @NotNull HtmlString getFormattedHtml(RenderContext ctx)
            {
                return HtmlString.of((String)getValue(ctx));
            }
        };
    }
}
