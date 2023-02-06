package org.labkey.panoramapublic;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.announcements.DiscussionService.StatusOption;
import org.labkey.api.announcements.api.Announcement;
import org.labkey.api.announcements.api.AnnouncementService;
import org.labkey.api.data.Container;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserUrls;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.panoramapublic.datacite.DataCiteException;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalExperiment;
import org.labkey.panoramapublic.model.Submission;
import org.labkey.panoramapublic.query.JournalManager;
import org.labkey.panoramapublic.query.SubmissionManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
Posts announcements to a message board, assuming the default MARKDOWN syntax (WikiRendererType.MARKDOWN). This will have to be
revisited if the default is changed or if it can be set in the Site Admin console, for example.
 */
public class PanoramaPublicNotification
{
    private static final String NL = "\n";
    private static final String NL2 = "\n\n";

    private enum ACTION
    {
        NEW ("Submitted"),
        UPDATED ("Submission Updated"),
        DELETED ("Deleted"),
        COPIED ("Copied"),
        RESUBMITTED ("Resubmitted"),
        RECOPIED ("Recopied"),
        PUBLISHED ("Published");

        private final String _displayName;
        ACTION(String displayName)
        {
            _displayName = displayName;
        }

        public String displayName()
        {
            return _displayName;
        }
    }

    public static void notifyCreated(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, Submission submission, User user)
    {
        StringBuilder messageBody = new StringBuilder("We have received your request to submit data to Panorama Public. The request details are included below. " +
                "You will receive a confirmation message after your data has been copied to Panorama Public.");
        messageBody.append(NL);
        appendSubmissionDetails(expAnnotations, journal, je, submission, null, user, messageBody);
        postNotification(expAnnotations, journal, je, messageBody.toString(), ACTION.NEW.displayName(), StatusOption.Active, getNotifyList(expAnnotations, user));
    }

    @NotNull
    private static List<User> getNotifyList(ExperimentAnnotations expAnnotations, User formSubmitter)
    {
        List<User> memberList = new ArrayList<>();
        memberList.add(formSubmitter);
        memberList.add(expAnnotations.getSubmitterUser());
        if (expAnnotations.getLabHeadUser() != null)
        {
            memberList.add(expAnnotations.getLabHeadUser());
        }
        return memberList;
    }

    public static void notifyUpdated(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, Submission submission, User user)
    {
        StringBuilder messageBody = new StringBuilder("Your submission request to Panorama Public has been updated. The request details are included below. " +
                "You will receive a confirmation message after your data has been copied to Panorama Public.");
        messageBody.append(NL);
        appendSubmissionDetails(expAnnotations, journal, je, submission, null, user, messageBody);
        postNotification(expAnnotations, journal, je, messageBody.toString(), ACTION.UPDATED.displayName(), StatusOption.Active, getNotifyList(expAnnotations, user));
    }

    public static void notifyDeleted(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, User user)
    {
        StringBuilder messageBody = new StringBuilder();
        messageBody.append("The submission request has been deleted.").append(NL);
        messageBody.append(NL).append("* Source folder: ").append(getContainerLink(expAnnotations.getContainer()));
        messageBody.append(NL).append("* Source experimentID: ").append(expAnnotations.getId());

        appendActionSubmitterDetails(expAnnotations, user, messageBody);

        postNotification(expAnnotations, journal, je, messageBody.toString(), ACTION.DELETED.displayName(), StatusOption.Closed, null);
    }

    public static void notifyResubmitted(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, Submission submission, ExperimentAnnotations currentJournalExpt, User user)
    {
        StringBuilder messageBody = new StringBuilder("We have received your request to re-submit data to Panorama Public. The request details are included below. " +
                "You will receive a confirmation email after your data has been re-copied.").append(NL);
        appendSubmissionDetails(expAnnotations, journal, je, submission, currentJournalExpt, user, messageBody);
        postNotification(expAnnotations, journal, je, messageBody.toString(), ACTION.RESUBMITTED.displayName(), StatusOption.Active, getNotifyList(expAnnotations, user));
    }

    public static void notifyCopied(ExperimentAnnotations srcExpAnnotations, ExperimentAnnotations targetExpAnnotations, Journal journal,
                                    JournalExperiment je, Submission submission, User reviewer, String reviewerPassword, User user, boolean isRecopy)
    {
        // This is the user that clicked the "Submit" button.
        User formSubmitter = UserManager.getUser(submission.getCreatedBy());

        StringBuilder messageBody = new StringBuilder();
        messageBody.append(getExperimentCopiedMessageBody(srcExpAnnotations, targetExpAnnotations, je, submission, journal, reviewer, reviewerPassword, formSubmitter, user, isRecopy));
        ACTION action = isRecopy ? ACTION.RECOPIED : ACTION.COPIED;

        postNotification(srcExpAnnotations, journal, je, messageBody.toString(), user /* User is either a site admin or a Panorama Public admin, and should have permissions to post*/,
                action.displayName(), StatusOption.Closed, null);
    }

