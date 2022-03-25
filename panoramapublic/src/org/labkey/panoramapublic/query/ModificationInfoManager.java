package org.labkey.panoramapublic.query;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.query.modification.ExperimentModInfo;
import org.labkey.panoramapublic.query.modification.ExperimentStructuralModInfo;

import java.util.Collections;
import java.util.List;

public class ModificationInfoManager
{
    private ModificationInfoManager() {}

    public static ExperimentStructuralModInfo getStructuralModInfo(int modInfoId)
    {
        return new TableSelector(PanoramaPublicManager.getTableInfoExperimentStructuralModInfo()).getObject(modInfoId, ExperimentStructuralModInfo.class);
    }

    public static ExperimentModInfo getIsotopeModInfo(int modInfoId)
    {
        return new TableSelector(PanoramaPublicManager.getTableInfoExperimentIsotopeModInfo()).getObject(modInfoId, ExperimentModInfo.class);
    }

    public static ExperimentStructuralModInfo getStructuralModInfo(long modificationId, int experimentAnnotationsId)
    {
        return getModInfo(modificationId, experimentAnnotationsId, PanoramaPublicManager.getTableInfoExperimentStructuralModInfo(), ExperimentStructuralModInfo.class);
    }

    public static ExperimentModInfo getIsotopeModInfo(long modificationId, int experimentAnnotationsId)
    {
        return getModInfo(modificationId, experimentAnnotationsId, PanoramaPublicManager.getTableInfoExperimentIsotopeModInfo(), ExperimentModInfo.class);
    }

    private static <T extends ExperimentModInfo> T getModInfo(long modificationId, int experimentAnnotationsId, TableInfo tableInfo, Class<T> cls)
    {
        var filter = new SimpleFilter().addCondition(FieldKey.fromParts("experimentAnnotationsId"), experimentAnnotationsId);
        filter.addCondition(FieldKey.fromParts("ModId"), modificationId);
        return new TableSelector(tableInfo, filter, null).getObject(cls);
    }

    public static ExperimentStructuralModInfo saveStructuralModInfo (ExperimentStructuralModInfo modInfo, User user)
    {
        return Table.insert(user, PanoramaPublicManager.getTableInfoExperimentStructuralModInfo(), modInfo);
    }

    public static ExperimentModInfo saveIsotopeModInfo (ExperimentModInfo modInfo, User user)
    {
        return Table.insert(user, PanoramaPublicManager.getTableInfoExperimentIsotopeModInfo(), modInfo);
    }

    public static void deleteIsotopeModInfo(ExperimentModInfo modInfo, int expAnnotationsId, Container container)
    {
        deleteModInfo(modInfo, expAnnotationsId, container, PanoramaPublicManager.getTableInfoExperimentIsotopeModInfo());
    }

    public static void deleteStructuralModInfo(ExperimentModInfo modInfo, int expAnnotationsId, Container container)
    {
        deleteModInfo(modInfo, expAnnotationsId, container, PanoramaPublicManager.getTableInfoExperimentStructuralModInfo());
    }

    private static void deleteModInfo(ExperimentModInfo modInfo, int expAnnotationsId, Container container, TableInfo tableInfo)
    {
        ExperimentAnnotations experimentAnnotations = ExperimentAnnotationsManager.get(expAnnotationsId, container);
        if (experimentAnnotations != null && modInfo.getExperimentAnnotationsId() == expAnnotationsId)
        {
            Table.delete(tableInfo, modInfo.getId());
        }
    }

    public static List<ExperimentStructuralModInfo> getStructuralModInfosForExperiment(int experimentAnnotationsId, Container container)
    {
        return getModInfosForExperiment(experimentAnnotationsId, container, PanoramaPublicManager.getTableInfoExperimentStructuralModInfo(), ExperimentStructuralModInfo.class);
    }

    public static List<ExperimentModInfo> getIsotopeModInfosForExperiment(int experimentAnnotationsId, Container container)
    {
        return getModInfosForExperiment(experimentAnnotationsId, container, PanoramaPublicManager.getTableInfoExperimentIsotopeModInfo(), ExperimentModInfo.class);
    }

    private static <T extends ExperimentModInfo> List<T> getModInfosForExperiment(int experimentAnnotationsId, Container container, TableInfo tableInfo, Class<T> cls)
    {
        var expAnnotations = ExperimentAnnotationsManager.get(experimentAnnotationsId);
        if (expAnnotations != null && expAnnotations.getContainer().equals(container))
        {
            var filter = new SimpleFilter().addCondition(FieldKey.fromParts("experimentAnnotationsId"), experimentAnnotationsId);
            return new TableSelector(tableInfo, filter, null).getArrayList(cls);
        }
        return Collections.emptyList();
    }

    public static boolean runsHaveStructuralModifications(List<Long> runIds, User user, Container container)
    {
        return runsHaveModifications(runIds, TargetedMSService.get().getTableInfoPeptideStructuralModification(), user, container);
    }

    public static boolean runsHaveIsotopeModifications(List<Long> runIds, User user, Container container)
    {
        return runsHaveModifications(runIds, TargetedMSService.get().getTableInfoPeptideIsotopeModification(), user, container);
    }

    private static boolean runsHaveModifications(List<Long> runIds, TableInfo tableInfo, User user, Container container)
    {
        if (runIds.size() == 0) return false;

        TargetedMSService svc = TargetedMSService.get();
        SQLFragment sql = new SQLFragment("SELECT mod.Id FROM ")
                .append(tableInfo, "mod")
                .append(" INNER JOIN ")
                .append(svc.getTableInfoGeneralMolecule(), "gm").append(" ON gm.Id = mod.peptideId ")
                .append(" INNER JOIN ")
                .append(svc.getTableInfoPeptideGroup(), "pg").append(" ON pg.Id = gm.peptideGroupId ")
                .append(" WHERE pg.runId IN (").append(StringUtils.join(runIds, ",")).append(") ");
        var schema = svc.getUserSchema(user, container).getDbSchema();
        sql = schema.getSqlDialect().limitRows(sql, 1);
        return new SqlSelector(schema, sql).exists();
    }
}
