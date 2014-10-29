/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package edu.umass.cs.mallet.base.pipe;

import edu.umass.cs.mallet.base.types.Label;
import edu.umass.cs.mallet.base.types.LabelAlphabet;
import edu.umass.cs.mallet.base.types.Instance;
/** Convert object in the target field into a label in the target field.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class Target2Label extends Pipe
{
	public Target2Label ()
	{
		super (null, LabelAlphabet.class);
	}

	public Target2Label (LabelAlphabet ldict)
	{
		super (null, ldict);
	}
	
	public Instance pipe (Instance carrier)
	{
		if (carrier.getTarget() != null) {
			if (carrier.getTarget() instanceof Label)
				throw new IllegalArgumentException ("Already a label.");
			LabelAlphabet ldict = (LabelAlphabet) getTargetAlphabet();
			carrier.setTarget(ldict.lookupLabel (carrier.getTarget()));
		}
		return carrier;
	}

}
