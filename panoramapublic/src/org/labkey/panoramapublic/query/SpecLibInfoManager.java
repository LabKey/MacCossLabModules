package org.labkey.panoramapublic.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ISpectrumLibrary;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.speclib.SpecLibInfo;
import org.labkey.panoramapublic.model.speclib.SpectralLibrary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SpecLibInfoManager
{
    private SpecLibInfoManager() {}

    public static SpecLibInfo get(int id, Container container)
    {
        var sql = new SQLFragment("SELECT slib.* FROM ")
                .append(PanoramaPublicManager.getTableInfoSpecLibInfo(), "slib")
                .append(" INNER JOIN ")
                .append(PanoramaPublicManager.getTableInfoExperimentAnnotations(), "exp")
                .append(" ON exp.Id = slib.experimentAnnotationsId ")
                .append(" WHERE slib.Id = ? ").add(id)
                .append(" AND exp.Container = ?").add(container);
         return new SqlSelector(PanoramaPublicManager.getSchema(), sql).getObject(SpecLibInfo.class);
    }

    public static SpecLibInfo getSpecLibInfo(int id)
    {
        return new TableSelector(PanoramaPublicManager.getTableInfoSpecLibInfo()).getObject(id, SpecLibInfo.class);
    }

    public static SpecLibInfo save(SpecLibInfo specLibInfo, User user)
    {
        return Table.insert(user, PanoramaPublicManager.getTableInfoSpecLibInfo(), specLibInfo);
    }

    public static SpecLibInfo update(SpecLibInfo specLibInfo, User user)
    {
        return Table.update(user, PanoramaPublicManager.getTableInfoSpecLibInfo(), specLibInfo, specLibInfo.getId());
    }

    public static List<SpecLibInfo> getForExperiment(int experimentAnnotationsId, Container container)
    {
        var expAnnotations = ExperimentAnnotationsManager.get(experimentAnnotationsId);
        if (expAnnotations != null && expAnnotations.getContainer().equals(container))
        {
            var filter = new SimpleFilter().addCondition(FieldKey.fromParts("experimentAnnotationsId"), experimentAnnotationsId);
            return new TableSelector(PanoramaPublicManager.getTableInfoSpecLibInfo(), filter, null).getArrayList(SpecLibInfo.class);
        }
        return Collections.emptyList();
    }

    public static @Nullable SpectralLibrary getSpectralLibrary(long specLibId, @Nullable Container container, User user)
    {
        var library = TargetedMSService.get().getLibrary(specLibId, container, user);
        return library != null ? new SpectralLibrary(library) : null;
    }

    public static List<SpectralLibrary> getLibraries(Collection<Long> specLibIds, User user)
    {
        var libraries = new ArrayList<ISpectrumLibrary>();
        var svc = TargetedMSService.get();
        specLibIds.forEach(id -> libraries.add(svc.getLibrary(id, null, user)));
        return libraries.stream().filter(Objects::nonNull).map(SpectralLibrary::new).collect(Collectors.toList());
    }

    public static void deleteSpecLibInfo(int specLibInfoId, Container container)
    {
        ExperimentAnnotations experimentAnnotations = ExperimentAnnotationsManager.getExperimentInContainer(container);
        SpecLibInfo specLibInfo = getSpecLibInfo(specLibInfoId);
        deleteSpecLibInfo(specLibInfo, experimentAnnotations);
    }

    public static void deleteSpecLibInfo(SpecLibInfo specLibInfo, ExperimentAnnotations expAnnotations)
    {
        if (specLibInfo != null && expAnnotations != null && specLibInfo.getExperimentAnnotationsId() == expAnnotations.getId())
        {
           Table.delete(PanoramaPublicManager.getTableInfoSpecLibInfo(), specLibInfo.getId());
        }
    }
}
