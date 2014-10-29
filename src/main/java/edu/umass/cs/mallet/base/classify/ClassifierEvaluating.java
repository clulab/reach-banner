/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */


/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.classify;

import edu.umass.cs.mallet.base.types.InstanceList;

// xxx This interface is experimental, and may be removed.

public interface ClassifierEvaluating
{
	/** Training will terminate if "false" is returned. */
	public boolean evaluate (Classifier classifier, boolean finishedTraining, int iteration, double cost,
													 InstanceList training, InstanceList validation, InstanceList testing);
}
