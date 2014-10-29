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

/**
 * Estimates the confidence of a {@link Segment} extracted by a {@link
 * Transducer} by performing a "constrained lattice"
 * calculation. Essentially, this sums all possible ways this segment
 * could have been extracted and normalizes.
 */
public class ConstrainedForwardBackwardConfidenceEstimator extends TransducerConfidenceEstimator
{
	
	public ConstrainedForwardBackwardConfidenceEstimator (Transducer model) {
		this.model = model;
	}
	
	/**
		 Calculates the confidence in the tagging of a {@link Segment}.
		 @return 0-1 confidence value. higher = more confident.
	 */
	public double estimateConfidenceFor (Segment segment, Transducer.Lattice cachedLattice) {
		Sequence predSequence = segment.getPredicted ();
		Sequence input = segment.getInput ();
		Transducer.Lattice lattice = (cachedLattice == null) ? model.forwardBackward (input) :
                                             cachedLattice;
		// constrained lattice
		Transducer.Lattice constrainedLattice = model.forwardBackward (input, null, segment, predSequence);
		double latticeCost = lattice.getCost ();
		double constrainedLatticeCost = constrainedLattice.getCost ();
		double confidence = Math.exp (latticeCost - constrainedLatticeCost);
		//System.err.println ("confidence: " + confidence);
		return confidence;
	}
}
