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
	 Estimates the confidence of an entire sequence randomly.
 */
public class RandomSequenceConfidenceEstimator extends TransducerSequenceConfidenceEstimator
{
	
	java.util.Random generator;

	public RandomSequenceConfidenceEstimator (int seed, Transducer model) {
		generator = new Random (seed);
		this.model = model;
	}

	public RandomSequenceConfidenceEstimator (Transducer model) {
		this (1, model);
	}

	/**
		 Calculates the confidence in the tagging of an {@link Instance}.
	 */
	public double estimateConfidenceFor (Instance instance,
																			 Object[] startTags,
																			 Object[] inTags) {
		return generator.nextDouble();
	}
}

