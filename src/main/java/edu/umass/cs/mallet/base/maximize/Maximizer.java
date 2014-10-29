/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.maximize;


public interface Maximizer
{

  public interface ByValue {
	// Returns true if it has converged
	  public boolean maximize (Maximizable.ByValue maxable);
	  public boolean maximize (Maximizable.ByValue maxable, int numIterations);
  }

	public interface ByGradient {
		// Returns true if it has converged
		public boolean maximize (Maximizable.ByGradient maxable);
		public boolean maximize (Maximizable.ByGradient maxable, int numIterations);
	}
	
	public interface ByHessian {
		// Returns true if it has converged
		public boolean maximize (Maximizable.ByHessian minable);
		public boolean maximize (Maximizable.ByHessian minable, int numIterations);
	}

	public interface ByGISUpdate {
		// Returns true if it has converged
		public boolean maximize (Maximizable.ByGISUpdate maxable);
		public boolean maximize (Maximizable.ByGISUpdate maxable, int numIterations);
	}
}
