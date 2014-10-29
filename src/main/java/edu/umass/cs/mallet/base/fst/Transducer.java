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

// Analogous to base.types.classify.Classifier

import java.util.logging.*;
import java.util.*;
import java.io.*;

import edu.umass.cs.mallet.base.pipe.Pipe;
//import edu.umass.cs.mallet.base.pipe.SerialPipe;
import edu.umass.cs.mallet.base.types.InstanceList;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.Sequence;
import edu.umass.cs.mallet.base.types.ArraySequence;
import edu.umass.cs.mallet.base.types.SequencePair;
import edu.umass.cs.mallet.base.types.SequencePairAlignment;
import edu.umass.cs.mallet.base.types.Label;
import edu.umass.cs.mallet.base.types.LabelAlphabet;
import edu.umass.cs.mallet.base.types.LabelVector;
import edu.umass.cs.mallet.base.types.DenseVector;
import edu.umass.cs.mallet.base.types.Alphabet;
import edu.umass.cs.mallet.base.types.MatrixOps;
import edu.umass.cs.mallet.base.util.MalletLogger;
import edu.umass.cs.mallet.base.util.search.*;

// Variable name key:
// "ip" = "input position"
// "op" = "output position"

public abstract class Transducer implements Serializable
{
	private static Logger logger = MalletLogger.getLogger(Transducer.class.getName());

	{
		// xxx Why isn't this resulting in printing the log messages?
		//logger.setLevel (Level.FINE);
		//logger.addHandler (new StreamHandler (System.out, new SimpleFormatter ()));
		//System.out.println ("Setting level to finer");
		//System.out.println ("level = " + logger.getLevel());
		//logger.warning ("Foooooo");
	}
	
	public static final double ZERO_COST = 0;
	public static final double INFINITE_COST = Double.POSITIVE_INFINITY;

	
	//private Stack availableTransitionIterators = new Stack ();

	// Serialization

	private static final long serialVersionUID = 1;
  // Version history:
  //  0: Initial
  //  1: Add pipes
  //  3: Add beam width
	private static final int CURRENT_SERIAL_VERSION = 3;
	private static final int NO_PIPE_VERSION = 0;

  private void writeObject (ObjectOutputStream out) throws IOException {
		int i, size;
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject(inputPipe);
		out.writeObject(outputPipe);
    out.writeInt (beamWidth);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int size, i;
		int version = in.readInt ();
		if (version == NO_PIPE_VERSION) {
			inputPipe = null;
			outputPipe = null;
		}
		else {
			inputPipe = (Pipe) in.readObject();
			outputPipe = (Pipe) in.readObject();
		}

    if (version < 3) {
      beamWidth = 50;
    } else {
      beamWidth = in.readInt ();
    }
	}
	
	public abstract static class State implements Serializable
	{
		protected double initialCost = 0;
		protected double finalCost = 0;

		public abstract String getName();
		public abstract int getIndex ();
		public double getInitialCost () { return initialCost; }
		public void setInitialCost (double c) { initialCost = c; }
		public double getFinalCost () { return finalCost; }
		public void setFinalCost (double c) { finalCost = c; }
		//public Transducer getTransducer () { return (Transducer)this; }
		//public abstract TransitionIterator transitionIterator (Object input);

		// Pass negative positions for a sequence to request "epsilon
		// transitions" for either input or output.  (-position-1) should be
		// the position in the sequence after which we are trying to insert
		// the espilon transition.
		public abstract TransitionIterator transitionIterator
		(Sequence input,	int inputPosition, Sequence output, int outputPosition);

		/*
		public abstract TransitionIterator transitionIterator {
			if (availableTransitionIterators.size() > 0)
				return ((TransitionIterator)availableTransitionIterators.pop()).initialize
					(State source, Sequence input,	int inputPosition, Sequence output, int outputPosition);
			else
				return newTransitionIterator
					(Sequence input,	int inputPosition, Sequence output, int outputPosition);
		}
		*/

		// Pass negative input position for a sequence to request "epsilon
		// transitions".  (-position-1) should be the position in the
		// sequence after which we are trying to insert the espilon
		// transition.
		public TransitionIterator transitionIterator (Sequence input,
																									int inputPosition) {
			return transitionIterator (input, inputPosition, null, 0);
		}

		// For generative transducers:
		// Return all possible transitions, independent of input
		public TransitionIterator transitionIterator () {
			return transitionIterator (null, 0, null, 0);
		}

		// For trainable transducers:
		public void incrementInitialCount (double count) {
			throw new UnsupportedOperationException (); }
		public void incrementFinalCount (double count) {
			throw new UnsupportedOperationException (); }
		

		// Serialization
		
		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;


		private void writeObject (ObjectOutputStream out) throws IOException {
			int i, size;
			out.writeInt (CURRENT_SERIAL_VERSION);
			out.writeDouble(initialCost);
			out.writeDouble(finalCost);
		}
	

