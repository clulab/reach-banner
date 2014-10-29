/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
		@author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.fst;

import edu.umass.cs.mallet.base.types.*;
import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.minimize.*;
import edu.umass.cs.mallet.base.util.Maths;
import edu.umass.cs.mallet.base.util.MalletLogger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;
import java.util.regex.*;
import java.util.logging.*;
import java.io.*;
import java.lang.reflect.Constructor;


/* There are several different kinds of numeric values:

   "weights" range from -Inf to Inf.  High weights make a path more
   likely.  These don't appear directly in Transducer.java, but appear
	 as parameters to many subclasses, such as CRFs.  Weights are also
	 often summed, or combined in a dot product with feature vectors.

	 "unnormalized costs" range from -Inf to Inf.  High costs make a
	 path less likely.  Unnormalized costs can be obtained from negated
	 weights or negated sums of weights.  These are often returned by a
	 TransitionIterator's getCost() method.  The LatticeNode.alpha
	 values are unnormalized costs.

	 "normalized costs" range from 0 to Inf.  High costs make a path
	 less likely.  Normalized costs can safely be considered as the
	 -log(probability) of some event.  They can be obtained by
	 substracting a (negative) normalizer from unnormalized costs, for
	 example, subtracting the total cost of a lattice.  Typically
	 initialCosts and finalCosts are examples of normalized costs, but
	 they are also allowed to be unnormalized costs.  The gammas[][],
	 stateGammas[], and transitionXis[][] are all normalized costs, as
	 well as the return value of Lattice.getCost().

	 "probabilities" range from 0 to 1.  High probabilities make a path
	 more likely.  They are obtained from normalized costs by taking the
	 log and negating.  

	 "sums of probabilities" range from 0 to positive numbers.  They are
	 the sum of several probabilities.  These are passed to the
	 incrementCount() methods.
	 
*/


public class CRF3 extends Transducer implements Serializable
{
	private static Logger logger = MalletLogger.getLogger(CRF.class.getName());

	static final double DEFAULT_GAUSSIAN_PRIOR_VARIANCE = 0.5;//Double.POSITIVE_INFINITY;//10.0;
	static final double DEFAULT_HYPERBOLIC_PRIOR_SLOPE = 0.2;
//<<<<<<< CRF3.java
	static final double DEFAULT_HYPERBOLIC_PRIOR_SHARPNESS = 15.0;//10.0;
//=======
  static final String LABEL_SEPARATOR = ",";
//>>>>>>> 1.6
	
	// The length of each weights[i] Vector is the size of the input
	// dictionary plus one; the additional column at the end is for the
	// "default feature".
	Alphabet inputAlphabet;
	Alphabet outputAlphabet;
	ArrayList states = new ArrayList ();
	ArrayList initialStates = new ArrayList ();
	HashMap name2state = new HashMap ();
	SparseVector[] weights, constraints, expectations;
	double[] defaultWeights, defaultConstraints, defaultExpectations;	// parameters for default feature
	BitSet[] weightsPresent;							// Only used in setWeightsDimensionAsIn()
	// FeatureInduction can fill this in
	FeatureSelection globalFeatureSelection;
	// "featureSelections" is on a per- weights[i] basis, and over-rides
	// (permanently disabiling) FeatureInducer's and
	// setWeightsDimensionsAsIn() from using these features on these transitions
	FeatureSelection[] featureSelections;
	Alphabet weightAlphabet = new Alphabet ();
	boolean trainable = false;
	boolean gatheringConstraints = false;
	boolean gatheringWeightsPresent = false;
	//int defaultFeatureIndex;
	boolean usingHyperbolicPrior = false;
	double gaussianPriorVariance = DEFAULT_GAUSSIAN_PRIOR_VARIANCE;
	double hyperbolicPriorSlope = DEFAULT_HYPERBOLIC_PRIOR_SLOPE;
	double hyperbolicPriorSharpness = DEFAULT_HYPERBOLIC_PRIOR_SHARPNESS;
	private boolean cachedCostStale = true;
	private boolean cachedGradientStale = true;
	private boolean someTrainingDone = false;
	ArrayList featureInducers = new ArrayList();


	
	double[] priorInitialCost;
	double[] priorFinalCost;

	// xxx temporary hack
	public boolean printGradient = false;

	public CRF3 (Pipe inputPipe, Pipe outputPipe)
	{
		this.inputPipe = inputPipe;
		this.outputPipe = outputPipe;
		this.inputAlphabet = inputPipe.getDataAlphabet();
		this.outputAlphabet = inputPipe.getTargetAlphabet();
		//this.defaultFeatureIndex = inputAlphabet.size();
		//inputAlphabet.stopGrowth();
	}
	
	public CRF3 (Alphabet inputAlphabet,
							 Alphabet outputAlphabet)
	{
		inputAlphabet.stopGrowth();
		logger.info ("CRF input dictionary size = "+inputAlphabet.size());
		//xxx outputAlphabet.stopGrowth();
		this.inputAlphabet = inputAlphabet;
		this.outputAlphabet = outputAlphabet;
		//this.defaultFeatureIndex = inputAlphabet.size();
	}

	// xxx Remove this method?
	/** Create a new CRF sharing Alphabet and other attributes, but possibly
			having a larger weights array. */
	/*
	private CRF3 (CRF3 initialCRF) {
		System.out.println ("new CRF. Old weight size = "+initialCRF.weights[0].singleSize()+
												" New weight size = "+initialCRF.inputAlphabet.size());
		this.inputAlphabet = initialCRF.inputAlphabet;
		this.outputAlphabet = initialCRF.outputAlphabet;
		this.states = new ArrayList (initialCRF.states.size());
		this.inputPipe = initialCRF.inputPipe;
		this.outputPipe = initialCRF.outputPipe;
		//this.defaultFeatureIndex = initialCRF.defaultFeatureIndex;
		for (int i = 0; i < initialCRF.states.size(); i++) {
			State s = (State) initialCRF.getState (i);
			String[] weightNames = new String[s.weightsIndices.length];
			for (int j = 0; j < weightNames.length; j++)
				weightNames[j] = (String) initialCRF.weightAlphabet.lookupObject(s.weightsIndices[i]);
			addState (s.name, s.initialCost, s.finalCost, s.destinationNames, s.labels, weightNames);
		}
		assert (weights.length > 0);
		for (int i = 0; i < weights.length; i++)
			weights[i].arrayCopyFrom (0, initialCRF.getWeights ((String)weightAlphabet.lookupObject(i)));
	}
	*/

	public Alphabet getInputAlphabet () { return inputAlphabet; }
	public Alphabet getOutputAlphabet () { return outputAlphabet; }
	
	public void setUseHyperbolicPrior (boolean f) { usingHyperbolicPrior = f; }
	public void setHyperbolicPriorSlope (double p) { hyperbolicPriorSlope = p; }
	public void setHyperbolicPriorSharpness (double p) { hyperbolicPriorSharpness = p; }
	public double getUseHyperbolicPriorSlope () { return hyperbolicPriorSlope; }
	public double getUseHyperbolicPriorSharpness () { return hyperbolicPriorSharpness; }
	public void setGaussianPriorVariance (double p) { gaussianPriorVariance = p; }
	public double getGaussianPriorVariance () { return gaussianPriorVariance; }
	//public int getDefaultFeatureIndex () { return defaultFeatureIndex;}
	
	public void addState (String name, double initialCost, double finalCost,
												String[] destinationNames,
												String[] labelNames,
												String[][] weightNames)
	{
		assert (weightNames.length == destinationNames.length);
		assert (labelNames.length == destinationNames.length);
		setTrainable (false);
		if (name2state.get(name) != null)
			throw new IllegalArgumentException ("State with name `"+name+"' already exists.");
		State s = new State (name, states.size(), initialCost, finalCost,
												 destinationNames, labelNames, weightNames, this);
		s.print ();
		states.add (s);
		if (initialCost < INFINITE_COST)
			initialStates.add (s);
		name2state.put (name, s);
	}

	public void addState (String name, double initialCost, double finalCost,
												String[] destinationNames,
												String[] labelNames,
												String[] weightNames)
	{
		String[][] newWeightNames = new String[weightNames.length][1];
		for (int i = 0; i < weightNames.length; i++)
			newWeightNames[i][0] = weightNames[i];
		this.addState (name, initialCost, finalCost, destinationNames, labelNames, newWeightNames);
	}
	
	// Default gives separate parameters to each transition
	public void addState (String name, double initialCost, double finalCost,
												String[] destinationNames,
												String[] labelNames)
	{
		assert (destinationNames.length == labelNames.length);
		String[] weightNames = new String[labelNames.length];
		for (int i = 0; i < labelNames.length; i++)
			weightNames[i] = name + "->" + destinationNames[i] + ":" + labelNames[i];
		this.addState (name, initialCost, finalCost, destinationNames, labelNames, weightNames);
	}
												
	// Add a state with parameters equal zero, and labels on out-going arcs
	// the same name as their destination state names.
	public void addState (String name, String[] destinationNames)
	{
		// == changed by Fuchun Peng to use non-zero prior cost
		int index = outputAlphabet.lookupIndex(name);
		this.addState (name, priorInitialCost[index], priorFinalCost[index], destinationNames, destinationNames);
		
//		this.addState (name, 0, 0, destinationNames, destinationNames);
	}

	// Add a group of states that are fully connected with each other,
	// with parameters equal zero, and labels on their out-going arcs
	// the same name as their destination state names.
	public void addFullyConnectedStates (String[] stateNames)
	{
		for (int i = 0; i < stateNames.length; i++)
			addState (stateNames[i], stateNames);
	}

	public void addFullyConnectedStatesForLabels ()
	{
		String[] labels = new String[outputAlphabet.size()];
		// This is assuming the the entries in the outputAlphabet are Strings!
		for (int i = 0; i < outputAlphabet.size(); i++) {
			logger.info ("CRF: outputAlphabet.lookup class = "+
													outputAlphabet.lookupObject(i).getClass().getName());
			labels[i] = (String) outputAlphabet.lookupObject(i);
		}
		addFullyConnectedStates (labels);
	}

	private boolean[][] labelConnectionsIn (InstanceList trainingSet)
	{
		int numLabels = outputAlphabet.size();
		boolean[][] connections = new boolean[numLabels][numLabels];
		for (int i = 0; i < trainingSet.size(); i++) {
			Instance instance = trainingSet.getInstance(i);
			FeatureSequence output = (FeatureSequence) instance.getTarget();
			for (int j = 1; j < output.size(); j++) {
				int sourceIndex = outputAlphabet.lookupIndex (output.get(j-1));
				int destIndex = outputAlphabet.lookupIndex (output.get(j));
				assert (sourceIndex >= 0 && destIndex >= 0);
				connections[sourceIndex][destIndex] = true;
			}
		}
		return connections;
	}

