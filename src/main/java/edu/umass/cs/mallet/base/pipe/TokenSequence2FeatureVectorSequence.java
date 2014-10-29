/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package edu.umass.cs.mallet.base.pipe;

import edu.umass.cs.mallet.base.types.TokenSequence;
import edu.umass.cs.mallet.base.types.FeatureVector;
import edu.umass.cs.mallet.base.types.FeatureVectorSequence;
import edu.umass.cs.mallet.base.types.Token;
import edu.umass.cs.mallet.base.types.Alphabet;
import edu.umass.cs.mallet.base.types.Alphabet;
import edu.umass.cs.mallet.base.types.Instance;
import java.io.*;
/**
 * Convert the token sequence in the data field of each instance to a feature vector sequence.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class TokenSequence2FeatureVectorSequence extends Pipe implements Serializable
{
	boolean augmentable;									// Create AugmentableFeatureVector's in the sequence
	boolean binary;												// Create binary (Augmentable)FeatureVector's in the sequence
	boolean growAlphabet = true;
	
	public TokenSequence2FeatureVectorSequence (Alphabet dataDict,
																							boolean binary, boolean augmentable)
	{
		super (dataDict, null);
		this.augmentable = augmentable;
		this.binary = binary;
	}

	public TokenSequence2FeatureVectorSequence (Alphabet dataDict)
	{
		this (dataDict, false, false);
	}
	
	public TokenSequence2FeatureVectorSequence (boolean binary, boolean augmentable)
	{
		super (Alphabet.class, null);
		this.augmentable = augmentable;
		this.binary = binary;
	}

	public TokenSequence2FeatureVectorSequence ()
	{
		this (false, false);
	}
	
	public Instance pipe (Instance carrier)
	{
		carrier.setData(new FeatureVectorSequence ((Alphabet)getDataAlphabet(),
																							 (TokenSequence)carrier.getData(),
																							 binary, augmentable,
																							 growAlphabet));
		return carrier;
	}

	public void setGrowAlphabet(boolean growAlphabet) {
		this.growAlphabet = growAlphabet;
	}

	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeBoolean(augmentable);
		out.writeBoolean(binary);
		out.writeBoolean(growAlphabet);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		augmentable = in.readBoolean();
		binary = in.readBoolean();
//		growAlphabet = true;
		growAlphabet = in.readBoolean();
	}

}
