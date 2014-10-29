/* 
 Copyright (c) 2007 Arizona State University, Dept. of Computer Science and Dept. of Biomedical Informatics.
 This file is part of the BANNER Named Entity Recognition System, http://banner.sourceforge.net
 This software is provided under the terms of the Common Public License, version 1.0, as published by http://www.opensource.org.  For further information, see the file 'LICENSE.txt' included with this distribution.
 */

package bc2;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import banner.BannerProperties;
import banner.Sentence;
import banner.processing.PostProcessor;
import banner.tagging.Mention;
import banner.tagging.Tagger;
import banner.tokenization.Tokenizer;

public class TestDictionary extends Base
{

    /**
     * @param args
     */
    public static void main(String[] args) throws IOException
    {
        long startTime = System.currentTimeMillis();
        BannerProperties properties = BannerProperties.load(args[0]);
        BufferedReader sentenceFile = new BufferedReader(new FileReader(args[1]));
        BufferedReader mentionTestFile = new BufferedReader(new FileReader(args[2]));
        BufferedReader mentionAlternateFile = new BufferedReader(new FileReader(args[3]));
        String directory = args[4];

        properties.log();

        HashMap<String, LinkedList<Base.Tag>> tags = new HashMap<String, LinkedList<Base.Tag>>(getTags(mentionTestFile));
        HashMap<String, LinkedList<Base.Tag>> alternateTags = new HashMap<String, LinkedList<Base.Tag>>(getAlternateTags(mentionAlternateFile));

        String line = sentenceFile.readLine();
        List<Sentence> sentences = new ArrayList<Sentence>();
        Set<Mention> mentionsTest = new HashSet<Mention>();
        Set<Mention> mentionsAlternate = new HashSet<Mention>();
        while (line != null)
        {
            int space = line.indexOf(' ');
            String id = line.substring(0, space).trim();
            String sentenceText = line.substring(space).trim();
            Sentence sentence = getSentence(id, sentenceText, properties.getTokenizer(), tags);
            mentionsTest.addAll(sentence.getMentions());
            mentionsAlternate.addAll(getMentions(sentence, alternateTags));
            sentences.add(sentence);
            line = sentenceFile.readLine();
        }
        sentenceFile.close();

        String outputFilename = directory + "/output.txt";
        String mentionFilename = directory + "/mention.txt";

        PrintWriter outputFile = new PrintWriter(new BufferedWriter(new FileWriter(outputFilename)));
        PrintWriter mentionFile = new PrintWriter(new BufferedWriter(new FileWriter(mentionFilename)));

        Tokenizer tokenizer = properties.getTokenizer();
        Tagger tagger = properties.getPreTagger();
        PostProcessor postProcessor = properties.getPostProcessor();

        System.out.println("Tagging sentences");
        int count = 0;
        Set<Mention> mentionsFound = new HashSet<Mention>();
        try
        {
            for (Sentence sentence : sentences)
            {
                if (count % 1000 == 0)
                    System.out.println(count);
                String sentenceText = sentence.getText();
                Sentence sentence2 = new Sentence(sentence.getTag(), sentenceText);
                tokenizer.tokenize(sentence2);
                tagger.tag(sentence2);
                if (postProcessor != null)
                    postProcessor.postProcess(sentence2);
                outputFile.println(sentence2.getTrainingText(properties.getTagFormat()));
                mentionsFound.addAll(sentence2.getMentions());
                outputMentions(sentence2, mentionFile);
                count++;
            }
        }
        finally
        {
            outputFile.close();
            mentionFile.close();
        }

        System.out.println("Elapsed time: " + (System.currentTimeMillis() - startTime));
        Map<String, Integer> tpByString = new HashMap<String, Integer>();
        Map<String, Integer> fpByString = new HashMap<String, Integer>();
        int tp = 0;
        int fp = 0;
        for (Mention mention : mentionsFound)
        {
            if (mentionsTest.contains(mention) || mentionsAlternate.contains(mention))
            {
                tp++;
                Integer num = tpByString.get(mention.getText());
                if (num == null)
                    num = new Integer(0);
                tpByString.put(mention.getText(), num + 1);
            }
            else
            {
                fp++;
                Integer num = fpByString.get(mention.getText());
                if (num == null)
                    num = new Integer(0);
                fpByString.put(mention.getText(), num + 1);
            }
        }

        System.out.println("tp: " + tp);
        System.out.println("fp: " + fp);

        double[] results = new double[3];
        results[1] = (double)tp / mentionsFound.size(); // precision
        results[2] = (double)tp / mentionsTest.size(); // recall
        results[0] = 2.0 * results[1] * results[2] / (results[1] + results[2]); // f-measure

        System.out.println("precision: " + results[1]);
        System.out.println("   recall: " + results[2]);
        System.out.println("f-measure: " + results[0]);

        // Set<String> stringsFound = new HashSet<String>(tpByString.keySet());
        // stringsFound.addAll(fpByString.keySet());
        // for (String stringFound : stringsFound)
        // {
        // Integer numAsTP = tpByString.get(stringFound);
        // if (numAsTP == null)
        // numAsTP = new Integer(0);
        // Integer numAsFP = fpByString.get(stringFound);
        // if (numAsFP == null)
        // numAsFP = new Integer(0);
        // if (numAsTP.intValue() <= 2 && numAsFP.intValue() >= 5)
        // System.out.println(stringFound + "\t" + numAsTP + "\t" + numAsFP);
        // }

        // Map<String, Integer> fnByString = new HashMap<String, Integer>();
        // for (Mention mention : mentionsAlternate)
        // {
        // if (!mentionsFound.contains(mention))
        // {
        // Integer num = fnByString.get(mention.getText());
        // if (num == null)
        // num = new Integer(0);
        // fnByString.put(mention.getText(), num + 1);
        // }
        // }
        // for (String stringNotFound : fnByString.keySet())
        // {
        // Integer num = fnByString.get(stringNotFound);
        // System.out.println(stringNotFound + "\t" + num);
        // }
    }
}
