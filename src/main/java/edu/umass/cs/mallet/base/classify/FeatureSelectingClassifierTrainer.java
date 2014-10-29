/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */






package edu.umass.cs.mallet.base.classify;

import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.InstanceList;
import edu.umass.cs.mallet.base.types.FeatureSelector;
import edu.umass.cs.mallet.base.classify.Classifier;
import edu.umass.cs.mallet.base.util.CommandOption;
import edu.umass.cs.mallet.base.util.BshInterpreter;
import java.io.*;
import java.util.*;
/**
 * Adaptor for adding feature selection to a classifier trainer.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class FeatureSelectingClassifierTrainer extends ClassifierTrainer
{
	ClassifierTrainer underlyingTrainer;
	FeatureSelector featureSelector;

	public FeatureSelectingClassifierTrainer (ClassifierTrainer underlyingTrainer,
																						FeatureSelector featureSelector)
	{
		this.underlyingTrainer = underlyingTrainer;
		this.featureSelector = featureSelector;
	}

	public Classifier train (InstanceList trainingSet,
													 InstanceList validationSet,
													 InstanceList testSet,
													 ClassifierEvaluating evaluator,
													 Classifier initialClassifier)
	{
		featureSelector.selectFeaturesFor (trainingSet, validationSet);
		// xxx What about also selecting features for the validation set?
		return underlyingTrainer.train (trainingSet, validationSet, testSet, evaluator, initialClassifier);
	}

}
