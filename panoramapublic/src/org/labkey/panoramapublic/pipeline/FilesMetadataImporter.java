package org.labkey.panoramapublic.pipeline;

import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.writer.VirtualFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public class FilesMetadataImporter
{
    public void doImport(Container container, boolean includeSubfolders, VirtualFile vf, User user, Logger log) throws PipelineJobException
    {
        log.info("Importing files metadata.");
        FileContentService fcs = FileContentService.get();
        ExperimentService expSvc = ExperimentService.get();
        doImport(container, includeSubfolders, vf, fcs, expSvc, user, log);
    }

    private void doImport(Container container, boolean includeSubfolders, VirtualFile vf,
                          FileContentService fcs, ExperimentService expSvc, User user, Logger log) throws PipelineJobException
    {
        readXmlAndImport(container, vf, fcs, expSvc, user, log);

        if (includeSubfolders)
        {
            List<Container> children = ContainerManager.getChildren(container);
            if (children.size() > 0)
            {
                var subfolders = vf.getDir("subfolders"); // TODO: Make SubfolderWriter.DIRECTORY_NAME public
                for (Container child : children)
                {
                    doImport(child, true, subfolders.getDir(child.getName()), fcs, expSvc, user, log);
                }
            }
        }
    }

    private void readXmlAndImport(Container container, VirtualFile vf, FileContentService fcs, ExperimentService expSvc, User user, Logger log) throws PipelineJobException
    {
        log.debug(String.format("[%s]  Reading files metadata from %s", container.getPath(), vf.getLocation()));

        try (InputStream is = vf.getInputStream(FilesMetadataWriter.FILENAME))
        {
            if (is == null)
            {
                log.error(String.format("Could not find expected file %s in %s.", FilesMetadataWriter.FILENAME,  vf.getLocation()));
                return;
            }

            Path fileRoot = fcs.getFileRootPath(container, FileContentService.ContentType.files);
            if (fileRoot == null)
            {
                log.error("Could not get file root for " + container.getPath());
                return;
            }

            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(is));
            NodeList nodes = document.getElementsByTagName(FilesMetadataWriter.FILE);
            for(int i = 0; i < nodes.getLength(); i++)
            {
                Node node = nodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE)
                {
                    Element fileEl = (Element)node;
                    String relPath = fileEl.getElementsByTagName(FilesMetadataWriter.RELATIVE_PATH).item(0).getTextContent();
                    String comment = fileEl.getElementsByTagName(FilesMetadataWriter.COMMENT).item(0).getTextContent();
                    if (relPath != null && comment != null)
                    {
                        ExpData data = expSvc.getExpDataByURL(fileRoot.resolve(relPath), container);
                        if (data != null)
                        {
                            try
                            {
                                data.setComment(user, comment);
                            }
                            catch (ValidationException validationErrors)
                            {
                                log.error("Error setting comment on " + data.getFilePath(), validationErrors);
                            }
                        }
                    }
                }
            }
        }
        catch (IOException | ParserConfigurationException | SAXException e)
        {
            throw new PipelineJobException(String.format("Error reading %s in %s.", FilesMetadataWriter.FILENAME, vf.getLocation()), e);
        }
    }
}
