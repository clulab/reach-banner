/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package edu.umass.cs.mallet.base.fst;

import edu.umass.cs.mallet.base.maximize.LimitedMemoryBFGS;
import edu.umass.cs.mallet.base.maximize.Maximizable;
import edu.umass.cs.mallet.base.maximize.Maximizer;
import edu.umass.cs.mallet.base.types.*;
import edu.umass.cs.mallet.base.util.ArrayUtils;
import edu.umass.cs.mallet.base.util.MalletLogger;
import edu.umass.cs.mallet.base.util.Maths;
import gnu.trove.TIntArrayList;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;


/**
 * A CRF trained by pseudolikelihood.  zt test time, the standard, globally-normalized
 *  model as used, as in the clssical work on pseudolikelihood.
 */
public class CRF_PL extends CRF4 implements Serializable
{
	private static Logger logger = MalletLogger.getLogger(CRF_PL.class.getName());

  private static final String LABEL_SEPARATOR = "^";

  public boolean dumpProbabilities = false;

	private boolean gatheringTrainingData = false;

  // After training sets have been gathered in the states, record which
  //   InstanceList we've gathers, so we don't double-count instances.
  private InstanceList trainingGatheredFor;

  // I hate that trainingSets can't be a member of MaximizableCRF... breaks thread-safety.
  //  It would be better if you could pass a hook to forwardBackward than this TransitionIterator business

  /* Indexed by [state@-1][state@+1] */
  private static class PLInstance {
    // Names of the true states involved
    String stateNameL;
    String stateNameC;
    String stateNameR;

    FeatureVector fv0;  // Feature Vector for L-->C transition
    FeatureVector fv1;  // Feature Vector for C-->R transition

    double weight;

    // Rest is for debugging

    int ip;  //  Position from original sequence
    int inum;

    public PLInstance (String stateNameL, String stateNameC, String stateNameR, FeatureVector fv0, FeatureVector fv1, int inum, int ip, double weight)
    {
      this.stateNameL = stateNameL;
      this.stateNameC = stateNameC;
      this.stateNameR = stateNameR;
      this.fv0 = fv0;
      this.fv1 = fv1;
      this.inum = inum;
      this.ip = ip;
      this.weight = weight;
    }
  }

  private List[][] trainingSets;
  private List startingInstances;
  private List endingInstances;

  // If true, use normalizatio at test time as in Toutanova et al.
  private boolean normalizeCosts = false;

  public CRF_PL (CRF4 crf)
  {
    super (crf.getInputAlphabet (), crf.getOutputAlphabet ());
    this.inputPipe = crf.inputPipe;
    this.outputPipe = crf.outputPipe;
    // To do local normalization, we use a second-order model with scores p(y_t | y_{t-1},y_{t+1},x_t)
    //   on the transition (y_t-1&y_t ==> y_t+1)
    // This trick helps when gathering training sets, too.
    makeSecondOrderStatesFrom (crf);
  }

