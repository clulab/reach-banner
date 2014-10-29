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

import edu.umass.cs.mallet.base.types.*;

public interface Maximizable
{
	public int getNumParameters ();

	public void getParameters (double[] buffer);
	public double getParameter (int index);

	public void setParameters (double[] params);
	public void setParameter (int index, double value);


	public interface ByValue extends Maximizable
	{
		public double getValue ();
	}

	public interface ByGradient extends Maximizable
	{
		public void getValueGradient (double[] buffer);
		public double getValue ();
	}

	public interface ByHessian extends Maximizable.ByGradient
	{
		public void getCostHessian (double[][] buffer);
	}

	public interface ByVotedPerceptron extends Maximizable
	{
		public int getNumInstances ();
		public void getValueGradientForInstance (int instanceIndex, double[] bufffer);
	}
	
	public interface ByGISUpdate extends Maximizable
	{
		public double getValue();
		public void getGISUpdate (double[] buffer);
	}
	
}
