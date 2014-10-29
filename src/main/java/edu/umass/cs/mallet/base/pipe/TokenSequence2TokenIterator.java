/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package edu.umass.cs.mallet.base.pipe;

import java.io.*;
import java.util.Iterator;
import java.util.ArrayList;
import edu.umass.cs.mallet.base.types.*;
import edu.umass.cs.mallet.base.pipe.iterator.AbstractPipeInputIterator;
/**
 * Convert the token sequence in the data field of each instance to a token iterator
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class TokenSequence2TokenIterator extends Pipe
{

	private class TokenIterator extends AbstractPipeInputIterator
	{
		Iterator subiterator;
		int count = 0;

		public TokenIterator (TokenSequence ts) {
			subiterator = ts.iterator();
		}

		public Instance nextInstance () {
			return new Instance (subiterator.next(), null, "tokensequence:"+count++,
															 null);
		}

		public boolean hasNext () {
			return subiterator.hasNext();
		}
	}

	public TokenSequence2TokenIterator () {}

	public Instance pipe (Instance carrier)
	{
		carrier.setData(new TokenIterator ((TokenSequence)carrier.getData()));
		return carrier;
	}

	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
	}
	
}