    public static void notifyDataPublished(ExperimentAnnotations srcExperiment, ExperimentAnnotations journalCopy, Journal journal,
                                           JournalExperiment je, DataCiteException doiError, @NotNull String message, User user)
    {
        StringBuilder messageBody = new StringBuilder(message).append(NL);
        if (journalCopy.hasPubmedId())
        {
            messageBody.append(NL).append("* PubMed ID: ").append(journalCopy.getPubmedId());
        }
        if (journalCopy.isPublished())
        {
            StringBuilder link = new StringBuilder("[").append(escape(journalCopy.getPublicationLink())).append("]");
            link.append("(").append(journalCopy.getPublicationLink()).append(")");
            messageBody.append(NL).append("* Publication Link: ").append(link);
        }
        if (journalCopy.hasCitation())
        {
            messageBody.append(NL).append("* Citation: ").append(escape(journalCopy.getCitation()));
        }
        if (doiError != null)
        {
            messageBody.append(NL).append("--------------------------------------------------------------------------------------------");
            messageBody.append(NL).append("There was an error making the DOI findable. The error was: ").append(escape(doiError.getMessage()));
        }
        appendActionSubmitterDetails(srcExperiment, user, messageBody);

        postNotification(srcExperiment, journal, je, messageBody.toString(), ACTION.PUBLISHED.displayName(),
                StatusOption.Active, // Set the status to "Active" so that an admin can announce the data on ProteomeXchange.
                null);
    }

    private static void postNotification(ExperimentAnnotations experimentAnnotations, Journal journal, JournalExperiment je, String messageBody,
                                        @NotNull String messageTitlePrefix, @NotNull StatusOption status, List<User> notifyUserIds)
    {
        // The user submitting the request does not have permissions to post in the journal's support container.  So the
        // announcement will be posted by the journal admin user.
        User journalAdmin = JournalManager.getJournalAdminUser(journal);
        if (journalAdmin == null)
        {
            throw new NotFoundException(String.format("Could not find an admin user for %s.", journal.getName()));
        }

        postNotification(experimentAnnotations, journal, je, messageBody, journalAdmin, messageTitlePrefix, status, notifyUserIds);
    }

    private static void postNotification(ExperimentAnnotations experimentAnnotations, Journal journal, JournalExperiment je, String messageBody, User messagePoster,
                                        @NotNull String messageTitlePrefix, @NotNull StatusOption status, @Nullable List<User> notifyUsers)
    {
        AnnouncementService svc = AnnouncementService.get();
        Container supportContainer = journal.getSupportContainer();
        String messageTitle = messageTitlePrefix +" - " + experimentAnnotations.getContainer().getPath();

        Set<User> combinedNotifyUserIds = new HashSet<>();
        if (notifyUsers != null)
        {
            combinedNotifyUserIds.addAll(notifyUsers);
        }
        if(je.getAnnouncementId() != null)
        {
            Announcement ann = svc.getLatestPost(supportContainer, messagePoster, je.getAnnouncementId());
            if (ann != null)
            {
                List<Integer> savedMemberIds = ann.getMemberListIds();
                for (Integer memberId: savedMemberIds)
                {
                    User memberUser = UserManager.getUser(memberId);
                    if (memberUser != null)
                    {
                        combinedNotifyUserIds.add(memberUser);
                    }
                }
            }
        }

        Announcement announcement = svc.insertAnnouncement(supportContainer, messagePoster, messageTitle, messageBody, true, je.getAnnouncementId(),
                status.name(), combinedNotifyUserIds.size() == 0 ? null : new ArrayList<>(combinedNotifyUserIds));
        if (je.getAnnouncementId() == null)
        {
            je.setAnnouncementId(announcement.getRowId());
            SubmissionManager.updateJournalExperiment(je, messagePoster);
        }
    }

