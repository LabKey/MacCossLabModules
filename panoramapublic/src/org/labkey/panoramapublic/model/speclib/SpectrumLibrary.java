package org.labkey.panoramapublic.model.speclib;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.targetedms.ISpectrumLibrary;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.targetedms.TargetedMSUrls;
import org.labkey.api.util.DOM;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Link;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.WebdavService;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.speclib.LibraryType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.EM;
import static org.labkey.api.util.DOM.SPAN;
import static org.labkey.api.util.DOM.at;

public class SpectrumLibrary implements ISpectrumLibrary
{
    private final long _id;
    private final long _runId;
    private final String _name;
    private final String _fileNameHint;
    private final String _skylineLibraryId;  // lsid in <bibliospec_lite_library> element, id in others
    private final String _revision;
    private final String _libraryType;

    private ITargetedMSRun _run;
    private boolean _runInitialized;
    private Path _libFilePath;
    private boolean _pathInitialized;
    private long _fileSize = 0L;

    public SpectrumLibrary(@NotNull ISpectrumLibrary library)
    {
        _id = library.getId();
        _runId = library.getRunId();
        _libraryType = library.getLibraryType();
        _name = library.getName();
        _fileNameHint = library.getFileNameHint();
        _skylineLibraryId = library.getSkylineLibraryId();
        _revision = library.getRevision();
    }

    @Override
    public long getId()
    {
        return _id;
    }

    @Override
    public long getRunId()
    {
        return _runId;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public String getFileNameHint()
    {
        return _fileNameHint;
    }

    @Override
    public String getSkylineLibraryId()
    {
        return _skylineLibraryId;
    }

    @Override
    public String getRevision()
    {
        return _revision;
    }

    @Override
    public String getLibraryType()
    {
        return _libraryType;
    }

    public SpecLibKey getKey()
    {
        return new SpecLibKey(getName(), getFileNameHint(), getSkylineLibraryId(), getLibraryType(), getRevision());
    }

    public LibraryType getType()
    {
        return getLibraryType() != null ? LibraryType.getType(getLibraryType()) : LibraryType.unknown;
    }

    public boolean isSupported()
    {
        return getType().isSupported();
    }

    public ITargetedMSRun getRun(User user)
    {
        initRun(user);
        return _run;
    }

    private void initRun(User user)
    {
        if (_run == null && !_runInitialized)
        {
            _run = TargetedMSService.get().getRun(getRunId(), user);
            _runInitialized = true;
        }
    }

    public Path getLibFilePath(User user)
    {
        initLibPath(user);
        return _libFilePath;
    }

    private void initLibPath(User user)
    {
        if (_libFilePath == null && !_pathInitialized)
        {
            initRun(user);
            if (_run != null)
            {
                Path path = TargetedMSService.get().getLibraryFilePath(_run, this);
                _pathInitialized = true;
                if (path != null && FileUtil.isFileAndExists(path))
                {
                    _libFilePath = path;
                    try { _fileSize = Files.size(_libFilePath); } catch (IOException ignored) {}
                }
            }
        }
    }

    public @NotNull DOM.Renderable getRunLibraryLink(@NotNull User user, @NotNull Map<String, String> viewSpecLibParams)
    {
        return SPAN(getRunLink(user),
                HtmlString.NBSP,
                SPAN(at(style, "margin-left:5px;"), getViewLibInfoLink(viewSpecLibParams)));
    }

    public @NotNull DOM.Renderable getRunLink(@NotNull User user)
    {
        initRun(user);
        if (_run == null)
        {
            return SPAN(at(style, "color:red;"), "Run not found");
        }
        ActionURL runUrl = PageFlowUtil.urlProvider(TargetedMSUrls.class).getShowRunUrl(_run.getContainer(), _run.getId());

        return new Link.LinkBuilder(_run.getFileName()).href(runUrl).clearClasses().build();
    }

    @NotNull
    public DOM.Renderable getViewLibInfoAndDownloadLink(@NotNull User user, @NotNull Map<String, String> viewSpecLibParams)
    {
        return SPAN(getViewLibInfoLink(viewSpecLibParams), getDownloadLink(user));
    }

    @NotNull
    private DOM.Renderable getViewLibInfoLink(@NotNull Map<String, String> viewSpecLibParams)
    {
        ActionURL viewSpecLibAction = new ActionURL(PanoramaPublicController.ViewSpecLibAction.class, _run.getContainer());
        viewSpecLibAction.addParameter("specLibId", getId());
        for (Map.Entry<String, String> param : viewSpecLibParams.entrySet())
        {
            viewSpecLibAction.replaceParameter(param.getKey(), param.getValue());
        }
        return new Link.LinkBuilder("Library").href(viewSpecLibAction).tooltip("View library details").build();
    }

    @NotNull
    public DOM.Renderable getDownloadLink(@NotNull User user)
    {
        initRun(user);
        if (_run != null)
        {
            initLibPath(user);
            if (_libFilePath != null)
            {
                var webdavUrl = getWebdavUrl(_run.getContainer(), _libFilePath);
                if (webdavUrl != null)
                {
                    var displaySize = FileUtils.byteCountToDisplaySize(_fileSize);
                    return SPAN(
                            new Link.LinkBuilder().href(webdavUrl).iconCls("fa fa-download").build(),
                            HtmlString.NBSP,
                            new Link.LinkBuilder(displaySize).href(webdavUrl).tooltip("Download library file included in the Skyline document").clearClasses().build()
                    );
                }
                else
                {
                    return missingLibrary("Cannot build WebDAV URL");
                }
            }
            return missingLibrary("Library file not included in the Skyline document zip file");
        }
        return SPAN(at(style, "color:red;"), "Run not found");
    }

    @NotNull
    private DOM.Renderable missingLibrary(String popupText)
    {
        return EM(at(style, "color:red;",
                DOM.Attribute.title, popupText),
                "(Missing Library)");
    }

    private @Nullable String getWebdavUrl(@NotNull Container container, @NotNull Path file)
    {
        FileContentService fcs = FileContentService.get();
        if (fcs != null)
        {
            var fileRootPath = fcs.getFileRootPath(container, FileContentService.ContentType.files);
            if (fileRootPath != null)
            {
                var relPath = fileRootPath.relativize(file);
                var path = WebdavService.getPath()
                        .append(container.getParsedPath())
                        .append(FileContentService.FILES_LINK)
                        .append(new org.labkey.api.util.Path(relPath));
                return AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath() + path.encode();
            }
        }
        return null;
    }
}