  private void makeSecondOrderStatesFrom (CRF4 initialCrf)
  {
    weightAlphabet = new Alphabet ();
    for (int widx = 0; widx < initialCrf.weightAlphabet.size(); widx++) {
      getWeightsIndex (initialCrf.getWeightsName (widx));
    }

    for (int snum = 0; snum < initialCrf.numStates (); snum++) {
      CRF4.State s = (CRF4.State) initialCrf.getState (snum);
      for (int didx = 0; didx < s.destinationNames.length; didx++) {
        CRF4.State dest = initialCrf.getState (s.destinationNames[didx]);
        String newStateName = s.getName () + LABEL_SEPARATOR + dest.getName ();

        // create new destination names
        String[] newDests = new String[dest.destinationNames.length];
        for (int didx2 = 0; didx2 < dest.destinationNames.length; didx2++) {
          newDests[didx2] = dest.getName () + LABEL_SEPARATOR + dest.destinationNames[didx2];
        }

        // and new weight names. On
        String[][] weightNames = new String[dest.weightsIndices.length][];
        int[][] prevWeightIndices = new int [dest.weightsIndices.length][];
        for (int j = 0; j < weightNames.length; j++) {
          TIntArrayList weightIdxList = new TIntArrayList ();
          weightIdxList.add (dest.weightsIndices [j]);
          int[] widxs = weightIdxList.toNativeArray ();
          weightNames[j] = (String[]) initialCrf.weightAlphabet.lookupObjects (widxs, new String[widxs.length]);
          // Computing p(C|L,R) requires having the weights L-->C as well as C-->R
          prevWeightIndices [j] = (int[]) s.weightsIndices [didx].clone();
        }


        // better initial & final state handling?
        addState (newStateName, INFINITE_COST, dest.finalCost, newDests, dest.labels, weightNames);
        State theState = (State) getState (newStateName);
        theState.prevWeightsIndices = prevWeightIndices;
      }
    }

    // Add start states
    for (int snum = 0; snum < initialCrf.numStates (); snum++) {
      CRF4.State s = (CRF4.State) initialCrf.getState (snum);
      if (Double.isInfinite (s.getInitialCost ())) continue;
      String[] destNames = new String [s.destinationNames.length];
      String[][] weightNames = new String [s.weightsIndices.length][];
      for (int didx = 0; didx < s.destinationNames.length; didx++) {
        destNames [didx] = s.getName() + LABEL_SEPARATOR + s.destinationNames[didx];
        int[] widxs = s.weightsIndices[didx];
        weightNames [didx] = (String[]) weightAlphabet.lookupObjects (widxs, new String [widxs.length]);
      }
      addState (s.getName(), s.getInitialCost (), s.getFinalCost (), destNames, s.labels, weightNames);
    }
  }

  protected CRF4.State newState (String name, int index,
	                               double initialCost, double finalCost,
	                               String[] destinationNames,
	                               String[] labelNames,
	                               String[][] weightNames,
	                               CRF4 crf)
	{
		return new State (name, index, initialCost, finalCost,
		                  destinationNames, labelNames, weightNames, crf);
	}


