/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




package edu.umass.cs.mallet.base.pipe;

import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.types.FeatureVector;
import edu.umass.cs.mallet.base.types.Alphabet;
import edu.umass.cs.mallet.base.types.Labeling;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.util.MalletLogger;

import java.util.logging.*;
import java.lang.reflect.Array;

/** Converts a Java array of numerical types to a FeatureVector, where the
    Alphabet is the data array index wrapped in an Integer object.

    @author Jerod Weinman <A HREF="mailto:weinman@cs.umass.edu">weinman@cs.umass.edu</A>
 */
public class Array2FeatureVector extends Pipe {

    private static Logger logger = MalletLogger.getLogger(Array2FeatureVector.class.getName());

    public Array2FeatureVector(int capacity) {

	this.dataDict = new Alphabet(capacity);

    }

    public Array2FeatureVector() {
	this(1000);
    }

    /** Construct a pipe based on the dimensions of the data and target. */
    public Array2FeatureVector( Alphabet dataDict, Alphabet targetDict ) {
	
	this.dataDict = dataDict;
	this.targetDict = targetDict;

    }

    /** Convert the data in an <CODE>Instance</CODE> from an array to a 
	<CODE>FeatureVector</CODE> leaving other fields unchanged.
	
	<CODE>Instance.getData()</CODE> must return a numeric array, and it is
	 cast to <CODE>double[]</CODE>

	@throws IllegalStateException If <CODE>Instance.getTarget()</CODE> is
	not a Labeling
    */
    public Instance pipe(  Instance carrier )
	throws IllegalStateException
    {
	
	int dataLength = Array.getLength( carrier.getData() );

	if ( dataLength > dataDict.size() )
	    for (int k=dataDict.size() ; k<dataLength ; k++ )
		dataDict.lookupIndex( new Integer(k) , true ); // 'add'
	
	FeatureVector fv = new FeatureVector( dataDict, 
					      (double[])carrier.getData() );


	// Check if we've set the target alphabet member
	if (targetDict == null) {
	    if (carrier.getTarget() instanceof Labeling)
		targetDict = ((Labeling)carrier.getTarget()).getLabelAlphabet();
	    else
		throw new IllegalStateException ("Instance target is not a " +
						 "Labeling; it is a " + 
						 carrier.getTarget().getClass().getName());
	    

	}

	carrier.setData( fv );
	
	return carrier;

	/*return new Instance( fv, carrier.getTarget(), carrier.getName(), 
	  carrier.getSource(), this );*/
    }
				 

    /** Current size of the Vocabulary */
    public int size() 
    {
	return dataDict.size();
    }
}
