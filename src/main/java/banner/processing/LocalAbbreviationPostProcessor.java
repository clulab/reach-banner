package banner.processing;

import java.util.Set;

import banner.Sentence;
import banner.processing.ExtractAbbrev.AbbreviationPair;
import banner.tagging.Mention;
import banner.tagging.MentionType;

public class LocalAbbreviationPostProcessor implements PostProcessor
{
    private ExtractAbbrev extractAbbrev;


    public LocalAbbreviationPostProcessor()
    {
        extractAbbrev = new ExtractAbbrev();
    }


    private void processAbbreviation(Mention formFound, String formNotFound)
    {
        Sentence sentence = formFound.getSentence();
        MentionType type = formFound.getType();
        int charIndex = sentence.getText().indexOf(formNotFound);
        int start = sentence.getTokenIndex(charIndex, true);
        int end = sentence.getTokenIndex(charIndex + formNotFound.length(), false);
        if (start == end)
            return;
        Mention newMention = new Mention(sentence, type, start, end);

        boolean overlaps = false;
        for (Mention mention : sentence.getMentions())
            overlaps |= mention.overlaps(newMention);
        if (!overlaps)
        {
            // System.out.println(sentence.getText());
            // System.out.println("\tFound: " + "(" + formFound.getStart() + "-" + formFound.getEnd() + ") " + formFound.getText());
            // System.out.println("\t Not: " + "(" + start + "-" + end + ") " + formNotFound);
            // System.out.println("\t New: " + "(" + newMention.getStart() + "-" + newMention.getEnd() + ") " + newMention.getText());
            sentence.addMention(newMention);
        }
    }


    public void postProcess(Sentence sentence)
    {
        Set<AbbreviationPair> abbreviationPairs = extractAbbrev.extractAbbrPairs(sentence.getText());
        if (abbreviationPairs.size() > 0)
        {
            for (AbbreviationPair abbreviation : abbreviationPairs)
            {
                Mention shortMention = null;
                Mention longMention = null;
                for (Mention mention : sentence.getMentions())
                {
                    if (abbreviation.getShortForm().equals(mention.getText()))
                        shortMention = mention;
                    if (abbreviation.getLongForm().equals(mention.getText()))
                        longMention = mention;
                }
                if (shortMention == null)
                {
                    if (longMention != null)
                        processAbbreviation(longMention, abbreviation.getShortForm());
                }
                else
                {
                    if (longMention == null)
                        processAbbreviation(shortMention, abbreviation.getLongForm());
                }
            }
        }
    }

}
