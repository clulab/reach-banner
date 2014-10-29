/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>

	 Maximize a function projected along a line.
 */

package edu.umass.cs.mallet.base.maximize;

public interface LineMaximizer
{
	/** Returns the last step size used. */
	public double maximize (Maximizable.ByValue maxable, double[] line,
													double initialStep);

	public interface ByGradient
	{
		/** Returns the last step size used. */
		public double maximize (Maximizable.ByGradient maxable, double[] line,
														double initialStep);
	}
	
}
