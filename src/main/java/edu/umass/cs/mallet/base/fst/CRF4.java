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
import edu.umass.cs.mallet.base.maximize.*;
import edu.umass.cs.mallet.base.maximize.tests.*;
import edu.umass.cs.mallet.base.util.Maths;
import edu.umass.cs.mallet.base.util.MalletLogger;
import edu.umass.cs.mallet.base.util.ArrayUtils;

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
import java.text.DecimalFormat;


/*
	Changes from CRF3:
	- converted MinimizableTrainer to a MaximizableTrainer
	- getCost is now getValue
	- getCostGradient is now getValueGradient	
*/

/* There are several different kinds of numeric values:

   "weights" range from -Inf to Inf.  High weights make a path more
   likely.  These don't appear directly in Transducer.java, but appear
	 as parameters to many subclasses, such as CRFs.  Weights are also
	 often summed, or combined in a dot product with feature vectors.

	 "unnormalized costs" range from -Inf to Inf.  High costs make a
	 path less likely.  Unnormalized costs can be obtained from negated
	 weights or negated sums of weights.  These are often returned by a
	 TransitionIterator's getValue() method.  The LatticeNode.alpha
	 values are unnormalized costs.

	 "normalized costs" range from 0 to Inf.  High costs make a path
	 less likely.  Normalized costs can safely be considered as the
	 -log(probability) of some event.  They can be obtained by
	 substracting a (negative) normalizer from unnormalized costs, for
	 example, subtracting the total cost of a lattice.  Typically
	 initialCosts and finalCosts are examples of normalized costs, but
	 they are also allowed to be unnormalized costs.  The gammas[][],
	 stateGammas[], and transitionXis[][] are all normalized costs, as
	 well as the return value of Lattice.getValue().

	 "probabilities" range from 0 to 1.  High probabilities make a path
	 more likely.  They are obtained from normalized costs by taking the
	 log and negating.  

	 "sums of probabilities" range from 0 to positive numbers.  They are
	 the sum of several probabilities.  These are passed to the
	 incrementCount() methods.
	 
*/


public class CRF4 extends Transducer implements Serializable
{
	private static Logger logger = MalletLogger.getLogger(CRF.class.getName());

	static final double DEFAULT_GAUSSIAN_PRIOR_VARIANCE = 1.0;
	static final double DEFAULT_HYPERBOLIC_PRIOR_SLOPE = 0.2;
	static final double DEFAULT_HYPERBOLIC_PRIOR_SHARPNESS = 10.0;
  static final String LABEL_SEPARATOR = ",";
	
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
  boolean[] weightsFrozen;
	Alphabet weightAlphabet = new Alphabet ();
	boolean trainable = false;
	boolean gatheringConstraints = false;
	boolean gatheringWeightsPresent = false;
	//int defaultFeatureIndex;
	boolean usingHyperbolicPrior = false;
	double gaussianPriorVariance = DEFAULT_GAUSSIAN_PRIOR_VARIANCE;
	double hyperbolicPriorSlope = DEFAULT_HYPERBOLIC_PRIOR_SLOPE;
	double hyperbolicPriorSharpness = DEFAULT_HYPERBOLIC_PRIOR_SHARPNESS;
	boolean useSparseWeights = true;
  private transient boolean useSomeUnsupportedTrick = true;
	private boolean cachedValueStale = true;
	private boolean cachedGradientStale = true;
	protected boolean someTrainingDone = false;
  private int transductionType = 0;
	ArrayList featureInducers = new ArrayList();

	// xxx temporary hack.
  //  This is quite useful to have, though!! -cas
	public boolean printGradient = false;

	public CRF4 (Pipe inputPipe, Pipe outputPipe)
	{
		this.inputPipe = inputPipe;
		this.outputPipe = outputPipe;
		this.inputAlphabet = inputPipe.getDataAlphabet();
		this.outputAlphabet = inputPipe.getTargetAlphabet();
		//this.defaultFeatureIndex = inputAlphabet.size();
		//inputAlphabet.stopGrowth();
	}
	
	public CRF4 (Alphabet inputAlphabet,
							 Alphabet outputAlphabet)
	{
		inputAlphabet.stopGrowth();
		logger.info ("CRF input dictionary size = "+inputAlphabet.size());
		//xxx outputAlphabet.stopGrowth();
		this.inputAlphabet = inputAlphabet;
		this.outputAlphabet = outputAlphabet;
		//this.defaultFeatureIndex = inputAlphabet.size();
	}

  /**
   * Create a CRF whose states and weights are a copy of noes from another CRF.
   */
  public CRF4 (CRF4 other)
  {
    this (other.getInputPipe (), other.getOutputPipe ());
    copyStatesAndWeightsFrom (other);
    assertWeightsLength ();
  }

