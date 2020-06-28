package org.labkey.panoramapublic;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.announcements.api.Announcement;
import org.labkey.api.announcements.api.AnnouncementService;
import org.labkey.api.data.Container;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserUrls;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalExperiment;
import org.labkey.panoramapublic.query.JournalManager;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Set;

/*
Posts announcements to a message board, assuming the default MARKDOWN syntax (WikiRendererType.MARKDOWN). This will have to be
revisited if the default is changed or if it can be set in the Site Admin console, for example.
 */
public class PanoramaPublicNotification
{
    private static final String NL = "\n";
    private static final String NL2 = "\n\n";

    private enum ACTION {NEW, UPDATED, DELETED, COPIED, RESUBMITTED, RECOPIED}

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

    public static void notifyResubmitted(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, ExperimentAnnotations currentJournalExpt, User user)
    {
        StringBuilder messageBody = new StringBuilder();
        appendFullMessageBody(expAnnotations, journal, je, ACTION.RESUBMITTED, user, messageBody);
        if(currentJournalExpt != null)
        {
            messageBody.append(NL2).append(bold(String.format("Current %s Folder:", journal.getName()))).append(" ").append(getContainerLink(currentJournalExpt.getContainer()));
        }
        else
        {
            messageBody.append(NL2).append(bold(String.format("Could not find the current %s folder.", journal.getName())));
        }

        postNotification(expAnnotations, journal, je, messageBody.toString());
    }

    public static void notifyCopied(ExperimentAnnotations srcExpAnnotations, ExperimentAnnotations targetExpAnnotations, Journal journal,
                                    JournalExperiment je, User reviewer, String reviewerPassword, User user, boolean isRecopy)
    {
        StringBuilder messageBody = new StringBuilder();
        appendRequestName(srcExpAnnotations, journal, isRecopy ? ACTION.RECOPIED : ACTION.COPIED, messageBody);
        appendSubmissionDetails(srcExpAnnotations, je, messageBody);
        messageBody.append(NL);
        messageBody.append(NL).append(String.format("Experiment has been %scopied to ", isRecopy ? "re": "")).append(escape(journal.getName()));
        messageBody.append(NL).append("Folder: ").append(getContainerLink(targetExpAnnotations.getContainer()));
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
            if(isRecopy && je.isKeepPrivate())
            {
                messageBody.append(NL2).append(bolditalics("Reviewer account is the same as the previous copy of this data."));
            }
            else
            {
                messageBody.append(NL2).append(bolditalics("Data has been made public"));
            }
        }

        if(!StringUtils.isBlank(targetExpAnnotations.getPxid()))
        {
            messageBody.append(NL2).append(bold("ProteomeXchange ID:")).append(" ").append(targetExpAnnotations.getPxid());
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
        User journalAdmin = JournalManager.getJournalAdminUser(journal);
        if(journalAdmin == null)
        {
            throw new NotFoundException(String.format("Could not find an admin user for %s.", journal.getName()));
        }

        if(je.getAnnouncementId() != null)
        {
            svc.insertAnnouncement(supportContainer, journalAdmin, messageTitle, messageBody, true, je.getAnnouncementId());
        }
        else
        {
            Announcement announcement = svc.insertAnnouncement(supportContainer, journalAdmin, messageTitle, messageBody, true);
            je.setAnnouncementId(announcement.getRowId());
            JournalManager.updateJournalExperiment(je, journalAdmin);
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
            text.append(NL).append("Lab Head: ").append(escape(journalExperiment.getLabHeadName()));
            text.append(NL).append("Lab Head Email: ").append(escape(journalExperiment.getLabHeadEmail()));
            text.append(NL).append("Lab Head Affiliation: ").append(escape(journalExperiment.getLabHeadAffiliation()));
        }

        if(expAnnotations.getLabHeadUser() == null && !journalExperiment.hasLabHeadDetails() && journalExperiment.isPxidRequested())
        {
            text.append(NL2).append(bolditalics("Lab Head details were not provided. Submitter's details will be used in the Lab Head field for announcing data to ProteomeXchange."));
        }
        appendActionSubmitterDetails(expAnnotations, user, text);
    }

    private static void appendRequestName(ExperimentAnnotations expAnnotations, Journal journal, ACTION action, @NotNull StringBuilder text)
    {
        text.append(bold(String.format("%s: Experiment ID %d submitted to %s", action, expAnnotations.getId(),
                escape(journal.getName()))));
    }

