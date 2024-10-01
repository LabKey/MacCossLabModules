package org.labkey.panoramapublic.query;

import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.WritablePropertyMap;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.catalog.CatalogEntrySettings;
import org.labkey.panoramapublic.catalog.CatalogImageAttachmentParent;
import org.labkey.panoramapublic.model.CatalogEntry;
import org.labkey.panoramapublic.model.ExperimentAnnotations;

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;


public class CatalogEntryManager
{
    public static String PANORAMA_PUBLIC_CATALOG = "Panorama Public catalog";
    public static String CATALOG_ENTRY_ENABLED = "Catalog entry enabled";
    public static String CATALOG_MAX_FILE_SIZE = "Catalog entry max file size";
    public static String CATALOG_IMG_WIDTH = "Catalog image preferred width";
    public static String CATALOG_IMG_HEIGHT = "Catalog image preferred height";
    public static String CATALOG_TEXT_CHAR_LIMIT = "Catalog text character limit";
    public static String CATALOG_MAX_ENTRIES = "Maximum entries to display in the slideshow";

    public enum CatalogEntryType { All, Approved, Pending, Rejected }

    public static CatalogEntry get(int catalogEntryId)
    {
        return new TableSelector(PanoramaPublicManager.getTableInfoCatalogEntry(),null, null).getObject(catalogEntryId, CatalogEntry.class);
    }
    private static void save(CatalogEntry entry, User user)
    {
        Table.insert(user, PanoramaPublicManager.getTableInfoCatalogEntry(), entry);
    }

    public static void update(CatalogEntry entry, User user)
    {
        Table.update(user, PanoramaPublicManager.getTableInfoCatalogEntry(), entry, entry.getId());
    }

    public static void saveEntry(@NotNull CatalogEntry entry, @NotNull AttachmentFile imageFile,
                                 @NotNull ExperimentAnnotations expAnnotations, User user) throws IOException
    {
        try(DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
        {
            save(entry, user);

            saveImageAttachment(imageFile, expAnnotations, user);

            transaction.commit();
        }
    }

    private static void saveImageAttachment(@NotNull AttachmentFile imageFile, @NotNull ExperimentAnnotations expAnnotations, User user) throws IOException
    {
        AttachmentParent ap = new CatalogImageAttachmentParent(expAnnotations.getShortUrl(), expAnnotations.getContainer());
        AttachmentService svc = AttachmentService.get();
        svc.deleteAttachments(ap); // If there is an existing attachment, delete it.
        svc.addAttachments(ap, Collections.singletonList(imageFile), user);
    }

    public static void updateEntry(@NotNull CatalogEntry entry, @NotNull ExperimentAnnotations expAnnotations,
                                   @Nullable AttachmentFile imageFile, User user) throws IOException
    {
        try(DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
        {
            update(entry, user);

            if (imageFile != null)
            {
                // Save the new image file if one was uploaded.
                saveImageAttachment(imageFile, expAnnotations, user);
            }

            transaction.commit();
        }
    }

    public static @Nullable CatalogEntry getEntryForShortUrl(@Nullable ShortURLRecord record)
    {
        if (record != null)
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("ShortUrl"), record.getEntityId());
            return new TableSelector(PanoramaPublicManager.getTableInfoCatalogEntry(), filter, null).getObject(CatalogEntry.class);
        }
        return null;
    }

    public static @Nullable CatalogEntry getEntryForExperiment(@NotNull ExperimentAnnotations expAnnotations)
    {
        return getEntryForShortUrl(expAnnotations.getShortUrl());
    }

