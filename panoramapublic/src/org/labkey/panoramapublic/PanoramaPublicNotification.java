package org.labkey.panoramapublic;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.announcements.api.Announcement;
import org.labkey.api.announcements.api.AnnouncementService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.notification.EmailMessage;
import org.labkey.api.notification.EmailService;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.Group;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserUrls;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalExperiment;
import org.labkey.panoramapublic.query.JournalManager;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

public class PanoramaPublicNotification
{
    private static final String NL = "\n";
    private static final String P = "\n\n";

    private enum ACTION {NEW, UPDATED, DELETED, COPIED, RESUBMITTED}

    public static void notifyCreated(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, User user)
    {
        StringBuilder messageBody = getCreateUpdateMessageBody(expAnnotations, journal, je, ACTION.NEW, user);
        postNotification(expAnnotations, journal, je, messageBody.toString());
    }

    public static void notifyUpdated(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, User user)
    {
        StringBuilder messageBody = getCreateUpdateMessageBody(expAnnotations, journal, je, ACTION.UPDATED, user);
        postNotification(expAnnotations, journal, je, messageBody.toString());
    }

    public static void notifyDeleted(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, User user)
    {
        StringBuilder messageBody = new StringBuilder();
        appendRequestName(expAnnotations, journal, ACTION.DELETED, messageBody);
        messageBody.append(NL).append("* ExperimentID: ").append(expAnnotations.getId());
        messageBody.append(NL).append("* User Folder: ").append(getContainerLink(expAnnotations.getContainer()));
        appendActionSubmitterDetails(expAnnotations, user, messageBody);

        postNotification(expAnnotations, journal, je, messageBody.toString());
    }

    public static void notifyResubmitted(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, Container currentJournalFolder, User user)
    {
        StringBuilder messageBody = new StringBuilder();
        appendFullMessageBody(expAnnotations, journal, je, ACTION.RESUBMITTED, user, messageBody);
        messageBody.append(P).append(bold("Current Journal Folder:")).append(" ").append(getContainerLink(currentJournalFolder));

        postNotification(expAnnotations, journal, je, messageBody.toString());
    }

    public static void notifyCopied(ExperimentAnnotations srcExpAnnotations, ExperimentAnnotations targetExpAnnotations, Journal journal,
                                    JournalExperiment je, User reviewer, String reviewerPassword, User user)
    {
        StringBuilder messageBody = new StringBuilder();
        appendRequestName(srcExpAnnotations, journal, ACTION.COPIED, messageBody);
        appendSubmissionDetails(srcExpAnnotations, je, messageBody);
        messageBody.append(NL);
        messageBody.append(NL).append("Experiment has been copied to ").append(journal.getName());
        messageBody.append(NL).append(journal.getName()).append(" Folder: ").append(getContainerLink(targetExpAnnotations.getContainer()));
        messageBody.append(NL).append("Experiment ID: ").append(targetExpAnnotations.getId());

        if(reviewer != null)
        {
            messageBody.append(NL);
            messageBody.append(NL).append(bold("Reviewer Account:"));
            messageBody.append(NL).append("* Email: ").append(reviewer.getEmail());
            messageBody.append(NL).append("* Password: ").append(reviewerPassword);
        }
        else
        {
            messageBody.append(P).append(italics(bold("Data has been made public")));
        }

        if(!StringUtils.isBlank(targetExpAnnotations.getPxid()))
        {
            messageBody.append(P).append(bold("ProteomeXchange ID:")).append(" ").append(targetExpAnnotations.getPxid());
        }

        appendActionSubmitterDetails(targetExpAnnotations, user, messageBody);

        postNotification(srcExpAnnotations, journal, je, messageBody.toString());
    }

