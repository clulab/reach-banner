/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package edu.umass.cs.mallet.base.classify;

import edu.umass.cs.mallet.base.types.*;
import edu.umass.cs.mallet.base.classify.*;
import edu.umass.cs.mallet.base.classify.evaluate.*;
import edu.umass.cs.mallet.base.pipe.*;
import edu.umass.cs.mallet.base.pipe.iterator.*;
import edu.umass.cs.mallet.base.util.*;
import java.io.*;
/**
 * Text User Interface for classification (unsupported).
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class TUI
{
	static CommandOption.File instanceListFile = new CommandOption.File
	(TUI.class, "instance-list", "FILE", true, new File("instance-list.mallet"),
	 "Read or write the instance list to this file.", null);

	static CommandOption.Set whatDoing = new CommandOption.Set
	(TUI.class, "mode", "MODENAME", true, new String[] {"index", "test"}, 0,
	 "Set primary option.", null);

	static CommandOption.SpacedStrings indexTextFileDirectories =	new CommandOption.SpacedStrings
	(TUI.class, "index-text-dirs", "DIR...", true, null,
	 "The directories containing text files to be classified, one directory per class", null);

	static CommandOption.SpacedStrings indexCsvLines =	new CommandOption.SpacedStrings
	(TUI.class, "index-csv-lines", "FILENAME", true, new String[] {"-"},
	 "The name of the file containing one line per instance to be classified", null);
	
	public static void main (String[] args)
	{
		if (args.length == 0)
			args = new String[] {
				"/usr/gob/data1/hough/20news-18828/comp.graphics",
//				"/usr/col/tmp1/culotta/data/20news-18828/comp.os.ms-windows.misc",
//				"/usr/col/tmp1/culotta/data/20news-18828/comp.sys.ibm.pc.hardware",
//				"/usr/col/tmp1/culotta/data/20news-18828/comp.sys.mac.hardware",
				"/usr/gob/data1/hough/20news-18828/comp.windows.x",
				};

		File[] directories = new File[args.length];
		for (int i = 0; i < args.length; i++)
			directories[i] = new File (args[i]);
		Pipe instancePipe = new SerialPipes (new Pipe[] {
			new Target2Label (),
			new Input2CharSequence (),
			//new CharSubsequence (CharSubsequence.SKIP_HEADER),
			new CharSequence2TokenSequence (),
			new TokenSequenceLowercase (),
			//new TokenSequenceRemoveStopwords (),
			new TokenSequence2FeatureSequence(),
			//new PrintInputAndTarget (),
			//new FeatureSequence2FeatureVector(),
			new FeatureSequence2AugmentableFeatureVector(),
			//new AugmentableFeatureVectorLogScale(),
			//new PrintInputAndTarget ()
		});
		InstanceList ilist = new InstanceList (instancePipe);
		ilist.add (new FileIterator (directories, FileIterator.STARTING_DIRECTORIES));

		java.util.Random r = new java.util.Random (1);

		int numTrials = 4;

		ClassifierTrainer[] trainers = new ClassifierTrainer[] {
			new NaiveBayesTrainer(),
			new MaxEntTrainer(),
			//new CPMaxEntTrainer(10),
			//new CPMaxEntTrainer(5),
			//new CPMaxEntTrainer(1),
			//new CPMaxEntTrainer(0.5),
			//new CPMaxEntTrainer(0.2),
			//new CPMaxEntTrainer(0.01),
			//new CPMaxEntTrainer(0.001),
			//new FeatureSelectingClassiferTrainer (new MaxEntTrainer(),
			//new PerClassInfoGainFeatureSelector (200)),
			//new DecisionTreeTrainer(4),
			//new RoostingTrainer(),
			//new AdaBoostTrainer(new DecisionTreeTrainer(2),10),
		};
		double trainAccuracy[][] = new double[trainers.length][numTrials];
		double testAccuracy[][] = new double[trainers.length][numTrials];
		double stddev[][] = new double[trainers.length][numTrials];
		double stderr[][] = new double[trainers.length][numTrials];
		String confusionMatrix[][] = new String[trainers.length][numTrials];

		for (int trialIndex = 0; trialIndex < numTrials; trialIndex++) {
			
			Classifier classifiers[] = new Classifier[trainers.length];
			InstanceList[] ilists = ilist.split (r, new double[] {.75, .25});

			//InfoGain ig = new InfoGain (ilists[0]);
			//int igl = Math.min (10, ig.numLocations());
			//for (int i = 0; i < igl; i++)
			//System.out.println ("InfoGain["+ig.getObjectAtRank(i)+"]="+ig.getValueAtRank(i));
			//ig.print();
		
			//FeatureSelection selectedFeatures = new FeatureSelection (ig, 8000);
			//ilists[0].setFeatureSelection (selectedFeatures);
			//OddsRatioFeatureInducer orfi = new OddsRatioFeatureInducer (ilists[0]);
			//orfi.induceFeatures (ilists[0], false, true);

			//System.out.println ("Training with "+ilists[0].size()+" instances");
			long time[] = new long[trainers.length];
			for (int c = 0; c < classifiers.length; c++){
				time[c] = System.currentTimeMillis();
				//System.out.println (">>>>>>>>>>>  Training "+trainers[c].toString());
				classifiers[c] = trainers[c].train (ilists[0]);
				time[c] = System.currentTimeMillis() - time[c];
			}
			for (int c = 0; c < classifiers.length; c++) {
				Trial trainTrial = new Trial (classifiers[c], ilists[0]);
				Trial testTrial = new Trial (classifiers[c], ilists[1]);
				confusionMatrix[c][trialIndex] = new ConfusionMatrix (testTrial).toString();
				trainAccuracy[c][trialIndex] = trainTrial.accuracy();
				testAccuracy[c][trialIndex] = testTrial.accuracy();
			}
		}

		for (int c = 0; c < trainers.length; c++) {
			System.out.println ("\n"+trainers[c].toString());
			System.out.println ("Accuracy mean = "+ MatrixOps.mean (testAccuracy[c])+
													" stddev = "+ MatrixOps.stddev (testAccuracy[c])+
													" stderr = "+ MatrixOps.stderr (testAccuracy[c]));
			for (int trialIndex = 0; trialIndex < numTrials; trialIndex++)
				System.out.println (confusionMatrix[c][trialIndex]);
		}
		
	}
    
}