    public static void deleteEntryForExperiment(CatalogEntry entry, @NotNull ExperimentAnnotations expAnnotations, User user)
    {
        if (entry != null)
        {
            try(DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
            {
                AttachmentParent ap = new CatalogImageAttachmentParent(expAnnotations.getShortUrl(), expAnnotations.getContainer());
                AttachmentService.get().deleteAttachment(ap, entry.getImageFileName(), user);

                Table.delete(PanoramaPublicManager.getTableInfoCatalogEntry(), new SimpleFilter(FieldKey.fromParts("id"), entry.getId()));

                transaction.commit();
            }
        }
    }

    public static void deleteEntryForExperiment(@NotNull ExperimentAnnotations expAnnotations, User user)
    {
        deleteEntryForExperiment(getEntryForExperiment(expAnnotations), expAnnotations, user);
    }

    public static void moveEntry(@NotNull ExperimentAnnotations previousCopy, @NotNull ExperimentAnnotations targetExperiment, User user) throws IOException
    {
        AttachmentService svc = AttachmentService.get();
        CatalogEntry entry = getEntryForShortUrl(targetExperiment.getShortUrl());
        if (entry != null)
        {
            AttachmentParent ap = new CatalogImageAttachmentParent(targetExperiment.getShortUrl(), previousCopy.getContainer());
            svc.moveAttachments(targetExperiment.getContainer(), List.of(ap), user);
        }
    }

    public static List<CatalogEntry> getEntries(@NotNull CatalogEntryType entryType, int entryCount)
    {
        SimpleFilter filter = getFilterForEntryType(entryType);

        List<Integer> entryIdList = new TableSelector(PanoramaPublicManager.getTableInfoCatalogEntry(),
                Collections.singleton("Id"), filter, new Sort(FieldKey.fromParts("Id")))
                .getArrayList(Integer.class);

        if (entryCount < entryIdList.size())
        {
            filter.addInClause(FieldKey.fromParts("Id"), getSubList(entryIdList, entryCount, LocalDate.now()));
        }

        return new TableSelector(PanoramaPublicManager.getTableInfoCatalogEntry(),
                filter,
                null).getArrayList(CatalogEntry.class);
    }

    public static boolean hasEntries(CatalogEntryType entryType)
    {
        return new TableSelector(PanoramaPublicManager.getTableInfoCatalogEntry(), getFilterForEntryType(entryType), null).exists();
    }

    private static SimpleFilter getFilterForEntryType(CatalogEntryType entryType)
    {
        FieldKey fieldKey = FieldKey.fromParts("Approved");
        return switch (entryType)
            {
                case Approved -> new SimpleFilter(fieldKey, true);
                case Rejected -> new SimpleFilter(fieldKey, false);
                case Pending -> new SimpleFilter(fieldKey, null, CompareType.ISBLANK);
                case All -> new SimpleFilter();
            };
    }

    private static List<Integer> getSubList(List<Integer> entryIdList, int displayCount, LocalDate localDate)
    {
        if (displayCount >= entryIdList.size())
        {
            return new ArrayList<>(entryIdList);
        }
        // Use the number of days since the "unix epoch date", and the display count to index into the array of all entries
        // Example:
        // entryIdList: [1, 3, 4, 5, 8, 10, 11]
        // displayCount: 3
        // It will take 3 iterations (days) to view all entries
        // Day 23: startIndex = (23 * 3) % 7 = 6; sublist -> [11, 1, 3]
        // Day 24: startIndex = (24 * 3) % 7 = 2; sublist -> [4, 5, 8]
        // Day 25: startIndex = (25 * 3) % 7 = 5; sublist -> [10, 11, 1]
        long days = ChronoUnit.DAYS.between(LocalDate.EPOCH, localDate);
        int startIndex = (int) ((days * displayCount) % entryIdList.size());
        List<Integer> subList = new ArrayList<>(displayCount);
        subList.addAll(entryIdList.subList(startIndex, Math.min(startIndex + displayCount, entryIdList.size())));
        if (subList.size() < displayCount)
        {
            // Wrap around to get the rest of the entries
            subList.addAll(entryIdList.subList(0, displayCount - subList.size()));
        }
        return subList;
    }

    public static CatalogEntrySettings getCatalogEntrySettings()
    {
        WritablePropertyMap map = PropertyManager.getNormalStore().getWritableProperties(PANORAMA_PUBLIC_CATALOG, false);
        if (map != null)
        {
            if (Boolean.parseBoolean(map.get(CATALOG_ENTRY_ENABLED)))
            {
                return new CatalogEntrySettings(
                        NumberUtils.toLong(map.get(CATALOG_MAX_FILE_SIZE), CatalogEntrySettings.MAX_FILE_SIZE),
                        NumberUtils.toInt(map.get(CATALOG_IMG_WIDTH), CatalogEntrySettings.IMG_WIDTH),
                        NumberUtils.toInt(map.get(CATALOG_IMG_HEIGHT), CatalogEntrySettings.IMG_HEIGHT),
                        NumberUtils.toInt(map.get(CATALOG_TEXT_CHAR_LIMIT), CatalogEntrySettings.MAX_TEXT_CHARS),
                        NumberUtils.toInt(map.get(CATALOG_MAX_ENTRIES), CatalogEntrySettings.MAX_ENTRIES)
                        );
            }
        }
        return CatalogEntrySettings.DISABLED;
    }

    public static void saveCatalogEntrySettings(boolean enabled, @Nullable Long maxFileSize, @Nullable Integer imgWidth,
                                                @Nullable Integer imgHeight, @Nullable Integer maxTextChars,
                                                @Nullable Integer maxEntries)
    {
        WritablePropertyMap map = PropertyManager.getNormalStore().getWritableProperties(CatalogEntryManager.PANORAMA_PUBLIC_CATALOG, true);
        map.put(CatalogEntryManager.CATALOG_ENTRY_ENABLED, Boolean.toString(enabled));
        map.put(CatalogEntryManager.CATALOG_MAX_FILE_SIZE, maxFileSize != null ? String.valueOf(maxFileSize) : null);
        map.put(CatalogEntryManager.CATALOG_IMG_WIDTH, imgWidth !=null ? String.valueOf(imgWidth) : null);
        map.put(CatalogEntryManager.CATALOG_IMG_HEIGHT, imgHeight != null ? String.valueOf(imgHeight) : null);
        map.put(CatalogEntryManager.CATALOG_TEXT_CHAR_LIMIT, maxTextChars != null ? String.valueOf(maxTextChars) : null);
        map.put(CatalogEntryManager.CATALOG_MAX_ENTRIES, maxEntries != null ? String.valueOf(maxEntries) : null);
        map.save();
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testGetSubList()
        {
            List<Integer> testList = List.of(11, 5, 4, 3, 8, 10, 1);
            testGetSubList(testList, 3);

            testGetSubList(1000, 25);
            testGetSubList(25, 25);
            testGetSubList(26, 100);
        }

        private void testGetSubList(int allEntriesSize, int sublistSize)
        {
            List<Integer> allEntries = IntStream.rangeClosed(1, allEntriesSize).boxed().toList();
            testGetSubList(allEntries, sublistSize);
        }

        private void testGetSubList(List<Integer> allEntries, int sublistSize)
        {
            LocalDate now = LocalDate.now();
            // Number of iterations needed for complete coverage of allEntries
            int iterToCompleteCoverage = (int) Math.ceil(allEntries.size() / (double) sublistSize);

            Set<Integer> selected = new HashSet<>(allEntries.size());
            iterate(0, iterToCompleteCoverage, allEntries, sublistSize, now, selected);
            // Do a few more iterations
            iterate(iterToCompleteCoverage, iterToCompleteCoverage + 100, allEntries, sublistSize, now, selected);
        }

        private void iterate(int start, int end, List<Integer> allEntries, int sublistSize,  LocalDate now, Set<Integer> selected)
        {
            for (int i = start; i < end; i++)
            {
                LocalDate localDate = now.plusDays(i); // go to the next day
                List<Integer> sublist = getSubList(allEntries, sublistSize, localDate);
                assertEquals("Unexpected number of entries in sublist.", Math.min(sublistSize, allEntries.size()), sublist.size());
                if (end <= allEntries.size() / sublistSize)
                {
                    assertEquals("Unexpected number of entries in selected list at iteration " + i,
                            Math.min(i * sublistSize, allEntries.size()), selected.size());
                }
                selected.addAll(sublist);
            }
            assertEquals("Unexpected number of selected entries", allEntries.size(), selected.size());
            List<Integer> allEntriesSorted = new ArrayList<>(allEntries);
            Collections.sort(allEntriesSorted);
            List<Integer> selectedSetSorted = new ArrayList<>(selected);
            Collections.sort(selectedSetSorted);
            assertEquals("Selected list is not the same as the original list.",allEntriesSorted, selectedSetSorted);
        }
    }
}
