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
        notifyUserAction(expAnnotations, journal, je, submission, null, user, ACTION.NEW,
                "We have received your request to submit data to Panorama Public.");
    }

    public static void notifyUpdated(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, Submission submission, User user)
    {
        notifyUserAction(expAnnotations, journal, je, submission, null, user, ACTION.UPDATED,
                "Your submission request to Panorama Public has been updated.");
    }

    public static void notifyResubmitted(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, Submission submission, ExperimentAnnotations currentJournalExpt, User user)
    {
        notifyUserAction(expAnnotations, journal, je, submission, currentJournalExpt, user, ACTION.RESUBMITTED,
                "We have received your request to re-submit data to Panorama Public.");
    }

    private static void notifyUserAction(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, Submission submission,
                                         ExperimentAnnotations currentJournalExpt,
                                         User user, ACTION action, String actionMessage)
    {
        StringBuilder messageBody = new StringBuilder("Dear ").append(getUserName(user)).append(",").append(NL2)
                .append(actionMessage)
                .append(" The request details are included below.")
                .append(" You will receive a confirmation email after your data has been copied.");
        messageBody.append(NL);
        appendSubmissionDetails(expAnnotations, journal, je, submission, currentJournalExpt, messageBody);
        postNotification(expAnnotations, journal, je, messageBody, true, action.displayName(), StatusOption.Active, user);
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

    public static void notifyDeleted(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, User user)
    {
        StringBuilder messageBody = new StringBuilder();
        messageBody.append("Your request to submit data to Panorama Public has been deleted.").append(NL);
        messageBody.append(NL).append("* Folder: ").append(getContainerLink(expAnnotations.getContainer()));
        messageBody.append(NL).append("* ExperimentID: ").append(expAnnotations.getId());

        postNotification(expAnnotations, journal, je, messageBody, false, ACTION.DELETED.displayName(), StatusOption.Closed, user);
    }

    public static void notifyCopied(ExperimentAnnotations srcExpAnnotations, ExperimentAnnotations targetExpAnnotations, Journal journal,
                                    JournalExperiment je, Submission submission, User reviewer, String reviewerPassword, User user, boolean isRecopy)
    {
        // This is the user that clicked the "Submit" button.
        User formSubmitter = UserManager.getUser(submission.getCreatedBy());

        String messageBody = getExperimentCopiedMessageBody(srcExpAnnotations, targetExpAnnotations, je, submission, journal, reviewer, reviewerPassword, formSubmitter, user, isRecopy);
        ACTION action = isRecopy ? ACTION.RECOPIED : ACTION.COPIED;

        postNotification(journal, je, messageBody, user /* User is either a site admin or a Panorama Public admin, and should have permissions to post*/,
                         action.displayName(), StatusOption.Closed,  getNotifyList(srcExpAnnotations, user));
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

        postNotification(srcExperiment, journal, je, messageBody, false, ACTION.PUBLISHED.displayName(),
                StatusOption.Active, // Set the status to "Active" so that an admin can announce the data on ProteomeXchange.
                user);
    }

    private static void postNotification(ExperimentAnnotations experimentAnnotations, Journal journal, JournalExperiment je,
                                         StringBuilder messageBody, boolean addCopyLink,
                                        @NotNull String messageTitlePrefix, @NotNull StatusOption status, User user)
    {
        // The user submitting the request does not have permissions to post in the journal's support container.  So the
        // announcement will be posted by the journal admin user.
        User journalAdmin = JournalManager.getJournalAdminUser(journal);
        if (journalAdmin == null)
        {
            throw new NotFoundException(String.format("Could not find an admin user for %s.", journal.getName()));
        }

        messageBody.append(NL2).append("Best regards,");
        messageBody.append(NL).append(getUserName(journalAdmin));

        messageBody.append(NL2).append("---").append(NL2);
        appendActionSubmitterDetails(user, messageBody);

        if (addCopyLink)
        {
            messageBody.append(NL).append(italics("For Panorama administrators:")).append(" **[Copy Link](").append(je.getShortCopyUrl().renderShortURL()).append(")**");
        }

        postNotification(journal, je, messageBody.toString(), journalAdmin, messageTitlePrefix, status, getNotifyList(experimentAnnotations, user));
    }

    private static void postNotification(Journal journal, JournalExperiment je, String messageBody, User messagePoster,
                                        @NotNull String messageTitlePrefix, @NotNull StatusOption status, @Nullable List<User> notifyUsers)
    {
        AnnouncementService svc = AnnouncementService.get();
        Container supportContainer = journal.getSupportContainer();
        String messageTitle = messageTitlePrefix +" - " + je.getShortAccessUrl().renderShortURL();

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
                                                @Nullable ExperimentAnnotations currentJournalExpt, StringBuilder text)
    {
        appendSubmissionDetails(expAnnotations, je, submission, text);
        appendUserDetails(expAnnotations.getSubmitterUser(), "Submitter", text);
        if (expAnnotations.getLabHeadUser() != null)
        {
            appendUserDetails(expAnnotations.getLabHeadUser(),"Lab Head", text);
        }
        else if (submission.hasLabHeadDetails())
        {
            text.append(NL).append("* ").append("Lab Head details provided in submission form:");
            text.append(NL).append("  * Name: ").append(escape(submission.getLabHeadName()))
                    .append(" (").append(escape(submission.getLabHeadEmail())).append(")");
            text.append(NL).append("  * Affiliation: ").append(escape(submission.getLabHeadAffiliation()));
        }

        if (submission.isPxidRequested() && expAnnotations.getLabHeadUser() == null && !submission.hasLabHeadDetails())
        {
            text.append(NL).append("* ").append(italics("Lab head details were not provided. Submitter's name and affiliation will be used in the Lab Head field for announcing data on ProteomeXchange."));
        }
        if(currentJournalExpt != null)
        {
            text.append(NL).append(String.format("* Current %s folder:", journal.getName())).append(" ").append(getContainerLink(currentJournalExpt.getContainer()));
        }
    }

    private static void appendSubmissionDetails(ExperimentAnnotations exptAnnotations, JournalExperiment journalExperiment, Submission submission, @NotNull StringBuilder text)
    {
        text.append(NL2);
        text.append(italics("Submission details:"));
        text.append(NL).append("* Source folder: ").append(getContainerLink(exptAnnotations.getContainer()));
        text.append(NL).append("* Permanent URL: ").append(journalExperiment.getShortAccessUrl().renderShortURL());
        text.append(NL).append("* Reviewer account requested: ").append(bold(submission.isKeepPrivate() ? "Yes" : "No"));
        text.append(NL).append("* PX ID requested: ").append(bold(submission.isPxidRequested() ? "Yes" : "No"));
        if (submission.isIncompletePxSubmission())
        {
            text.append(" (Incomplete submission)");
        }
        text.append(NL).append("* Source experiment ID: ").append(exptAnnotations.getId());
    }

    private static void appendUserDetails(User user, String userType, StringBuilder text)
    {
        if(user != null)
        {
            text.append(NL).append("* ").append(userType).append(": ").append(getUserDetails(user));
        }
        else
        {
            text.append(NL).append("* ").append(italics("LabKey user for "  + userType + " was not found in the submitted experiment details."));
        }
    }

    private static void appendActionSubmitterDetails(User user, StringBuilder text)
    {
        text.append(italics("Requested by: " + getUserDetails(user)));
    }

    public static String getUserName(User user)
    {
        return !StringUtils.isBlank(user.getFullName()) ? user.getFullName() : user.getFriendlyName();
    }

    private static String getUserDetails(User user)
    {
        return escape(getUserName(user)) + " (" + escape(user.getEmail()) + ")";
    }

    private static String getContainerLink(Container container)
    {
        ActionURL url = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(container);
        StringBuilder link = new StringBuilder("[").append(escape(container.getPath())).append("]");
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
        emailMsg.append(String.format("Your data has been %s, and the access URL (%s)", recopy ? "re-copied" : "copied", targetExperiment.getShortUrl().renderShortURL()))
                .append(String.format(" now links to the %scopy on %s.", recopy ? "new " : "", journalName))
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
                    .append(NL)
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
        appendSubmissionDetails(sourceExperiment, journal, jExperiment, submission, null, emailMsg);
        emailMsg.append(NL).append(String.format("* %s to folder: ", recopy ? "Recopied": "Copied")).append(getContainerLink(targetExperiment.getContainer()));

        return emailMsg.toString();
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
