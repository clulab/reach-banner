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
import java.util.logging.*;
import java.io.*;


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


public class CRF extends Transducer implements Serializable
{
	private static Logger logger =
	MalletLogger.getLogger(CRF.class.getName());

	static final double DEFAULT_GAUSSIAN_PRIOR_VARIANCE = 10.0;
	static final double DEFAULT_HYPERBOLIC_PRIOR_SLOPE = 0.2;
	static final double DEFAULT_HYPERBOLIC_PRIOR_SHARPNESS = 10.0;
	
	// The length of each weights[i] Vector is the size of the input
	// dictionary plus one; the additional column at the end is for the
	// "default feature".
	Alphabet inputAlphabet;
	Alphabet outputAlphabet;
	ArrayList states = new ArrayList ();
	ArrayList initialStates = new ArrayList ();
	HashMap name2state = new HashMap ();
	DenseVector[] weights, constraints, expectations;
	Alphabet weightAlphabet = new Alphabet ();
	boolean trainable = false;
	boolean gatheringConstraints = false;
	int defaultFeatureIndex;
	boolean usingHyperbolicPrior = false;
	double gaussianPriorVariance = DEFAULT_GAUSSIAN_PRIOR_VARIANCE;
	double hyperbolicPriorSlope = DEFAULT_HYPERBOLIC_PRIOR_SLOPE;
	double hyperbolicPriorSharpness = DEFAULT_HYPERBOLIC_PRIOR_SHARPNESS;
	private boolean cachedCostStale = true;
	private boolean cachedGradientStale = true;

        // "featureSelections" is on a per- weights[i] basis, and over-rides
        // (permanently disabiling) FeatureInducer's and
        // setWeightsDimensionsAsIn() from using these features on these transitions
        FeatureSelection[] featureSelections;

	// xxx temporary hack
	public boolean printGradient = false;

	public CRF (Pipe inputPipe, Pipe outputPipe)
	{
		this.inputPipe = inputPipe;
		this.outputPipe = outputPipe;
		this.inputAlphabet = inputPipe.getDataAlphabet();
		this.outputAlphabet = inputPipe.getTargetAlphabet();
		this.defaultFeatureIndex = inputAlphabet.size();
		//inputAlphabet.stopGrowth();
	}
	
	public CRF (Alphabet inputAlphabet,
							Alphabet outputAlphabet)
	{
		inputAlphabet.stopGrowth();
		logger.info ("CRF input dictionary size = "+inputAlphabet.size());
		//xxx outputAlphabet.stopGrowth();
		this.inputAlphabet = inputAlphabet;
		this.outputAlphabet = outputAlphabet;
		this.defaultFeatureIndex = inputAlphabet.size();
	}