	//added by Fuchun Peng
	public void priorCost(InstanceList trainingSet)
	{
		int numLabels = outputAlphabet.size();
		priorInitialCost = new double[numLabels];
		priorFinalCost = new double[numLabels];
		int initialNum = 0;
		int finalNum = 0;
		for (int i = 0; i < trainingSet.size(); i++) {
			Instance instance = trainingSet.getInstance(i);
			FeatureSequence output = (FeatureSequence) instance.getTarget();
			int initialIndex = outputAlphabet.lookupIndex (output.get(0));
			int finalIndex= outputAlphabet.lookupIndex (output.get(output.size()-1));
			assert (initialIndex >= 0 && finalIndex >= 0);
			priorInitialCost[initialIndex] ++;
			priorFinalCost[finalIndex] ++;
			initialNum ++;
			finalNum ++;
		}

		for(int i=0; i<numLabels; i++){
			priorInitialCost[i] = (priorInitialCost[i]+1)/(initialNum+numLabels);
			priorInitialCost[i] = -Math.log(priorInitialCost[i]);

			priorFinalCost[i] /= finalNum;
			priorFinalCost[i] = -Math.log(priorFinalCost[i]);
			System.out.println((String)outputAlphabet.lookupObject(i) + " " 
					+ priorInitialCost[i] + " " 
					+ priorFinalCost[i]);
		}
	}

	/** Add states to create a first-order Markov model on labels,
			adding only those transitions the occur in the given
			trainingSet. */
	public void addStatesForLabelsConnectedAsIn (InstanceList trainingSet)
	{
		priorCost(trainingSet);

		int numLabels = outputAlphabet.size();
		boolean[][] connections = labelConnectionsIn (trainingSet);
		for (int i = 0; i < numLabels; i++) {
			int numDestinations = 0;
			for (int j = 0; j < numLabels; j++)
				if (connections[i][j]) numDestinations++;
			String[] destinationNames = new String[numDestinations];
			int destinationIndex = 0;
			for (int j = 0; j < numLabels; j++)
				if (connections[i][j])
					destinationNames[destinationIndex++] = (String)outputAlphabet.lookupObject(j);
			addState ((String)outputAlphabet.lookupObject(i), destinationNames);
		}
	}

	/** Add as many states as there are labels, but don't create separate weights
			for each source-destination pair of states.  Instead have all the incoming
			transitions to a state share the same weights. */
	public void addStatesForHalfLabelsConnectedAsIn (InstanceList trainingSet)
	{
		priorCost(trainingSet);

		int numLabels = outputAlphabet.size();
		boolean[][] connections = labelConnectionsIn (trainingSet);
		for (int i = 0; i < numLabels; i++) {
			int numDestinations = 0;
			for (int j = 0; j < numLabels; j++)
				if (connections[i][j]) numDestinations++;
			String[] destinationNames = new String[numDestinations];
			int destinationIndex = 0;
			for (int j = 0; j < numLabels; j++)
				if (connections[i][j])
					destinationNames[destinationIndex++] = (String)outputAlphabet.lookupObject(j);
//			addState ((String)outputAlphabet.lookupObject(i), 0.0, 0.0,
//								destinationNames, destinationNames, destinationNames);
			
			addState ((String)outputAlphabet.lookupObject(i), priorInitialCost[i], priorFinalCost[i],
								destinationNames, destinationNames, destinationNames);
			

		}
	}

	/** Add as many states as there are labels, but don't create
			separate observational-test-weights for each source-destination
			pair of states---instead have all the incoming transitions to a
			state share the same observational-feature-test weights.
			However, do create separate default feature for each transition,
			(which acts as an HMM-style transition probability). */
	public void addStatesForThreeQuarterLabelsConnectedAsIn (InstanceList trainingSet)
	{
		priorCost(trainingSet);

		int numLabels = outputAlphabet.size();
		boolean[][] connections = labelConnectionsIn (trainingSet);
		for (int i = 0; i < numLabels; i++) {
			int numDestinations = 0;
			for (int j = 0; j < numLabels; j++)
				if (connections[i][j]) numDestinations++;
			String[] destinationNames = new String[numDestinations];
			String[][] weightNames = new String[numDestinations][];
			int destinationIndex = 0;
			for (int j = 0; j < numLabels; j++)
				if (connections[i][j]) {
					String labelName = (String)outputAlphabet.lookupObject(j);
					destinationNames[destinationIndex] = labelName;
					weightNames[destinationIndex] = new String[2];
					// The "half-labels" will include all observational tests
					weightNames[destinationIndex][0] = labelName;
					// The "transition" weights will include only the default feature
					String wn = (String)outputAlphabet.lookupObject(i) + "->" + (String)outputAlphabet.lookupObject(j);
					weightNames[destinationIndex][1] = wn;
					int wi = getWeightsIndex (wn);
					// A new empty FeatureSelection won't allow any features here, so we only
					// get the default feature for transitions
					featureSelections[wi] = new FeatureSelection(trainingSet.getDataAlphabet());
					destinationIndex++;
				}
//			addState ((String)outputAlphabet.lookupObject(i), 0.0, 0.0,
//								destinationNames, destinationNames, weightNames);
			addState ((String)outputAlphabet.lookupObject(i),  priorInitialCost[i], priorFinalCost[i],
								destinationNames, destinationNames, weightNames);


		}
	}

	public void addFullyConnectedStatesForThreeQuarterLabels (InstanceList trainingSet)
	{
		int numLabels = outputAlphabet.size();
		for (int i = 0; i < numLabels; i++) {
			String[] destinationNames = new String[numLabels];
			String[][] weightNames = new String[numLabels][];
			for (int j = 0; j < numLabels; j++) {
				String labelName = (String)outputAlphabet.lookupObject(j);
				destinationNames[j] = labelName;
				weightNames[j] = new String[2];
				// The "half-labels" will include all observational tests
				weightNames[j][0] = labelName;
				// The "transition" weights will include only the default feature
				String wn = (String)outputAlphabet.lookupObject(i) + "->" + (String)outputAlphabet.lookupObject(j);
				weightNames[j][1] = wn;
				int wi = getWeightsIndex (wn);
				// A new empty FeatureSelection won't allow any features here, so we only
				// get the default feature for transitions
				featureSelections[wi] = new FeatureSelection(trainingSet.getDataAlphabet());
			}
			addState ((String)outputAlphabet.lookupObject(i), 0.0, 0.0,
								destinationNames, destinationNames, weightNames);
		}
	}
	
	public void addFullyConnectedStatesForBiLabels ()
	{
		String[] labels = new String[outputAlphabet.size()];
		// This is assuming the the entries in the outputAlphabet are Strings!
		for (int i = 0; i < outputAlphabet.size(); i++) {
			logger.info ("CRF: outputAlphabet.lookup class = "+
									 outputAlphabet.lookupObject(i).getClass().getName());
			labels[i] = (String) outputAlphabet.lookupObject(i);
		}
		for (int i = 0; i < labels.length; i++) {
			for (int j = 0; j < labels.length; j++) {
				String[] destinationNames = new String[labels.length];
				for (int k = 0; k < labels.length; k++)
					destinationNames[k] = labels[j]+LABEL_SEPARATOR+labels[k];
				addState (labels[i]+LABEL_SEPARATOR+labels[j], 0.0, 0.0,
									destinationNames, labels);
			}
		}
	}

	/** Add states to create a second-order Markov model on labels,
			adding only those transitions the occur in the given
			trainingSet. */
	public void addStatesForBiLabelsConnectedAsIn (InstanceList trainingSet)
	{
		priorCost(trainingSet);

		int numLabels = outputAlphabet.size();
		boolean[][] connections = labelConnectionsIn (trainingSet);
		for (int i = 0; i < numLabels; i++) {
			for (int j = 0; j < numLabels; j++) {
				if (!connections[i][j])
					continue;
				int numDestinations = 0;
				for (int k = 0; k < numLabels; k++)
					if (connections[j][k]) numDestinations++;
				String[] destinationNames = new String[numDestinations];
				String[] labels = new String[numDestinations];
				int destinationIndex = 0;
				for (int k = 0; k < numLabels; k++)
					if (connections[j][k]) {
						destinationNames[destinationIndex] =
							(String)outputAlphabet.lookupObject(j)+LABEL_SEPARATOR+(String)outputAlphabet.lookupObject(k);
						labels[destinationIndex] = (String)outputAlphabet.lookupObject(k);
						destinationIndex++;
					}
				addState ((String)outputAlphabet.lookupObject(i)+LABEL_SEPARATOR+
									(String)outputAlphabet.lookupObject(j), 0.0, 0.0,
									destinationNames, labels);
			}
		}
	}
	
	public void addFullyConnectedStatesForTriLabels ()
	{
		String[] labels = new String[outputAlphabet.size()];
		// This is assuming the the entries in the outputAlphabet are Strings!
		for (int i = 0; i < outputAlphabet.size(); i++) {
			logger.info ("CRF: outputAlphabet.lookup class = "+
									 outputAlphabet.lookupObject(i).getClass().getName());
			labels[i] = (String) outputAlphabet.lookupObject(i);
		}
		for (int i = 0; i < labels.length; i++) {
			for (int j = 0; j < labels.length; j++) {
				for (int k = 0; k < labels.length; k++) {
					String[] destinationNames = new String[labels.length];
					for (int l = 0; l < labels.length; l++)
						destinationNames[l] = labels[j]+LABEL_SEPARATOR+labels[k]+LABEL_SEPARATOR+labels[l];
					addState (labels[i]+LABEL_SEPARATOR+labels[j]+LABEL_SEPARATOR+labels[k], 0.0, 0.0,
										destinationNames, labels);
				}
			}
		}
	}
	
	public void addSelfTransitioningStateForAllLabels (String name)
	{
		String[] labels = new String[outputAlphabet.size()];
		String[] destinationNames  = new String[outputAlphabet.size()];
		// This is assuming the the entries in the outputAlphabet are Strings!
		for (int i = 0; i < outputAlphabet.size(); i++) {
			logger.info ("CRF: outputAlphabet.lookup class = "+
													outputAlphabet.lookupObject(i).getClass().getName());
			labels[i] = (String) outputAlphabet.lookupObject(i);
			destinationNames[i] = name;
		}
		addState (name, 0.0, 0.0, destinationNames, labels);
	}

  private String concatLabels(String[] labels)
  {
    String sep = "";
    StringBuffer buf = new StringBuffer();
    for (int i = 0; i < labels.length; i++)
    {
      buf.append(sep).append(labels[i]);
      sep = LABEL_SEPARATOR;
    }
    return buf.toString();
  }
  
  private String nextKGram(String[] history, int k, String next)
  {
    String sep = "";
    StringBuffer buf = new StringBuffer();
    int start = history.length + 1 - k;
    for (int i = start; i < history.length; i++)
    {
      buf.append(sep).append(history[i]);
      sep = LABEL_SEPARATOR;
    }
    buf.append(sep).append(next);
    return buf.toString();
  }
  
  private boolean allowedTransition(String prev, String curr,
                                    Pattern no, Pattern yes)
  {
    String pair = concatLabels(new String[]{prev, curr});
    if (no != null && no.matcher(pair).matches())
      return false;
    if (yes != null && !yes.matcher(pair).matches())
      return false;
    return true;
  }
    
