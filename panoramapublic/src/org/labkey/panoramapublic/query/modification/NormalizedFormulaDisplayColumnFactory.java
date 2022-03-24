package org.labkey.panoramapublic.query.modification;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.panoramapublic.proteomexchange.Formula;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

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
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                String formula = ctx.get(colInfo.getFieldKey(), String.class);
                if (formula != null)
                {
                    out.write(Formula.normalizeFormula(formula));
                }
            }
        };
    }
}
