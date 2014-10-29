/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.minimize;

import edu.umass.cs.mallet.base.types.Matrix;

public interface Minimizer
{
	// Returns true if it has converged
	public boolean minimize (Minimizable minable);
	public boolean minimize (Minimizable minable, int numIterations);

	public interface ByGradient {
		// Returns true if it has converged
		public boolean minimize (Minimizable.ByGradient minable);
		public boolean minimize (Minimizable.ByGradient minable, int numIterations);
	}
	
	public interface ByHessian {
		// Returns true if it has converged
		public boolean minimize (Minimizable.ByHessian minable);
		public boolean minimize (Minimizable.ByHessian minable, int numIterations);
	}

	public interface ByGISUpdate {
		// Returns true if it has converged
		public boolean minimize (Minimizable.ByGISUpdate minable);
		public boolean minimize (Minimizable.ByGISUpdate minable, int numIterations);
	}
	
}