  private boolean allowedHistory(String[] history, Pattern no, Pattern yes) {
    for (int i = 1; i < history.length; i++)
      if (!allowedTransition(history[i-1], history[i], no, yes))
        return false;
    return true;
  }

  /**
   * Assumes that the CRF's output alphabet contains
   * <code>String</code>s. Creates an order-<em>n</em> CRF with input
   * predicates and output labels given by <code>trainingSet</code>
   * and order, connectivity, and weights given by the remaining
   * arguments.
   *
   * @param trainingSet the training instances
   * @param orders an array of increasing non-negative numbers giving
   * the orders of the features for this CRF. The largest number
   * <em>n</em> is the Markov order of the CRF. States are
   * <em>n</em>-tuples of output labels. Each of the other numbers
   * <em>k</em> in <code>orders</code> represents a weight set shared
   * by all destination states whose last (most recent) <em>k</em>
   * labels agree. If <code>orders</code> is <code>null</code>, an
   * order-0 CRF is built.
   * @param defaults If non-null, it must be the same length as
   * <code>orders</code>, with <code>true</code> positions indicating
   * that the weight set for the corresponding order contains only the
   * weight for a default feature; otherwise, the weight set has
   * weights for all features built from input predicates.
   * @param start The label that represents the context of the start of
   * a sequence. It may be also used for sequence labels.
   * @param forbidden If non-null, specifies what pairs of successive
   * labels are not allowed, both for constructing <em>n</em>order
   * states or for transitions. A label pair (<em>u</em>,<em>v</em>)
   * is not allowed if <em>u</em> + "," + <em>v</em> matches
   * <code>forbidden</code>.
   * @param allowed If non-null, specifies what pairs of successive
   * labels are allowed, both for constructing <em>n</em>order
   * states or for transitions. A label pair (<em>u</em>,<em>v</em>)
   * is allowed only if <em>u</em> + "," + <em>v</em> matches
   * <code>allowed</code>.
   * @param fullyConnected Whether to include all allowed transitions,
   * even those not occurring in <code>trainingSet</code>,
   * @return The name of the start state.
   * 
   */
  public String addOrderNStates(InstanceList trainingSet, int[] orders,
                                boolean[] defaults, String start,
                                Pattern forbidden, Pattern allowed,
                                boolean fullyConnected)
  {
    boolean[][] connections = null;
    if (!fullyConnected)
      connections = labelConnectionsIn (trainingSet);
    int order = -1;
    if (defaults != null && defaults.length != orders.length)
      throw new IllegalArgumentException("Defaults must be null or match orders");
    if (orders == null)
      order = 0;
    else
    {
      for (int i = 0; i < orders.length; i++)
        if (orders[i] <= order)
          throw new IllegalArgumentException("Orders must be non-negative and in ascending order");
        else 
          order = orders[i];
      if (order < 0) order = 0;
    }
    if (order > 0)
    {
      int[] historyIndexes = new int[order];
      String[] history = new String[order];
      String label0 = (String)outputAlphabet.lookupObject(0);
      for (int i = 0; i < order; i++)
        history[i] = label0;
      int numLabels = outputAlphabet.size();
      while (historyIndexes[0] < numLabels)
      {
        logger.info("Preparing " + concatLabels(history));
        if (allowedHistory(history, forbidden, allowed))
        {
          String stateName = concatLabels(history);
          int nt = 0;
          String[] destNames = new String[numLabels];
          String[] labelNames = new String[numLabels];
          String[][] weightNames = new String[numLabels][orders.length];
          for (int nextIndex = 0; nextIndex < numLabels; nextIndex++)
          {
            String next = (String)outputAlphabet.lookupObject(nextIndex);
            if (allowedTransition(history[order-1], next, forbidden, allowed)
                && (fullyConnected ||
                    connections[historyIndexes[order-1]][nextIndex]))
            {
              destNames[nt] = nextKGram(history, order, next);
              labelNames[nt] = next;
              for (int i = 0; i < orders.length; i++)
              {
                weightNames[nt][i] = nextKGram(history, orders[i]+1, next);
                if (defaults != null && defaults[i])
                  featureSelections[getWeightsIndex(weightNames[nt][i])] =
                    new FeatureSelection(trainingSet.getDataAlphabet());
              }
              nt++;
            }
          }
          if (nt < numLabels)
          {
            String[] newDestNames = new String[nt];
            String[] newLabelNames = new String[nt];
            String[][] newWeightNames = new String[nt][];
            for (int t = 0; t < nt; t++)
            {
              newDestNames[t] = destNames[t];
              newLabelNames[t] = labelNames[t];
              newWeightNames[t] = weightNames[t];
            }
            destNames = newDestNames;
            labelNames = newLabelNames;
            weightNames = newWeightNames;
          }
          for (int i = 0; i < destNames.length; i++)
          {
            StringBuffer b = new StringBuffer();
            for (int j = 0; j < orders.length; j++)
              b.append(" ").append(weightNames[i][j]);
            logger.info(stateName + "->" + destNames[i] +
                        "(" + labelNames[i] + ")" + b.toString());
          }
          addState (stateName, 0.0, 0.0, destNames, labelNames, weightNames);
        }
        for (int o = order-1; o >= 0; o--) 
          if (++historyIndexes[o] < numLabels)
          {
            history[o] = (String)outputAlphabet.lookupObject(historyIndexes[o]);
            break;
          } else if (o > 0)
          {
            historyIndexes[o] = 0;
            history[o] = label0;
          }
      }
      for (int i = 0; i < order; i++)
        history[i] = start;
      return concatLabels(history);
    }
    else
    {
      String[] stateNames = new String[outputAlphabet.size()];
      for (int s = 0; s < outputAlphabet.size(); s++)
        stateNames[s] = (String)outputAlphabet.lookupObject(s);
      for (int s = 0; s < outputAlphabet.size(); s++)
        addState(stateNames[s], 0.0, 0.0, stateNames, stateNames, stateNames);
      return start;
    }
  }

	public State getState (String name)
	{
		return (State) name2state.get(name);
	}
	
	public void setWeights (int weightsIndex, SparseVector transitionWeights)
	{
		cachedCostStale = cachedGradientStale = true;
		if (weightsIndex >= weights.length || weightsIndex < 0)
			throw new IllegalArgumentException ("weightsIndex "+weightsIndex+" is out of bounds");
		weights[weightsIndex] = transitionWeights;
	}

	public void setWeights (String weightName, SparseVector transitionWeights)
	{
		setWeights (getWeightsIndex (weightName), transitionWeights);
	}

	public String getWeightsName (int weightIndex)
	{
		return (String) weightAlphabet.lookupObject (weightIndex);
	}

	public SparseVector getWeights (String weightName)
	{
		return weights[getWeightsIndex (weightName)];
	}

	public SparseVector getWeights (int weightIndex)
	{
		return weights[weightIndex];
	}

	// Methods added by Ryan McDonald
	// Purpose is for AGIS-Limited Memory Experiments
	// Allows one to train on AGIS for N iterations, and then
	// copy weights to begin training on Limited-Memory for the
	// rest.
	public SparseVector[] getWeights ()
	{
		return weights;
	}
	
	public void setWeights (SparseVector[] m) {
		weights = m;
	}

	public void setWeightsDimensionAsIn (InstanceList trainingData)
	{
		int totalNumFeatures = 0;
		// The cost doesn't actually change, because the "new" parameters will have zero value
		// but the gradient changes because the parameters now have different layout.
		cachedCostStale = cachedGradientStale = true;
		setTrainable (false);
		weightsPresent = new BitSet[weights.length];
		for (int i = 0; i < weights.length; i++)
			weightsPresent[i] = new BitSet();
		gatheringWeightsPresent = true;
		// Put in the weights that are already there
		for (int i = 0; i < weights.length; i++) 
			for (int j = weights[i].numLocations()-1; j >= 0; j--)
				weightsPresent[i].set (weights[i].indexAtLocation(j));
		// Put in the weights in the training set
		if (this.someTrainingDone) System.err.println("Some training done previously");
		for (int i = 0; i < trainingData.size(); i++) {
			Instance instance = trainingData.getInstance(i);
			FeatureVectorSequence input = (FeatureVectorSequence) instance.getData();
			FeatureSequence output = (FeatureSequence) instance.getTarget();
			// Do it for the paths consistent with the labels...
			gatheringConstraints = true;
			forwardBackward (input, output, true);
			// ...and also do it for the paths selected by the current model (so we will get some negative weights)
			gatheringConstraints = false;
			if (this.someTrainingDone) 
				// (do this once some training is done)
				forwardBackward (input, null, true);
		}
		gatheringWeightsPresent = false;
		SparseVector[] newWeights = new SparseVector[weights.length];
		for (int i = 0; i < weights.length; i++) {
			int numLocations = weightsPresent[i].cardinality ();
			logger.info ("CRF weights["+weightAlphabet.lookupObject(i)+"] num features = "+numLocations);
			totalNumFeatures += numLocations;
			int[] indices = new int[numLocations];
			for (int j = 0; j < numLocations; j++) {
				indices[j] = weightsPresent[i].nextSetBit (j == 0 ? 0 : indices[j-1]+1);
				//System.out.println ("CRF3 has index "+indices[j]);
			}
			newWeights[i] = new IndexedSparseVector (indices, new double[numLocations],
																							 numLocations, numLocations, false, false, false);
			newWeights[i].plusEqualsSparse (weights[i]);
		}
		logger.info ("CRF total num features = "+totalNumFeatures);
		weights = newWeights;
	}

	/** Increase the size of the weights[] parameters to match (a new, larger)
			input Alphabet size */
	// No longer needed
	/*
	public void growWeightsDimensionToInputAlphabet ()
	{
		int vs = inputAlphabet.size();
		if (vs == this.defaultFeatureIndex)
			// Doesn't need to grow
			return;
		assert (vs > this.defaultFeatureIndex);
		setTrainable (false);
		for (int i = 0; i < weights.length; i++) {
			DenseVector newWeights = new DenseVector (vs+1);
			newWeights.arrayCopyFrom (0, weights[i]);
			newWeights.setValue (vs, weights[i].value (defaultFeatureIndex));
			newWeights.setValue (defaultFeatureIndex, 0);
			weights[i] = newWeights;
		}
		this.defaultFeatureIndex = vs;
		cachedCostStale = true;
		cachedGradientStale = true;
	}
	*/
	
	// Create a new weight Vector if weightName is new.
	public int getWeightsIndex (String weightName)
	{
		int wi = weightAlphabet.lookupIndex (weightName);
		if (wi == -1)
			throw new IllegalArgumentException ("Alphabet frozen, and no weight with name "+ weightName);
		if (weights == null) {
			assert (wi == 0);
			weights = new SparseVector[1];
			defaultWeights = new double[1];
			featureSelections = new FeatureSelection[1];
			// Use initial capacity of 8
			weights[0] = new IndexedSparseVector ();
			defaultWeights[0] = 0;
			featureSelections[0] = null;
		} else if (wi == weights.length) {
			SparseVector[] newWeights = new SparseVector[weights.length+1];
			double[] newDefaultWeights = new double[weights.length+1];
			FeatureSelection[] newFeatureSelections = new FeatureSelection[weights.length+1];
			for (int i = 0; i < weights.length; i++) {
				newWeights[i] = weights[i];
				newDefaultWeights[i] = defaultWeights[i];
				newFeatureSelections[i] = featureSelections[i];
			}
			newWeights[wi] = new IndexedSparseVector ();
			newDefaultWeights[wi] = 0;
			newFeatureSelections[wi] = null;
			weights = newWeights;
			defaultWeights = newDefaultWeights;
			featureSelections = newFeatureSelections;
		}
		setTrainable (false);
		return wi;
	}
	
