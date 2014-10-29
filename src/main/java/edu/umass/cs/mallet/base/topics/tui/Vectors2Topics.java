/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package edu.umass.cs.mallet.base.topics.tui;

import edu.umass.cs.mallet.base.util.CommandOption;
import edu.umass.cs.mallet.base.util.Random;
import edu.umass.cs.mallet.base.types.InstanceList;
import edu.umass.cs.mallet.base.topics.TopicalNGrams;
import edu.umass.cs.mallet.base.topics.LDA;

import java.io.File;
import java.io.ObjectOutputStream;
import java.io.FileOutputStream;

// TODO: Does this really belong in classify.tui?  Perhaps we should make a topics.tui?

/** Perform topic analysis in the style of LDA and its variants.
 *  @author <a href="mailto:mccallum@cs.umass.edu">Andrew McCallum</a>
 */

public class Vectors2Topics {

	static CommandOption.String inputFile = new CommandOption.String
	(Vectors2Topics.class, "input", "FILENAME", true, "text.vectors",
	 "The filename from which to read the list of training instances.  Use - for stdin.  " +
	 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);

	static CommandOption.String topicFile = new CommandOption.String
	(Vectors2Topics.class, "output-model", "FILENAME", true, null,
	 "The filename in which to write the binary topic model at the end of the iterations.  " +
	 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String stateFile = new CommandOption.String
	(Vectors2Topics.class, "output-state", "FILENAME", true, null,
	 "The filename in which to write the Gibbs sampling state after at the end of the iterations.  " +
	 "By default this is null, indicating that no file will be written.", null);

  static CommandOption.String docTopicsFile = new CommandOption.String
  (Vectors2Topics.class, "output-doc-topics", "FILENAME", true, null,
   "The filename in which to write the topic proportions per document, at the end of the iterations.  " +
   "By default this is null, indicating that no file will be written.", null);

  static CommandOption.Integer numTopics = new CommandOption.Integer
	(Vectors2Topics.class, "num-topics", "INTEGER", true, 10,
	 "The number of topics to fit.", null);

	static CommandOption.Integer numIterations = new CommandOption.Integer
	(Vectors2Topics.class, "num-iterations", "INTEGER", true, 1000,
	 "The number of iterations of Gibbs sampling.", null);

	static CommandOption.Integer randomSeed = new CommandOption.Integer
	(Vectors2Topics.class, "random-seed", "INTEGER", true, 0,
	 "The random seed for the Gibbs sampler.  Default is 0, which will use the clock.", null);

	static CommandOption.Integer topWords = new CommandOption.Integer
	(Vectors2Topics.class, "num-top-words", "INTEGER", true, 20,
	 "The number of most probable words to print for each topic after model estimation.", null);

	static CommandOption.Integer showTopicsInterval = new CommandOption.Integer
	(Vectors2Topics.class, "show-topics-interval", "INTEGER", true, 50,
	 "The number of iterations between printing a brief summary of the topics so far.", null);

  static CommandOption.Integer outputModelInterval = new CommandOption.Integer
  (Vectors2Topics.class, "output-model-interval", "INTEGER", true, 0,
   "The number of iterations between writing the model (and its Gibbs sampling state) to a binary file.  " +
   "You must also set the --output-model to use this option, whose argument will be the prefix of the filenames.", null);

  static CommandOption.Boolean useNgrams = new CommandOption.Boolean
	(Vectors2Topics.class, "use-ngrams", "true|false", false, false,
	 "Rather than using LDA, use Topical-N-Grams, which models phrases.", null);

	public static void main (String[] args) throws java.io.IOException
	{
		// Process the command-line options
		CommandOption.setSummary (Vectors2Topics.class,
		"A tool for estimating, saving and printing diagnostics for topic models, such as LDA.");
		CommandOption.process (Vectors2Topics.class, args);

		InstanceList ilist = InstanceList.load (new File(inputFile.value));
		System.out.println ("Data loaded.");
		Object topicModel = null;
		if (useNgrams.value) {
			TopicalNGrams tng = new TopicalNGrams(numTopics.value);
			tng.estimate (ilist, numIterations.value, showTopicsInterval.value,
                    outputModelInterval.value, topicFile.value, 
                    randomSeed.value == 0 ? new Random() : new Random(randomSeed.value));
			tng.printTopWords(topWords.value, true);
			if (stateFile.value != null)
				tng.printState (new File(stateFile.value));
      if (docTopicsFile.value != null)
        tng.printDocumentTopics (new File(docTopicsFile.value));
    } else {
			LDA lda = new LDA (numTopics.value);
			lda.estimate (ilist, numIterations.value, showTopicsInterval.value,
                    outputModelInterval.value, topicFile.value,
                    randomSeed.value == 0 ? new Random() : new Random(randomSeed.value));
			lda.printTopWords (topWords.value, true);
			if (stateFile.value != null)
				lda.printState (new File(stateFile.value));
      if (docTopicsFile.value != null)
        lda.printDocumentTopics (new File(docTopicsFile.value));
		}
		if (topicFile.value != null) {
			try {
				ObjectOutputStream oos = new ObjectOutputStream (new FileOutputStream (topicFile.value));
				oos.writeObject (topicModel);
				oos.close();
			} catch (Exception e) {
				e.printStackTrace();
				throw new IllegalArgumentException ("Couldn't write topic model to filename "+topicFile.value);
			}
		}
	}

}