    private static void appendSubmissionDetails(ExperimentAnnotations exptAnnotations, JournalExperiment journalExperiment, @NotNull StringBuilder text)
    {
        text.append(NL);
        text.append(NL).append("* Experiment ID: ").append(exptAnnotations.getId());
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
            text.append(NL2).append(userType).append(": ").append(getUserDetailsLink(user, container));
        }
        else
        {
            text.append(NL2).append(italics("LabKey user for "  + userType + " was not found in the submitted experiment details."));
        }
    }

    private static void appendActionSubmitterDetails(ExperimentAnnotations exptAnnotations, User user, StringBuilder text)
    {
        text.append(NL2).append("Action Triggered By: ").append(getUserDetailsLink(user, exptAnnotations.getContainer()));
    }

    public static String getUserName(User user)
    {
        return !StringUtils.isBlank(user.getFullName()) ? user.getFullName() : user.getFriendlyName();
    }

    private static String getUserDetailsLink(User user, Container container)
    {
        ActionURL url = PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(container, user.getUserId(), null);
        StringBuilder link = new StringBuilder();
        link.append("[").append(bold(escape(getUserName(user)))).append("]");
        link.append("(").append(AppProps.getInstance().getBaseServerUrl() + url.getEncodedLocalURIString()).append(")");

        return link.toString();
    }

    private static String getContainerLink(Container container)
    {
        ActionURL url = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(container);
        StringBuilder link = new StringBuilder("[").append(escape(container.getName())).append("]");
        link.append("(").append(AppProps.getInstance().getBaseServerUrl()).append(url.getEncodedLocalURIString()).append(")");

        return link.toString();
    }

    public static String bolditalics(String text)
    {
        return "***" + text + "***";
    }

    public static String bold(String text)
    {
        return "**" + text + "**";
    }

    private static String italics(String text)
    {
        return "*" + text + "*";
    }

    private static String preformatted(String text)
    {
        return "`" + text + "`";
    }

    private static String escape(String text)
    {
        // https://www.markdownguide.org/basic-syntax/#characters-you-can-escape
        // Escape Markdown special characters. Some character combinations can result in
        // unintended Markdown styling, e.g. "+_Italics_+" will results in "Italics" to be italicized.
        // This can be seen with the tricky characters used for project names in labkey tests.
        return text.replaceAll("([`*_{}\\[\\]()#+.!|-])", "\\\\$1");
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

    public static String getExperimentCopiedEmailBody(ExperimentAnnotations sourceExperiment,
                                   ExperimentAnnotations targetExperiment,
                                   JournalExperiment jExperiment,
                                   Journal journal,
                                   User reviewer,
                                   String reviewerPassword,
                                   String toUserName,
                                   String fromUserName)
    {
        return getExperimentCopiedEmailBody(sourceExperiment, targetExperiment, jExperiment, journal, reviewer, reviewerPassword, toUserName, fromUserName, false);
    }

    public static String getExperimentReCopiedEmailBody(ExperimentAnnotations sourceExperiment,
                                                      ExperimentAnnotations targetExperiment,
                                                      JournalExperiment jExperiment,
                                                      Journal journal,
                                                      String toUserName,
                                                      String fromUserName)
    {
        return getExperimentCopiedEmailBody(sourceExperiment, targetExperiment, jExperiment, journal, null, null, toUserName, fromUserName, true);
    }

    public static String getExperimentCopiedEmailBody(ExperimentAnnotations sourceExperiment,
                                                      ExperimentAnnotations targetExperiment,
                                                      JournalExperiment jExperiment,
                                                      Journal journal,
                                                      User reviewer,
                                                      String reviewerPassword,
                                                      String toUserName,
                                                      String fromUserName,
                                                      boolean recopy)
    {
        String journalName = journal.getName();

        StringBuilder emailMsg = new StringBuilder();
        emailMsg.append("Dear ").append(toUserName).append(",")
                .append(NL2);
        if(!recopy)
        {
            emailMsg.append("Thank you for your request to submit data to ").append(journalName).append(". ");
        }
        emailMsg.append(String.format("Your data has been %scopied, and the access URL (", recopy ? "re" : "")).append(targetExperiment.getShortUrl().renderShortURL()).append(")")
                .append(" now links to the copy on ").append(journalName).append(".")
                .append(" Please take a moment to verify that the copy is accurate.")
                .append(" Let us know right away if you notice any discrepancies.");


        if(jExperiment.isKeepPrivate())
        {
            if(!recopy)
            {
                emailMsg.append(NL2)
                        .append("As requested, your data on ").append(journalName).append(" is private.  Here are the reviewer account details:")
                        .append(NL).append("Email: ").append(reviewer.getEmail())
                        .append(NL).append("Password: ").append(reviewerPassword);
            }
            else
            {
                emailMsg.append(NL2).append("As requested, your data on ").append(journalName).append(" is private.  The reviewer account details remain unchanged.");
            }
        }
        else
        {
            emailMsg.append(NL2).append("As requested, your data on ").append(journalName).append(" has been made public.");
        }

        if(targetExperiment.getPxid() != null && !recopy)
        {
            emailMsg.append(NL2)
                    .append("The ProteomeXchange ID reserved for your data is:")
                    .append(NL).append(targetExperiment.getPxid())
                    .append(" (http://proteomecentral.proteomexchange.org/cgi/GetDataset?ID=").append(targetExperiment.getPxid()).append(")");
        }

        emailMsg.append(NL2)
                .append("The access URL (").append(targetExperiment.getShortUrl().renderShortURL()).append(")")
                .append(" is the unique identifier of your data on ").append(journalName).append(".")
                .append(" You can put the access URL")
                .append(StringUtils.isBlank(targetExperiment.getPxid()) ? "" : " and the ProteomeXchange ID ")
                .append(" in your manuscript. ");

        if(jExperiment.isKeepPrivate())
        {
            emailMsg.append(NL2)
                    .append("Please respond to this email when you are ready to make your data public.");
        }

        emailMsg.append(NL2).append("Best regards,");
        if(StringUtils.isBlank(fromUserName))
        {
            emailMsg.append(NL).append("The Panorama Public Team");
        }
        else
        {
            emailMsg.append(NL).append(fromUserName).append(NL).append("(For the Panorama Public Team)");
        }

        // Add submission details
        emailMsg.append(NL2)
                .append("Submission Details:")
                .append("Experiment ID: ").append(sourceExperiment.getId())
                .append(NL).append("Reviewer account requested: ").append(jExperiment.isKeepPrivate() ? "Yes" : "No")
                .append(NL).append("PX ID requested: ").append(jExperiment.isPxidRequested() ? "Yes" : "No")
                .append(NL).append("Short Access URL: ").append(targetExperiment.getShortUrl().renderShortURL())
                .append(NL).append("Message ID: ").append(jExperiment.getAnnouncementId());

        return emailMsg.toString();
    }

    public static void postEmailContents(String subject, String emailBody, Set<String> toAddresses, User sender,
                                         ExperimentAnnotations sourceExperiment, JournalExperiment jExperiment, Journal journal, boolean emailSent)
    {
        postEmailContents(subject, emailBody, toAddresses, sender, sourceExperiment, jExperiment, journal, null, emailSent);
    }

    public static void postEmailContentsWithError(String subject, String emailBody, Set<String> toAddresses, User sender,
                                                  ExperimentAnnotations sourceExperiment, JournalExperiment jExperiment, Journal journal, String errorMessage)
    {
        postEmailContents(subject, emailBody, toAddresses, sender, sourceExperiment, jExperiment, journal, errorMessage, false);
    }

    private static void postEmailContents(String subject, String emailBody, Set<String> toAddresses, User sender,
                                         ExperimentAnnotations sourceExperiment, JournalExperiment jExperiment, Journal journal, String errorMessage,
                                          boolean emailSent)
    {
        StringBuilder messageBody = new StringBuilder();
        if(!StringUtils.isBlank(errorMessage))
        {
            messageBody.append(PanoramaPublicNotification.bold("There was an error sending email to the submitter."));
            messageBody.append(NL).append("The error message was: ").append(errorMessage);
        }
        else
        {
            messageBody.append(PanoramaPublicNotification.bold(String.format("Email %s sent to submitter.", emailSent ? "" : "was not")));
        }
        messageBody.append(NL2).append("Email Contents:")
                .append(NL2).append("To: ").append(escape(StringUtils.join(toAddresses, ", ")))
                .append(NL).append("From: ").append(escape(sender.getEmail()))
                .append(NL2).append("Subject: ").append(escape(subject))
                .append(NL2).append(escape(emailBody));

        postNotification(sourceExperiment, journal, jExperiment, messageBody.toString());
    }
    public static class TestCase extends Assert
    {
        @Test
        public void testMarkdownEscape()
        {
            Assert.assertEquals("\\+\\_Test\\_\\+", escape("+_Test_+"));
            Assert.assertEquals("PanoramaPublicTest Project ☃~\\!@$&\\(\\)\\_\\+\\{\\}\\-=\\[\\],\\.\\#äöüÅ",
                    escape("PanoramaPublicTest Project ☃~!@$&()_+{}-=[],.#äöüÅ"));
        }
    }
}
