/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.classify;

import edu.umass.cs.mallet.base.types.*;
import edu.umass.cs.mallet.base.classify.evaluate.*;
import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.pipe.Classification2ConfidencePredictingFeatureVector;
import edu.umass.cs.mallet.base.util.MalletLogger;
import edu.umass.cs.mallet.base.util.PropertyList;
import java.util.ArrayList;
import java.util.logging.*;

public class ConfidencePredictingClassifierTrainer extends ClassifierTrainer implements Boostable
{
	private static Logger logger =
	MalletLogger.getLogger(ConfidencePredictingClassifierTrainer.class.getName());
	
	ClassifierTrainer underlyingClassifierTrainer;
	MaxEntTrainer confidencePredictingClassifierTrainer;
	//DecisionTreeTrainer confidencePredictingClassifierTrainer;
	//NaiveBayesTrainer confidencePredictingClassifierTrainer;
	Pipe confidencePredictingPipe;
	static ConfusionMatrix confusionMatrix = null;
	
	public ConfidencePredictingClassifierTrainer (ClassifierTrainer underlyingClassifierTrainer,
																								Pipe confidencePredictingPipe)
	{
		this.confidencePredictingPipe = confidencePredictingPipe;
		this.confidencePredictingClassifierTrainer = new MaxEntTrainer();
		//this.confidencePredictingClassifierTrainer = new DecisionTreeTrainer();
		//this.confidencePredictingClassifierTrainer = new NaiveBayesTrainer();
		this.underlyingClassifierTrainer = underlyingClassifierTrainer;

	}
 
	public ConfidencePredictingClassifierTrainer (ClassifierTrainer underlyingClassifierTrainer)
	{
		this (underlyingClassifierTrainer, new Classification2ConfidencePredictingFeatureVector());
	}
	
	public Classifier train (InstanceList trainList,
													 InstanceList validationList,
													 InstanceList testSet,
													 ClassifierEvaluating evaluator,
													 Classifier initialClassifier)
	{
		FeatureSelection selectedFeatures = trainList.getFeatureSelection();
		logger.fine ("Training underlying classifier");
		Classifier c = underlyingClassifierTrainer.train (trainList, null, null, null, initialClassifier);
		confusionMatrix = new ConfusionMatrix(new Trial(c, trainList));
		
		Trial t = new Trial (c, validationList);
		double accuracy = t.accuracy();
		InstanceList confidencePredictionTraining = new InstanceList (confidencePredictingPipe);
		logger.fine ("Creating confidence prediction instance list");
		double weight;
		for (int i = 0; i < t.size(); i++) {
			Classification classification = t.getClassification(i);
			confidencePredictionTraining.add (classification, null, classification.getInstance().getName(), classification.getInstance().getSource());			
		}
		
		logger.info("Begin training ConfidencePredictingClassifier . . . ");
		Classifier cpc = confidencePredictingClassifierTrainer.train (confidencePredictionTraining);
		logger.info("Accuracy at predicting correct/incorrect in training = " + cpc.getAccuracy(confidencePredictionTraining));

		// get most informative features per class, then combine to make
		// new feature conjunctions
		PerLabelInfoGain perLabelInfoGain = new PerLabelInfoGain (trainList);
		



/*		AdaBoostTrainer adaTrainer = new AdaBoostTrainer (confidencePredictingClassifierTrainer, 10);
			Classifier ada = adaTrainer.train (confidencePredictionTraining);
			System.out.println ("Accuracy at predicting correct/incorrect in BOOSTING training = " + ada.getAccuracy(confidencePredictionTraining));
*/
		

// print out most informative features
/*		InfoGain ig = new InfoGain (confidencePredictionTraining);
		for (int i = 0; i < ig.numLocations(); i++)
		logger.info ("InfoGain["+ig.getObjectAtRank(i)+"]="+ig.getValueAtRank(i));
*/
		return new ConfidencePredictingClassifier (c, cpc);
//		return new ConfidencePredictingClassifier (c, ada);
	}

}

