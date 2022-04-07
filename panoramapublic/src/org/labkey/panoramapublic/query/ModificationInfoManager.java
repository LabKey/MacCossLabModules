package org.labkey.panoramapublic.query;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.formSchema.Field;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicSchema;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.validation.Modification;
import org.labkey.panoramapublic.query.modification.ExperimentIsotopeModInfo;
import org.labkey.panoramapublic.query.modification.ExperimentModInfo;
import org.labkey.panoramapublic.query.modification.ExperimentStructuralModInfo;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ModificationInfoManager
{
    private ModificationInfoManager() {}

    public static ExperimentStructuralModInfo getStructuralModInfo(int modInfoId)
    {
        return new TableSelector(PanoramaPublicManager.getTableInfoExperimentStructuralModInfo()).getObject(modInfoId, ExperimentStructuralModInfo.class);
    }

    public static ExperimentIsotopeModInfo getIsotopeModInfo(int modInfoId)
    {
        ExperimentIsotopeModInfo modInfo = new TableSelector(PanoramaPublicManager.getTableInfoExperimentIsotopeModInfo()).getObject(modInfoId, ExperimentIsotopeModInfo.class);
        addAdditionalIsotopeUnimodInfos(modInfo);
        return modInfo;
    }

    private static void addAdditionalIsotopeUnimodInfos(ExperimentIsotopeModInfo modInfo)
    {
        if (modInfo != null)
        {
            List<ExperimentModInfo.UnimodInfo> unimodInfos = new TableSelector(PanoramaPublicManager.getTableInfoIsotopeUnimodInfo(),
                    new SimpleFilter(FieldKey.fromParts("ModInfoId"), modInfo.getId()), null).getArrayList(ExperimentModInfo.UnimodInfo.class);
            unimodInfos.forEach(modInfo::addUnimodInfo);
        }
    }

    public static ExperimentStructuralModInfo getStructuralModInfo(int modInfoId, Container container)
    {
        var expAnnotations = ExperimentAnnotationsManager.getExperimentInContainer(container);
        if (expAnnotations != null)
        {
            var modInfo = getStructuralModInfo(modInfoId);
            return modInfo != null && modInfo.getExperimentAnnotationsId() == expAnnotations.getId() ? modInfo : null;
        }
        return null;
    }

    public static ExperimentIsotopeModInfo getIsotopeModInfo(int modInfoId, Container container)
    {
        var expAnnotations = ExperimentAnnotationsManager.getExperimentInContainer(container);
        if (expAnnotations != null)
        {
            var modInfo = getIsotopeModInfo(modInfoId);
            return modInfo != null && modInfo.getExperimentAnnotationsId() == expAnnotations.getId() ? modInfo : null;
        }
        return null;
    }

    public static ExperimentStructuralModInfo getStructuralModInfo(long modificationId, int experimentAnnotationsId)
    {
        return getModInfo(modificationId, experimentAnnotationsId, PanoramaPublicManager.getTableInfoExperimentStructuralModInfo(), ExperimentStructuralModInfo.class);
    }

    public static ExperimentIsotopeModInfo getIsotopeModInfo(long modificationId, int experimentAnnotationsId)
    {
        ExperimentIsotopeModInfo modInfo = getModInfo(modificationId, experimentAnnotationsId, PanoramaPublicManager.getTableInfoExperimentIsotopeModInfo(), ExperimentIsotopeModInfo.class);
        addAdditionalIsotopeUnimodInfos(modInfo);
        return modInfo;
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

    public static ExperimentIsotopeModInfo saveIsotopeModInfo (ExperimentIsotopeModInfo modInfo, User user)
    {
        var savedModInfo = Table.insert(user, PanoramaPublicManager.getTableInfoExperimentIsotopeModInfo(), modInfo);
        for (ExperimentModInfo.UnimodInfo unimodInfo: modInfo.getAdditionalUnimodInfos())
        {
            unimodInfo.setModInfoId(savedModInfo.getId());
            Table.insert(user, PanoramaPublicManager.getTableInfoIsotopeUnimodInfo(), unimodInfo);
        }
        return savedModInfo;
    }

    public static void deleteIsotopeModInfo(int modInfoId, Container container, User user)
    {
        var expAnnotations = ExperimentAnnotationsManager.getExperimentInContainer(container);
        var modInfo = getIsotopeModInfo(modInfoId);
        deleteIsotopeModInfo(modInfo, expAnnotations, container, user);
    }

    public static void deleteIsotopeModInfo(ExperimentIsotopeModInfo modInfo, ExperimentAnnotations expAnnotations, Container container, User user)
    {
        if (modInfo != null && expAnnotations != null && modInfo.getExperimentAnnotationsId() == expAnnotations.getId())
        {
            try (DbScope.Transaction transaction = PanoramaPublicSchema.getSchema().getScope().ensureTransaction())
            {
                Table.delete(PanoramaPublicManager.getTableInfoIsotopeUnimodInfo(), new SimpleFilter(FieldKey.fromParts("modInfoId"), modInfo.getId()));
                Table.delete(PanoramaPublicManager.getTableInfoExperimentIsotopeModInfo(), modInfo.getId());
                DataValidationManager.removeModInfo(expAnnotations, container, modInfo.getModId(), Modification.ModType.Isotopic, user);
                transaction.commit();
            }
        }
    }

    public static void deleteIsotopeModInfoForExperiment(ExperimentAnnotations expAnnotations)
    {
        if (expAnnotations != null)
        {
            try (DbScope.Transaction transaction = PanoramaPublicSchema.getSchema().getScope().ensureTransaction())
            {
                List<Integer> modInfoIds = new TableSelector(PanoramaPublicManager.getTableInfoExperimentIsotopeModInfo(),
                        Collections.singleton("Id"),
                        new SimpleFilter(FieldKey.fromParts("experimentAnnotationsId"), expAnnotations.getId()),
                        null
                        ).getArrayList(Integer.class);
                if (modInfoIds.size() > 0)
                {
                    SimpleFilter filter = new SimpleFilter(new SimpleFilter.InClause(FieldKey.fromParts("modInfoId"), modInfoIds));
                    Table.delete(PanoramaPublicManager.getTableInfoIsotopeUnimodInfo(), filter);
                    Table.delete(PanoramaPublicManager.getTableInfoExperimentIsotopeModInfo(), new SimpleFilter(FieldKey.fromParts("experimentAnnotationsId"), expAnnotations.getId()));
                    // No need to update the data validation status.  The experiment is being deleted, everything in the data validation tables will be deleted.
                }
                transaction.commit();
            }
        }
    }

    public static void deleteStructuralModInfo(int modInfoId, Container container, User user)
    {
        ExperimentAnnotations experimentAnnotations = ExperimentAnnotationsManager.getExperimentInContainer(container);
        var modInfo = getStructuralModInfo(modInfoId);
        deleteStructuralModInfo(modInfo, experimentAnnotations, container, user);
    }

    public static void deleteStructuralModInfo(ExperimentModInfo modInfo, ExperimentAnnotations expAnnotations, Container container, User user)
    {
        if (modInfo != null && expAnnotations != null && modInfo.getExperimentAnnotationsId() == expAnnotations.getId())
        {
            try (DbScope.Transaction transaction = PanoramaPublicSchema.getSchema().getScope().ensureTransaction())
            {
                Table.delete(PanoramaPublicManager.getTableInfoExperimentStructuralModInfo(), modInfo.getId());
                DataValidationManager.removeModInfo(expAnnotations, container, modInfo.getModId(), Modification.ModType.Structural, user);
                transaction.commit();
            }
        }
    }

    public static List<ExperimentStructuralModInfo> getStructuralModInfosForExperiment(int experimentAnnotationsId, Container container)
    {
        return getModInfosForExperiment(experimentAnnotationsId, container, PanoramaPublicManager.getTableInfoExperimentStructuralModInfo(), ExperimentStructuralModInfo.class);
    }

    public static List<ExperimentIsotopeModInfo> getIsotopeModInfosForExperiment(int experimentAnnotationsId, Container container)
    {
        var modInfos = getModInfosForExperiment(experimentAnnotationsId, container, PanoramaPublicManager.getTableInfoExperimentIsotopeModInfo(), ExperimentIsotopeModInfo.class);
        modInfos.forEach(ModificationInfoManager::addAdditionalIsotopeUnimodInfos);
        return modInfos;
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

    public static List<Character> getIsotopeModificationSites(long isotopeModId, List<ITargetedMSRun> runs, User user)
    {
        Set<Character> sites = new HashSet<>();
        for (ITargetedMSRun run: runs)
        {
            sites.addAll(getIsotopeModificationSites(isotopeModId, run, user).stream()
                    .filter(aa -> !StringUtils.isBlank(aa))
                    .map(aa -> aa.charAt(0))
                    .collect(Collectors.toList()));
        }
        return sites.stream().collect(Collectors.toList());
    }

    private static List<String> getIsotopeModificationSites(long isotopeModId, ITargetedMSRun run, User user)
    {
        TargetedMSService svc = TargetedMSService.get();
        if (run != null)
        {
            SQLFragment sql = new SQLFragment("SELECT DISTINCT substring(pep.sequence, pimod.indexAA + 1, 1) FROM ")
                    .append(svc.getTableInfoPeptideIsotopeModification(), "pimod")
                    .append(" INNER JOIN ")
                    // Not enough to query the PeptideIsotopeModification table which will have rows for all the isotope label types defined in the document.
                    // We need to join to the Precursor and RunIsotopeModification tables to return only those rows where there is a precursor with a
                    // given isotope label type. The user can "pick children" for a peptide in Skyline.  If there are two isotope labels types (e.g. heavy and medium)
                    // the user may only pick the "heavy" label for a peptide, for example. The document's <peptide> element, however, lists all the label types
                    // as <implicit_heavy_modifications>. The information in these elements is what goes into the PeptideIsotopeModification table. We have to find
                    // the precursors for a peptide that have a label type (generalprecursor.isotopeLabelType) which includes the given isotopeModId.  The RunIsotopeModification
                    // table links label types to the modifications Ids included in that label type.
                    .append(svc.getTableInfoGeneralPrecursor(), "pre")
                    .append(" ON pre.generalMoleculeId = piMod.peptideId ")
                    .append(" INNER JOIN ")
                    .append("targetedms.RunIsotopeModification AS rim ")
                    .append(" ON rim.isotopeModId = pimod.isotopeModId AND rim.isotopeLabelId = pre.isotopeLabelId ")
                    .append(" INNER JOIN ")
                    .append("targetedms.peptide AS pep").append(" ON pep.Id = pimod.peptideId ")
                    .append(" INNER JOIN ")
                    .append(svc.getTableInfoGeneralMolecule(), "gm").append(" ON gm.Id = pep.Id ")
                    .append(" INNER JOIN ")
                    .append(svc.getTableInfoPeptideGroup(), "pg").append(" ON pg.Id = gm.peptideGroupId ")
                    .append(" WHERE pg.runId = ?").add(run.getId())
                    .append(" AND pimod.IsotopeModId = ?").add(isotopeModId);
            return new SqlSelector(svc.getUserSchema(user, run.getContainer()).getDbSchema(), sql).getArrayList(String.class);
        }
        return Collections.emptyList();
    }
}
