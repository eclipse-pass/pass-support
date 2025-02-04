package org.eclipse.pass.deposit.provider.util;

import java.time.format.DateTimeFormatter;

import org.eclipse.pass.deposit.model.DepositMetadata;
import org.eclipse.pass.deposit.model.DepositMetadata.Person;
import org.eclipse.pass.deposit.model.DepositSubmission;

public class CitationUtil {
    private CitationUtil() {}

    // TODO Could we use citation from CrossRef metadata?
    public static String createCitation(DepositSubmission submission) {
        DepositMetadata submissionMd = submission.getMetadata();
        DepositMetadata.Article articleMd = submissionMd.getArticleMetadata();
        DepositMetadata.Journal journalMd = submissionMd.getJournalMetadata();

        StringBuilder citationBldr = new StringBuilder();

        int authorIndex = 0;
        for (Person p: submissionMd.getPersons()) {
            if (p.getType() == DepositMetadata.PERSON_TYPE.author) {
                // Citation: For author 0, add name.  For authorIndex 1 and 2, add comma then name.
                // For author 3, add comma and "et al".  For later authorIndex, do nothing.
                if (authorIndex == 0) {
                    citationBldr.append(p.getReversedName());
                } else if (authorIndex <= 2) {
                    citationBldr.append(", " + p.getReversedName());
                } else if (authorIndex == 3) {
                    citationBldr.append(", et al");
                }
                authorIndex++;
            }
        }

        // Add period at end of author list in citation

        if (citationBldr.length() > 0) {
            citationBldr.append(".");
        }

        // Attach a <dc:identifier:citation> if not empty
        // publication date - after a single space, in parens, followed by "."
        if (journalMd != null && journalMd.getPublicationDate() != null) {
            citationBldr.append(" (" + journalMd.getPublicationDate().
                    format(DateTimeFormatter.ISO_LOCAL_DATE) + ").");
        }
        // article title - after single space, in double quotes with "." inside
        if (articleMd != null && articleMd.getTitle() != null && !articleMd.getTitle().isEmpty()) {
            citationBldr.append(" \"" + articleMd.getTitle() + ".\"");
        }
        // journal title - after single space, followed by "."
        if (journalMd != null && journalMd.getJournalTitle() != null && !journalMd.getJournalTitle().isEmpty()) {
            citationBldr.append(" " + journalMd.getJournalTitle() + ".");
        }
        // volume - after single space
        if (articleMd != null && articleMd.getVolume() != null && !articleMd.getVolume().isEmpty()) {
            citationBldr.append(" " + articleMd.getVolume());
        }
        // issue - after single space, inside parens, followed by "."
        if (articleMd != null && articleMd.getIssue() != null && !articleMd.getIssue().isEmpty()) {
            citationBldr.append(" (" + articleMd.getIssue() + ").");
        }
        // DOI - after single space, followed by "."
        if (articleMd != null && articleMd.getDoi() != null) {
            citationBldr.append(" " + articleMd.getDoi().toString() + ".");
        }

        return citationBldr.toString();
    }
}
