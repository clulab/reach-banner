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

public interface Minimizable
{
	public Matrix getNewMatrix ();
	
	// The Matrix argument m is simply returned.
	// This makes convenient code like f.getParameters().twoNorm()
	public Matrix getParameters (Matrix m);
	public double getParameter (int[] indices);

	// If the "params" argument is the functions "live" copy, calling
	// this function merely informs the function that the parameters
	// have been changed, and this it may efficiently involve no copying.
	public void setParameters (Matrix params);
	public void setParameter (int[] indices, double value);

	public double getCost ();

	public interface ByGradient extends Minimizable
	{
		// Remember that we are minimizing costs, so this gradient should
		// point "downhill", i.e. in the direction we want to go, toward
		// improved, lower costs.
		// No!  The gradient is a slope and should always point uphill.
		// The second statement is correct, not the first.
		// The Matrix argument m is simply returned
		public Matrix getCostGradient (Matrix m);
	}

	public interface ByHessian extends Minimizable.ByGradient
	{
		public Matrix getNewHessianMatrix ();
		
		// Remember that we are minimizing costs, so this Hessian should
		// be with respect to a "downhill" gradient.
		// The Matrix argument m is simply returned
		public Matrix getCostHessian (Matrix m);
	}
	
	public interface ByGISUpdate extends Minimizable
	{
		public void getGISUpdate (Matrix m, Matrix u);
	}
	
}
