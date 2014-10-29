/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package edu.umass.cs.mallet.base.types;

import edu.umass.cs.mallet.base.types.Label;

/**
	 A collection of labelings, either for a multi-label problem (all
	 labels are part of the same label dictionary), or a factorized
	 labeling, (each label is part of a different dictionary).

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class Labelings
{
	Labeling[] labels;
	
	public Labelings (Labeling[] labels)
	{
		this.labels = new Labeling[labels.length];
		System.arraycopy (labels, 0, this.labels, 0, labels.length);
	}


	int size () { return labels.length; }

	Labeling get (int i) { return labels[i]; }
	
}
