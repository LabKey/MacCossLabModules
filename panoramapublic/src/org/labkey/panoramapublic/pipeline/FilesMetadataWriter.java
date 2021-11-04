package org.labkey.panoramapublic.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.FolderExportPermission;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class FilesMetadataWriter
{
    public static final String FILENAME = "files.xml";
    public static final String FILES = "files";
    public static final String FILE = "file";
    public static final String RELATIVE_PATH = "relative_path";
    public static final String COMMENT = "comment";

    public void write(Container container, boolean includeSubfolders, FileSystemFile vf, User user, Logger log) throws PipelineJobException
    {
        log.info("Exporting files metadata.");
        ExperimentService expSvc = ExperimentService.get();
        FileContentService fcs = FileContentService.get();
        writeFilesMetadata(vf, container, includeSubfolders, expSvc, fcs, user, log);
    }

    private void writeFilesMetadata(VirtualFile vf, Container container, boolean includeSubfolders,
                                    ExperimentService expSvc, FileContentService fcs, User user, Logger log) throws PipelineJobException
    {
        log.debug(String.format("[%s]  Writing files metadata to %s", container.getPath(), vf.getLocation()));

        try (PrintWriter writer = vf.getPrintWriter(FILENAME))
        {
            writeFilesXml(container, expSvc, fcs, writer, log);
        }
        catch (IOException | ParserConfigurationException | TransformerException e)
        {
            throw new PipelineJobException(String.format("Error writing %s in %s", FILENAME, vf.getLocation()));
        }
        if (includeSubfolders)
        {
            List<Container> children = ContainerManager.getChildren(container, user, FolderExportPermission.class);
            if (children.size() > 0)
            {
                var subfolders = vf.getDir("subfolders"); // TODO: Make SubfolderWriter.DIRECTORY_NAME public
                for (Container child : children)
                {
                    writeFilesMetadata(subfolders.getDir(child.getName()), child, true, expSvc, fcs, user, log);
                }
            }
        }
    }

    private void writeFilesXml(Container container, ExperimentService expSvc, FileContentService fcs, PrintWriter writer, Logger log)
            throws ParserConfigurationException, TransformerException
    {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        List<? extends ExpData> expDatasWithComments = getExpDatasWithComments(container, expSvc);

        Path fileRoot = fcs.getFileRootPath(container, FileContentService.ContentType.files);
        if (fileRoot == null)
        {
            log.error("Could not get file root for " + container.getPath());
            return;
        }

        Element root = doc.createElement(FILES);
        doc.appendChild(root);

        for (ExpData data: expDatasWithComments)
        {
            Element fileEl = doc.createElement(FILE);
            root.appendChild(fileEl);

            Path dataPath = data.getFilePath();
            if (dataPath != null)
            {
                // Write the path relative to the container file root
                Path relFilePath = fileRoot.relativize(dataPath);
                Element relPathNode = doc.createElement(RELATIVE_PATH);
                relPathNode.appendChild(doc.createTextNode(relFilePath.toString()));

                // Write the comment (description) for the ExpData
                Element commentNode = doc.createElement(COMMENT);
                commentNode.appendChild(doc.createTextNode(data.getComment()));

                fileEl.appendChild(relPathNode);
                fileEl.appendChild(commentNode);
            }
        }

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(writer);
        transformer.transform(source, result);
    }

    @NotNull
    private List<? extends ExpData> getExpDatasWithComments(Container container, ExperimentService expService)
    {
        return expService.getExpData(container).stream().filter(d -> !StringUtils.isBlank(d.getComment())).collect(Collectors.toList());
    }
}