	public boolean train (InstanceList training, InstanceList validation, InstanceList testing,
												TransducerEvaluator eval, int numIterations)
	{
		if (numIterations <= 0)
			return false;
		assert (training.size() > 0);

    initializeTrainingFor (training);

    if (false) {
			// Expectation-based placement of training data would go here.
			for (int i = 0; i < training.size(); i++) {
				Instance instance = training.getInstance(i);
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
				int[] indices = new int[numLocations];
				for (int j = 0; j < numLocations; j++) {
					indices[j] = weightsPresent[i].nextSetBit (j == 0 ? 0 : indices[j-1]+1);
					//System.out.println ("CRF4 has index "+indices[j]);
				}
				newWeights[i] = new IndexedSparseVector (indices, new double[numLocations],
				                                         numLocations, numLocations, false, false, false);
				newWeights[i].plusEqualsSparse (weights[i]);
			}
			weights = newWeights;
		}

		MaximizableCRF_PL maximizable = new MaximizableCRF_PL (training, this);
		// Gather the constraints
		maximizable.gatherExpectationsOrConstraints (true);
	  Maximizer.ByGradient maximizer = new LimitedMemoryBFGS();
//	  Maximizer.ByGradient maximizer = new GradientAscent ();
//    ((GradientAscent)maximizer).setLineMaximizer (new BoldDriverLineSearch ());
		int i;
		boolean continueTraining = true;
		boolean converged = false;
    boolean retry = true;
		logger.info ("CRF about to train with "+numIterations+" iterations");
		for (i = 0; i < numIterations; i++) {
			try {
				converged = maximizer.maximize (maximizable, 1);
				logger.info ("CRF finished one iteration of maximizer, i="+i);
        retry = true;
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
        if (retry && maximizer instanceof LimitedMemoryBFGS) {
          logger.info ("Catching expception "+e+"... retrying...");
          ((LimitedMemoryBFGS)maximizer).reset ();
          retry = false;
        } else {
				  logger.info ("Catching exception; saying converged.");
				  converged = true;
        }
			}
			if (eval != null) {
				continueTraining = eval.evaluate (this, (converged || i == numIterations-1), i,
																					converged, maximizable.getValue(), training, validation, testing);
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

  public void initializeTrainingFor (InstanceList training)
  {
    initOriginalStates ();

    // Allocate space for the parameters, and place transition FeatureVectors in
    // per-source-state InstanceLists.
    // Here, gatheringTrainingSets will be true, and these methods will result
    // in new InstanceList's being created in each source state, and the FeatureVectors
    // of their outgoing transitions to be added to them as the data field in the Instances.
    if (trainingGatheredFor != training) {
		  gatherTrainingSets (training);
    }
    if (useSparseWeights) {
			setWeightsDimensionAsIn (training);
		} else {
			setWeightsDimensionDensely ();
		}
  }


  // hack for debugging
  private int inum;

  public void gatherTrainingSets (InstanceList training)
  {
    if (trainingGatheredFor != null) {
      // It would be easy enough to support this, just got through all the states and set trainingSet to null.
      logger.warning ("Training sets already gathered.  Clearing....");
    }

    trainingSets = new ArrayList [numOriginalStates][numOriginalStates];
    startingInstances = new ArrayList ();
    endingInstances = new ArrayList ();

    trainingGatheredFor = training;
    gatheringTrainingData = true;
    for (int i = 0; i < training.size(); i++) {
        Instance instance = training.getInstance(i);
      inum = i;
        FeatureVectorSequence input = (FeatureVectorSequence) instance.getData();
        FeatureSequence output = (FeatureSequence) instance.getTarget();
        // Do it for the paths consistent with the labels...
        forwardBackward (input, output, true);
     }
     gatheringTrainingData = false;

    int total = 0;
    for (int i = 0; i < trainingSets.length; i++) {
      for (int j = 0; j < trainingSets[i].length; j++) {
        if (trainingSets[i][j] != null) {
          total += trainingSets[i][j].size();
        }
      }
    }
    logger.info ("Total local training instances = "+total);
  }


  public boolean train (InstanceList training, InstanceList validation, InstanceList testing,
												TransducerEvaluator eval, int numIterations,
												int numIterationsPerProportion,
												double[] trainingProportions)
	{
		throw new UnsupportedOperationException();
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
		throw new UnsupportedOperationException();
	}

	public MaximizableCRF getMaximizableCRF (InstanceList ilist)
	{
		return new MaximizableCRF_PL (ilist, this);
	}


  public void printInstanceLists ()
  {
    for (int i = 0; i < numOriginalStates; i++) {
      String s1 = getOriginalStateName (i);
      for (int j = 0; j < numOriginalStates; j++) {
        String s2 = getOriginalStateName (j);
        List training = trainingSets[i][j];
        System.out.println ("States ("+i+","+j+") : ("+s1+","+s2);
        if (training == null) {
          System.out.println ("No data");
          continue;
        }
        for (int inum = 0; inum < training.size(); inum++) {
          PLInstance inst = (PLInstance) training.get (inum);
          System.out.println ("State C : "+inst.stateNameC);
          System.out.println ("Instance "+inum+" weight: "+inst.weight);
          System.out.println ("FV0 is\n"+inst.fv0);
          System.out.println ("FV1 is\n"+inst.fv1);
        }
      }
    }
  }


  public static class State extends CRF4.State implements Serializable
	{

    // The weights used by the previous feature vector
    int[][] prevWeightsIndices;

		protected State (String name, int index,
										 double initialCost, double finalCost,
										 String[] destinationNames,
										 String[] labelNames,
										 String[][] weightNames,
										 CRF4 crf)
		{
		  super (name, index, initialCost, finalCost, destinationNames, labelNames, weightNames, crf);
		}

    public void setPrevWeightsIndices (int[][] prevWeightsIndices)
    {
      this.prevWeightsIndices = prevWeightsIndices;
    }

		// Necessary because the CRF4 implementation will return CRF4.TransitionIterator
		public Transducer.TransitionIterator transitionIterator (
			Sequence inputSequence, int inputPosition,
			Sequence outputSequence, int outputPosition)
		{
			if (inputPosition < 0 || outputPosition < 0)
				throw new UnsupportedOperationException ("Epsilon transitions not implemented.");
			if (inputSequence == null)
				throw new UnsupportedOperationException ("CRFs are not generative models; must have an input sequence.");
			return ((CRF_PL)crf).new TransitionIterator (
				this, (FeatureVectorSequence)inputSequence, inputPosition,
				(outputSequence == null ? null : (String)outputSequence.get(outputPosition)), crf);
		}


    public void incrementFinalCount (double count)
    {
      if (!((CRF_PL)crf).gatheringTrainingData) {
        super.incrementFinalCount (count);
      }
    }


    public void incrementInitialCount (double count)
    {
      if (!((CRF_PL)crf).gatheringTrainingData) {
        super.incrementInitialCount (count);
      }
    }


	}


	protected class TransitionIterator extends CRF4.TransitionIterator implements Serializable
	{
    // You need *two* feature vectors, not one, to compute PL.
    FeatureVector fv0;
    FeatureVector fv1;

    int ip;

    boolean isStart = false; // True if at first transition
    boolean isLast = false;  // True if at last transition

    public TransitionIterator (State source,
															 FeatureVectorSequence inputSeq,
															 int inputPosition,
															 String output, CRF4 memm)
		{
			super (source, inputSeq, inputPosition, output, memm);
      this.fv0 = inputSeq.getFeatureVector (inputPosition);
      if (inputPosition > 0)
        this.fv1 = inputSeq.getFeatureVector (inputPosition-1);
      this.ip = inputPosition;
      if (ip == 0) isStart = true;
      if (ip == inputSeq.size()) isLast = true;
		}

		public TransitionIterator (State source,
															 FeatureVector fv,
															 String output, CRF4 memm)
		{
			super (source, fv, output, memm);
		}

    public double getCost ()
    {
      if (normalizeCosts) {
        double cost = super.getCost ();
        double logZ = computeLocalLogZ (fv0, fv1, (State) source, (State) getDestinationState ());
        return cost - logZ;
      } else {
        return super.getCost ();
      }
    }

    // I'm going to regret writing a separate functio to do this. -cas
    public void incrementCount (double count)
    {
      if (((CRF_PL) crf).gatheringTrainingData) {
        if (count != 0) {
          State dest = (State) getDestinationState ();

          if (count != 1) {
            System.out.println ("Huh?");
          }

          String stateNameL = leftNameOfState ((State) source);
          String stateNameR = rightNameOfState (dest);
          int stateL = leftIndexFromStateName (crf, source.getName ());
          int stateR = rightIndexFromStateName (crf, dest.getName ());

          // Create the source state's trainingSet if it doesn't exist yet.
          if (trainingSets[stateL][stateR] == null)
          // New InstanceList with a null pipe, because it doesn't do any processing of input.
           trainingSets[stateL][stateR] = new ArrayList ();
          // xxx We should make sure we don't add duplicates (through a second call to setWeightsDimenstion..!
          // xxx Note that when the training data still allows ambiguous outgoing transitions
          // this will add the same FV more than once to the source state's trainingSet, each
          // with >1.0 weight.  Not incorrect, but inefficient.
//        System.out.println ("From: "+source.getName()+" ---> "+getOutput()+" : "+getInput());
          String stateNameC = rightNameOfState ((State) source);

          if (!isStart) {
            PLInstance inst = new PLInstance (stateNameL, stateNameC, stateNameR, fv0, fv1, inum, ip, count);
            trainingSets[stateL][stateR].add (inst);
          }

          if (isStart) {
            PLInstance inst = new PLInstance (null, stateNameL, stateNameC, fv0, null, inum, ip, count);
            startingInstances.add (inst);
          }

          if (isLast) {
            PLInstance inst = new PLInstance (stateNameC, stateNameR, null, fv1, null, inum, ip, count);
            endingInstances.add (inst);
          }

        }
      } else {
        super.incrementCount (count);
      }
    }
  }


  private double computeLocalLogZ (FeatureVector fv0, FeatureVector fv1, State biState1, State biState2)
  {
    // Let the transition sources state be L,C and the destinatin be C,R.
    //  To do the pseudolikelihood normalization, we need to normalizate over
    //  all values of C!  Messy, messy.
    // OTOH, this also means that for a single transition, each destiantion will
    //  have a different normalizing factor,which could be an advantage.
    double[] costs = new double [numOriginalStates];
    String stateNameL = leftNameOfState (biState1);
    String stateNameR = rightNameOfState (biState2);

    for (int i = 0; i < numOriginalStates; i++) {
      String stateNameC = getOriginalStateName (i);
      State twiddledState1 = (State) getState (stateNameL + LABEL_SEPARATOR + stateNameC);
      State twiddledState2 = (State) getState (stateNameC + LABEL_SEPARATOR + stateNameR);
      if ((twiddledState1 != null) && (twiddledState2 != null) && isTransition (twiddledState1, twiddledState2)) {
        costs [i] = transitionCost (fv0, fv1, twiddledState1, twiddledState2);
      } else {
        costs [i] = Double.NEGATIVE_INFINITY;
      }
    }

    double logZ = Maths.sumLogProb (costs);
    return logZ;
  }

  private boolean isTransition (State s1, State s2)
  {
    return (ArrayUtils.indexOf (s1.destinationNames, s2.getName()) >= 0);
  }

  private double transitionCost (FeatureVector fv0, FeatureVector fv1, State biState1, State biState2)
  {
    int widx = ArrayUtils.indexOf (biState1.destinationNames, biState2.getName());
    int[] weightIndices = biState1.weightsIndices [widx];
    int[] prevWeightIndices = biState1.prevWeightsIndices [widx];

    double sum = 0;
    sum += weightsDotProduct (weightIndices, fv1);
    sum += weightsDotProduct (prevWeightIndices, fv0);

    return sum;
  }

  private double weightsDotProduct (int[] weightIndices, FeatureVector fv)
  {
    double sum = 0;
    for (int wi = 0; wi < weightIndices.length; wi++) {
      int weightsIndex = weightIndices[wi];
      SparseVector w = weights [weightsIndex];
      sum += w.dotProduct (fv) + defaultWeights[weightsIndex];
    }
    return sum;
  }

  private double logTransitionProb (FeatureVector fv0, FeatureVector fv1, State biState1, State biState2)
  {
    double cost = transitionCost (fv0, fv1, biState1, biState2);
    double logZ = computeLocalLogZ (fv0, fv1, biState1, biState2);
    return cost - logZ;
  }





  // Decoding the 2nd-order state names

    int leftIndexFromStateName (CRF4 crf, String name)
    {
      String leftName = leftNameOfState (name);
      int idx = originalStateNames.lookupIndex (leftName, false);
      if (idx == -1)
        throw new IllegalStateException ("Could not extract left state name from "+name+"  Tried "+leftName);
      return idx;
    }

  private String leftNameOfState (State state) { return leftNameOfState (state.getName ()); }

  private String leftNameOfState (String name)
  {
    int leftIdx = name.indexOf (LABEL_SEPARATOR);
    if (leftIdx < 0) {
      return name;
    } else {
      String leftName = name.substring (0, leftIdx);
      return leftName;
    }
  }

    int rightIndexFromStateName (CRF4 crf, String name)
    {
      String rightName = rightNameOfState (name);
      int idx = originalStateNames.lookupIndex (rightName, false);
      if (idx == -1)
        throw new IllegalStateException ("Could not extract left state name from "+name+"  Tried "+rightName);
      return idx;
    }

  private String rightNameOfState (State state) { return rightNameOfState (state.getName ()); }

  private String rightNameOfState (String name)
  {
    int leftIdx = name.indexOf (LABEL_SEPARATOR);
    String rightName = name.substring (leftIdx + 1);
    return rightName;
  }

  private int numOriginalStates;
  private Alphabet originalStateNames;

  private void initOriginalStates ()
  {
    originalStateNames = new Alphabet ();
    for (int snum = 0; snum < numStates (); snum++) {
      State state = (State) getState (snum);
      if (!higherOrderState(state)) continue;
      String stateL = leftNameOfState (state);
      originalStateNames.lookupIndex (stateL);
      String stateR = rightNameOfState (state);
      originalStateNames.lookupIndex (stateR);
    }
    numOriginalStates = originalStateNames.size();
  }

  private boolean higherOrderState (State state)
  {
      return state.getName().indexOf (LABEL_SEPARATOR) > 0;
  }

  private String getOriginalStateName (int idx)
  {
    return (String) originalStateNames.lookupObject (idx);
  }


	public class MaximizableCRF_PL extends MaximizableCRF implements Maximizable.ByGradient
	{

		protected MaximizableCRF_PL (InstanceList trainingData, CRF_PL memm)
		{
			super (trainingData, memm);
		}

		// if constraints=false, return log probability of the training labels
		protected double gatherExpectationsOrConstraints (boolean constraints)
		{
			double totalLogProb = 0;
			for (int i = 0; i < numOriginalStates; i++) {
				String stateNameL = getOriginalStateName (i);
        for (int j = 0; j < numOriginalStates; j++) {
          String stateNameR = getOriginalStateName (j);

          List training = trainingSets[i][j];

				  if (training == null) {
					  continue;
				  }

				for (int inum = 0; inum < training.size(); inum++) {
					PLInstance instance = (PLInstance) training.get (inum);
					double instWeight = instance.weight;
					FeatureVector fv0 = instance.fv0;
					FeatureVector fv1 = instance.fv1;

          // Compute the value

          // This way doesn't allow different labels than states, but I don't know what the best way to implement
          //   that is right now.
          String stateNameC = instance.stateNameC;
          State trueBiSource = concatState (stateNameL, stateNameC);
          State trueBiDest = concatState (stateNameC, stateNameR);
          double logLabelProb = logTransitionProb (fv0, fv1, trueBiSource, trueBiDest);
          totalLogProb += instWeight * logLabelProb;

          if (dumpProbabilities) {
            System.out.println ("Instance "+instance.inum+" pos "+instance.ip+" (w="+instWeight+") prob = "+Math.exp (logLabelProb));
          }

          // Now do the gradient

          if (constraints) {
            incrementConstraints (trueBiSource, trueBiDest, fv0, fv1, instWeight);
          } else {
            for (int stateC = 0; stateC < numOriginalStates; stateC++) {
              String twiddledNameC = getOriginalStateName (stateC);
              State biState1 = concatState (stateNameL, twiddledNameC);
              State biState2 = concatState (twiddledNameC, stateNameR);
              if ((biState1 != null) && (biState2 != null) && isTransition (biState1, biState2)) {
                double logProb = logTransitionProb (fv0, fv1, biState1, biState2);
                incrementExpectations (biState1, biState2, fv0, fv1, Math.exp (logProb) * instWeight);
              }
            }
          }
				}
        }
      }

      //xxx Now the starting and ending instances
      /*
      for (int i = 0; i < startingInstances.size(); i++) {
        PLInstance inst = (PLInstance) startingInstances.get (i);

      }
      */

      // Force initial & final costs to 0 ???
      for (int i = 0; i < crf.numStates(); i++) {
        State s = (State) crf.getState (i);
        s.initialExpectation = s.initialConstraint;
        s.finalExpectation = s.finalConstraint;
      }

			return totalLogProb;
		}

    private void incrementConstraints (State source, State target, FeatureVector fv0, FeatureVector fv1, double prob)
    {
      int targetIdx = ArrayUtils.indexOf (source.destinationNames, target.getName());
      int[] widxs = source.weightsIndices [targetIdx];
      for (int wi = 0; wi < widxs.length; wi++) {
        int widx = widxs[wi];
        constraints[widx].plusEqualsSparse (fv0, prob);
        defaultConstraints[widx] += prob;
      }
      widxs = source.prevWeightsIndices [targetIdx];
      for (int wi = 0; wi < widxs.length; wi++) {
        int widx = widxs[wi];
        constraints[widx].plusEqualsSparse (fv1, prob);
        defaultConstraints[widx] += prob;
      }
    }

    private void incrementExpectations (State source, State target, FeatureVector fv0, FeatureVector fv1, double prob)
    {
      int targetIdx = ArrayUtils.indexOf (source.destinationNames, target.getName());
      int[] widxs = source.weightsIndices [targetIdx];
      // Increment expectations for current transition
      for (int wi = 0; wi < widxs.length; wi++) {
        int widx = widxs[wi];
        expectations[widx].plusEqualsSparse (fv0, prob);
        defaultExpectations[widx] += prob;
      }
      // And previous transition
      widxs = source.prevWeightsIndices [targetIdx];
      for (int wi = 0; wi < widxs.length; wi++) {
        int widx = widxs[wi];
        expectations[widx].plusEqualsSparse (fv1, prob);
        defaultExpectations[widx] += prob;
      }
    }

    private State concatState (String stateNameL, String stateNameR)
    {
      String biStateName = stateNameL + LABEL_SEPARATOR + stateNameR;
      return (State) getState (biStateName);
    }

    // log probability of the training sequence labels, and fill in expectations[]
		protected double getExpectationValue ()
		{
			return gatherExpectationsOrConstraints (false);
		}

    protected void gatherConstraints (InstanceList ilist)
    {
      gatherExpectationsOrConstraints (true);
    }

	}
}
