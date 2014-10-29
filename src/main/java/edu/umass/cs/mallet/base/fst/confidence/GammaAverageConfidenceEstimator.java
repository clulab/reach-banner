/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** 
		@author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
*/

package edu.umass.cs.mallet.base.fst.confidence;

import edu.umass.cs.mallet.base.types.*;
import edu.umass.cs.mallet.base.fst.*;
import java.util.HashMap;

/** Calculates the confidence in an extracted segment by taking the
 * average of P(s_i|o) for each state in the segment. */
public class GammaAverageConfidenceEstimator extends TransducerConfidenceEstimator
{
	HashMap string2stateIndex;
	
	public GammaAverageConfidenceEstimator (Transducer model) {
		this.model = model;
		string2stateIndex = new HashMap();
		// store state indices
		for (int i=0; i < model.numStates(); i++) {
			string2stateIndex.put (model.getState(i).getName(), new Integer (i));
		}
	}
	
	/**
		 Calculates the confidence in the tagging of a {@link Segment}.
		 @return 0-1 confidence value. higher = more confident.
	 */
	public double estimateConfidenceFor (Segment segment, Transducer.Lattice cachedLattice) {
		Sequence predSequence = segment.getPredicted ();
		Sequence input = segment.getInput ();
		Transducer.Lattice lattice = (cachedLattice==null) ? model.forwardBackward (input) :
                                             cachedLattice;
		double confidence = 0;
		for (int i=segment.getStart(); i <= segment.getEnd(); i++) 
			confidence += lattice.getGammaProbability (i+1, model.getState (stateIndexOfString ((String)predSequence.get (i))));
		return confidence/(double)segment.size();
	}
	
	private int stateIndexOfString (String s)
	{
		Integer index = (Integer) string2stateIndex.get (s);
		if (index == null)
			throw new IllegalArgumentException ("state label " + s + " not a state in transducer");
		return index.intValue();
	}
}