	// xxx Remove this method?
	/** Create a new CRF sharing Alphabet and other attributes, but possibly
			having a larger weights array. */
	private CRF (CRF initialCRF) {
		logger.info ("new CRF. Old weight size = "+initialCRF.weights[0].singleSize()+
												" New weight size = "+initialCRF.inputAlphabet.size());
		this.inputAlphabet = initialCRF.inputAlphabet;
		this.outputAlphabet = initialCRF.outputAlphabet;
		this.states = new ArrayList (initialCRF.states.size());
		this.inputPipe = initialCRF.inputPipe;
		this.outputPipe = initialCRF.outputPipe;
		this.defaultFeatureIndex = initialCRF.defaultFeatureIndex;
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

	public Alphabet getInputAlphabet () { return inputAlphabet; }
	public Alphabet getOutputAlphabet () { return outputAlphabet; }
	
	public void setUseHyperbolicPrior (boolean f) { usingHyperbolicPrior = f; }
	public void setHyperbolicPriorSlope (double p) { hyperbolicPriorSlope = p; }
	public void setHyperbolicPriorSharpness (double p) { hyperbolicPriorSharpness = p; }
	public double getUseHyperbolicPriorSlope () { return hyperbolicPriorSlope; }
	public double getUseHyperbolicPriorSharpness () { return hyperbolicPriorSharpness; }
	public void setGaussianPriorVariance (double p) { gaussianPriorVariance = p; }
	public double getGaussianPriorVariance () { return gaussianPriorVariance; }
	public int getDefaultFeatureIndex () { return defaultFeatureIndex;}
	
	public void addState (String name, double initialCost, double finalCost,
												String[] destinationNames,
												String[] labelNames,
												String[] weightNames)
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
/*        public void addStatesForThreeQuarterLabelsConnectedAsIn (InstanceList trainingSet)
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
*/
	
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
					destinationNames[k] = labels[j]+','+labels[k];
				addState (labels[i]+','+labels[j], 0.0, 0.0,
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
							(String)outputAlphabet.lookupObject(j)+','+(String)outputAlphabet.lookupObject(k);
						labels[destinationIndex] = (String)outputAlphabet.lookupObject(k);
						destinationIndex++;
					}
				addState ((String)outputAlphabet.lookupObject(i)+','+
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
						destinationNames[l] = labels[j]+','+labels[k]+','+labels[l];
					addState (labels[i]+','+labels[j]+','+labels[k], 0.0, 0.0,
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
	
	public void setWeights (int weightsIndex, DenseVector transitionWeights)
	{
		if (transitionWeights.singleSize() != (defaultFeatureIndex+1))
			throw new IllegalArgumentException ("Vector transitionWeights has incorrect size = "
																					+ transitionWeights.singleSize());
		if (weightsIndex >= weights.length || weightsIndex < 0)
			throw new IllegalArgumentException ("weightsIndex "+weightsIndex+" is out of bounds");
		weights[weightsIndex] = transitionWeights;
	}

	public void setWeights (String weightName, DenseVector transitionWeights)
	{
		setWeights (getWeightsIndex (weightName), transitionWeights);
	}

	public String getWeightsName (int weightIndex)
	{
		return (String) weightAlphabet.lookupObject (weightIndex);
	}

	public DenseVector getWeights (String weightName)
	{
		return weights[getWeightsIndex (weightName)];
	}

	public DenseVector getWeights (int weightIndex)
	{
		return weights[weightIndex];
	}

	/** Increase the size of the weights[] parameters to match (a new, larger)
			input Alphabet size */
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
	
	// Create a new weight Vector if weightName is new.
	public int getWeightsIndex (String weightName)
	{
		int wi = weightAlphabet.lookupIndex (weightName);
		if (wi == -1)
			throw new IllegalArgumentException ("Alphabet frozen, and no weight with name "+ weightName);
		if (weights == null) {
			assert (wi == 0);
			weights = new DenseVector[1];
			weights[0] = new DenseVector ((defaultFeatureIndex+1));
			setTrainable (false);
		} else if (wi == weights.length) {
			DenseVector[] newWeights = new DenseVector[weights.length+1];
			for (int i = 0; i < weights.length; i++)
				newWeights[i] = weights[i];
			newWeights[wi] = new DenseVector ((defaultFeatureIndex+1));
			weights = newWeights;
			setTrainable (false);
		}
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
				constraints = new DenseVector[weights.length];
				expectations = new DenseVector[weights.length];
				for (int i = 0; i < weights.length; i++) {
					constraints[i] = new DenseVector (weights[i].singleSize());
					expectations[i] = new DenseVector (weights[i].singleSize());
				}
			} else
				constraints = expectations = null;
			for (int i = 0; i < numStates(); i++)
				((State)getState(i)).setTrainable(f);
			trainable = f;
		}
	}

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
		DenseVector v = weights[source.weightsIndices[rowIndex]];
		v.setValue (featureIndex, value);
	}

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
		DenseVector v = weights[source.weightsIndices[rowIndex]];
		return v.value (featureIndex);
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