	private void copyStatesAndWeightsFrom (CRF4 initialCRF)
  {
		//this.defaultFeatureIndex = initialCRF.defaultFeatureIndex;

    weightAlphabet = (Alphabet) initialCRF.weightAlphabet.clone ();
    weights = new SparseVector [initialCRF.weights.length];

    states.clear ();
		for (int i = 0; i < initialCRF.states.size(); i++) {
			State s = (State) initialCRF.getState (i);
			String[][] weightNames = new String[s.weightsIndices.length][];
			for (int j = 0; j < weightNames.length; j++) {
        int[] thisW = s.weightsIndices[j];
        weightNames[j] = (String[]) initialCRF.weightAlphabet.lookupObjects(thisW, new String [s.weightsIndices[j].length]);
      }
			addState (s.name, s.initialCost, s.finalCost, s.destinationNames, s.labels, weightNames);
		}

		assert (weights.length > 0);
    defaultWeights = (double[]) initialCRF.defaultWeights.clone();
		for (int i = 0; i < weights.length; i++) {
      Object wname = weightAlphabet.lookupObject (i);
      int otherIndex = initialCRF.weightAlphabet.lookupIndex (wname);
			weights[i] = (SparseVector) initialCRF.weights [otherIndex].cloneMatrix();
    }

    featureSelections = (FeatureSelection[]) initialCRF.featureSelections.clone ();
    weightsFrozen = (boolean[]) initialCRF.weightsFrozen.clone();
	}

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

	public void setUseSparseWeights (boolean b) { useSparseWeights = b; }
	public boolean getUseSparseWeights () { return useSparseWeights; }

  /** Sets whether to use the 'some unsupported trick.' This trick is, if training a CRF
   * where some training has been done and sparse weights are used, to add a few weights
   * for feaures that do not occur in the tainig data.
   * <p>
   * This generally leads to better accuracy at only a  small memory cost.
   *
   * @param b Whether to use the trick
   */
  public void setUseSomeUnsupportedTrick (boolean b) { useSomeUnsupportedTrick = b; }

    // Types of transuction support
    public static final int VITERBI = 0;
    // CPAL   - some new beam based "transducers"
    //        - Here is a forward viterbi "beam search"
    public static final int VITERBI_FBEAM = 1;
    // CPAL   - backward beam transducer
    public static final int VITERBI_BBEAM = 2;
    // CPAL   - forward backward beam transducer
    public static final int VITERBI_FBBEAM = 3;
    // CPAL   - adaptive KL divergence forward beam
    public static final int VITERBI_FBEAMKL = 4;


  public int getTransductionType () { return transductionType; }
  public void setTransductionType (int transductionType) { this.transductionType = transductionType; }

	protected State newState (String name, int index,
	                          double initialCost, double finalCost,
	                          String[] destinationNames,
	                          String[] labelNames,
	                          String[][] weightNames,
	                          CRF4 crf)
	{
		return new State (name, index, initialCost, finalCost,
		                  destinationNames, labelNames, weightNames, crf);
	}

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
		State s = newState (name, states.size(), initialCost, finalCost,
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
		this.addState (name, 0, 0,
									 destinationNames, destinationNames);
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

  public void addStartState ()
  {
    addStartState ("<START>");
  }

  public void addStartState (String name)
  {
    for (int i = 0; i < numStates (); i++)
      getState(i).initialCost = INFINITE_COST;

    String[] dests = new String [numStates ()];
    for (int i = 0; i < dests.length; i++)
      dests[i] = getState(i).getName();

    addState (name, 0, INFINITE_COST, dests, dests);
  }

  public void setAsStartState (State state)
  {
    for (int i = 0; i < numStates(); i++) {
      Transducer.State other = getState (i);
      if (other == state) {
        other.setInitialCost (0);
      } else {
        other.setInitialCost (INFINITE_COST);
      }
    }
  }

  private boolean[][] labelConnectionsIn (InstanceList trainingSet)
  {
    return labelConnectionsIn (trainingSet, null);
  }

	private boolean[][] labelConnectionsIn (InstanceList trainingSet, String start)
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

    // Handle start state
    if (start != null) {
      int startIndex = outputAlphabet.lookupIndex (start);
      for (int j = 0; j < outputAlphabet.size(); j++) {
        connections[startIndex][j] = true;
      }
    }

		return connections;
	}

