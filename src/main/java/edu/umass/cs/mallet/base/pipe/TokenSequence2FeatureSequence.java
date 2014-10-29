/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package edu.umass.cs.mallet.base.pipe;

import edu.umass.cs.mallet.base.types.TokenSequence;
import edu.umass.cs.mallet.base.types.FeatureSequence;
import edu.umass.cs.mallet.base.types.Token;
import edu.umass.cs.mallet.base.types.Alphabet;
import edu.umass.cs.mallet.base.types.Instance;
import java.io.*;
/**
 * Convert the token sequence in the data field each instance to a feature sequence.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class TokenSequence2FeatureSequence extends Pipe
{
	public TokenSequence2FeatureSequence (Alphabet dataDict)
	{
		super (dataDict, null);
	}

	public TokenSequence2FeatureSequence ()
	{
		super(Alphabet.class, null);
	}
	
	public Instance pipe (Instance carrier)
	{
		TokenSequence ts = (TokenSequence) carrier.getData();
		FeatureSequence ret =
			new FeatureSequence ((Alphabet)getDataAlphabet(), ts.size());
		for (int i = 0; i < ts.size(); i++) {
			ret.add (ts.getToken(i).getText());
		}
		carrier.setData(ret);
		return carrier;
	}

}
