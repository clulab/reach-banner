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
import edu.umass.cs.mallet.base.util.PropertyList;

import java.util.*;

/**
	 Stores a {@link Sequence} and a PropertyList, used when extracting
	 features from a Sequence in a pipe for confidence prediction
*/
public class SequenceConfidenceInstance 
{
	PropertyList features;
	Instance instance;
	
	public SequenceConfidenceInstance (Instance inst) {
		this.instance = inst;
	}

	public Instance getInstance () { return this.instance; }
	public PropertyList getFeatures ()	{	return features; }
	public void setFeatureValue (String key, double value)	{
		features = PropertyList.add (key, value, features);	}
	public boolean hasFeature (String key) {
		return (features == null ? false : features.hasProperty(key));	}
	public double getFeatureValue (String key) {
		return (features == null ? 0.0 : features.lookupNumber (key));	}
}
