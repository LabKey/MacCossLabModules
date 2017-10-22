/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONObject;
import org.labkey.api.action.NavTrailAction;
import org.labkey.api.action.PermissionCheckable;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
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
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpRedirectView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.skylinetoolsstore.model.Rating;
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

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class SkylineToolsStoreController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SkylineToolsStoreController.class);
    private static final String[] VALID_ICON_EXTENSIONS = new String[] { "png", "jpg", "jpeg", "gif" };

    public SkylineToolsStoreController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
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

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(getToolStoreNav(getContainer()));
        }
    }

    public static NavTree getToolStoreNav(Container container)
    {
        return new NavTree("Skyline Tool Store", new ActionURL(BeginAction.class, container));
    }

    protected SkylineTool getToolFromZip(MultipartFile zip) throws IOException
    {
        SkylineTool tool = null;
        byte[] toolIcon = null;
        try (ZipInputStream zipStream = new ZipInputStream(zip.getInputStream()))
        {
            ZipEntry zipEntry;
            while ((zipEntry = zipStream.getNextEntry()) != null &&
                    (tool == null || tool.getIcon() == null))
            {
                if (zipEntry.getName().toLowerCase().startsWith("tool-inf/"))
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

    public static File makeFile(Container c, String filename)
    {
        return new File(getLocalPath(c) + FileUtil.makeLegalName(filename));
    }

    public static String getLocalPath(Container c)
    {
        String localPath = ServiceRegistry.get().getService(FileContentService.class).getFileRoot(c)
                + File.separator;
        if (new File(localPath + "@files").exists())
            localPath += "@files" + File.separator;
        return localPath;
    }

    protected Container makeContainer(Container parent, String folderName, List<User> users, Role role)
    {
        StringBuilder sb = new StringBuilder();
        if (!Container.isLegalName(folderName, false, sb))
            return null;

        if (parent.hasChild(folderName))
            return null;

        Container c = ContainerManager.createContainer(parent, folderName, null, null, Container.TYPE.normal, getUser());
        c.setFolderType(FolderTypeManager.get().getFolderType("Collaboration"), getUser());

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

        SecurityPolicyManager.savePolicy(policy);

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

        SecurityPolicyManager.savePolicy(copyPolicy(to, from.getPolicy()));
    }

    public static SkylineTool[] sortToolsByCreateDate(SkylineTool[] tools)
    {
        List<SkylineTool> toolList = Arrays.asList(tools);

        Collections.sort(toolList, new Comparator<SkylineTool>()
        {
            public int compare(SkylineTool lhs, SkylineTool rhs)
            {
                return rhs.getCreated().compareTo(lhs.getCreated());
            }
        });

        return (SkylineTool[])toolList.toArray();
    }

    public static String getUsersForAutocomplete()
    {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (User u : UserManager.getActiveUsers())
            sb.append(sb.length() == 1 ? "\"" + u.getEmail() + "\"" : ",\"" + u.getEmail() + "\"");
        sb.append(']');
        return sb.toString();
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
                users.add(UserManager.getUser(assignment.getUserId()).getEmail());
        return new ArrayList<>(users);
    }

    public static HashMap<String, String> getSupplementaryFiles(SkylineTool tool)
    {
        // Store supporting files in map <url, icon url>
        final String[] knownExtensions = {"pdf", "zip"};
        final String imgDir = AppProps.getInstance().getContextPath() + "/skylinetoolsstore/img/";

        HashMap<String, String> suppFiles = new HashMap();
        for (String suppFile : getSupplementaryFileBasenames(tool))
        {
            final String suppFileExtension = FileUtil.getExtension(suppFile).toLowerCase();
            final String suppFileIcon = (Arrays.asList(knownExtensions).contains(suppFileExtension)) ?
                imgDir + suppFileExtension + "-icon.png" : imgDir + "unknown-icon.jpg";
            suppFiles.put(tool.getFolderUrl() + suppFile, suppFileIcon);
        }

        return suppFiles;
    }

    public static HashSet<String> getSupplementaryFileBasenames(SkylineTool tool)
    {
        HashSet<String> suppFiles = new HashSet();
        File localToolDir = new File(getLocalPath(tool.lookupContainer()));
        for (String suppFile : localToolDir.list())
        {
            final String basename = new File(suppFile).getName();
            if (!basename.startsWith(".") && !basename.equals(tool.getZipName()) && !basename.equals("icon.png"))
                suppFiles.add(suppFile);
        }
        return suppFiles;
    }

    @RequiresNoPermission
    public class InsertAction extends AbstractController implements NavTrailAction, PermissionCheckable
    {
        private static final String NO_FILE = "You did not submit a file.";
        private static final String INVALID_TOOL_FILE = "The file was not a valid Skyline Tool zip file.";
        private static final String MISSING_REQUIRED_PROPERTIES = "The tool was missing the following properties: ";
        private static final String TOOL_DOES_NOT_EXIST = "The Skyline Tool being updated does not exist.";
        private static final String TOOL_ALREADY_EXISTS = "The Skyline Tool you are trying to add already exists.";
        private static final String WRONG_TOOL = "The Skyline Tool zip file did not contain the Skyline Tool being updated.";
        private static final String SAME_VERSION = "The Skyline Tool zip file contained the same version of the tool being updated.";
        private static final String OLD_VERSION = "The Skyline Tool zip file contained an older version of the tool.";
        private static final String NO_UPDATE_PERMISSIONS = "You do not have permission to update that Skyline Tool.";
        private static final String NO_INSERT_PERMISSIONS = "You do not have permission to add a new Skyline Tool.";
        private static final String UNKNOWN_USERS = "The following users are unknown: ";

        public InsertAction()
        {
        }

        public ModelAndView handleRequestInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Exception
        {
            final String sender = httpServletRequest.getParameter("sender");
            final String updateTargetString = StringUtils.trimToNull(httpServletRequest.getParameter("updatetarget"));
            final int updateTarget = (updateTargetString != null) ? Integer.parseInt(updateTargetString) : -1;
            final String toolOwners = httpServletRequest.getParameter("toolOwners");

            if(updateTarget == -1 &&
               !getContainer().getActiveModules().contains(ModuleLoader.getInstance().getModule(SkylineToolsStoreModule.class)))
            {
                // Make sure that the tool store module is enabled in the folder where the user is trying
                // to insert the new tool.
                return new HtmlView("The Skyline Tool Store is not available in this folder.");
            }

            if (httpServletRequest.getMethod().equalsIgnoreCase("post") &&
                httpServletRequest instanceof MultipartHttpServletRequest)
            {
                Map<?, ?> fileMap = ((MultipartHttpServletRequest)httpServletRequest).getFileMap();
                MultipartFile zip = (MultipartFile)(fileMap.get("toolZip"));

                Pair<ArrayList<User>, ArrayList<String>> parsedOwners = parseToolOwnerString(toolOwners);
                ArrayList<User> toolOwnersUsers = parsedOwners.first;
                ArrayList<String> toolOwnersInvalid = parsedOwners.second;

                if (toolOwnersInvalid.size() > 0)
                {
                    getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                        UNKNOWN_USERS + StringUtils.join(toolOwnersInvalid, ", "));
                }
                else if (!zip.getOriginalFilename().isEmpty())
                {
                    SkylineTool tool = getToolFromZip(zip);
                    String folderName = (tool != null && tool.getName() != null) ? "_tool_" +
                        FileUtil.getBaseName(FileUtil.makeLegalName(tool.getName())) + "_" +
                        FileUtil.makeLegalName(tool.getVersion()) : "";
                    Container existingVersionContainer = null;
                    boolean addTool = false;
                    HashSet<String> copyFiles = null; // Paths of files to copy to the new tool's container

                    if (tool == null)
                    {
                        getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                            INVALID_TOOL_FILE);
                    }
                    else if (!tool.getMissingValues().isEmpty())
                    {
                        getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                            MISSING_REQUIRED_PROPERTIES + StringUtils.join(tool.getMissingValues(), ", "));
                    }
                    else if (updateTarget >= 0)
                    {
                        // Updating tool with rowId == updateTarget
                        SkylineTool existingVersion = SkylineToolsStoreManager.get().getTool(updateTarget);
                        if (existingVersion == null)
                        {
                            getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                                TOOL_DOES_NOT_EXIST);
                        }

                        // If the container in the request URL does not match the parent of the container associated
                        // with the tool, redirect to a URL with the correct container path.
                        if(!getContainer().equals(existingVersion.getContainerParent()))
                        {
                            ActionURL url = getURL();
                            // Add these parameters so that they are available after the redirect.
                            url.addParameter("sender", sender);
                            url.addParameter("updatetarget", updateTargetString);
                            url.addParameter("toolowners", toolOwners);
                            redirectToToolStoreContainer(existingVersion, url);
                        }

                        if (!tool.getIdentifier().equalsIgnoreCase(existingVersion.getIdentifier()))
                        {
                            getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                                WRONG_TOOL);
                        }
                        else if (tool.getVersion().equalsIgnoreCase(existingVersion.getVersion()))
                        {
                            getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                                SAME_VERSION);
                        }
                        else if (!existingVersion.lookupContainer().hasPermission(getUser(), UpdatePermission.class))
                        {
                            getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                                NO_UPDATE_PERMISSIONS);
                        }
                        else
                        {
                            addTool = true;
                            for (SkylineTool checkTool : SkylineToolsStoreManager.get().getToolsByIdentifier(tool.getIdentifier()))
                            {
                                if (checkTool.getVersion().equalsIgnoreCase(tool.getVersion()))
                                {
                                    addTool = false;
                                    getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                                        OLD_VERSION);
                                    break;
                                }
                            }

                            if (addTool)
                            {
                                existingVersion.setLatest(false);
                                existingVersionContainer = existingVersion.lookupContainer();

                                SkylineToolsStoreManager.get().updateTool(existingVersionContainer, getUser(), existingVersion);

                                copyFiles = getSupplementaryFileBasenames(existingVersion);
                            }
                        }
                    }
                    else if (!getContainer().hasPermission(getUser(), InsertPermission.class))
                    {
                        getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                            NO_INSERT_PERMISSIONS);
                    }
                    else
                    {
                        // Adding a new tool
                        addTool = true;

                        for (SkylineTool checkTool : SkylineToolsStoreManager.get().getToolsLatest())
                        {
                            if (tool.getIdentifier().equalsIgnoreCase(checkTool.getIdentifier()))
                            {
                                addTool = false;
                                getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                                    TOOL_ALREADY_EXISTS);
                                break;
                            }
                        }

                        for (Container checkContainer : getContainer().getChildren())
                        {
                            if (checkContainer.getName().equalsIgnoreCase(folderName))
                            {
                                addTool = false;
                                getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                                    TOOL_ALREADY_EXISTS);
                                break;
                            }
                        }
                    }

                    if (addTool)
                    {
                        Container c = makeContainer(getContainer(), folderName, toolOwnersUsers, RoleManager.getRole(EditorRole.class));
                        copyContainerPermissions(existingVersionContainer, c);
                        zip.transferTo(makeFile(c, zip.getOriginalFilename()));
                        tool.writeIconToFile(makeFile(c, "icon.png"), "png");

                        if (copyFiles != null && existingVersionContainer != null)
                            for (String copyFile : copyFiles)
                                FileUtils.copyFile(makeFile(existingVersionContainer, copyFile), makeFile(c, copyFile), true);

                        tool.setLatest(true);
                        SkylineToolsStoreManager.get().insertTool(c, getUser(), tool);

                        return HttpView.redirect(SkylineToolStoreUrls.getToolDetailsUrl(tool).getLocalURIString());
                    }
                }
                else
                {
                    getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                        NO_FILE);
                }
            }

            getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "sender", sender);
            getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "updatetarget", updateTargetString);
            getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "toolowners", toolOwners);
            return new JspView("/org/labkey/skylinetoolsstore/view/SkylineToolsStoreUpload.jsp", null);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild(getToolStoreNav(getContainer())).addChild("Upload Tool", getURL());
            return root;
        }

        public ActionURL getURL()
        {
            return new ActionURL(InsertAction.class, getContainer());
        }

        @Override
        public void checkPermissions() throws UnauthorizedException
        {
            if(getUser().isGuest())
                throw new UnauthorizedException();
        }
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

    @RequiresNoPermission
    public class SubmitRatingAction extends AbstractController implements PermissionCheckable
    {
        private static final String NO_TITLE = "You did not enter a valid title.";
        private static final String NO_RATING = "You did not submit a valid rating. Ratings must be between 1 and 5.";
        private static final String NO_REVIEW = "You did not submit a valid review.";
        private static final String ALREADY_REVIEWED = "You have already left a review for this tool.";

        public SubmitRatingAction()
        {
        }

        @Override
        protected ModelAndView handleRequestInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Exception
        {
            final User user = getUser();
            final String toolIdString = httpServletRequest.getParameter("toolId");
            int toolId = (toolIdString != null && !toolIdString.isEmpty()) ? Integer.parseInt(toolIdString) : -1;

            final String ratingIdString = httpServletRequest.getParameter("ratingId");
            int ratingId;
            try {
                ratingId = (ratingIdString != null && !ratingIdString.isEmpty()) ? Integer.parseInt(ratingIdString) : -1;;
            } catch(Exception e) {
                return new JspView("/org/labkey/skylinetoolsstore/view/SkylineRating.jsp", null);
            }
            Rating rating = (ratingId < 0) ? null : RatingManager.get().getRatingById(ratingId);
            final SkylineTool tool = SkylineToolsStoreManager.get().getTool((toolId >= 0) ? toolId : rating.getToolId());

            final String ratingValueString = httpServletRequest.getParameter("value");
            final int ratingValue;
            try {
                ratingValue = Integer.parseInt(ratingValueString);
            } catch(Exception e) {
                return new JspView("/org/labkey/skylinetoolsstore/view/SkylineRating.jsp", null);
            }
            final String ratingTitle = httpServletRequest.getParameter("title");
            final String review = httpServletRequest.getParameter("review");

            if (ratingId < 0 && RatingManager.get().userLeftRating(tool.getIdentifier(), getUser()))
            {
                getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                    ALREADY_REVIEWED);
                getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "hideForm",
                    true);
            }
            else if (ratingTitle == null || ratingTitle.isEmpty())
            {
                getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                    NO_TITLE);
            }
            else if (ratingValue < 1 || ratingValue > 5)
            {
                getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                    NO_RATING);
            }
            else if (review == null || review.length() == 0)
            {
                getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                    NO_REVIEW);
            }
            else if (user.isGuest())
            {
                throw new Exception();
            }
            else if (tool == null || (ratingId >= 0 && rating == null))
            {
                throw new Exception();
            }
            else
            {
                if (rating == null)
                {
                    // Adding new rating
                    rating = new Rating(ratingValue, review, toolId, ratingTitle);
                    rating.setContainer(getContainer().getId());
                    RatingManager.get().insertRating(user, rating);
                }
                else
                {
                    // Editing existing rating
                    if (rating.getCreatedBy() != user.getUserId() && !getUser().isSiteAdmin())
                    {
                        throw new Exception();
                    }
                    rating.setTitle(ratingTitle);
                    rating.setRating(ratingValue);
                    rating.setReview(review);
                    RatingManager.get().editRating(rating, user);
                }
                return HttpView.redirect(SkylineToolStoreUrls.getToolDetailsUrl(tool));
            }

            if (toolId >= 0)
                getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "toolId", toolId);
            if (ratingId >= 0)
                getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "ratingId", ratingId);
            if (ratingTitle != null)
                getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "formTitle", ratingTitle);
            getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "formValue", ratingValue);
            if (review != null)
                getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "formReview", review);

            return new JspView("/org/labkey/skylinetoolsstore/view/SkylineRating.jsp", null);
        }

        @Override
        public void checkPermissions() throws UnauthorizedException
        {

        }
    }

    @RequiresNoPermission
    public class DeleteRatingAction extends AbstractController implements PermissionCheckable
    {
        public DeleteRatingAction()
        {
        }

        public ModelAndView handleRequestInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Exception
        {
            int id = NumberUtils.toInt(httpServletRequest.getParameter("id"), -1);
            int user = getUser().getUserId();
            final Rating rating = RatingManager.get().getRatingById(id);
            if(rating != null)
            {
                if (user == rating.getCreatedBy() || getUser().isSiteAdmin())
                    RatingManager.get().deleteRating(id);
                else
                    throw new Exception();
            }

            final SkylineTool tool = SkylineToolsStoreManager.get().getTool(rating.getToolId());
            return HttpView.redirect(SkylineToolStoreUrls.getToolDetailsUrl(tool));
        }

        @Override
        public void checkPermissions() throws UnauthorizedException
        {

        }
    }

    @RequiresNoPermission
    public class InsertSupplementAction extends AbstractController implements PermissionCheckable
    {
        private final Class REQ_PERMS = InsertPermission.class;

        private static final String NO_FILE = "You did not submit a file.";
        private static final String SUPPLEMENT_ALREADY_EXISTS = "The supplementary file already exists.";
        private static final String INVALID_TOOL_ID = "Invalid tool Id in request: ";

        public InsertSupplementAction()
        {
        }

        public ModelAndView handleRequestInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Exception
        {
            final String suppTargetString = httpServletRequest.getParameter("supptarget");
            int suppTarget = NumberUtils.toInt(suppTargetString, -1);

            if(suppTarget == -1)
            {
                httpServletRequest.setAttribute(BindingResult.MODEL_KEY_PREFIX + "form", INVALID_TOOL_ID + " " + suppTargetString);
                httpServletRequest.setAttribute(BindingResult.MODEL_KEY_PREFIX + "supptarget", suppTargetString);
                return new JspView("/org/labkey/skylinetoolsstore/view/SkylineToolSupplementUpload.jsp", null);
            }

            SkylineTool tool = SkylineToolsStoreManager.get().getTool(suppTarget);
            final Container c = tool.lookupContainer();
            if (!c.hasPermission(getUser(), REQ_PERMS))
                throw new Exception();

            Map<?, ?> fileMap = ((MultipartHttpServletRequest)httpServletRequest).getFileMap();
            MultipartFile suppFile = (MultipartFile)(fileMap.get("suppFile"));

            if (!suppFile.getOriginalFilename().isEmpty())
            {
                File targetFile = makeFile(c, FileUtil.makeLegalName(suppFile.getOriginalFilename()));
                if (targetFile.exists())
                {
                    // Can't upload supplementary file if the file already exists
                    getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                        SUPPLEMENT_ALREADY_EXISTS);
                }
                else
                {
                    suppFile.transferTo(targetFile);
                    return HttpView.redirect(SkylineToolStoreUrls.getToolDetailsUrl(tool));
                }
            }
            else
            {
                getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                    NO_FILE);
            }

            getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "supptarget", suppTargetString);
            return new JspView("/org/labkey/skylinetoolsstore/view/SkylineToolSupplementUpload.jsp", null);
        }

        @Override
        public void checkPermissions() throws UnauthorizedException
        {

        }
    }

    @RequiresNoPermission
    public class DeleteSupplementAction extends AbstractController implements PermissionCheckable
    {
        private final Class REQ_PERMS = DeletePermission.class;

        public DeleteSupplementAction()
        {
        }

        public ModelAndView handleRequestInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Exception
        {
            final String sender = httpServletRequest.getParameter("sender");
            final int suppTarget = Integer.parseInt(httpServletRequest.getParameter("supptarget"));

            final String suppFile = httpServletRequest.getParameter("suppFile");

            final SkylineTool tool = SkylineToolsStoreManager.get().getTool(suppTarget);
            if (tool == null)
                throw new NotFoundException("Could not find tool with Id " + suppTarget);

            final Container c = tool.lookupContainer();
            if (!c.hasPermission(getUser(), REQ_PERMS))
                throw new Exception();

            File targetDel = makeFile(c, suppFile);

            if (targetDel.isFile() &&
                !targetDel.getName().equalsIgnoreCase("icon.png") &&
                !targetDel.getName().equalsIgnoreCase(tool.getZipName()))
                targetDel.delete();
            else
                throw new Exception();

            return HttpView.redirect((sender != null) ? sender :
                    SkylineToolStoreUrls.getToolDetailsUrl(tool).getLocalURIString());
        }

        @Override
        public void checkPermissions() throws UnauthorizedException
        {

        }
    }

    @RequiresLogin
    public class DeleteAction extends RedirectAction<IdForm>
    {
        @Override
        public URLHelper getSuccessURL(IdForm idForm)
        {
            return SkylineToolStoreUrls.getToolStoreHomeUrl(getContainer(), getUser());
        }

        @Override
        public boolean doAction(IdForm idForm, BindException errors) throws Exception
        {
            final SkylineTool tool = SkylineToolsStoreManager.get().getTool(idForm.getId());

            if(tool == null)
            {
                errors.reject(ERROR_MSG, "Tool with id " + idForm.getId() + " does not exist.");
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
                RatingManager.get().deleteRatingsByToolId(toDelete.getRowId());
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
        private int _id;

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

        public int getId()
        {
            return _id;
        }

        public void setId(int id)
        {
            _id = id;
        }
    }

    @RequiresLogin
    public class DeleteLatestAction extends AbstractController implements PermissionCheckable
    {
        private final Class REQ_PERMS = DeletePermission.class;

        public ModelAndView handleRequestInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Exception
        {
            Integer id;
            try {
                id = Integer.parseInt(httpServletRequest.getParameter("id"));
            }
            catch(Exception e) {
                return null;
            }
            final SkylineTool tool = SkylineToolsStoreManager.get().getTool(id);

            if (!tool.lookupContainer().hasPermission(getUser(), REQ_PERMS))
                throw new UnauthorizedException("User does not have permission to delete the tool.");

            final String sender = httpServletRequest.getParameter("sender");
            ActionURL senderUrl = sender != null ? new ActionURL(sender) : null;

            // Get the tool store container, in case we need it, before we delete the tool and its container.
            Container toolStoreContainer = tool != null ? tool.getContainerParent() : getContainer();

            if (tool != null)
            {
                final String identifier = tool.getIdentifier();
                SkylineTool[] tools = sortToolsByCreateDate(SkylineToolsStoreManager.get().getToolsByIdentifier(identifier));

                // This action cannot be used if there is only one version
                if (tools.length == 1)
                    throw new Exception();

                RatingManager.get().deleteRatingsByToolId(tool.getRowId());
                ContainerManager.delete(tools[0].lookupContainer(), getUser());

                if (tools.length > 1)
                {
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
                }
            }

            return HttpView.redirect((senderUrl != null) ? senderUrl.getLocalURIString() :
                SkylineToolStoreUrls.getToolStoreHomeUrl(toolStoreContainer, getUser()).getLocalURIString());
        }

        @Override
        public void checkPermissions() throws UnauthorizedException
        {

        }
    }

    @RequiresNoPermission
    public class DownloadToolAction extends AbstractController implements PermissionCheckable
    {
        public static final String DOWNLOADED_COOKIE_PREFIX = "downloadtool";

        public ModelAndView handleRequestInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Exception
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

                SkylineToolsStoreManager.get().recordToolDownload(tool);

                DateFormat df = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss 'GMT'", Locale.US);
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.SECOND, expires);

                httpServletResponse.setHeader("Set-Cookie",
                    DOWNLOADED_COOKIE_PREFIX + tool.getRowId() + "=1; " +
                    "Expires=" + df.format(calendar.getTime()) + "; " +
                    "Max-Age=" + expires + "; " +
                    "Path=/; Domain=;");
            }

            return new HttpRedirectView(
                AppProps.getInstance().getContextPath() + "/files" + tool.lookupContainer().getPath()
                + "/" + tool.getZipName());
        }

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
    public class DownloadToolFileAction extends SimpleViewAction<DownloadFileForm> implements PermissionCheckable
    {
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
                    PageFlowUtil.streamFile(getViewContext().getResponse(), downloadFile, true);
                    return null;
                }
            }
            else
            {
                errors.reject(SpringActionController.ERROR_MSG, "Could not find tool with name " + form.getTool());
            }

            return new SimpleErrorView(errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
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
                    StringBuilder msg = new StringBuilder("Could not find tool ").append(" by Id ").append(toolId);
                    errors.reject(SpringActionController.ERROR_MSG,  msg.toString());
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

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
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
    public class SetOwnersAction extends AbstractController implements PermissionCheckable
    {
        private static final String UNKNOWN_USERS = "The following users are unknown: ";

        public SetOwnersAction()
        {
        }

        public ModelAndView handleRequestInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Exception
        {
            final String sender = httpServletRequest.getParameter("sender");
            final String updateTargetString = httpServletRequest.getParameter("updatetarget");
            final int updateTarget = Integer.parseInt(updateTargetString);

            final String toolOwners = httpServletRequest.getParameter("toolOwners");

            Pair<ArrayList<User>, ArrayList<String>> parsedOwners = parseToolOwnerString(toolOwners);
            ArrayList<User> toolOwnersUsers = parsedOwners.first;
            ArrayList<String> toolOwnersInvalid = parsedOwners.second;

            if (toolOwnersInvalid.isEmpty())
            {
                final SkylineTool tool = SkylineToolsStoreManager.get().getTool(updateTarget);
                final Container c = tool.lookupContainer();

                ArrayList<User> newToolEditors = new ArrayList<>();
                for (User u : toolOwnersUsers)
                    newToolEditors.add(u);

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
                SecurityPolicyManager.savePolicy(policy);

                Container toolStoreContainer = tool != null ? tool.getContainerParent() : getContainer();

                return HttpView.redirect((sender != null) ? sender :
                        SkylineToolStoreUrls.getToolStoreHomeUrl(toolStoreContainer, getUser()).getLocalURIString());
            }
            else
            {
                getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "form",
                    UNKNOWN_USERS + StringUtils.join(toolOwnersInvalid, ", "));
            }

            getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "toolowners", toolOwners);
            getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "sender", sender);
            getViewContext().getRequest().setAttribute(BindingResult.MODEL_KEY_PREFIX + "updatetarget", updateTargetString);
            return new JspView("/org/labkey/skylinetoolsstore/view/SkylineToolManageOwners.jsp", null);
        }

        @Override
        public void checkPermissions() throws UnauthorizedException
        {

        }
    }

    @RequiresNoPermission
    public class UpdatePropertyAction extends AbstractController implements PermissionCheckable
    {
        private final Class REQ_PERMS = InsertPermission.class;

        public ModelAndView handleRequestInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Exception
        {
            final Integer id = Integer.parseInt(httpServletRequest.getParameter("id"));

            final SkylineTool tool = SkylineToolsStoreManager.get().getTool(id);
            if(tool == null)
            {
                throw new IllegalStateException("No tool found for id " + id);
            }
            final Container container = tool.lookupContainer();
            if (!container.hasPermission(getUser(), REQ_PERMS))
                throw new UnauthorizedException("User does not have permissions to edit tool properties.");

            final String propName = httpServletRequest.getParameter("propName");
            String propValue = "";
            final MultipartFile icon = (httpServletRequest.getMethod().equalsIgnoreCase("post") &&
                httpServletRequest instanceof MultipartHttpServletRequest) ?
                (MultipartFile)((Map<?, ?>)(((MultipartHttpServletRequest)httpServletRequest).getFileMap())).get("propValue")
                : null;


            if (icon == null)
            {
                propValue = httpServletRequest.getParameter("propValue").replace("\r", "").replace("\n", "\r\n");
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

            if (!zipFile.delete())
                throw new IOException("Couldn't delete " + zipFile);
            tmpFile.renameTo(zipFile);

            if (icon == null)
                SkylineToolsStoreManager.get().updateTool(tool.lookupContainer(), getUser(), tool);
            else
                tool.writeIconToFile(makeFile(tool.lookupContainer(), "icon.png"), "png");

            String sender = httpServletRequest.getParameter("sender");
            return HttpView.redirect((sender != null) ? sender : new ActionURL(BeginAction.class, getContainer()).getLocalURIString());
        }

        @Override
        public void checkPermissions() throws UnauthorizedException
        {

        }
    }

    @RequiresNoPermission
    public class GetToolsApiAction extends AbstractController implements PermissionCheckable
    {
        public ModelAndView handleRequestInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException
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
                sb.append(jsonObject.toString());
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
