package org.labkey.panoramapublic.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.panoramapublic.PanoramaPublicManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ContainerJoin
{
    private final List<InnerJoinClause> _joinList;

    private static final String JOIN_TABLE_COL = "id";
    private static final String CONTAINER = "Container";
    private static final String JOIN_TABLE_ALIAS_PREFIX = "J";

    public static final ContainerJoin ExpAnnotJoin = new ContainerJoin("ExperimentAnnotationsId", PanoramaPublicManager.getTableInfoExperimentAnnotations());
    public static final ContainerJoin DataValidatoinJoin = ExpAnnotJoin.addJoin("ValidationId", PanoramaPublicManager.getTableInfoDataValidation());
    public static final ContainerJoin SpecLibValidationJoin = DataValidatoinJoin.addJoin("SpecLibValidationId", PanoramaPublicManager.getTableInfoSpecLibValidation());
    public static final ContainerJoin SkyDocValidationJoin = DataValidatoinJoin.addJoin("SkylineDocValidationId", PanoramaPublicManager.getTableInfoSkylineDocValidation());
    public static final ContainerJoin ModificationJoin = DataValidatoinJoin.addJoin("ModificationValidationId", PanoramaPublicManager.getTableInfoModificationValidation());

    private ContainerJoin(List<InnerJoinClause> joinList)
    {
        _joinList = joinList;
    }

    public ContainerJoin(@NotNull String joinCol, @NotNull TableInfo joinTable)
    {
        this(List.of(new InnerJoinClause(null, joinCol, joinTable, JOIN_TABLE_ALIAS_PREFIX + "1", JOIN_TABLE_COL)));
    }

    // Example:
    // If the join was INNER JOIN ExperimentAnnotations J1 ON J1.Id = ExperimentAnnotationsId
    // The join returned by addJoin("DataValidationId", PanoramaPublicManager.getTableInfoDataValidation()) will be:
    // INNER JOIN DataValidation J1 ON J1.Id = DataValidationId
    // INNER JOIN ExperimentAnnotations J2 ON J2.Id = J1.ExperimentAnnotationsId
    public ContainerJoin addJoin(@NotNull String joinCol, @NotNull TableInfo table)
    {
        List<InnerJoinClause> newJoinList = new ArrayList<>();
        int aliasIndex = 1;
        newJoinList.add(new InnerJoinClause(null, joinCol, table, JOIN_TABLE_ALIAS_PREFIX + aliasIndex, JOIN_TABLE_COL));
        for (InnerJoinClause join: _joinList)
        {
            newJoinList.add(new InnerJoinClause(JOIN_TABLE_ALIAS_PREFIX + aliasIndex++, join.getJoinCol(),
                    join.getJoinTable(), JOIN_TABLE_ALIAS_PREFIX + aliasIndex, join.getJoinTableCol()));
        }
        return new ContainerJoin(newJoinList);
    }

    public @NotNull SQLFragment getJoinSql()
    {
        SQLFragment sql = new SQLFragment();
        _joinList.stream().map(InnerJoinClause::toSql).forEach(sql::append);
        return sql;
    }

    public @NotNull SQLFragment getContainerSql()
    {
        SQLFragment sql = new SQLFragment();
        if (_joinList.size() > 0)
        {
            // We expect the last table in the join sequence to have the container column
            sql.append(getLastJoinTableAlias()).append(".");
        }
        return sql.append(CONTAINER);
    }

    private String getLastJoinTableAlias()
    {
        return _joinList.get(_joinList.size() - 1).getJoinTableAlias();
    }

    public @Nullable FieldKey getContainerFieldKey()
    {
        if (_joinList.size() > 0)
        {
            var parts = _joinList.stream().map(InnerJoinClause::getJoinCol).collect(Collectors.toList());
            parts.add(CONTAINER);
            return FieldKey.fromParts(parts);
        }
        return null;
    }

    private static class InnerJoinClause
    {
        private final String _tableAlias;
        private final String _joinCol;
        private final TableInfo _joinTable;
        private final String _joinTableAlias;
        private final String _joinTableCol;

        public InnerJoinClause(@Nullable String tableAlias, @NotNull String joinCol, @NotNull TableInfo joinTable, @NotNull String joinTableAlias, @NotNull String joinTableCol)
        {
            _tableAlias = tableAlias;
            _joinCol = joinCol;
            _joinTable = joinTable;
            _joinTableAlias = joinTableAlias;
            _joinTableCol = joinTableCol;
        }

        private SQLFragment toSql()
        {
            return new SQLFragment(" INNER JOIN ")
                    .append(_joinTable, _joinTableAlias)
                    .append(" ON ")
                    .append(_joinTableAlias).append(".").append(_joinTableCol)
                    .append(" = ")
                    .append(_tableAlias != null ? _tableAlias + "." : "").append(_joinCol)
                    .append(" ");
        }

        private String getJoinCol()
        {
            return _joinCol;
        }

        private String getJoinTableAlias()
        {
            return _joinTableAlias;
        }

        public TableInfo getJoinTable()
        {
            return _joinTable;
        }

        public String getJoinTableCol()
        {
            return _joinTableCol;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testContainerJoin()
        {
            ContainerJoin cj = new ContainerJoin("ExperimentAnnotationsId", PanoramaPublicManager.getTableInfoExperimentAnnotations());
            assertEquals("INNER JOIN panoramapublic.experimentannotations J1 ON J1.id = ExperimentAnnotationsId", cj.getJoinSql().getSQL().trim());
            assertEquals("J1.Container", cj.getContainerSql().getRawSQL().trim());
            assertEquals(FieldKey.fromParts("ExperimentAnnotationsId", "Container"), cj.getContainerFieldKey());

            cj = cj.addJoin("DataValidationId", PanoramaPublicManager.getTableInfoDataValidation());
            assertEquals(
                    "INNER JOIN panoramapublic.datavalidation J1 ON J1.id = DataValidationId " +
                    " INNER JOIN panoramapublic.experimentannotations J2 ON J2.id = J1.ExperimentAnnotationsId", cj.getJoinSql().getSQL().trim());
            assertEquals("J2.Container", cj.getContainerSql().getRawSQL().trim());
            assertEquals(FieldKey.fromParts("DataValidationId", "ExperimentAnnotationsId", "Container"), cj.getContainerFieldKey());

            cj = cj.addJoin("SkylineDocValidationId", PanoramaPublicManager.getTableInfoSkylineDocValidation());
            assertEquals(
                    "INNER JOIN panoramapublic.skylinedocvalidation J1 ON J1.id = SkylineDocValidationId " +
                    " INNER JOIN panoramapublic.datavalidation J2 ON J2.id = J1.DataValidationId " +
                            " INNER JOIN panoramapublic.experimentannotations J3 ON J3.id = J2.ExperimentAnnotationsId", cj.getJoinSql().getSQL().trim());
            assertEquals("J3.Container", cj.getContainerSql().getRawSQL().trim());
            assertEquals(FieldKey.fromParts("SkylineDocValidationId", "DataValidationId", "ExperimentAnnotationsId", "Container"), cj.getContainerFieldKey());
        }
    }
}
