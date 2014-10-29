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
	 Estimates the confidence of an entire sequence by the ration of the
	 probabilities of the first and second best Viterbi paths.
 */
public class ViterbiRatioConfidenceEstimator extends TransducerSequenceConfidenceEstimator
{
	
	private static Logger logger = MalletLogger.getLogger(
		SegmentProductConfidenceEstimator.class.getName());


	public ViterbiRatioConfidenceEstimator (Transducer model) {
		this.model = model;
	}

	/**
		 Calculates the confidence in the tagging of an {@link Instance}.
	 */
	public double estimateConfidenceFor (Instance instance,
																			 Object[] startTags,
																			 Object[] inTags) {
		Transducer.Lattice lattice = model.forwardBackward ((Sequence)instance.getData());
		Transducer.ViterbiPath_NBest bestViterbis = model.viterbiPath_NBest ((Sequence)instance.getData(), 2);
 		double[] costs = bestViterbis.costNBest();
		if (costs.length > 2) // then something's weird
			throw new IllegalStateException ("NBest isn't returning the N asked for.");
		return (Math.exp (-costs[0] + lattice.getCost()) / Math.exp(-costs[1] + lattice.getCost()));
	}
}

