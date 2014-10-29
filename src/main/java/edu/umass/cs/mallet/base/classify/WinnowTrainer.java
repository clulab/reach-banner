/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */



/** 
   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
*/

package edu.umass.cs.mallet.base.classify;

import edu.umass.cs.mallet.base.classify.Classifier;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.InstanceList;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.Alphabet;
import edu.umass.cs.mallet.base.types.FeatureVector;
import edu.umass.cs.mallet.base.types.Labeling;
import edu.umass.cs.mallet.base.types.LabelVector;
import edu.umass.cs.mallet.base.types.FeatureSelection;
import edu.umass.cs.mallet.base.classify.Winnow;
import edu.umass.cs.mallet.base.pipe.Pipe;

/**
 * An implementation of the training methods of a 
 * Winnow2 on-line classifier. Given an instance xi,
 * the algorithm computes Sum(xi*wi), where wi is 
 * the weight for that feature in the given class. 
 * If the Sum is greater than some threshold 
 * {@link #theta theta}, then the classifier guess
 * true for that class. 
 * Only when the classifier makes a mistake are the 
 * weights updated in one of two steps:
 * Promote: guessed 0 and answer was 1. Multiply
 * all weights of present features by {@link #alpha alpha}.
 * Demote: guessed 1 and answer was 0. Divide
 * all weights of present features by {@link #beta beta}.
 *
 * Limitations: Winnow2 only considers binary feature
 * vectors (i.e. whether or not the feature is present,
 * not its value).
 */
public class WinnowTrainer extends ClassifierTrainer
{
	static final double DEFAULT_ALPHA = 2.0; 
	static final double DEFAULT_BETA = 2.0;   
	static final double DEFAULT_NFACTOR = .5;
	
	/**
	 *constant to multiply to "correct" weights in promotion step
	 */
	double alpha;
	/**
	 *constant to divide "incorrect" weights by in demotion step
	 */
	double beta;
	/**
	 *threshold for sum of wi*xi in formulating guess 
	 */
	double theta;
	/** 
	 *factor of n to set theta to. e.g. if n=1/2, theta = n/2.
	 */
	double nfactor;
	/**
	 *array of weights, one for each feature, initialized to 1
	 */
	double [][] weights;
	
	/**
	 * Default constructor. Sets all features to defaults.
	 */
	public WinnowTrainer(){
		this(DEFAULT_ALPHA, DEFAULT_BETA, DEFAULT_NFACTOR);
	}
	
	/**
	 * Sets alpha and beta and default value for theta
	 * @param a alpha value
	 * @param b beta value
	 */
	public WinnowTrainer(double a, double b){
		this(a, b, DEFAULT_NFACTOR);
	}
	
	/**
	 * Sets alpha, beta, and nfactor
	 * @param a alpha value
	 * @param b beta value
	 * @param nfact nfactor value
	 */
	public WinnowTrainer(double a, double b, double nfact){
		this.alpha = a;
		this.beta = b;
		this.nfactor = nfact;
	}
	
	/**
	 * Trains winnow on the instance list, updating 
	 * {@link #weights weights} according to errors
	 * @param ilist Instance list to be trained on
	 * @return Classifier object containing learned weights
	 */
	public Classifier train (InstanceList trainingList,
													 InstanceList validationList,
													 InstanceList testSet,
													 ClassifierEvaluating evaluator,
													 Classifier initialClassifier)
	{
		FeatureSelection selectedFeatures = trainingList.getFeatureSelection();
		if (selectedFeatures != null)
			// xxx Attend to FeatureSelection!!!
			throw new UnsupportedOperationException ("FeatureSelection not yet implemented.");
		// if "train" is run more than once, 
		// we will be reinitializing the weights
		// TODO: provide method to save weights
		trainingList.getDataAlphabet().stopGrowth();
		trainingList.getTargetAlphabet().stopGrowth();
		Pipe dataPipe = trainingList.getPipe ();
		Alphabet dict = (Alphabet) trainingList.getDataAlphabet ();
		int numLabels = trainingList.getTargetAlphabet().size();
		int numFeats = dict.size(); 
		this.theta =  numFeats * this.nfactor;
		this.weights = new double [numLabels][numFeats];
		// init weights to 1
		for(int i=0; i<numLabels; i++)
			for(int j=0; j<numFeats; j++)
				this.weights[i][j] = 1.0;
		//System.out.println("Init weights to 1.  Theta= "+theta);
		// loop through all instances
		for (int ii = 0; ii < trainingList.size(); ii++){
			Instance inst = (Instance) trainingList.get(ii);
			Labeling labeling = inst.getLabeling ();
			FeatureVector fv = (FeatureVector) inst.getData (dataPipe);
			double[] results = new double [numLabels]; 
			int fvisize = fv.numLocations();
			int correctIndex = labeling.getBestIndex();
			
			for(int rpos=0; rpos < numLabels; rpos++)
		    results[rpos]=0;
			// sum up xi*wi for each class
			for(int fvi=0; fvi < fvisize; fvi++){
				int fi = fv.indexAtLocation(fvi);
				//System.out.println("feature index "+fi);
				for(int lpos=0; lpos < numLabels; lpos++)
			    results[lpos] += this.weights[lpos][fi];
			}
			//System.out.println("In instance " + ii);
			// make guess for each label using threshold
			// update weights according to alpha and beta 
			// upon incorrect guess
			for(int ri=0; ri < numLabels; ri++){
				if(results[ri] > this.theta){ // guess 1
					if(correctIndex != ri) // correct is 0
				    demote(ri, fv);
				}
				else{ // guess 0
					if(correctIndex == ri) // correct is 1
						promote(ri, fv);   
				}
			}
//			System.out.println("Results guessed:")
//		for(int x=0; x<numLabels; x++)
//		    System.out.println(results[x]);
//			System.out.println("Correct label: "+correctIndex );
//			System.out.println("Weights are");
//			for(int h=0; h<numLabels; h++){
//				for(int g=0; g<numFeats; g++)
//			    System.out.println(weights[h][g]);
//				System.out.println("");
//			}
		}
		return new Winnow (dataPipe, weights, theta, numLabels, numFeats);
	}
  /**
   * Promotes (by {@link #alpha alpha}) the weights 
   * responsible for the incorrect guess
   * @param lpos index of incorrectly guessed label
   * @param fv feature vector
   */
  private void promote(int lpos, FeatureVector fv){
		int fvisize = fv.numLocations();
	  // learner predicted 0, correct is 1 -> promotion
		for(int fvi=0; fvi < fvisize; fvi++){
			int fi = fv.indexAtLocation(fvi);
			this.weights[lpos][fi] *= this.alpha;
		}		
	}

  /**
   *Demotes (by {@link #beta beta) the weights 
   * responsible for the incorrect guess
   * @param lpos index of incorrectly guessed label
   * @param fv feature vector
   */
  private void demote(int lpos, FeatureVector fv){
		int fvisize = fv.numLocations();
		// learner predicted 1, correct is 0 -> demotion
		for(int fvi=0; fvi < fvisize; fvi++){
			int fi = fv.indexAtLocation(fvi);
			this.weights[lpos][fi] /= this.beta;
		}		
	}
}