    public static void postNotification(ExperimentAnnotations experimentAnnotations, Journal journal, JournalExperiment je, String messageBody)
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
            text.append(P).append(italics(bold("Lab Head details were not provided. Submitter's details will be used in the Lab Head field for announcing data to ProteomeXchange.")));
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
            text.append(P).append(userType).append(": ").append(getUserDetailsLink(container, user));
        }
        else
        {
            text.append(P).append(italics("LabKey user for "  + userType + " was not found in the submitted experiment details."));
        }
    }

    private static void appendActionSubmitterDetails(ExperimentAnnotations exptAnnotations, User user, StringBuilder text)
    {
        text.append(P).append("Action Triggered By: ").append(getUserDetailsLink(exptAnnotations.getContainer(), user));
    }

    public static String getUserName(User user)
    {
        return !StringUtils.isBlank(user.getFullName()) ? user.getFullName() : user.getFriendlyName();
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

    public static User getJournalAdminUser(Journal journal)
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

    public static String bold(String text)
    {
        return "**" + text + "**";
    }

    private static String italics(String text)
    {
        return "_" + text + "_";
    }

    public static void sendEmailNotification(String subject, String emailBody, Container container,
                                                   User fromUser, Set<String> toEmails) throws MessagingException
    {
        MailHelper.ViewMessage m = MailHelper.createMessage();
        m.addFrom(MailHelper.createAddressArray(fromUser.getEmail()));
        m.addRecipients(Message.RecipientType.TO, MailHelper.createAddressArray(StringUtils.join(toEmails, ";")));
        m.setSubject(subject);
        m.setText(emailBody);
        MailHelper.send(m, fromUser, container);
    }

    public static String getExperimentCopiedEmailBody(ExperimentAnnotations targetExperiment,
                                   JournalExperiment jExperiment,
                                   Journal journal,
                                   User reviewer,
                                   String reviewerPassword,
                                   String toUserName,
                                   String fromUserName)
    {
        String journalName = journal.getName();

        StringBuilder emailMsg = new StringBuilder();
        emailMsg.append("Dear ").append(toUserName).append(",")
                .append(P)
                .append("Thank you for your request to submit data to ").append(journalName).append(".")
                .append(" Your data has been copied, and the access URL (").append(targetExperiment.getShortUrl().renderShortURL()).append(")")
                .append(" now links to the copy on ").append(journalName).append(".")
                .append(" Please take a moment to verify that the copy is accurate.")
                .append(" Let us know right away if you notice any discrepancies.");

        if(jExperiment.isKeepPrivate())
        {
            emailMsg.append(P)
                    .append("As requested, your data on ").append(journalName).append(" is private.  Here are the reviewer account details:")
                    .append(NL).append("Email: ").append(reviewer.getEmail())
                    .append(NL).append("Password: ").append(reviewerPassword);
        }
        else
        {
            emailMsg.append(P)
                    .append("As requested, your data on ").append(journalName).append(" has been made public.");
        }

        if(targetExperiment.getPxid() != null)
        {
            emailMsg.append(P)
                    .append("The ProteomeXchange ID reserved for your data is:")
                    .append(NL).append(targetExperiment.getPxid())
                    .append(" (http://proteomecentral.proteomexchange.org/cgi/GetDataset?ID=").append(targetExperiment.getPxid()).append(")");
        }


        emailMsg.append(P)
                .append("The access URL (").append(targetExperiment.getShortUrl().renderShortURL()).append(")")
                .append(" is the unique identifier of your data on ").append(journalName).append(".")
                .append(" You can put the access URL")
                .append(StringUtils.isBlank(targetExperiment.getPxid()) ? "" : " and the ProteomeXchange ID ")
                .append(" in your manuscript. ");

        if(jExperiment.isKeepPrivate())
        {
            emailMsg.append(P)
                    .append("Please respond to this email when you are ready to make your data public.");
        }

        emailMsg.append(P).append("Best regards,");
        if(StringUtils.isBlank(fromUserName))
        {
            emailMsg.append(NL).append("The Panorama Public Team");
        }
        else
        {
            emailMsg.append(NL).append(fromUserName).append(NL).append("(For the Panorama Public Team)");
        }

        return emailMsg.toString();
    }

    public static void postEmailContents(String subject, String emailBody, Set<String> toAddresses, User sender,
                                         ExperimentAnnotations sourceExperiment, JournalExperiment jExperiment, Journal journal)
    {
        postEmailContents(subject, emailBody, toAddresses, sender, sourceExperiment, jExperiment, journal, null);
    }

    public static void postEmailContentsWithError(String subject, String emailBody, Set<String> toAddresses, User sender,
                                                  ExperimentAnnotations sourceExperiment, JournalExperiment jExperiment, Journal journal, String errorMessage)
    {
        postEmailContents(subject, emailBody, toAddresses, sender, sourceExperiment, jExperiment, journal, errorMessage);
    }

    private static void postEmailContents(String subject, String emailBody, Set<String> toAddresses, User sender,
                                         ExperimentAnnotations sourceExperiment, JournalExperiment jExperiment, Journal journal, String errorMessage)
    {
        StringBuilder messageBody = new StringBuilder();
        if(!StringUtils.isBlank(errorMessage))
        {
            messageBody.append(PanoramaPublicNotification.bold("There was an error sending email to the submitter."));
            messageBody.append(NL).append("The error message was: ").append(errorMessage);
        }
        else
        {
            messageBody.append(PanoramaPublicNotification.bold("Email sent to submitter"));
        }
        messageBody.append(P).append("Email Contents:")
                .append(P).append("To: ").append(StringUtils.join(toAddresses, ", "))
                .append(NL).append("From: ").append(sender.getEmail())
                .append(P).append(subject)
                .append(P).append(emailBody);

        postNotification(sourceExperiment, journal, jExperiment, messageBody.toString());
    }
}