	/** Add states to create a first-order Markov model on labels,
			adding only those transitions the occur in the given
			trainingSet. */
	public void addStatesForLabelsConnectedAsIn (InstanceList trainingSet)
	{
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
			addState ((String)outputAlphabet.lookupObject(i), 0.0, 0.0,
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
			addState ((String)outputAlphabet.lookupObject(i), 0.0, 0.0,
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
   * a sequence. It may be also used for sequence labels.  If no label of
   * this name exists, one will be added. Connection wills be added between
   * the start label and all other labels, even if <tt>fullyConnected</tt> is
   * <tt>false</tt>.  This argument may be null, in which case no special
   * start state is added.
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
    if (start != null)
      outputAlphabet.lookupIndex (start);
    if (!fullyConnected)
      connections = labelConnectionsIn (trainingSet, start);
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
                if (defaults != null && defaults[i]) {
									int wi = getWeightsIndex (weightNames[nt][i]);
									// Using empty feature selection gives us only the
									// default features
									featureSelections[wi] =
                    new FeatureSelection(trainingSet.getDataAlphabet());
								}
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
		cachedValueStale = cachedGradientStale = true;
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

	public double[] getDefaultWeights ()
	{
		return defaultWeights;
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

  public void setDefaultWeights (double[] w)
  {
    defaultWeights = w;
  }

  public void setDefaultWeight (int widx, double val) { defaultWeights[widx] = val; }

  /**
    * Freezes a set of weights to their current values.
    *  Frozen weights are used for labeling sequences (as in <tt>transduce</tt>),
    *  but are not be modified by the <tt>train</tt> methods.
    * @param weightsIndex Index of weight set to freeze.
    */
   public void freezeWeights (int weightsIndex)
  {
    weightsFrozen [weightsIndex] = true;
  }

  /**
   * Freezes a set of weights to their current values.
   *  Frozen weights are used for labeling sequences (as in <tt>transduce</tt>),
   *  but are not be modified by the <tt>train</tt> methods.
   * @param weightsName Name of weight set to freeze.
   */
  public void freezeWeights (String weightsName)
  {
    int widx = getWeightsIndex (weightsName);
    freezeWeights (widx);
  }

  /**
   * Unfreezes a set of weights.
   *  Frozen weights are used for labeling sequences (as in <tt>transduce</tt>),
   *  but are not be modified by the <tt>train</tt> methods.
   * @param weightsName Name of weight set to unfreeze.
   */
  public void unfreezeWeights (String weightsName)
  {
    int widx = getWeightsIndex (weightsName);
    weightsFrozen[widx] = false;
  }

  public void setFeatureSelection (int weightIdx, FeatureSelection fs)
  {
    featureSelections [weightIdx] = fs;
  }

	public void setWeightsDimensionAsIn (InstanceList trainingData)
	{
    int numWeights = 0;
		// The value doesn't actually change, because the "new" parameters will have zero value
		// but the gradient changes because the parameters now have different layout.
		cachedValueStale = cachedGradientStale = true;
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

            // ******************************************************************************************
            // CPAL - Beam Version could be used here
			forwardBackward (input, output, true);
            // forwardBackwardBeam (input, output, true);
            // ******************************************************************************************
			// ...and also do it for the paths selected by the current model (so we will get some negative weights)
			gatheringConstraints = false;
			if (this.someTrainingDone && useSomeUnsupportedTrick) {
        logger.info ("CRF4: Incremental training detected.  Adding weights for some supported features...");
				// (do this once some training is done)
                // ******************************************************************************************
                // CPAL - Beam Version could be used here
				forwardBackward (input, null, true);
                // ******************************************************************************************
                //forwardBackwardBeam (input, output, true);
      }
		}
		gatheringWeightsPresent = false;
		SparseVector[] newWeights = new SparseVector[weights.length];
		for (int i = 0; i < weights.length; i++) {
			int numLocations = weightsPresent[i].cardinality ();
			logger.info ("CRF weights["+weightAlphabet.lookupObject(i)+"] num features = "+numLocations);
			int[] indices = new int[numLocations];
			for (int j = 0; j < numLocations; j++) {
				indices[j] = weightsPresent[i].nextSetBit (j == 0 ? 0 : indices[j-1]+1);
				//System.out.println ("CRF4 has index "+indices[j]);
			}
			newWeights[i] = new IndexedSparseVector (indices, new double[numLocations],
																							 numLocations, numLocations, false, false, false);
			newWeights[i].plusEqualsSparse (weights[i]);
      numWeights += (numLocations + 1);
		}
    logger.info("Number of weights = "+numWeights);
		weights = newWeights;
	}

	public void setWeightsDimensionDensely ()
	{
		SparseVector[] newWeights = new SparseVector [weights.length];
		int max = inputAlphabet.size();
		int numWeights = 0;
		logger.info ("CRF using dense weights, num input features = "+max);
		for (int i = 0; i < weights.length; i++) {
      int nfeatures;
      if (featureSelections[i] == null) {
        nfeatures = max;
			  newWeights [i] = new SparseVector (null, new double [max],
				  																 max, max, false, false, false);
      } else {
        // Respect the featureSelection
        FeatureSelection fs = featureSelections[i];
        nfeatures = fs.getBitSet ().cardinality ();
        int[] idxs = new int [nfeatures];
        int j = 0, thisIdx = -1;
        while ((thisIdx = fs.nextSelectedIndex (thisIdx + 1)) >= 0) {
          idxs[j++] = thisIdx;
        }
        newWeights[i] = new SparseVector (idxs, new double [nfeatures], nfeatures, nfeatures, false, false, false);
      }
			newWeights [i].plusEqualsSparse (weights [i]);
			numWeights += (nfeatures + 1);
		}
		logger.info("Number of weights = "+numWeights);
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
		cachedValueStale = true;
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
      weightsFrozen = new boolean [1];
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
      weightsFrozen = ArrayUtils.append (weightsFrozen, false);
		}
		setTrainable (false);
		return wi;
	}

  private void assertWeightsLength ()
  {
    if (weights != null) {
      assert defaultWeights != null;
      assert featureSelections != null;
      assert weightsFrozen != null;

      int n = weights.length;
      assert defaultWeights.length == n;
      assert featureSelections.length == n;
      assert weightsFrozen.length == n;
    }
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
		cachedValueStale = cachedGradientStale = true;
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

  //xxx experimental
  public Sequence transduce (Object unpipedInput)
  {
    Instance inst = new Instance (unpipedInput, null, null, null, inputPipe);
    return transduce ((FeatureVectorSequence) inst.getData ());
  }

  public Sequence transduce (Sequence input)
  {
    if (!(input instanceof FeatureVectorSequence))
      throw new IllegalArgumentException ("CRF4.transduce requires FeatureVectorSequence.  This may be relaxed later...");

    switch (transductionType) {
      case VITERBI:
        ViterbiPath lattice = viterbiPath (input);
        return lattice.output ();

      // CPAL - added viterbi "beam search"
      case VITERBI_FBEAM:
        ViterbiPathBeam lattice2 = viterbiPathBeam(input);
        return lattice2.output();

      //CPAL - added viterbi backward beam search
      case VITERBI_BBEAM:
        ViterbiPathBeamB lattice3 = viterbiPathBeamB(input);
        return lattice3.output();

      // CPAL - added viterbi forward backward beam
      //      - we may call this constrained "max field" or something similar later
      case VITERBI_FBBEAM:
        ViterbiPathBeamFB lattice4 = viterbiPathBeamFB(input);
        return lattice4.output();

      // CPAL - added an adaptive viterbi "beam search"
      case VITERBI_FBEAMKL:
        ViterbiPathBeamKL lattice5 = viterbiPathBeamKL(input);
        return lattice5.output();


      default:
        throw new IllegalStateException ("Unknown CRF4 transuction type "+transductionType);
    }
  }

  public void print ()
  {
    print (new PrintWriter (new OutputStreamWriter (System.out), true));
  }

  // yyy
  public void print (PrintWriter out)
  {
    out.println ("*** CRF STATES ***");
    for (int i = 0; i < numStates (); i++) {
      State s = (State) getState (i);
      out.print ("STATE NAME=\"");
      out.print (s.name); out.print ("\" ("); out.print (s.destinations.length); out.print (" outgoing transitions)\n");
      out.print ("  "); out.print ("initialCost = "); out.print (s.initialCost); out.print ('\n');
      out.print ("  "); out.print ("finalCost = "); out.print (s.finalCost); out.print ('\n');
      out.println ("  transitions:");
      for (int j = 0; j < s.destinations.length; j++) {
        out.print ("    "); out.print (s.name); out.print (" -> "); out.println (s.getDestinationState (j).getName ());
        for (int k = 0; k < s.weightsIndices[j].length; k++) {
          out.print ("        WEIGHTS = \"");
          int widx = s.weightsIndices[j][k];
          out.print (weightAlphabet.lookupObject (widx).toString ());
          out.print ("\"\n");
        }
      }
      out.println ();
    }

    out.println ("\n\n\n*** CRF WEIGHTS ***");
    for (int widx = 0; widx < weights.length; widx++) {
      out.println ("WEIGHTS NAME = " + weightAlphabet.lookupObject (widx));
      out.print (": <DEFAULT_FEATURE> = "); out.print (defaultWeights[widx]); out.print ('\n');
      SparseVector transitionWeights = weights[widx];
      if (transitionWeights.numLocations () == 0)
        continue;
      RankedFeatureVector rfv = new RankedFeatureVector (inputAlphabet, transitionWeights);
      for (int m = 0; m < rfv.numLocations (); m++) {
        double v = rfv.getValueAtRank (m);
        int index = rfv.indexAtLocation (rfv.getIndexAtRank (m));
        Object feature = inputAlphabet.lookupObject (index);
        if (v != 0) {
          out.print (": "); out.print (feature); out.print (" = "); out.println (v);
        }
      }
    }

    out.flush ();
  }


	// Java question:
	// If I make a non-static inner class CRF.Trainer,
	// can that class by subclassed in another .java file,
	// and can that subclass still have access to all the CRF's
	// instance variables?
  // ANSWER: Yes and yes, but you have to use special syntax in the subclass ctor (see mallet-dev archive) -cas

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

		if (useSparseWeights) {
			setWeightsDimensionAsIn (ilist);			 
		} else {
			setWeightsDimensionDensely ();
		}
		
		MaximizableCRF mc = new MaximizableCRF (ilist, this);
		//Maximizer.ByGradient minimizer = new ConjugateGradient (0.001);
		Maximizer.ByGradient maximizer = new LimitedMemoryBFGS();

		int i;
		boolean continueTraining = true;
		boolean converged = false;
		logger.info ("CRF about to train with "+numIterations+" iterations");
		for (i = 0; i < numIterations; i++) {
			try {
                // CPAL - added this to alter forward backward beam parameters based on iteration
                setCurIter(i);  // CPAL - this resets the tctIter as well
				converged = maximizer.maximize (mc, 1);

                logger.info ("CRF took " + tctIter + " intermediate iterations");
                //if (i!=0) {
                //    if(tctIter>1) {
                        // increase the beam size
                //    }
                //}
                // CPAL - done

				logger.info ("CRF finished one iteration of maximizer, i="+i);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
				logger.info ("Catching exception; saying converged.");
				converged = true;
			}
			if (eval != null) {
				continueTraining = eval.evaluate (this, (converged || i == numIterations-1), i,
																					converged, mc.getValue(), ilist, validation, testing);
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
				logger.info ("CRF4 FeatureSelection now includes "+this.globalFeatureSelection.cardinality()+" features");
				klfi = null;
			}
			// This is done in CRF4.train() anyway
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
	
	public MaximizableCRF getMaximizableCRF (InstanceList ilist)
	{
		return new MaximizableCRF (ilist, this);
	}

	// Serialization
	// For CRF class

	private static final long serialVersionUID = 1;
  // Serial versions
  //  3: Add transduction type.
  //  4: Add weightsFrozen
	private static final int CURRENT_SERIAL_VERSION = 4;
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
    if (weightsFrozen != null) {
      size = weightsFrozen.length;
      out.writeInt (size);
      for (i = 0; i < size; i++)
       out.writeBoolean (weightsFrozen[i]);
    } else {
      out.writeInt (NULL_INTEGER);
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
		out.writeBoolean(cachedValueStale);
		out.writeBoolean(cachedGradientStale);
		out.writeBoolean(someTrainingDone);
		out.writeInt(featureInducers.size());
		for (i = 0; i < featureInducers.size(); i++) {
			out.writeObject(featureInducers.get(i));
		}
		out.writeBoolean(printGradient);
		out.writeBoolean (useSparseWeights);
    out.writeInt (transductionType);
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
			State s = (CRF4.State) in.readObject();
			states.add(s);
		}
		size = in.readInt();
		initialStates = new ArrayList();
		for (i=0; i<size; i++) {
			State s = (CRF4.State) in.readObject();
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

    // weightsFrozen appears in version 4
    if (version >= 4) {
      size = in.readInt ();
      if (size == NULL_INTEGER) {
        weightsFrozen = null;
      } else {
        weightsFrozen = new boolean [size];
        for (i = 0; i < size; i++)
          weightsFrozen[i] = (boolean)in.readBoolean ();
      }
    } else {
      weightsFrozen = new boolean [weights.length];
    }

    assertWeightsLength ();

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
		cachedValueStale = in.readBoolean();
		cachedGradientStale = in.readBoolean();
		someTrainingDone = in.readBoolean();
		size = in.readInt();
		featureInducers = new ArrayList();
		for (i = 0; i < size; i++) {
			featureInducers.add((FeatureInducer)in.readObject());
		}
		printGradient = in.readBoolean();

		if (version > 1) {
			useSparseWeights = in.readBoolean();
		} else {
			useSparseWeights = false;
		}

    if (version >= 3) {
      transductionType = in.readInt ();
    } else {
      transductionType = VITERBI;
    }

	}


  public ViterbiPath viterbiPath (Sequence inputSequence, boolean keepLattice)
  {
    return new ViterbiPath (inputSequence, null, keepLattice);
  }


  public class MaximizableCRF implements Maximizable.ByGradient, Serializable
	{
		InstanceList trainingSet;
		double cachedValue = -123456789;
		DenseVector cachedGradient;
		BitSet infiniteValues = null;
		int numParameters;
		CRF4 crf;

		protected MaximizableCRF (InstanceList ilist, CRF4 crf)
		{
			// Set up
			this.numParameters = 2 * numStates() + defaultWeights.length;
			for (int i = 0; i < weights.length; i++)
				numParameters += weights[i].numLocations();
			this.trainingSet = ilist;
			this.crf = crf;
			cachedGradient = new DenseVector (numParameters);

			// This resets and values that may have been in expecations and constraints
			setTrainable (true);

      // This is unfortunately necessary, b/c cachedValue & cachedValueStale not in same place!
      cachedValueStale = cachedGradientStale = true;
      
			gatherConstraints (ilist);
		}

		protected void gatherConstraints (InstanceList ilist)
		{
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
			//System.out.println ("testing Value and Gradient");
      //TestMaximizable.testValueAndGradientCurrentParameters (this);
		}

        // CPAL - added this to alter parameters of forward backward beam during optimization
        public void setCurIter(int curIter){
            this.crf.setCurIter(curIter);
        }

		public Matrix getNewMatrix () { return new DenseVector (numParameters); }

		// Negate initialCost and finalCost because the parameters are in
		// terms of "weights", not "values".
		
		public int getNumParameters () {return this.numParameters;}

		public void getParameters (double[] buffer)
		{
			if (buffer.length != getNumParameters ())
				buffer = new double [getNumParameters()];
			DenseVector parameters = new DenseVector (buffer, true);
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
			parameters.arrayCopyTo (0, buffer);
		}
		
		public double getParameter (int index) {
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
				throw new IllegalArgumentException ("index too high = "+index);
			}
		}
		
		public void setParameters (double [] buff) {
			assert (buff.length == getNumParameters());
			cachedValueStale = cachedGradientStale = true;
			DenseVector parameters = new DenseVector (buff, true);			
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

		public void setParameter (int index, double value) {
			cachedValueStale = cachedGradientStale = true;
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
				throw new IllegalArgumentException ("index too high = "+index);
			}
		}

		// log probability of the training sequence labels, and fill in expectations[]
		protected double getExpectationValue ()
		{
			// Instance values must either always or never be included in
			// the total values; we can't just sometimes skip a value
			// because it is infinite, this throws off the total values.
			boolean initializingInfiniteValues = false;
			double value = 0;
			if (infiniteValues == null) {
				infiniteValues = new BitSet ();
				initializingInfiniteValues = true;
			}
			// Calculate the value of each instance, and also fill in expectations
			double unlabeledCost, labeledCost, cost;

            // CPAL - added this to compute some stats for beam forward backward
            double meanStatesExpl[];
            double cMean;
            meanStatesExpl = new double[trainingSet.size()];
            tctIter++;
            // CPAL - done

			for (int ii = 0; ii < trainingSet.size(); ii++) {
				Instance instance = trainingSet.getInstance(ii);
				FeatureVectorSequence input = (FeatureVectorSequence) instance.getData();
				FeatureSequence output = (FeatureSequence) instance.getTarget();
				labeledCost = forwardBackward (input, output, false).getCost();
				//System.out.println ("labeledCost = "+labeledCost);
				if (Double.isInfinite (labeledCost))
					logger.warning (instance.getName().toString() + " has infinite labeled cost.\n"
													+(instance.getSource() != null ? instance.getSource() : ""));

                // CPAL - modified for beam forwardBackward
                // unlabeledCost = forwardBackward (input, true).getCost ();
                if (UseForwardBackwardBeam == true) {
                    unlabeledCost = forwardBackwardBeam (input, true).getCost ();
                    meanStatesExpl[ii] = MatrixOps.mean(getNstatesExpl());
                } else {
                    unlabeledCost = forwardBackward (input, true).getCost ();
                }
                // CPAL - done modified for beam forwardBackward

				//System.out.println ("unlabeledCost = "+unlabeledCost);

				if (Double.isInfinite (unlabeledCost))
					logger.warning (instance.getName().toString() + " has infinite unlabeled cost.\n"
													+(instance.getSource() != null ? instance.getSource() : ""));
				// Here cost is -log(conditional probability correct label sequence)
				cost = labeledCost - unlabeledCost;
				//System.out.println ("Instance "+ii+" CRF.MaximizableCRF.getCost = "+cost);
				if (Double.isInfinite(cost)) {
					logger.warning (instance.getName().toString() + " has infinite cost; skipping.");
					if (initializingInfiniteValues)
						infiniteValues.set (ii);
					else if (!infiniteValues.get(ii))
						throw new IllegalStateException ("Instance i used to have non-infinite value, "
																						 +"but now it has infinite value.");
					continue;
				} else {
					// Negate here because costs are -log probabilities, and we want to return a log probability
					value -= cost;
				}
			}

            // CPAL - output some beam stats
            if (UseForwardBackwardBeam == true) {
                cMean = MatrixOps.mean(meanStatesExpl);
                logger.info ("Mean states explored="+cMean);
            }
            // CPal - done

			return value;
		}

		// log probability of the training sequence labels and the prior over parameters
		public double getValue ()
		{
			if (cachedValueStale) {
				long startingTime = System.currentTimeMillis();
				cachedValue = 0;
				cachedGradientStale = true;
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
				// Negate here because cachedValue is being calculated as a "cost" here.
				cachedValue = -getExpectationValue ();

				// Incorporate prior on parameters
				if (usingHyperbolicPrior) {
					// Hyperbolic prior
					for (int i = 0; i < numStates(); i++) {
						State s = (State) getState (i);
						if (!Double.isInfinite(s.initialCost))
							cachedValue += (hyperbolicPriorSlope / hyperbolicPriorSharpness
														 * Math.log (Maths.cosh (hyperbolicPriorSharpness * -s.initialCost)));
						if (!Double.isInfinite(s.finalCost))
							cachedValue += (hyperbolicPriorSlope / hyperbolicPriorSharpness
														 * Math.log (Maths.cosh (hyperbolicPriorSharpness * -s.finalCost)));
					}
					for (int i = 0; i < weights.length; i++) {
						cachedValue += (hyperbolicPriorSlope / hyperbolicPriorSharpness
													 * Math.log (Maths.cosh (hyperbolicPriorSharpness * defaultWeights[i])));
						for (int j = 0; j < weights[i].numLocations(); j++) {
							double w = weights[i].valueAtLocation(j);
							if (!Double.isInfinite(w))
								cachedValue += (hyperbolicPriorSlope / hyperbolicPriorSharpness
															 * Math.log (Maths.cosh (hyperbolicPriorSharpness * w)));
						}
					}
				} else {
					// Gaussian prior
					double priorDenom = 2 * gaussianPriorVariance;
					for (int i = 0; i < numStates(); i++) {
						State s = (State) getState (i);
						if (!Double.isInfinite(s.initialCost))
							cachedValue += s.initialCost * s.initialCost / priorDenom;
						if (!Double.isInfinite(s.finalCost))
							cachedValue += s.finalCost * s.finalCost / priorDenom;
					}
					for (int i = 0; i < weights.length; i++) {
						if (!Double.isInfinite(defaultWeights[i]))
							cachedValue += defaultWeights[i] * defaultWeights[i] / priorDenom;
						for (int j = 0; j < weights[i].numLocations(); j++) {
							double w = weights[i].valueAtLocation (j);
							if (!Double.isInfinite(w))
								cachedValue += w * w / priorDenom;
						}
					}
				}
				// we've been calculating the cost up to now.
				// take the opposite to get the value
				cachedValue *= -1.0; 
				cachedValueStale = false;
				logger.info ("getValue() (loglikelihood) = "+cachedValue);
				logger.fine ("getValue() (loglikelihood) = "+cachedValue);
				//crf.print();
				long endingTime = System.currentTimeMillis();
				logger.info ("Inference milliseconds = "+(endingTime - startingTime));
			}
			return cachedValue;
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
	
		public void getValueGradient (double [] buffer)
		{
			// Gradient is (constraint - expectation - parameters/gaussianPriorVariance)
			// == (expectation + parameters/gaussianPriorVariance - constraint)
			// gradient points "up-hill", i.e. in the direction of higher value
			if (cachedGradientStale) {
				if (cachedValueStale)
					// This will fill in the this.expectation
					getValue ();
				assert (checkForNaN());
				int gi = 0;
				for (int i = 0; i < numStates(); i++) {
					State s = (State) getState (i);
          double initialPrior = (usingHyperbolicPrior ? (hyperbolicPriorSlope
                                                  * Maths.tanh (-s.initialCost)
                                                  * hyperbolicPriorSharpness)
                                                  : ((-s.initialCost) / gaussianPriorVariance));
          cachedGradient.setValue (gi++, (Double.isInfinite(s.initialCost)
																					? 0.0
																					: (s.initialExpectation
																						 + initialPrior
																						 - s.initialConstraint)));
          if (printGradient) {
            System.out.println ("CRF gradient initial cost ["+crf.getState(i).getName()+"] (gidx:"+(gi-1)+") = "
                          +s.initialExpectation + " (exp) - "
                          +s.initialConstraint + " (ctr) + "
                          +initialPrior+ "(reg) = "
                          +(cachedGradient.value(gi-1)));
          }


          double finalPrior = (usingHyperbolicPrior
                                                       ? (hyperbolicPriorSlope
                                                          * Maths.tanh (-s.finalCost)
                                                          * hyperbolicPriorSharpness)
                                                       : ((-s.finalCost) / gaussianPriorVariance));
          cachedGradient.setValue (gi++, (Double.isInfinite (s.finalCost)
																					? 0.0
																					: s.finalExpectation
																					+ finalPrior
																					- s.finalConstraint));
          if (printGradient) {
            System.out.println ("CRF gradient final cost  ["+crf.getState(i).getName()+"] (gidx:"+(gi-1)+") = "
                          +s.finalExpectation + " (exp) - "
                          +s.finalConstraint + " (ctr) + "
                          +finalPrior+ "(reg) = "
                          +(cachedGradient.value(gi-1)));
          }
				}
				if (usingHyperbolicPrior) {
					// Hyperbolic prior
					for (int i = 0; i < weights.length; i++) {
						if (weightsFrozen[i]) {
              gi += weights[i].numLocations () + 1;
              continue;
            }

            cachedGradient.setValue (gi++, (Double.isInfinite (defaultWeights[i])
																							? 0.0
																							: (defaultExpectations[i]
																								 + (hyperbolicPriorSlope
																										* Maths.tanh (defaultWeights[i])
																										* hyperbolicPriorSharpness)
																								 - defaultConstraints[i])));
						if (printGradient)
							System.out.println ("CRF gradient["+crf.getWeightsName(i)+"][<DEFAULT_FEATURE>] (gidx:"+(gi-1)+"="+cachedGradient.value(gi-1));
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
            if (weightsFrozen[i]) {
              gi += weights[i].numLocations () + 1;
              continue;
            }

						cachedGradient.setValue (gi++, (Double.isInfinite (defaultWeights[i])
																							? 0.0
																							: (defaultExpectations[i]
																								 + defaultWeights[i] / gaussianPriorVariance
																								 - defaultConstraints[i])));
						if (printGradient) {
							System.out.println ("CRF gradient["+crf.getWeightsName(i)+"][<DEFAULT_FEATURE>] (gidx:"+(gi-1)+") = "
                      +defaultExpectations[i] + " (exp) - "
                      +defaultConstraints[i] + " (ctr) + "
											+(defaultWeights[i] / gaussianPriorVariance)+ "(reg) = "
                      +(cachedGradient.value(gi-1)));
            }
              for (int j = 0; j < weights[i].numLocations(); j++) {
							cachedGradient.setValue (gi++, (Double.isInfinite (weights[i].valueAtLocation(j))
																							? 0.0
																							: (expectations[i].valueAtLocation(j)
																								 + weights[i].valueAtLocation(j) / gaussianPriorVariance
																								 - constraints[i].valueAtLocation(j))));
							if (printGradient)
								System.out.println ("CRF gradient["+crf.getWeightsName(i)+"]["
                        +inputAlphabet.lookupObject(constraints[i].indexAtLocation (j))+"] (gidx:"+(gi-1)+") ="+
                        + expectations[i].valueAtLocation (j)+" (exp) - "
                        + constraints[i].valueAtLocation (j)+" (ctr) + "
                        + (weights[i].valueAtLocation (j) / gaussianPriorVariance)+" (reg) = "
                        + cachedGradient.value(gi-1));
						}
					}
				}
				// xxx Show the feature with maximum gradient
				cachedGradientStale = false;
				assert (!cachedGradient.isNaN());
				// up to now we've been calculating the costGradient.
				// take the opposite to get the valueGradient
				cachedGradient.timesEquals (-1.0); // point uphill
			}
			if (buffer.length != this.numParameters)
				buffer = new double[this.numParameters];
			cachedGradient.arrayCopyTo (0, buffer);
			printGradient = false;
		}

		//Serialization of MaximizableCRF
		
		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;
	
		private void writeObject (ObjectOutputStream out) throws IOException {
			out.writeInt (CURRENT_SERIAL_VERSION);
			out.writeObject(trainingSet);
			out.writeDouble(cachedValue);
			out.writeObject(cachedGradient);
			out.writeObject(infiniteValues);
			out.writeInt(numParameters);
			out.writeObject(crf);
		}
		
		private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
			int version = in.readInt ();
			trainingSet = (InstanceList) in.readObject();
			cachedValue = in.readDouble();
			cachedGradient = (DenseVector) in.readObject();
			infiniteValues = (BitSet) in.readObject();
			numParameters = in.readInt();
			crf = (CRF4)in.readObject();
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
		State[] destinations;             // N.B. elements are null until getDestinationState(int) is called
		int[][] weightsIndices;								// contains indices into CRF.weights[],
		String[] labels;
		CRF4 crf;
		
		// No arg constructor so serialization works
		
		protected State() {
			super ();
		}
		
		
		protected State (String name, int index,
										 double initialCost, double finalCost,
										 String[] destinationNames,
										 String[] labelNames,
										 String[][] weightNames,
										 CRF4 crf)
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
			crf.cachedValueStale = crf.cachedGradientStale = true;
		}
		
		
		public void print ()
		{
			System.out.println ("State #"+index+" \""+name+"\"");
			System.out.println ("initialCost="+initialCost+", finalCost="+finalCost);
			System.out.println ("#destinations="+destinations.length);
			for (int i = 0; i < destinations.length; i++)
				System.out.println ("-> "+destinationNames[i]);
		}
		
		public int numDestinations () { return destinations.length;}

		public String[] getWeightNames (int index) {
			int[] indices = this.weightsIndices[index];
			String[] ret = new String[indices.length];
			for (int i=0; i < ret.length; i++)
				ret[i] = crf.weightAlphabet.lookupObject(indices[i]).toString();
			return ret;
		}

    public void addWeight (int didx, String weightName) {
      int widx = crf.getWeightsIndex (weightName);
      weightsIndices[didx] = ArrayUtils.append (weightsIndices[didx], widx);
    }

    public String getLabelName (int index) {
      return labels [index];
    }

		public State getDestinationState (int index)
		{
			State ret;
			if ((ret = destinations[index]) == null) {
				ret = destinations[index] = (State) crf.name2state.get (destinationNames[index]);
				if (ret == null)
					throw new IllegalArgumentException ("this.name="+this.name+" index="+index+" destinationNames[index]="+destinationNames[index]+" name2state.size()="+ crf.name2state.size());
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

    public Transducer.TransitionIterator transitionIterator (FeatureVector fv, String output)
    {
      return new TransitionIterator (this, fv, output, crf);
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
			crf = (CRF4) in.readObject();
		}
		
	
	}


	protected static class TransitionIterator extends Transducer.TransitionIterator implements Serializable
	{
		State source;
		int index, nextIndex;
		protected double[] costs;
		// Eventually change this because we will have a more space-efficient
		// FeatureVectorSequence that cannot break out each FeatureVector
		FeatureVector input;
		CRF4 crf;

		public TransitionIterator (State source,
															 FeatureVectorSequence inputSeq,
															 int inputPosition,
															 String output, CRF4 crf)
		{
			this (source, (FeatureVector)inputSeq.get(inputPosition), output, crf);
		}

		protected TransitionIterator (State source,
		                              FeatureVector fv,
		                              String output, CRF4 crf)
		{
			this.source = source;
			this.crf = crf;
			this.input = fv;
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
						costs[transIndex] -= (crf.weights[swi].dotProduct (fv)
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
			crf = (CRF4) in.readObject();
		}


    public String describeTransition (double cutoff)
    {
      DecimalFormat f = new DecimalFormat ("0.###");
      StringBuffer buf = new StringBuffer ();
      buf.append ("Value: " + f.format (-getCost ()) + " <br />\n");

      try {
        int[] theseWeights = source.weightsIndices[index];
        for (int i = 0; i < theseWeights.length; i++) {
          int wi = theseWeights[i];
          SparseVector w = crf.weights[wi];

          buf.append ("WEIGHTS <br />\n" + crf.weightAlphabet.lookupObject (wi) + "<br />\n");
          buf.append ("  d.p. = "+f.format (w.dotProduct (input))+"<br />\n");

          double[] vals = new double[input.numLocations ()];
          double[] absVals = new double[input.numLocations ()];
          for (int k = 0; k < vals.length; k++) {
            int index = input.indexAtLocation (k);
            vals[k] = w.value (index) * input.value (index);
            absVals[k] = Math.abs (vals[k]);
          }

          buf.append ("DEFAULT " + f.format (crf.defaultWeights[wi]) + "<br />\n");
          RankedFeatureVector rfv = new RankedFeatureVector (crf.inputAlphabet, input.getIndices (), absVals);
          for (int rank = 0; rank < absVals.length; rank++) {
            int fidx = rfv.getIndexAtRank (rank);
            Object fname = crf.inputAlphabet.lookupObject (input.indexAtLocation (fidx));
            if (absVals[fidx] < cutoff) break; // Break looping over features
            if (vals[fidx] != 0) {
              buf.append (fname + " " + f.format (vals[fidx]) + "<br />\n");
            }
          }
        }
      } catch (Exception e) {
        System.err.println ("Error writing transition descriptions.");
        e.printStackTrace ();
        buf.append ("ERROR WHILE WRITING OUTPUT...\n");
      }

      return buf.toString ();
    }
  }
}


