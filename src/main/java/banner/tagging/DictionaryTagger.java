/* 
 Copyright (c) 2007 Arizona State University, Dept. of Computer Science and Dept. of Biomedical Informatics.
 This file is part of the BANNER Named Entity Recognition System, http://banner.sourceforge.net
 This software is provided under the terms of the Common Public License, version 1.0, as published by http://www.opensource.org.  For further information, see the file 'LICENSE.txt' included with this distribution.
 */

package banner.tagging;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import banner.Sentence;
import banner.tokenization.Token;
import banner.tokenization.Tokenizer;
import banner.util.Trie;

/**
 * This class represents a very simple dictionary-based tagger. All text subsequences which match an entry will be tagged, without regard to the
 * context. No facilities for text processing (such as case-folding) are provided.
 * 
 * @author Bob
 */
public class DictionaryTagger implements Tagger
{

    public static final String delimiter = "-->";

    private Tokenizer tokenizer;
    private boolean filterContainedMentions;
    protected Trie<String, MentionType> entities;


    /**
     * Creates a new {@link DictionaryTagger}
     * 
     * @param tokenizer
     *        The {@link Tokenizer} to use for breaking new entries into tokens
     * @param filterContainedMentions
     *        Whether mentions which are contained in another mention should be added
     */
    public DictionaryTagger(Tokenizer tokenizer, boolean filterContainedMentions)
    {
        this.tokenizer = tokenizer;
        this.filterContainedMentions = filterContainedMentions;
        entities = new Trie<String, MentionType>();
    }


    protected List<String> process(String input)
    {
        Sentence inputSentence = new Sentence(input);
        tokenizer.tokenize(inputSentence);
        List<Token> tokens = inputSentence.getTokens();
        List<String> output = new ArrayList<String>(tokens.size());
        for (int i = 0; i < tokens.size(); i++)
        {
            output.add(transform(tokens.get(i).getText()));
        }
        return output;
    }


    protected String transform(String str)
    {
        return str;
    }


    /**
     * Adds a single entry to the dictionary. The text is processed by the tokenizer and the resulting tokens are stored.
     * 
     * @param text
     *        The text to find
     * @param type
     *        The {@link MentionType} to tag the text with
     */
    public void add(String text, MentionType type)
    {
        add(process(text), type);
    }


    protected void add(List<String> tokens, MentionType type)
    {
        if (tokens.size() == 0)
            throw new IllegalArgumentException("Number of tokens must be greater than zero");
        MentionType previousType = entities.add(tokens, type);
        if (previousType != null && !previousType.equals(type))
        {
            String str = "Text is already associated with a different tag: " + tokens;
            str += " was tag " + previousType.getText() + " trying to add as tag " + type;
            throw new IllegalArgumentException(str);
        }
    }


    /**
     * Loads multiple entries of a single type to the dictionary by reading them from the specified {@link Reader}.
     * 
     * @param reader
     *        The {@link Reader} containing the entries to be added, one entry per line
     * @param type
     *        The {@link MentionType} for all entries to the dictionary
     * @throws IOException
     */
    public void add(Reader reader, MentionType type) throws IOException
    {
        BufferedReader buffered = new BufferedReader(reader);
        String line = buffered.readLine();
        while (line != null)
        {
            line = line.trim();
            if (line.length() > 0)
                add(line, type);
            line = buffered.readLine();
        }
    }


    /**
     * Loads multiple entries to the dictionary by reading them from the specified {@link Reader}.
     * 
     * @param reader
     *        The {@link Reader} containing the entries to be added, one entry per line, in the format <entry>--><type>
     * @throws IOException
     */
    public void add(Reader reader) throws IOException
    {
        BufferedReader buffered = new BufferedReader(reader);
        String line = buffered.readLine();
        while (line != null)
        {
            String[] split = line.split(delimiter);
            if (split.length != 2)
                throw new IllegalArgumentException();
            add(split[0].trim(), MentionType.getType(split[1].trim()));
            line = buffered.readLine();
        }
        // System.out.println("Size is now " + entities.size());
    }


    public void tag(Sentence sentence)
    {
        List<Token> tokens = sentence.getTokens();
        // Lookup mentions
        List<Mention> mentions = new LinkedList<Mention>();
        for (int startIndex = 0; startIndex < tokens.size(); startIndex++)
        {
            Trie<String, MentionType> t = entities;
            for (int currentIndex = startIndex; currentIndex < tokens.size() && t != null; currentIndex++)
            {
                MentionType type = t.getValue();
                if (type != null)
                    mentions.add(new Mention(sentence, type, startIndex, currentIndex));
                Token currentToken = tokens.get(currentIndex);
                t = t.getChild(transform(currentToken.getText()));
            }
        }

        // Add mentions found

        // Iterator<Mention> mentionIterator = mentions.iterator();
        // while (mentionIterator.hasNext())
        // {
        // Mention mention = mentionIterator.next();
        // boolean contained = false;
        // for (Mention mention2 : mentions)
        // contained |= !mention2.equals(mention) && mention2.contains(mention);
        // if (!filterContainedMentions || !contained)
        // sentence.addMention(mention);
        // }

        while (!mentions.isEmpty())
        {
            Mention mention1 = mentions.remove(0);
            if (filterContainedMentions)
            {
                int start = mention1.getStart();
                int end = mention1.getEnd();
                ArrayList<Mention> adjacentMentions = new ArrayList<Mention>();
                Iterator<Mention> mentionIterator = mentions.iterator();
                boolean changed = true;
                while (changed)
                {
                    changed = false;
                    while (mentionIterator.hasNext())
                    {
                        Mention mention2 = mentionIterator.next();
                        boolean adjacent = (end >= mention2.getStart()) && (start <= mention2.getEnd());
                        if (mention1.getType().equals(mention2.getType()) && adjacent)
                        {
                            adjacentMentions.add(mention2);
                            mentionIterator.remove();
                            start = Math.min(start, mention2.getStart());
                            end = Math.max(end, mention2.getEnd());
                            changed = true;
                        }
                    }
                }
                sentence.addMention(new Mention(sentence, mention1.getType(), start, end));
            }
            else
            {
                sentence.addMention(mention1);
            }
        }
        // System.out.println(sentence.getText());
        // for (Mention mention : sentence.getMentions())
        // System.out.println("\t" + mention.getText());
    }


    /**
     * @return The number of entries in this dictionary
     */
    public int size()
    {
        return entities.size();
    }
}