	public void print ()
	{
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < numStates(); i++) {
			State s = (State) getState (i);
			sb.append (s.name);	sb.append (" ("); sb.append (s.destinations.length); sb.append (" outgoing transitions)\n");
			sb.append ("  "); sb.append ("initialCost = "); sb.append (s.initialCost); sb.append ('\n');
			sb.append ("  "); sb.append ("finalCost = "); sb.append (s.finalCost); sb.append ('\n');
			for (int j = 0; j < s.destinations.length; j++) {
				sb.append (" -> ");	sb.append (s.destinations[j].name); sb.append ('\n');
				DenseVector transitionWeights = weights[s.weightsIndices[j]];
				RankedFeatureVector rfv = new RankedFeatureVector (inputAlphabet, transitionWeights);
				for (int k = 0; k < transitionWeights.singleSize(); k++) {
					double v = rfv.getValueAtRank(k);
					int index = rfv.getIndexAtRank(k);
					Object feature = index == defaultFeatureIndex ? "<DEFAULT_FEATURE>" : inputAlphabet.lookupObject (index);
					if (v != 0) {
						sb.append ("  ");
						sb.append (s.name); sb.append (" -> "); sb.append (s.destinations[j].name); sb.append (": ");
						sb.append (feature); sb.append (" = "); sb.append (v); sb.append ('\n');
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
		MinimizableCRF mc = new MinimizableCRF (ilist, this);
		//Minimizer.ByGradient minimizer = new ConjugateGradient (0.001);
		Minimizer.ByGradient minimizer = new LimitedMemoryBFGS();
		int i;
		boolean continueTraining = true;
		boolean converged = false;
		for (i = 0; i < numIterations; i++) {
			try {
				converged = minimizer.minimize (mc, 1);
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
			if (converged)
				break;
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
		int trainingIteration = 0;
		int numLabels = outputAlphabet.size();
		
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
			}
			boolean converged = this.train (theTrainingData, validationData, testingData,
																			eval, numIterationsBetweenFeatureInductions);
			trainingIteration += numIterationsBetweenFeatureInductions;

			logger.info ("Starting feature induction with "+inputAlphabet.size()+" features.");
			
			// Create the list of error tokens, for both unclustered and clustered feature induction
			InstanceList errorInstances = new InstanceList (trainingData.getDataAlphabet(),
																												trainingData.getTargetAlphabet());
			ArrayList errorLabelVectors = new ArrayList();
			InstanceList clusteredErrorInstances[][] = new InstanceList[numLabels][numLabels];
			ArrayList clusteredErrorLabelVectors[][] = new ArrayList[numLabels][numLabels];
			
			for (int i = 0; i < numLabels; i++)
				for (int j = 0; j < numLabels; j++) {
					clusteredErrorInstances[i][j] = new InstanceList (trainingData.getDataAlphabet(),
																														 trainingData.getTargetAlphabet());
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
						logger.info ("Doing feature induction for "+
																outputAlphabet.lookupObject(i)+" -> "+outputAlphabet.lookupObject(j));
						if (clusteredErrorInstances[i][j].size() < 20) {
							logger.info ("..skipping because only "+clusteredErrorInstances[i][j].size()+" instances.");
							continue;
						}
						int s = clusteredErrorLabelVectors[i][j].size();
						LabelVector[] lvs = new LabelVector[s];
						for (int k = 0; k < s; k++)
							lvs[k] = (LabelVector) clusteredErrorLabelVectors[i][j].get(k);
						klfi[i][j] = new FeatureInducer (new ExpGain.Factory (lvs),
																						 clusteredErrorInstances[i][j], 
																						 numFeaturesPerFeatureInduction);
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
						klfi[i][j].induceFeaturesFor (trainingData, false, false);
						klfi[i][j].induceFeaturesFor (testingData, false, false);
					}
				}
				klfi = null;
			} else if (true) {
				int s = errorLabelVectors.size();
				LabelVector[] lvs = new LabelVector[s];
				for (int i = 0; i < s; i++)
					lvs[i] = (LabelVector) errorLabelVectors.get(i);
				FeatureInducer klfi = new FeatureInducer (new ExpGain.Factory (lvs),
																									errorInstances, numFeaturesPerFeatureInduction);
				klfi.induceFeaturesFor (trainingData, false, false);
				klfi.induceFeaturesFor (testingData, false, false);
				klfi = null;
			} else {
				// Currently not ever used
				FeatureInducer igfi = new FeatureInducer (new InfoGain.Factory(), errorInstances,
																									numFeaturesPerFeatureInduction);
				igfi.induceFeaturesFor (trainingData, false, false);
				igfi.induceFeaturesFor (testingData, false, false);
				igfi = null;
			}
			this.growWeightsDimensionToInputAlphabet ();
		}
		return this.train (trainingData, validationData, testingData,
											 eval, numIterations - trainingIteration);
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
	private static final int CURRENT_SERIAL_VERSION = 0;
	static final int NULL_INTEGER = -1;

	/* Need to check for null pointers. */
	private void writeObject (ObjectOutputStream out) throws IOException {
		int i, size;
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject (inputAlphabet);
		out.writeObject (outputAlphabet);
		size = states.size();
		out.writeInt(size);
		for (i = 0; i<size; i++) {
			out.writeObject(states.get(i));
		}
		size = initialStates.size();
		out.writeInt(size);
		for (i = 0; i <size; i++) {
			out.writeObject(initialStates.get(i));
		}
		out.writeObject(name2state);
		if(weights != null) {
			size = weights.length;
			out.writeInt(size);
			for (i=0; i<size; i++) {
				out.writeObject(weights[i]);
			}
		}
		else {
			out.writeInt(NULL_INTEGER);
		}
		if(constraints != null) {
			size = constraints.length;
			out.writeInt(size);
			for (i=0; i<size; i++) {
				out.writeObject(constraints[i]);
			}
		}
		else {
			out.writeInt(NULL_INTEGER);
		}
		if (expectations != null) {
			size = expectations.length;
			out.writeInt(size);
			for (i=0; i<size; i++) {
				out.writeObject(expectations[i]);
			}
		}
		else {
			out.writeInt(NULL_INTEGER);
		}
		out.writeObject(weightAlphabet);
		out.writeBoolean(trainable);
		out.writeBoolean(gatheringConstraints);
		out.writeInt(defaultFeatureIndex);
		out.writeBoolean(usingHyperbolicPrior);
		out.writeDouble(gaussianPriorVariance);
		out.writeDouble(hyperbolicPriorSlope);
		out.writeDouble(hyperbolicPriorSharpness);
		out.writeBoolean(cachedCostStale);
		out.writeBoolean(cachedGradientStale);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int size, i;
		int version = in.readInt ();
		inputAlphabet = (Alphabet) in.readObject();
		outputAlphabet = (Alphabet) in.readObject();
		size = in.readInt();
		states = new ArrayList(size);
		for (i=0; i<size; i++) {
			State s = (CRF.State) in.readObject();
			states.add(s);
		}
		size = in.readInt();
		initialStates = new ArrayList();
		for (i=0; i<size; i++) {
			State s = (CRF.State) in.readObject();
			initialStates.add(s);
		}
		name2state = (HashMap) in.readObject();
		size = in.readInt();
		if (size == NULL_INTEGER) {
			weights = null;
		}
		else {
			weights = new DenseVector[size];
			for(i=0; i< size; i++) {
				weights[i] = (DenseVector) in.readObject();
			}
		}
		size = in.readInt();
		if (size == NULL_INTEGER) {
			constraints = null;
		}
		else {
			constraints = new DenseVector[size];
			for(i=0; i< size; i++) {
				constraints[i] = (DenseVector) in.readObject();
			}
		}
		size = in.readInt();
		if (size == NULL_INTEGER) {
			expectations = null;
		}
		else {
			expectations = new DenseVector[size];
			for(i=0; i< size; i++) {
				expectations[i] = (DenseVector)in.readObject();
			}
		}
		weightAlphabet = (Alphabet) in.readObject();
		trainable = in.readBoolean();
		gatheringConstraints = in.readBoolean();
		defaultFeatureIndex = in.readInt();
		usingHyperbolicPrior = in.readBoolean();
		gaussianPriorVariance = in.readDouble();
		hyperbolicPriorSlope = in.readDouble();
		hyperbolicPriorSharpness = in.readDouble();
		cachedCostStale = in.readBoolean();
		cachedGradientStale = in.readBoolean();
	}

	public class MinimizableCRF implements Minimizable.ByGradient, Serializable
	{
		InstanceList trainingSet;
		double cachedCost = -123456789;
		DenseVector cachedGradient;
		BitSet infiniteCosts = null;
		int numParameters;
		CRF crf;

		protected MinimizableCRF (InstanceList ilist, CRF crf)
		{
			// Set up
			this.numParameters = 2 * numStates() + weights.length * (defaultFeatureIndex+1);
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
			for (int i = 0; i < weights.length; i++)
				pi = parameters.arrayCopyFrom (pi, weights[i]);
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
			for (int i = 0; i < weights.length; i++)
				pi = parameters.arrayCopyTo (pi, weights[i]);
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
				Vector v = weights[index / (defaultFeatureIndex+1)];
				return v.singleValue (index % (defaultFeatureIndex+1));
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
				DenseVector v = weights[index / (defaultFeatureIndex+1)];
				v.setSingleValue (index % (defaultFeatureIndex+1), value);
			}
		}


		// Minus log probability of the training sequence labels
		public double getCost ()
		{
			if (cachedCostStale) {
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
				for (int i = 0; i < weights.length; i++)
					expectations[i].setAll (0.0);
				// Calculate the cost of each instance, and also fill in expectations
				double unlabeledCost, labeledCost, cost;
				for (int ii = 0; ii < trainingSet.size(); ii++) {
					Instance instance = trainingSet.getInstance(ii);
					FeatureVectorSequence input = (FeatureVectorSequence) instance.getData();
					FeatureSequence output = (FeatureSequence) instance.getTarget();
					labeledCost = forwardBackward (input, output, false).getCost();
					//System.out.println ("input size = "+input.size());
					//System.out.println ("labeledCost = "+labeledCost);
					if (Double.isInfinite (labeledCost))
						logger.warning (instance.getName().toString() + " has infinite labeled cost.\n"
														+(instance.getSource() != null ? instance.getSource() : ""));
					unlabeledCost = forwardBackward (input, true).getCost ();
					//System.out.println ("unlabeledCost = "+unlabeledCost); System.exit (0);
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
						for (int j = 0; j < weights[i].singleSize(); j++) {
							double w = weights[i].singleValue(j);
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
            DenseVector weightVector = weights[i];
            int singleSize = weightVector.singleSize();
            for (int j = 0; j < singleSize; j++) {
              double w = weightVector.singleValue( j );
              if (!Double.isInfinite( w )) {
                cachedCost += w * w / priorDenom;
              }
            }
          }
				}
				cachedCostStale = false;
				logger.info ("getCost() (-loglikelihood) = "+cachedCost);
				logger.fine ("getCost() (-loglikelihood) = "+cachedCost);
				//crf.print();
			}
			return cachedCost;
		}

		private boolean checkForNaN ()
		{
			for (int i = 0; i < weights.length; i++) {
				assert (!weights[i].isNaN());
				assert (constraints == null || !constraints[i].isNaN());
				assert (expectations == null || !expectations[i].isNaN());
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
					for (int i = 0; i < weights.length; i++)
						for (int j = 0; j < weights[i].singleSize(); j++) {
							cachedGradient.setValue (gi++, (Double.isInfinite (weights[i].singleValue(j))
																							? 0.0
																							: (expectations[i].singleValue(j)
																								 + (hyperbolicPriorSlope
																										* Maths.tanh (weights[i].singleValue(j))
																										* hyperbolicPriorSharpness)
																								 - constraints[i].singleValue(j))));
							if (printGradient)
								System.out.println ("CRF gradient["+crf.getWeightsName(i)+"]["+(j!=defaultFeatureIndex?inputAlphabet.lookupObject(j):"<DEFAULT_FEATURE>")+"]="+cachedGradient.value(gi-1));								
						}
				} else {
					// Gaussian prior
					for (int i = 0; i < weights.length; i++)
						for (int j = 0; j < weights[i].singleSize(); j++) {
							cachedGradient.setValue (gi++, (Double.isInfinite (weights[i].singleValue(j))
																							? 0.0
																							: (expectations[i].singleValue(j)
																								 + weights[i].singleValue(j) / gaussianPriorVariance
																								 - constraints[i].singleValue(j))));
							if (printGradient)
								System.out.println ("CRF gradient["+crf.getWeightsName(i)+"]["+(j!=defaultFeatureIndex?inputAlphabet.lookupObject(j):"<DEFAULT_FEATURE>")+"]="+cachedGradient.value(gi-1));
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

		// Accessors for constraints and expectations.... useful for unit tests
		//  These return a copies, so they aren't for "real" code

		public DenseVector[] getConstraints () { return constExpCopy (constraints); }
		public DenseVector[] getExpectations () { return constExpCopy (expectations); }
		
		private DenseVector[] constExpCopy (DenseVector[] stuff)
		{
			DenseVector[] values = new DenseVector [stuff.length];
			for (int i = 0; i < values.length; i++) {
				values [i] = (DenseVector) stuff[i].cloneMatrix ();
			}
			return values;
		}


		//Serialization of MinimizableCRF
		
		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;
	
		private void writeObject (ObjectOutputStream out) throws IOException {
			out.writeInt (CURRENT_SERIAL_VERSION);
			out.writeObject(trainingSet);
			out.writeDouble(cachedCost);
			out.writeObject(cachedGradient);
			out.writeObject((BitSet)infiniteCosts);
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
			crf = (CRF)in.readObject();
		}

	}



	public class State extends Transducer.State implements Serializable
	{
		// Parameters indexed by destination state, feature index
		double initialConstraint, initialExpectation;
		double finalConstraint, finalExpectation;
		String name;
		int index;
		String[] destinationNames;
		State[] destinations;
		int[] weightsIndices;								// contains indices into CRF.weights[],
		String[] labels;
		CRF crf;
		
		// No arg constructor so serialization works
		
		protected State() {
		}
		
		
		protected State (String name, int index,
										 double initialCost, double finalCost,
										 String[] destinationNames,
										 String[] labelNames,
										 String[] weightNames,
										 CRF crf)
		{
			assert (destinationNames.length == labelNames.length);
			assert (destinationNames.length == weightNames.length);
			this.name = name;
			this.index = index;
			this.initialCost = initialCost;
			this.finalCost = finalCost;
			this.destinationNames = destinationNames;
			this.destinations = new State[labelNames.length];
			this.weightsIndices = new int[labelNames.length];
			this.labels = new String[labelNames.length];
			this.destinations = new State[labelNames.length];
			this.crf = crf;
			for (int i = 0; i < labelNames.length; i++) {
				outputAlphabet.lookupIndex (labelNames[i]);
				this.labels[i] = labelNames[i];
				this.weightsIndices[i] = getWeightsIndex (weightNames[i]);
			}
			
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
			assert (crf.trainable);
			if (crf.gatheringConstraints)
				initialConstraint += count;
			else
				initialExpectation += count;
		}
		
		public void incrementFinalCount (double count)
		{
			//System.out.println ("incrementFinalCount "+(gatheringConstraints?"constraints":"expectations")+" state#="+this.index+" count="+count);
			assert (crf.trainable);
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
				for (i=0; i<size; i++){
					out.writeInt(weightsIndices[i]);
				}
			}
			size = (labels == null) ? NULL_INTEGER : labels.length;
			out.writeInt(size);
			if (size != NULL_INTEGER) {
				for (i=0; i<size; i++)
					out.writeObject(labels[i]);
//				out.writeObject (inputAlphabet); // this will cause error
//				out.writeObject (outputAlphabet);
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
				weightsIndices = new int[size];
				for (i=0; i<size; i++) {
					weightsIndices[i] =  in.readInt();
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
//				inputAlphabet = (Alphabet) in.readObject();
//				outputAlphabet = (Alphabet) in.readObject();
			}	else {
				labels = null;
			}
			crf = (CRF) in.readObject();
		}
		
	
	}


	protected  class TransitionIterator extends Transducer.TransitionIterator implements Serializable
	{
		State source;
		int index, nextIndex;
		double[] costs;
		// Eventually change this because we will have a more space-efficient
		// FeatureVectorSequence that cannot break out each FeatureVector
		FeatureVector input;
		CRF crf;
		
		public TransitionIterator (State source,
															 FeatureVectorSequence inputSeq,
															 int inputPosition,
															 String output, CRF crf)
		{
			this.source = source;
			this.crf = crf;
			this.input = (FeatureVector) inputSeq.get(inputPosition);
			this.costs = new double[source.destinations.length];
			double totalCost = 0;
			//for normalization, added by Fuchun Peng
			for (int transIndex = 0; transIndex < source.destinations.length; transIndex++) {
				// xxx Or do we want output.equals(...) here?
				if (output == null || output.equals(source.labels[transIndex])) {
					// Here is the dot product of the feature weights with the lambda weights
					// for one transition
					costs[transIndex] = -(inputSeq.dotProduct (inputPosition, crf.weights[source.weightsIndices[transIndex]])
																// include with implicit weight 1.0 the default feature
																+ crf.weights[source.weightsIndices[transIndex]].value (crf.defaultFeatureIndex));

//					System.out.println("costs: " + costs[transIndex]);
					
					totalCost = sumNegLogProb (totalCost, costs[transIndex]);
					assert (!Double.isNaN(costs[transIndex]));
				}
				else
					costs[transIndex] = INFINITE_COST;
			}

/*			// normalized cost
			for (int transIndex = 0; transIndex < source.destinations.length; transIndex++) {
				// xxx Or do we want output.equals(...) here?
				if (output == null || output.equals(source.labels[transIndex])) {
					// Here is the dot product of the feature weights with the lambda weights
					// for one transition

					costs[transIndex] -= totalCost;

//					System.out.println("costs: " + costs[transIndex]);
					
					assert (!Double.isNaN(costs[transIndex]));
				}
				else
					costs[transIndex] = INFINITE_COST;
			}
*/
		
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
			assert (crf.trainable);
			int weightsIndex = source.weightsIndices[index];
			if (crf.gatheringConstraints) {
				crf.constraints[weightsIndex].plusEquals (input, count);
				crf.constraints[weightsIndex].columnPlusEquals (crf.defaultFeatureIndex, count);
			} else {
				crf.expectations[weightsIndex].plusEquals (input, count);
				crf.expectations[weightsIndex].columnPlusEquals (crf.defaultFeatureIndex, count);
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
			crf = (CRF) in.readObject();
		}
		
	}
}