	public int numStates () { return states.size(); }

	public Transducer.State getState (int index) {
		return (Transducer.State) states.get(index); }
	
	public Iterator initialStateIterator () {
		return initialStates.iterator (); }

	public boolean isTrainable () { return trainable; }

	public void setTrainable (boolean f)
	{
		if (f != trainable) {
			if (f) {
				constraints = new SparseVector[weights.length];
				expectations = new SparseVector[weights.length];
				defaultConstraints = new double[weights.length];
				defaultExpectations = new double[weights.length];
				for (int i = 0; i < weights.length; i++) {
					// index the vector so the index can be shared
					((IndexedSparseVector)weights[i]).indexVector(); 
					constraints[i] = (SparseVector) weights[i].cloneMatrixZeroed ();
					expectations[i] = (SparseVector) weights[i].cloneMatrixZeroed ();
				}
			} else {
				constraints = expectations = null;
				defaultConstraints = defaultExpectations = null;
			}
			for (int i = 0; i < numStates(); i++)
				((State)getState(i)).setTrainable(f);
			trainable = f;
		}
	}

	public double getParametersAbsNorm ()
	{
		double ret = 0;
		for (int i = 0; i < numStates(); i++) {
			State s = (State) getState (i);
			ret += Math.abs (s.initialCost);
			ret += Math.abs (s.finalCost);
		}
		for (int i = 0; i < weights.length; i++) {
			ret += Math.abs (defaultWeights[i]);
			ret += weights[i].absNorm();
		}
		return ret;
	}

	/** Only sets the parameter from the first group of parameters. */
	public void setParameter (int sourceStateIndex, int destStateIndex, int featureIndex, double value)
	{
		cachedCostStale = cachedGradientStale = true;
		State source = (State)getState(sourceStateIndex);
		State dest = (State) getState(destStateIndex);
		int rowIndex;
		for (rowIndex = 0; rowIndex < source.destinationNames.length; rowIndex++)
			if (source.destinationNames[rowIndex].equals (dest.name))
				break;
		if (rowIndex == source.destinationNames.length)
			throw new IllegalArgumentException ("No transtition from state "+sourceStateIndex+" to state "+destStateIndex+".");
		int weightsIndex = source.weightsIndices[rowIndex][0];
		if (featureIndex < 0)
			defaultWeights[weightsIndex] = value;
		else {
			weights[weightsIndex].setValue (featureIndex, value);
		}
		someTrainingDone = true;
	}

	/** Only gets the parameter from the first group of parameters. */
	public double getParameter (int sourceStateIndex, int destStateIndex, int featureIndex, double value)
	{
		State source = (State)getState(sourceStateIndex);
		State dest = (State) getState(destStateIndex);
		int rowIndex;
		for (rowIndex = 0; rowIndex < source.destinationNames.length; rowIndex++)
			if (source.destinationNames[rowIndex].equals (dest.name))
				break;
		if (rowIndex == source.destinationNames.length)
			throw new IllegalArgumentException ("No transtition from state "+sourceStateIndex+" to state "+destStateIndex+".");
		int weightsIndex = source.weightsIndices[rowIndex][0];
		if (featureIndex < 0)
			return defaultWeights[weightsIndex];
		else
			return weights[weightsIndex].value (featureIndex);
	}
	
	public void reset ()
	{
		throw new UnsupportedOperationException ("Not used in CRFs");
	}

	public void estimate ()
	{
		if (!trainable)
			throw new IllegalStateException ("This transducer not currently trainable.");
		// xxx Put stuff in here.
		throw new UnsupportedOperationException ("Not yet implemented.  Never?");
	}