		private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
			int size, i;
			int version = in.readInt ();
			initialCost = in.readDouble();
			finalCost = in.readDouble();
		}
	}

	public abstract static class TransitionIterator implements Iterator, Serializable
	{
		//public abstract void initialize (Sequence input, int inputPosition,
		//Sequence output, int outputPosition);
		public abstract boolean hasNext ();
		public int numberNext(){ return -1;}
		public abstract State nextState ();	// returns the destination state
		public Object next () { return nextState(); }
		public void remove () {
			throw new UnsupportedOperationException (); }
		public abstract Object getInput ();
		public abstract Object getOutput ();
		public abstract double getCost ();
		public abstract State getSourceState ();
		public abstract State getDestinationState ();
		// In future these will allow for transition that consume variable amounts of the sequences
		public int getInputPositionIncrement () { return 1; }
		public int getOutputPositionIncrement () { return 1; }
		//public abstract Transducer getTransducer () {return getSourceState().getTransducer();}
		// For trainable transducers:
		public void incrementCount (double count) {
			throw new UnsupportedOperationException (); }

		// Serialization

		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;
		
		
		private void writeObject (ObjectOutputStream out) throws IOException {
			int i, size;
			out.writeInt (CURRENT_SERIAL_VERSION);
		}
		
		private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
			int size, i;
			int version = in.readInt ();
		}

    // I hate that I need this; there's really no other way
    public String describeTransition (double cutoff) { return ""; }

	}

    // CPAL - these worked well for nettalk
    //private int beamWidth = 10;
    //private double KLeps = .005;
    boolean UseForwardBackwardBeam = false;
    private int beamWidth = 0;
    private double KLeps = 0;
    private double Rmin = 0.1;
    private double nstatesExpl[];
    private int curIter = 0;
    int tctIter = 0;    // The number of times we have been called this iteration
    private double curAvgNstatesExpl;

	/** A pipe that should produce a Sequence in the "data" slot, (and possibly one in the "target" slot also */
	protected Pipe inputPipe;

	/** A pipe that should expect a ViterbiPath in the "target" slot,
			and should produce something printable in the "source" slot that
			indicates the results of transduction. */
	protected Pipe outputPipe;

	public Pipe getInputPipe () { return inputPipe; }
	public Pipe getOutputPipe () { return outputPipe; }

  public int getBeamWidth ()
  {
    return beamWidth;
  }

  public void setBeamWidth (int beamWidth)
  {
    this.beamWidth = beamWidth;
  }

  public void setCurIter (int curIter)
  {
    this.curIter = curIter;
    this.tctIter = 0;
  }

  public void incIter ()
  {
    this.tctIter++;
  }

  public void setKLeps (double KLeps)
  {
    this.KLeps = KLeps;
  }

  public void setRmin (double Rmin) {
      this.Rmin = Rmin;
  }

  public double[] getNstatesExpl()
  {
      return nstatesExpl;
  }

  public void setUseForwardBackwardBeam (boolean state) {
      this.UseForwardBackwardBeam = state;
  }

	/** We aren't really a Pipe subclass, but this method works like Pipes' do. */
	public Instance pipe (Instance carrier)
	{
		carrier.setTarget(viterbiPath ((Sequence)carrier.getData()));
		return carrier;
	}

  /**
   * Converts the given sequence into another sequence according to this transducer.
   *  For exmaple, robabilistic transducer may do something like Viterbi here.
   *  Subclasses of transducer may specify that they only accept special kinds of sequence.
   * @param input Input sequence
   * @return Sequence output by this transudcer 
   */
	public Sequence transduce (Sequence input)
	{
    ViterbiPath lattice = viterbiPath (input);
    return lattice.output ();
	}

	public abstract int numStates ();
	public abstract State getState (int index);
	
	// Note that this method is allowed to return states with infinite initialCost.
	public abstract Iterator initialStateIterator ();

	// Some transducers are "generative", meaning that you can get a
	// sequence out of them without giving them an input sequence.  In
	// this case State.transitionIterator() should return all available
	// transitions, but attempts to obtain the input and cost fields may
	// throw an exception.
	// xxx Why could obtaining "cost" be a problem???
	public boolean canIterateAllTransitions () { return false; }

	// If true, this is a "generative transducer".  In this case
	// State.transitionIterator() should return transitions that have
	// valid input and cost fields.  True returned here should imply
	// that canIterateAllTransitions() is true.
	public boolean isGenerative () { return false; }

	public boolean isTrainable () { return false; }
	// If f is true, and it was already trainable, this has same effect as reset()
	public void setTrainable (boolean f) {
		if (f) throw new IllegalStateException ("Cannot be trainable."); }
	public boolean train (InstanceList instances) {
		throw new UnsupportedOperationException ("Not trainable."); }

	public double averageTokenAccuracy (InstanceList ilist)
	{
		double accuracy = 0;
		for (int i = 0; i < ilist.size(); i++) {
			Instance instance = ilist.getInstance(i);
			Sequence input = (Sequence) instance.getData();
			Sequence output = (Sequence) instance.getTarget();
			assert (input.size() == output.size());
			double pathAccuracy = viterbiPath(input).tokenAccuracy(output);
			accuracy += pathAccuracy;
			logger.info ("Transducer path accuracy = "+pathAccuracy);
		}
		return accuracy/ilist.size();
	}

	public double averageTokenAccuracy (InstanceList ilist, String fileName)
	{
		double accuracy = 0;
		PrintWriter out;
		File f = new File(fileName);
		try {
			out = new PrintWriter(new FileWriter(f));
		}
		catch (IOException e) {
			out = null;
		}
		for (int i = 0; i < ilist.size(); i++) {
			Instance instance = ilist.getInstance(i);
			Sequence input = (Sequence) instance.getData();
			Sequence output = (Sequence) instance.getTarget();
			assert (input.size() == output.size());
			double pathAccuracy = viterbiPath(input).tokenAccuracy(output, out);
			accuracy += pathAccuracy;
			logger.info ("Transducer path accuracy = "+pathAccuracy);
		}
		out.close();
		return accuracy/ilist.size();
	}

	// Treat the costs as if they are -log(probabilies); we will
	// normalize them if necessary
	public SequencePairAlignment generatePath ()
	{
		if (isGenerative() == false)
			throw new IllegalStateException ("Transducer is not generative.");
		ArrayList initialStates = new ArrayList ();
		Iterator iter = initialStateIterator ();
		while (iter.hasNext()) { initialStates.add (iter.next()); }
		// xxx Not yet finished.
		throw new UnsupportedOperationException ();
	}


	
	public Lattice forwardBackward (Sequence inputSequence)
	{
		return forwardBackward (inputSequence, null, false);
	}

	public Lattice forwardBackward (Sequence inputSequence, boolean increment)
	{
		return forwardBackward (inputSequence, null, increment);
	}
	
	public Lattice forwardBackward (Sequence inputSequence, Sequence outputSequence)
	{
		return forwardBackward (inputSequence, outputSequence, false);
	}

	public Lattice forwardBackward (Sequence inputSequence, Sequence outputSequence, boolean increment)
	{
		return forwardBackward (inputSequence, outputSequence, increment, null);
	}
	public Lattice forwardBackward (Sequence inputSequence, Sequence outputSequence, boolean increment,
																	LabelAlphabet outputAlphabet)
	{
    return forwardBackward (inputSequence, outputSequence, increment, false, outputAlphabet);
  }

    public Lattice forwardBackward (Sequence inputSequence, Sequence outputSequence, boolean increment,
                                    boolean saveXis, LabelAlphabet outputAlphabet)
    {
		// xxx We don't do epsilon transitions for now
		assert (outputSequence == null
						|| inputSequence.size() == outputSequence.size());
		return new Lattice (inputSequence, outputSequence, increment, saveXis, outputAlphabet);
	}

	// culotta: interface for constrained lattice
	/**
		 Create constrained lattice such that all paths pass through the
		 the labeling of <code> requiredSegment </code> as indicated by
		 <code> constrainedSequence </code>
		 @param inputSequence input sequence
		 @param outputSequence output sequence
		 @param requiredSegment segment of sequence that must be labelled
		 @param constrainedSequence lattice must have labels of this
		 sequence from <code> requiredSegment.start </code> to <code>
		 requiredSegment.end </code> correctly
	*/
	public Lattice forwardBackward (Sequence inputSequence,
																	Sequence outputSequence,
																	Segment requiredSegment,
																	Sequence constrainedSequence) {
		if (constrainedSequence.size () != inputSequence.size ())
			throw new IllegalArgumentException ("constrainedSequence.size [" + constrainedSequence.size () + "] != inputSequence.size [" + inputSequence.size () + "]"); 
		// constraints tells the lattice which states must emit which
		// observations.  positive values say all paths must pass through
		// this state index, negative values say all paths must _not_
		// pass through this state index.  0 means we don't
		// care. initialize to 0. include 1 extra node for start state.
		int [] constraints = new int [constrainedSequence.size() + 1];
		for (int c = 0; c < constraints.length; c++)
			constraints[c] = 0;
		for (int i=requiredSegment.getStart (); i <= requiredSegment.getEnd(); i++) {
			int si = stateIndexOfString ((String)constrainedSequence.get (i));
			if (si == -1)
				logger.warning ("Could not find state " + constrainedSequence.get (i) + ". Check that state labels match startTages and inTags, and that all labels are seen in training data.");
//				throw new IllegalArgumentException ("Could not find state " + constrainedSequence.get(i) + ". Check that state labels match startTags and InTags.");
			constraints[i+1] = si + 1;
		}
		// set additional negative constraint to ensure state after
		// segment is not a continue tag
		
		// xxx if segment length=1, this actually constrains the sequence
		// to B-tag (B-tag)', instead of the intended constraint of B-tag
		// (I-tag)'
		// the fix below is unsafe, but will have to do for now.
		// FIXED BELOW
/*		String endTag = (String) constrainedSequence.get (requiredSegment.getEnd ());
		if (requiredSegment.getEnd()+2 < constraints.length) {
			if (requiredSegment.getStart() == requiredSegment.getEnd()) { // segment has length 1
				if (endTag.startsWith ("B-")) {
					endTag = "I" + endTag.substring (1, endTag.length());
				}
				else if (!(endTag.startsWith ("I-") || endTag.startsWith ("0")))
					throw new IllegalArgumentException ("Constrained Lattice requires that states are tagged in B-I-O format.");
			}
			int statei = stateIndexOfString (endTag);
			if (statei == -1) // no I- tag for this B- tag
				statei = stateIndexOfString ((String)constrainedSequence.get (requiredSegment.getStart ()));
			constraints[requiredSegment.getEnd() + 2] = - (statei + 1);
		}
*/
		if (requiredSegment.getEnd() + 2 < constraints.length) { // if 
			String endTag = requiredSegment.getInTag().toString();
			int statei = stateIndexOfString (endTag);
			if (statei == -1)
				logger.fine ("Could not find state " + endTag + ". Check that state labels match startTags and InTags.");
			else
				constraints[requiredSegment.getEnd() + 2] = - (statei + 1);			
		}
		
		
		//		printStates ();
		logger.fine ("Segment:\n" + requiredSegment.sequenceToString () +
								 "\nconstrainedSequence:\n" + constrainedSequence +
								 "\nConstraints:\n");
		for (int i=0; i < constraints.length; i++) {
			logger.fine (constraints[i] + "\t");
		}
		logger.fine ("");
		return forwardBackward (inputSequence, outputSequence, constraints);
	}		
	
	public int stateIndexOfString (String s)
	{
		for (int i = 0; i < this.numStates(); i++) {
			String state = this.getState (i).getName();
			if (state.equals (s))
				return i;
	 	}
	 	return -1;		
	}
	
	private void printStates () {
		for (int i = 0; i < this.numStates(); i++) 
			logger.fine (i + ":" + this.getState (i).getName());
	}
		
	public void print () {
		logger.fine ("Transducer "+this);
		printStates();
	}

	public Lattice forwardBackward (Sequence inputSequence,
																	Sequence outputSequence, int [] constraints)
	{
		return new Lattice (inputSequence, outputSequence, false, null,
												constraints);
	}

	
	// Remove this method?
	// If "increment" is true, call incrementInitialCount, incrementFinalCount and incrementCount
	private Lattice forwardBackward (SequencePair inputOutputPair, boolean increment) {
		return this.forwardBackward (inputOutputPair.input(), inputOutputPair.output(), increment);
	}
	
	// xxx Include methods like this?
	// ...making random selections proportional to cost
	//public Transduction transduce (Object[] inputSequence)
	//{	throw new UnsupportedOperationException (); }
	//public Transduction transduce (Sequence inputSequence)
	//{	throw new UnsupportedOperationException (); }


	public class Lattice // ?? extends SequencePairAlignment, but there isn't just a single output!
	{
		// "ip" == "input position", "op" == "output position", "i" == "state index"
		double cost;
		Sequence input, output;
		LatticeNode[][] nodes;			 // indexed by ip,i
		int latticeLength;
		// xxx Now that we are incrementing here directly, there isn't
		// necessarily a need to save all these arrays...
    // (Actually, there are useful to have, and they can be turned off by
		// -log(probability) of being in state "i" at input position "ip"
		double[][] gammas;					 // indexed by ip,i
    double[][][] xis;            // indexed by ip,i,j; saved only if saveXis is true;

		LabelVector labelings[];			 // indexed by op, created only if "outputAlphabet" is non-null in constructor

		private LatticeNode getLatticeNode (int ip, int stateIndex)
		{
			if (nodes[ip][stateIndex] == null)
				nodes[ip][stateIndex] = new LatticeNode (ip, getState (stateIndex));
			return nodes[ip][stateIndex];
		}

		// You may pass null for output, meaning that the lattice
		// is not constrained to match the output
		protected Lattice (Sequence input, Sequence output, boolean increment)
		{
			this (input, output, increment, false, null);
		}

    // You may pass null for output, meaning that the lattice
    // is not constrained to match the output
    protected Lattice (Sequence input, Sequence output, boolean increment, boolean saveXis)
    {
      this (input, output, increment, saveXis, null);
    }

		// If outputAlphabet is non-null, this will create a LabelVector
		// for each position in the output sequence indicating the
		// probability distribution over possible outputs at that time
		// index
		protected Lattice (Sequence input, Sequence output, boolean increment, boolean saveXis, LabelAlphabet outputAlphabet)
		{
			if (false && logger.isLoggable (Level.FINE)) {
				logger.fine ("Starting Lattice");
				logger.fine ("Input: ");
				for (int ip = 0; ip < input.size(); ip++)
					logger.fine (" " + input.get(ip));
				logger.fine ("\nOutput: ");
				if (output == null)
					logger.fine ("null");
				else
					for (int op = 0; op < output.size(); op++)
						logger.fine (" " + output.get(op));
				logger.fine ("\n");
			}

			// Initialize some structures
			this.input = input;
			this.output = output;
			// xxx Not very efficient when the lattice is actually sparse,
			// especially when the number of states is large and the
			// sequence is long.
			latticeLength = input.size()+1;
			int numStates = numStates();
			nodes = new LatticeNode[latticeLength][numStates];
			// xxx Yipes, this could get big; something sparse might be better?
			gammas = new double[latticeLength][numStates];
			if (saveXis) xis = new double[latticeLength][numStates][numStates];

			double outputCounts[][] = null;
			if (outputAlphabet != null)
				outputCounts = new double[latticeLength][outputAlphabet.size()];

			for (int i = 0; i < numStates; i++) {
				for (int ip = 0; ip < latticeLength; ip++)
					gammas[ip][i] = INFINITE_COST;
        if (saveXis)
          for (int j = 0; j < numStates; j++)
					  for (int ip = 0; ip < latticeLength; ip++)
						  xis[ip][i][j] = INFINITE_COST;
			}

			// Forward pass
			logger.fine ("Starting Foward pass");
			boolean atLeastOneInitialState = false;
			for (int i = 0; i < numStates; i++) {
				double initialCost = getState(i).initialCost;
				//System.out.println ("Forward pass initialCost = "+initialCost);
				if (initialCost < INFINITE_COST) {
					getLatticeNode(0, i).alpha = initialCost;
					//System.out.println ("nodes[0][i].alpha="+nodes[0][i].alpha);
					atLeastOneInitialState = true;
				}
			}
			if (atLeastOneInitialState == false)
				logger.warning ("There are no starting states!");

			for (int ip = 0; ip < latticeLength-1; ip++)
				for (int i = 0; i < numStates; i++) {
					if (nodes[ip][i] == null || nodes[ip][i].alpha == INFINITE_COST)
						// xxx if we end up doing this a lot,
						// we could save a list of the non-null ones
						continue;
					State s = getState(i);
					TransitionIterator iter = s.transitionIterator (input, ip, output, ip);
					if (logger.isLoggable (Level.FINE))
						logger.fine (" Starting Foward transition iteration from state "
												 + s.getName() + " on input " + input.get(ip).toString()
												 + " and output "
												 + (output==null ? "(null)" : output.get(ip).toString()));
					while (iter.hasNext()) {
						State destination = iter.nextState();
						if (logger.isLoggable (Level.FINE))
							logger.fine ("Forward Lattice[inputPos="+ip
													 +"][source="+s.getName()
													 +"][dest="+destination.getName()+"]");
						LatticeNode destinationNode = getLatticeNode (ip+1, destination.getIndex());
						destinationNode.output = iter.getOutput();
						double transitionCost = iter.getCost();
						if (logger.isLoggable (Level.FINE))
							logger.fine ("transitionCost="+transitionCost
													 +" nodes["+ip+"]["+i+"].alpha="+nodes[ip][i].alpha
													 +" destinationNode.alpha="+destinationNode.alpha);
						destinationNode.alpha = sumNegLogProb (destinationNode.alpha,
																									 nodes[ip][i].alpha + transitionCost);
						//System.out.println ("destinationNode.alpha <- "+destinationNode.alpha);
					}
				}

			// Calculate total cost of Lattice.  This is the normalizer
			cost = INFINITE_COST;
			for (int i = 0; i < numStates; i++)
				if (nodes[latticeLength-1][i] != null) {
					// Note: actually we could sum at any ip index,
					// the choice of latticeLength-1 is arbitrary
					//System.out.println ("Ending alpha, state["+i+"] = "+nodes[latticeLength-1][i].alpha);
					//System.out.println ("Ending beta,  state["+i+"] = "+getState(i).finalCost);
					cost = sumNegLogProb (cost,
																(nodes[latticeLength-1][i].alpha + getState(i).finalCost));
				}
			// Cost is now an "unnormalized cost" of the entire Lattice
			//assert (cost >= 0) : "cost = "+cost;

			// If the sequence has infinite cost, just return.
			// Usefully this avoids calling any incrementX methods.
			// It also relies on the fact that the gammas[][] and .alpha and .beta values
			// are already initialized to values that reflect infinite cost
			// xxx Although perhaps not all (alphas,betas) exactly correctly reflecting?
			if (cost == INFINITE_COST)
				return;

			// Backward pass
			for (int i = 0; i < numStates; i++)
				if (nodes[latticeLength-1][i] != null) {
					State s = getState(i);
					nodes[latticeLength-1][i].beta = s.finalCost;
					gammas[latticeLength-1][i] =
						nodes[latticeLength-1][i].alpha + nodes[latticeLength-1][i].beta - cost;
					if (increment) {
						double p = Math.exp(-gammas[latticeLength-1][i]);
						assert (p < INFINITE_COST && !Double.isNaN(p))
							: "p="+p+" gamma="+gammas[latticeLength-1][i];
						s.incrementFinalCount (p);
					}
				}

			for (int ip = latticeLength-2; ip >= 0; ip--) {
				for (int i = 0; i < numStates; i++) {
					if (nodes[ip][i] == null || nodes[ip][i].alpha == INFINITE_COST)
						// Note that skipping here based on alpha means that beta values won't
						// be correct, but since alpha is infinite anyway, it shouldn't matter.
						continue;
					State s = getState(i);
					TransitionIterator iter = s.transitionIterator (input, ip, output, ip);
					while (iter.hasNext()) {
						State destination = iter.nextState();
						if (logger.isLoggable (Level.FINE))
							logger.fine ("Backward Lattice[inputPos="+ip
													 +"][source="+s.getName()
													 +"][dest="+destination.getName()+"]");
						int j = destination.getIndex();
						LatticeNode destinationNode = nodes[ip+1][j];
						if (destinationNode != null) {
							double transitionCost = iter.getCost();
							assert (!Double.isNaN(transitionCost));
							//							assert (transitionCost >= 0);  Not necessarily
							double oldBeta = nodes[ip][i].beta;
							assert (!Double.isNaN(nodes[ip][i].beta));
							nodes[ip][i].beta = sumNegLogProb (nodes[ip][i].beta,
																								 destinationNode.beta + transitionCost);
							assert (!Double.isNaN(nodes[ip][i].beta))
								: "dest.beta="+destinationNode.beta+" trans="+transitionCost+" sum="+(destinationNode.beta+transitionCost)
								+ " oldBeta="+oldBeta;
              double xi = nodes[ip][i].alpha + transitionCost + nodes[ip+1][j].beta - cost;
							if (saveXis) xis[ip][i][j] = xi;
							assert (!Double.isNaN(nodes[ip][i].alpha));
							assert (!Double.isNaN(transitionCost));
							assert (!Double.isNaN(nodes[ip+1][j].beta));
							assert (!Double.isNaN(cost));
							if (increment || outputAlphabet != null) {
								double p = Math.exp(-xi);
								assert (p < INFINITE_COST && !Double.isNaN(p)) : "xis["+ip+"]["+i+"]["+j+"]="+-xi;
								if (increment)
									iter.incrementCount (p);
								if (outputAlphabet != null) {
									int outputIndex = outputAlphabet.lookupIndex (iter.getOutput(), false);
									assert (outputIndex >= 0);
									// xxx This assumes that "ip" == "op"!
									outputCounts[ip][outputIndex] += p;
									//System.out.println ("CRF Lattice outputCounts["+ip+"]["+outputIndex+"]+="+p);
								}
							}
						}
					}
					gammas[ip][i] = nodes[ip][i].alpha + nodes[ip][i].beta - cost;
				}
			}
			if (increment)
				for (int i = 0; i < numStates; i++) {
					double p = Math.exp(-gammas[0][i]);
					assert (p < INFINITE_COST && !Double.isNaN(p));
					getState(i).incrementInitialCount (p);
				}
			if (outputAlphabet != null) {
				labelings = new LabelVector[latticeLength];
				for (int ip = latticeLength-2; ip >= 0; ip--) {
					assert (Math.abs(1.0-DenseVector.sum (outputCounts[ip])) < 0.000001);;
					labelings[ip] = new LabelVector (outputAlphabet, outputCounts[ip]);
				}
			}
		}

		// culotta: constructor for constrained lattice
		/** Create a lattice that constrains its transitions such that the
		 * <position,label> pairs in "constraints" are adhered
		 * to. constraints is an array where each entry is the index of
		 * the required label at that position. An entry of 0 means there
		 * are no constraints on that <position, label>. Positive values
		 * mean the path must pass through that state. Negative values
		 * mean the path must _not_ pass through that state. NOTE -
		 * constraints.length must be equal to output.size() + 1. A
		 * lattice has one extra position for the initial
		 * state. Generally, this should be unconstrained, since it does
		 * not produce an observation.
		*/
		protected Lattice (Sequence input, Sequence output, boolean increment, LabelAlphabet outputAlphabet, int [] constraints)
		{
			if (false && logger.isLoggable (Level.FINE)) {
				logger.fine ("Starting Lattice");
				logger.fine ("Input: ");
				for (int ip = 0; ip < input.size(); ip++)
					logger.fine (" " + input.get(ip));
				logger.fine ("\nOutput: ");
				if (output == null)
					logger.fine ("null");
				else
					for (int op = 0; op < output.size(); op++)
						logger.fine (" " + output.get(op));
				logger.fine ("\n");
			}

			// Initialize some structures
			this.input = input;
			this.output = output;
			// xxx Not very efficient when the lattice is actually sparse,
			// especially when the number of states is large and the
			// sequence is long.
			latticeLength = input.size()+1;
			int numStates = numStates();
			nodes = new LatticeNode[latticeLength][numStates];
			// xxx Yipes, this could get big; something sparse might be better?
			gammas = new double[latticeLength][numStates];
			// xxx Move this to an ivar, so we can save it?  But for what?
      // Commenting this out, because it's a memory hog and not used right now.
      //  Uncomment and conditionalize under a flag if ever needed. -cas
			// double xis[][][] = new double[latticeLength][numStates][numStates];
			double outputCounts[][] = null;
			if (outputAlphabet != null)
				outputCounts = new double[latticeLength][outputAlphabet.size()];

			for (int i = 0; i < numStates; i++) {
				for (int ip = 0; ip < latticeLength; ip++)
					gammas[ip][i] = INFINITE_COST;
       /* Commenting out xis -cas
				for (int j = 0; j < numStates; j++)
					for (int ip = 0; ip < latticeLength; ip++)
						xis[ip][i][j] = INFINITE_COST;
        */
			}

			// Forward pass
			logger.fine ("Starting Constrained Foward pass");

			// ensure that at least one state has initial cost less than Infinity
			// so we can start from there
			boolean atLeastOneInitialState = false;
			for (int i = 0; i < numStates; i++) {
				double initialCost = getState(i).initialCost;
				//System.out.println ("Forward pass initialCost = "+initialCost);
				if (initialCost < INFINITE_COST) {
					getLatticeNode(0, i).alpha = initialCost;
					//System.out.println ("nodes[0][i].alpha="+nodes[0][i].alpha);
					atLeastOneInitialState = true;
				}
			}

			if (atLeastOneInitialState == false)
				logger.warning ("There are no starting states!");
			for (int ip = 0; ip < latticeLength-1; ip++)
				for (int i = 0; i < numStates; i++) {
					logger.fine ("ip=" + ip+", i=" + i);
					// check if this node is possible at this <position,
					// label>. if not, skip it.
					if (constraints[ip] > 0) { // must be in state indexed by constraints[ip] - 1
						if (constraints[ip]-1 != i) {
							logger.fine ("Current state does not match positive constraint. position="+ip+", constraint="+(constraints[ip]-1)+", currState="+i);
 							continue;
						}
					}
		 			else if (constraints[ip] < 0) { // must _not_ be in state indexed by constraints[ip]
					 	if (constraints[ip]+1 == -i) {
							logger.fine ("Current state does not match negative constraint. position="+ip+", constraint="+(constraints[ip]+1)+", currState="+i);
 							continue;
						}
					}
					if (nodes[ip][i] == null || nodes[ip][i].alpha == INFINITE_COST) {
						// xxx if we end up doing this a lot,
					 	// we could save a list of the non-null ones
						if (nodes[ip][i] == null) logger.fine ("nodes[ip][i] is NULL");
						else if (nodes[ip][i].alpha == INFINITE_COST) logger.fine ("nodes[ip][i].alpha is Inf");
						logger.fine ("INFINITE cost or NULL...skipping");
						continue;
					}
					State s = getState(i);

					TransitionIterator iter = s.transitionIterator (input, ip, output, ip);
					if (logger.isLoggable (Level.FINE))
						logger.fine (" Starting Forward transition iteration from state "
												 + s.getName() + " on input " + input.get(ip).toString()
												 + " and output "
												 + (output==null ? "(null)" : output.get(ip).toString()));
					while (iter.hasNext()) {
						State destination = iter.nextState();
						boolean legalTransition = true;
						// check constraints to see if node at <ip,i> can transition to destination
						if (ip+1 < constraints.length && constraints[ip+1] > 0 && ((constraints[ip+1]-1) != destination.getIndex())) {
							logger.fine ("Destination state does not match positive constraint. Assigning infinite cost. position="+(ip+1)+", constraint="+(constraints[ip+1]-1)+", source ="+i+", destination="+destination.getIndex());
							legalTransition = false;
						}
						else if (((ip+1) < constraints.length) && constraints[ip+1] < 0 && (-(constraints[ip+1]+1) == destination.getIndex())) {
							logger.fine ("Destination state does not match negative constraint. Assigning infinite cost. position="+(ip+1)+", constraint="+(constraints[ip+1]+1)+", destination="+destination.getIndex());
							legalTransition = false;
						}

						if (logger.isLoggable (Level.FINE))
							logger.fine ("Forward Lattice[inputPos="+ip
													 +"][source="+s.getName()
													 +"][dest="+destination.getName()+"]");
						LatticeNode destinationNode = getLatticeNode (ip+1, destination.getIndex());
						destinationNode.output = iter.getOutput();
						double transitionCost = iter.getCost();
						if (legalTransition) {
							//if (logger.isLoggable (Level.FINE))
							logger.fine ("transitionCost="+transitionCost
													 +" nodes["+ip+"]["+i+"].alpha="+nodes[ip][i].alpha
													 +" destinationNode.alpha="+destinationNode.alpha);
							destinationNode.alpha = sumNegLogProb (destinationNode.alpha,
																									 nodes[ip][i].alpha + transitionCost);
							//System.out.println ("destinationNode.alpha <- "+destinationNode.alpha);
							logger.fine ("Set alpha of latticeNode at ip = "+ (ip+1) + " stateIndex = " + destination.getIndex() + ", destinationNode.alpha = " + destinationNode.alpha);
						}
						else {
							// this is an illegal transition according to our
							// constraints, so set its prob to 0 . NO, alpha's are
							// unnormalized costs...set to Inf //
							// destinationNode.alpha = 0.0;
//							destinationNode.alpha = INFINITE_COST;
							logger.fine ("Illegal transition from state " + i + " to state " + destination.getIndex() + ". Setting alpha to Inf");
						}
					}
				}

			// Calculate total cost of Lattice.  This is the normalizer
			cost = INFINITE_COST;
			for (int i = 0; i < numStates; i++)
				if (nodes[latticeLength-1][i] != null) {
					// Note: actually we could sum at any ip index,
					// the choice of latticeLength-1 is arbitrary
					//System.out.println ("Ending alpha, state["+i+"] = "+nodes[latticeLength-1][i].alpha);
					//System.out.println ("Ending beta,  state["+i+"] = "+getState(i).finalCost);
					if (constraints[latticeLength-1] > 0 && i != constraints[latticeLength-1]-1)
						continue;
					if (constraints[latticeLength-1] < 0 && -i == constraints[latticeLength-1]+1)
						continue;
					logger.fine ("Summing final lattice cost. state="+i+", alpha="+nodes[latticeLength-1][i].alpha + ", final cost = "+getState(i).finalCost);
					cost = sumNegLogProb (cost,
																(nodes[latticeLength-1][i].alpha + getState(i).finalCost));
				}
			// Cost is now an "unnormalized cost" of the entire Lattice
			//assert (cost >= 0) : "cost = "+cost;

			// If the sequence has infinite cost, just return.
			// Usefully this avoids calling any incrementX methods.
			// It also relies on the fact that the gammas[][] and .alpha and .beta values
			// are already initialized to values that reflect infinite cost
			// xxx Although perhaps not all (alphas,betas) exactly correctly reflecting?
			if (cost == INFINITE_COST)
				return;

			// Backward pass
			for (int i = 0; i < numStates; i++)
				if (nodes[latticeLength-1][i] != null) {
					State s = getState(i);
					nodes[latticeLength-1][i].beta = s.finalCost;
					gammas[latticeLength-1][i] =
						nodes[latticeLength-1][i].alpha + nodes[latticeLength-1][i].beta - cost;
					if (increment) {
						double p = Math.exp(-gammas[latticeLength-1][i]);
						assert (p < INFINITE_COST && !Double.isNaN(p))
							: "p="+p+" gamma="+gammas[latticeLength-1][i];
						s.incrementFinalCount (p);
					}
				}
			for (int ip = latticeLength-2; ip >= 0; ip--) {
				for (int i = 0; i < numStates; i++) {
					if (nodes[ip][i] == null || nodes[ip][i].alpha == INFINITE_COST)
						// Note that skipping here based on alpha means that beta values won't
						// be correct, but since alpha is infinite anyway, it shouldn't matter.
						continue;
					State s = getState(i);
					TransitionIterator iter = s.transitionIterator (input, ip, output, ip);
					while (iter.hasNext()) {
						State destination = iter.nextState();
						if (logger.isLoggable (Level.FINE))
							logger.fine ("Backward Lattice[inputPos="+ip
													 +"][source="+s.getName()
													 +"][dest="+destination.getName()+"]");
						int j = destination.getIndex();
						LatticeNode destinationNode = nodes[ip+1][j];
						if (destinationNode != null) {
							double transitionCost = iter.getCost();
							assert (!Double.isNaN(transitionCost));
							//							assert (transitionCost >= 0);  Not necessarily
							double oldBeta = nodes[ip][i].beta;
							assert (!Double.isNaN(nodes[ip][i].beta));
							nodes[ip][i].beta = sumNegLogProb (nodes[ip][i].beta,
																								 destinationNode.beta + transitionCost);
							assert (!Double.isNaN(nodes[ip][i].beta))
								: "dest.beta="+destinationNode.beta+" trans="+transitionCost+" sum="+(destinationNode.beta+transitionCost)
								+ " oldBeta="+oldBeta;
							// xis[ip][i][j] = nodes[ip][i].alpha + transitionCost + nodes[ip+1][j].beta - cost;
							assert (!Double.isNaN(nodes[ip][i].alpha));
							assert (!Double.isNaN(transitionCost));
							assert (!Double.isNaN(nodes[ip+1][j].beta));
							assert (!Double.isNaN(cost));
							if (increment || outputAlphabet != null) {
                double xi = nodes[ip][i].alpha + transitionCost + nodes[ip+1][j].beta - cost;
								double p = Math.exp(-xi);
								assert (p < INFINITE_COST && !Double.isNaN(p)) : "xis["+ip+"]["+i+"]["+j+"]="+-xi;
								if (increment)
									iter.incrementCount (p);
								if (outputAlphabet != null) {
									int outputIndex = outputAlphabet.lookupIndex (iter.getOutput(), false);
									assert (outputIndex >= 0);
									// xxx This assumes that "ip" == "op"!
									outputCounts[ip][outputIndex] += p;
									//System.out.println ("CRF Lattice outputCounts["+ip+"]["+outputIndex+"]+="+p);
								}
							}
						}
					}
					gammas[ip][i] = nodes[ip][i].alpha + nodes[ip][i].beta - cost;
				}
			}
			if (increment)
				for (int i = 0; i < numStates; i++) {
					double p = Math.exp(-gammas[0][i]);
					assert (p < INFINITE_COST && !Double.isNaN(p));
					getState(i).incrementInitialCount (p);
				}
			if (outputAlphabet != null) {
				labelings = new LabelVector[latticeLength];
				for (int ip = latticeLength-2; ip >= 0; ip--) {
					assert (Math.abs(1.0-DenseVector.sum (outputCounts[ip])) < 0.000001);;
					labelings[ip] = new LabelVector (outputAlphabet, outputCounts[ip]);
				}
			}
		}

		public double getCost () {
			assert (!Double.isNaN(cost));
			return cost; }

		// No, this.cost is an "unnormalized cost"
		//public double getProbability () { return Math.exp (-cost); }

		public double getGammaCost (int inputPosition, State s) {
			return gammas[inputPosition][s.getIndex()]; }

		public double getGammaProbability (int inputPosition, State s) {
			return Math.exp (-gammas[inputPosition][s.getIndex()]); }

    public double getXiProbability (int ip, State s1, State s2) {
      if (xis == null)
        throw new IllegalStateException ("xis were not saved.");

      int i = s1.getIndex ();
      int j = s2.getIndex ();
      return Math.exp (-xis[ip][i][j]);
    }

    public double getXiCost (int ip, State s1, State s2)
    {
      if (xis == null)
        throw new IllegalStateException ("xis were not saved.");

      int i = s1.getIndex ();
      int j = s2.getIndex ();
      return xis[ip][i][j];
    }

    public int length () { return latticeLength; }

    public double getAlpha (int ip, State s) {
      LatticeNode node = getLatticeNode (ip, s.getIndex ());
      return node.alpha;
    }

    public double getBeta (int ip, State s) {
       LatticeNode node = getLatticeNode (ip, s.getIndex ());
       return node.beta;
     }

		public LabelVector getLabelingAtPosition (int outputPosition)	{
			if (labelings != null)
				return labelings[outputPosition];
			return null;
		}

		// Q: We are a non-static inner class so this should be easy; but how?
		// A: By the following weird syntax -cas
		public Transducer getTransducer ()
		{
			return Transducer.this;
		}


		// A container for some information about a particular input position and state
		private class LatticeNode
		{
			int inputPosition;
			// outputPosition not really needed until we deal with asymmetric epsilon.
			State state;
			Object output;
			double alpha = INFINITE_COST;
			double beta = INFINITE_COST;
			LatticeNode (int inputPosition, State state)	{
				this.inputPosition = inputPosition;
				this.state = state;
				assert (this.alpha == INFINITE_COST);	// xxx Remove this check
			}
		}

	}	// end of class Lattice

    // ******************************************************************************
    // CPAL - NEW "BEAM" Version of Forward Backward
    // ******************************************************************************
    
    public BeamLattice forwardBackwardBeam (Sequence inputSequence)
    {
        return forwardBackwardBeam (inputSequence, null, false);
    }

    public BeamLattice forwardBackwardBeam (Sequence inputSequence, boolean increment)
    {
        return forwardBackwardBeam (inputSequence, null, increment);
    }

    public BeamLattice forwardBackwardBeam (Sequence inputSequence, Sequence outputSequence)
    {
        return forwardBackwardBeam (inputSequence, outputSequence, false);
    }

    public BeamLattice forwardBackwardBeam (Sequence inputSequence, Sequence outputSequence, boolean increment)
    {
        return forwardBackwardBeam (inputSequence, outputSequence, increment, null);
    }
    public BeamLattice forwardBackwardBeam (Sequence inputSequence, Sequence outputSequence, boolean increment,
                                                                    LabelAlphabet outputAlphabet)
    {
    return forwardBackwardBeam (inputSequence, outputSequence, increment, false, outputAlphabet);
  }

    public BeamLattice forwardBackwardBeam (Sequence inputSequence, Sequence outputSequence, boolean increment,
                                    boolean saveXis, LabelAlphabet outputAlphabet)
    {
        // xxx We don't do epsilon transitions for now
        assert (outputSequence == null
                        || inputSequence.size() == outputSequence.size());
        return new BeamLattice (inputSequence, outputSequence, increment, saveXis, outputAlphabet);
    }

    // culotta: interface for constrained lattice
    /**
         Create constrained lattice such that all paths pass through the
         the labeling of <code> requiredSegment </code> as indicated by
         <code> constrainedSequence </code>
         @param inputSequence input sequence
         @param outputSequence output sequence
         @param requiredSegment segment of sequence that must be labelled
         @param constrainedSequence lattice must have labels of this
         sequence from <code> requiredSegment.start </code> to <code>
         requiredSegment.end </code> correctly
    */
    public BeamLattice forwardBackwardBeam (Sequence inputSequence,
                                                                    Sequence outputSequence,
                                                                    Segment requiredSegment,
                                                                    Sequence constrainedSequence) {
        if (constrainedSequence.size () != inputSequence.size ())
            throw new IllegalArgumentException ("constrainedSequence.size [" + constrainedSequence.size () + "] != inputSequence.size [" + inputSequence.size () + "]");
        // constraints tells the lattice which states must emit which
        // observations.  positive values say all paths must pass through
        // this state index, negative values say all paths must _not_
        // pass through this state index.  0 means we don't
        // care. initialize to 0. include 1 extra node for start state.
        int [] constraints = new int [constrainedSequence.size() + 1];
        for (int c = 0; c < constraints.length; c++)
            constraints[c] = 0;
        for (int i=requiredSegment.getStart (); i <= requiredSegment.getEnd(); i++) {
            int si = stateIndexOfString ((String)constrainedSequence.get (i));
            if (si == -1)
                logger.warning ("Could not find state " + constrainedSequence.get (i) + ". Check that state labels match startTages and inTags, and that all labels are seen in training data.");
//				throw new IllegalArgumentException ("Could not find state " + constrainedSequence.get(i) + ". Check that state labels match startTags and InTags.");
            constraints[i+1] = si + 1;
        }
        // set additional negative constraint to ensure state after
        // segment is not a continue tag

        // xxx if segment length=1, this actually constrains the sequence
        // to B-tag (B-tag)', instead of the intended constraint of B-tag
        // (I-tag)'
        // the fix below is unsafe, but will have to do for now.
        // FIXED BELOW
/*		String endTag = (String) constrainedSequence.get (requiredSegment.getEnd ());
		if (requiredSegment.getEnd()+2 < constraints.length) {
			if (requiredSegment.getStart() == requiredSegment.getEnd()) { // segment has length 1
				if (endTag.startsWith ("B-")) {
					endTag = "I" + endTag.substring (1, endTag.length());
				}
				else if (!(endTag.startsWith ("I-") || endTag.startsWith ("0")))
					throw new IllegalArgumentException ("Constrained Lattice requires that states are tagged in B-I-O format.");
			}
			int statei = stateIndexOfString (endTag);
			if (statei == -1) // no I- tag for this B- tag
				statei = stateIndexOfString ((String)constrainedSequence.get (requiredSegment.getStart ()));
			constraints[requiredSegment.getEnd() + 2] = - (statei + 1);
		}
*/
        if (requiredSegment.getEnd() + 2 < constraints.length) { // if
            String endTag = requiredSegment.getInTag().toString();
            int statei = stateIndexOfString (endTag);
            if (statei == -1)
                throw new IllegalArgumentException ("Could not find state " + endTag + ". Check that state labels match startTags and InTags.");
            constraints[requiredSegment.getEnd() + 2] = - (statei + 1);
        }


        //		printStates ();
        logger.fine ("Segment:\n" + requiredSegment.sequenceToString () +
                                 "\nconstrainedSequence:\n" + constrainedSequence +
                                 "\nConstraints:\n");
        for (int i=0; i < constraints.length; i++) {
            logger.fine (constraints[i] + "\t");
        }
        logger.fine ("");
        return forwardBackwardBeam (inputSequence, outputSequence, constraints);
    }

