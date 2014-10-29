/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package edu.umass.cs.mallet.base.classify.tui;

import edu.umass.cs.mallet.base.types.*;
import edu.umass.cs.mallet.base.classify.*;
import edu.umass.cs.mallet.base.pipe.*;
import edu.umass.cs.mallet.base.pipe.iterator.*;
import edu.umass.cs.mallet.base.util.*;
import java.util.logging.*;
import java.util.Random;
import java.io.*;
/**
	 A command-line tool for manipulating InstanceLists.  For example,
	 reducing the feature space by information gain.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class Vectors2Vectors
{
	private static Logger logger = MalletLogger.getLogger(Vectors2Vectors.class.getName());

	static CommandOption.File inputFile = new CommandOption.File
	(Vectors2Vectors.class, "input", "FILE", true, new File("-"),
	 "Read the instance list from this file; Using - indicates stdin.", null);

	static CommandOption.File trainingFile = new CommandOption.File
	(Vectors2Vectors.class, "training-file", "FILE", true, new File("text.vectors"),
	 "Write the training set instance list to this file; Using - indicates stdout.", null);

	static CommandOption.File testFile = new CommandOption.File
	(Vectors2Vectors.class, "testing-file", "FILE", true, new File("text.vectors"),
	 "Write the test set instance list to this file; Using - indicates stdout.", null);

	static CommandOption.File validationFile = new CommandOption.File
	(Vectors2Vectors.class, "validation-file", "FILE", true, new File("text.vectors"),
	 "Write the validation set instance list to this file; Using - indicates stdout.", null);

	static CommandOption.Double trainingProportionOption = new CommandOption.Double
	(Vectors2Vectors.class, "training-portion", "DECIMAL", true, 1.0,
	 "The fraction of the instances that should be used for training.", null);

	static CommandOption.Double validationProportionOption = new CommandOption.Double
	(Vectors2Vectors.class, "validation-portion", "DECIMAL", true, 0.0,
	 "The fraction of the instances that should be used for validation.", null);

	static CommandOption.Integer randomSeedOption = new CommandOption.Integer
	(Vectors2Vectors.class, "random-seed", "INTEGER", true, 0,
	 "The random seed for randomly selecting a proportion of the instance list for training", null);

	static CommandOption.Integer featureInfogain = new CommandOption.Integer
    (Vectors2Vectors.class, "feature-infogain", "N", false, 0,
	 "Reduce features to the top N by information gain.", null);


	public static void main (String[] args) throws FileNotFoundException, IOException
	{
		// Process the command-line options
		CommandOption.setSummary (Vectors2Vectors.class,
		"A tool for manipulating instance lists of feature vectors.");
		CommandOption.process (Vectors2Vectors.class, args);

		// Print some helpful messages for error cases
		if (args.length == 0) {
			CommandOption.getList(Vectors2Vectors.class).printUsage(false);
			System.exit (-1);
		}
		if (false && !inputFile.wasInvoked()) {
			System.err.println ("You must specify an input instance list, with --input.");
			System.exit (-1);
		}

		Random r = randomSeedOption.wasInvoked() ? new Random (randomSeedOption.value) : new Random ();
		double t = trainingProportionOption.value;
		double v = validationProportionOption.value;
		logger.info ("Training portion = "+t);
		logger.info ("Validation portion = "+v);
		logger.info ("Testing portion = "+(1-v-t));

		// Read the InstanceList
		InstanceList ilist = InstanceList.load (inputFile.value);

		// split things if that's what was requested
		if (trainingProportionOption.wasInvoked() || validationProportionOption.wasInvoked()){
			InstanceList[] ilists = ilist.split (r, new double[] {t, 1-t-v, v});

			// And write them out
			if (ilists[0].size()>0)
				writeInstanceList(ilists[0], trainingFile.value());
			if (ilists[1].size()>0)
				writeInstanceList(ilists[1], testFile.value());
			if (ilists[2].size()>0)
				writeInstanceList(ilists[2], validationFile.value());

		}

		if (featureInfogain.value > 0) {
			throw new UnsupportedOperationException ("Not yet implemented.");
		}
		
	}

	private static void writeInstanceList(InstanceList ilist, File file)
	throws FileNotFoundException, IOException
	{
		ObjectOutputStream oos;
		oos = new ObjectOutputStream(new FileOutputStream(file));
		oos.writeObject(ilist);
		oos.close();

	}
}
