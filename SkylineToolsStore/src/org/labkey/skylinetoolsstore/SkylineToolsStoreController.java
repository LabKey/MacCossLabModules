/*
 * Copyright (c) 2017-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.skylinetoolsstore;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.NavTrailAction;
import org.labkey.api.action.PermissionCheckable;
import org.labkey.api.action.PermissionCheckableAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.NormalContainerType;
import org.labkey.api.files.FileContentService;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.RoleAssignment;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.JavaScriptFragment;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.SafeToRender;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.skylinetoolsstore.model.SkylineTool;
import org.labkey.skylinetoolsstore.view.SkylineToolDetails;
import org.labkey.skylinetoolsstore.view.SkylineToolStoreUrls;
import org.labkey.skylinetoolsstore.view.SkylineToolsStoreWebPart;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class SkylineToolsStoreController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SkylineToolsStoreController.class);
    private static final String[] VALID_ICON_EXTENSIONS = new String[] { "png", "jpg", "jpeg", "gif" };
    private static final String STORE_NOT_AVAILABLE = "The Skyline Tool Store is not available in this folder.";

    public SkylineToolsStoreController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ReadPermission.class)
    public static class BeginAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            ModuleLoader moduleLoader = ModuleLoader.getInstance();
            if(!getContainer().getActiveModules().contains(moduleLoader.getModule(SkylineToolsStoreModule.class)))
            {
                // If the toolstore module is not enabled in the container look for a container
                // that has tools.
                SkylineTool[] tools = SkylineToolsStoreManager.get().getToolsLatest();
                if (tools != null && tools.length > 0)
                {
                    Container toolsHomeContainer = tools[0].getContainerParent();
                    // NOTE: This returns the first container that contains tools. We have only one such container
                    // on the skyline website.
                    // TODO: Need to look into why the tool store module is enabled in the individual tool sub-folders.
                    if (!getContainer().equals(toolsHomeContainer))
                    {
                        ActionURL redirectUrl = getViewContext().getActionURL();
                        redirectUrl.setContainer(toolsHomeContainer);
                        throw new RedirectException(redirectUrl);
                    }
                }
            }
            return new SkylineToolsStoreWebPart();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild(getToolStoreNav(getContainer()));
        }
    }

    public static NavTree getToolStoreNav(Container container)
    {
        return new NavTree("Skyline Tool Store", new ActionURL(BeginAction.class, container));
    }

    /**
     * Nav trail link for an action that runs in a tool's own folder. We need to link back to The main
     * tool store folder that is the tool folder's parent.
     */
    private static NavTree getToolStoreNavFromToolFolder(Container toolContainer)
    {
        return getToolStoreNav(toolContainer.getParent());
    }

    protected SkylineTool getToolFromZip(MultipartFile zip) throws IOException
    {
        SkylineTool tool = null;
        byte[] toolIcon = null;
        try (ZipInputStream zipStream = new ZipInputStream(zip.getInputStream()))
        {
            ZipEntry zipEntry;
            while ((zipEntry = zipStream.getNextEntry()) != null &&
                    (tool == null || toolIcon == null))
            {
                String entryLower = zipEntry.getName().toLowerCase();
                if (entryLower.startsWith("tool-inf/") && !entryLower.startsWith("tool-inf/docs/"))
                {
                    String lowerBaseName = new File(zipEntry.getName()).getName().toLowerCase();

                    if (lowerBaseName.equals("info.properties"))
                    {
                        byte[] bytes = unzip(zipStream);
                        // zipEntry.closeEntry() not necessary, getNextEntry() does it automatically

                        tool = new SkylineTool(new BufferedReader(new StringReader(new String(bytes, "UTF-8"))));
                    }
                    else if (Arrays.asList(VALID_ICON_EXTENSIONS).contains(FileUtil.getExtension(lowerBaseName)))
                    {
                        toolIcon = unzip(zipStream);
                    }
                }
            }
        }
        catch (Exception e)
        {
            throw e;
        }

        if (tool != null)
        {
            tool.setZipName(FileUtil.makeLegalName(zip.getOriginalFilename()));
            if (toolIcon != null)
                tool.setIcon(toolIcon);
        }

        return tool;
    }

    protected byte[] unzip(ZipInputStream stream)
    {
        final int BUFFER_SIZE = 2048;

        byte[] bytes = new byte[BUFFER_SIZE];
        int bytesRead;
        try (ByteArrayOutputStream unzipBytes = new ByteArrayOutputStream())
        {
            while ((bytesRead = stream.read(bytes, 0, BUFFER_SIZE)) != -1)
                unzipBytes.write(bytes, 0, bytesRead);
            return unzipBytes.toByteArray();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    protected static boolean extractDocsFromZip(Path zipFile, Path containerDir) throws IOException
    {
        Path docsDir = containerDir.resolve("docs");
        boolean extracted = false;
        try (ZipFile zf = new ZipFile(zipFile.toFile()))
        {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements())
            {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.toLowerCase().startsWith("tool-inf/docs/") || entry.isDirectory())
                    continue;
                // Strip "tool-inf/docs/" prefix to get relative path within docs dir
                String relativePath = name.substring("tool-inf/docs/".length());
                if (relativePath.isEmpty())
                    continue;
                Path destPath = docsDir.resolve(relativePath).normalize();
                // Zip-slip protection
                if (!destPath.startsWith(docsDir.normalize()))
                    throw new IOException("Zip entry outside target directory: " + name);
                Files.createDirectories(destPath.getParent());
                try (InputStream in = zf.getInputStream(entry))
                {
                    Files.copy(in, destPath, StandardCopyOption.REPLACE_EXISTING);
                }
                extracted = true;
            }
        }
        return extracted;
    }

    public static File makeFile(Container c, String filename)
    {
        return getLocalPath(c).resolve(FileUtil.makeLegalName(filename)).toFile();
    }

    /**
     * Resolves a tool row id and confirms the tool lives in the given container.
     */
    private static SkylineTool requireToolInContainer(int toolId, Container c)
    {
        SkylineTool tool = SkylineToolsStoreManager.get().getTool(toolId);
        if (tool == null || !c.equals(tool.lookupContainer()))
            throw new NotFoundException("Could not find tool with Id " + toolId + " in this folder.");
        return tool;
    }



    public static Path getLocalPath(Container c)
    {
        return FileContentService.get().getFileRootPath(c, FileContentService.ContentType.files);
    }

    protected Container makeContainer(Container parent, String folderName, List<User> users, Role role) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        if (!Container.isLegalName(folderName, false, sb))
            return null;

        if (parent.hasChild(folderName))
            return null;

        Container c = ContainerManager.createContainer(parent, folderName, null, null, NormalContainerType.NAME, getUser());
        c.setFolderType(FolderTypeManager.get().getFolderType("Collaboration"), getUser());

        Path fileRoot = FileContentService.get().getFileRootPath(c, FileContentService.ContentType.files);
        if(!Files.exists(fileRoot))
        {
            Files.createDirectories(fileRoot);
        }

        // Make folder readable by all site users and guests, so that they can access the zip file/icon
        MutableSecurityPolicy policy = new MutableSecurityPolicy(c);
        User guest = new User();
        guest.setUserId(Group.groupGuests);
        User user = new User();
        user.setUserId(Group.groupUsers);
        policy.addRoleAssignment(guest, RoleManager.getRole(ReaderRole.class));
        policy.addRoleAssignment(user, RoleManager.getRole(ReaderRole.class));

        if (users != null && !users.isEmpty() && role != null)
            for (User u : users)
                policy.addRoleAssignment(u, role);

        SecurityPolicyManager.savePolicy(policy, User.getAdminServiceUser());

        return c;
    }

    protected MutableSecurityPolicy copyPolicy(Container c, SecurityPolicy from)
    {
        MutableSecurityPolicy policy = new MutableSecurityPolicy(c);
        for (RoleAssignment assignment : from.getAssignments())
        {
            User u = new User();
            u.setUserId(assignment.getUserId());
            policy.addRoleAssignment(u, assignment.getRole());
        }
        return policy;
    }

    protected MutableSecurityPolicy filterPolicy(SecurityPolicy original, List<User> users, Role[] roles)
    {
        // For each role assignment where the role is in roles, only keep if the user is in users
        MutableSecurityPolicy policy = new MutableSecurityPolicy(ContainerManager.getForId(original.getContainerId()));
        for (RoleAssignment assignment : original.getAssignments())
        {
            if (Arrays.asList(roles).contains(assignment.getRole()))
            {
                boolean skip = true;
                for (User u : users)
                {
                    if (u.getUserId() == assignment.getUserId())
                    {
                        skip = false;
                        break;
                    }
                }
                if (skip)
                    continue;
            }

            User u = new User();
            u.setUserId(assignment.getUserId());
            policy.addRoleAssignment(u, assignment.getRole());
        }
        return policy;
    }

    protected void copyContainerPermissions(Container from, Container to)
    {
        if (from == null || to == null)
            return;

        SecurityPolicyManager.savePolicy(copyPolicy(to, from.getPolicy()), User.getAdminServiceUser());
    }

    public static SkylineTool[] sortToolsByCreateDate(SkylineTool[] tools)
    {
        List<SkylineTool> toolList = Arrays.asList(tools);

        toolList.sort((lhs, rhs) -> rhs.getCreated().compareTo(lhs.getCreated()));

        return toolList.toArray(new SkylineTool[0]);
    }

    public static SafeToRender getUsersForAutocomplete()
    {
        JSONArray jsonArray = UserManager.getActiveUsers().stream()
            .map(User::getEmail)
            .collect(LabKeyCollectors.toJSONArray());

        return JavaScriptFragment.unsafe(jsonArray.toString());
    }

    protected static Pair<ArrayList<User>, ArrayList<String>> parseToolOwnerString(String toolOwners) throws ValidEmail.InvalidEmailException
    {
        ArrayList<User> toolOwnersUsers = new ArrayList<>();
        ArrayList<String> toolOwnersInvalid = new ArrayList<>();
        if (toolOwners != null)
        {
            for (String toolOwner : toolOwners.split(","))
            {
                toolOwner = toolOwner.trim();
                if (!toolOwner.isEmpty())
                {
                    User u = UserManager.getUser(new ValidEmail(toolOwner));
                    if (u == null)
                        toolOwnersInvalid.add(toolOwner);
                    else
                        toolOwnersUsers.add(u);
                }
            }
        }
        return new Pair<>(toolOwnersUsers,  toolOwnersInvalid);
    }

    public static ArrayList<String> getToolOwners(SkylineTool tool)
    {
        return getToolRelevantUsers(tool, new Role[]{RoleManager.getRole(EditorRole.class), RoleManager.getRole(FolderAdminRole.class)});
    }

    public static ArrayList<String> getToolRelevantUsers(SkylineTool tool, Role[] roles)
    {
        HashSet<String> users = new HashSet<>();
        for (RoleAssignment assignment : tool.lookupContainer().getPolicy().getAssignments())
            if (Arrays.asList(roles).contains(assignment.getRole()))
            {
                User user = UserManager.getUser(assignment.getUserId());
                if(user != null && user.getEmail() != null)
                {
                    users.add(user.getEmail());
                }
            }
        return new ArrayList<>(users);
    }

    public static HashMap<String, String> getSupplementaryFiles(SkylineTool tool) throws IOException
    {
        // Store supporting files in map <url, icon url>
        final String[] knownExtensions = {"pdf", "zip"};
        final String imgDir = AppProps.getInstance().getContextPath() + "/skylinetoolsstore/img/";

        HashMap<String, String> suppFiles = new HashMap<>();
        for (String suppFile : getSupplementaryFileBasenames(tool))
        {
            final String suppFileExtension = FileUtil.getExtension(suppFile).toLowerCase();
            final String suppFileIcon = (Arrays.asList(knownExtensions).contains(suppFileExtension)) ?
                imgDir + suppFileExtension + "-icon.png" : imgDir + "unknown-icon.jpg";
            suppFiles.put(tool.getFolderUrl() + suppFile, suppFileIcon);
        }

        return suppFiles;
    }

    public static HashSet<String> getSupplementaryFileBasenames(SkylineTool tool) throws IOException
    {
        HashSet<String> suppFiles = new HashSet<>();
        Path localToolDir = getLocalPath(tool.lookupContainer());
        try (var stream = Files.list(localToolDir))
        {
            stream.map(p -> p.getFileName().toString())
                  .filter(name -> !name.startsWith(".") && !name.equals(tool.getZipName()) && !name.equals("icon.png") && !name.equals("docs"))
                  .forEach(suppFiles::add);
        }
        return suppFiles;
    }

    /**
     * Adds a brand-new tool to this store folder.
     *
     * Site admin only. Tool authors do not upload here - they attach a zip to a message board post and
     * an admin adds it.
     *
     * Split from the old combined InsertAction because adding a tool and publishing a new tool version
     * need different permissions on different containers. See UpdateToolAction.
     */
    @RequiresSiteAdmin
    @ActionNames("insertTool, insert")
    public class InsertToolAction extends FormViewAction<ToolUploadForm>
    {
        private SkylineTool _tool;

        @Override
        public void validateCommand(ToolUploadForm form, Errors errors)
        {
            // validateCommand runs on POST only, so this is what stops a hand-posted upload. The
            // matching check in getView is what stops the form being drawn in the first place.
            if (!getContainer().hasActiveModuleByName(SkylineToolsStoreModule.NAME))
                errors.reject(ERROR_MSG, STORE_NOT_AVAILABLE);
        }

        @Override
        public ModelAndView getView(ToolUploadForm form, boolean reshow, BindException errors)
        {
            // On a reshow the message is already in errors, so let the JSP render it there.
            if (!reshow && !getContainer().hasActiveModuleByName(SkylineToolsStoreModule.NAME))
                return HtmlView.of(STORE_NOT_AVAILABLE);

            return new JspView<>("/org/labkey/skylinetoolsstore/view/SkylineToolsStoreUpload.jsp", form, errors);
        }

        @Override
        public boolean handlePost(ToolUploadForm form, BindException errors) throws Exception
        {
            Pair<ArrayList<User>, ArrayList<String>> parsedOwners = parseToolOwnerString(form.getToolOwners());
            if (!parsedOwners.second.isEmpty())
            {
                errors.reject(ERROR_MSG, "The following users are unknown: " +
                        StringUtils.join(parsedOwners.second, ", "));
                return false;
            }

            SkylineTool tool = readToolFromUpload(getFileMap().get("toolZip"), errors);
            if (tool == null)
                return false;

            // Identifiers are unique across the whole server, since Skyline keys on them.
            for (SkylineTool existing : SkylineToolsStoreManager.get().getToolsLatest())
            {
                if (tool.getIdentifier().equalsIgnoreCase(existing.getIdentifier()))
                {
                    errors.reject(ERROR_MSG, "The Skyline Tool you are trying to add already exists.");
                    return false;
                }
            }
            for (Container child : getContainer().getChildren())
            {
                if (child.getName().equalsIgnoreCase(toolFolderName(tool)))
                {
                    errors.reject(ERROR_MSG, "The Skyline Tool you are trying to add already exists.");
                    return false;
                }
            }

            _tool = storeToolVersion(getContainer(), tool, getFileMap().get("toolZip"),
                    parsedOwners.first, null);
            return true;
        }

        @Override
        public URLHelper getSuccessURL(ToolUploadForm form)
        {
            return SkylineToolStoreUrls.getToolDetailsUrl(_tool);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild(getToolStoreNav(getContainer()));
            root.addChild("Upload Tool");
        }
    }

    /**
     * Publishes a new version of an existing tool.
     *
     * Addressed to the TOOL's own container, so @RequiresPermission checks the folder where the
     * owner holds Editor. This is what lets a tool author maintain their tool without an admin.
     *
     * The old combined action redirected on a container mismatch, which silently lost the uploaded
     * zip because a browser follows a 302 after POST with a GET. Addressing the action to the tool's
     * container removes the mismatch case entirely.
     */
    @RequiresPermission(UpdatePermission.class)
    public class UpdateToolAction extends FormViewAction<ToolUploadForm>
    {
        private SkylineTool _tool;

        @Override
        public void validateCommand(ToolUploadForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(ToolUploadForm form, boolean reshow, BindException errors)
        {
            // The shared JSP decides which form to draw from toolId, so a request without one would
            // render the add-a-new-tool form pointed at this folder. There is no such thing as
            // updating an unnamed tool, so fail instead.
            requireToolInContainer(form.getToolId(), getContainer());

            return new JspView<>("/org/labkey/skylinetoolsstore/view/SkylineToolsStoreUpload.jsp", form, errors);
        }

        @Override
        public boolean handlePost(ToolUploadForm form, BindException errors) throws Exception
        {
            SkylineTool previousVersion = requireToolInContainer(form.getToolId(), getContainer());

            SkylineTool tool = readToolFromUpload(getFileMap().get("toolZip"), errors);
            if (tool == null)
                return false;

            if (!tool.getIdentifier().equalsIgnoreCase(previousVersion.getIdentifier()))
            {
                errors.reject(ERROR_MSG, "The Skyline Tool zip file did not contain the Skyline Tool being updated.");
                return false;
            }
            if (tool.getVersion().equalsIgnoreCase(previousVersion.getVersion()))
            {
                errors.reject(ERROR_MSG, "The Skyline Tool zip file contained the same version of the tool being updated.");
                return false;
            }
            for (SkylineTool existing : SkylineToolsStoreManager.get().getToolsByIdentifier(tool.getIdentifier()))
            {
                if (existing.getVersion().equalsIgnoreCase(tool.getVersion()))
                {
                    errors.reject(ERROR_MSG, "The Skyline Tool zip file contained an older version of the tool.");
                    return false;
                }
            }

            previousVersion.setLatest(false);
            SkylineToolsStoreManager.get().updateTool(getContainer(), getUser(), previousVersion);

            // We are in the tool's own folder, so the store folder that holds every version is its parent.
            // Owners are not passed - copyContainerPermissions carries the previous version's policy over.
            _tool = storeToolVersion(getContainer().getParent(), tool, getFileMap().get("toolZip"),
                    Collections.emptyList(), previousVersion);
            return true;
        }

        @Override
        public URLHelper getSuccessURL(ToolUploadForm form)
        {
            return SkylineToolStoreUrls.getToolDetailsUrl(_tool);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild(getToolStoreNavFromToolFolder(getContainer()));
            root.addChild("Upload New Version");
        }
    }

    /**
     * Shared by both upload actions. InsertToolAction ignores toolId, UpdateToolAction ignores
     * toolOwners - a new version inherits its owners from the version it supersedes.
     */
    public static class ToolUploadForm
    {
        private int _toolId;
        private String _toolOwners;
        private String _sender;

        public int getToolId()
        {
            return _toolId;
        }

        public void setToolId(int toolId)
        {
            _toolId = toolId;
        }

        public String getToolOwners()
        {
            return _toolOwners;
        }

        public void setToolOwners(String toolOwners)
        {
            _toolOwners = toolOwners;
        }

        public String getSender()
        {
            return _sender;
        }

        public void setSender(String sender)
        {
            _sender = sender;
        }
    }


    /**
     * Creates the child folder for a tool version and stores its zip, icon and docs, then inserts the
     * row. Shared by InsertToolAction and UpdateToolAction, which differ only in what they check first.
     *
     * @param storeContainer  the tool store folder to create the version's folder under
     * @param previousVersion the version being superseded, or null for a brand-new tool. Supplies the
     *                        permissions, supplementary files and docs that carry forward, which is
     *                        how a tool's owners keep their access across versions.
     */
    private SkylineTool storeToolVersion(Container storeContainer, SkylineTool tool, MultipartFile zip,
                                         List<User> owners, @Nullable SkylineTool previousVersion)
            throws IOException
    {
        Container previousContainer = previousVersion != null ? previousVersion.lookupContainer() : null;
        Set<String> carryForward = previousVersion != null
                ? getSupplementaryFileBasenames(previousVersion) : Collections.emptySet();

        Container c = makeContainer(storeContainer, toolFolderName(tool), owners,
                RoleManager.getRole(EditorRole.class));
        copyContainerPermissions(previousContainer, c);

        File storedZip = makeFile(c, zip.getOriginalFilename());
        zip.transferTo(storedZip);
        tool.writeIconToFile(makeFile(c, "icon.png"), "png");

        // Docs come from tool-inf/docs/ in the zip, or carry forward from the previous version.
        boolean hasDocs = extractDocsFromZip(storedZip.toPath(), getLocalPath(c));
        if (!hasDocs && previousContainer != null)
        {
            Path oldDocs = getLocalPath(previousContainer).resolve("docs");
            if (Files.isDirectory(oldDocs))
                FileUtil.copyDirectory(oldDocs, getLocalPath(c).resolve("docs"));
        }

        if (previousContainer != null)
            for (String copyFile : carryForward)
                FileUtils.copyFile(makeFile(previousContainer, copyFile), makeFile(c, copyFile), true);

        tool.setLatest(true);
        SkylineToolsStoreManager.get().insertTool(c, getUser(), tool);
        return tool;
    }

    /** The child folder a tool version lives in, for example _tool_MSstats_4.0 */
    private static String toolFolderName(SkylineTool tool)
    {
        return "_tool_" + FileUtil.getBaseName(FileUtil.makeLegalName(tool.getName())) + "_" +
                FileUtil.makeLegalName(tool.getVersion());
    }

    /** Reads the uploaded zip, rejecting anything that is not a usable tool. Null if rejected. */
    private SkylineTool readToolFromUpload(MultipartFile zip, BindException errors) throws IOException
    {
        if (zip == null || StringUtils.isEmpty(zip.getOriginalFilename()))
        {
            errors.reject(ERROR_MSG, "You did not submit a file.");
            return null;
        }

        SkylineTool tool = getToolFromZip(zip);
        if (tool == null)
        {
            errors.reject(ERROR_MSG, "The file was not a valid Skyline Tool zip file.");
            return null;
        }
        if (!tool.getMissingValues().isEmpty())
        {
            errors.reject(ERROR_MSG, "The tool was missing the following properties: " +
                    StringUtils.join(tool.getMissingValues(), ", "));
            return null;
        }
        return tool;
    }

    private void redirectToToolStoreContainer(SkylineTool tool, ActionURL originalUrl)
    {
        // If the container in the request URL does not match the parent of the container associated
        // with the tool, redirect to the correct URL
        Container toolContainerParent = tool.getContainerParent();
        if(toolContainerParent != null)
        {
            if(!toolContainerParent.equals(getContainer()))
            {
                ActionURL url = originalUrl.clone();
                url.setContainer(toolContainerParent);
                throw new RedirectException(url);
            }
        }
    }

    /**
     * Uploads one supplementary file for a tool. Addressed to the tool's own container.
     */
    @RequiresPermission(InsertPermission.class)
    public static class InsertSupplementAction extends FormViewAction<SupplementUploadForm>
    {
        private SkylineTool _tool;

        @Override
        public void validateCommand(SupplementUploadForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(SupplementUploadForm form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/skylinetoolsstore/view/SkylineToolSupplementUpload.jsp", form, errors);
        }

        @Override
        public boolean handlePost(SupplementUploadForm form, BindException errors) throws Exception
        {
            _tool = requireToolInContainer(form.getToolId(), getContainer());

            // Uploaded files are multipart parts, not request parameters, so they do not bind to the form.
            MultipartFile suppFile = getFileMap().get("suppFile");
            if (suppFile == null || StringUtils.isEmpty(suppFile.getOriginalFilename()))
            {
                errors.reject(ERROR_MSG, "Please submit a file.");
                return false;
            }

            File targetFile = makeFile(getContainer(), FileUtil.makeLegalName(suppFile.getOriginalFilename()));
            if (targetFile.exists())
            {
                errors.reject(ERROR_MSG, "A supplementary file with that name already exists.");
                return false;
            }

            suppFile.transferTo(targetFile);
            return true;
        }

        @Override
        public URLHelper getSuccessURL(SupplementUploadForm form)
        {
            return SkylineToolStoreUrls.getToolDetailsUrl(_tool);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild(getToolStoreNavFromToolFolder(getContainer()));
            root.addChild("Upload Supplementary File");
        }
    }

    public static class SupplementUploadForm
    {
        private int _toolId;

        public int getToolId()
        {
            return _toolId;
        }

        public void setToolId(int toolId)
        {
            _toolId = toolId;
        }
    }

    /**
     * Deletes one supplementary file from a tool.
     *
     * Addressed to the TOOL's own container, not the store folder, so @RequiresPermission checks the
     * folder that actually holds the file. Callers must build the URL with
     * SkylineToolStoreUrls.getToolActionUrl.
     */
    @RequiresPermission(DeletePermission.class)
    public static class DeleteSupplementAction extends FormHandlerAction<SupplementForm>
    {
        private SkylineTool _tool;

        @Override
        public void validateCommand(SupplementForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(SupplementForm form, BindException errors) throws Exception
        {
            _tool = requireToolInContainer(form.getToolId(), getContainer());

            File targetDel = makeFile(getContainer(), form.getSuppFile());

            // The tool's own zip and icon are not supplementary files, so they are not deletable here.
            if (!targetDel.isFile() ||
                targetDel.getName().equalsIgnoreCase("icon.png") ||
                targetDel.getName().equalsIgnoreCase(_tool.getZipName()))
            {
                throw new NotFoundException("No supplementary file named " + form.getSuppFile() +
                        " for tool " + _tool.getName());
            }
            targetDel.delete();
            return true;
        }

        @Override
        public URLHelper getSuccessURL(SupplementForm form)
        {
            return SkylineToolStoreUrls.getToolDetailsUrl(_tool);
        }
    }

    public static class SupplementForm
    {
        private int _toolId;
        private String _suppFile;

        public int getToolId()
        {
            return _toolId;
        }

        public void setToolId(int toolId)
        {
            _toolId = toolId;
        }

        public String getSuppFile()
        {
            return _suppFile;
        }

        public void setSuppFile(String suppFile)
        {
            _suppFile = suppFile;
        }
    }

    @RequiresLogin
    public static class DeleteAction extends FormHandlerAction<IdForm>
    {
        @Override
        public URLHelper getSuccessURL(IdForm idForm)
        {
            return SkylineToolStoreUrls.getToolStoreHomeUrl(getContainer(), getUser());
        }

        @Override
        public boolean handlePost(IdForm idForm, BindException errors) throws Exception
        {
            final SkylineTool tool = SkylineToolsStoreManager.get().getTool(idForm.getToolId());

            if(tool == null)
            {
                errors.reject(ERROR_MSG, "Tool with id " + idForm.getToolId() + " does not exist.");
                return false;
            }

            // Get the tool store container, in case we need it, before the tool and its container is deleted.
            Container toolStoreContainer = tool.getContainerParent();
            if(toolStoreContainer == null)
            {
                errors.reject(ERROR_MSG, "Failed to look up tool's parent container: " + tool.getName());
                return false;
            }
            if(!getContainer().equals(toolStoreContainer))
            {
                ActionURL url = getViewContext().getActionURL().clone();
                url.setContainer(toolStoreContainer);
                throw new RedirectException(url);
            }

            Container toolContainer = tool.lookupContainer();
            if(toolContainer == null)
            {
                errors.reject(ERROR_MSG, "Failed to look up tool's container: " + tool.getName());
                return false;
            }
            if(!toolContainer.hasPermission(getUser(), DeletePermission.class))
            {
                errors.reject(ERROR_MSG, "User does not have permission to delete the tool." + tool.getName());
                return false;
            }

            // TODO: Should be in a transaction
            for (SkylineTool toDelete : SkylineToolsStoreManager.get().getToolsByIdentifier(tool.getIdentifier()))
            {
                ContainerManager.delete(toDelete.lookupContainer(), getUser());
            }

            return true;
        }

        @Override
        public void validateCommand(IdForm idForm, Errors errors)
        {

        }
    }

    public static class IdForm extends ReturnUrlForm
    {
        private String _name;
        private int _toolId;

        public IdForm()
        {
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public int getToolId()
        {
            return _toolId;
        }

        public void setToolId(int toolId)
        {
            _toolId = toolId;
        }
    }

    /**
     * Deletes only the newest version of a tool and promotes the previous one.
     *
     * This is a FormHandlerAction, so it accepts POST only. It used to be reachable by GET, which
     * meant a container delete could be triggered by an img tag on any page, and no CSRF token can
     * protect a GET. LabKey's own dev-mode guardrail flagged it too, as
     * "MUTATING SQL executed as part of handling action: GET ...DeleteLatestAction".
     *
     * That guardrail is not the thing to silence here. Wrapping the delete in ignoreSqlUpdates(),
     * as PR #608 correctly did for DownloadToolAction's download counter, would hide the warning and
     * leave the delete reachable by GET.
     */
    @RequiresLogin
    public static class DeleteLatestAction extends FormHandlerAction<DeleteLatestForm>
    {
        private URLHelper _successURL;

        @Override
        public void validateCommand(DeleteLatestForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(DeleteLatestForm form, BindException errors) throws Exception
        {
            final SkylineTool tool = SkylineToolsStoreManager.get().getTool(form.getToolId());
            if (tool == null)
                throw new NotFoundException("Could not find tool with Id " + form.getToolId());

            Container toolContainer = tool.lookupContainer();
            if (toolContainer == null)
                throw new NotFoundException("Failed to look up the tool's container: " + tool.getName());
            if (!toolContainer.hasPermission(getUser(), DeletePermission.class))
                throw new UnauthorizedException("User does not have permission to delete the tool.");

            ActionURL senderUrl = form.getSender() != null ? new ActionURL(form.getSender()) : null;

            // Resolve the tool store container before the tool's own container is deleted.
            Container toolStoreContainer = tool.getContainerParent() != null ? tool.getContainerParent() : getContainer();

            SkylineTool[] tools = sortToolsByCreateDate(SkylineToolsStoreManager.get().getToolsByIdentifier(tool.getIdentifier()));

            // This action removes the newest version only, so it needs an older version to fall back to.
            if (tools.length == 1)
            {
                errors.reject(ERROR_MSG, "Cannot delete the only version of " + tool.getName() +
                        ". Use Delete to remove the tool entirely.");
                return false;
            }

            ContainerManager.delete(tools[0].lookupContainer(), getUser());

            if (senderUrl != null)
            {
                if (!tools[0].getName().equals(tools[1].getName()) && senderUrl.getParameter("name") != null)
                    senderUrl.replaceParameter("name", tools[1].getName());

                if (senderUrl.getParameter("version") != null && senderUrl.getParameter("version").equals(tools[0].getVersion()))
                    senderUrl.deleteParameter("version");
            }

            SkylineTool newLatest = tools[1];
            newLatest.setLatest(true);
            SkylineToolsStoreManager.get().updateTool(newLatest.lookupContainer(), getUser(), newLatest);

            _successURL = senderUrl != null ? senderUrl :
                    SkylineToolStoreUrls.getToolStoreHomeUrl(toolStoreContainer, getUser());
            return true;
        }

        @Override
        public URLHelper getSuccessURL(DeleteLatestForm form)
        {
            return _successURL;
        }
    }

    public static class DeleteLatestForm extends IdForm
    {
        private String _sender;

        public String getSender()
        {
            return _sender;
        }

        public void setSender(String sender)
        {
            _sender = sender;
        }
    }

    /**
     * Left as an AbstractController with an empty checkPermissions() on purpose, unlike the mutating
     * actions in this controller.
     *
     * This is an anonymous GET that shipped Skyline clients call directly, and CSRF validation only
     * applies to non-GET requests, so routing it through the framework would buy no security. It
     * would however start enforcing terms-of-use, which could break tool downloads on a site that has
     * one. Not worth the risk for no gain. Same reasoning for GetToolsApiAction.
     */
    @RequiresNoPermission
    public class DownloadToolAction extends AbstractController implements PermissionCheckable
    {
        public static final String DOWNLOADED_COOKIE_PREFIX = "downloadtool";

        @Override
        public ModelAndView handleRequestInternal(HttpServletRequest httpServletRequest, @NotNull HttpServletResponse httpServletResponse) throws Exception
        {
            final int id = NumberUtils.toInt(httpServletRequest.getParameter("id"), -1);
            final String toolName = httpServletRequest.getParameter("name");
            final String toolLsid = httpServletRequest.getParameter("lsid");
            SkylineTool tool = null;

            if (id > 0 && (tool = SkylineToolsStoreManager.get().getTool(id)) == null)
                throw new NotFoundException("Could not find tool with id " + id);
            else if (toolName != null &&
                     (tool = SkylineToolsStoreManager.get().getLatestTool(URLDecoder.decode(toolName.trim(), "UTF-8"))) == null)
                throw new NotFoundException("Could not find tool with name " + toolName);
            else if (toolLsid != null &&
                     (tool = SkylineToolsStoreManager.get().getToolLatestByIdentifier(toolLsid.trim())) == null)
                throw new NotFoundException("Could not find tool with LSID " + toolLsid);

            if (tool == null)
                throw new NotFoundException("Could not do tool lookup");

            if (recordDownload(httpServletRequest, tool.getRowId()))
            {
                // Cookie expires after 1 day
                final int expires = 24 * 60 * 60;

                // Download counter is an incidental write on a GET action — use ignoreSqlUpdates()
                // to avoid the dev-mode mutating SQL assertion (like auditing writes)
                try (var ignored = SpringActionController.ignoreSqlUpdates())
                {
                    SkylineToolsStoreManager.get().recordToolDownload(tool);
                }

                DateFormat df = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss 'GMT'", Locale.US);
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.SECOND, expires);

                httpServletResponse.setHeader("Set-Cookie",
                    DOWNLOADED_COOKIE_PREFIX + tool.getRowId() + "=1; " +
                    "Expires=" + df.format(calendar.getTime()) + "; " +
                    "Max-Age=" + expires + "; " +
                    "Path=/; Domain=;");
            }

            Container toolContainer = ContainerManager.getForId(tool.getContainerId());
            if (toolContainer == null)
            {
                throw new NotFoundException("Tool container not found");
            }

            org.labkey.api.util.Path path = WebdavService.getPath().append(toolContainer.getParsedPath()).append(FileContentService.FILES_LINK).append(tool.getZipName());
            WebdavResource resource = WebdavService.get().getResolver().lookup(path);
            if (resource == null || !resource.isFile())
                throw new NotFoundException("Resource could not be found: " + path.toString());

            // Issue 49580: https://www.labkey.org/MacCoss/Issue%20Tracker/issues-details.view?issueId=49580
            // Add our own 'Content-Disposition' header so that it overwrites the one set in
            // ResponseHelper.setContentDisposition(HttpServletResponse response, ContentDispositionType type, @NotNull String filename)
            // Skyline expects the 'Content-Disposition' header value to look like this: filename="MSstatsShiny.zip".
            Map<String, String> headers = Collections.singletonMap("Content-Disposition", "filename=\""+ tool.getZipName() + "\"");
            PageFlowUtil.streamFile(getViewContext().getResponse(),
                    headers,
                    resource.getName(),
                    resource.getInputStream(getUser()),
                    true);
            return null;
        }

        /**
         * Whether this request should count as a download.
         *
         * Best effort by design, and accepted as such. The 24-hour cookie only deduplicates a
         * cooperating browser, so the counter can be inflated with a private window or by clearing
         * cookies. It is a popularity signal, not a metric to rely on.
         */
        protected boolean recordDownload(HttpServletRequest httpServletRequest, int toolId)
        {
            if (httpServletRequest.getParameter("noSaveCookie") != null)
            {
                // To prevent incrementing download counter when the tool is downloaded
                // from Skyline for running tests.
                return false;
            }
            else if (httpServletRequest.getCookies() == null)
            {
                return true;
            }

            for (Cookie cookie : httpServletRequest.getCookies())
                if (cookie.getName().equalsIgnoreCase("downloadtool" + toolId) && cookie.getValue().equals("1"))
                    return false;

            return true;
        }

        @Override
        public void checkPermissions() throws UnauthorizedException
        {

        }
    }

    @RequiresNoPermission
    @ActionNames("downloadFile")
    public static class DownloadToolFileAction extends SimpleViewAction<DownloadFileForm> implements PermissionCheckable
    {
        @Override
        public ModelAndView getView(DownloadFileForm form, BindException errors) throws Exception
        {
            if (StringUtils.trimToNull(form.getTool()) == null)
                errors.reject(SpringActionController.ERROR_MSG, "Could not find tool name in request.");
            if (StringUtils.trimToNull(form.getFile()) == null)
                errors.reject(SpringActionController.ERROR_MSG, "Could not find filename in request");
            if (errors.hasErrors())
                return new SimpleErrorView(errors);

            SkylineTool tool = SkylineToolsStoreManager.get().getLatestTool(form.getTool().trim());
            if (tool != null)
            {
                String fileName = form.getFile().trim();

                Container toolContainer = tool.lookupContainer();
                File downloadFile = makeFile(toolContainer, fileName);
                if (!NetworkDrive.exists(downloadFile))
                {
                    errors.reject(SpringActionController.ERROR_MSG, "File " + fileName +
                            " does not exist in the " + tool.getName() + " " + tool.getVersion() + " directory.");
                }
                else
                {
                    PageFlowUtil.streamFile(getViewContext().getResponse(), downloadFile.toPath(), true);
                    return null;
                }
            }
            else
            {
                errors.reject(SpringActionController.ERROR_MSG, "Could not find tool with name " + form.getTool());
            }

            return new SimpleErrorView(errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }
    public static class DownloadFileForm
    {
        private String _tool;
        private String _file;

        public String getTool()
        {
            return _tool;
        }

        public void setTool(String tool)
        {
            _tool = tool;
        }

        public String getFile()
        {
            return _file;
        }

        public void setFile(String file)
        {
            _file = file;
        }
    }


    @RequiresNoPermission
    @ActionNames("details, toolDetails")
    public class DetailsAction extends SimpleViewAction<ViewToolDetailsForm> implements PermissionCheckable
    {
        private SkylineTool _tool = null;

        @Override
        public ModelAndView getView(ViewToolDetailsForm form, BindException errors) throws Exception
        {
            Integer toolId = form.getId();

            if (toolId != null)
            {
                // Do lookup by id if we are given an id.
                _tool = SkylineToolsStoreManager.get().getTool(toolId);
                if (_tool == null)
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Could not find tool " + " by Id " + toolId);
                    return new SimpleErrorView(errors);
                }
            }
            else
            {
                // Lookup tool by name and version (optional) if we were not given a toolId.
                String toolName = form.getName();
                if (StringUtils.trimToNull(toolName) == null)
                {
                    errors.reject(SpringActionController.ERROR_MSG, "No tool name found in request");
                    return new SimpleErrorView(errors);
                }

                String version = form.getVersion();
                if (StringUtils.trimToNull(version) != null)
                {
                    // Lookup by version if we were given one
                    _tool = SkylineToolsStoreManager.get().getToolByNameAndVersion(toolName, version);
                }
                else
                {
                    // Otherwise, return the latest version of this tool
                    _tool = SkylineToolsStoreManager.get().getLatestTool(toolName);
                }

                if (_tool == null)
                {
                    StringBuilder msg = new StringBuilder("Could not find tool ").append(" by name ").append(toolName);
                    if (version != null)
                        msg.append(" and version ").append(version);
                    errors.reject(SpringActionController.ERROR_MSG,  msg.toString());
                    return new SimpleErrorView(errors);
                }
            }

            // If the container in the request URL does not match the parent of the container associated
            // with the tool, redirect to the correct URL
            redirectToToolStoreContainer(_tool, getViewContext().getActionURL());

            return new SkylineToolDetails(_tool);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    public static class ViewToolDetailsForm
    {
        private Integer _id;
        private String _name;
        private String _version;

        public Integer getId()
        {
            return _id;
        }

        public void setId(Integer id)
        {
            _id = id;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String getVersion()
        {
            return _version;
        }

        public void setVersion(String version)
        {
            _version = version;
        }
    }

    @RequiresSiteAdmin
    /**
     * Replaces the set of users holding Editor on a tool's folder.
     *
     * Stays addressed to the store folder rather than the tool's, because @RequiresSiteAdmin is
     * checked against the whole site and not a container, so there is nothing to gain by moving it.
     */
    public class SetOwnersAction extends FormViewAction<SetOwnersForm>
    {
        private URLHelper _successURL;

        @Override
        public void validateCommand(SetOwnersForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(SetOwnersForm form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/skylinetoolsstore/view/SkylineToolManageOwners.jsp", form, errors);
        }

        @Override
        public boolean handlePost(SetOwnersForm form, BindException errors) throws Exception
        {
            Pair<ArrayList<User>, ArrayList<String>> parsedOwners = parseToolOwnerString(form.getToolOwners());
            ArrayList<User> toolOwnersUsers = parsedOwners.first;
            ArrayList<String> toolOwnersInvalid = parsedOwners.second;

            if (!toolOwnersInvalid.isEmpty())
            {
                errors.reject(ERROR_MSG, "The following users are unknown: " +
                        StringUtils.join(toolOwnersInvalid, ", "));
                return false;
            }

            final SkylineTool tool = SkylineToolsStoreManager.get().getTool(form.getToolId());
            if (tool == null)
                throw new NotFoundException("Could not find tool with Id " + form.getToolId());
            final Container c = tool.lookupContainer();
            if (c == null)
                throw new NotFoundException("Failed to look up the tool's container: " + tool.getName());

            ArrayList<User> newToolEditors = new ArrayList<>(toolOwnersUsers);

            for (RoleAssignment assignment : c.getPolicy().getAssignments())
            {
                if (assignment.getRole() != RoleManager.getRole(FolderAdminRole.class) &&
                    assignment.getRole() != RoleManager.getRole(EditorRole.class))
                    continue;
                for (int i = 0; i < newToolEditors.size(); ++i)
                {
                    if (newToolEditors.get(i).getUserId() == assignment.getUserId())
                    {
                        newToolEditors.remove(i);
                        break;
                    }
                }
            }

            MutableSecurityPolicy policy = copyPolicy(c, c.getPolicy());
            for (User u : newToolEditors)
                policy.addRoleAssignment(u, RoleManager.getRole(EditorRole.class));
            policy = filterPolicy(policy, toolOwnersUsers, new Role[]{RoleManager.getRole(EditorRole.class), RoleManager.getRole(FolderAdminRole.class)});
            SecurityPolicyManager.savePolicy(policy, User.getAdminServiceUser());

            Container toolStoreContainer = tool.getContainerParent() != null ? tool.getContainerParent() : getContainer();
            _successURL = form.getSender() != null ? new ActionURL(form.getSender())
                    : SkylineToolStoreUrls.getToolStoreHomeUrl(toolStoreContainer, getUser());
            return true;
        }

        @Override
        public URLHelper getSuccessURL(SetOwnersForm form)
        {
            return _successURL;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild(getToolStoreNavFromToolFolder(getContainer()));
            root.addChild("Manage Tool Owners");
        }
    }

    public static class SetOwnersForm
    {
        private int _toolId;
        private String _toolOwners;
        private String _sender;

        public int getToolId()
        {
            return _toolId;
        }

        public void setToolId(int toolId)
        {
            _toolId = toolId;
        }

        public String getToolOwners()
        {
            return _toolOwners;
        }

        public void setToolOwners(String toolOwners)
        {
            _toolOwners = toolOwners;
        }

        public String getSender()
        {
            return _sender;
        }

        public void setSender(String sender)
        {
            _sender = sender;
        }
    }

    /**
     * Edits one property of a tool, or replaces its icon, rewriting tool-inf/info.properties inside
     * the stored zip so the file and the database row stay in step.
     *
     * Addressed to the TOOL's own container - see DeleteSupplementAction.
     */
    @RequiresPermission(InsertPermission.class)
    public static class UpdatePropertyAction extends FormHandlerAction<UpdatePropertyForm>
    {
        private SkylineTool _tool;

        @Override
        public void validateCommand(UpdatePropertyForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(UpdatePropertyForm form, BindException errors) throws Exception
        {
            _tool = requireToolInContainer(form.getToolId(), getContainer());
            final SkylineTool tool = _tool;
            final Container container = getContainer();

            final String propName = form.getPropName();
            String propValue = "";

            // An icon is uploaded as a file part also named propValue. Files do not bind to the form.
            final MultipartFile icon = getFileMap().get("propValue");

            if (icon == null)
            {
                if (propName == null)
                    throw new ApiUsageException("propName is required.");
                // Form binding turns an empty value into null. Blanking a property is a normal edit,
                // so treat null as an empty value rather than a missing parameter.
                String submitted = form.getPropValue() == null ? "" : form.getPropValue();
                propValue = submitted.replace("\r", "").replace("\n", "\r\n");
                tool.setProperty(propName, propValue);
            }
            else
            {
                tool.setIcon(icon.getBytes());
            }

            File zipFile = makeFile(container, tool.getZipName());
            File tmpFile = makeFile(container, tool.getZipName() + "~");
            try (ZipFile zipIn = new ZipFile(zipFile);
                 ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(tmpFile)))
            {
                for (Enumeration e = zipIn.entries(); e.hasMoreElements();)
                {
                    ZipEntry zipEntry = (ZipEntry)e.nextElement();
                    final String lowerName = zipEntry.getName().toLowerCase();

                    if (icon == null)
                    {
                        try (InputStream in = lowerName.equals("tool-inf/info.properties")
                                ? tool.getInfoPropertiesStream(zipIn.getInputStream(zipEntry), propName, propValue)
                                : zipIn.getInputStream(zipEntry))
                        {
                            ZipEntry newEntry = new ZipEntry(zipEntry.getName());
                            zipOut.putNextEntry(newEntry);
                            byte[] buf = new byte[1024];
                            int len;
                            while ((len = in.read(buf)) > 0)
                                zipOut.write(buf, 0, len);
                            zipOut.closeEntry();
                        }
                    }
                    else if (!lowerName.startsWith("tool-inf/") ||
                             !Arrays.asList(VALID_ICON_EXTENSIONS).contains(FileUtil.getExtension(lowerName)))
                    {
                        try (InputStream in = zipIn.getInputStream(zipEntry))
                        {
                            ZipEntry newEntry = new ZipEntry(zipEntry.getName());
                            zipOut.putNextEntry(newEntry);
                            byte[] buf = new byte[1024];
                            int len;
                            while ((len = in.read(buf)) > 0)
                                zipOut.write(buf, 0, len);
                            zipOut.closeEntry();
                        }
                    }
                }
                if (icon != null)
                {
                    zipOut.putNextEntry(new ZipEntry("tool-inf/" + icon.getOriginalFilename()));
                    try (InputStream in = icon.getInputStream())
                    {
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = in.read(buf)) > 0)
                            zipOut.write(buf, 0, len);
                        zipOut.closeEntry();
                    }
                }
            }

            // Replace in one step. Deleting first and then renaming left no copy of the tool zip if
            // the rename failed, and the rename result was not checked.
            try
            {
                Files.move(tmpFile.toPath(), zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException e)
            {
                tmpFile.delete();
                throw new IOException("Could not replace " + zipFile + ". The tool zip is unchanged.", e);
            }

            if (icon == null)
                SkylineToolsStoreManager.get().updateTool(container, getUser(), tool);
            else
                tool.writeIconToFile(makeFile(container, "icon.png"), "png");

            return true;
        }

        @Override
        public URLHelper getSuccessURL(UpdatePropertyForm form)
        {
            return SkylineToolStoreUrls.getToolDetailsUrl(_tool);
        }
    }

    public static class UpdatePropertyForm
    {
        private int _toolId;
        private String _propName;
        private String _propValue;

        public int getToolId()
        {
            return _toolId;
        }

        public void setToolId(int toolId)
        {
            _toolId = toolId;
        }

        public String getPropName()
        {
            return _propName;
        }

        public void setPropName(String propName)
        {
            _propName = propName;
        }

        public String getPropValue()
        {
            return _propValue;
        }

        public void setPropValue(String propValue)
        {
            _propValue = propValue;
        }
    }

    @RequiresNoPermission
    public class GetToolsApiAction extends AbstractController implements PermissionCheckable
    {
        @Override
        public ModelAndView handleRequestInternal(@NotNull HttpServletRequest httpServletRequest, @NotNull HttpServletResponse httpServletResponse) throws IOException
        {
            StringBuilder sb = new StringBuilder();

            String toolName = getViewContext().getRequest().getParameter("toolName");
            SkylineTool tool;
            SkylineTool[] tools =
                (toolName == null || (tool = SkylineToolsStoreManager.get().getLatestTool(toolName)) == null) ?
                SkylineToolsStoreManager.get().getToolsLatest() : new SkylineTool[]{tool};

            if (tools.length > 1)
                sb.append("[");

            for (int i = 0; i < tools.length; ++i)
            {
                tool = tools[i];
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("Authors", tool.getAuthors());
                jsonObject.put("Description", tool.getDescription());
                jsonObject.put("Downloads", tool.getDownloads());
                // The container does not really matter for the download URL, but try to set the right container in the URL.
                Container toolStoreContainer = tool.getContainerParent();
                if(toolStoreContainer == null)
                {
                    toolStoreContainer = getContainer();
                }
                jsonObject.put("DownloadUrl", new ActionURL(DownloadToolAction.class, toolStoreContainer).addParameter("id", tool.getRowId()).toString());
                jsonObject.put("IconUrl", tool.getIconUrl());
                jsonObject.put("Identifier", tool.getIdentifier());
                jsonObject.put("Languages", tool.getLanguages());
                jsonObject.put("Name", tool.getName());
                jsonObject.put("Organization", tool.getOrganization());
                jsonObject.put("Provider", tool.getProvider());
                jsonObject.put("Version", tool.getVersion());
                sb.append(jsonObject);
                if (i < tools.length - 1)
                    sb.append(',');
            }

            if (tools.length > 1)
                sb.append(']');

            httpServletResponse.setContentType("application/json");
            httpServletResponse.getOutputStream().write(sb.toString().getBytes());
            return null;
        }

        @Override
        public void checkPermissions() throws UnauthorizedException
        {

        }
    }
}
