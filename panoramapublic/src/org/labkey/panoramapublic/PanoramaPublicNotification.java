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
import org.labkey.api.data.ContainerManager;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
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
        DELETED ("Deleted"),
        COPIED ("Copied"),
        RESUBMITTED ("Resubmitted"),
        RECOPIED ("Recopied"),
        PUBLISHED ("Published"),
        CATALOG_ENTRY ("Catalog Entry");

        private final String _title;
        ACTION(String title)
        {
            _title = title;
        }

        public String title()
        {
            return _title;
        }
    }

    public static void notifyCreated(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, Submission submission, User user)
    {
        notifyUserAction(expAnnotations, journal, je, submission, null, user, ACTION.NEW,
                String.format("We have received your request to submit data to %s.", journal.getName()));
    }

    public static void notifyUpdated(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, Submission submission, @Nullable ExperimentAnnotations currentJournalCopy, User user)
    {
        notifyUserAction(expAnnotations, journal, je, submission, currentJournalCopy, user, currentJournalCopy != null ? ACTION.RESUBMITTED : ACTION.NEW,
                String.format("We have received the changes made to your pending submission request to %s.", journal.getName()));
    }

    public static void notifyResubmitted(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, Submission submission, ExperimentAnnotations currentJournalCopy, User user)
    {
        notifyUserAction(expAnnotations, journal, je, submission, currentJournalCopy, user, ACTION.RESUBMITTED,
                String.format("We have received your request to resubmit data to %s.", journal.getName()));
    }

    private static void notifyUserAction(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, Submission submission,
                                         @Nullable ExperimentAnnotations currentJournalCopy,
                                         User user, ACTION action, String actionMessage)
    {
        StringBuilder messageBody = new StringBuilder("Dear ").append(getUserName(user)).append(",").append(NL2)
                .append(actionMessage)
                .append(" The request details are included below.")
                .append(" You will receive a confirmation message after your data has been copied.");
        messageBody.append(NL);
        appendSubmissionDetails(expAnnotations, journal, je, submission, currentJournalCopy, messageBody);
        postNotification(expAnnotations, journal, je, messageBody, true, action.title(), StatusOption.Active, user);
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
        messageBody.append(String.format("Your request to submit data to %s has been deleted.", journal.getName())).append(NL);
        messageBody.append(NL).append("* Folder: ").append(getContainerLink(expAnnotations.getContainer()));
        messageBody.append(NL).append("* ExperimentID: ").append(expAnnotations.getId());

        postNotification(expAnnotations, journal, je, messageBody, false, ACTION.DELETED.title(), StatusOption.Closed, user);
    }

    public static void notifyCopied(ExperimentAnnotations srcExpAnnotations, ExperimentAnnotations targetExpAnnotations, Journal journal,
                                    JournalExperiment je, Submission submission, User reviewer, String reviewerPassword, User user, boolean isRecopy)
    {
        // This is the user that clicked the "Submit" button.
        User formSubmitter = UserManager.getUser(submission.getCreatedBy());

        String messageBody = getExperimentCopiedMessageBody(srcExpAnnotations, targetExpAnnotations, je, submission, journal, reviewer, reviewerPassword, formSubmitter, user, isRecopy);
        ACTION action = isRecopy ? ACTION.RECOPIED : ACTION.COPIED;

        postNotification(journal, je, messageBody, user /* User is either a site admin or a Panorama Public admin, and should have permissions to post*/,
                         action.title(), StatusOption.Closed,  getNotifyList(srcExpAnnotations, user));
    }

    public static void notifyDataPublished(ExperimentAnnotations srcExperiment, ExperimentAnnotations journalCopy, Journal journal,
                                           JournalExperiment je, DataCiteException doiError, boolean madePublic, boolean addedPublication, User user)
    {
        StringBuilder messageBody = new StringBuilder();
        messageBody.append("Dear ").append(getUserName(user)).append(",").append(NL2);
        if (madePublic)
        {
            messageBody.append(String.format("Thank you for making your data public%s.", (addedPublication ? " and adding the following publication details" : "")));
        }
        else if (addedPublication)
        {
            messageBody.append("Thank you for adding the following publication details for your data.");
        }

        if (journalCopy.hasPubmedId())
        {
            messageBody.append(NL).append("* PubMed ID: ").append(journalCopy.getPubmedId());
        }
        if (journalCopy.isPublished())
        {
            messageBody.append(NL).append("* Publication Link: ").append(link(journalCopy.getPublicationLink()));
        }
        if (journalCopy.hasCitation())
        {
            messageBody.append(NL).append("* Citation: ").append(escape(journalCopy.getCitation()));
        }

        if (journalCopy.hasPxid())
        {
            messageBody.append(NL2);
            messageBody.append(String.format(" The accession %s (%s)", journalCopy.getPxid(), link(pxdLink(journalCopy.getPxid()))));
            messageBody.append(madePublic ? " will be made public" : " will be updated");
            messageBody.append(String.format(" on ProteomeXchange by a %s administrator.", journal.getName()));
        }
        if (doiError != null)
        {
            messageBody.append(NL2);
            messageBody.append(NL).append("There was an error making the DOI public. The error was: ").append(escape(doiError.getMessage()));
            messageBody.append(NL).append("We will investigate the problem and get back to you.");
        }

        postNotification(srcExperiment, journal, je, messageBody, false, ACTION.PUBLISHED.title(),
                (doiError != null || journalCopy.hasPxid())
                        ? StatusOption.Active
                        : StatusOption.Closed,
                user);
    }


    public static void notifyCatalogEntryAdded(ExperimentAnnotations srcExperiment, Journal journal, JournalExperiment je,  User user)
    {
        StringBuilder messageBody = new StringBuilder();
        messageBody.append("Dear ").append(getUserName(user)).append(",").append(NL2);
        messageBody.append("Thank you for providing an entry for the Panorama Public data catalog.")
                .append(" We will review your entry and add it to the slideshow on ")
                .append(LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getShortName())
                .append(" (").append(AppProps.getInstance().getBaseServerUrl()).append(").");

        postNotification(srcExperiment, journal, je, messageBody, false, ACTION.CATALOG_ENTRY.title(), StatusOption.Active, user);
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
            messageBody.append(NL).append("For Panorama administrators:").append(" ").append(link("Copy Link", je.getShortCopyUrl().renderShortURL()));
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
                status.name(), combinedNotifyUserIds.isEmpty() ? null : new ArrayList<>(combinedNotifyUserIds));
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
            text.append(NL).append("* ").append("Lab Head details provided in the submission form:");
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
        text.append(NL).append("* Permanent URL: ").append(link(journalExperiment.getShortAccessUrl().renderShortURL()));
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
        text.append("Requested by: ").append(getUserDetails(user));
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
        return link(container.getPath(), url.getEncodedLocalURIString());
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

    private static String link(String url)
    {
        return link(url, url);
    }

    private static String link(String text, String url)
    {
        return String.format("[%s](%s)", escape(text), url);
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

        String accessUrlLink = link(targetExperiment.getShortUrl().renderShortURL());

        StringBuilder message = new StringBuilder();
        message.append("Dear ").append(getUserName(toUser)).append(",")
                .append(NL2);
        if(!recopy)
        {
            message.append("Thank you for your request to submit data to ").append(journalName).append(". ");
        }
        message.append(String.format("Your data has been %s, and the permanent URL (%s)", recopy ? "recopied" : "copied", accessUrlLink))
                .append(String.format(" now links to the %scopy on %s.", recopy ? "new " : "", journalName))
                .append(" Please take a moment to verify that the copy is accurate.");

        if(reviewer != null)
        {
            message.append(NL2)
                    .append("As requested, your data on ").append(journalName).append(" is private.  Here are the reviewer account details:")
                    .append(NL).append("Email: ").append(reviewer.getEmail())
                    .append(NL).append("Password: ").append(reviewerPassword);
        }
        else
        {
            if (submission.isKeepPrivate() && recopy)
            {
                message.append(NL2).append("As requested, your data on ").append(journalName).append(" is private.  The reviewer account details remain unchanged.");
            }
            else
            {
                message.append(NL2).append("As requested, your data on ").append(journalName).append(" has been made public.");
            }
        }

        if(targetExperiment.hasPxid())
        {
            message.append(NL2)
                    .append("The ProteomeXchange ID reserved for your data is:")
                    .append(NL).append(targetExperiment.getPxid())
                    .append(" (").append(link(pxdLink(targetExperiment.getPxid()))).append(")");

            if (submission.isIncompletePxSubmission())
            {
                message.append(NL).append("The data will be submitted as \"supported by repository but incomplete data and/or metadata\" when it is made public on ProteomeXchange.");
            }
        }

        message.append(NL2)
                .append("The permanent URL (").append(accessUrlLink).append(")")
                .append(" is the unique identifier of your data on ").append(journalName).append(".")
                .append(" You can put the permanent URL")
                .append(targetExperiment.hasPxid() ? " and the ProteomeXchange ID " : " ")
                .append("in your manuscript. ");

        if (submission.isKeepPrivate())
        {
            message.append(NL2)
                    .append("When you are ready to make the data public you can click the \"Make Public\" button in your data folder or click this link: ")
                    .append(bold(link("Make Data Public", PanoramaPublicController.getMakePublicUrl(targetExperiment.getId(), targetExperiment.getContainer()).getEncodedLocalURIString())));
        }

        message.append(NL2).append("Best regards,");
        String fromUserName = getUserName(fromUser);
        if(StringUtils.isBlank(fromUserName))
        {
            message.append(NL).append(String.format("The %s team", journalName));
        }
        else
        {
            message.append(NL).append(fromUserName);
        }

        // Add submission details
        appendSubmissionDetails(sourceExperiment, journal, jExperiment, submission, null, message);
        message.append(NL).append(String.format("* %s to folder: ", recopy ? "Recopied": "Copied")).append(getContainerLink(targetExperiment.getContainer()));
        if (targetExperiment.hasDoi())
        {
            message.append(NL).append("* DOI: ").append(link(targetExperiment.getDoi(), "https://doi.org/" + PageFlowUtil.encode(targetExperiment.getDoi())));
        }

        return message.toString();
    }

    private static String pxdLink(String pxdAccession)
    {
        return "http://proteomecentral.proteomexchange.org/cgi/GetDataset?ID=" + pxdAccession;
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
