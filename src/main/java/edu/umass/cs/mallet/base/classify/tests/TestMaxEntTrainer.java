/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.classify.tests;

import edu.umass.cs.mallet.base.types.*;
import edu.umass.cs.mallet.base.classify.*;
import edu.umass.cs.mallet.base.maximize.Maximizable;
import edu.umass.cs.mallet.base.maximize.tests.TestMaximizable;
import edu.umass.cs.mallet.base.pipe.*;
import edu.umass.cs.mallet.base.util.*;
import edu.umass.cs.mallet.base.pipe.iterator.ArrayIterator;
import junit.framework.*;
import java.net.URI;

public class TestMaxEntTrainer extends TestCase
{
	public TestMaxEntTrainer (String name)
	{
		super (name);
	}

	private static Alphabet dictOfSize (int size)
	{
		Alphabet ret = new Alphabet ();
		for (int i = 0; i < size; i++)
			ret.lookupIndex ("feature"+i);
		return ret;
	}

	public void testSetGetParameters ()
	{
 		MaxEntTrainer trainer = new MaxEntTrainer();
		Alphabet fd = dictOfSize (6);
		String[] classNames = new String[] {"class0", "class1", "class2"};
		InstanceList ilist = new InstanceList (new Random(1), fd, classNames, 20);
		Maximizable.ByGradient maxable = trainer.getMaximizableTrainer (ilist);
		TestMaximizable.testGetSetParameters (maxable);
	}

	public void testRandomMaximizable ()
	{
		MaxEntTrainer trainer = new MaxEntTrainer();
		Alphabet fd = dictOfSize (6);
		String[] classNames = new String[] {"class0", "class1"};
		InstanceList ilist = new InstanceList (new Random(1), fd, classNames, 20);
		Maximizable.ByGradient maxable = trainer.getMaximizableTrainer (ilist);
		TestMaximizable.testValueAndGradient (maxable);
	}
	

	public static Test suite ()
	{
		return new TestSuite (TestMaxEntTrainer.class);
	}

	protected void setUp ()
	{
	}

	public static void main (String[] args)
	{
		junit.textui.TestRunner.run (suite());
	}
	
}
		
