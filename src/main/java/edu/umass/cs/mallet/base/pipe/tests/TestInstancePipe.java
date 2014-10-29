/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** 
 @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.pipe.tests;

import edu.umass.cs.mallet.base.types.*;
import edu.umass.cs.mallet.base.types.tests.TestSerializable;
import edu.umass.cs.mallet.base.pipe.*;
import edu.umass.cs.mallet.base.pipe.iterator.*;
import edu.umass.cs.mallet.base.pipe.tsf.*;
import edu.umass.cs.mallet.base.util.ArrayListUtils;
import junit.framework.*;
import java.util.ArrayList;
import java.util.regex.*;
import java.io.IOException;

public class TestInstancePipe extends TestCase {
	public TestInstancePipe(String name) {
		super(name);
	}

	String[] data = new String[] { "This is the first test string", "The second test string is here", "And this is the third test string", };

	public static class Array2ArrayIterator extends Pipe {
		public Instance pipe(Instance carrier) {
			carrier.setData(new ArrayIterator((Object[]) carrier.getData()));
			return carrier;
		}
	}

	public void testOne() {
		// SerialPipes.setSuppressExceptions (false);
		Pipe p = createIteratingPipe();
		InstanceList ilist = (InstanceList) new Instance(data, null, null, null, p).getData();
		assertTrue(ilist.size() == 3);
	}

	private static Pipe createIteratingPipe() {
		Pipe p = new SerialPipes(new Pipe[] {
				new Array2ArrayIterator(),
				new IteratingPipe(new InstanceList(), new SerialPipes(new Pipe[] { new CharSequence2TokenSequence(), new TokenSequenceLowercase(), new TokenSequence2FeatureSequence(),
						new FeatureSequence2FeatureVector() })) });
		return p;
	}

	public void testTwo() {
		Pipe p = new SerialPipes(new Pipe[] {
				new Array2ArrayIterator(),
				new IteratingPipe(new InstanceList(), new SerialPipes(new Pipe[] { new CharSequence2TokenSequence(), new TokenSequenceLowercase(),
						new RegexMatches("vowel", Pattern.compile("[aeiou]")), new RegexMatches("firsthalf", Pattern.compile("[a-m]")), new RegexMatches("secondhalf", Pattern.compile("[n-z]")),
						new RegexMatches("length2", Pattern.compile("..")), new RegexMatches("length3", Pattern.compile("...")), new PrintInput(), new TokenSequence2TokenIterator(),
						new IteratingPipe(new InstanceList(), new SerialPipes(new Pipe[] {
						// new PrintInput(),
								new Token2FeatureVector(), })), })) });
		InstanceList ilist = (InstanceList) new Instance(data, null, null, null, p).getData();
		assert (ilist.size() == 19) : "list size = " + ilist.size();
		assertTrue(ilist.size() == 19);
	}

	public void testOneFromSerialized() throws IOException, ClassNotFoundException {
		// SerialPipes.setSuppressExceptions(false);
		Pipe p = createIteratingPipe();
		Pipe clone = (Pipe) TestSerializable.cloneViaSerialization(p);
		InstanceList ilist = (InstanceList) new Instance(data, null, null, null, clone).getData();
		assertTrue(ilist.size() == 3);
	}

	public static Test suite() {
		return new TestSuite(TestInstancePipe.class);
	}

	protected void setUp() {
	}

	public static void main(String[] args) {
		junit.textui.TestRunner.run(suite());
	}

}
