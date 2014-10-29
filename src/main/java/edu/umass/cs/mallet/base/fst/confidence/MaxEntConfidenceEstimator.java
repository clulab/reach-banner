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
import edu.umass.cs.mallet.base.pipe.*;
import edu.umass.cs.mallet.base.classify.*;
import edu.umass.cs.mallet.base.fst.*;
import java.util.*;

/**
 * Estimates the confidence of a {@link Segment} extracted by a {@link
 * Transducer} using a {@link MaxEnt} classifier to classify segments
 * as "correct" or "incorrect." xxx needs some interface work
 */
public class MaxEntConfidenceEstimator extends TransducerConfidenceEstimator
{
	MaxEntTrainer meTrainer;
	MaxEnt meClassifier;
	Pipe pipe;
	String correct, incorrect;
	
	public MaxEntConfidenceEstimator (Transducer model, double gaussianVariance) {
		this.model = model;
		meTrainer = new MaxEntTrainer (gaussianVariance);
	}

	public MaxEntConfidenceEstimator (Transducer model) {
		this (model, 10.0);
	}

	public MaxEnt trainClassifier (InstanceList ilist, String correct, String incorrect) {
		this.meClassifier = (MaxEnt) meTrainer.train (ilist);
		this.pipe = ilist.getPipe ();
		this.correct = correct;
		this.incorrect = incorrect;
		InfoGain ig = new InfoGain (ilist);
		int igl = Math.min (30, ig.numLocations());
		for (int i = 0; i < igl; i++)
			System.out.println ("InfoGain["+ig.getObjectAtRank(i)+"]="+ig.getValueAtRank(i));
		return this.meClassifier;
	}
	
	/**
		 Calculates the confidence in the tagging of a {@link Segment}.
	 */
	public double estimateConfidenceFor (Segment segment, Transducer.Lattice cachedLattice) {		
		Classification c = this.meClassifier.classify (new Instance (
																										 segment, segment.getTruth(), null, null, pipe));
		return c.getLabelVector().value (this.correct);
	}
}