	// yyy
	public void print ()
	{
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < numStates(); i++) {
			State s = (State) getState (i);
			sb.append ("STATE NAME=\"");
			sb.append (s.name);	sb.append ("\" ("); sb.append (s.destinations.length); sb.append (" outgoing transitions)\n");
			sb.append ("  "); sb.append ("initialCost = "); sb.append (s.initialCost); sb.append ('\n');
			sb.append ("  "); sb.append ("finalCost = "); sb.append (s.finalCost); sb.append ('\n');
			for (int j = 0; j < s.destinations.length; j++) {
				sb.append (" -> ");	sb.append (s.getDestinationState(j).getName());
				for (int k = 0; k < s.weightsIndices[j].length; k++) {
					sb.append (" WEIGHTS NAME=\"");
					sb.append (weightAlphabet.lookupObject(s.weightsIndices[j][k]).toString());
					sb.append ("\"\n");
					sb.append ("  ");
					sb.append (s.name); sb.append (" -> "); sb.append (s.destinations[j].name); sb.append (": ");
					sb.append ("<DEFAULT_FEATURE> = "); sb.append (defaultWeights[s.weightsIndices[j][k]]); sb.append('\n');
					SparseVector transitionWeights = weights[s.weightsIndices[j][k]];
					if (transitionWeights.numLocations() == 0)
						continue;
					RankedFeatureVector rfv = new RankedFeatureVector (inputAlphabet, transitionWeights);
					for (int m = 0; m < rfv.numLocations(); m++) {
						double v = rfv.getValueAtRank(m);
						int index = rfv.getIndexAtRank(m);
						Object feature = inputAlphabet.lookupObject (index);
						if (v != 0) {
							sb.append ("  ");
							sb.append (s.name); sb.append (" -> "); sb.append (s.destinations[j].name); sb.append (": ");
							sb.append (feature); sb.append (" = "); sb.append (v); sb.append ('\n');
						}
					}
				}
			}
		}
		System.out.println (sb.toString());
	}


	// Java question:
	// If I make a non-static inner class CRF.Trainer,
	// can that class by subclassed in another .java file,
	// and can that subclass still have access to all the CRF's
	// instance variables?

	public boolean train (InstanceList ilist)
	{
		return train (ilist, (InstanceList)null, (InstanceList)null);
	}

	public boolean train (InstanceList ilist, InstanceList validation, InstanceList testing)
	{
		return train (ilist, validation, testing, (TransducerEvaluator)null);
	}
	
	public boolean train (InstanceList ilist, InstanceList validation, InstanceList testing,
										 TransducerEvaluator eval)
	{
		return train (ilist, validation, testing, eval, 9999);
	}

	public boolean train (InstanceList ilist, InstanceList validation, InstanceList testing,
												TransducerEvaluator eval, int numIterations)
	{
		if (numIterations <= 0)
			return false;
		assert (ilist.size() > 0);
		setWeightsDimensionAsIn (ilist);
		MinimizableCRF mc = new MinimizableCRF (ilist, this);
		//Minimizer.ByGradient minimizer = new ConjugateGradient (0.001);
		Minimizer.ByGradient minimizer = new LimitedMemoryBFGS();
		int i;
		boolean continueTraining = true;
		boolean converged = false;
		logger.info ("CRF about to train with "+numIterations+" iterations");
		for (i = 0; i < numIterations; i++) {
			try {
				converged = minimizer.minimize (mc, 1);
//<<<<<<< CRF3.java
				System.out.println ("CRF finished one iteration of minimizer, i="+i);


//=======
				logger.info ("CRF finished one iteration of minimizer, i="+i);
//>>>>>>> 1.6
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
				logger.info ("Catching exception; saying converged.");
				converged = true;
			}
			if (eval != null) {
				continueTraining = eval.evaluate (this, (converged || i == numIterations-1), i,
																					converged, mc.getCost(), ilist, validation, testing);
				if (!continueTraining)
					break;
			}
			if (converged) {
				logger.info ("CRF training has converged, i="+i);
				break;
			}
		}
		logger.info ("About to setTrainable(false)");
		// Free the memory of the expectations and constraints
		setTrainable (false);
		logger.info ("Done setTrainable(false)");
		return converged;
	}

	public boolean train (InstanceList training, InstanceList validation, InstanceList testing,
												TransducerEvaluator eval, int numIterations,
												int numIterationsPerProportion,
												double[] trainingProportions)
	{
		int trainingIteration = 0;
		for (int i = 0; i < trainingProportions.length; i++) {
			// Train the CRF
			InstanceList theTrainingData = training;
			if (trainingProportions != null && i < trainingProportions.length) {
				logger.info ("Training on "+trainingProportions[i]+"% of the data this round.");
				InstanceList[] sampledTrainingData = training.split (new Random(1),
																															new double[] {trainingProportions[i],	1-trainingProportions[i]});
				theTrainingData = sampledTrainingData[0];
			}
			boolean converged = this.train (theTrainingData, validation, testing, eval, numIterationsPerProportion);
			trainingIteration += numIterationsPerProportion;
		}
		logger.info ("Training on 100% of the data this round, for "+
												(numIterations-trainingIteration)+" iterations.");
		return this.train (training, validation, testing,
											 eval, numIterations - trainingIteration);
	}

	public boolean trainWithFeatureInduction (InstanceList trainingData,
																						InstanceList validationData, InstanceList testingData,
																						TransducerEvaluator eval, int numIterations,
																						int numIterationsBetweenFeatureInductions,
																						int numFeatureInductions,
																						int numFeaturesPerFeatureInduction,
																						double trueLabelProbThreshold,
																						boolean clusteredFeatureInduction,
																						double[] trainingProportions)
	{
		return trainWithFeatureInduction (trainingData, validationData, testingData,
																			eval, numIterations, numIterationsBetweenFeatureInductions,
																			numFeatureInductions, numFeaturesPerFeatureInduction,
																			trueLabelProbThreshold, clusteredFeatureInduction,
																			trainingProportions, "exp");
	}
	
	public boolean trainWithFeatureInduction (InstanceList trainingData,
																						InstanceList validationData, InstanceList testingData,
																						TransducerEvaluator eval, int numIterations,
																						int numIterationsBetweenFeatureInductions,
																						int numFeatureInductions,
																						int numFeaturesPerFeatureInduction,
																						double trueLabelProbThreshold,
																						boolean clusteredFeatureInduction,
																						double[] trainingProportions,
																						String gainName)
	{
		int trainingIteration = 0;
		int numLabels = outputAlphabet.size();

		this.globalFeatureSelection = trainingData.getFeatureSelection();
		if (this.globalFeatureSelection == null) {
			// Mask out all features; some will be added later by FeatureInducer.induceFeaturesFor(.)
			this.globalFeatureSelection = new FeatureSelection (trainingData.getDataAlphabet());
			trainingData.setFeatureSelection (this.globalFeatureSelection);
		}
		if (validationData != null) validationData.setFeatureSelection (this.globalFeatureSelection);
		if (testingData != null) testingData.setFeatureSelection (this.globalFeatureSelection);
		
		for (int featureInductionIteration = 0;
				 featureInductionIteration < numFeatureInductions;
				 featureInductionIteration++)
		{
			// Print out some feature information
			logger.info ("Feature induction iteration "+featureInductionIteration);

			// Train the CRF
			InstanceList theTrainingData = trainingData;
			if (trainingProportions != null && featureInductionIteration < trainingProportions.length) {
				logger.info ("Training on "+trainingProportions[featureInductionIteration]+"% of the data this round.");
				InstanceList[] sampledTrainingData = trainingData.split (new Random(1),
																																	new double[] {trainingProportions[featureInductionIteration],
																																								1-trainingProportions[featureInductionIteration]});
				theTrainingData = sampledTrainingData[0];
				theTrainingData.setFeatureSelection (this.globalFeatureSelection); // xxx necessary?
				logger.info ("  which is "+theTrainingData.size()+" instances");
			}
			boolean converged = false;
			if (featureInductionIteration != 0)
				// Don't train until we have added some features
				converged = this.train (theTrainingData, validationData, testingData,
																eval, numIterationsBetweenFeatureInductions);
			trainingIteration += numIterationsBetweenFeatureInductions;

			// xxx Remove this next line
			this.print ();
			logger.info ("Starting feature induction with "+inputAlphabet.size()+" features.");
			
			// Create the list of error tokens, for both unclustered and clustered feature induction
			InstanceList errorInstances = new InstanceList (trainingData.getDataAlphabet(),
																											trainingData.getTargetAlphabet());
			// This errorInstances.featureSelection will get examined by FeatureInducer,
			// so it can know how to add "new" singleton features
			errorInstances.setFeatureSelection (this.globalFeatureSelection);
			ArrayList errorLabelVectors = new ArrayList();
			InstanceList clusteredErrorInstances[][] = new InstanceList[numLabels][numLabels];
			ArrayList clusteredErrorLabelVectors[][] = new ArrayList[numLabels][numLabels];
			
			for (int i = 0; i < numLabels; i++)
				for (int j = 0; j < numLabels; j++) {
					clusteredErrorInstances[i][j] = new InstanceList (trainingData.getDataAlphabet(),
																														trainingData.getTargetAlphabet());
					clusteredErrorInstances[i][j].setFeatureSelection (this.globalFeatureSelection);
					clusteredErrorLabelVectors[i][j] = new ArrayList();
				}
			
			for (int i = 0; i < theTrainingData.size(); i++) {
				logger.info ("instance="+i);
				Instance instance = theTrainingData.getInstance(i);
				Sequence input = (Sequence) instance.getData();
				Sequence trueOutput = (Sequence) instance.getTarget();
				assert (input.size() == trueOutput.size());
				System.out.println("Fuchun Peng: " + theTrainingData.getTargetAlphabet());
				Transducer.Lattice lattice = this.forwardBackward (input, null, false,
																													 (LabelAlphabet)theTrainingData.getTargetAlphabet());
				int prevLabelIndex = 0;					// This will put extra error instances in this cluster
				for (int j = 0; j < trueOutput.size(); j++) {
					Label label = (Label) ((LabelSequence)trueOutput).getLabelAtPosition(j);
					assert (label != null);
					//System.out.println ("Instance="+i+" position="+j+" fv="+lattice.getLabelingAtPosition(j).toString(true));
					LabelVector latticeLabeling = lattice.getLabelingAtPosition(j);
					double trueLabelProb = latticeLabeling.value(label.getIndex());
					int labelIndex = latticeLabeling.getBestIndex();
					//System.out.println ("position="+j+" trueLabelProb="+trueLabelProb);
					if (trueLabelProb < trueLabelProbThreshold) {
						logger.info ("Adding error: instance="+i+" position="+j+" prtrue="+trueLabelProb+
												 (label == latticeLabeling.getBestLabel() ? "  " : " *")+
												 " truelabel="+label+
												 " predlabel="+latticeLabeling.getBestLabel()+
												 " fv="+((FeatureVector)input.get(j)).toString(true));
						errorInstances.add (input.get(j), label, null, null);
						errorLabelVectors.add (latticeLabeling);
						clusteredErrorInstances[prevLabelIndex][labelIndex].add (input.get(j), label, null, null);
						clusteredErrorLabelVectors[prevLabelIndex][labelIndex].add (latticeLabeling);
					}
					prevLabelIndex = labelIndex;
				}
			}
			logger.info ("Error instance list size = "+errorInstances.size());
			if (clusteredFeatureInduction) {
				FeatureInducer[][] klfi = new FeatureInducer[numLabels][numLabels];
				for (int i = 0; i < numLabels; i++) {
					for (int j = 0; j < numLabels; j++) {
						// Note that we may see some "impossible" transitions here (like O->I in a OIB model)
						// because we are using lattice gammas to get the predicted label, not Viterbi.
						// I don't believe this does any harm, and may do some good.
						logger.info ("Doing feature induction for "+
												 outputAlphabet.lookupObject(i)+" -> "+outputAlphabet.lookupObject(j)+
												 " with "+clusteredErrorInstances[i][j].size()+" instances");
						if (clusteredErrorInstances[i][j].size() < 20) {
							logger.info ("..skipping because only "+clusteredErrorInstances[i][j].size()+" instances.");
							continue;
						}
						int s = clusteredErrorLabelVectors[i][j].size();
						LabelVector[] lvs = new LabelVector[s];
						for (int k = 0; k < s; k++)
							lvs[k] = (LabelVector) clusteredErrorLabelVectors[i][j].get(k);
						RankedFeatureVector.Factory gainFactory = null;
						if (gainName.equals ("exp"))
							gainFactory = new ExpGain.Factory (lvs, gaussianPriorVariance);
						else if (gainName.equals("grad"))
							gainFactory =	new GradientGain.Factory (lvs);
						else if (gainName.equals("info"))
							gainFactory =	new InfoGain.Factory ();
						klfi[i][j] = new FeatureInducer (gainFactory,
																						 clusteredErrorInstances[i][j], 
																						 numFeaturesPerFeatureInduction,
																						 2*numFeaturesPerFeatureInduction,
																						 2*numFeaturesPerFeatureInduction);
						featureInducers.add(klfi[i][j]);
					}
				}
				for (int i = 0; i < numLabels; i++) {
					for (int j = 0; j < numLabels; j++) {
						logger.info ("Adding new induced features for "+
												 outputAlphabet.lookupObject(i)+" -> "+outputAlphabet.lookupObject(j));
						if (klfi[i][j] == null) {
							logger.info ("...skipping because no features induced.");
							continue;
						}
						// Note that this adds features globally, but not on a per-transition basis
						klfi[i][j].induceFeaturesFor (trainingData, false, false);
						if (testingData != null) klfi[i][j].induceFeaturesFor (testingData, false, false);
					}
				}
				klfi = null;
			} else {
				int s = errorLabelVectors.size();
				LabelVector[] lvs = new LabelVector[s];
				for (int i = 0; i < s; i++)
					lvs[i] = (LabelVector) errorLabelVectors.get(i);

				RankedFeatureVector.Factory gainFactory = null;
				if (gainName.equals ("exp"))
					gainFactory = new ExpGain.Factory (lvs, gaussianPriorVariance);
				else if (gainName.equals("grad"))
					gainFactory =	new GradientGain.Factory (lvs);
				else if (gainName.equals("info"))
					gainFactory =	new InfoGain.Factory ();
				FeatureInducer klfi =
					new FeatureInducer (gainFactory,
															errorInstances, 
															numFeaturesPerFeatureInduction,
															2*numFeaturesPerFeatureInduction,
															2*numFeaturesPerFeatureInduction);
				featureInducers.add(klfi);
				// Note that this adds features globally, but not on a per-transition basis
				klfi.induceFeaturesFor (trainingData, false, false);
				if (testingData != null) klfi.induceFeaturesFor (testingData, false, false);
				logger.info ("CRF3 FeatureSelection now includes "+this.globalFeatureSelection.cardinality()+" features");
				klfi = null;
			}
			// This is done in CRF3.train() anyway
			//this.setWeightsDimensionAsIn (trainingData);
			////this.growWeightsDimensionToInputAlphabet ();
		}
		return this.train (trainingData, validationData, testingData,
											 eval, numIterations - trainingIteration);
	}

	/** This method is deprecated. */
	public Sequence[] predict (InstanceList testing) {
		testing.setFeatureSelection(this.globalFeatureSelection);
		for (int i = 0; i < featureInducers.size(); i++) {
			FeatureInducer klfi = (FeatureInducer)featureInducers.get(i);
			klfi.induceFeaturesFor (testing, false, false);
		}
		Sequence[] ret = new Sequence[testing.size()];
		for (int i = 0; i < testing.size(); i++) {
			Instance instance = testing.getInstance(i);
			Sequence input = (Sequence) instance.getData();
			Sequence trueOutput = (Sequence) instance.getTarget();
			assert (input.size() == trueOutput.size());
			Sequence predOutput = viterbiPath(input).output();
			assert (predOutput.size() == trueOutput.size());
			ret[i] = predOutput;
		}
		return ret;
	}

	
	/** This method is deprecated. */
	public void evaluate (TransducerEvaluator eval, InstanceList testing) {
		testing.setFeatureSelection(this.globalFeatureSelection);
		for (int i = 0; i < featureInducers.size(); i++) {
			FeatureInducer klfi = (FeatureInducer)featureInducers.get(i);
			klfi.induceFeaturesFor (testing, false, false);
		}
		eval.evaluate (this, true, 0, true, 0.0, null, null, testing);
	}

	public void write (File f) {
		try {
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
			oos.writeObject(this);
			oos.close();
		}
		catch (IOException e) {
			System.err.println("Exception writing file " + f + ": " + e);
		}
	}
	
	public MinimizableCRF getMinimizableCRF (InstanceList ilist)
	{
		return new MinimizableCRF (ilist, this);
	}

	// Serialization
	// For CRF class

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;
	static final int NULL_INTEGER = -1;

	/* Need to check for null pointers. */
	private void writeObject (ObjectOutputStream out) throws IOException {
		int i, size;
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject(inputPipe);
		out.writeObject(outputPipe);
		out.writeObject (inputAlphabet);
		out.writeObject (outputAlphabet);
		size = states.size();
		out.writeInt(size);
		for (i = 0; i<size; i++)
			out.writeObject(states.get(i));
		size = initialStates.size();
		out.writeInt(size);
		for (i = 0; i <size; i++)
			out.writeObject(initialStates.get(i));
		out.writeObject(name2state);
		if(weights != null) {
			size = weights.length;
			out.writeInt(size);
			for (i=0; i<size; i++)
				out.writeObject(weights[i]);
		}	else {
			out.writeInt(NULL_INTEGER);
		}
		if(constraints != null) {
			size = constraints.length;
			out.writeInt(size);
			for (i=0; i<size; i++)
				out.writeObject(constraints[i]);
		}	else {
			out.writeInt(NULL_INTEGER);
		}
		if (expectations != null) {
			size = expectations.length;
			out.writeInt(size);
			for (i=0; i<size; i++)
				out.writeObject(expectations[i]);
		} else {
			out.writeInt(NULL_INTEGER);
		}
		
		if(defaultWeights != null) {
			size = defaultWeights.length;
			out.writeInt(size);
			for (i=0; i<size; i++)
				out.writeDouble(defaultWeights[i]);
		}	else {
			out.writeInt(NULL_INTEGER);
		}
		if(defaultConstraints != null) {
			size = defaultConstraints.length;
			out.writeInt(size);
			for (i=0; i<size; i++)
				out.writeDouble(defaultConstraints[i]);
		}	else {
			out.writeInt(NULL_INTEGER);
		}
		if (defaultExpectations != null) {
			size = defaultExpectations.length;
			out.writeInt(size);
			for (i=0; i<size; i++)
				out.writeDouble(defaultExpectations[i]);
		}	else {
			out.writeInt(NULL_INTEGER);
		}
		
		if (weightsPresent != null) {
			size = weightsPresent.length;
			out.writeInt(size);
			for (i=0; i<size; i++)
				out.writeObject(weightsPresent[i]);
		}	else {
			out.writeInt(NULL_INTEGER);
		}
		if (featureSelections != null) {
			size = featureSelections.length;
			out.writeInt(size);
			for (i=0; i<size; i++)
				out.writeObject(featureSelections[i]);
		} else {
			out.writeInt(NULL_INTEGER);
		}
		
		out.writeObject(globalFeatureSelection);
		out.writeObject(weightAlphabet);
		out.writeBoolean(trainable);
		out.writeBoolean(gatheringConstraints);
		out.writeBoolean(gatheringWeightsPresent);
		//out.writeInt(defaultFeatureIndex);
		out.writeBoolean(usingHyperbolicPrior);
		out.writeDouble(gaussianPriorVariance);
		out.writeDouble(hyperbolicPriorSlope);
		out.writeDouble(hyperbolicPriorSharpness);
		out.writeBoolean(cachedCostStale);
		out.writeBoolean(cachedGradientStale);
		out.writeBoolean(someTrainingDone);
		out.writeInt(featureInducers.size());
		for (i = 0; i < featureInducers.size(); i++) {
			out.writeObject(featureInducers.get(i));
		}
		out.writeBoolean(printGradient);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int size, i;
		int version = in.readInt ();
		inputPipe = (Pipe) in.readObject();
		outputPipe = (Pipe) in.readObject();
		inputAlphabet = (Alphabet) in.readObject();
		outputAlphabet = (Alphabet) in.readObject();
		size = in.readInt();
		states = new ArrayList();
		for (i=0; i<size; i++) {
			State s = (CRF3.State) in.readObject();
			states.add(s);
		}
		size = in.readInt();
		initialStates = new ArrayList();
		for (i=0; i<size; i++) {
			State s = (CRF3.State) in.readObject();
			initialStates.add(s);
		}
		name2state = (HashMap) in.readObject();
		size = in.readInt();
		if (size == NULL_INTEGER) {
			weights = null;
		}
		else {
			weights = new SparseVector[size];
			for(i=0; i< size; i++) {
				weights[i] = (SparseVector) in.readObject();
			}
		}
		size = in.readInt();
		if (size == NULL_INTEGER) {
			constraints = null;
		}
		else {
			constraints = new SparseVector[size];
			for(i=0; i< size; i++) {
				constraints[i] = (SparseVector) in.readObject();
			}
		}
		size = in.readInt();
		if (size == NULL_INTEGER) {
			expectations = null;
		}
		else {
			expectations = new SparseVector[size];
			for(i=0; i< size; i++) {
				expectations[i] = (SparseVector)in.readObject();
			}
		}
		size = in.readInt();
		if (size == NULL_INTEGER) {
			defaultWeights = null;
		}
		else {
			defaultWeights = new double[size];
			for(i=0; i< size; i++) {
				defaultWeights[i] = in.readDouble();
			}
		}
		size = in.readInt();
		if (size == NULL_INTEGER) {
			defaultConstraints = null;
		}
		else {
			defaultConstraints = new double[size];
			for(i=0; i< size; i++) {
				defaultConstraints[i] = in.readDouble();
			}
		}
		size = in.readInt();
		if (size == NULL_INTEGER) {
			defaultExpectations = null;
		}
		else {
			defaultExpectations = new double[size];
			for(i=0; i< size; i++) {
				defaultExpectations[i] = in.readDouble();
			}
		}
		size = in.readInt();
		if (size == NULL_INTEGER) {
			weightsPresent = null;
		}	else {
			weightsPresent = new BitSet[size];
			for(i=0; i<size; i++)
				weightsPresent[i] = (BitSet)in.readObject();
		}

		size = in.readInt();
		if (size == NULL_INTEGER) {
			featureSelections = null;
		}	else {
			featureSelections = new FeatureSelection[size];
			for(i=0; i<size; i++)
				featureSelections[i] = (FeatureSelection)in.readObject();
		}
		
		globalFeatureSelection = (FeatureSelection) in.readObject();
		weightAlphabet = (Alphabet) in.readObject();
		trainable = in.readBoolean();
		gatheringConstraints = in.readBoolean();
		gatheringWeightsPresent = in.readBoolean();
		//defaultFeatureIndex = in.readInt();
		usingHyperbolicPrior = in.readBoolean();
		gaussianPriorVariance = in.readDouble();
		hyperbolicPriorSlope = in.readDouble();
		hyperbolicPriorSharpness = in.readDouble();
		cachedCostStale = in.readBoolean();
		cachedGradientStale = in.readBoolean();
		someTrainingDone = in.readBoolean();
		size = in.readInt();
		featureInducers = new ArrayList();
		for (i = 0; i < size; i++) {
			featureInducers.add((FeatureInducer)in.readObject());
		}
		printGradient = in.readBoolean();
	}


	public class MinimizableCRF implements Minimizable.ByGradient, Serializable
	{
		InstanceList trainingSet;
		double cachedCost = -123456789;
		DenseVector cachedGradient;
		BitSet infiniteCosts = null;
		int numParameters;
		CRF3 crf;

		protected MinimizableCRF (InstanceList ilist, CRF3 crf)
		{
			// Set up
			this.numParameters = 2 * numStates() + defaultWeights.length;
			for (int i = 0; i < weights.length; i++)
				numParameters += weights[i].numLocations();
			this.trainingSet = ilist;
			this.crf = crf;
			cachedGradient = (DenseVector) getNewMatrix ();

			// This resets and values that may have been in expecations and constraints
			setTrainable (true);

			// Set the contraints by running forward-backward with the *output
			// label sequence provided*, thus restricting it to only those
			// paths that agree with the label sequence.
			gatheringConstraints = true;
			for (int i = 0; i < ilist.size(); i++) {
				Instance instance = ilist.getInstance(i);
				FeatureVectorSequence input = (FeatureVectorSequence) instance.getData();
				FeatureSequence output = (FeatureSequence) instance.getTarget();
				//System.out.println ("Confidence-gathering forward-backward on instance "+i+" of "+ilist.size());
				this.crf.forwardBackward (input, output, true);
				//System.out.println ("Gathering constraints for Instance #"+i);
			}
			gatheringConstraints = false;
		}

		public Matrix getNewMatrix () { return new DenseVector (numParameters); }

		// Negate initialCost and finalCost because the parameters are in
		// terms of "weights", not "costs".
		
		public Matrix getParameters (Matrix m)
		{
			assert (m instanceof DenseVector && ((Vector)m).singleSize() == numParameters);
			DenseVector parameters = (DenseVector)m;
			int pi = 0;
			for (int i = 0; i < numStates(); i++) {
				State s = (State) getState (i);
				parameters.setValue (pi++, -s.initialCost);
				parameters.setValue (pi++, -s.finalCost);
			}
			for (int i = 0; i < weights.length; i++) {
				parameters.setValue (pi++, defaultWeights[i]);
				int nl = weights[i].numLocations();
				for (int j = 0; j < nl; j++)
					parameters.setValue (pi++, weights[i].valueAtLocation(j));
			}
			return parameters;
		}

		public void setParameters (Matrix m) {
			assert (m instanceof DenseVector && ((DenseVector)m).singleSize() == numParameters);
			cachedCostStale = cachedGradientStale = true;
			DenseVector parameters = (DenseVector)m;
			int pi = 0;
			for (int i = 0; i < numStates(); i++) {
				State s = (State) getState (i);
				s.initialCost = -parameters.value (pi++);
				s.finalCost = -parameters.value (pi++);
			}
			for (int i = 0; i < weights.length; i++) {
				defaultWeights[i] = parameters.value (pi++);
				int nl = weights[i].numLocations();
				for (int j = 0; j < nl; j++)
					weights[i].setValueAtLocation (j, parameters.value (pi++));
			}
			someTrainingDone = true;
		}

		public double getParameter (int[] indices) {
			assert (indices.length == 1);
			int index = indices[0];
			int numStateParms = 2 * numStates();
			if (index < numStateParms) {
				State s = (State)getState(index / 2);
				if (index % 2 == 0)
					return -s.initialCost;
				else
					return -s.finalCost;
			} else {
				index -= numStateParms;
				for (int i = 0; i < weights.length; i++) {
					if (index == 0)
						return defaultWeights[i];
					index--;
					if (index < weights[i].numLocations())
						return weights[i].valueAtLocation (index);
					else
						index -= weights[i].numLocations();
				}
				throw new IllegalArgumentException ("index too high = "+indices[0]);
			}
		}

		public void setParameter (int[] indices, double value) {
			cachedCostStale = cachedGradientStale = true;
			assert (indices.length == 1);
			int index = indices[0];
			int numStateParms = 2 * numStates();
			if (index < numStateParms) {
				State s = (State)getState(index / 2);
				if (index % 2 == 0)
					s.initialCost = -value;
				else
					s.finalCost = -value;
			} else {
				index -= numStateParms;
				for (int i = 0; i < weights.length; i++) {
					if (index == 0) {
						defaultWeights[i] = value;
						return;
					} else
						index--;
					if (index < weights[i].numLocations()) {
						weights[i].setValueAtLocation (index, value);
					} else
						index -= weights[i].numLocations();
				}
				throw new IllegalArgumentException ("index too high = "+indices[0]);
			}
		}


		// Minus log probability of the training sequence labels
		public double getCost ()
		{
			if (cachedCostStale) {
				long startingTime = System.currentTimeMillis();
				cachedCost = 0;
				cachedGradientStale = true;
				// Instance costs must either always or never be included in
				// the total costs; we can't just sometimes skip a cost
				// because it is infinite, this throws off the total costs.
				boolean initializingInfiniteCosts = false;
				if (infiniteCosts == null) {
					infiniteCosts = new BitSet ();
					initializingInfiniteCosts = true;
				}
				// Clear the sufficient statistics that we are about to fill
				for (int i = 0; i < numStates(); i++) {
					State s = (State)getState(i);
					s.initialExpectation = 0;
					s.finalExpectation = 0;
				}
				for (int i = 0; i < weights.length; i++) {
					expectations[i].setAll (0.0);
					defaultExpectations[i] = 0.0;
				}
				// Calculate the cost of each instance, and also fill in expectations
				double unlabeledCost, labeledCost, cost;
				for (int ii = 0; ii < trainingSet.size(); ii++) {
					Instance instance = trainingSet.getInstance(ii);
					FeatureVectorSequence input = (FeatureVectorSequence) instance.getData();
					FeatureSequence output = (FeatureSequence) instance.getTarget();
					labeledCost = forwardBackward (input, output, false).getCost();
					//System.out.println ("labeledCost = "+labeledCost);
					if (Double.isInfinite (labeledCost))
						logger.warning (instance.getName().toString() + " has infinite labeled cost.\n"
														+(instance.getSource() != null ? instance.getSource() : ""));
					unlabeledCost = forwardBackward (input, true).getCost ();
					//System.out.println ("unlabeledCost = "+unlabeledCost);
					//System.exit (0);
					if (Double.isInfinite (unlabeledCost))
						logger.warning (instance.getName().toString() + " has infinite unlabeled cost.\n"
														+(instance.getSource() != null ? instance.getSource() : ""));
					// Here cost is -log(conditional probability correct label sequence)
					cost = labeledCost - unlabeledCost;
					//System.out.println ("Instance "+ii+" CRF.MinimizableCRF.getCost = "+cost);
					if (Double.isInfinite(cost)) {
						logger.warning (instance.getName().toString() + " has infinite cost; skipping.");
						if (initializingInfiniteCosts)
							infiniteCosts.set (ii);
						else if (!infiniteCosts.get(ii))
							throw new IllegalStateException ("Instance i used to have non-infinite cost, "
																							 +"but now it has infinite cost.");
						continue;
					} else {
						cachedCost += cost;
					}
				}
				
				//System.out.println ("Total CRF.MinimizableCRF.getCost = "+cachedCost);

				// Incorporate prior on parameters
				if (usingHyperbolicPrior) {
					// Hyperbolic prior
					for (int i = 0; i < numStates(); i++) {
						State s = (State) getState (i);
						if (!Double.isInfinite(s.initialCost))
							cachedCost += (hyperbolicPriorSlope / hyperbolicPriorSharpness
														 * Math.log (Maths.cosh (hyperbolicPriorSharpness * -s.initialCost)));
						if (!Double.isInfinite(s.finalCost))
							cachedCost += (hyperbolicPriorSlope / hyperbolicPriorSharpness
														 * Math.log (Maths.cosh (hyperbolicPriorSharpness * -s.finalCost)));
					}
					for (int i = 0; i < weights.length; i++) {
						cachedCost += (hyperbolicPriorSlope / hyperbolicPriorSharpness
													 * Math.log (Maths.cosh (hyperbolicPriorSharpness * defaultWeights[i])));
						for (int j = 0; j < weights[i].numLocations(); j++) {
							double w = weights[i].valueAtLocation(j);
							if (!Double.isInfinite(w))
								cachedCost += (hyperbolicPriorSlope / hyperbolicPriorSharpness
															 * Math.log (Maths.cosh (hyperbolicPriorSharpness * w)));
						}
					}
				} else {
					// Gaussian prior
					double priorDenom = 2 * gaussianPriorVariance;
					for (int i = 0; i < numStates(); i++) {
						State s = (State) getState (i);
						if (!Double.isInfinite(s.initialCost))
							cachedCost += s.initialCost * s.initialCost / priorDenom;
						if (!Double.isInfinite(s.finalCost))
							cachedCost += s.finalCost * s.finalCost / priorDenom;
					}
					for (int i = 0; i < weights.length; i++) {
						if (!Double.isInfinite(defaultWeights[i]))
							cachedCost += defaultWeights[i] * defaultWeights[i] / priorDenom;
						for (int j = 0; j < weights[i].numLocations(); j++) {
							double w = weights[i].valueAtLocation (j);
							if (!Double.isInfinite(w))
								cachedCost += w * w / priorDenom;
						}
					}
				}
				
				cachedCostStale = false;
				logger.info ("getCost() (-loglikelihood) = "+cachedCost);
				logger.fine ("getCost() (-loglikelihood) = "+cachedCost);
				//crf.print();
				long endingTime = System.currentTimeMillis();
				logger.info ("Inference milliseconds = "+(endingTime - startingTime));
			}
			return cachedCost;
		}

		private boolean checkForNaN ()
		{
			for (int i = 0; i < weights.length; i++) {
				assert (!weights[i].isNaN());
				assert (constraints == null || !constraints[i].isNaN());
				assert (expectations == null || !expectations[i].isNaN());
				assert (!Double.isNaN(defaultExpectations[i]));
				assert (!Double.isNaN(defaultConstraints[i]));
			}
			for (int i = 0; i < numStates(); i++) {
				State s = (State) getState (i);
				assert (!Double.isNaN (s.initialExpectation));
				assert (!Double.isNaN (s.initialConstraint));
				assert (!Double.isNaN (s.initialCost));
				assert (!Double.isNaN (s.finalExpectation));
				assert (!Double.isNaN (s.finalConstraint));
				assert (!Double.isNaN (s.finalCost));
			}
			return true;
		}
	
		public Matrix getCostGradient (Matrix m)
		{
			// Gradient is -(constraint - expectation - parameters/gaussianPriorVariance)
			// == (expectation + parameters/gaussianPriorVariance - constraint)
			// This might be opposite from what you are used to seeing, this
			// is because this is the gradient of the "cost" and the
			// gradient should point "up-hill", which is actually away from
			// the direction we want to parameters to go.
			if (cachedGradientStale) {
				if (cachedCostStale)
					// This will fill in the this.expectation
					getCost ();
				assert (checkForNaN());
				Vector g = (Vector) m;
				int gi = 0;
				for (int i = 0; i < numStates(); i++) {
					State s = (State) getState (i);
					cachedGradient.setValue (gi++, (Double.isInfinite(s.initialCost)
																					? 0.0
																					: (s.initialExpectation
																						 + (usingHyperbolicPrior
																								? (hyperbolicPriorSlope
																									 * Maths.tanh (-s.initialCost)
																									 * hyperbolicPriorSharpness)
																								: ((-s.initialCost) / gaussianPriorVariance))
																						 - s.initialConstraint)));
					cachedGradient.setValue (gi++, (Double.isInfinite (s.finalCost)
																					? 0.0
																					: s.finalExpectation
																					+ (usingHyperbolicPrior
																						 ? (hyperbolicPriorSlope
																								* Maths.tanh (-s.finalCost)
																								* hyperbolicPriorSharpness)
																						 : ((-s.finalCost) / gaussianPriorVariance))
																					- s.finalConstraint));
				}
				if (usingHyperbolicPrior) {
					// Hyperbolic prior
					for (int i = 0; i < weights.length; i++) {
						cachedGradient.setValue (gi++, (Double.isInfinite (defaultWeights[i])
																							? 0.0
																							: (defaultExpectations[i]
																								 + (hyperbolicPriorSlope
																										* Maths.tanh (defaultWeights[i])
																										* hyperbolicPriorSharpness)
																								 - defaultConstraints[i])));
						if (printGradient)
							System.out.println ("CRF gradient["+crf.getWeightsName(i)+"][<DEFAULT_FEATURE>]="+cachedGradient.value(gi-1));
						for (int j = 0; j < weights[i].numLocations(); j++) {
							cachedGradient.setValue (gi++, (Double.isInfinite (weights[i].valueAtLocation(j))
																							? 0.0
																							: (expectations[i].valueAtLocation(j)
																								 + (hyperbolicPriorSlope
																										* Maths.tanh (weights[i].valueAtLocation(j))
																										* hyperbolicPriorSharpness)
																								 - constraints[i].valueAtLocation(j))));
							if (printGradient)
								System.out.println ("CRF gradient["+crf.getWeightsName(i)+"]["+inputAlphabet.lookupObject(j)+"]="+cachedGradient.value(gi-1));								
						}
					}
				} else {
					// Gaussian prior
					for (int i = 0; i < weights.length; i++) {
						cachedGradient.setValue (gi++, (Double.isInfinite (defaultWeights[i])
																							? 0.0
																							: (defaultExpectations[i]
																								 + defaultWeights[i] / gaussianPriorVariance
																								 - defaultConstraints[i])));
						if (printGradient)
							System.out.println ("CRF gradient["+crf.getWeightsName(i)+"][<DEFAULT_FEATURE>]="+cachedGradient.value(gi-1));
						for (int j = 0; j < weights[i].numLocations(); j++) {
							cachedGradient.setValue (gi++, (Double.isInfinite (weights[i].valueAtLocation(j))
																							? 0.0
																							: (expectations[i].valueAtLocation(j)
																								 + weights[i].valueAtLocation(j) / gaussianPriorVariance
																								 - constraints[i].valueAtLocation(j))));
							if (printGradient)
								System.out.println ("CRF gradient["+crf.getWeightsName(i)+"]["+inputAlphabet.lookupObject(j)+"]="+cachedGradient.value(gi-1));
						}
					}
				}
				// xxx Show the feature with maximum gradient
				cachedGradientStale = false;
				assert (!cachedGradient.isNaN());
			}
			m.set (cachedGradient);
			printGradient = false;
			return m;
		}

		//Serialization of MinimizableCRF
		
		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;
	
		private void writeObject (ObjectOutputStream out) throws IOException {
			out.writeInt (CURRENT_SERIAL_VERSION);
			out.writeObject(trainingSet);
			out.writeDouble(cachedCost);
			out.writeObject(cachedGradient);
			out.writeObject(infiniteCosts);
			out.writeInt(numParameters);
			out.writeObject(crf);
		}
		
		private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
			int version = in.readInt ();
			trainingSet = (InstanceList) in.readObject();
			cachedCost = in.readDouble();
			cachedGradient = (DenseVector) in.readObject();
			infiniteCosts = (BitSet) in.readObject();
			numParameters = in.readInt();
			crf = (CRF3)in.readObject();
		}

	}



	public static class State extends Transducer.State implements Serializable
	{
		// Parameters indexed by destination state, feature index
		double initialConstraint, initialExpectation;
		double finalConstraint, finalExpectation;
		String name;
		int index;
		String[] destinationNames;
		State[] destinations;
		int[][] weightsIndices;								// contains indices into CRF.weights[],
		String[] labels;
		CRF3 crf;
		
		// No arg constructor so serialization works
		
		protected State() {
			super ();
		}
		
		
		protected State (String name, int index,
										 double initialCost, double finalCost,
										 String[] destinationNames,
										 String[] labelNames,
										 String[][] weightNames,
										 CRF3 crf)
		{
			super ();
			assert (destinationNames.length == labelNames.length);
			assert (destinationNames.length == weightNames.length);
			this.name = name;
			this.index = index;
			this.initialCost = initialCost;
			this.finalCost = finalCost;
			this.destinationNames = new String[destinationNames.length];
			this.destinations = new State[labelNames.length];
			this.weightsIndices = new int[labelNames.length][];
			this.labels = new String[labelNames.length];
			this.crf = crf;
			for (int i = 0; i < labelNames.length; i++) {
				// Make sure this label appears in our output Alphabet
				crf.outputAlphabet.lookupIndex (labelNames[i]);
				this.destinationNames[i] = destinationNames[i];
				this.labels[i] = labelNames[i];
				this.weightsIndices[i] = new int[weightNames[i].length];
				for (int j = 0; j < weightNames[i].length; j++)
					this.weightsIndices[i][j] = crf.getWeightsIndex (weightNames[i][j]);
			}
			crf.cachedCostStale = crf.cachedGradientStale = true;
		}
		
		
		public void print ()
		{
			System.out.println ("State #"+index+" \""+name+"\"");
			System.out.println ("initialCost="+initialCost+", finalCost="+finalCost);
			System.out.println ("#destinations="+destinations.length);
			for (int i = 0; i < destinations.length; i++)
				System.out.println ("-> "+destinationNames[i]);
		}
		
		public State getDestinationState (int index)
		{
			State ret;
			if ((ret = destinations[index]) == null) {
				ret = destinations[index] = (State) crf.name2state.get (destinationNames[index]);
				//if (ret == null) System.out.println ("this.name="+this.name+" index="+index+" destinationNames[index]="+destinationNames[index]+" name2state.size()="+ crf.name2state.size());
				assert (ret != null) : index;
			}
			return ret;
		}
		
		public void setTrainable (boolean f)
		{
			if (f) {
				initialConstraint = finalConstraint = 0;
				initialExpectation = finalExpectation = 0;
			}
		}
		
		public Transducer.TransitionIterator transitionIterator (
			Sequence inputSequence, int inputPosition,
			Sequence outputSequence, int outputPosition)
		{
			if (inputPosition < 0 || outputPosition < 0)
				throw new UnsupportedOperationException ("Epsilon transitions not implemented.");
			if (inputSequence == null)
				throw new UnsupportedOperationException ("CRFs are not generative models; must have an input sequence.");
			return new TransitionIterator (
				this, (FeatureVectorSequence)inputSequence, inputPosition,
				(outputSequence == null ? null : (String)outputSequence.get(outputPosition)), crf);
		}
		
		public String getName () { return name; }
		
		public int getIndex () { return index; }

		public void incrementInitialCount (double count)
		{
			//System.out.println ("incrementInitialCount "+(gatheringConstraints?"constraints":"expectations")+" state#="+this.index+" count="+count);
			assert (crf.trainable || crf.gatheringWeightsPresent);
			if (crf.gatheringConstraints)
				initialConstraint += count;
			else
				initialExpectation += count;
		}
		
		public void incrementFinalCount (double count)
		{
			//System.out.println ("incrementFinalCount "+(gatheringConstraints?"constraints":"expectations")+" state#="+this.index+" count="+count);
			assert (crf.trainable || crf.gatheringWeightsPresent);
			if (crf.gatheringConstraints)
				finalConstraint += count;
			else
				finalExpectation += count;
		}
		
		// Serialization
		// For  class State
		
		
		
		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;
		private static final int NULL_INTEGER = -1;
		
		private void writeObject (ObjectOutputStream out) throws IOException {
			int i, size;
			out.writeInt (CURRENT_SERIAL_VERSION);
			out.writeDouble(initialConstraint);
			out.writeDouble(initialExpectation);
			out.writeDouble(finalConstraint);
			out.writeDouble(finalExpectation);
			out.writeObject(name);
			out.writeInt(index);
			size = (destinationNames == null) ? NULL_INTEGER : destinationNames.length;
			out.writeInt(size);
			if (size != NULL_INTEGER) {
				for(i=0; i<size; i++){
					out.writeObject(destinationNames[i]);
				}
			}
			size = (destinations == null) ? NULL_INTEGER : destinations.length;
			out.writeInt(size);
			if (size != NULL_INTEGER) {
				for(i=0; i<size;i++) {
					out.writeObject(destinations[i]);
				}
			}
			size = (weightsIndices == null) ? NULL_INTEGER : weightsIndices.length;
			out.writeInt(size);
			if (size != NULL_INTEGER) {
				for (i=0; i<size; i++) {
					out.writeInt(weightsIndices[i].length);
					for (int j = 0; j < weightsIndices[i].length; j++)
						out.writeInt(weightsIndices[i][j]);
				}
			}
			size = (labels == null) ? NULL_INTEGER : labels.length;
			out.writeInt(size);
			if (size != NULL_INTEGER) {
				for (i=0; i<size; i++)
					out.writeObject(labels[i]);
				//out.writeObject (inputAlphabet);
				//out.writeObject (outputAlphabet);
			}
			out.writeObject(crf);
		}
		
		private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
			int size, i;
			int version = in.readInt ();
			initialConstraint = in.readDouble();
			initialExpectation = in.readDouble();
			finalConstraint = in.readDouble();
			finalExpectation = in.readDouble();
			name = (String) in.readObject();
			index = in.readInt();
			size = in.readInt();
			if (size != NULL_INTEGER) {
				destinationNames = new String[size];
				for (i=0; i<size; i++) {
					destinationNames[i] = (String) in.readObject();
				}
			}
			else {
				destinationNames = null;
			}
			size = in.readInt();
			if (size != NULL_INTEGER) {
				destinations = new State[size];
				for (i=0; i<size; i++) {
					destinations[i] = (State) in.readObject();
				}
			}
			else {
				destinations = null;
			}
			size = in.readInt();
			if (size != NULL_INTEGER) {
				weightsIndices = new int[size][];
				for (i=0; i<size; i++) {
					int size2 = in.readInt();
					weightsIndices[i] = new int[size2];
					for (int j = 0; j < size2; j++)
						weightsIndices[i][j] = in.readInt();
				}
			}
			else {
				weightsIndices = null;
			}
			size = in.readInt();
			if (size != NULL_INTEGER) {
				labels = new String[size];
				for (i=0; i<size; i++)
					labels[i] = (String) in.readObject();
				//inputAlphabet = (Alphabet) in.readObject();
				//outputAlphabet = (Alphabet) in.readObject();
			}	else {
				labels = null;
			}
			crf = (CRF3) in.readObject();
		}
		
	
	}


	protected static class TransitionIterator extends Transducer.TransitionIterator implements Serializable
	{
		State source;
		int index, nextIndex;
		double[] costs;
		// Eventually change this because we will have a more space-efficient
		// FeatureVectorSequence that cannot break out each FeatureVector
		FeatureVector input;
		CRF3 crf;
		
		public TransitionIterator (State source,
															 FeatureVectorSequence inputSeq,
															 int inputPosition,
															 String output, CRF3 crf)
		{
			this.source = source;
			this.crf = crf;
			this.input = (FeatureVector) inputSeq.get(inputPosition);
			this.costs = new double[source.destinations.length];
			int nwi, swi;
			for (int transIndex = 0; transIndex < source.destinations.length; transIndex++) {
				// xxx Or do we want output.equals(...) here?
				if (output == null || output.equals(source.labels[transIndex])) {
					// Here is the dot product of the feature weights with the lambda weights
					// for one transition
					costs[transIndex] = 0;
					nwi = source.weightsIndices[transIndex].length;
					for (int wi = 0; wi < nwi; wi++) {
						swi = source.weightsIndices[transIndex][wi];
						costs[transIndex] -= (inputSeq.dotProduct (inputPosition, crf.weights[swi])
																	// include with implicit weight 1.0 the default feature
																	+ crf.defaultWeights[swi]);
					}
					assert (!Double.isNaN(costs[transIndex]));
				}
				else
					costs[transIndex] = INFINITE_COST;
			}
			nextIndex = 0;
			while (nextIndex < source.destinations.length && costs[nextIndex] == INFINITE_COST)
				nextIndex++;
		}
		
		public boolean hasNext ()	{ return nextIndex < source.destinations.length; }

		public int numberNext(){return source.destinations.length;} //added by Fuchun Peng
		
		public Transducer.State nextState ()
		{
			assert (nextIndex < source.destinations.length);
			index = nextIndex;
			nextIndex++;
			while (nextIndex < source.destinations.length && costs[nextIndex] == INFINITE_COST)
				nextIndex++;
			return source.getDestinationState (index);
		}
		
		public Object getInput () { return input; }
		public Object getOutput () { return source.labels[index]; }
		public double getCost () { return costs[index]; }
		public Transducer.State getSourceState () { return source; }
		public Transducer.State getDestinationState () {
			return source.getDestinationState (index);	}
		
		public void incrementCount (double count)
		{
			//System.out.println ("incrementCount "+(gatheringConstraints?"constraints":"expectations")+" dest#="+source.index+" count="+count);
			assert (crf.trainable || crf.gatheringWeightsPresent);
			// Because of parameter tying there may be multiple "weight arrays" associated with a single transition.
			int nwi = source.weightsIndices[index].length;
			for (int wi = 0; wi < nwi; wi++) {
				int weightsIndex = source.weightsIndices[index][wi];
				if (crf.gatheringWeightsPresent) {
					if (crf.gatheringConstraints || count >= 0.2)								// xxx This 0.2 is somewhat arbitrary!
						// When doing this without the true output labels, don't include everything
						for (int i = 0; i < input.numLocations(); i++) {
							int index = input.indexAtLocation(i);
							if ((crf.globalFeatureSelection == null || crf.globalFeatureSelection.contains(index))
									&& (crf.featureSelections == null
											|| crf.featureSelections[weightsIndex] == null
											|| crf.featureSelections[weightsIndex].contains(index)))
								crf.weightsPresent[weightsIndex].set (index);
						}
				} else if (crf.gatheringConstraints) {
					crf.constraints[weightsIndex].plusEqualsSparse (input, count);
					crf.defaultConstraints[weightsIndex] += count;
				} else {
					crf.expectations[weightsIndex].plusEqualsSparse (input, count);
					crf.defaultExpectations[weightsIndex] += count;
				}
			}
		}


		// Serialization
		// TransitionIterator
		
		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;
		private static final int NULL_INTEGER = -1;
		
		private void writeObject (ObjectOutputStream out) throws IOException {
			out.writeInt (CURRENT_SERIAL_VERSION);
			out.writeObject (source);
			out.writeInt (index);
			out.writeInt (nextIndex);
			if (costs != null) {
				out.writeInt (costs.length);
				for (int i = 0; i < costs.length; i++) {
					out.writeDouble (costs[i]);
				}
			}
			else {
				out.writeInt(NULL_INTEGER);
			}
			out.writeObject (input);
			out.writeObject(crf);
		}
		
		private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
			int version = in.readInt ();
			source = (State) in.readObject();
			index = in.readInt ();
			nextIndex = in.readInt ();
			int size = in.readInt();
			if (size == NULL_INTEGER) {
				costs = null;
			}
			else {
				costs = new double[size];
				for (int i =0; i <size; i++) {
					costs[i] = in.readDouble();
				}
			}
			input = (FeatureVector) in.readObject();
			crf = (CRF3) in.readObject();
		}
		
	}
}


