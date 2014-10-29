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
import edu.umass.cs.mallet.base.util.MalletLogger;
import java.util.logging.*;
import edu.umass.cs.mallet.base.pipe.iterator.*;
import edu.umass.cs.mallet.base.fst.*;
import java.util.*;

/**
	 Estimates the confidence of an entire sequence by the probability
	 of the Viterbi path normalized by the probabliity of the entire
	 lattice.
 */
public class ViterbiConfidenceEstimator extends TransducerSequenceConfidenceEstimator
{
	
	private static Logger logger = MalletLogger.getLogger(
		ViterbiConfidenceEstimator.class.getName());


	public ViterbiConfidenceEstimator (Transducer model) {
		this.model = model;
	}

	/**
		 Calculates the confidence in the tagging of a {@link Instance}.
	 */
	public double estimateConfidenceFor (Instance instance,
																			 Object[] startTags,
																			 Object[] inTags) {
		Transducer.Lattice lattice = model.forwardBackward ((Sequence)instance.getData());
		Transducer.ViterbiPath viterbi = model.viterbiPath ((Sequence)instance.getData());		
		return Math.exp (-viterbi.getCost() + lattice.getCost()); 
	}
}
