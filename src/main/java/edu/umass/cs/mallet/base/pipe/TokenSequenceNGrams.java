/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package edu.umass.cs.mallet.base.pipe;

import edu.umass.cs.mallet.base.types.TokenSequence;
import edu.umass.cs.mallet.base.types.Token;
import edu.umass.cs.mallet.base.types.Instance;
import java.io.*;

/**
 *  Convert the token sequence in the data field to a token sequence of ngrams.
		@author Don Metzler <a href="mailto:metzler@cs.umass.edu">metzler@cs.umass.edu</a>
*/

public class TokenSequenceNGrams extends Pipe implements Serializable
{
	int [] gramSizes = null;
    
	public TokenSequenceNGrams (int [] sizes)
	{
		this.gramSizes = sizes;
	}
	
	public Instance pipe (Instance carrier)
	{
		String newTerm = null;
		TokenSequence tmpTS = new TokenSequence();
		TokenSequence ts = (TokenSequence) carrier.getData();

		for (int i = 0; i < ts.size(); i++) {
			Token t = ts.getToken(i);
			for(int j = 0; j < gramSizes.length; j++) {
				int len = gramSizes[j];
				if (len <= 0 || len > (i+1)) continue;
				if (len == 1) { tmpTS.add(t); continue; }
				newTerm = new String(t.getText());
				for(int k = 1; k < len; k++)
					newTerm = ts.getToken(i-k) + "_" + newTerm;
				tmpTS.add(newTerm);
			}
		}

		carrier.setData(tmpTS);

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
