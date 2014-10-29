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
import java.util.*;

/** Randomly assigns values between 0-1 to the confidence of a {@link
 * Segment}. Used as baseline to compare with other methods.
 */
public class RandomConfidenceEstimator extends TransducerConfidenceEstimator
{
	java.util.Random generator;
	
	public RandomConfidenceEstimator (int seed, Transducer model) {
		generator = new Random (seed);
		this.model = model;
	}

	public RandomConfidenceEstimator (Transducer model) {
		this (1, model);
	}
	
	/**
		 Randomly generate the confidence in the tagging of a {@link Segment}.
	 */
	public double estimateConfidenceFor (Segment segment, Transducer.Lattice cachedLattice) {
		return generator.nextDouble();
	}
}
