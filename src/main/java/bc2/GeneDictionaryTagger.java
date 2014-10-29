package bc2;

import java.util.ArrayList;
import java.util.List;

import banner.tagging.DictionaryTagger;
import banner.tagging.MentionType;
import banner.tokenization.Tokenizer;

public class GeneDictionaryTagger extends DictionaryTagger
{

    public GeneDictionaryTagger(Tokenizer tokenizer, boolean filterContainedMentions)
    {
        super(tokenizer, filterContainedMentions);
    }


    // // Use this code if you want to normalize digits
    // @Override
    // protected String transform(String str)
    // {
    // return str.replaceAll("\\d", "0");
    // // return str.replaceAll("\\d+", "0");
    // }

    @Override
    public void add(String text, MentionType type)
    {
        List<String> tokens = process(text);
        add(tokens, type);
        if (tokens.size() == 1 && tokens.get(0).matches("[A-Za-z]+[0-9]+"))
        {
            int split = 0;
            String token = tokens.get(0);
            while (Character.isLetter(token.charAt(split)))
                split++;
            add2Part(token.substring(0, split), token.substring(split, token.length()), type);
        }
        if (tokens.size() == 2)
        {
            add2Part(tokens.get(0), tokens.get(1), type);
        }
        if (tokens.size() == 3 && (tokens.get(1).equals("-") || tokens.get(1).equals("/")))
        {
            add2Part(tokens.get(0), tokens.get(2), type);
        }
        if (tokens.size() > 1 && tokens.get(tokens.size() - 1).equals("homolog"))
            add(tokens.subList(0, tokens.size() - 1), type);
    }


    private void add2Part(String part1, String part2, MentionType type)
    {
        List<String> tokens = new ArrayList<String>();
        tokens.add(part1 + part2);
        tokens.add(part2);
        add(tokens, type);
        tokens = new ArrayList<String>();
        tokens.add(part1);
        tokens.add(part2);
        add(tokens, type);
        tokens.add(1, "-");
        add(tokens, type);
        tokens.set(1, "/");
        add(tokens, type);
    }

}
