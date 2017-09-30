/*
 * Copyright (c) 2013 LabKey Corporation
 * Author: Yuval Boss  (yuval@uw.edu)
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

package org.labkey.signup;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.Table;
import org.labkey.api.security.Group;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityMessage;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.WebPartView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SignUpController extends SpringActionController
{
    private static final Logger _log = Logger.getLogger(SignUpController.class);

    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SignUpController.class);

    public SignUpController()
    {
        setActionResolver(_actionResolver);
    }

    public static ActionURL getShowSignUpAdminUrl()
    {
        return new ActionURL(ShowSignUpAdminAction.class, ContainerManager.getRoot());
    }

    @RequiresSiteAdmin
    public class ShowSignUpAdminAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            JspView<User> result = new JspView<>("/org/labkey/signup/SignUpAdmin.jsp", getUser());
            result.setFrame(WebPartView.FrameType.PORTAL);
            result.setTitle("Sign Up Administration");
            return result;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            PageFlowUtil.urlProvider(AdminUrls.class).appendAdminNavTrail(root, "Sign Up Admin", null);
            return root;
        }
    }

   @RequiresSiteAdmin
   public class AddPropertyAction extends RedirectAction<AddPropertyForm>
   {
       @Override
       public URLHelper getSuccessURL(AddPropertyForm addPropertyForm)
       {
           return getShowSignUpAdminUrl();
       }

       @Override
       public boolean doAction(AddPropertyForm addPropertyForm, BindException errors) throws Exception
       {
           Container c = ContainerManager.getForRowId(addPropertyForm.getContainerId());
           if(c == null)
           {
               errors.addError(new LabKeyError("No container found for rowId " + addPropertyForm.getContainerId()));
               return false;
           }
           PropertyManager.PropertyMap m = PropertyManager.getWritableProperties(c, SignUpModule.SIGNUP_CATEGORY, true);
           Integer groupId = SecurityManager.getGroupId(c.getProject(), addPropertyForm.getGroupName());
           if(groupId == null)
           {
               errors.addError(new LabKeyError("No groupId found for group " + addPropertyForm.getGroupName() + " in container " + c.getPath()));
               return false;
           }
           m.put(SignUpModule.SIGNUP_GROUP_NAME, addPropertyForm.getGroupName());
           m.save();
           return true;
       }

       @Override
       public void validateCommand(AddPropertyForm target, Errors errors)
       {

       }
   }

    @RequiresSiteAdmin
    public class RemovePropertyAction extends RedirectAction<ContainerIdForm>
    {
        @Override
        public URLHelper getSuccessURL(ContainerIdForm containerIdForm)
        {
            return getShowSignUpAdminUrl();
        }

        @Override
        public boolean doAction(ContainerIdForm containerIdForm, BindException errors) throws Exception
        {
            Container c = ContainerManager.getForRowId(containerIdForm.getContainerId());
            if(c == null)
            {
                errors.addError(new LabKeyError("No container found for rowId " + containerIdForm.getContainerId()));
                return false;
            }
            PropertyManager.PropertyMap m = PropertyManager.getWritableProperties(c, SignUpModule.SIGNUP_CATEGORY, true);
            m.remove(SignUpModule.SIGNUP_GROUP_NAME);
            m.save();

            return true;
        }

        @Override
        public void validateCommand(ContainerIdForm target, Errors errors)
        {

        }
    }

    @RequiresSiteAdmin
    public class AddGroupChangeProperty extends RedirectAction<AddGroupChangeForm>
    {
        @Override
        public URLHelper getSuccessURL(AddGroupChangeForm containerIdForm)
        {
            return getShowSignUpAdminUrl();
        }

        @Override
        public boolean doAction(AddGroupChangeForm addGroupChangeForm, BindException errors) throws Exception
        {
            if((addGroupChangeForm.getOldgroup() == 0 || addGroupChangeForm.getNewgroup() == 0) || // if both groups have id=0
                    (addGroupChangeForm.getOldgroup() == addGroupChangeForm.getNewgroup())) // if both groups are the same
                return false;
            PropertyManager.PropertyMap m = PropertyManager.getWritableProperties(SignUpModule.SIGNUP_GROUP_TO_GROUP, true);
            String existingRules = m.get(String.valueOf(addGroupChangeForm.getOldgroup()));
            if(existingRules == null)
                existingRules = "";
            ArrayList<String> rules = new ArrayList<>(Arrays.asList(existingRules.split(",")));
            if(!rules.contains(String.valueOf(addGroupChangeForm.getNewgroup())))
                rules.add(String.valueOf(addGroupChangeForm.getNewgroup()));
            String newProperties = StringUtils.join(rules, ',');
            m.put(String.valueOf(addGroupChangeForm.getOldgroup()), newProperties);

            m.save();
            return true;
        }

        @Override
        public void validateCommand(AddGroupChangeForm target, Errors errors)
        {

        }
    }

    @RequiresSiteAdmin
    public class RemoveGroupChangeProperty extends RedirectAction<AddGroupChangeForm>
    {
        @Override
        public URLHelper getSuccessURL(AddGroupChangeForm containerIdForm)
        {
            return getShowSignUpAdminUrl();
        }

        @Override
        public boolean doAction(AddGroupChangeForm addGroupChangeForm, BindException errors) throws Exception
        {
            int oldgroup = addGroupChangeForm.getOldgroup();
            int newgroup = addGroupChangeForm.getNewgroup();
            if(oldgroup == 0 || newgroup == 0)
                return false;
            PropertyManager.PropertyMap m = PropertyManager.getWritableProperties(SignUpModule.SIGNUP_GROUP_TO_GROUP, true);
            String existingRules = m.get(String.valueOf(oldgroup));
            ArrayList<String> rules = new ArrayList<>(Arrays.asList(existingRules.split(",")));
            if(!rules.contains(String.valueOf(newgroup)))
                return false;
            rules.remove(String.valueOf(newgroup));
            String newProperties = StringUtils.join(rules, ',');
            m.put(String.valueOf(oldgroup), newProperties);
            if(rules.size() == 0 || (rules.size() == 1 && rules.contains("")))
                m.remove(oldgroup);

            m.save();
            return true;
        }

        @Override
        public void validateCommand(AddGroupChangeForm target, Errors errors)
        {

        }
    }

    public static class ContainerIdForm
    {
        private int _containerId;

        public int getContainerId()
        {
            return _containerId;
        }

        public void setContainerId(int containerId)
        {
            _containerId = containerId;
        }
    }

    public static class AddPropertyForm extends ContainerIdForm
    {
        private String _groupName;

        public String getGroupName()
        {
            return _groupName;
        }

        public void setGroupName(String groupName)
        {
            _groupName = groupName;
        }
    }

    public static class AddGroupChangeForm
    {
        private int _labkeyUserId;
        private int _oldgroup;
        private int _newgroup;

        public int getLabkeyUserId()
        {
            return _labkeyUserId;
        }

        public void setLabkeyUserId(int labkeyUserId)
        {
            _labkeyUserId = labkeyUserId;
        }

        public int getOldgroup()
        {
            return _oldgroup;
        }

        public void setOldgroup(int oldgroup)
        {
            _oldgroup = oldgroup;
        }

        public int getNewgroup()
        {
            return _newgroup;
        }

        public void setNewgroup(int newgroup)
        {
            _newgroup = newgroup;
        }
    }


    // Class ConfirmAction handles a user trying to confirm an account creation.  If the email and confirmation code match
    // that in our database they will be added to the LabKey user base
    @RequiresNoPermission
    public class ConfirmAction extends SimpleViewAction<SignupConfirmForm>
    {
        public ModelAndView getView(SignupConfirmForm form, BindException errors) throws Exception
        {
            ValidEmail email;
            try
            {
                email = new ValidEmail(form.getEmail());
            }
            catch (ValidEmail.InvalidEmailException iee)
            {
                errors.reject(ERROR_MSG, "Invalid email address " + form.getEmail() + " in account confirmation request.");
                return new SimpleErrorView(errors, false);
            }

            String key = form.getKey();

            // Check parameter email & verification(key) match that in database
            TempUser tempUser = SignUpManager.get().verifyUser(email, key);
            if (tempUser != null)
            {
                if (UserManager.getUser(email) != null)
                {
                    // First check if user already exists.
                    errors.addError(new LabKeyError(String.format(SignUpManager.USER_ALREADY_EXISTS, form.getEmail())));
                    return new SimpleErrorView(errors, false);
                }
                else
                {
                    SecurityManager.NewUserStatus newUserStatus;

                    // User creation should be in a transaction
                    try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
                    {
                        // Add user to LabKey core database
                        newUserStatus = SecurityManager.addUser(new ValidEmail(email.getEmailAddress()), null);
                        User newUser = newUserStatus.getUser();
                        // Set user's first, last, and organization name
                        newUser.setFirstName(tempUser.getFirstName());
                        newUser.setLastName(tempUser.getLastName());
                        newUser.setDescription(StringUtils.isBlank(tempUser.getOrganization()) ? "" : "Organization: " + tempUser.getOrganization()); // don't add anything if organization is empty

                        Container c = tempUser.getContainer();
                        PropertyManager.PropertyMap property = PropertyManager.getWritableProperties(c, SignUpModule.SIGNUP_CATEGORY, false);
                        if (property != null && property.get(SignUpModule.SIGNUP_GROUP_NAME) != null)
                        {
                            Integer groupId = SecurityManager.getGroupId(c.getProject(), property.get(SignUpModule.SIGNUP_GROUP_NAME));
                            if (groupId != null)
                            {
                                final Group group = SecurityManager.getGroup(groupId);
                                SecurityManager.addMember(group, newUser);
                                UserManager.updateUser(newUser, newUser); // Update user in LabKey core database.
                            }
                        }
                        tempUser.setLabkeyUserId(newUser.getUserId());
                        Table.update(null, SignUpManager.getTableInfoTempUsers(), tempUser, tempUser.getUserId());

                        transaction.commit();
                    }

                    // Send email to site admin
                    ActionURL confirmationUrl = getConfirmationURL(getContainer(), email, tempUser.getKey());
                    // _log.info("Confirmation URL: " + confirmationUrl.getLocalURIString());
                    String siteAdminEmail = LookAndFeelProperties.getInstance(getContainer()).getSystemEmailAddress();
                    // _log.info("Site admin email: " + siteAdminEmail);
                    User newUser = newUserStatus.getUser();
                    // _log.info("New user: " + newUser.getEmail());
                    try
                    {
                        SecurityMessage msg = SecurityManager.getRegistrationMessage(null, true);
                        msg.setTo(email.getEmailAddress()); // This will add the new user's email address to
                                                            // the message body and subject.
                        // newUser is used for auditing purposes, the user who originated the message
                        SecurityManager.sendEmail(getContainer(), newUser, msg, siteAdminEmail, confirmationUrl);
                    }
                    catch(Exception e)
                    {
                        _log.error(e);
                    }

                    // creates and redirects the new user to their setPasswordAction page
                    ActionURL url = SecurityManager.createVerificationURL(getContainer(), email, newUserStatus.getVerification(), null);
                    return new ModelAndView(new RedirectView(url.getURIString()));
                }
            }
            else
            {
                errors.addError(new LabKeyError(SignUpManager.CONFIRMATION_DID_NOT_MATCH));
                return new SimpleErrorView(errors, false);
            }
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    public static class SignupConfirmForm
    {
        private String _email;
        private String _key;

        public String getEmail()
        {
            return _email;
        }

        public void setEmail(String email)
        {
            _email = email;
        }

        public String getKey()
        {
            return _key;
        }

        public void setKey(String key)
        {
            _key = key;
        }
    }


    // Class BeginAction handles a user creating an account and sends them a confirmation email
    // Adds user to a temporary database where credentials can be confirmed in class @ConfirmAction
    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends FormViewAction<SignupForm>
    {
        @Override
        public void validateCommand(SignupForm form, Errors errors)
        {
            validateSignupForm(form, errors);
        }

        public ModelAndView getView(SignupForm form, boolean reshow, BindException errors) throws Exception
        {
            if(form.isNewSignUp())
            {
                JspView view = new JspView<Object>("/org/labkey/signup/signupPage.jsp", form, errors);
                view.setFrame(WebPartView.FrameType.PORTAL);
                view.setTitle("Sign Up");
                return view;
            }
            else
            {
                String message = form.isAccountExists() ? SignUpManager.USER_ALREADY_EXISTS : SignUpManager.CONFIRMATION_SENT;
                message = String.format(message, form.getEmail());
                if(form.isAccountExists())
                {
                    errors.addError(new LabKeyError(message));
                    return new SimpleErrorView(errors);
                }
                else
                {
                    return new HtmlView(PageFlowUtil.filter(message));
                }
            }
        }

        @Override
        public boolean handlePost(SignupForm signupForm, BindException errors) throws Exception
        {
            ValidEmail email;
            try
            {
                email = new ValidEmail(signupForm.getEmail());
            }
            catch (ValidEmail.InvalidEmailException iee)
            {
                errors.reject(ERROR_MSG, iee.getMessage());
                return false;
            }

            if(UserManager.userExists(email))
            {
                // If the user already exists forward them to a page where they can click on a link to recover their password, if required
                signupForm.setAccountExists(true);
                signupForm.setNewSignUp(false);
                return false;
            }

            // If the user does not exit in LabKey's core database, check in our temporaryusers table
            TempUser tempUser = getTempUser(signupForm, email);

            // Send email to the user.
            ActionURL confirmationUrl = getConfirmationURL(getContainer(), email, tempUser.getKey());
            try
            {
                User mockUser = new User();
                mockUser.setEmail(email.getEmailAddress());
                SecurityManager.sendEmail(getContainer(), mockUser, SecurityManager.getRegistrationMessage(null, false), email.getEmailAddress(), confirmationUrl);
            }
            catch(Exception e)
            {
                String systemEmail = LookAndFeelProperties.getInstance(getContainer()).getSystemEmailAddress();
                errors.reject(ERROR_MSG, "Could not send new user registration email.  Please contact your server administrator at " + systemEmail);
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }


            signupForm.setNewSignUp(false);
            return false;
        }

        @Override
        public URLHelper getSuccessURL(SignupForm signupForm)
        {
            throw new UnsupportedOperationException();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    private void validateSignupForm(SignupForm form, Errors errors)
    {
        if(StringUtils.isBlank(form.getFirstName()))
        {
            errors.reject(ERROR_MSG, "First name cannot be blank.");
        }
        if(StringUtils.isBlank(form.getLastName()))
        {
            errors.reject(ERROR_MSG, "Last name cannot be blank.");
        }
        if(StringUtils.isBlank(form.getOrganization()))
        {
            errors.reject(ERROR_MSG, "Organization cannot be blank.");
        }
        if(StringUtils.isBlank(form.getEmail()))
        {
            errors.reject(ERROR_MSG, "Email cannot be blank.");
        }
    }

    public static ActionURL getConfirmationURL(Container c, ValidEmail email, String key)
    {
        ActionURL url = new ActionURL(ConfirmAction.class, c);
        url.addParameter("key", key);
        url.addParameter("email", email.getEmailAddress());
        return url;
    }

    public static class SignupForm extends ReturnUrlForm
    {
        private String _firstName;
        private String _lastName;
        private String _organization;
        private String _email;
        private boolean _accountExists;
        private boolean _newSignUp = true;

        public String getFirstName()
        {
            return _firstName;
        }

        public void setFirstName(String firstName)
        {
            _firstName = firstName;
        }

        public String getLastName()
        {
            return _lastName;
        }

        public void setLastName(String lastName)
        {
            _lastName = lastName;
        }

        public String getOrganization()
        {
            return _organization;
        }

        public void setOrganization(String organization)
        {
            _organization = organization;
        }

        public String getEmail()
        {
            return _email;
        }

        public void setEmail(String email)
        {
            _email = email;
        }

        public boolean isAccountExists()
        {
            return _accountExists;
        }

        public void setAccountExists(boolean accountExists)
        {
            _accountExists = accountExists;
        }

        public boolean isNewSignUp()
        {
            return _newSignUp;
        }

        public void setNewSignUp(boolean newSignUp)
        {
            _newSignUp = newSignUp;
        }
    }

    @RequiresLogin
    public class ChangeGroupsApiAction extends ApiAction<AddGroupChangeForm>
    {
        @Override
        public ApiResponse execute(AddGroupChangeForm addGroupChangeForm, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            User user = getViewContext().getUser();
            if(user == null) {
                response.put("status", "NA_USER");
                return response;
            }

            if(!user.isInGroup(addGroupChangeForm.getOldgroup())) {
                response.put("status", "NO_PERMISSIONS");
                return response;
            }
            PropertyManager.PropertyMap m = PropertyManager.getWritableProperties(SignUpModule.SIGNUP_GROUP_TO_GROUP, true);
            String existingRules = m.get(String.valueOf(addGroupChangeForm.getOldgroup()));
            if(existingRules == null) {
                response.put("status", "NO_PERMISSIONS");
                return response;
            }
            ArrayList<String> rules = new ArrayList<>(Arrays.asList(existingRules.split(",")));
            if(!rules.contains(String.valueOf(addGroupChangeForm.getNewgroup()))) {
                response.put("status", "NO_PERMISSIONS");
                return response;
            }
            // once reached here we can assume that group a and b exist and that a rule exists allowing
            // a user to change from group a to group b
            try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                final Group oldgroup = SecurityManager.getGroup(addGroupChangeForm.getOldgroup());
                final Group newgroup = SecurityManager.getGroup(addGroupChangeForm.getNewgroup());
                SecurityManager.addMember(newgroup, user);
                SecurityManager.deleteMember(oldgroup, user);
                UserManager.updateUser(user, user);
                addGroupChangeForm.setLabkeyUserId(user.getUserId());
                Table.insert(user, SignUpSchema.getTableInfoMovedUsers(), addGroupChangeForm);
                response.put("status", "USER_MOVED_SUCCESS");  // success status
                transaction.commit();
            }
            if(response.get("status") == null) // if transaction failed
                response.put("status", "USER_MOVED_ERROR");
            return response;
        }
    }

    @RequiresNoPermission
    public class SignUpApiAction extends ApiAction<SignupForm>
    {

        @Override
        public ApiResponse execute(SignupForm signupForm, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            ValidEmail email;
            try
            {
                email = new ValidEmail(signupForm.getEmail());
            }
            catch (ValidEmail.InvalidEmailException iee)
            {
                errors.reject(ERROR_MSG, iee.getMessage());
                return response;
            }

            if(UserManager.userExists(email))
            {
                response.put("status", "USER_EXISTS");
                return response;
            }

            validateSignupForm(signupForm, errors);
            if(errors.hasErrors())
            {
                return response;
            }

            TempUser tempUser = getTempUser(signupForm, email);


            // Send email to the user.
            ActionURL confirmationUrl = getConfirmationURL(getContainer(), email, tempUser.getKey());
            try
            {
                User mockUser = new User();
                mockUser.setEmail(email.getEmailAddress());
                SecurityManager.sendEmail(getContainer(), mockUser, SecurityManager.getRegistrationMessage(null, false), email.getEmailAddress(), confirmationUrl);
            }
            catch(Exception e)
            {
                String systemEmail = LookAndFeelProperties.getInstance(getContainer()).getSystemEmailAddress();
                List<String> messages = new ArrayList<>();
                messages.add("Could not send new user registration email.  Please contact your server administrator at " + systemEmail);
                messages.add(e.getMessage());
                response.put("error_message", messages);
            }

            signupForm.setNewSignUp(false); // TODO: Most likely not needed here
            response.put("status", "USER_ADDED");

            return response;
        }
    }

    private TempUser getTempUser(SignupForm signupForm, ValidEmail email) throws java.sql.SQLException
    {
        // If the user does not exit in LabKey's core database, check in our temporaryusers table
        TempUser tempUser = SignUpManager.get().getTempUserWithEmail(email.getEmailAddress());

        if(tempUser == null)
        {   // If tempUser is null create a new TempUser object and populate fields
            tempUser = new TempUser();
            tempUser.setEmail(email.getEmailAddress());
            tempUser.setFirstName(signupForm.getFirstName());
            tempUser.setLastName(signupForm.getLastName());
            tempUser.setOrganization(signupForm.getOrganization());
            tempUser.setKey(SecurityManager.createTempPassword());
            tempUser.setContainer(getContainer());

            Table.insert(null, SignUpManager.getTableInfoTempUsers(), tempUser);
        }
        return tempUser;
    }
}