package org.labkey.panoramapublic;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.announcements.api.Announcement;
import org.labkey.api.announcements.api.AnnouncementService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.Group;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserUrls;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalExperiment;
import org.labkey.panoramapublic.query.JournalManager;

import javax.mail.MessagingException;
import java.util.Set;

public class PanoramaPublicNotification
{
    private static final String NL = "\n";

    private enum ACTION {NEW, UPDATED, DELETED, COPIED, RESUBMITTED}

    public static void notifyCreated(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, User user)
    {
        StringBuilder messageBody = getCreateUpdateMessageBody(expAnnotations, journal, je, ACTION.NEW, user);
        postNotification(expAnnotations, journal, je, ACTION.NEW, messageBody.toString());
    }

    public static void notifyUpdated(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, User user)
    {
        StringBuilder messageBody = getCreateUpdateMessageBody(expAnnotations, journal, je, ACTION.UPDATED, user);
        postNotification(expAnnotations, journal, je, ACTION.UPDATED, messageBody.toString());
    }

    public static void notifyDeleted(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, User user)
    {
        StringBuilder messageBody = new StringBuilder();
        appendRequestName(expAnnotations, journal, ACTION.DELETED, messageBody);
        messageBody.append(NL).append("* ExperimentID: ").append(expAnnotations.getId());
        messageBody.append(NL).append("* User Folder: ").append(getContainerLink(expAnnotations.getContainer()));
        appendActionSubmitterDetails(expAnnotations, user, messageBody);

        postNotification(expAnnotations, journal, je, ACTION.DELETED, messageBody.toString());
    }

    public static void notifyResubmitted(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, Container currentJournalFolder, User user)
    {
        StringBuilder messageBody = new StringBuilder();
        appendFullMessageBody(expAnnotations, journal, je, ACTION.RESUBMITTED, user, messageBody);
        messageBody.append(NL);
        messageBody.append(NL).append(bold("Current Journal Folder:")).append(" ").append(getContainerLink(currentJournalFolder));

        postNotification(expAnnotations, journal, je, ACTION.RESUBMITTED, messageBody.toString());
    }

    private static void postNotification(ExperimentAnnotations experimentAnnotations, Journal journal, JournalExperiment je, ACTION action, String messageBody)
    {
        postNotification(experimentAnnotations, journal, je, action, messageBody, false);
    }

    private static void postNotification(ExperimentAnnotations experimentAnnotations, Journal journal, JournalExperiment je, ACTION action, String messageBody, boolean sendEmail)
    {
        AnnouncementService svc = AnnouncementService.get();
        Container supportContainer = journal.getSupportContainer();
        String messageTitle = "ID: " + experimentAnnotations.getId() +" " + experimentAnnotations.getContainer().getPath();

        // The user submitting the request does not have permissions to post in the journal's support container.  So the
        // announcement will be posted by the journal admin user.
        User panoramaPublicAdmin = getJournalAdminUser(journal);

        if(je.getAnnouncementId() != null)
        {
            svc.insertAnnouncement(supportContainer, panoramaPublicAdmin, messageTitle, messageBody, true, je.getAnnouncementId());
        }
        else
        {
            Announcement announcement = svc.insertAnnouncement(supportContainer, panoramaPublicAdmin, messageTitle, messageBody, true);
            je.setAnnouncementId(announcement.getRowId());
            JournalManager.updateJournalExperiment(je, panoramaPublicAdmin);
        }

        if(sendEmail)
        {
            sendEmailNotification(experimentAnnotations, journal, panoramaPublicAdmin, action, messageBody);
        }
    }

    private static void sendEmailNotification(ExperimentAnnotations exptAnnotations, Journal journal, User user, ACTION action, String messageBody)
    {
        // Email contact person for the journal
        String journalEmail = LookAndFeelProperties.getInstance(journal.getProject()).getSystemEmailAddress();
        String serverAdminEmail = LookAndFeelProperties.getInstance(ContainerManager.getHomeContainer()).getSystemEmailAddress();

        try
        {
            MailHelper.ViewMessage m = MailHelper.createMessage(serverAdminEmail, journalEmail);
            String subject = String.format("**%s** Experiment ID: %d has been submitted to Panorama Public", action, exptAnnotations.getId());
            m.setSubject(subject);
            m.setText(messageBody);
            MailHelper.send(m, user, exptAnnotations.getContainer());
        }
        catch (MessagingException e)
        {
            throw new RuntimeException("Error sending email notification", e);
        }
    }

    private static StringBuilder getCreateUpdateMessageBody(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment journalExperiment, ACTION action, User user)
    {
        StringBuilder text = new StringBuilder();
        appendFullMessageBody(expAnnotations, journal, journalExperiment, action, user, text);
        return text;
    }

