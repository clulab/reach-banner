/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.fst.tests;

import edu.umass.cs.mallet.base.types.*;
import edu.umass.cs.mallet.base.fst.*;
import edu.umass.cs.mallet.base.pipe.*;
import edu.umass.cs.mallet.base.pipe.*;
import junit.framework.*;
import java.net.URI;
import java.util.Iterator;

public class TestFeatureTransducer extends TestCase
{
	public TestFeatureTransducer (String name)
	{
		super (name);
	}

	FeatureTransducer transducer;
	ArrayListSequence seq;
	double seqCost;

	public void setUp ()
	{
		System.out.println ("Setup");
		transducer = new FeatureTransducer ();
		FeatureTransducer t = transducer;
		t.addState ("0", 0, Transducer.INFINITE_COST,
								new String[] {"a", "b"},
								new String[] {"x", "y"},
								new double[] {44, 66},
								new String[] {"0", "1"});
		t.addState ("1", Transducer.INFINITE_COST, Transducer.INFINITE_COST,
								new String[] {"c", "d", "d"},
								new String[] {"x", "y", "z"},
								new double[] {44, 11, 66},
								new String[] {"1", "1", "2"});
		t.addState ("2", Transducer.INFINITE_COST, 8,
								new String[] {"e"},
								new String[] {"z"},
								new double[] {11},
								new String[] {"2"});

		seq = new ArrayListSequence ();
		Alphabet dict = transducer.getInputAlphabet ();
		seq.add ("a");
		seq.add ("a");
		seq.add ("b");
		seq.add ("c");
		seq.add ("d");
		seq.add ("e");

		seqCost = 0 + 44 + 44 + 66 + 44 + 66 + 11 + 8;
	}

	public void testInitialState ()
	{
		Iterator iter = transducer.initialStateIterator ();
		int count = 0;
		FeatureTransducer.State state;
		while (iter.hasNext ()) {
			count++;
			state = (FeatureTransducer.State) iter.next();
			assertTrue (state.getName().equals ("0"));
		}
		assertTrue (count == 1);
	}

	public void testForwardBackward ()
	{
		Transducer.Lattice lattice = transducer.forwardBackward (seq, false);
		System.out.println ("cost = "+lattice.getCost());
		assertTrue (lattice.getCost() == seqCost);
	}

	public void testViterbi ()
	{
		Transducer.ViterbiPath path = transducer.viterbiPath (seq);
		System.out.println ("cost = "+path.getCost());
		assertTrue (path.getCost() == seqCost);
	}

	public void testEstimate ()
	{
		transducer.setTrainable (true);
		Transducer.Lattice lattice = transducer.forwardBackward (seq, true);
		double oldCost = lattice.getCost ();
		transducer.estimate ();
		lattice = transducer.forwardBackward (seq, false);
		double newCost = lattice.getCost ();
		System.out.println ("oldCost="+oldCost+" newCost="+newCost);
		assertTrue (newCost < oldCost);
	}

	public void testIncrement ()
	{
		transducer.setTrainable (true);
		Transducer.Lattice lattice = transducer.forwardBackward (seq, true);
		double oldCost = lattice.getCost ();
		System.out.println ("State 0 transition estimator");
		Multinomial.Estimator est
			= ((FeatureTransducer.State)transducer.getState(0)).getTransitionEstimator();
		est.print();
		assertTrue (est.getCount(0) == 2.0);
		assertTrue (est.getCount(1) == 1.0);
	}
	
	public static Test suite ()
	{
		return new TestSuite (TestFeatureTransducer.class);
	}

	public static void main (String[] args)
	{
		junit.textui.TestRunner.run (suite());
	}
	
}