/*    public int stateIndexOfString (String s)
    {
        for (int i = 0; i < this.numStates(); i++) {
            String state = this.getState (i).getName();
            if (state.equals (s))
                return i;
         }
         return -1;
    }

    private void printStates () {
        for (int i = 0; i < this.numStates(); i++)
            logger.fine (i + ":" + this.getState (i).getName());
    }

    public void print () {
        logger.fine ("Transducer "+this);
        printStates();
    }*/

    public BeamLattice forwardBackwardBeam (Sequence inputSequence,
                                                                    Sequence outputSequence, int [] constraints)
    {
        return new BeamLattice (inputSequence, outputSequence, false, null,
                                                constraints);
    }


    // Remove this method?
    // If "increment" is true, call incrementInitialCount, incrementFinalCount and incrementCount
    private BeamLattice forwardBackwardBeam (SequencePair inputOutputPair, boolean increment) {
        return this.forwardBackwardBeam (inputOutputPair.input(), inputOutputPair.output(), increment);
    }
    
    public class BeamLattice // CPAL - like Lattice but using max-product to get the viterbiPath
    {
		// "ip" == "input position", "op" == "output position", "i" == "state index"
		double cost;
		Sequence input, output;
		LatticeNode[][] nodes;			 // indexed by ip,i
		int latticeLength;
        int curBeamWidth;               // CPAL - can be adapted if maximizer is confused

		// xxx Now that we are incrementing here directly, there isn't
		// necessarily a need to save all these arrays...
		// -log(probability) of being in state "i" at input position "ip"
		double[][] gammas;					 // indexed by ip,i
        double[][][] xis;            // indexed by ip,i,j; saved only if saveXis is true;

		LabelVector labelings[];			 // indexed by op, created only if "outputAlphabet" is non-null in constructor

		private LatticeNode getLatticeNode (int ip, int stateIndex)
		{
			if (nodes[ip][stateIndex] == null)
				nodes[ip][stateIndex] = new LatticeNode (ip, getState (stateIndex));
			return nodes[ip][stateIndex];
		}

		// You may pass null for output, meaning that the lattice
		// is not constrained to match the output
		protected BeamLattice (Sequence input, Sequence output, boolean increment)
		{
			this (input, output, increment, false, null);
		}

    // You may pass null for output, meaning that the lattice
    // is not constrained to match the output
    protected BeamLattice (Sequence input, Sequence output, boolean increment, boolean saveXis)
    {
      this (input, output, increment, saveXis, null);
    }

		// If outputAlphabet is non-null, this will create a LabelVector
		// for each position in the output sequence indicating the
		// probability distribution over possible outputs at that time
		// index
		protected BeamLattice (Sequence input, Sequence output, boolean increment, boolean saveXis, LabelAlphabet outputAlphabet)
		{
			if (false && logger.isLoggable (Level.FINE)) {
				logger.fine ("Starting Lattice");
				logger.fine ("Input: ");
				for (int ip = 0; ip < input.size(); ip++)
					logger.fine (" " + input.get(ip));
				logger.fine ("\nOutput: ");
				if (output == null)
					logger.fine ("null");
				else
					for (int op = 0; op < output.size(); op++)
						logger.fine (" " + output.get(op));
				logger.fine ("\n");
			}

			// Initialize some structures
			this.input = input;
			this.output = output;
			// xxx Not very efficient when the lattice is actually sparse,
			// especially when the number of states is large and the
			// sequence is long.
			latticeLength = input.size()+1;
			int numStates = numStates();
			nodes = new LatticeNode[latticeLength][numStates];
			// xxx Yipes, this could get big; something sparse might be better?
			gammas = new double[latticeLength][numStates];
			if (saveXis) xis = new double[latticeLength][numStates][numStates];

			double outputCounts[][] = null;
			if (outputAlphabet != null)
				outputCounts = new double[latticeLength][outputAlphabet.size()];

			for (int i = 0; i < numStates; i++) {
				for (int ip = 0; ip < latticeLength; ip++)
					gammas[ip][i] = INFINITE_COST;
        if (saveXis)
          for (int j = 0; j < numStates; j++)
					  for (int ip = 0; ip < latticeLength; ip++)
						  xis[ip][i][j] = INFINITE_COST;
			}

			// Forward pass
			logger.fine ("Starting Foward pass");
			boolean atLeastOneInitialState = false;
			for (int i = 0; i < numStates; i++) {
				double initialCost = getState(i).initialCost;
				//System.out.println ("Forward pass initialCost = "+initialCost);
				if (initialCost < INFINITE_COST) {
					getLatticeNode(0, i).alpha = initialCost;
					//System.out.println ("nodes[0][i].alpha="+nodes[0][i].alpha);
					atLeastOneInitialState = true;
				}
			}
			if (atLeastOneInitialState == false)
				logger.warning ("There are no starting states!");


            // CPAL - a sorted list for our beam experiments
            NBestSlist[] slists = new NBestSlist[latticeLength];
            // CPAL - used for stats
            nstatesExpl = new double[latticeLength];
            // CPAL - used to adapt beam if optimizer is getting confused
            // tctIter++;
            if(curIter == 0) {
                curBeamWidth = numStates;
            } else if(tctIter > 1 && curIter != 0) {
                //curBeamWidth = Math.min((int)Math.round(curAvgNstatesExpl*2),numStates);
                //System.out.println ("Doubling Minimum Beam Size to: "+curBeamWidth);
                curBeamWidth = beamWidth;
            } else {
                curBeamWidth = beamWidth;
            }

            // ************************************************************
			for (int ip = 0; ip < latticeLength-1; ip++) {

                    // CPAL - add this to construct the beam
                             // ***************************************************

                             // CPAL - sets up the sorted list
                             slists[ip] = new NBestSlist(numStates);
                             // CPAL - set the
                             slists[ip].setKLMinE(curBeamWidth);
                             slists[ip].setKLeps(KLeps);
                             slists[ip].setRmin(Rmin);

                             for(int i = 0 ; i< numStates ; i++){
                                  if (nodes[ip][i] == null || nodes[ip][i].alpha == INFINITE_COST)
                                     continue;
                                 //State s = getState(i);
                                 // CPAL - give the NB viterbi node the (cost, position)
                                 NBForBackNode cnode = new NBForBackNode(nodes[ip][i].alpha, i);
                                 slists[ip].push(cnode);

                             }

                             // CPAL - unlike std. n-best beam we now filter the list based
                             // on a KL divergence like measure
                             // ***************************************************
                             // use method which computes the cumulative log sum and
                             // finds the point at which the sum is within KLeps
                             int KLMaxPos=1;
                             int RminPos=1;


                             if(KLeps > 0) {
                                 KLMaxPos = slists[ip].getKLpos();
                                 nstatesExpl[ip]=(double)KLMaxPos;
                             } else if(KLeps == 0) {

                                 if(Rmin > 0) {
                                     RminPos = slists[ip].getTHRpos();
                                 } else {
                                     slists[ip].setRmin(-Rmin);
                                     RminPos = slists[ip].getTHRposSTRAWMAN();
                                 }
                                 nstatesExpl[ip]=(double)RminPos;

                             } else {
                                 // Trick, negative values for KLeps mean use the max of KL an Rmin
                                 slists[ip].setKLeps(-KLeps);
                                 KLMaxPos = slists[ip].getKLpos();

                                 //RminPos = slists[ip].getTHRpos();

                                 if(Rmin > 0) {
                                     RminPos = slists[ip].getTHRpos();
                                 } else {
                                     slists[ip].setRmin(-Rmin);
                                     RminPos = slists[ip].getTHRposSTRAWMAN();
                                 }

                                 if(KLMaxPos > RminPos) {
                                     nstatesExpl[ip]=(double)KLMaxPos;
                                 } else {
                                     nstatesExpl[ip]=(double)RminPos;
                                 }
                             }
                             //System.out.println(nstatesExpl[ip] + " ");

                // CPAL - contemplating setting values to something else
                int tmppos;
                for (int i = (int) nstatesExpl[ip]+1; i < slists[ip].size(); i++) {
                    tmppos = slists[ip].getPosByIndex(i);
                    nodes[ip][tmppos].alpha = INFINITE_COST;
                    nodes[ip][tmppos] = null;   // Null is faster and seems to work the same
                }
                // - done contemplation

				//for (int i = 0; i < numStates; i++) {
                for(int jj=0 ; jj< nstatesExpl[ip]; jj++) {

                    int i = slists[ip].getPosByIndex(jj);

                    // CPAL - dont need this anymore
                    // should be taken care of in the lists
					//if (nodes[ip][i] == null || nodes[ip][i].alpha == INFINITE_COST)
						// xxx if we end up doing this a lot,
						// we could save a list of the non-null ones
					//	continue;


					State s = getState(i);

					TransitionIterator iter = s.transitionIterator (input, ip, output, ip);
					if (logger.isLoggable (Level.FINE))
						logger.fine (" Starting Foward transition iteration from state "
												 + s.getName() + " on input " + input.get(ip).toString()
												 + " and output "
												 + (output==null ? "(null)" : output.get(ip).toString()));
					while (iter.hasNext()) {
						State destination = iter.nextState();
						if (logger.isLoggable (Level.FINE))
							logger.fine ("Forward Lattice[inputPos="+ip
													 +"][source="+s.getName()
													 +"][dest="+destination.getName()+"]");
						LatticeNode destinationNode = getLatticeNode (ip+1, destination.getIndex());
						destinationNode.output = iter.getOutput();
						double transitionCost = iter.getCost();
						if (logger.isLoggable (Level.FINE))
							logger.fine ("transitionCost="+transitionCost
													 +" nodes["+ip+"]["+i+"].alpha="+nodes[ip][i].alpha
													 +" destinationNode.alpha="+destinationNode.alpha);
						destinationNode.alpha = sumNegLogProb (destinationNode.alpha,
																									 nodes[ip][i].alpha + transitionCost);
						//System.out.println ("destinationNode.alpha <- "+destinationNode.alpha);
					}
				}
            }

            //System.out.println("Mean Nodes Explored: " + MatrixOps.mean(nstatesExpl));
            curAvgNstatesExpl = MatrixOps.mean(nstatesExpl);

			// Calculate total cost of Lattice.  This is the normalizer
			cost = INFINITE_COST;
			for (int i = 0; i < numStates; i++)
				if (nodes[latticeLength-1][i] != null) {
					// Note: actually we could sum at any ip index,
					// the choice of latticeLength-1 is arbitrary
					//System.out.println ("Ending alpha, state["+i+"] = "+nodes[latticeLength-1][i].alpha);
					//System.out.println ("Ending beta,  state["+i+"] = "+getState(i).finalCost);
					cost = sumNegLogProb (cost,
																(nodes[latticeLength-1][i].alpha + getState(i).finalCost));
				}
			// Cost is now an "unnormalized cost" of the entire Lattice
			//assert (cost >= 0) : "cost = "+cost;

			// If the sequence has infinite cost, just return.
			// Usefully this avoids calling any incrementX methods.
			// It also relies on the fact that the gammas[][] and .alpha and .beta values
			// are already initialized to values that reflect infinite cost
			// xxx Although perhaps not all (alphas,betas) exactly correctly reflecting?
			if (cost == INFINITE_COST)
				return;

			// Backward pass
			for (int i = 0; i < numStates; i++)
				if (nodes[latticeLength-1][i] != null) {
					State s = getState(i);
					nodes[latticeLength-1][i].beta = s.finalCost;
					gammas[latticeLength-1][i] =
						nodes[latticeLength-1][i].alpha + nodes[latticeLength-1][i].beta - cost;
					if (increment) {
						double p = Math.exp(-gammas[latticeLength-1][i]);
						assert (p < INFINITE_COST && !Double.isNaN(p))
							: "p="+p+" gamma="+gammas[latticeLength-1][i];
						s.incrementFinalCount (p);
					}
				}

			for (int ip = latticeLength-2; ip >= 0; ip--) {
				for (int i = 0; i < numStates; i++) {
					if (nodes[ip][i] == null || nodes[ip][i].alpha == INFINITE_COST)
						// Note that skipping here based on alpha means that beta values won't
						// be correct, but since alpha is infinite anyway, it shouldn't matter.
						continue;
					State s = getState(i);
					TransitionIterator iter = s.transitionIterator (input, ip, output, ip);
					while (iter.hasNext()) {
						State destination = iter.nextState();
						if (logger.isLoggable (Level.FINE))
							logger.fine ("Backward Lattice[inputPos="+ip
													 +"][source="+s.getName()
													 +"][dest="+destination.getName()+"]");
						int j = destination.getIndex();
						LatticeNode destinationNode = nodes[ip+1][j];
						if (destinationNode != null) {
							double transitionCost = iter.getCost();
							assert (!Double.isNaN(transitionCost));
							//							assert (transitionCost >= 0);  Not necessarily
							double oldBeta = nodes[ip][i].beta;
							assert (!Double.isNaN(nodes[ip][i].beta));
							nodes[ip][i].beta = sumNegLogProb (nodes[ip][i].beta,
																								 destinationNode.beta + transitionCost);
							assert (!Double.isNaN(nodes[ip][i].beta))
								: "dest.beta="+destinationNode.beta+" trans="+transitionCost+" sum="+(destinationNode.beta+transitionCost)
								+ " oldBeta="+oldBeta;
              double xi = nodes[ip][i].alpha + transitionCost + nodes[ip+1][j].beta - cost;
							if (saveXis) xis[ip][i][j] = xi;
							assert (!Double.isNaN(nodes[ip][i].alpha));
							assert (!Double.isNaN(transitionCost));
							assert (!Double.isNaN(nodes[ip+1][j].beta));
							assert (!Double.isNaN(cost));
							if (increment || outputAlphabet != null) {
								double p = Math.exp(-xi);
								assert (p < INFINITE_COST && !Double.isNaN(p)) : "xis["+ip+"]["+i+"]["+j+"]="+-xi;
								if (increment)
									iter.incrementCount (p);
								if (outputAlphabet != null) {
									int outputIndex = outputAlphabet.lookupIndex (iter.getOutput(), false);
									assert (outputIndex >= 0);
									// xxx This assumes that "ip" == "op"!
									outputCounts[ip][outputIndex] += p;
									//System.out.println ("CRF Lattice outputCounts["+ip+"]["+outputIndex+"]+="+p);
								}
							}
						}
					}
					gammas[ip][i] = nodes[ip][i].alpha + nodes[ip][i].beta - cost;
				}

                if(true){
                // CPAL - check the normalization
                double checknorm = INFINITE_COST;
			    for (int i = 0; i < numStates; i++)
				if (nodes[ip][i] != null) {
					// Note: actually we could sum at any ip index,
					// the choice of latticeLength-1 is arbitrary
					//System.out.println ("Ending alpha, state["+i+"] = "+nodes[latticeLength-1][i].alpha);
					//System.out.println ("Ending beta,  state["+i+"] = "+getState(i).finalCost);
					checknorm = sumNegLogProb (checknorm, gammas[ip][i]);
				}
                // System.out.println ("Check Gamma, sum="+checknorm);
                // CPAL - done check of normalization

                // CPAL - normalize
			    for (int i = 0; i < numStates; i++)
				if (nodes[ip][i] != null) {
					gammas[ip][i] = gammas[ip][i] - checknorm;
				}
                //System.out.println ("Check Gamma, sum="+checknorm);
                // CPAL - normalization
                }
			}
			if (increment)
				for (int i = 0; i < numStates; i++) {
					double p = Math.exp(-gammas[0][i]);
					assert (p < INFINITE_COST && !Double.isNaN(p));
					getState(i).incrementInitialCount (p);
				}
			if (outputAlphabet != null) {
				labelings = new LabelVector[latticeLength];
				for (int ip = latticeLength-2; ip >= 0; ip--) {
					assert (Math.abs(1.0-DenseVector.sum (outputCounts[ip])) < 0.000001);;
					labelings[ip] = new LabelVector (outputAlphabet, outputCounts[ip]);
				}
			}

		}

        // CPAL - a simple node holding a cost and position of the state
        private class NBForBackNode
        {
            double cost;
            int pos;
            NBForBackNode(double cost, int pos)
            {
                this.cost = cost;
                this.pos = pos;
            }
        }

        private class NBestSlist
        {
			ArrayList list = new ArrayList();
            int MaxElements;
            int KLMinElements;
            int KLMaxPos;
            double KLeps;
            double Rmin;

			NBestSlist(int MaxElements)
			{
                this.MaxElements = MaxElements;
			}

            boolean setKLMinE(int KLMinElements){
                this.KLMinElements = KLMinElements;
                return true;
            }

			int size()
			{
				return list.size();
			}

			boolean empty()
			{
				return list.isEmpty();
			}

			Object pop()
			{
				return list.remove(0);
			}

            int getPosByIndex(int ii){
                NBForBackNode tn = (NBForBackNode)list.get(ii);
                return tn.pos;
            }

            double getCostByIndex(int ii){
                NBForBackNode tn = (NBForBackNode)list.get(ii);
                return tn.cost;
            }

            void setKLeps(double KLeps){
                this.KLeps = KLeps;
            }

            void setRmin(double Rmin){
                this.Rmin = Rmin;
            }

            int getTHRpos(){

                 NBForBackNode tn;
                 double lc1, lc2;


                 tn = (NBForBackNode)list.get(0);
                 lc1 = tn.cost;
                 tn = (NBForBackNode)list.get(list.size()-1);
                 lc2 = tn.cost;

                 double minc = lc1 - lc2;
                 double mincTHR = minc - minc*Rmin;

                 for(int i=1;i<list.size();i++){
                    tn = (NBForBackNode)list.get(i);
                    lc1 = tn.cost - lc2;
                    if(lc1 > mincTHR){
                        return i+1;
                    }

                 }

                 return list.size();

            }

            int getTHRposSTRAWMAN(){

                 NBForBackNode tn;
                 double lc1, lc2;


                 tn = (NBForBackNode)list.get(0);
                 lc1 = tn.cost;

                 double mincTHR = -lc1*Rmin;

                 //double minc = lc1 - lc2;
                 //double mincTHR = minc - minc*Rmin;

                 for(int i=1;i<list.size();i++){
                    tn = (NBForBackNode)list.get(i);
                    lc1 = -tn.cost;
                    if(lc1 < mincTHR){
                        return i+1;
                    }

                 }

                 return list.size();

            }

            int getKLpos(){

                //double KLeps = 0.1;
                double CSNLP[];
                CSNLP = new double[MaxElements];
                double worstc;
                NBForBackNode tn;

                tn = (NBForBackNode)list.get(list.size()-1);
                worstc = tn.cost;

                for(int i=0;i<list.size();i++){
                    tn = (NBForBackNode)list.get(i);
                    // NOTE: sometimes we can have positive numbers !
                    double lc = tn.cost;
                    //double lc = tn.cost-worstc;

                    //if(lc >0){
                    //    int asdf=1;
                    //}

                    if (i==0) {
                        CSNLP[i] = lc;
                    } else {
                        CSNLP[i] = sumNegLogProb(CSNLP[i-1], lc);
                    }
                }

                // normalize
                for(int i=0;i<list.size();i++){
                    CSNLP[i]=CSNLP[i]-CSNLP[list.size()-1];
                    if(CSNLP[i] < KLeps){
                        KLMaxPos = i+1;
                        if(KLMaxPos >= KLMinElements) {
                            return KLMaxPos;
                        } else if(list.size() >= KLMinElements){
                            return KLMinElements;
                        }
                    }
                }

                KLMaxPos = list.size();
                return KLMaxPos;
            }

			ArrayList push(NBForBackNode vn)
			{
                double tc = vn.cost;
                boolean atEnd = true;

                for(int i=0;i<list.size();i++){
                    NBForBackNode tn = (NBForBackNode)list.get(i);
                    double lc = tn.cost;
                    if(tc < lc){
                        list.add(i,vn);
                        atEnd = false;
                        break;
                    }
                }

                if(atEnd) {
                    list.add(vn);
                }

                // CPAL - if the list is too big,
                // remove the first, largest cost element
                if(list.size()>MaxElements) {
                    list.remove(MaxElements);
                }

				//double f = o.totalCost[o.nextBestStateIndex];
				//boolean atEnd = true;
				//for(int i=0; i<list.size(); i++){
				//	ASearchNode_NBest tempNode = (ASearchNode_NBest)list.get(i);
				//	double f1 = tempNode.totalCost[tempNode.nextBestStateIndex];
				//	if(f < f1) {
				//		list.add(i, o);
				//		atEnd = false;
				//		break;
				//	}
				//}

				//if(atEnd) list.add(o);

				return list;
			}
		} // CPAL - end NBestSlist


		// culotta: constructor for constrained lattice
		/** Create a lattice that constrains its transitions such that the
		 * <position,label> pairs in "constraints" are adhered
		 * to. constraints is an array where each entry is the index of
		 * the required label at that position. An entry of 0 means there
		 * are no constraints on that <position, label>. Positive values
		 * mean the path must pass through that state. Negative values
		 * mean the path must _not_ pass through that state. NOTE -
		 * constraints.length must be equal to output.size() + 1. A
		 * lattice has one extra position for the initial
		 * state. Generally, this should be unconstrained, since it does
		 * not produce an observation.
		*/
		protected BeamLattice (Sequence input, Sequence output, boolean increment, LabelAlphabet outputAlphabet, int [] constraints)
		{
			if (false && logger.isLoggable (Level.FINE)) {
				logger.fine ("Starting Lattice");
				logger.fine ("Input: ");
				for (int ip = 0; ip < input.size(); ip++)
					logger.fine (" " + input.get(ip));
				logger.fine ("\nOutput: ");
				if (output == null)
					logger.fine ("null");
				else
					for (int op = 0; op < output.size(); op++)
						logger.fine (" " + output.get(op));
				logger.fine ("\n");
			}

			// Initialize some structures
			this.input = input;
			this.output = output;
			// xxx Not very efficient when the lattice is actually sparse,
			// especially when the number of states is large and the
			// sequence is long.
			latticeLength = input.size()+1;
			int numStates = numStates();
			nodes = new LatticeNode[latticeLength][numStates];
			// xxx Yipes, this could get big; something sparse might be better?
			gammas = new double[latticeLength][numStates];
			// xxx Move this to an ivar, so we can save it?  But for what?
      // Commenting this out, because it's a memory hog and not used right now.
      //  Uncomment and conditionalize under a flag if ever needed. -cas
			// double xis[][][] = new double[latticeLength][numStates][numStates];
			double outputCounts[][] = null;
			if (outputAlphabet != null)
				outputCounts = new double[latticeLength][outputAlphabet.size()];

			for (int i = 0; i < numStates; i++) {
				for (int ip = 0; ip < latticeLength; ip++)
					gammas[ip][i] = INFINITE_COST;
       /* Commenting out xis -cas
				for (int j = 0; j < numStates; j++)
					for (int ip = 0; ip < latticeLength; ip++)
						xis[ip][i][j] = INFINITE_COST;
        */
			}

			// Forward pass
			logger.fine ("Starting Constrained Foward pass");

			// ensure that at least one state has initial cost less than Infinity
			// so we can start from there
			boolean atLeastOneInitialState = false;
			for (int i = 0; i < numStates; i++) {
				double initialCost = getState(i).initialCost;
				//System.out.println ("Forward pass initialCost = "+initialCost);
				if (initialCost < INFINITE_COST) {
					getLatticeNode(0, i).alpha = initialCost;
					//System.out.println ("nodes[0][i].alpha="+nodes[0][i].alpha);
					atLeastOneInitialState = true;
				}
			}

			if (atLeastOneInitialState == false)
				logger.warning ("There are no starting states!");
			for (int ip = 0; ip < latticeLength-1; ip++)
				for (int i = 0; i < numStates; i++) {
					logger.fine ("ip=" + ip+", i=" + i);
					// check if this node is possible at this <position,
					// label>. if not, skip it.
					if (constraints[ip] > 0) { // must be in state indexed by constraints[ip] - 1
						if (constraints[ip]-1 != i) {
							logger.fine ("Current state does not match positive constraint. position="+ip+", constraint="+(constraints[ip]-1)+", currState="+i);
 							continue;
						}
					}
		 			else if (constraints[ip] < 0) { // must _not_ be in state indexed by constraints[ip]
					 	if (constraints[ip]+1 == -i) {
							logger.fine ("Current state does not match negative constraint. position="+ip+", constraint="+(constraints[ip]+1)+", currState="+i);
 							continue;
						}
					}
					if (nodes[ip][i] == null || nodes[ip][i].alpha == INFINITE_COST) {
						// xxx if we end up doing this a lot,
					 	// we could save a list of the non-null ones
						if (nodes[ip][i] == null) logger.fine ("nodes[ip][i] is NULL");
						else if (nodes[ip][i].alpha == INFINITE_COST) logger.fine ("nodes[ip][i].alpha is Inf");
						logger.fine ("INFINITE cost or NULL...skipping");
						continue;
					}
					State s = getState(i);

					TransitionIterator iter = s.transitionIterator (input, ip, output, ip);
					if (logger.isLoggable (Level.FINE))
						logger.fine (" Starting Forward transition iteration from state "
												 + s.getName() + " on input " + input.get(ip).toString()
												 + " and output "
												 + (output==null ? "(null)" : output.get(ip).toString()));
					while (iter.hasNext()) {
						State destination = iter.nextState();
						boolean legalTransition = true;
						// check constraints to see if node at <ip,i> can transition to destination
						if (ip+1 < constraints.length && constraints[ip+1] > 0 && ((constraints[ip+1]-1) != destination.getIndex())) {
							logger.fine ("Destination state does not match positive constraint. Assigning infinite cost. position="+(ip+1)+", constraint="+(constraints[ip+1]-1)+", source ="+i+", destination="+destination.getIndex());
							legalTransition = false;
						}
						else if (((ip+1) < constraints.length) && constraints[ip+1] < 0 && (-(constraints[ip+1]+1) == destination.getIndex())) {
							logger.fine ("Destination state does not match negative constraint. Assigning infinite cost. position="+(ip+1)+", constraint="+(constraints[ip+1]+1)+", destination="+destination.getIndex());
							legalTransition = false;
						}

						if (logger.isLoggable (Level.FINE))
							logger.fine ("Forward Lattice[inputPos="+ip
													 +"][source="+s.getName()
													 +"][dest="+destination.getName()+"]");
						LatticeNode destinationNode = getLatticeNode (ip+1, destination.getIndex());
						destinationNode.output = iter.getOutput();
						double transitionCost = iter.getCost();
						if (legalTransition) {
							//if (logger.isLoggable (Level.FINE))
							logger.fine ("transitionCost="+transitionCost
													 +" nodes["+ip+"]["+i+"].alpha="+nodes[ip][i].alpha
													 +" destinationNode.alpha="+destinationNode.alpha);
							destinationNode.alpha = sumNegLogProb (destinationNode.alpha,
																									 nodes[ip][i].alpha + transitionCost);
							//System.out.println ("destinationNode.alpha <- "+destinationNode.alpha);
							logger.fine ("Set alpha of latticeNode at ip = "+ (ip+1) + " stateIndex = " + destination.getIndex() + ", destinationNode.alpha = " + destinationNode.alpha);
						}
						else {
							// this is an illegal transition according to our
							// constraints, so set its prob to 0 . NO, alpha's are
							// unnormalized costs...set to Inf //
							// destinationNode.alpha = 0.0;
//							destinationNode.alpha = INFINITE_COST;
							logger.fine ("Illegal transition from state " + i + " to state " + destination.getIndex() + ". Setting alpha to Inf");
						}
					}
				}

			// Calculate total cost of Lattice.  This is the normalizer
			cost = INFINITE_COST;
			for (int i = 0; i < numStates; i++)
				if (nodes[latticeLength-1][i] != null) {
					// Note: actually we could sum at any ip index,
					// the choice of latticeLength-1 is arbitrary
					//System.out.println ("Ending alpha, state["+i+"] = "+nodes[latticeLength-1][i].alpha);
					//System.out.println ("Ending beta,  state["+i+"] = "+getState(i).finalCost);
					if (constraints[latticeLength-1] > 0 && i != constraints[latticeLength-1]-1)
						continue;
					if (constraints[latticeLength-1] < 0 && -i == constraints[latticeLength-1]+1)
						continue;
					logger.fine ("Summing final lattice cost. state="+i+", alpha="+nodes[latticeLength-1][i].alpha + ", final cost = "+getState(i).finalCost);
					cost = sumNegLogProb (cost,
																(nodes[latticeLength-1][i].alpha + getState(i).finalCost));
				}
			// Cost is now an "unnormalized cost" of the entire Lattice
			//assert (cost >= 0) : "cost = "+cost;

			// If the sequence has infinite cost, just return.
			// Usefully this avoids calling any incrementX methods.
			// It also relies on the fact that the gammas[][] and .alpha and .beta values
			// are already initialized to values that reflect infinite cost
			// xxx Although perhaps not all (alphas,betas) exactly correctly reflecting?
			if (cost == INFINITE_COST)
				return;

			// Backward pass
			for (int i = 0; i < numStates; i++)
				if (nodes[latticeLength-1][i] != null) {
					State s = getState(i);
					nodes[latticeLength-1][i].beta = s.finalCost;
					gammas[latticeLength-1][i] =
						nodes[latticeLength-1][i].alpha + nodes[latticeLength-1][i].beta - cost;
					if (increment) {
						double p = Math.exp(-gammas[latticeLength-1][i]);
						assert (p < INFINITE_COST && !Double.isNaN(p))
							: "p="+p+" gamma="+gammas[latticeLength-1][i];
						s.incrementFinalCount (p);
					}
				}
			for (int ip = latticeLength-2; ip >= 0; ip--) {
				for (int i = 0; i < numStates; i++) {
					if (nodes[ip][i] == null || nodes[ip][i].alpha == INFINITE_COST)
						// Note that skipping here based on alpha means that beta values won't
						// be correct, but since alpha is infinite anyway, it shouldn't matter.
						continue;
					State s = getState(i);
					TransitionIterator iter = s.transitionIterator (input, ip, output, ip);
					while (iter.hasNext()) {
						State destination = iter.nextState();
						if (logger.isLoggable (Level.FINE))
							logger.fine ("Backward Lattice[inputPos="+ip
													 +"][source="+s.getName()
													 +"][dest="+destination.getName()+"]");
						int j = destination.getIndex();
						LatticeNode destinationNode = nodes[ip+1][j];
						if (destinationNode != null) {
							double transitionCost = iter.getCost();
							assert (!Double.isNaN(transitionCost));
							//							assert (transitionCost >= 0);  Not necessarily
							double oldBeta = nodes[ip][i].beta;
							assert (!Double.isNaN(nodes[ip][i].beta));
							nodes[ip][i].beta = sumNegLogProb (nodes[ip][i].beta,
																								 destinationNode.beta + transitionCost);
							assert (!Double.isNaN(nodes[ip][i].beta))
								: "dest.beta="+destinationNode.beta+" trans="+transitionCost+" sum="+(destinationNode.beta+transitionCost)
								+ " oldBeta="+oldBeta;
							// xis[ip][i][j] = nodes[ip][i].alpha + transitionCost + nodes[ip+1][j].beta - cost;
							assert (!Double.isNaN(nodes[ip][i].alpha));
							assert (!Double.isNaN(transitionCost));
							assert (!Double.isNaN(nodes[ip+1][j].beta));
							assert (!Double.isNaN(cost));
							if (increment || outputAlphabet != null) {
                double xi = nodes[ip][i].alpha + transitionCost + nodes[ip+1][j].beta - cost;
								double p = Math.exp(-xi);
								assert (p < INFINITE_COST && !Double.isNaN(p)) : "xis["+ip+"]["+i+"]["+j+"]="+-xi;
								if (increment)
									iter.incrementCount (p);
								if (outputAlphabet != null) {
									int outputIndex = outputAlphabet.lookupIndex (iter.getOutput(), false);
									assert (outputIndex >= 0);
									// xxx This assumes that "ip" == "op"!
									outputCounts[ip][outputIndex] += p;
									//System.out.println ("CRF Lattice outputCounts["+ip+"]["+outputIndex+"]+="+p);
								}
							}
						}
					}
					gammas[ip][i] = nodes[ip][i].alpha + nodes[ip][i].beta - cost;
				}
			}
			if (increment)
				for (int i = 0; i < numStates; i++) {
					double p = Math.exp(-gammas[0][i]);
					assert (p < INFINITE_COST && !Double.isNaN(p));
					getState(i).incrementInitialCount (p);
				}
			if (outputAlphabet != null) {
				labelings = new LabelVector[latticeLength];
				for (int ip = latticeLength-2; ip >= 0; ip--) {
					assert (Math.abs(1.0-DenseVector.sum (outputCounts[ip])) < 0.000001);;
					labelings[ip] = new LabelVector (outputAlphabet, outputCounts[ip]);
				}
			}
		}

		public double getCost () {
			assert (!Double.isNaN(cost));
			return cost; }

		// No, this.cost is an "unnormalized cost"
		//public double getProbability () { return Math.exp (-cost); }

		public double getGammaCost (int inputPosition, State s) {
			return gammas[inputPosition][s.getIndex()]; }

		public double getGammaProbability (int inputPosition, State s) {
			return Math.exp (-gammas[inputPosition][s.getIndex()]); }

    public double getXiProbability (int ip, State s1, State s2) {
      if (xis == null)
        throw new IllegalStateException ("xis were not saved.");

      int i = s1.getIndex ();
      int j = s2.getIndex ();
      return Math.exp (-xis[ip][i][j]);
    }

    public double getXiCost (int ip, State s1, State s2)
    {
      if (xis == null)
        throw new IllegalStateException ("xis were not saved.");

      int i = s1.getIndex ();
      int j = s2.getIndex ();
      return xis[ip][i][j];
    }

    public int length () { return latticeLength; }

    public double getAlpha (int ip, State s) {
      LatticeNode node = getLatticeNode (ip, s.getIndex ());
      return node.alpha;
    }

    public double getBeta (int ip, State s) {
       LatticeNode node = getLatticeNode (ip, s.getIndex ());
       return node.beta;
     }

		public LabelVector getLabelingAtPosition (int outputPosition)	{
			if (labelings != null)
				return labelings[outputPosition];
			return null;
		}

		// Q: We are a non-static inner class so this should be easy; but how?
		// A: By the following weird syntax -cas
		public Transducer getTransducer ()
		{
			return Transducer.this;
		}


		// A container for some information about a particular input position and state
		private class LatticeNode
		{
			int inputPosition;
			// outputPosition not really needed until we deal with asymmetric epsilon.
			State state;
			Object output;
			double alpha = INFINITE_COST;
			double beta = INFINITE_COST;
			LatticeNode (int inputPosition, State state)	{
				this.inputPosition = inputPosition;
				this.state = state;
				assert (this.alpha == INFINITE_COST);	// xxx Remove this check
			}
		}

	}	// CPAL - end of class BeamLattice

    // *********************************************************************
    // CPAL - Std viterbiPath
    // *********************************************************************

	public ViterbiPath viterbiPath (Object unpipedObject)
	{
		Instance carrier = new Instance (unpipedObject, null, null, null, inputPipe);
		return viterbiPath ((Sequence)carrier.getData());
	}

	public ViterbiPath viterbiPath (Sequence inputSequence)
	{
		return viterbiPath (inputSequence, null);
	}

	public ViterbiPath viterbiPath (Sequence inputSequence, Sequence outputSequence)
	{
		// xxx We don't do epsilon transitions for now
		assert (outputSequence == null
						|| inputSequence.size() == outputSequence.size());
		return new ViterbiPath (inputSequence, outputSequence);
	}

    // *********************************************************************
    // CPAL - Fuchun's NBest viterbiPath
    // *********************************************************************

    public ViterbiPath_NBest viterbiPath_NBest (Sequence inputSequence, int N)
        {
                return viterbiPath_NBest (inputSequence, null, N);
        }

    public ViterbiPath_NBest viterbiPath_NBest (Sequence inputSequence, Sequence outputSequence, int N)
        {
                // xxx We don't do epsilon transitions for now
                assert (outputSequence == null
                                                || inputSequence.size() == outputSequence.size());
		assert(N > 0);
                return new ViterbiPath_NBest (inputSequence, outputSequence, N);
        }

    // *********************************************************************
    // CPAL - New viterbiBeam Stuff
    // *********************************************************************

  public ViterbiPathBeam viterbiPathBeam (Sequence inputSequence)
  {
    return viterbiPathBeam (inputSequence, beamWidth);
  }

	public ViterbiPathBeam viterbiPathBeam (Sequence inputSequence, int Bwidth)
	{
		return viterbiPathBeam (inputSequence, null, Bwidth);
	}

    public ViterbiPathBeam viterbiPathBeam (Sequence inputSequence, Sequence outputSequence, int Bwidth)
	{
		// xxx We don't do epsilon transitions for now
		assert (outputSequence == null
						|| inputSequence.size() == outputSequence.size());
		return new ViterbiPathBeam (inputSequence, outputSequence, Bwidth);
	}

   // *********************************************************************
       // *********************************************************************
    // CPAL - New viterbiBeamKL Stuff - an adaptive beam based on KL divergence
    // *********************************************************************

  public ViterbiPathBeamKL viterbiPathBeamKL (Sequence inputSequence)
  {
    return viterbiPathBeamKL (inputSequence, beamWidth);
  }

	public ViterbiPathBeamKL viterbiPathBeamKL (Sequence inputSequence, int Bwidth)
	{
		return viterbiPathBeamKL (inputSequence, null, Bwidth);
	}

    public ViterbiPathBeamKL viterbiPathBeamKL (Sequence inputSequence, Sequence outputSequence, int Bwidth)
	{
		// xxx We don't do epsilon transitions for now
		assert (outputSequence == null
						|| inputSequence.size() == outputSequence.size());
		return new ViterbiPathBeamKL (inputSequence, outputSequence, Bwidth);
	}

   // *********************************************************************



    // *********************************************************************
    // CPAL - A Backward Version of Viterbi Beam Search
    //
    // *********************************************************************
  public ViterbiPathBeamB viterbiPathBeamB (Sequence inputSequence)
  {
    return viterbiPathBeamB (inputSequence, beamWidth);
  }

    public ViterbiPathBeamB viterbiPathBeamB (Object unpipedObject)
	{
		Instance carrier = new Instance (unpipedObject, null, null, null, inputPipe);
		return viterbiPathBeamB ((Sequence)carrier.getData());
	}

	public ViterbiPathBeamB viterbiPathBeamB (Sequence inputSequence, int Bwidth)
	{
		return viterbiPathBeamB (inputSequence, null, Bwidth);
	}

    public ViterbiPathBeamB viterbiPathBeamB (Sequence inputSequence, Sequence outputSequence, int Bwidth)
	{
		// xxx We don't do epsilon transitions for now
		assert (outputSequence == null
						|| inputSequence.size() == outputSequence.size());
		return new ViterbiPathBeamB (inputSequence, outputSequence, Bwidth);
	}

   // *********************************************************************


    // *********************************************************************
    // CPAL - New viterbiBeamFB Stuff
    //      - a new forward backward beam "search"
    // *********************************************************************
  public ViterbiPathBeamFB viterbiPathBeamFB (Sequence inputSequence)
  {
    return viterbiPathBeamFB (inputSequence, beamWidth);
  }

    public ViterbiPathBeamFB viterbiPathBeamFB (Object unpipedObject)
	{
		Instance carrier = new Instance (unpipedObject, null, null, null, inputPipe);
		return viterbiPathBeamFB ((Sequence)carrier.getData());
	}

	public ViterbiPathBeamFB viterbiPathBeamFB (Sequence inputSequence, int Bwidth)
	{
		return viterbiPathBeamFB (inputSequence, null, Bwidth);
	}

    public ViterbiPathBeamFB viterbiPathBeamFB (Sequence inputSequence, Sequence outputSequence, int Bwidth)
	{
		// xxx We don't do epsilon transitions for now
		assert (outputSequence == null
						|| inputSequence.size() == outputSequence.size());
		return new ViterbiPathBeamFB (inputSequence, outputSequence, Bwidth);
	}

   // *********************************************************************



	public class ViterbiPath extends SequencePairAlignment
	{
		// double cost inherited from SequencePairAlignment
		// Sequence input, output inherited from SequencePairAlignment
		// this.output stores the actual output of Viterbi transitions
		Sequence providedOutput;
		ViterbiNode[] nodePath;
		int latticeLength;

    ViterbiNode[][] lattice; // Entire lattic, saved only if saveLattice is true,

    public double getDelta (int ip, int stateIndex) {
      if (lattice != null) {
        return getViterbiNode (lattice, ip, stateIndex).delta;
      } else {
        throw new RuntimeException ("Attempt to called getDelta() when lattice not stored.");
      }
    }

    public State getBestState (int ip) { return getStateAtRank (ip, 0); }

    public State getStateAtRank (final int ip, int rank)
    {
      if (lattice != null) {
        Integer[] rankedStates = new Integer [numStates()];
        for (int k = 0; k < numStates(); k++)
          rankedStates[k] = new Integer (k);
        Arrays.sort (rankedStates, new Comparator () {
          public int compare (Object o, Object o1)
          {
            int i1 = ((Integer)o).intValue ();
            int i2 = ((Integer)o1).intValue ();
            return Double.compare (getDelta (ip, i1), getDelta (ip, i2));
          }
        });
        return getState (rankedStates[rank].intValue ());
      } else {
        throw new RuntimeException ("Attempt to called getMaxState() when lattice not stored.");
      }
    }

		protected ViterbiNode getViterbiNode (ViterbiNode[][] nodes, int ip, int stateIndex)
		{
			if (nodes[ip][stateIndex] == null)
				nodes[ip][stateIndex] = new ViterbiNode (ip, getState (stateIndex));
			return nodes[ip][stateIndex];
		}

		// You may pass null for output
		protected ViterbiPath (Sequence inputSequence, Sequence outputSequence)
		{
      this (inputSequence, outputSequence, false);
    }

    protected ViterbiPath (Sequence inputSequence, Sequence outputSequence, boolean saveLattice)
    {
			assert (inputSequence != null);
			if (logger.isLoggable (Level.FINE)) {
				logger.fine ("Starting ViterbiPath");
				logger.fine ("Input: ");
				for (int ip = 0; ip < inputSequence.size(); ip++)
					logger.fine (" " + inputSequence.get(ip));
				logger.fine ("\nOutput: ");
				if (outputSequence == null)
					logger.fine ("null");
				else
					for (int op = 0; op < outputSequence.size(); op++)
						logger.fine (" " + outputSequence.get(op));
				logger.fine ("\n");
			}

			this.input = inputSequence;
			this.providedOutput = outputSequence;
			// this.output is set at the end when we know the exact outputs
			// of the Viterbi path.  Note that in some cases the "output"
			// may be provided non-null as an argument to this method, but the
			// "output" objects may not be fully-specified even though they do
			// provide some restrictions.  This is why we set our this.output
			// from the outputs provided by the transition iterator along the
			// Viterbi path.

			// xxx Not very efficient when the lattice is actually sparse,
			// especially when the number of states is large and the
			// sequence is long.
			latticeLength = input.size()+1;
			int numStates = numStates();
			ViterbiNode[][] nodes = new ViterbiNode[latticeLength][numStates];
      if (saveLattice) lattice = nodes;

            // CPAL - a bit of a hack, but this makes std. viterbi compatable with
            //        the statistics generated for the newer beam versions of viterbi
            nstatesExpl = new double[1];
            nstatesExpl[0] = numStates;
            // CPAL - done

			// Viterbi Forward
			logger.fine ("Starting Viterbi");
      boolean anyInitialState = false;
			for (int i = 0; i < numStates; i++) {
				double initialCost = getState(i).initialCost;
				if (initialCost < INFINITE_COST) {
					ViterbiNode n = getViterbiNode (nodes, 0, i);
					n.delta = initialCost;
          anyInitialState = true;
				}
			}

      if (!anyInitialState) {
        logger.warning ("Viterbi: No initial states!");
      }

			for (int ip = 0; ip < latticeLength-1; ip++)
				for (int i = 0; i < numStates; i++) {
					if (nodes[ip][i] == null || nodes[ip][i].delta == INFINITE_COST)
						// xxx if we end up doing this a lot,
						// we could save a list of the non-null ones
						continue;
					State s = getState(i);
					TransitionIterator iter = s.transitionIterator (input, ip, providedOutput, ip);
					if (logger.isLoggable (Level.FINE))
						logger.fine (" Starting Viterbi transition iteration from state "
												 + s.getName() + " on input " + input.get(ip));
					while (iter.hasNext()) {
						State destination = iter.nextState();
						if (logger.isLoggable (Level.FINE))
							logger.fine ("Viterbi[inputPos="+ip
													 +"][source="+s.getName()
													 +"][dest="+destination.getName()+"]");
						ViterbiNode destinationNode = getViterbiNode (nodes, ip+1,
																													destination.getIndex());
						destinationNode.output = iter.getOutput();
						cost = nodes[ip][i].delta + iter.getCost();

//						System.out.println("cost = " + iter.getCost() + " (" + ip + " " + i +")");

						if (ip == latticeLength-2){// why there is multiple next states at the end of sequence????
							cost += destination.getFinalCost();

//							System.out.println("number of next states: " + iter.numberNext());
						}

						if (cost < destinationNode.delta) {
							if (logger.isLoggable (Level.FINE))
								logger.fine ("Viterbi[inputPos="+ip
														 +"][source][dest="+destination.getName()
														 +"] cost reduced to "+cost+" by source="+
														 s.getName());
							destinationNode.delta = cost;
							destinationNode.minCostPredecessor = nodes[ip][i];
						}
					}
				}

			// Find the final state with minimum cost, and get the total
			// cost of the Viterbi path.
			ViterbiNode minCostNode;
			int ip = latticeLength-1;
			this.cost = INFINITE_COST;
			minCostNode = null;
			for (int i = 0; i < numStates; i++) {
				if (nodes[ip][i] == null)
					continue;
				if (nodes[ip][i].delta < this.cost) {
					minCostNode = nodes[ip][i];
					this.cost = minCostNode.delta;

//					System.out.println("this.cost = " + this.cost);
				}
			}

      if (minCostNode == null) {
        logger.warning ("Viterbi: Sequence has infinite cost.  Output will be empty...");
        this.output = new ArraySequence (new ArrayList ());
        return;
      }

			// Build the path and the output sequence.
			this.nodePath = new ViterbiNode[latticeLength];
			Object[] outputArray = new Object[input.size()];
			for (ip = latticeLength-1; ip >= 0; ip--) {
				this.nodePath[ip] = minCostNode;
				if (ip > 0){
					outputArray[ip-1] = minCostNode.output;
//					System.out.println(ip + ":" + outputArray[ip-1]);
				}


//				System.out.println(ip + ": " + latticeLength);
//				System.out.println("posit: " + minCostNode.inputPosition);
//				System.out.println("state: " + minCostNode.state.getName());
//				System.out.println("delta: " + minCostNode.delta);

				minCostNode = minCostNode.minCostPredecessor;
			}
			this.output = new ArraySequence (outputArray, false);
		}

		// xxx Delete this method and move it into the Viterbi constructor, just like Lattice.
		// Increment states and transitions in the tranducer with the
		// expectations from this path.  This provides for the so-called
		// "Viterbi training" approximation.
		public void incrementTransducerCounts ()
		{
			nodePath[0].state.incrementInitialCount (1.0);
			nodePath[nodePath.length-1].state.incrementFinalCount (1.0);
			for (int ip = 0; ip < nodePath.length-1; ip++) {
				TransitionIterator iter =
					nodePath[ip].state.transitionIterator (input, ip,
																								 providedOutput, ip);
				// xxx This assumes that a transition is completely
				// identified, and made unique by its destination state and
				// output.  This may not be true!
				int numIncrements = 0;
				while (iter.hasNext()) {
					if (iter.nextState().equals (nodePath[ip+1].state)
							&& iter.getOutput().equals (nodePath[ip].output)) {
						iter.incrementCount (1.0);
						numIncrements++;
					}
				}
				if (numIncrements > 1)
					throw new IllegalStateException ("More than one satisfying transition found.");
				if (numIncrements == 0)
					throw new IllegalStateException ("No satisfying transition found.");
			}
		}

		public SequencePairAlignment trimStateInfo ()
		{
			return new SequencePairAlignment (input, output, cost);
		}

		public double tokenAccuracy (Sequence referenceOutput)
		{
			int accuracy = 0;
			assert (referenceOutput.size() == output.size());
			for (int i = 0; i < output.size(); i++) {
				//logger.fine("tokenAccuracy: ref: "+referenceOutput.get(i)+" viterbi: "+output.get(i));
				if (referenceOutput.get(i).toString().equals (output.get(i).toString())) {
					accuracy++;
				}
			}
			logger.info ("Number correct: " + accuracy + " out of " + output.size());
			return ((double)accuracy)/output.size();
		}

		public double tokenAccuracy (Sequence referenceOutput, PrintWriter out)
		{
			int accuracy = 0;
			String testString;
			assert (referenceOutput.size() == output.size());
			for (int i = 0; i < output.size(); i++) {
				//logger.fine("tokenAccuracy: ref: "+referenceOutput.get(i)+" viterbi: "+output.get(i));
				testString = output.get(i).toString();
				if (out != null) {
					out.println(testString);
				}
				if (referenceOutput.get(i).toString().equals (testString)) {
					accuracy++;
				}
			}
			logger.info ("Number correct: " + accuracy + " out of " + output.size());
			return ((double)accuracy)/output.size();
		}


    public Transducer getTransducer ()
    {
      return Transducer.this;
    }

    /**
     * Returns the maximum-value state sequence from this Lattice.
     *  This is distinct from {@link #output()}, which returns the Transducer's output from the
     *   most likely state sequence.
     * <p>
     *  Almost all applications will want to use the <tt>output</tt> method instead.
     *   An exception are debugging tools that care specifically about the state sequence.
     * @return A Sequence of Strings. Its length will be the input length plus 1. (for start state).
     *
     */
    public Sequence getBestStates ()
    {
      String[] stateNames = new String [nodePath.length];
      for (int i = 0; i < nodePath.length; i++) {
        ViterbiNode node = nodePath[i];
        stateNames[i] = node.state.getName ();
      }
      return new ArraySequence (stateNames);
    }

    private class ViterbiNode
		{
			int inputPosition;								// Position of input used to enter this node
			State state;											// Transducer state from which this node entered
			Object output;										// Transducer output produced on entering this node
			double delta = INFINITE_COST;
			ViterbiNode minCostPredecessor = null;
			ViterbiNode (int inputPosition, State state)
			{
				this.inputPosition = inputPosition;
				this.state = state;
			}
		}

	} // end of ViterbiPath

  public class ViterbiLattice {
    private Sequence input, providedOutput;
    private int latticeLength;
    private ViterbiNode[][] lattice;
    private CostCache first, last;
    private CostCache[] caches;
    private int numCaches, maxCaches;

    private class ViterbiNode implements AStarState {
      int inputPosition;								// Position of input used to enter this node
      State state;											// Transducer state from which this node entered
      Object output;										// Transducer output produced on entering this node
      double delta = INFINITE_COST;
      ViterbiNode minCostPredecessor = null;
      ViterbiNode (int inputPosition, State state) {
        this.inputPosition = inputPosition;
        this.state = state;
      }
      public double completionCost() { return delta; }
      public boolean isFinal() {
        return inputPosition == 0 &&
                state.getInitialCost() < INFINITE_COST;
      }
      private class PreviousStateIterator extends AStarState.NextStateIterator {
        private int prev;
        private boolean found;
        private double cost;
        private double[] costs;
        private PreviousStateIterator() {
         prev = 0;
         if (inputPosition > 0) {
            int t = state.getIndex();
            costs = new double[numStates()];
            CostCache c = getCache(inputPosition-1);
            for (int s = 0; s < numStates(); s++)
              costs[s] = c.cost[s][t];
          }
        }
        private void lookAhead() {
          if (costs != null && !found) {
            for (; prev < numStates(); prev++)
              if (costs[prev] < INFINITE_COST) {
                found = true;
                return;
              }
          }
        }
        public boolean hasNext() {
          lookAhead();
          return costs != null && prev < numStates();
        }

        public SearchState nextState() {
          lookAhead();
          cost = costs[prev++];
          found = false;
          return getViterbiNode(inputPosition-1, prev-1);
        }

        public double cost() {
          return cost;
        }
      }

      public NextStateIterator getNextStates() {
        return new PreviousStateIterator();
      }
    }

    private class CostCache {
      private CostCache prev, next;
      private double cost[][];
      private int position;
      private CostCache(int position) {
        cost = new double[numStates()][numStates()];
        init(position);
      }
      private void init(int position) {
        this.position = position;
        for (int i = 0; i < numStates(); i++)
          for (int j = 0; j < numStates(); j++)
            cost[i][j] = INFINITE_COST;
      }
    }

    private CostCache getCache(int position) {
      CostCache cache = caches[position];
      if (cache == null) {            // No cache for this position
//        System.out.println("cache " + numCaches + "/" + maxCaches);
        if (numCaches < maxCaches)  { // Create another cache
          cache = new CostCache(position);
          if (numCaches++ == 0)
            first = last = cache;
        }
        else {                        // Steal least used cache
          cache = last;
          caches[cache.position] = null;
          cache.init(position);
        }
        for (int i = 0; i < numStates(); i++) {
          if (lattice[position][i] == null || lattice[position][i].delta == INFINITE_COST)
            continue;
          State s = getState(i);
          TransitionIterator iter =
                  s.transitionIterator (input, position, providedOutput, position);
          while (iter.hasNext()) {
            State d = iter.nextState();
            cache.cost[i][d.getIndex()] = iter.getCost();
          }
        }        
        caches[position] = cache;
      }
      if (cache != first) {           // Move to front
        if (cache == last)
          last = cache.prev;
        if (cache.prev != null)
          cache.prev.next = cache.next;
        cache.next = first;
        cache.prev = null;
        first.prev = cache;
        first = cache;
      }
      return cache;
    }

    protected ViterbiNode getViterbiNode (int ip, int stateIndex)
    {
      if (lattice[ip][stateIndex] == null)
        lattice[ip][stateIndex] = new ViterbiNode (ip, getState (stateIndex));
      return lattice[ip][stateIndex];
    }

    protected ViterbiLattice (Sequence inputSequence, Sequence outputSequence,
                              int maxCaches) {
      if (maxCaches < 1)
        maxCaches = 1;
      this.maxCaches = maxCaches;
      assert (inputSequence != null);
      if (logger.isLoggable (Level.FINE)) {
        logger.fine ("Starting ViterbiLattice");
        logger.fine ("Input: ");
        for (int ip = 0; ip < inputSequence.size(); ip++)
          logger.fine (" " + inputSequence.get(ip));
        logger.fine ("\nOutput: ");
        if (outputSequence == null)
          logger.fine ("null");
        else
          for (int op = 0; op < outputSequence.size(); op++)
            logger.fine (" " + outputSequence.get(op));
        logger.fine ("\n");
      }

      this.input = inputSequence;
      this.providedOutput = outputSequence;
      latticeLength = input.size()+1;
      int numStates = numStates();
      lattice = new ViterbiNode[latticeLength][numStates];
      caches = new CostCache[latticeLength-1];

      // Viterbi Forward
      logger.fine ("Starting Viterbi");
      boolean anyInitialState = false;
      for (int i = 0; i < numStates; i++) {
        double initialCost = getState(i).initialCost;
        if (initialCost < INFINITE_COST) {
          ViterbiNode n = getViterbiNode (0, i);
          n.delta = initialCost;
          anyInitialState = true;
        }
      }

      if (!anyInitialState) {
        logger.warning ("Viterbi: No initial states!");
      }

      for (int ip = 0; ip < latticeLength-1; ip++)
        for (int i = 0; i < numStates; i++) {
          if (lattice[ip][i] == null || lattice[ip][i].delta == INFINITE_COST)
            continue;
          State s = getState(i);
          TransitionIterator iter = s.transitionIterator (input, ip, providedOutput, ip);
          if (logger.isLoggable (Level.FINE))
            logger.fine (" Starting Viterbi transition iteration from state "
                    + s.getName() + " on input " + input.get(ip));
          while (iter.hasNext()) {
            State destination = iter.nextState();
            if (logger.isLoggable (Level.FINE))
              logger.fine ("Viterbi[inputPos="+ip
                      +"][source="+s.getName()
                      +"][dest="+destination.getName()+"]");
            ViterbiNode destinationNode = getViterbiNode (ip+1, destination.getIndex());
            destinationNode.output = iter.getOutput();
            double cost = lattice[ip][i].delta + iter.getCost();
            if (ip == latticeLength-2) {
              cost += destination.getFinalCost();
            }
            if (cost < destinationNode.delta) {
              if (logger.isLoggable (Level.FINE))
                logger.fine ("Viterbi[inputPos="+ip
                        +"][source][dest="+destination.getName()
                        +"] cost reduced to "+cost+" by source="+
                        s.getName());
              destinationNode.delta = cost;
              destinationNode.minCostPredecessor = lattice[ip][i];
            }
          }
        }
    }
    public Sequence[] outputNBest(int n) {
      int numFinal = 0;
      for (int i = 0; i < numStates(); i++) {
        if (lattice[latticeLength-1][i] != null && lattice[latticeLength-1][i].delta < INFINITE_COST)
          numFinal++;
      }
      ViterbiNode[] finalNodes = new ViterbiNode[numFinal];
      int f = 0;
      for (int i = 0; i < numStates(); i++) {
        if (lattice[latticeLength-1][i] != null && lattice[latticeLength-1][i].delta < INFINITE_COST)
          finalNodes[f++] = lattice[latticeLength-1][i];
      }
      AStar search = new AStar(finalNodes, latticeLength * numStates());
      List outputs = new ArrayList(n);
      for (int i = 0; i < n && search.hasNext(); i++) {
        SearchNode ans = (SearchNode)search.next();
        Object[] seq = new Object[input.size()];
        ans = ans.getParent(); // this corresponds to the Viterbi node after the first transition
        for (int j = 0; j < input.size(); j++) {
          ViterbiNode v = (ViterbiNode)ans.getState();
          assert(v.inputPosition == j + 1);
          seq[j] = v.output;
          ans = ans.getParent();
        }
        outputs.add(new ArraySequence(seq, false));
      }
      return (Sequence [])outputs.toArray(new Sequence[] {});
    }
  } // end of ViterbiLattice

  public ViterbiLattice getViterbiLattice(Sequence input, Sequence output, int cacheSize) {
    return new ViterbiLattice(input, output, 1+cacheSize/(numStates()*numStates()));
  }

    // CPAL - added this ViberbiPathBeam
    public class ViterbiPathBeam extends SequencePairAlignment
	{
		// double cost inherited from SequencePairAlignment
		// Sequence input, output inherited from SequencePairAlignment
		// this.output stores the actual output of Viterbi transitions
		Sequence providedOutput;
		ViterbiNode[] nodePath;
		int latticeLength;

    ViterbiNode[][] lattice; // Entire lattic, saved only if saveLattice is true,

    public double getDelta (int ip, int stateIndex) {
      if (lattice != null) {
        return getViterbiNode (lattice, ip, stateIndex).delta;
      } else {
        throw new RuntimeException ("Attempt to called getDelta() when lattice not stored.");
      }
    }

    public State getBestState (int ip) { return getStateAtRank (ip, 0); }

    public State getStateAtRank (final int ip, int rank)
    {
      if (lattice != null) {
        Integer[] rankedStates = new Integer [numStates()];
        for (int k = 0; k < numStates(); k++)
          rankedStates[k] = new Integer (k);
        Arrays.sort (rankedStates, new Comparator () {
          public int compare (Object o, Object o1)
          {
            int i1 = ((Integer)o).intValue ();
            int i2 = ((Integer)o1).intValue ();
            return Double.compare (getDelta (ip, i1), getDelta (ip, i2));
          }
        });
        return getState (rankedStates[rank].intValue ());
      } else {
        throw new RuntimeException ("Attempt to called getMaxState() when lattice not stored.");
      }
    }

		protected ViterbiNode getViterbiNode (ViterbiNode[][] nodes, int ip, int stateIndex)
		{
			if (nodes[ip][stateIndex] == null)
				nodes[ip][stateIndex] = new ViterbiNode (ip, getState (stateIndex));
			return nodes[ip][stateIndex];
		}

		// You may pass null for output
		protected ViterbiPathBeam (Sequence inputSequence, Sequence outputSequence, int Bwidth)
		{
      this (inputSequence, outputSequence, false, Bwidth);
    }

    protected ViterbiPathBeam (Sequence inputSequence, Sequence outputSequence, boolean saveLattice, int Bwidth)
    {
			assert (inputSequence != null);
			if (logger.isLoggable (Level.FINE)) {
				logger.fine ("Starting ViterbiPath");
				logger.fine ("Input: ");
				for (int ip = 0; ip < inputSequence.size(); ip++)
					logger.fine (" " + inputSequence.get(ip));
				logger.fine ("\nOutput: ");
				if (outputSequence == null)
					logger.fine ("null");
				else
					for (int op = 0; op < outputSequence.size(); op++)
						logger.fine (" " + outputSequence.get(op));
				logger.fine ("\n");
			}

			this.input = inputSequence;
			this.providedOutput = outputSequence;
			// this.output is set at the end when we know the exact outputs
			// of the Viterbi path.  Note that in some cases the "output"
			// may be provided non-null as an argument to this method, but the
			// "output" objects may not be fully-specified even though they do
			// provide some restrictions.  This is why we set our this.output
			// from the outputs provided by the transition iterator along the
			// Viterbi path.

			// xxx Not very efficient when the lattice is actually sparse,
			// especially when the number of states is large and the
			// sequence is long.
			latticeLength = input.size()+1;
			int numStates = numStates();
			ViterbiNode[][] nodes = new ViterbiNode[latticeLength][numStates];


      if (saveLattice) lattice = nodes;

      // CPAL - a bit of a hack, but this makes std. viterbi compatable with
      //        the statistics generated for the newer beam versions of viterbi
      nstatesExpl = new double[1];
      nstatesExpl[0] = Bwidth;
      // CPAL - done

	  // Viterbi Forward
      // CPAL - Initial Viberbi Values

	  logger.fine ("Starting Viterbi");
      boolean anyInitialState = false;
			for (int i = 0; i < numStates; i++) {
				double initialCost = getState(i).initialCost;
				if (initialCost < INFINITE_COST) {
					ViterbiNode n = getViterbiNode (nodes, 0, i);
					n.delta = initialCost;
                    anyInitialState = true;
				}
			}

      if (!anyInitialState) {
        logger.warning ("Viterbi: No initial states!");
      }

      NBestSlist[] slists = new NBestSlist[latticeLength];


			for (int ip = 0; ip < latticeLength-1; ip++) {

                // CPAL - add this to construct the beam
                // ***************************************************

                // CPAL - sets up the sorted list
                slists[ip] = new NBestSlist(Bwidth);

                for(int i = 0 ; i< numStates ; i++){
                     if (nodes[ip][i] == null || nodes[ip][i].delta == INFINITE_COST)
						continue;
                    //State s = getState(i);
                    // CPAL - give the NB viterbi node the (cost, position)
                    NBViterbiNode cnode = new NBViterbiNode(nodes[ip][i].delta, i);
                    slists[ip].push(cnode);

                }
                // ***************************************************

                // CPAL - we now loop over the NBestSlist
				// for (int i = 0; i < numStates; i++) {
                for(int jj=0 ; jj< slists[ip].size(); jj++) {

                    int i = slists[ip].getPosByIndex(jj);

                    // CPAL - dont need this anymore
					// if (nodes[ip][i] == null || nodes[ip][i].delta == INFINITE_COST)
						// xxx if we end up doing this a lot,
						// we could save a list of the non-null ones
					//	continue;

					State s = getState(i);
					TransitionIterator iter = s.transitionIterator (input, ip, providedOutput, ip);
					if (logger.isLoggable (Level.FINE))
						logger.fine (" Starting Viterbi transition iteration from state "
												 + s.getName() + " on input " + input.get(ip));
					while (iter.hasNext()) {
						State destination = iter.nextState();
						if (logger.isLoggable (Level.FINE))
							logger.fine ("Viterbi[inputPos="+ip
													 +"][source="+s.getName()
													 +"][dest="+destination.getName()+"]");
						ViterbiNode destinationNode = getViterbiNode (nodes, ip+1, destination.getIndex());
						destinationNode.output = iter.getOutput();
						cost = nodes[ip][i].delta + iter.getCost();

//						System.out.println("cost = " + iter.getCost() + " (" + ip + " " + i +")");

						if (ip == latticeLength-2){// why there is multiple next states at the end of sequence????
							cost += destination.getFinalCost();

//							System.out.println("number of next states: " + iter.numberNext());
						}

						if (cost < destinationNode.delta) {
							if (logger.isLoggable (Level.FINE))
								logger.fine ("Viterbi[inputPos="+ip
														 +"][source][dest="+destination.getName()
														 +"] cost reduced to "+cost+" by source="+
														 s.getName());
							destinationNode.delta = cost;
							destinationNode.minCostPredecessor = nodes[ip][i];
						}
					}
				}
              }

			// Find the final state with minimum cost, and get the total
			// cost of the Viterbi path.
			ViterbiNode minCostNode;
			int ip = latticeLength-1;
			this.cost = INFINITE_COST;
			minCostNode = null;
			for (int i = 0; i < numStates; i++) {
				if (nodes[ip][i] == null)
					continue;
				if (nodes[ip][i].delta < this.cost) {
					minCostNode = nodes[ip][i];
					this.cost = minCostNode.delta;

//					System.out.println("this.cost = " + this.cost);
				}
			}

      if (minCostNode == null) {
        logger.warning ("Viterbi: Sequence has infinite cost.  Output will be empty...");
        this.output = new ArraySequence (new ArrayList ());
        return;
      }

			// Build the path and the output sequence.
			this.nodePath = new ViterbiNode[latticeLength];
			Object[] outputArray = new Object[input.size()];
			for (ip = latticeLength-1; ip >= 0; ip--) {
				this.nodePath[ip] = minCostNode;
				if (ip > 0){
					outputArray[ip-1] = minCostNode.output;
//					System.out.println(ip + ":" + outputArray[ip-1]);
				}


//				System.out.println(ip + ": " + latticeLength);
//				System.out.println("posit: " + minCostNode.inputPosition);
//				System.out.println("state: " + minCostNode.state.getName());
//				System.out.println("delta: " + minCostNode.delta);

				minCostNode = minCostNode.minCostPredecessor;
			}
			this.output = new ArraySequence (outputArray, false);
		}

        // CPAL - done ViterbiPathBeam


		// xxx Delete this method and move it into the Viterbi constructor, just like Lattice.
		// Increment states and transitions in the tranducer with the
		// expectations from this path.  This provides for the so-called
		// "Viterbi training" approximation.
		public void incrementTransducerCounts ()
		{
			nodePath[0].state.incrementInitialCount (1.0);
			nodePath[nodePath.length-1].state.incrementFinalCount (1.0);
			for (int ip = 0; ip < nodePath.length-1; ip++) {
				TransitionIterator iter =
					nodePath[ip].state.transitionIterator (input, ip,
																								 providedOutput, ip);
				// xxx This assumes that a transition is completely
				// identified, and made unique by its destination state and
				// output.  This may not be true!
				int numIncrements = 0;
				while (iter.hasNext()) {
					if (iter.nextState().equals (nodePath[ip+1].state)
							&& iter.getOutput().equals (nodePath[ip].output)) {
						iter.incrementCount (1.0);
						numIncrements++;
					}
				}
				if (numIncrements > 1)
					throw new IllegalStateException ("More than one satisfying transition found.");
				if (numIncrements == 0)
					throw new IllegalStateException ("No satisfying transition found.");
			}
		}

		public SequencePairAlignment trimStateInfo ()
		{
			return new SequencePairAlignment (input, output, cost);
		}

		public double tokenAccuracy (Sequence referenceOutput)
		{
			int accuracy = 0;
			assert (referenceOutput.size() == output.size());
			for (int i = 0; i < output.size(); i++) {
				//logger.fine("tokenAccuracy: ref: "+referenceOutput.get(i)+" viterbi: "+output.get(i));
				if (referenceOutput.get(i).toString().equals (output.get(i).toString())) {
					accuracy++;
				}
			}
			logger.info ("Number correct: " + accuracy + " out of " + output.size());
			return ((double)accuracy)/output.size();
		}

		public double tokenAccuracy (Sequence referenceOutput, PrintWriter out)
		{
			int accuracy = 0;
			String testString;
			assert (referenceOutput.size() == output.size());
			for (int i = 0; i < output.size(); i++) {
				//logger.fine("tokenAccuracy: ref: "+referenceOutput.get(i)+" viterbi: "+output.get(i));
				testString = output.get(i).toString();
				if (out != null) {
					out.println(testString);
				}
				if (referenceOutput.get(i).toString().equals (testString)) {
					accuracy++;
				}
			}
			logger.info ("Number correct: " + accuracy + " out of " + output.size());
			return ((double)accuracy)/output.size();
		}


    public Transducer getTransducer ()
    {
      return Transducer.this;
    }


    private class ViterbiNode
		{
			int inputPosition;								// Position of input used to enter this node
			State state;											// Transducer state from which this node entered
			Object output;										// Transducer output produced on entering this node
			double delta = INFINITE_COST;
			ViterbiNode minCostPredecessor = null;
			ViterbiNode (int inputPosition, State state)
			{
				this.inputPosition = inputPosition;
				this.state = state;
			}
		}

        // CPAL - a simple node holding a cost and position of the state
        private class NBViterbiNode
        {
            double cost;
            int pos;
            NBViterbiNode(double cost, int pos)
            {
                this.cost = cost;
                this.pos = pos;
            }
        }

        private class NBestSlist
		{
			ArrayList list = new ArrayList();
            int MaxElements;

			NBestSlist(int MaxElements)
			{
                this.MaxElements = MaxElements;
			}

			int size()
			{
				return list.size();
			}

			boolean empty()
			{
				return list.isEmpty();
			}

			Object pop()
			{
				return list.remove(0);
			}

            int getPosByIndex(int ii){
                NBViterbiNode tn = (NBViterbiNode)list.get(ii);
                return tn.pos;
            }

			ArrayList push(NBViterbiNode vn)
			{
                double tc = vn.cost;
                boolean atEnd = true;

                for(int i=0;i<list.size();i++){
                    NBViterbiNode tn = (NBViterbiNode)list.get(i);
                    double lc = tn.cost;
                    if(tc < lc){
                        list.add(i,vn);
                        atEnd = false;
                        break;
                    }
                }

                if(atEnd) {
                    list.add(vn);
                }

                // CPAL - if the list is too big,
                // remove the first, largest cost element
                if(list.size()>MaxElements) {
                    list.remove(MaxElements);
                }

				//double f = o.totalCost[o.nextBestStateIndex];
				//boolean atEnd = true;
				//for(int i=0; i<list.size(); i++){
				//	ASearchNode_NBest tempNode = (ASearchNode_NBest)list.get(i);
				//	double f1 = tempNode.totalCost[tempNode.nextBestStateIndex];
				//	if(f < f1) {
				//		list.add(i, o);
				//		atEnd = false;
				//		break;
				//	}
				//}

				//if(atEnd) list.add(o);

				return list;
			}
		} // CPAL - end NBestSlist

	} // end of ViterbiPathBeam
    // CPAL - Done Beam version


    // CPAL - added this ViberbiPathBeamKL
    public class ViterbiPathBeamKL extends SequencePairAlignment
	{
		// double cost inherited from SequencePairAlignment
		// Sequence input, output inherited from SequencePairAlignment
		// this.output stores the actual output of Viterbi transitions
		Sequence providedOutput;
		ViterbiNode[] nodePath;
		int latticeLength;

    ViterbiNode[][] lattice; // Entire lattic, saved only if saveLattice is true,

    public double getDelta (int ip, int stateIndex) {
      if (lattice != null) {
        return getViterbiNode (lattice, ip, stateIndex).delta;
      } else {
        throw new RuntimeException ("Attempt to called getDelta() when lattice not stored.");
      }
    }

    public State getBestState (int ip) { return getStateAtRank (ip, 0); }

    public State getStateAtRank (final int ip, int rank)
    {
      if (lattice != null) {
        Integer[] rankedStates = new Integer [numStates()];
        for (int k = 0; k < numStates(); k++)
          rankedStates[k] = new Integer (k);
        Arrays.sort (rankedStates, new Comparator () {
          public int compare (Object o, Object o1)
          {
            int i1 = ((Integer)o).intValue ();
            int i2 = ((Integer)o1).intValue ();
            return Double.compare (getDelta (ip, i1), getDelta (ip, i2));
          }
        });
        return getState (rankedStates[rank].intValue ());
      } else {
        throw new RuntimeException ("Attempt to called getMaxState() when lattice not stored.");
      }
    }

		protected ViterbiNode getViterbiNode (ViterbiNode[][] nodes, int ip, int stateIndex)
		{
			if (nodes[ip][stateIndex] == null)
				nodes[ip][stateIndex] = new ViterbiNode (ip, getState (stateIndex));
			return nodes[ip][stateIndex];
		}

		// You may pass null for output
		protected ViterbiPathBeamKL (Sequence inputSequence, Sequence outputSequence, int Bwidth)
		{
      this (inputSequence, outputSequence, false, Bwidth);
    }

    protected ViterbiPathBeamKL (Sequence inputSequence, Sequence outputSequence, boolean saveLattice, int Bwidth)
    {
			assert (inputSequence != null);
			if (logger.isLoggable (Level.FINE)) {
				logger.fine ("Starting ViterbiPath");
				logger.fine ("Input: ");
				for (int ip = 0; ip < inputSequence.size(); ip++)
					logger.fine (" " + inputSequence.get(ip));
				logger.fine ("\nOutput: ");
				if (outputSequence == null)
					logger.fine ("null");
				else
					for (int op = 0; op < outputSequence.size(); op++)
						logger.fine (" " + outputSequence.get(op));
				logger.fine ("\n");
			}

			this.input = inputSequence;
			this.providedOutput = outputSequence;
			// this.output is set at the end when we know the exact outputs
			// of the Viterbi path.  Note that in some cases the "output"
			// may be provided non-null as an argument to this method, but the
			// "output" objects may not be fully-specified even though they do
			// provide some restrictions.  This is why we set our this.output
			// from the outputs provided by the transition iterator along the
			// Viterbi path.

			// xxx Not very efficient when the lattice is actually sparse,
			// especially when the number of states is large and the
			// sequence is long.
			latticeLength = input.size()+1;
			int numStates = numStates();
			ViterbiNode[][] nodes = new ViterbiNode[latticeLength][numStates];


      if (saveLattice) lattice = nodes;

	  // Viterbi Forward
      // CPAL - Initial Viberbi Values

	  logger.fine ("Starting Viterbi");
      boolean anyInitialState = false;
			for (int i = 0; i < numStates; i++) {
				double initialCost = getState(i).initialCost;
				if (initialCost < INFINITE_COST) {
					ViterbiNode n = getViterbiNode (nodes, 0, i);
					n.delta = initialCost;
                    anyInitialState = true;
				}
			}

      if (!anyInitialState) {
        logger.warning ("Viterbi: No initial states!");
      }

      NBestSlist[] slists = new NBestSlist[latticeLength];

      //double NKLnodes[];
      //NKLnodes = new double[latticeLength];
      nstatesExpl = new double[latticeLength];


			for (int ip = 0; ip < latticeLength-1; ip++) {

                // CPAL - add this to construct the beam
                // ***************************************************

                // CPAL - sets up the sorted list
                slists[ip] = new NBestSlist(numStates);
                // CPAL - set the
                slists[ip].setKLMinE(beamWidth);
                slists[ip].setKLeps(KLeps);
                slists[ip].setRmin(Rmin);

                for(int i = 0 ; i< numStates ; i++){
                     if (nodes[ip][i] == null || nodes[ip][i].delta == INFINITE_COST)
						continue;
                    //State s = getState(i);
                    // CPAL - give the NB viterbi node the (cost, position)
                    NBViterbiNode cnode = new NBViterbiNode(nodes[ip][i].delta, i);
                    slists[ip].push(cnode);

                }

                // CPAL - unlike std. n-best beam we now filter the list based
                // on a KL divergence like measure
                // ***************************************************
                // use method which computes the cumulative log sum and
                // finds the point at which the sum is within KLeps
                int KLMaxPos=1;
                int RminPos=1;

                //if(KLeps > 0) {
                //    KLMaxPos = slists[ip].getKLpos();
                //} else {
                //    RminPos = slists[ip].getTHRpos();
                    //RminPos = slists[ip].getTHRposSTRAWMAN();
                //    slists[ip].setKLeps(-KLeps);
                //    KLMaxPos = slists[ip].getKLpos();
                //}
                //System.out.println(KLMaxPos + " ");
                //NKLnodes[ip] = KLMaxPos;

                if(KLeps > 0) {
                    KLMaxPos = slists[ip].getKLpos();
                    nstatesExpl[ip]=(double)KLMaxPos;
                } else if(KLeps == 0) {

                    if(Rmin > 0) {
                        RminPos = slists[ip].getTHRpos();
                    } else {
                        slists[ip].setRmin(-Rmin);
                        RminPos = slists[ip].getTHRposSTRAWMAN();
                    }
                    nstatesExpl[ip]=(double)RminPos;

                } else {
                    // Trick, negative values for KLeps mean use the max of KL an Rmin
                    slists[ip].setKLeps(-KLeps);
                    KLMaxPos = slists[ip].getKLpos();

                    //RminPos = slists[ip].getTHRpos();

                    if(Rmin > 0) {
                        RminPos = slists[ip].getTHRpos();
                    } else {
                        slists[ip].setRmin(-Rmin);
                        RminPos = slists[ip].getTHRposSTRAWMAN();
                    }

                    if(KLMaxPos > RminPos) {
                        nstatesExpl[ip]=(double)KLMaxPos;
                    } else {
                        nstatesExpl[ip]=(double)RminPos;
                    }
                }
                //System.out.println(nstatesExpl[ip] + " ");

                // CPAL - we now loop over the NBestSlist
				// for (int i = 0; i < numStates; i++) {
                //for(int jj=0 ; jj< slists[ip].size(); jj++) {
                for(int jj=0 ; jj< nstatesExpl[ip]; jj++) {

                    int i = slists[ip].getPosByIndex(jj);

                    // CPAL - dont need this anymore
					// if (nodes[ip][i] == null || nodes[ip][i].delta == INFINITE_COST)
						// xxx if we end up doing this a lot,
						// we could save a list of the non-null ones
					//	continue;

					State s = getState(i);
					TransitionIterator iter = s.transitionIterator (input, ip, providedOutput, ip);
					if (logger.isLoggable (Level.FINE))
						logger.fine (" Starting Viterbi transition iteration from state "
												 + s.getName() + " on input " + input.get(ip));
					while (iter.hasNext()) {
						State destination = iter.nextState();
						if (logger.isLoggable (Level.FINE))
							logger.fine ("Viterbi[inputPos="+ip
													 +"][source="+s.getName()
													 +"][dest="+destination.getName()+"]");
						ViterbiNode destinationNode = getViterbiNode (nodes, ip+1, destination.getIndex());
						destinationNode.output = iter.getOutput();
						cost = nodes[ip][i].delta + iter.getCost();

//						System.out.println("cost = " + iter.getCost() + " (" + ip + " " + i +")");

						if (ip == latticeLength-2){// why there is multiple next states at the end of sequence????
							cost += destination.getFinalCost();

//							System.out.println("number of next states: " + iter.numberNext());
						}

						if (cost < destinationNode.delta) {
							if (logger.isLoggable (Level.FINE))
								logger.fine ("Viterbi[inputPos="+ip
														 +"][source][dest="+destination.getName()
														 +"] cost reduced to "+cost+" by source="+
														 s.getName());
							destinationNode.delta = cost;
							destinationNode.minCostPredecessor = nodes[ip][i];
						}
					}
				}
              }

			// Find the final state with minimum cost, and get the total
			// cost of the Viterbi path.
			ViterbiNode minCostNode;
			int ip = latticeLength-1;
			this.cost = INFINITE_COST;
			minCostNode = null;
			for (int i = 0; i < numStates; i++) {
				if (nodes[ip][i] == null)
					continue;
				if (nodes[ip][i].delta < this.cost) {
					minCostNode = nodes[ip][i];
					this.cost = minCostNode.delta;

//					System.out.println("this.cost = " + this.cost);
				}
			}

      if (minCostNode == null) {
        logger.warning ("Viterbi: Sequence has infinite cost.  Output will be empty...");
        this.output = new ArraySequence (new ArrayList ());
        return;
      }

			// Build the path and the output sequence.
			this.nodePath = new ViterbiNode[latticeLength];
			Object[] outputArray = new Object[input.size()];
			for (ip = latticeLength-1; ip >= 0; ip--) {
				this.nodePath[ip] = minCostNode;
				if (ip > 0){
					outputArray[ip-1] = minCostNode.output;
//					System.out.println(ip + ":" + outputArray[ip-1]);
				}


//				System.out.println(ip + ": " + latticeLength);
//				System.out.println("posit: " + minCostNode.inputPosition);
//				System.out.println("state: " + minCostNode.state.getName());
//				System.out.println("delta: " + minCostNode.delta);

				minCostNode = minCostNode.minCostPredecessor;
			}

            // Output the average number of nodes expanded
            //System.out.println("Mean Nodes Explored: " + MatrixOps.mean(nstatesExpl));

			this.output = new ArraySequence (outputArray, false);
		}

        // CPAL - done ViterbiPathBeam


		// xxx Delete this method and move it into the Viterbi constructor, just like Lattice.
		// Increment states and transitions in the tranducer with the
		// expectations from this path.  This provides for the so-called
		// "Viterbi training" approximation.
		public void incrementTransducerCounts ()
		{
			nodePath[0].state.incrementInitialCount (1.0);
			nodePath[nodePath.length-1].state.incrementFinalCount (1.0);
			for (int ip = 0; ip < nodePath.length-1; ip++) {
				TransitionIterator iter =
					nodePath[ip].state.transitionIterator (input, ip,
																								 providedOutput, ip);
				// xxx This assumes that a transition is completely
				// identified, and made unique by its destination state and
				// output.  This may not be true!
				int numIncrements = 0;
				while (iter.hasNext()) {
					if (iter.nextState().equals (nodePath[ip+1].state)
							&& iter.getOutput().equals (nodePath[ip].output)) {
						iter.incrementCount (1.0);
						numIncrements++;
					}
				}
				if (numIncrements > 1)
					throw new IllegalStateException ("More than one satisfying transition found.");
				if (numIncrements == 0)
					throw new IllegalStateException ("No satisfying transition found.");
			}
		}

		public SequencePairAlignment trimStateInfo ()
		{
			return new SequencePairAlignment (input, output, cost);
		}

		public double tokenAccuracy (Sequence referenceOutput)
		{
			int accuracy = 0;
			assert (referenceOutput.size() == output.size());
			for (int i = 0; i < output.size(); i++) {
				//logger.fine("tokenAccuracy: ref: "+referenceOutput.get(i)+" viterbi: "+output.get(i));
				if (referenceOutput.get(i).toString().equals (output.get(i).toString())) {
					accuracy++;
				}
			}
			logger.info ("Number correct: " + accuracy + " out of " + output.size());
			return ((double)accuracy)/output.size();
		}

		public double tokenAccuracy (Sequence referenceOutput, PrintWriter out)
		{
			int accuracy = 0;
			String testString;
			assert (referenceOutput.size() == output.size());
			for (int i = 0; i < output.size(); i++) {
				//logger.fine("tokenAccuracy: ref: "+referenceOutput.get(i)+" viterbi: "+output.get(i));
				testString = output.get(i).toString();
				if (out != null) {
					out.println(testString);
				}
				if (referenceOutput.get(i).toString().equals (testString)) {
					accuracy++;
				}
			}
			logger.info ("Number correct: " + accuracy + " out of " + output.size());
			return ((double)accuracy)/output.size();
		}


    public Transducer getTransducer ()
    {
      return Transducer.this;
    }


    private class ViterbiNode
		{
			int inputPosition;								// Position of input used to enter this node
			State state;											// Transducer state from which this node entered
			Object output;										// Transducer output produced on entering this node
			double delta = INFINITE_COST;
			ViterbiNode minCostPredecessor = null;
			ViterbiNode (int inputPosition, State state)
			{
				this.inputPosition = inputPosition;
				this.state = state;
			}
		}

        // CPAL - a simple node holding a cost and position of the state
        private class NBViterbiNode
        {
            double cost;
            int pos;
            NBViterbiNode(double cost, int pos)
            {
                this.cost = cost;
                this.pos = pos;
            }
        }

        private class NBestSlist
		{
			ArrayList list = new ArrayList();
            int MaxElements;
            int KLMinElements;
            int KLMaxPos;
            double KLeps;
            double Rmin;

			NBestSlist(int MaxElements)
			{
                this.MaxElements = MaxElements;
			}

            boolean setKLMinE(int KLMinElements){
                this.KLMinElements = KLMinElements;
                return true;
            }

			int size()
			{
				return list.size();
			}

			boolean empty()
			{
				return list.isEmpty();
			}

			Object pop()
			{
				return list.remove(0);
			}

            int getPosByIndex(int ii){
                NBViterbiNode tn = (NBViterbiNode)list.get(ii);
                return tn.pos;
            }

            void setKLeps(double KLeps){
                this.KLeps = KLeps;
            }

            void setRmin(double Rmin){
                this.Rmin = Rmin;
            }

            int getTHRpos(){

                 NBViterbiNode tn;
                 double lc1, lc2;


                 tn = (NBViterbiNode)list.get(0);
                 lc1 = tn.cost;
                 tn = (NBViterbiNode)list.get(list.size()-1);
                 lc2 = tn.cost;

                 double minc = lc1 - lc2;
                 double mincTHR = minc - minc*Rmin;

                 for(int i=1;i<list.size();i++){
                    tn = (NBViterbiNode)list.get(i);
                    lc1 = tn.cost - lc2;
                    if(lc1 > mincTHR){
                        return i+1;
                    }

                 }

                 return list.size();

            }

            int getTHRposSTRAWMAN(){

                 NBViterbiNode tn;
                 double lc1, lc2;


                 tn = (NBViterbiNode)list.get(0);
                 lc1 = tn.cost;

                 double mincTHR = -lc1*Rmin;

                 //double minc = lc1 - lc2;
                 //double mincTHR = minc - minc*Rmin;

                 for(int i=1;i<list.size();i++){
                    tn = (NBViterbiNode)list.get(i);
                    lc1 = -tn.cost;
                    if(lc1 < mincTHR){
                        return i+1;
                    }

                 }

                 return list.size();

            }

            int getKLpos(){

                //double KLeps = 0.1;
                double CSNLP[];
                CSNLP = new double[MaxElements];
                double worstc;
                NBViterbiNode tn;

                tn = (NBViterbiNode)list.get(list.size()-1);
                worstc = tn.cost;

                for(int i=0;i<list.size();i++){
                    tn = (NBViterbiNode)list.get(i);
                    // NOTE: sometimes we can have positive numbers !
                    // double lc = tn.cost;
                    double lc = tn.cost-worstc;

                    //if(lc >0){
                    //    int asdf=1;
                    //}

                    if (i==0) {
                        CSNLP[i] = lc;
                    } else {
                        CSNLP[i] = sumNegLogProb(CSNLP[i-1], lc);
                    }
                }

                // normalize
                for(int i=0;i<list.size();i++){
                    CSNLP[i]=CSNLP[i]-CSNLP[list.size()-1];
                    if(CSNLP[i] < KLeps){
                        KLMaxPos = i+1;
                        if(KLMaxPos >= KLMinElements) {
                            return KLMaxPos;
                        } else if(list.size() > KLMinElements){
                            return KLMinElements;
                        }
                    }
                }

                KLMaxPos = list.size();
                return KLMaxPos;
            }

			ArrayList push(NBViterbiNode vn)
			{
                double tc = vn.cost;
                boolean atEnd = true;

                for(int i=0;i<list.size();i++){
                    NBViterbiNode tn = (NBViterbiNode)list.get(i);
                    double lc = tn.cost;
                    if(tc < lc){
                        list.add(i,vn);
                        atEnd = false;
                        break;
                    }
                }

                if(atEnd) {
                    list.add(vn);
                }

                // CPAL - if the list is too big,
                // remove the first, largest cost element
                if(list.size()>MaxElements) {
                    list.remove(MaxElements);
                }

				//double f = o.totalCost[o.nextBestStateIndex];
				//boolean atEnd = true;
				//for(int i=0; i<list.size(); i++){
				//	ASearchNode_NBest tempNode = (ASearchNode_NBest)list.get(i);
				//	double f1 = tempNode.totalCost[tempNode.nextBestStateIndex];
				//	if(f < f1) {
				//		list.add(i, o);
				//		atEnd = false;
				//		break;
				//	}
				//}

				//if(atEnd) list.add(o);

				return list;
			}
		} // CPAL - end NBestSlist

	} // end of ViterbiPathBeamKL
    // CPAL - Done BeamKL version



    // CPAL - added this backward viterbi beam
    //
    public class ViterbiPathBeamB extends SequencePairAlignment
	{
		// double cost inherited from SequencePairAlignment
		// Sequence input, output inherited from SequencePairAlignment
		// this.output stores the actual output of Viterbi transitions
		Sequence providedOutput;
		ViterbiNode[] nodePath;
		int latticeLength;

    ViterbiNode[][] lattice; // Entire lattic, saved only if saveLattice is true,

    public double getDelta (int ip, int stateIndex) {
      if (lattice != null) {
        return getViterbiNode (lattice, ip, stateIndex).delta;
      } else {
        throw new RuntimeException ("Attempt to called getDelta() when lattice not stored.");
      }
    }

    public State getBestState (int ip) { return getStateAtRank (ip, 0); }

    public State getStateAtRank (final int ip, int rank)
    {
      if (lattice != null) {
        Integer[] rankedStates = new Integer [numStates()];
        for (int k = 0; k < numStates(); k++)
          rankedStates[k] = new Integer (k);
        Arrays.sort (rankedStates, new Comparator () {
          public int compare (Object o, Object o1)
          {
            int i1 = ((Integer)o).intValue ();
            int i2 = ((Integer)o1).intValue ();
            return Double.compare (getDelta (ip, i1), getDelta (ip, i2));
          }
        });
        return getState (rankedStates[rank].intValue ());
      } else {
        throw new RuntimeException ("Attempt to called getMaxState() when lattice not stored.");
      }
    }

		protected ViterbiNode getViterbiNode (ViterbiNode[][] nodes, int ip, int stateIndex)
		{
			if (nodes[ip][stateIndex] == null)
				nodes[ip][stateIndex] = new ViterbiNode (ip, getState (stateIndex));
			return nodes[ip][stateIndex];
		}

		// You may pass null for output
		protected ViterbiPathBeamB (Sequence inputSequence, Sequence outputSequence, int Bwidth)
		{
      this (inputSequence, outputSequence, false, Bwidth);
    }


    protected ViterbiPathBeamB (Sequence inputSequence, Sequence outputSequence, boolean saveLattice, int Bwidth)
        {
                assert (inputSequence != null);
                if (logger.isLoggable (Level.FINE)) {
                    logger.fine ("Starting ViterbiPath");
                    logger.fine ("Input: ");
                    for (int ip = 0; ip < inputSequence.size(); ip++)
                        logger.fine (" " + inputSequence.get(ip));
                    logger.fine ("\nOutput: ");
                    if (outputSequence == null)
                        logger.fine ("null");
                    else
                        for (int op = 0; op < outputSequence.size(); op++)
                            logger.fine (" " + outputSequence.get(op));
                    logger.fine ("\n");
                }

                this.input = inputSequence;
                this.providedOutput = outputSequence;
                // this.output is set at the end when we know the exact outputs
                // of the Viterbi path.  Note that in some cases the "output"
                // may be provided non-null as an argument to this method, but the
                // "output" objects may not be fully-specified even though they do
                // provide some restrictions.  This is why we set our this.output
                // from the outputs provided by the transition iterator along the
                // Viterbi path.

                // xxx Not very efficient when the lattice is actually sparse,
                // especially when the number of states is large and the
                // sequence is long.
                latticeLength = input.size()+1;
                int numStates = numStates();
                // ViterbiNode[][] nodes = new ViterbiNode[latticeLength][numStates];

                // CPAL - added this for a backward beam
                ViterbiNode[][] bnodes = new ViterbiNode[latticeLength][numStates];

          if (saveLattice) lattice = bnodes;


          // CPAL - Initial Backward Viterbi Values
          logger.fine("Starting Backward Viterbi Computation");
          boolean anyFinalState = false;
            for(int i=0;i<numStates;i++){
                double finalCost = getState(i).finalCost;
                if(finalCost < INFINITE_COST) {
                    ViterbiNode n = getViterbiNode(bnodes, latticeLength-1, i);
                    n.delta = finalCost;
                    anyFinalState = true;
                    State s = getState(i);
                    n.output = s.getName();
                }
            }

          if (!anyFinalState) {
            logger.warning ("Viterbi: No final states!");
          }

          // CPAL - a backward lattice structure
          NBestSlist[] slistsB = new NBestSlist[latticeLength];

          // CPAL - BACKWARDS viterbi beam computation

                for (int ip = latticeLength-2; ip >= 0; ip--) {

                    // CPAL - add this to construct the beam
                    // ***************************************************

                    // CPAL - create a sorted list for position ip+1
                    // CPAL - an experiment to see if keeping the full state space
                    //      at the last state helps
                    //if (ip == latticeLength-2) {
                    //    slistsB[ip+1] = new NBestSlist(latticeLength);
                    //} else {
                    //    slistsB[ip+1] = new NBestSlist(Bwidth);
                    //}

                    // CPAL - create a sorted list for position ip+1
                    // CPAL - tried some experiments setting this to Bwidth/2
                    slistsB[ip+1] = new NBestSlist(Bwidth);

                    // make the initial beam at (ip+1)
                    for(int i = 0 ; i< numStates ; i++){
                         if (bnodes[ip+1][i] == null || bnodes[ip+1][i].delta == INFINITE_COST)
                            continue;
                        //State s = getState(i);
                        // CPAL - give the NB viterbi node the (cost, position)

                        NBViterbiNode cnode = new NBViterbiNode(bnodes[ip+1][i].delta, i);
                        slistsB[ip+1].push(cnode);

                    }
                    // ***************************************************

                    // CPAL - Reminder: BACKWARDS, we going are
                    // CPAL - we now loop over the NBestSlist
                    for (int i = 0; i < numStates; i++) {
                        // CPAL - this time we are going to loop through
                        //      the states of the previous node (indexed by ip)
                        //      we will not consider states in (ip+1) which are not
                        //      contained in the "beam" at (ip+1)

                        State s = getState(i);
                        ViterbiNode currentNode = getViterbiNode (bnodes, ip, s.getIndex());


                        // CPAL - when going backwards, we only check for null
                        if (bnodes[ip][i] == null)
                        // || bnodes[ip][i].delta == INFINITE_COST)
                        // xxx if we end up doing this a lot,
                        // we could save a list of the non-null ones
                        	continue;

                        TransitionIterator iter = s.transitionIterator (input, ip, providedOutput, ip);
                        // CPAL - Need someting analogous to this
                        // currentNode.output = iter.getOutput();
                        // CPAL - ah ha! this is whatcha want...
                        currentNode.output = s.getName();



                        if (logger.isLoggable (Level.FINE))
                            logger.fine (" Starting Backward Viterbi transition iteration from state "
                                                     + s.getName() + " on input " + input.get(ip));
                        while (iter.hasNext()) {
                            State destination = iter.nextState();
                            if (logger.isLoggable (Level.FINE))
                                logger.fine ("Backward Viterbi[inputPos="+ip
                                                         +"][source="+s.getName()
                                                         +"][dest="+destination.getName()+"]");

                            // CPAL - the forward way goes:
                            // ViterbiNode destinationNode = getViterbiNode (nodes, ip+1, destination.getIndex());
                            // destinationNode.output = iter.getOutput();
                            // cost = nodes[ip][i].delta + iter.getCost();

                            // CPAL - when going backwards we do it a little differently ;-)

                            int j = destination.getIndex();

                            boolean inbeam = slistsB[ip+1].haspos(j);

                            // CPAL - if the state is in the beam, proceed to process it
                            // Note: it should also be the case that there are no null states in the beam
                            if(inbeam){

                                ViterbiNode destinationNode = bnodes[ip+1][j];

                                //if(destinationNode != null){
                                double transitionCost = iter.getCost();
                                assert(!Double.isNaN(transitionCost));
                                cost = destinationNode.delta + transitionCost;
                                //}

    //						    System.out.println("cost = " + iter.getCost() + " (" + ip + " " + i +")");

                                if (ip == 0){// why there is multiple next states at the end of sequence????
                                    cost += s.getInitialCost();
                                    // System.out.println("number of next states: " + iter.numberNext());
                                }

                                //if (currentNode.delta == null) {
                                    // Initialize the cost of this node
                                //    currentNode.delta = cost;
                                    // CPAL - March 3. better check that this is still going to make sense later
                                //    currentNode.minCostPredecessor = nodes[ip+1][j];
                                //}else

                                if (cost < currentNode.delta) {
                                    if (logger.isLoggable (Level.FINE))
                                        logger.fine ("Backward Viterbi[inputPos="+ip
                                                                +"][source][dest="+s.getName()
                                                                +"] cost reduced to "+cost+" by source="+
                                                               destination.getName());
                                 currentNode.delta = cost;
                                 // CPAL - March 3. better check that this is still going to make sense later
                                 currentNode.minCostPredecessor = bnodes[ip+1][j];
                                }
                            }
                        }
                    }
                  }

                // CPAL - almost done backward pass
                //      - just need to create a sorted list for position 0
                    slistsB[0] = new NBestSlist(Bwidth);

                    for(int i = 0 ; i< numStates ; i++){
                         if (bnodes[0][i] == null || bnodes[0][i].delta == INFINITE_COST)
                            continue;
                        //State s = getState(i);
                        // CPAL - give the NB viterbi node the (cost, position)
                        NBViterbiNode cnode = new NBViterbiNode(bnodes[0][i].delta, i);
                        slistsB[0].push(cnode);
                    }

                // CPAL - done the backward pass

                // Normally, we would:
                // Find the final state with minimum cost, and get the total
                // cost of the Viterbi path.
                // In a simple Backward viterbi, we find the initial state with minimum cost
                // and we get the total cost of the backward viterbi path

                ViterbiNode minCostNode;
                // int ip = latticeLength-1;
                int ip = 0;
                this.cost = INFINITE_COST;
                minCostNode = null;
                for (int i = 0; i < numStates; i++) {
                    if (bnodes[ip][i] == null)
                        continue;
                    if (bnodes[ip][i].delta < this.cost) {
                        minCostNode = bnodes[ip][i];
                        this.cost = minCostNode.delta;
//					    System.out.println("this.cost = " + this.cost);
                    }
                }

          if (minCostNode == null) {
            logger.warning ("Viterbi: Sequence has infinite cost.  Output will be empty...");
            this.output = new ArraySequence (new ArrayList ());
            return;
          }

                // CPAL - Check that the backward viterbi path makes sense
                // Build the path and the output sequence.
                this.nodePath = new ViterbiNode[latticeLength];
                Object[] outputArray = new Object[input.size()];
                //for (ip = latticeLength-1; ip >= 0; ip--) {
                for (ip = 0; ip <= latticeLength-1; ip++){
                    this.nodePath[ip] = minCostNode;
                    if (ip > 0){
                        outputArray[ip-1] = minCostNode.output;
//					System.out.println(ip + ":" + outputArray[ip-1]);
                    }


//				System.out.println(ip + ": " + latticeLength);
//				System.out.println("posit: " + minCostNode.inputPosition);
//				System.out.println("state: " + minCostNode.state.getName());
//				System.out.println("delta: " + minCostNode.delta);

                    minCostNode = minCostNode.minCostPredecessor;
                }

                // CPAL - here is the final output
                this.output = new ArraySequence (outputArray, false);
            }

		// xxx Delete this method and move it into the Viterbi constructor, just like Lattice.
		// Increment states and transitions in the tranducer with the
		// expectations from this path.  This provides for the so-called
		// "Viterbi training" approximation.
		public void incrementTransducerCounts ()
		{
			nodePath[0].state.incrementInitialCount (1.0);
			nodePath[nodePath.length-1].state.incrementFinalCount (1.0);
			for (int ip = 0; ip < nodePath.length-1; ip++) {
				TransitionIterator iter =
					nodePath[ip].state.transitionIterator (input, ip,
																								 providedOutput, ip);
				// xxx This assumes that a transition is completely
				// identified, and made unique by its destination state and
				// output.  This may not be true!
				int numIncrements = 0;
				while (iter.hasNext()) {
					if (iter.nextState().equals (nodePath[ip+1].state)
							&& iter.getOutput().equals (nodePath[ip].output)) {
						iter.incrementCount (1.0);
						numIncrements++;
					}
				}
				if (numIncrements > 1)
					throw new IllegalStateException ("More than one satisfying transition found.");
				if (numIncrements == 0)
					throw new IllegalStateException ("No satisfying transition found.");
			}
		}

		public SequencePairAlignment trimStateInfo ()
		{
			return new SequencePairAlignment (input, output, cost);
		}

		public double tokenAccuracy (Sequence referenceOutput)
		{
			int accuracy = 0;
			assert (referenceOutput.size() == output.size());
			for (int i = 0; i < output.size(); i++) {
				//logger.fine("tokenAccuracy: ref: "+referenceOutput.get(i)+" viterbi: "+output.get(i));
				if (referenceOutput.get(i).toString().equals (output.get(i).toString())) {
					accuracy++;
				}
			}
			logger.info ("Number correct: " + accuracy + " out of " + output.size());
			return ((double)accuracy)/output.size();
		}

		public double tokenAccuracy (Sequence referenceOutput, PrintWriter out)
		{
			int accuracy = 0;
			String testString;
			assert (referenceOutput.size() == output.size());
			for (int i = 0; i < output.size(); i++) {
				//logger.fine("tokenAccuracy: ref: "+referenceOutput.get(i)+" viterbi: "+output.get(i));
				testString = output.get(i).toString();
				if (out != null) {
					out.println(testString);
				}
				if (referenceOutput.get(i).toString().equals (testString)) {
					accuracy++;
				}
			}
			logger.info ("Number correct: " + accuracy + " out of " + output.size());
			return ((double)accuracy)/output.size();
		}


    public Transducer getTransducer ()
    {
      return Transducer.this;
    }


    private class ViterbiNode
		{
			int inputPosition;								// Position of input used to enter this node
			State state;											// Transducer state from which this node entered
			Object output;										// Transducer output produced on entering this node
			double delta = INFINITE_COST;
			ViterbiNode minCostPredecessor = null;
			ViterbiNode (int inputPosition, State state)
			{
				this.inputPosition = inputPosition;
				this.state = state;
			}
		}

        // CPAL - a simple node holding a cost and position of the state
        private class NBViterbiNode
        {
            double cost;
            int pos;
            NBViterbiNode(double cost, int pos)
            {
                this.cost = cost;
                this.pos = pos;
            }
        }

        private class NBestSlist
		{
			ArrayList list = new ArrayList();
            int MaxElements;

			NBestSlist(int MaxElements)
			{
                this.MaxElements = MaxElements;
			}

			int size()
			{
				return list.size();
			}

			boolean empty()
			{
				return list.isEmpty();
			}

			Object pop()
			{
				return list.remove(0);
			}

            int getPosByIndex(int ii){
                NBViterbiNode tn = (NBViterbiNode)list.get(ii);
                return tn.pos;
            }

            NBViterbiNode getNBVNodeByIndex(int ii){
                NBViterbiNode tn = (NBViterbiNode)list.get(ii);
                return tn;
            }

            boolean haspos(int ipos){
                for(int i=0;i<list.size();i++){
                    NBViterbiNode tn = (NBViterbiNode)list.get(i);
                    int lp = tn.pos;
                    if(lp == ipos)
                        return true;
                }
                return false;
            }

            // CPAL - combine
            //      - adds elements to list, if the index is not in the list
            //      - if the index is in the list, the new value is added to the existing one
            boolean combine(NBViterbiNode vn){
                for(int i=0;i<list.size();i++){
                    NBViterbiNode tn = (NBViterbiNode)list.get(i);
                    int lp = tn.pos;
                    if(tn.pos == vn.pos){
                        tn.cost += vn.cost;
                        return true;
                    }
                }
                // If we make it here, this position was not on the list
                // so we simply add the node to the list
                list.add(vn);
                return false;
            }

			ArrayList push(NBViterbiNode vn)
			{
                double tc = vn.cost;
                boolean atEnd = true;

                for(int i=0;i<list.size();i++){
                    NBViterbiNode tn = (NBViterbiNode)list.get(i);
                    double lc = tn.cost;
                    if(tc < lc){
                        list.add(i,vn);
                        atEnd = false;
                        break;
                    }
                }

                if(atEnd) {
                    list.add(vn);
                }

                // CPAL - if the list is too big,
                // remove the first, largest cost element
                if(list.size()>MaxElements) {
                    list.remove(MaxElements);
                }

				//double f = o.totalCost[o.nextBestStateIndex];
				//boolean atEnd = true;
				//for(int i=0; i<list.size(); i++){
				//	ASearchNode_NBest tempNode = (ASearchNode_NBest)list.get(i);
				//	double f1 = tempNode.totalCost[tempNode.nextBestStateIndex];
				//	if(f < f1) {
				//		list.add(i, o);
				//		atEnd = false;
				//		break;
				//	}
				//}

				//if(atEnd) list.add(o);

				return list;
			}
		} // CPAL - end NBestSlist

	} // end of ViterbiPathBeamB
    // CPAL - Done Backward Beam version

        // CPAL - added this ViberbiPathBeamFB
        //      - implements a forwards and backwards beam
    public class ViterbiPathBeamFB extends SequencePairAlignment
	{
		// double cost inherited from SequencePairAlignment
		// Sequence input, output inherited from SequencePairAlignment
		// this.output stores the actual output of Viterbi transitions
		Sequence providedOutput;
		ViterbiNode[] nodePath;
		int latticeLength;

    ViterbiNode[][] lattice; // Entire lattice, saved only if saveLattice is true,

    public double getDelta (int ip, int stateIndex) {
      if (lattice != null) {
        return getViterbiNode (lattice, ip, stateIndex).delta;
      } else {
        throw new RuntimeException ("Attempt to called getDelta() when lattice not stored.");
      }
    }

    public State getBestState (int ip) { return getStateAtRank (ip, 0); }

    public State getStateAtRank (final int ip, int rank)
    {
      if (lattice != null) {
        Integer[] rankedStates = new Integer [numStates()];
        for (int k = 0; k < numStates(); k++)
          rankedStates[k] = new Integer (k);
        Arrays.sort (rankedStates, new Comparator () {
          public int compare (Object o, Object o1)
          {
            int i1 = ((Integer)o).intValue ();
            int i2 = ((Integer)o1).intValue ();
            return Double.compare (getDelta (ip, i1), getDelta (ip, i2));
          }
        });
        return getState (rankedStates[rank].intValue ());
      } else {
        throw new RuntimeException ("Attempt to called getMaxState() when lattice not stored.");
      }
    }

		protected ViterbiNode getViterbiNode (ViterbiNode[][] nodes, int ip, int stateIndex)
		{
			if (nodes[ip][stateIndex] == null)
				nodes[ip][stateIndex] = new ViterbiNode (ip, getState (stateIndex));
			return nodes[ip][stateIndex];
		}

		// You may pass null for output
		protected ViterbiPathBeamFB (Sequence inputSequence, Sequence outputSequence, int Bwidth)
		{
      this (inputSequence, outputSequence, false, Bwidth);
    }


    protected ViterbiPathBeamFB (Sequence inputSequence, Sequence outputSequence, boolean saveLattice, int Bwidth)
        {
                assert (inputSequence != null);
                if (logger.isLoggable (Level.FINE)) {
                    logger.fine ("Starting ViterbiPath");
                    logger.fine ("Input: ");
                    for (int ip = 0; ip < inputSequence.size(); ip++)
                        logger.fine (" " + inputSequence.get(ip));
                    logger.fine ("\nOutput: ");
                    if (outputSequence == null)
                        logger.fine ("null");
                    else
                        for (int op = 0; op < outputSequence.size(); op++)
                            logger.fine (" " + outputSequence.get(op));
                    logger.fine ("\n");
                }

                this.input = inputSequence;
                this.providedOutput = outputSequence;
                // this.output is set at the end when we know the exact outputs
                // of the Viterbi path.  Note that in some cases the "output"
                // may be provided non-null as an argument to this method, but the
                // "output" objects may not be fully-specified even though they do
                // provide some restrictions.  This is why we set our this.output
                // from the outputs provided by the transition iterator along the
                // Viterbi path.

                // xxx Not very efficient when the lattice is actually sparse,
                // especially when the number of states is large and the
                // sequence is long.
                latticeLength = input.size()+1;
                int numStates = numStates();
                ViterbiNode[][] nodes = new ViterbiNode[latticeLength][numStates];

                // CPAL - added this for a backward beam
                ViterbiNode[][] bnodes = new ViterbiNode[latticeLength][numStates];

                // CPAL - added this for MAX FIELD beams
                ViterbiNode[][] mfnodes = new ViterbiNode[latticeLength][numStates];

          if (saveLattice) lattice = nodes;

          // Viterbi Forward
          // CPAL - Initial Viberbi Values

          logger.fine ("Starting Viterbi");
          boolean anyInitialState = false;
                for (int i = 0; i < numStates; i++) {
                    double initialCost = getState(i).initialCost;
                    if (initialCost < INFINITE_COST) {
                        ViterbiNode n = getViterbiNode (nodes, 0, i);
                        n.delta = initialCost;
                        anyInitialState = true;
                    }
                }

          if (!anyInitialState) {
            logger.warning ("Viterbi: No initial states!");
          }

          // CPAL - Initial Backward Viterbi Values
          logger.fine("Starting Backward Viterbi Computation");
          boolean anyFinalState = false;
            for(int i=0;i<numStates;i++){
                double finalCost = getState(i).finalCost;
                if(finalCost < INFINITE_COST) {
                    ViterbiNode n = getViterbiNode(bnodes, latticeLength-1, i);
                    n.delta = finalCost;
                    anyFinalState = true;
                    State s = getState(i);
                    n.output = s.getName();
                }
            }

          if (!anyFinalState) {
            logger.warning ("Viterbi: No initial states!");
          }

          // CPAL - forward lattice structure
          NBestSlist[] slists = new NBestSlist[latticeLength];
          // CPAL - a backward lattice structure
          NBestSlist[] slistsB = new NBestSlist[latticeLength];
          // CPAL - EXPT #3
          NBestSlist[] slistsV = new NBestSlist[latticeLength];


          // CPAL - FORWARD beam viterbi computation

                for (int ip = 0; ip < latticeLength-1; ip++) {

                    // CPAL - add this to construct the beam
                    // ***************************************************

                    // CPAL - set up the beam list
                    slists[ip] = new NBestSlist(Bwidth);

                    for(int i = 0 ; i< numStates ; i++){
                         if (nodes[ip][i] == null || nodes[ip][i].delta == INFINITE_COST)
                            continue;
                        //State s = getState(i);
                        // CPAL - give the NB viterbi node the (cost, position)
                        NBViterbiNode cnode = new NBViterbiNode(nodes[ip][i].delta, i);
                        slists[ip].push(cnode);

                    }
                    // ***************************************************

                    // CPAL - we now loop over the NBestSlist
                    // for (int i = 0; i < numStates; i++) {
                    for(int jj=0 ; jj< slists[ip].size(); jj++) {

                        int i = slists[ip].getPosByIndex(jj);

                        // CPAL - dont need this anymore
                        // if (nodes[ip][i] == null || nodes[ip][i].delta == INFINITE_COST)
                            // xxx if we end up doing this a lot,
                            // we could save a list of the non-null ones
                        //	continue;

                        State s = getState(i);
                        TransitionIterator iter = s.transitionIterator (input, ip, providedOutput, ip);
                        if (logger.isLoggable (Level.FINE))
                            logger.fine (" Starting Viterbi transition iteration from state "
                                                     + s.getName() + " on input " + input.get(ip));
                        while (iter.hasNext()) {
                            State destination = iter.nextState();
                            if (logger.isLoggable (Level.FINE))
                                logger.fine ("Viterbi[inputPos="+ip
                                                         +"][source="+s.getName()
                                                         +"][dest="+destination.getName()+"]");
                            ViterbiNode destinationNode = getViterbiNode (nodes, ip+1,
                                                                                                                        destination.getIndex());
                            destinationNode.output = iter.getOutput();
                            cost = nodes[ip][i].delta + iter.getCost();

//						System.out.println("cost = " + iter.getCost() + " (" + ip + " " + i +")");

                            if (ip == latticeLength-2){// why there is multiple next states at the end of sequence????
                                cost += destination.getFinalCost();

//							System.out.println("number of next states: " + iter.numberNext());
                            }

                            if (cost < destinationNode.delta) {
                                if (logger.isLoggable (Level.FINE))
                                    logger.fine ("Viterbi[inputPos="+ip
                                                             +"][source][dest="+destination.getName()
                                                             +"] cost reduced to "+cost+" by source="+
                                                             s.getName());
                                destinationNode.delta = cost;
                                destinationNode.minCostPredecessor = nodes[ip][i];
                            }
                        }
                    }
                  }

            // CPAL - almost done forward step for forward backward beam viterbi
            //      - just need to create a sorted list for position latticeLength -1
            int lend = latticeLength -1;
            slists[lend] = new NBestSlist(Bwidth);

            for(int i = 0 ; i< numStates ; i++){
                if (nodes[lend][i] == null || nodes[lend][i].delta == INFINITE_COST)
                    continue;
                    //State s = getState(i);
                    // CPAL - give the NB viterbi node the (cost, position)
                NBViterbiNode cnode = new NBViterbiNode(nodes[lend][i].delta, i);
                slists[lend].push(cnode);
            }

        // CPAL - done forwards beam computation

            // CPAL - EXPT #3
            //      - Comptue the forward viterbi path
            // ******************************************************************
            // Find the final state with minimum cost, and get the total
			// cost of the Viterbi path.
			ViterbiNode minCostNode;
			int ip = latticeLength-1;
			this.cost = INFINITE_COST;
			minCostNode = null;
			for (int i = 0; i < numStates; i++) {
				if (nodes[ip][i] == null)
					continue;
				if (nodes[ip][i].delta < this.cost) {
					minCostNode = nodes[ip][i];
					this.cost = minCostNode.delta;

//					System.out.println("this.cost = " + this.cost);
				}
			}

            double VitFBCost = this.cost;

            if (minCostNode == null) {
                logger.warning ("Viterbi: Sequence has infinite cost.  Output will be empty...");
                this.output = new ArraySequence (new ArrayList ());
                return;
            }

			// Build the path and the output sequence.
			this.nodePath = new ViterbiNode[latticeLength];
			Object[] outputArray = new Object[input.size()];
			for (ip = latticeLength-1; ip >= 0; ip--) {
				this.nodePath[ip] = minCostNode;
				if (ip > 0){
					outputArray[ip-1] = minCostNode.output;
//					System.out.println(ip + ":" + outputArray[ip-1]);
				}


//				System.out.println(ip + ": " + latticeLength);
//				System.out.println("posit: " + minCostNode.inputPosition);
//				System.out.println("state: " + minCostNode.state.getName());
//				System.out.println("delta: " + minCostNode.delta);

				minCostNode = minCostNode.minCostPredecessor;
			}


            /// CPAL - EXPT #3
            // ******************************************************************

        // CPAL - BACKWARDS viterbi beam computation

                for (ip = latticeLength-2; ip >= 0; ip--) {

                    // CPAL - add this to construct the beam
                    // ***************************************************

                    // CPAL - create a sorted list for position ip+1
                    // CPAL - an experiment to see if keeping the full state space
                    //      at the last state helps
                    //if (ip == latticeLength-2) {
                    //    slistsB[ip+1] = new NBestSlist(latticeLength);
                    //} else {
                    //    slistsB[ip+1] = new NBestSlist(Bwidth);
                    //}

                    // CPAL - create a sorted list for position ip+1
                    // CPAL - tried some experiments setting this to Bwidth/2

                    // EXPT #3
                    // commented this out - slistsB[ip+1] = new NBestSlist(Bwidth);
                    slistsB[ip+1] = new NBestSlist(1);
                    slistsV[ip+1] = new NBestSlist(numStates);


                    // make the initial beam at (ip+1)
                    for(int i = 0 ; i< numStates ; i++){
                         if (bnodes[ip+1][i] == null || bnodes[ip+1][i].delta == INFINITE_COST)
                            continue;
                        // CPAL - for MAX FIELD we also need to query the forward nodes
                         if (nodes[ip+1][i] == null || nodes[ip+1][i].delta == INFINITE_COST)
                            continue;
                        // State s = getState(i);
                        // CPAL - give the NB viterbi node the (cost, position)
                        // CPAL - old
                        // NBViterbiNode cnode = new NBViterbiNode(bnodes[ip+1][i].delta, i);

                        // CPAL - start - MAX FIELD, we use the message from the forward pass
                        // CPAL - tried this because final cost is likely not very good
                        //      - didn't seem to make much difference
                        //if(ip == latticeLength-2) {
                        //    bnodes[ip+1][i].delta = nodes[ip+1][i].delta;
                        //} else {
                        // bnodes[ip+1][i].delta += nodes[ip+1][i].delta;

                        if(false){
                        ViterbiNode n = getViterbiNode (mfnodes, ip+1, i);
                        n.delta = 0;

                        mfnodes[ip+1][i].delta = bnodes[ip+1][i].delta + nodes[ip+1][i].delta;


                        NBViterbiNode cnode = new NBViterbiNode(mfnodes[ip+1][i].delta, i);
                        slistsB[ip+1].push(cnode);
                        }

                        // CPAL - update this potential with forward and backward info
                        if(false){
                        bnodes[ip+1][i].delta += nodes[ip+1][i].delta;
                        NBViterbiNode cnode = new NBViterbiNode(bnodes[ip+1][i].delta, i);
                        slistsB[ip+1].push(cnode);
                        }


                        // CPAL - EXPT #3
                        // ************************************

                        if(true){

                            ViterbiNode n = getViterbiNode (mfnodes, ip+1, i);
                            n.delta = 0;

                            mfnodes[ip+1][i].delta = bnodes[ip+1][i].delta + nodes[ip+1][i].delta;
                            // Find the best combined value
                            // This should correspond to the viterbi node
                            NBViterbiNode cnode = new NBViterbiNode(mfnodes[ip+1][i].delta, i);

                            // Find the best value in the backward nodes
                            NBViterbiNode ccnode = new NBViterbiNode(bnodes[ip+1][i].delta, i);
                            // There is only one element in this sorted list
                            // Just not the viterbi node
                            int VPState = this.nodePath[ip+1].state.getIndex();

                            if(ccnode.pos != VPState) {
                                    slistsB[ip+1].push(ccnode);
                            }

                            // Determine if there are any local nodes with paths better than viterbi path
                            if(cnode.pos == VPState || mfnodes[ip+1][i].delta <= VitFBCost) {
                                // add it to the list
                                // DOUBLE CHECK that the viterbi path node always gets on this list!!
                                // numerical stuff could mess that up
                                slistsV[ip+1].push(cnode);
                            }





                        }

                        // CPAL - EXPT #3
                        // ************************************

                    }
                    // ***************************************************

                    // CPAL - Reminder: BACKWARDS, we going are
                    // CPAL - we now loop over the NBestSlist
                    for (int i = 0; i < numStates; i++) {
                        // CPAL - this time we are going to loop through
                        //      the states of the previous node (indexed by ip)
                        //      we will not consider states in (ip+1) which are not
                        //      contained in the "beam" at (ip+1)

                        State s = getState(i);
                        ViterbiNode currentNode = getViterbiNode (bnodes, ip, s.getIndex());


                        // CPAL - when going backwards, we only check for null
                        if (bnodes[ip][i] == null)
                        // || bnodes[ip][i].delta == INFINITE_COST)
                        // xxx if we end up doing this a lot,
                        // we could save a list of the non-null ones
                        	continue;

                        TransitionIterator iter = s.transitionIterator (input, ip, providedOutput, ip);
                        // CPAL - Need someting analogous to this
                        // currentNode.output = iter.getOutput();
                        // CPAL - ah ha! this is whatcha want...
                        currentNode.output = s.getName();



                        if (logger.isLoggable (Level.FINE))
                            logger.fine (" Starting Backward Viterbi transition iteration from state "
                                                     + s.getName() + " on input " + input.get(ip));
                        while (iter.hasNext()) {
                            State destination = iter.nextState();
                            if (logger.isLoggable (Level.FINE))
                                logger.fine ("Backward Viterbi[inputPos="+ip
                                                         +"][source="+s.getName()
                                                         +"][dest="+destination.getName()+"]");

                            // CPAL - the forward way goes:
                            // ViterbiNode destinationNode = getViterbiNode (nodes, ip+1, destination.getIndex());
                            // destinationNode.output = iter.getOutput();
                            // cost = nodes[ip][i].delta + iter.getCost();

                            // CPAL - when going backwards we do it a little differently ;-)

                            int j = destination.getIndex();
                            // CPAL - start - used this for a pretty good hack at the backward pass
                            //boolean inbeam;
                            //boolean inBbeam = slistsB[ip+1].haspos(j);
                            //boolean inFbeam = slists[ip+1].haspos(j);
                            //inbeam = inBbeam || inFbeam;
                            // CPAL - done - used this for a pretty good hack at the backward pass


                            // EXPT #3
                            // boolean inbeam = slistsB[ip+1].haspos(j);
                            boolean inbeamORBvit;
                            boolean inBbeam = slistsB[ip+1].haspos(j);
                            boolean inVlist = slistsV[ip+1].haspos(j);
                            inbeamORBvit = inBbeam || inVlist;

                            // CPAL - if the state is in the beam, proceed to process it
                            // Note: it should also be the case that there are no null states in the beam
                            if(inbeamORBvit){

                                ViterbiNode destinationNode = bnodes[ip+1][j];

                                //if(destinationNode != null){
                                double transitionCost = iter.getCost();
                                assert(!Double.isNaN(transitionCost));
                                cost = destinationNode.delta + transitionCost;
                                //}

    //						    System.out.println("cost = " + iter.getCost() + " (" + ip + " " + i +")");

                                if (ip == 0){// why there is multiple next states at the end of sequence????
                                    cost += s.getInitialCost();
                                    // System.out.println("number of next states: " + iter.numberNext());
                                }

                                //if (currentNode.delta == null) {
                                    // Initialize the cost of this node
                                //    currentNode.delta = cost;
                                    // CPAL - March 3. better check that this is still going to make sense later
                                //    currentNode.minCostPredecessor = nodes[ip+1][j];
                                //}else

                                if (cost < currentNode.delta) {
                                    if (logger.isLoggable (Level.FINE))
                                        logger.fine ("Backward Viterbi[inputPos="+ip
                                                                +"][source][dest="+s.getName()
                                                                +"] cost reduced to "+cost+" by source="+
                                                               destination.getName());
                                 currentNode.delta = cost;
                                 // CPAL - March 3. better check that this is still going to make sense later
                                 currentNode.minCostPredecessor = bnodes[ip+1][j];
                                }
                            }
                        }
                    }
                  }

                // CPAL - almost done backward pass
                //      - just need to create a sorted list for position 0
                    slistsB[0] = new NBestSlist(Bwidth);

                    for(int i = 0 ; i< numStates ; i++){
                         if (bnodes[0][i] == null || bnodes[0][i].delta == INFINITE_COST)
                            continue;
                        //State s = getState(i);
                        // CPAL - give the NB viterbi node the (cost, position)
                        NBViterbiNode cnode = new NBViterbiNode(bnodes[0][i].delta, i);
                        slistsB[0].push(cnode);
                    }

                // CPAL - done the backward pass

 // CPAL - start - Iterate Max Field
 //**************************************************************

 //**************************************************************
 // CPAL - done - Iterate Max Field

 Object[] outputArray2 = new Object[input.size()];

  if(true){

                // Normally, we would:
                // Find the final state with minimum cost, and get the total
                // cost of the Viterbi path.
                // In a simple Backward viterbi, we find the initial state with minimum cost
                // and we get the total cost of the backward viterbi path

                //ViterbiNode minCostNode;
                // CPAL - now defined earlier

                // int ip = latticeLength-1;
                // int ip; - now defined earlier
                ip = 0;
                this.cost = INFINITE_COST;
                minCostNode = null;
                for (int i = 0; i < numStates; i++) {
                    if (bnodes[ip][i] == null)
                        continue;
                    if (bnodes[ip][i].delta < this.cost) {
                        minCostNode = bnodes[ip][i];
                        this.cost = minCostNode.delta;
//					    System.out.println("this.cost = " + this.cost);
                    }
                }

          if (minCostNode == null) {
            logger.warning ("Viterbi: Sequence has infinite cost.  Output will be empty...");
            this.output = new ArraySequence (new ArrayList ());
            return;
          }

                // CPAL - Check that the backward viterbi path makes sense
                // Build the path and the output sequence.
                this.nodePath = new ViterbiNode[latticeLength];
                //Object[] outputArray2 = new Object[input.size()];
                //outputArray2 = new Object[input.size()];

                //for (ip = latticeLength-1; ip >= 0; ip--) {
                for (ip = 0; ip <= latticeLength-1; ip++){
                    this.nodePath[ip] = minCostNode;
                    if (ip > 0){
                        outputArray2[ip-1] = minCostNode.output;
//					System.out.println(ip + ":" + outputArray[ip-1]);
                    }


//				System.out.println(ip + ": " + latticeLength);
//				System.out.println("posit: " + minCostNode.inputPosition);
//				System.out.println("state: " + minCostNode.state.getName());
//				System.out.println("delta: " + minCostNode.delta);
                    minCostNode = minCostNode.minCostPredecessor;
                }

        }


                // CPAL - alternative way to estimate path
                //      - Max Field -> take max element from each variable
                if(false) {
                    for(ip=1 ; ip < latticeLength-1; ip++) {
                        int i = slistsB[ip].getPosByIndex(0);
                        minCostNode = bnodes[ip][i];
                        outputArray[ip-1]=minCostNode.output;
                    }
                }

                // CPAL - what happens if we combine the beams?
                // ****************************************************************
                if(false) {
                NBestSlist[] slistsFB = new NBestSlist[latticeLength];
                NBViterbiNode tn;

                for(ip=0;ip<latticeLength;ip++){
                    // CPAL - create a new composite beam list
                    slistsFB[ip] = new NBestSlist(Bwidth);

                    // CPAL - go through the lists, first forward lists
                    for(int jj=0 ; jj< slists[ip].size(); jj++) {
                        tn = slists[ip].getNBVNodeByIndex(jj);
                        slistsFB[ip].combine(tn);
                    }

                    // CPAL - now the backwards lists
                    for(int jj=0 ; jj< slistsB[ip].size(); jj++) {
                        tn = slistsB[ip].getNBVNodeByIndex(jj);
                        slistsFB[ip].combine(tn);
                    }

                }
                }
                // CPAL - done combining beams
                // ****************************************************************

                // CPAL - here is the final output
                this.output = new ArraySequence (outputArray2, false);
            }


    // CPAL - Could reversing the order of the beam, then updates work better?
    // protected ViterbiPathBeamBF (Sequence inputSequence, Sequence outputSequence, boolean saveLattice, int Bwidth)


		// xxx Delete this method and move it into the Viterbi constructor, just like Lattice.
		// Increment states and transitions in the tranducer with the
		// expectations from this path.  This provides for the so-called
		// "Viterbi training" approximation.
		public void incrementTransducerCounts ()
		{
			nodePath[0].state.incrementInitialCount (1.0);
			nodePath[nodePath.length-1].state.incrementFinalCount (1.0);
			for (int ip = 0; ip < nodePath.length-1; ip++) {
				TransitionIterator iter =
					nodePath[ip].state.transitionIterator (input, ip,
																								 providedOutput, ip);
				// xxx This assumes that a transition is completely
				// identified, and made unique by its destination state and
				// output.  This may not be true!
				int numIncrements = 0;
				while (iter.hasNext()) {
					if (iter.nextState().equals (nodePath[ip+1].state)
							&& iter.getOutput().equals (nodePath[ip].output)) {
						iter.incrementCount (1.0);
						numIncrements++;
					}
				}
				if (numIncrements > 1)
					throw new IllegalStateException ("More than one satisfying transition found.");
				if (numIncrements == 0)
					throw new IllegalStateException ("No satisfying transition found.");
			}
		}

		public SequencePairAlignment trimStateInfo ()
		{
			return new SequencePairAlignment (input, output, cost);
		}

		public double tokenAccuracy (Sequence referenceOutput)
		{
			int accuracy = 0;
			assert (referenceOutput.size() == output.size());
			for (int i = 0; i < output.size(); i++) {
				//logger.fine("tokenAccuracy: ref: "+referenceOutput.get(i)+" viterbi: "+output.get(i));
				if (referenceOutput.get(i).toString().equals (output.get(i).toString())) {
					accuracy++;
				}
			}
			logger.info ("Number correct: " + accuracy + " out of " + output.size());
			return ((double)accuracy)/output.size();
		}

		public double tokenAccuracy (Sequence referenceOutput, PrintWriter out)
		{
			int accuracy = 0;
			String testString;
			assert (referenceOutput.size() == output.size());
			for (int i = 0; i < output.size(); i++) {
				//logger.fine("tokenAccuracy: ref: "+referenceOutput.get(i)+" viterbi: "+output.get(i));
				testString = output.get(i).toString();
				if (out != null) {
					out.println(testString);
				}
				if (referenceOutput.get(i).toString().equals (testString)) {
					accuracy++;
				}
			}
			logger.info ("Number correct: " + accuracy + " out of " + output.size());
			return ((double)accuracy)/output.size();
		}


    public Transducer getTransducer ()
    {
      return Transducer.this;
    }


    private class ViterbiNode
		{
			int inputPosition;								// Position of input used to enter this node
			State state;											// Transducer state from which this node entered
			Object output;										// Transducer output produced on entering this node
			double delta = INFINITE_COST;
			ViterbiNode minCostPredecessor = null;
			ViterbiNode (int inputPosition, State state)
			{
				this.inputPosition = inputPosition;
				this.state = state;
			}
		}

        // CPAL - a simple node holding a cost and position of the state
        private class NBViterbiNode
        {
            double cost;
            int pos;
            NBViterbiNode(double cost, int pos)
            {
                this.cost = cost;
                this.pos = pos;
            }
        }

        private class NBestSlist
		{
			ArrayList list = new ArrayList();
            int MaxElements;

			NBestSlist(int MaxElements)
			{
                this.MaxElements = MaxElements;
			}

			int size()
			{
				return list.size();
			}

			boolean empty()
			{
				return list.isEmpty();
			}

			Object pop()
			{
				return list.remove(0);
			}

            int getPosByIndex(int ii){
                NBViterbiNode tn = (NBViterbiNode)list.get(ii);
                return tn.pos;
            }

            double getCostByIndex(int ii){
                NBViterbiNode tn = (NBViterbiNode)list.get(ii);
                return tn.cost;
            }

            NBViterbiNode getNBVNodeByIndex(int ii){
                NBViterbiNode tn = (NBViterbiNode)list.get(ii);
                return tn;
            }

            boolean haspos(int ipos){
                for(int i=0;i<list.size();i++){
                    NBViterbiNode tn = (NBViterbiNode)list.get(i);
                    int lp = tn.pos;
                    if(lp == ipos)
                        return true;
                }
                return false;
            }

            // CPAL - combine
            //      - adds elements to list, if the index is not in the list
            //      - if the index is in the list, the new value is added to the existing one
            boolean combine(NBViterbiNode vn){
                for(int i=0;i<list.size();i++){
                    NBViterbiNode tn = (NBViterbiNode)list.get(i);
                    int lp = tn.pos;
                    if(tn.pos == vn.pos){
                        tn.cost += vn.cost;
                        return true;
                    }
                }
                // If we make it here, this position was not on the list
                // so we simply add the node to the list
                list.add(vn);
                return false;
            }

			ArrayList push(NBViterbiNode vn)
			{
                double tc = vn.cost;
                boolean atEnd = true;

                for(int i=0;i<list.size();i++){
                    NBViterbiNode tn = (NBViterbiNode)list.get(i);
                    double lc = tn.cost;
                    if(tc < lc){
                        list.add(i,vn);
                        atEnd = false;
                        break;
                    }
                }

                if(atEnd) {
                    list.add(vn);
                }

                // CPAL - if the list is too big,
                // remove the first, largest cost element
                if(list.size()>MaxElements) {
                    list.remove(MaxElements);
                }

				//double f = o.totalCost[o.nextBestStateIndex];
				//boolean atEnd = true;
				//for(int i=0; i<list.size(); i++){
				//	ASearchNode_NBest tempNode = (ASearchNode_NBest)list.get(i);
				//	double f1 = tempNode.totalCost[tempNode.nextBestStateIndex];
				//	if(f < f1) {
				//		list.add(i, o);
				//		atEnd = false;
				//		break;
				//	}
				//}

				//if(atEnd) list.add(o);

				return list;
			}
		} // CPAL - end NBestSlist

	} // end of ViterbiPathBeam
    // CPAL - Done Beam version

	//
	// Fuchun Peng fuchun@cs.umass.edu
	//
	//
	// return top N best list,
	// N best list generation involves a forward Viterbi decoding
	// and N passes of A* backward search
	//
	// sample research using N-best algorithms include speech regonition, disambiguation, and handwriting recognition.
	// 1: N-Best Search Methods Applied to Speech Recognition,  Diploma Thesis,
	//  Department of Telecommunications, Norwegian Institut of Technology, 1994
	// 2: N-Best Hidden Markov Model Supertagging for Tying with Ambiguous Keywords, Sasa Hansan, 2003
	// 3: SCHWARTZ,RICHARD,&YEN-LU CHOW. 1990.
	// The N-best algorithm: An efficient and exact procedure for finding the n most likely sentence hypotheses. ICASSP 1990
	// 4: Online handwriting reognition with Constraint N-best  decoding Jianying Hu, Michael Brown
	public class ViterbiPath_NBest extends SequencePairAlignment
	{
		// double cost inherited from SequencePairAlignment
		// Sequence input, output inherited from SequencePairAlignment
		// this.output stores the actual output of Viterbi transitions
		Sequence providedOutput;
		ViterbiNode_NBest[] nodePath;
		int latticeLength;
		int numStates;

		ViterbiNode_NBest[][] nodes;
		ViterbiNode_NBest[] finalNodePredecessor;


		protected ViterbiNode_NBest getViterbiNode (ViterbiNode_NBest[][] nodes, int ip, int stateIndex)
		{
			if (nodes[ip][stateIndex] == null)
				nodes[ip][stateIndex] = new ViterbiNode_NBest (ip, getState (stateIndex));

			if(nodes[ip][stateIndex] == null){
				throw new IllegalArgumentException("WARNING: nodes["+ip+"]["+stateIndex+"] is not successfully generated." );
			}

			return nodes[ip][stateIndex];
		}

		protected ViterbiPath_NBest (Sequence inputSequence, Sequence outputSequence, int N)
		{

			this.input = inputSequence;
			this.providedOutput = outputSequence;
			this.latticeLength = input.size()+1;
			this.numStates = numStates();

			//forward Viterbi
			nodes = new ViterbiNode_NBest[latticeLength][numStates];
			finalNodePredecessor = new ViterbiNode_NBest[numStates];// list of predecessors ranked according to final cost
			NBestForwardViterbi(nodes, finalNodePredecessor, N);

			//backward A* search
			outputNBest = NBestBackwardASearch(N);

			//combine the N best results, and produce a final output
			combineNBest();
		}


		protected void NBestForwardViterbi(ViterbiNode_NBest[][] nodes,
			ViterbiNode_NBest[] finalNodePredecessor,
			int N)
		{
			assert (input != null);
			if (logger.isLoggable (Level.FINE)) {
				logger.fine ("Starting ViterbiPath");
				logger.fine ("Input: ");
				for (int ip = 0; ip < input.size(); ip++)
					logger.fine (" " + input.get(ip));
				logger.fine ("\nOutput: ");
				if (providedOutput == null)
					logger.fine ("null");
				else
					for (int op = 0; op < providedOutput.size(); op++)
						logger.fine (" " + providedOutput.get(op));
				logger.fine ("\n");
			}

			// this.output is set at the end when we know the exact outputs
			// of the Viterbi path.  Note that in some cases the "output"
			// may be provided non-null as an argument to this method, but the
			// "output" objects may not be fully-specified even though they do
			// provide some restrictions.  This is why we set our this.output
			// from the outputs provided by the transition iterator along the
			// Viterbi path.

			// Viterbi Forward
			logger.fine ("Starting Viterbi");
			for (int i = 0; i < numStates; i++) {// position 0 is a null starting node
				double initialCost = getState(i).initialCost;
				if (initialCost < INFINITE_COST) {
					ViterbiNode_NBest n = getViterbiNode (nodes, 0, i);
					n.delta = initialCost;
				}
			}


			for (int ip = 0; ip < latticeLength-1; ip++)
				for (int i = 0; i < numStates; i++) {
					if (nodes[ip][i] == null || nodes[ip][i].delta == INFINITE_COST)
						// xxx if we end up doing this a lot,
						// we could save a list of the non-null ones
						continue;
					State s = getState(i);
					TransitionIterator iter = s.transitionIterator (input, ip, providedOutput, ip);
					if (logger.isLoggable (Level.FINE))
						logger.fine (" Starting Viterbi transition iteration from state "
												 + s.getName() + " on input " + input.get(ip));
					while (iter.hasNext()) {
						State destination = iter.nextState();
						if (logger.isLoggable (Level.FINE))
							logger.fine ("Viterbi[inputPos="+ip
													 +"][source="+s.getName()
													 +"][dest="+destination.getName()+"]");
						ViterbiNode_NBest destinationNode = getViterbiNode (nodes, ip+1,
																													destination.getIndex());
						destinationNode.output = iter.getOutput();
//						System.out.println(ip + ":" + destinationNode.state.getIndex() + " : " + iter.getOutput());
						cost = nodes[ip][i].delta + iter.getCost();
						destinationNode.iterCost[i] = iter.getCost();

//						System.out.println("cost = " + iter.getCost() + " (" + ip + " " + i +")");

						if (ip == latticeLength-2){// why there is multiple next states at the end of sequence????
							cost += destination.getFinalCost();
						}


						if (cost < destinationNode.delta) {
							if (logger.isLoggable (Level.FINE))
								logger.fine ("Viterbi[inputPos="+ip
														 +"][source][dest="+destination.getName()
														 +"] cost reduced to "+cost+" by source="+
														 s.getName());
							destinationNode.delta = cost;
						}

						destinationNode.phi[i] = cost;
					}
				}

			// sorting predecessors according to phi
			for (int ip = latticeLength - 1; ip > 0; ip--)
				for (int i = 0; i < numStates; i++) {
					if (nodes[ip][i] == null || nodes[ip][i].delta == INFINITE_COST)
						// xxx if we end up doing this a lot,
						// we could save a list of the non-null ones
						continue;
					nodes[ip][i].sortPredecessor();
				}


			//sort finalNodePredecessor
			int[] predecessorIndex = new int[numStates];
			predecessorIndex[0] = 0;
			for(int i=1; i<numStates; i++){
				boolean atEnd = true;
				for(int j=0; j<i; j++){
					if(nodes[latticeLength-1][i].delta < nodes[latticeLength-1][predecessorIndex[j]].delta){
						for(int k=i-1; k>=j; k--){
							predecessorIndex[k+1] = predecessorIndex[k];
						}
						predecessorIndex[j] = i;
						atEnd = false;
						break;
					}
				}

				if(atEnd) predecessorIndex[i] = i;
			}
			for(int i=0; i<numStates; i++){
				if(nodes[latticeLength-1][predecessorIndex[i]] != null)
					finalNodePredecessor[i] = nodes[latticeLength-1][predecessorIndex[i]];
				else
					throw new IllegalArgumentException("null node");
			}


			// Find the final state with minimum cost, and get the total
			// cost of the Viterbi path.
			ViterbiNode_NBest minCostNode;
			int ip = latticeLength-1;
			this.cost = INFINITE_COST;
			minCostNode = null;
			for (int i = 0; i < numStates; i++) {
				if (nodes[ip][i] == null)
					continue;
				if (nodes[ip][i].delta < this.cost) {
					minCostNode = nodes[ip][i];
					this.cost = minCostNode.delta;

//					System.out.println("this.cost = " + this.cost);
				}

			}

			// Build the path and the output sequence.
			this.nodePath = new ViterbiNode_NBest[latticeLength];
			Object[] outputArray = new Object[input.size()];
			for (ip = latticeLength-1; ip >= 0; ip--) {
				this.nodePath[ip] = minCostNode;
				if (ip > 0){
					outputArray[ip-1] = minCostNode.output;
//					System.out.println(ip + ":" + outputArray[ip-1]);
				}

//				System.out.println(ip + ": " + latticeLength);
//				System.out.println("posit: " + minCostNode.inputPosition
//						 + " state: " + minCostNode.state.getName()
//						 + " delta: " + minCostNode.delta);

				minCostNode = minCostNode.minCostPredecessor[0];
			}
			this.output = new ArraySequence (outputArray, false);

		}

		protected Sequence[] NBestBackwardASearch(int N)
		{

			NBestStack stack = new NBestStack();

			ASearchNode_NBest startNode = new ASearchNode_NBest(latticeLength, null);// null terminal node
			startNode.predecessorNodes = finalNodePredecessor;
			for(int i=0; i<numStates; i++){// i here is the rank
				if(finalNodePredecessor[i] == null){
					throw new IllegalArgumentException(i + ": null node");
				}

				startNode.totalCost[i] = finalNodePredecessor[i].delta + 0;//the cost for the last state to terminal node is zero
			}

			startNode.backwardCost = 0;
			startNode.nextBestStateIndex = 0;
			startNode.succeedNode = null;
			startNode.output = null;

			stack.push(startNode);// initialize the stack

			int nFound = 0;
			ArrayList hypothesisList = new ArrayList();
			ArrayList costList = new ArrayList();

//			System.out.println(startNode.totalCost[0]);
			// search iteratively for the N best hypothesis
			while(!stack.empty()){
				ASearchNode_NBest node_current = (ASearchNode_NBest) stack.pop();

//				if(node_current.inputPosition < latticeLength )
//				System.out.println("current_node: " + node_current.inputPosition
//					+ " : " + latticeLength + " "
//					+ node_current.totalCost[node_current.nextBestStateIndex]
//					+ " nextState= " + node_current.nextBestStateIndex
//					+ " state= " + node_current.state.getName()
//					+ " delta= " + nodes[node_current.inputPosition][node_current.state.getIndex()].delta
//					+ " nFound= " + nFound
//					);


				//update current node and push it back to the stack
				int nextStateIndex =  node_current.nextBestStateIndex;
				node_current.nextBestStateIndex ++;
				if ( node_current.nextBestStateIndex  < numStates ){
					stack.push(node_current);
				}

				//create a new node
				State s = node_current.predecessorNodes[nextStateIndex].state;
				ASearchNode_NBest node_next = new ASearchNode_NBest(node_current.inputPosition-1, s);
				node_next.output = nodes[node_next.inputPosition][s.getIndex()].output;
//				System.out.println( nextStateIndex + ":" + node_next.inputPosition +  ": " + s.getName() + ": " + node_next.output);

				// if s is a goal state, i.e., reaching the beginining of the sequence
				if(node_next.inputPosition == 1){
					//obtain the hypothesis by backtracking through the stored pointers

					Object[] outputArray = new Object[input.size()];
					outputArray[0] = node_next.output;
					ASearchNode_NBest v = node_current;
					int i=1;
					while(v.succeedNode != null){
						assert(i < input.size());
						outputArray[i++] = v.output;
//						System.out.println(v.state.getName());
						v = v.succeedNode;
					}

					Sequence hypo_current = new ArraySequence (outputArray, false);

					//store the new hypothesis
					hypothesisList.add(hypo_current);
					costList.add(new Double(node_current.totalCost[nextStateIndex] ));

					nFound ++;

					if(nFound >= N) break;
				}
				else{// expand new successor node
					node_next.predecessorNodes = nodes[node_next.inputPosition][s.getIndex()].minCostPredecessor;

					if(node_next.inputPosition < latticeLength-1 ){//the last symbol of the sequence
						int current_state_index = node_current.state.getIndex();
						int next_state_index    = node_next.state.getIndex();
						node_next.backwardCost = node_current.backwardCost +
							nodes[node_current.inputPosition][current_state_index].iterCost[next_state_index];
					}
					else{
						// the final node has a default cost
						node_next.backwardCost = node_next.state.getFinalCost();
					}

					ViterbiNode_NBest tempNode = nodes[node_next.inputPosition][s.getIndex()];
					for(int k=0; k<numStates; k++){
						node_next.totalCost[k] = tempNode.rankedPhi(k);
						// since rankedPhi[latticeLength-1] already contains a default final cost
						// as added in forward viterbi, there is no need to add the final cost again here.
						if(node_next.inputPosition < latticeLength-1){
							node_next.totalCost[k] +=  node_next.backwardCost;
						}
					}

					node_next.nextBestStateIndex = 0;
					node_next.succeedNode = node_current;

//					System.out.println(s.getName() + ": " + tempNode.rankedPhi(0) + " + "
//							+ " " + node_next.backwardCost
//							+ " = " + node_next.totalCost[0]);

//					if(Math.abs(node_next.totalCost[node_next.nextBestStateIndex]-this.cost) < 1)//constraint n-best
					stack.push(node_next);
				}

			}

			// store the n-best list and the costS
                        Sequence[] sequenceList = new Sequence[hypothesisList.size()];
                        costNBest = new double[hypothesisList.size()];
			for(int i=0; i<hypothesisList.size(); i++){
				sequenceList[i] = (Sequence) hypothesisList.get(i);
				costNBest[i] = ((Double)costList.get(i)).doubleValue();

//				System.out.println(i + ": " + sequenceList[i].size() + "/" + input.size() + " : " + costNBest[i]);
//				for(int j=0; j<sequenceList[i].size(); j++){
//					System.out.print(sequenceList[i].get(j) + " ");
//				}
//				System.out.println();

			}

			return sequenceList;
		}

		private class NBestStack
		{
			ArrayList list = new ArrayList();
			NBestStack()
			{
			}

			int size()
			{
				return list.size();
			}

			boolean empty()
			{
				return list.isEmpty();
			}

			Object pop()
			{
				return list.remove(0);
			}
			ArrayList push(ASearchNode_NBest o)
			{
				double f = o.totalCost[o.nextBestStateIndex];
				boolean atEnd = true;
				for(int i=0; i<list.size(); i++){
					ASearchNode_NBest tempNode = (ASearchNode_NBest)list.get(i);
					double f1 = tempNode.totalCost[tempNode.nextBestStateIndex];
					if(f < f1) {
						list.add(i, o);
						atEnd = false;
						break;
					}
				}

				if(atEnd) list.add(o);

				return list;
			}
		}

		// combine results and produce one final result
		protected void combineNBest()
		{

			Alphabet targets = inputPipe.getTargetAlphabet();
        	        assert(targets != null);

			if(numStates == 0) numStates = numStates();

			double[][] node_weight = new double[input.size()][numStates];

			double totalWeight = 0;
			for(int i=0; i<outputNBest.length; i++){
				double weight = Math.exp(-(costNBest[i]-costNBest[0])); //soft weighting
//				double weight = 1; // hard weighting

				for(int j=0; j<input.size(); j++){
					Object obj = outputNBest[i].get(j);
					int index = targets.lookupIndex(obj);

					node_weight[j][index] += weight;
				}
			}

			Object[] outputArray = new Object[input.size()];

			for(int j=0; j<input.size(); j++){
				int index = 0;
				for(int k=1; k<numStates; k++){
					if(node_weight[j][k] > node_weight[j][index]){
						index = k;
					}
				}
				outputArray[j] = targets.lookupObject(index);
			}

			this.output = new ArraySequence (outputArray, false);

		}

		private class ViterbiNode_NBest
		{
			int inputPosition;		// Position of input used to enter this node
			State state;			// Transducer state from which this node entered
			Object output;			// Transducer output produced on entering this node
			double delta = INFINITE_COST; // the highest likelihood of the partial path reaching this node
			double[] phi; // the cost of all partial path reaching this node
			double[] rankedPhi;
			ViterbiNode_NBest[] minCostPredecessor;//predecessor list ordered according to phi
			double[] iterCost; //store the transition cost from all source nodes
			ViterbiNode_NBest (int inputPosition, State state)
			{
				this.inputPosition = inputPosition;
				this.state = state;

				phi = new double[numStates];
				rankedPhi = new double[numStates];
				minCostPredecessor = new ViterbiNode_NBest[numStates];
				iterCost = new double[numStates];
				for(int i=0; i<numStates; i++){
					phi[i] = INFINITE_COST;
					minCostPredecessor[i] = null;
					iterCost[i] = INFINITE_COST;
				}
			}

			// return the cost ranking at k
			double rankedPhi(int k)
			{
//				int index = minCostPredecessor[k].state.getIndex();
//				return phi[index];
				return rankedPhi[k];
			}

			//sort acendingly according to phi
			void sortPredecessor()
			{
				if(inputPosition == 0){
					return;
				}

				int[] predecessorIndex = new int[numStates];
				predecessorIndex[0] = 0;
				for(int i=1; i<numStates; i++){

					boolean atEnd = true;
					for(int j=0; j<i; j++){
						if( phi[i]< phi[predecessorIndex[j]]){
							for(int k=i-1; k>=j; k--){
								predecessorIndex[k+1] = predecessorIndex[k];
							}
							predecessorIndex[j] = i;
							atEnd = false;
							break;
						}
					}

					if(atEnd) predecessorIndex[i] = i;
				}


				assert(inputPosition >= 1);
				for(int i=0; i<numStates; i++){
					rankedPhi[i] = phi[predecessorIndex[i]];
          minCostPredecessor[i] = getViterbiNode(nodes, inputPosition-1, predecessorIndex[i]);
//					if(nodes[inputPosition-1][predecessorIndex[i]] != null){
//						minCostPredecessor[i] = nodes[inputPosition-1][predecessorIndex[i]];
////						System.out.println(inputPosition + ": " + state.getName() +  " " +
////							i+" : " + predecessorIndex[i] + ": " + phi[predecessorIndex[i]]);
//					}
//					else{
//						throw new IllegalArgumentException("null node at position " + inputPosition + " " + predecessorIndex[i]);
//					}

				}
			}
		}

		private class ASearchNode_NBest
		{
			int inputPosition;		// Position of input used to enter this node
			State state;			// Transducer state from which this node entered
			Object output;			// Transducer output produced on entering this node

			ViterbiNode_NBest[] predecessorNodes;// a rank-ordered list of predecessor states, associated with the minCostPredecessor list

			double[] totalCost;// the total cost of a path passing through current node and having one of the predecessor states, f
			double backwardCost;// g

			int nextBestStateIndex;// keep track of the index of the next best predecessor state not yet expanded
			ASearchNode_NBest succeedNode;

			ASearchNode_NBest(int inputPosition, State state)
			{
				this.inputPosition = inputPosition;
				this.state = state;

				totalCost = new double[numStates];
//				backwardCost = state.getFinalCost();
//				predecessorNodes = new ViterbiNode_NBest[numStates];

				succeedNode = null;

			}
		}

	} // end of ViterbiPath_NBest



	/* sumNegLogProb()

		 We need to be able to sum probabilities that are represented as
		 costs (which are -log(probabilities)).  Naively, we would just
		 convert them into probabilities, sum them, and then convert them
		 back into costs.  This would be:

		 double sumNegLogProb (double a, double b) {
		   return -Math.log (Math.exp(-a) + Math.exp(-b));
		 }

		 This is how this function was originally implemented, but it
		 fails when a or b is too negative.  The machine would have the
		 resolution to represent the final cost, but not the resolution to
		 represent the intermediate exponentiated negative costs, and we
		 would get -infinity as our answer.

		 What we want is a method for getting the sum by exponentiating a
		 number that is not too large.  We can do this with the following.
		 Starting with the equation above, then:

		 sumNegProb = -log (exp(-a) + exp(-b))
		 -sumNegProb = log (exp(-a) + exp(-b))
		 exp(-sumNegProb) = exp(-a) + exp(-b)
		 exp(-sumNegProb)/exp(-a) = 1 + exp(-b)/exp(-a)
		 exp(-sumNegProb+a) = 1 + exp(-b+a)
		 -sumNegProb+a = log (1 + exp(-b+a))
		 sumNegProb = a - log (1 + exp(a-b)).

		 We want to make sure that "a-b" is negative or a small positive
		 number.  We can assure this by noticing that we could have
		 equivalently derived

		 sumNegProb = b - log (1 + exp(b-a)),

		 and we can simply select among the two alternative equations the
		 one that would have the smallest (or most negative) exponent.

	*/

	public static double sumNegLogProb (double a, double b)
	{
		if (a == Double.POSITIVE_INFINITY && b == Double.POSITIVE_INFINITY)
			return Double.POSITIVE_INFINITY;
		else if (a > b)
			return b - Math.log (1 + Math.exp(b-a));
		else
			return a - Math.log (1 + Math.exp(a-b));
	}



		
}