    private static void appendSubmissionDetails(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, Submission submission,
                                                @Nullable ExperimentAnnotations currentJournalExpt,
                                                User user, StringBuilder text)
    {
        appendSubmissionDetails(expAnnotations, je, submission, currentJournalExpt, journal, text);
        text.append(NL2);
        appendUserDetails(expAnnotations.getSubmitterUser(), expAnnotations.getContainer(), "Submitter", text);
        if (expAnnotations.getLabHeadUser() != null)
        {
            appendUserDetails(expAnnotations.getLabHeadUser(), expAnnotations.getContainer(), "Lab Head", text);
        }
        else if (submission.hasLabHeadDetails())
        {
            text.append(NL);
            text.append(NL).append(bold("Lab Head details provided in submission form:"));
            text.append(NL).append("Lab head: ").append(escape(submission.getLabHeadName()));
            text.append(NL).append("Lab head email: ").append(escape(submission.getLabHeadEmail()));
            text.append(NL).append("Lab head affiliation: ").append(escape(submission.getLabHeadAffiliation()));
        }

        if (submission.isPxidRequested() && expAnnotations.getLabHeadUser() == null && !submission.hasLabHeadDetails())
        {
            text.append(NL2).append(bolditalics("Lab head details were not provided. Submitter's name and affiliation will be used in the Lab Head field for announcing data on ProteomeXchange."));
        }
        if (!user.equals(expAnnotations.getSubmitterUser()))
        {
            // User that submitted the form is different from the one selected in the "Submitter" field of the ExperimentAnnotations.
            appendActionSubmitterDetails(expAnnotations, user, text);
        }
    }

    private static void appendSubmissionDetails(ExperimentAnnotations exptAnnotations, JournalExperiment journalExperiment, Submission submission,
                                                @Nullable ExperimentAnnotations currentJournalExpt, @NotNull Journal journal, @NotNull StringBuilder text)
    {
        text.append(NL2);
        text.append(italics("Submission details:"));
        text.append(NL).append("* Permanent URL: ").append(journalExperiment.getShortAccessUrl().renderShortURL());
        text.append(NL).append("* Reviewer account requested: ").append(bold(submission.isKeepPrivate() ? "Yes" : "No"));
        text.append(NL).append("* PX ID requested: ").append(bold(submission.isPxidRequested() ? "Yes" : "No"));
        if (submission.isIncompletePxSubmission())
        {
            text.append(" (Incomplete submission)");
        }

        text.append(NL).append("* Source folder: ").append(getContainerLink(exptAnnotations.getContainer()));
        text.append(NL).append("* Source experiment ID: ").append(exptAnnotations.getId());
        if(currentJournalExpt != null)
        {
            text.append(NL).append(bold(String.format("* Current %s folder:", journal.getName()))).append(" ").append(getContainerLink(currentJournalExpt.getContainer()));
        }
        text.append(NL).append("* **[[Copy Link](").append(journalExperiment.getShortCopyUrl().renderShortURL()).append(")]** (For admins only)");
    }

    private static void appendUserDetails(User user, Container container, String userType, StringBuilder text)
    {
        if(user != null)
        {
            text.append(NL).append(userType).append(": ").append(getUserDetailsLink(user, container));
        }
        else
        {
            text.append(NL).append(italics("LabKey user for "  + userType + " was not found in the submitted experiment details."));
        }
    }

