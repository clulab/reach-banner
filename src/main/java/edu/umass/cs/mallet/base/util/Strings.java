/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package edu.umass.cs.mallet.base.util;

import java.util.Arrays;

/**
 *  Static utility methods for Strings
 */
final public class Strings {

	public static int commonPrefixIndex (String[] strings)
	{
		int prefixLen = strings[0].length();
		for (int i = 1; i < strings.length; i++) {
			if (strings[i].length() < prefixLen)
				prefixLen = strings[i].length();
			int j = 0;
			if (prefixLen == 0)
				return 0;
			while (j < prefixLen) {
				if (strings[i-1].charAt(j) != strings[i].charAt(j)) {
					prefixLen = j;
					break;
				}
				j++;
			}
		}
		return prefixLen;
	}

	public static String commonPrefix (String[] strings)
	{
		return strings[0].substring (0, commonPrefixIndex(strings));
	}

  public static int count (String string, char ch)
  {
    int idx = -1;
    int count = 0;
    while ((idx = string.indexOf (ch, idx+1)) >= 0) { count++; };
    return count;
  }

}
