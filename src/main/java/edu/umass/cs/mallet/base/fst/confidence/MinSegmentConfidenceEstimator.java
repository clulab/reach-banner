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
	 Estimates the confidence of an entire sequence by the least
	 confidence segment.
 */

public class MinSegmentConfidenceEstimator extends TransducerSequenceConfidenceEstimator
{
	TransducerConfidenceEstimator segmentEstimator;
	
	private static Logger logger = MalletLogger.getLogger(
		SegmentProductConfidenceEstimator.class.getName());


	public MinSegmentConfidenceEstimator (Transducer model,
																				TransducerConfidenceEstimator segmentConfidenceEstimator) {
		this.segmentEstimator = segmentConfidenceEstimator;
		this.model = model;
	}

	/**
		 Calculates the confidence in the tagging of a {@link Instance}.
	 */
	public double estimateConfidenceFor (Instance instance,
																			 Object[] startTags,
																			 Object[] inTags) {
		SegmentIterator iter = new SegmentIterator (model, instance, startTags, inTags);
		double lowestConfidence = 9999;
		while (iter.hasNext()) {
			Segment s = (Segment) iter.nextSegment();
			double currConf = segmentEstimator.estimateConfidenceFor (s);			
			if (currConf < lowestConfidence)
				lowestConfidence = currConf;
		}
		return lowestConfidence;
	}
}