    private static void appendActionSubmitterDetails(ExperimentAnnotations exptAnnotations, User user, StringBuilder text)
    {
        text.append(NL2).append("Requested by: ").append(getUserDetailsLink(user, exptAnnotations.getContainer()));
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
        link.append("(").append(AppProps.getInstance().getBaseServerUrl()).append(url.getEncodedLocalURIString()).append(")");

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

    public static String getExperimentCopiedMessageBody(ExperimentAnnotations sourceExperiment,
                                                        ExperimentAnnotations targetExperiment,
                                                        JournalExperiment jExperiment,
                                                        Submission submission,
                                                        Journal journal,
                                                        User reviewer,
                                                        String reviewerPassword,
                                                        User toUser,
                                                        User fromUser,
                                                        boolean recopy)
    {
        String journalName = journal.getName();

        StringBuilder emailMsg = new StringBuilder();
        emailMsg.append("Dear ").append(getUserName(toUser)).append(",")
                .append(NL2);
        if(!recopy)
        {
            emailMsg.append("Thank you for your request to submit data to ").append(journalName).append(". ");
        }
        emailMsg.append(String.format("Your data has been %scopied, and the access URL (", recopy ? "re" : "")).append(targetExperiment.getShortUrl().renderShortURL()).append(")")
                .append(String.format(" now links to the %scopy on ", recopy ? "new " : "")).append(journalName).append(".")
                .append(" Please take a moment to verify that the copy is accurate.")
                .append(" Let us know right away if you notice any discrepancies.");


        if(reviewer != null)
        {
            emailMsg.append(NL2)
                    .append("As requested, your data on ").append(journalName).append(" is private.  Here are the reviewer account details:")
                    .append(NL).append("Email: ").append(reviewer.getEmail())
                    .append(NL).append("Password: ").append(reviewerPassword);
        }
        else
        {
            if (submission.isKeepPrivate() && recopy)
            {
                emailMsg.append(NL2).append("As requested, your data on ").append(journalName).append(" is private.  The reviewer account details remain unchanged.");
            }
            else
            {
                emailMsg.append(NL2).append("As requested, your data on ").append(journalName).append(" has been made public.");
            }
        }

        if(targetExperiment.getPxid() != null)
        {
            emailMsg.append(NL2)
                    .append("The ProteomeXchange ID reserved for your data is:")
                    .append(NL).append(targetExperiment.getPxid())
                    .append(" (http://proteomecentral.proteomexchange.org/cgi/GetDataset?ID=").append(targetExperiment.getPxid()).append(")");

            if (submission.isIncompletePxSubmission())
            {
                emailMsg.append(NL).append("The data will be submitted as \"supported by repository but incomplete data and/or metadata\" when it is made public on ProteomeXchange.");
            }
        }

        emailMsg.append(NL2)
                .append("The access URL (").append(targetExperiment.getShortUrl().renderShortURL()).append(")")
                .append(" is the unique identifier of your data on ").append(journalName).append(".")
                .append(" You can put the access URL")
                .append(StringUtils.isBlank(targetExperiment.getPxid()) ? "" : " and the ProteomeXchange ID ")
                .append(" in your manuscript. ");

        if (submission.isKeepPrivate())
        {
            emailMsg.append(NL2)
                    .append("Please click the link below, or the ").append(bold("Make Public")).append(" button in the copied folder when you are ready to make your data public.")
                    .append("**[[Make Public](")
                    .append(PanoramaPublicController.getMakePublicUrl(targetExperiment.getId(), targetExperiment.getContainer()).getEncodedLocalURIString())
                    .append(")]**");
        }

        emailMsg.append(NL2).append("Best regards,");
        String fromUserName = getUserName(fromUser);
        if(StringUtils.isBlank(fromUserName))
        {
            emailMsg.append(NL).append("The Panorama Public Team");
        }
        else
        {
            emailMsg.append(NL).append(fromUserName);
        }

        // Add submission details
        appendSubmissionDetails(sourceExperiment, jExperiment, submission, null, journal, emailMsg);
//        emailMsg.append(NL2)
//                .append(italics("Submission details:"))
//                .append("* Permanent URL: ").append(targetExperiment.getShortUrl().renderShortURL())
//                .append("* Source folder: ").append(getContainerLink(sourceExperiment.getContainer()))
//                .append("* Source experiment ID: ").append(sourceExperiment.getId())
//                .append("* Reviewer account requested: ").append(bold(submission.isKeepPrivate() ? "Yes" : "No"))
//                .append("* PX ID requested: ").append(bold(submission.isPxidRequested() ? "Yes" : "No"));
//        if (submission.isIncompletePxSubmission())
//        {
//            emailMsg.append(" (Incomplete submission)");
//        }
        emailMsg.append(NL).append(String.format("* %s to folder: ", recopy ? "Recopied": "Copied")).append(getContainerLink(targetExperiment.getContainer()));

        return emailMsg.toString();
    }

//    public static void postEmailContents(String subject, String emailBody, Set<String> toAddresses, User sender,
//                                         ExperimentAnnotations sourceExperiment, JournalExperiment jExperiment, Journal journal, boolean emailSent)
//    {
//        postEmailContents(subject, emailBody, toAddresses, sender, sourceExperiment, jExperiment, journal, null, emailSent);
//    }
//
//    public static void postEmailContentsWithError(String subject, String emailBody, Set<String> toAddresses, User sender,
//                                                  ExperimentAnnotations sourceExperiment, JournalExperiment jExperiment, Journal journal, String errorMessage)
//    {
//        postEmailContents(subject, emailBody, toAddresses, sender, sourceExperiment, jExperiment, journal, errorMessage, false);
//    }

//    private static void postEmailContents(String subject, String emailBody, Set<String> toAddresses, User sender,
//                                         ExperimentAnnotations sourceExperiment, JournalExperiment jExperiment, Journal journal, String errorMessage,
//                                          boolean emailSent)
//    {
//        StringBuilder messageBody = new StringBuilder();
//        if(!StringUtils.isBlank(errorMessage))
//        {
//            messageBody.append(PanoramaPublicNotification.bold("There was an error sending email to the submitter."));
//            messageBody.append(NL).append("The error message was: ").append(errorMessage);
//        }
//        else
//        {
//            // messageBody.append(PanoramaPublicNotification.bold(String.format("Email %s sent to submitter.", emailSent ? "" : "was not")));
//        }
////        messageBody.append(NL2).append("Email Contents:")
////                .append(NL2).append("To: ").append(escape(StringUtils.join(toAddresses, ", ")))
////                .append(NL).append("From: ").append(escape(sender.getEmail()))
////                .append(NL2).append("Subject: ").append(escape(subject))
////                .append(NL2).append(escape(emailBody));
//        messageBody.append(NL2).append(escape(emailBody));
//
//        postNotification(sourceExperiment, journal, jExperiment, messageBody.toString(), sender /* User is either a site admin or a Panorama Public admin, and should have permissions to post*/);
//    }

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