    private static void appendFullMessageBody(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment journalExperiment, ACTION action,
                                              User user, StringBuilder text)
    {
        appendRequestName(expAnnotations, journal, action, text);
        appendSubmissionDetails(expAnnotations, journalExperiment, text);
        appendUserDetails(expAnnotations.getSubmitterUser(), expAnnotations.getContainer(), "Submitter", text);
        appendUserDetails(expAnnotations.getLabHeadUser(), expAnnotations.getContainer(), "Lab Head", text);

        if(journalExperiment.hasLabHeadDetails())
        {
            text.append(NL);
            text.append(NL).append(bold("Lab Head details provided in submission form:"));
            text.append(NL).append("Lab Head: ").append(journalExperiment.getLabHeadName());
            text.append(NL).append("Lab Head Email: ").append(journalExperiment.getLabHeadEmail());
            text.append(NL).append("Lab Head Affiliation: ").append(journalExperiment.getLabHeadAffiliation());
        }

        if(expAnnotations.getLabHeadUser() == null && !journalExperiment.hasLabHeadDetails() && journalExperiment.isPxidRequested())
        {
            text.append(NL);
            text.append(NL).append(italics(bold("Lab Head details were not provided. Submitter's details will be used in the Lab Head field for announcing data to ProteomeXchange.")));
        }
        appendActionSubmitterDetails(expAnnotations, user, text);
    }

    private static void appendRequestName(ExperimentAnnotations expAnnotations, Journal journal, ACTION action, @NotNull StringBuilder text)
    {
        text.append(bold(String.format("%s: Experiment ID %d submitted to %s", action, expAnnotations.getId(), journal.getName())));
    }

    private static void appendSubmissionDetails(ExperimentAnnotations exptAnnotations, JournalExperiment journalExperiment, @NotNull StringBuilder text)
    {
        text.append(NL);
        text.append(NL).append("* ExperimentID: ").append(exptAnnotations.getId());
        text.append(NL).append("* Reviewer Account Requested: ").append(bold(journalExperiment.isKeepPrivate() ? "Yes" : "No"));
        text.append(NL).append("* PX ID Requested: ").append(bold(journalExperiment.isPxidRequested() ? "Yes" : "No"));
        text.append(NL).append("* Access URL: ").append(journalExperiment.getShortAccessUrl().renderShortURL());
        text.append(NL).append("* User Folder: ").append(getContainerLink(exptAnnotations.getContainer()));
        text.append(NL).append("* **[[Copy Link](").append(journalExperiment.getShortCopyUrl().renderShortURL()).append(")]**");
    }

    private static void appendUserDetails(User user, Container container, String userType, StringBuilder text)
    {
        if(user != null)
        {
            text.append(NL);
            text.append(NL).append(userType).append(": ").append(getUserDetailsLink(container, user));
        }
        else
        {
            text.append(NL);
            text.append(NL).append(italics("LabKey user for "  + userType + " was not found in the submitted experiment details."));
        }
    }

    private static void appendActionSubmitterDetails(ExperimentAnnotations exptAnnotations, User user, StringBuilder text)
    {
        text.append(NL);
        text.append(NL).append("Action Triggered By: ").append(getUserDetailsLink(exptAnnotations.getContainer(), user));
    }

    private static String getUserName(User submitter)
    {
        return !StringUtils.isBlank(submitter.getFullName()) ? submitter.getFullName() : submitter.getFriendlyName();
    }

    private static String getUserDetailsLink(Container container, User user)
    {
        ActionURL url = PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(container, user.getUserId(), null);
        StringBuilder link = new StringBuilder();
        link.append("[**").append(getUserName(user)).append("**]");
        link.append("(").append(AppProps.getInstance().getBaseServerUrl() + url.getEncodedLocalURIString()).append(")");

        return link.toString();
    }

    private static String getContainerLink(Container container)
    {
        ActionURL url = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(container);
        StringBuilder link = new StringBuilder("[").append(container.getName()).append("]");
        link.append("(").append(AppProps.getInstance().getBaseServerUrl() + url.getEncodedLocalURIString()).append(")");

        return link.toString();
    }

    private static User getJournalAdminUser(Journal journal)
    {
        Group group = SecurityManager.getGroup(journal.getLabkeyGroupId());
        if(group == null)
        {
            throw new IllegalStateException("Security group not found " + journal.getLabkeyGroupId());
        }

        Set<User> grpMembers = SecurityManager.getAllGroupMembers(group, MemberType.ACTIVE_USERS);
        if(grpMembers == null || grpMembers.size() == 0)
        {
            throw new IllegalStateException("Security group " + group.getName() + " does not have any active members.");
        }
        return grpMembers.iterator().next();
    }

    private static String bold(String text)
    {
        return "**" + text + "**";
    }

    private static String italics(String text)
    {
        return "_" + text + "_";
    }
}
