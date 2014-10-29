/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.minimize.tests;

import edu.umass.cs.mallet.base.minimize.Minimizable;
import edu.umass.cs.mallet.base.minimize.LineMinimizer;
import edu.umass.cs.mallet.base.types.Matrix;
import edu.umass.cs.mallet.base.types.DenseVector;

public class Quadratic implements Minimizable.ByGradient
{
	DenseVector parameters;
	double a, b, c;

	// A one-dimensional parabola
	public Quadratic (double a, double b, double c)
	{
		parameters = new DenseVector (1);
		this.a = a;
		this.b = b;
		this.c = c;
	}

	public Matrix getNewMatrix () { return (Matrix)parameters.cloneMatrix(); }
	public Matrix getParameters (Matrix m) { m.set(parameters); return m; }
	public double getParameter (int[] indices) { return parameters.value(indices); }
	public double getParameter () { return parameters.value (0); }

	public void setParameters (Matrix params) {
		if (params == parameters) return;
		parameters.set (params);
	}
	public void setParameter (int[] indices, double value) {
		assert (indices[0] == 0);
		parameters.setValue (0, value);
	}
	public void setParameter (double value) {
		parameters.setValue (0, value);
	}

	public double getCost () {
		double x = parameters.value(0);
		assert (a == a);
		assert (b == b);
		assert (c == c);
		assert (x == x);
		System.out.println ("getCost: a="+a+" b="+b+" c="+c+" x="+x);
		double ret = a*x*x + b*x + c;
		assert ret == ret;
		return ret;
	}

	public Matrix getCostGradient (Matrix m) {
		// This gradient points up-hill, as it should
		double x = parameters.value(0);
		((DenseVector)m).setValue (0, 2*a*x + b);
		return m;
	}
}
		
