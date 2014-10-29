/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.pipe;

import edu.umass.cs.mallet.base.types.AugmentableFeatureVector;
import edu.umass.cs.mallet.base.types.FeatureSequence;
import edu.umass.cs.mallet.base.types.Instance;
import java.io.*;

// This class does not insist on gettings its own Alphabet because it can rely on getting
// it from the FeatureSequence input.
/**
 * Convert the data field from a feature sequence to an augmentable feature vector.
 */
public class FeatureSequence2AugmentableFeatureVector extends Pipe
{
	boolean binary;

	public FeatureSequence2AugmentableFeatureVector (boolean binary)
	{
		this.binary = binary;
	}

	public FeatureSequence2AugmentableFeatureVector ()
	{
		this (false);
	}
	
	public Instance pipe (Instance carrier)
	{
		carrier.setData(new AugmentableFeatureVector ((FeatureSequence)carrier.getData(), binary));
		return carrier;
	}
}
