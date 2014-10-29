package banner.tagging;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.Token;
import edu.umass.cs.mallet.base.types.TokenSequence;

public class ConfigurableRegexMatches extends Pipe implements Serializable
{

	private static final long serialVersionUID = 1L;

	private List<RegexPair> regexPairs;

	public ConfigurableRegexMatches(String filename) throws IOException
	{
		regexPairs = new ArrayList<RegexPair>();
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		String line = reader.readLine();
		while (line != null)
		{
			line = line.trim();
			if (line.length() > 0)
				regexPairs.add(new RegexPair(line));
			line = reader.readLine();
		}
		reader.close();
	}

	@Override
	public Instance pipe(Instance carrier)
	{
		TokenSequence ts = (TokenSequence) carrier.getData();
		for (int i = 0; i < ts.size(); i++)
		{
			Token t = ts.getToken(i);
			for (RegexPair pair : regexPairs)
			{
				if (pair.regex.matcher(t.getText()).matches())
					t.setFeatureValue(pair.feature, 1.0);
			}
		}
		return carrier;
	}

	private class RegexPair implements Serializable
	{
		private static final long serialVersionUID = 1L;

		Pattern regex;
		String feature;

		public RegexPair(String text)
		{
			String[] split = text.split("\t");
			if (split.length != 2)
				throw new IllegalArgumentException("Regex pattern format should be \"<name>\\t<pattern>\".");
			regex = Pattern.compile(split[0]);
			feature = split[1];
		}
	}

}
