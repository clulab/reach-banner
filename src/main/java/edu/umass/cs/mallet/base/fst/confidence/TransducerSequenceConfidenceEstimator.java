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
 * Abstract class that estimates the confidence of a {@link Sequence}
 * extracted by a {@link Transducer}.Note that this is different from
 * {@link TransducerConfidenceEstimator}, which estimates the
 * confidence for a single {@link Segment}.
 */
abstract public class TransducerSequenceConfidenceEstimator
{
	private static Logger logger = MalletLogger.getLogger(TransducerSequenceConfidenceEstimator.class.getName());

	Transducer model; // the trained Transducer which performed the
										// extractions

	/**
		 Calculates the confidence in the tagging of a {@link Sequence}.
	 */
	abstract public double estimateConfidenceFor (
		Instance instance, Object[] startTags, Object[] inTags);


	/**
		 Ranks all {@link Sequences}s in this {@link InstanceList} by
		 confidence estimate.
		 @param ilist list of segmentation instances
		 @param startTags represent the labels for the start states (B-)
		 of all segments
		 @param continueTags represent the labels for the continue state
		 (I-) of all segments
		 @return array of {@link InstanceWithConfidence}s ordered by
		 non-decreasing confidence scores, as calculated by
		 <code>estimateConfidenceFor</code>
	 */
	public InstanceWithConfidence[] rankInstancesByConfidence (InstanceList ilist,
																														 Object[] startTags,
																														 Object[] continueTags) {
		ArrayList confidenceList = new ArrayList ();
		for (int i=0; i < ilist.size(); i++) {
			Instance instance = ilist.getInstance (i);
			Sequence predicted = model.viterbiPath ((Sequence)instance.getData()).output();
			double confidence = estimateConfidenceFor (instance, startTags, continueTags);
			confidenceList.add (new InstanceWithConfidence ( instance, confidence, predicted));
			logger.info ("instance#"+i+" confidence="+confidence);
		}
		Collections.sort (confidenceList);
		InstanceWithConfidence[] ret = new InstanceWithConfidence[1];
		ret = (InstanceWithConfidence[]) confidenceList.toArray (ret);
		return ret;
	}
}
