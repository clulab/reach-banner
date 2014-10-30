package banner;

import banner.processing.PostProcessor;
import banner.tagging.CRFTagger;
import banner.tagging.Mention;
import banner.tokenization.Tokenizer;

import java.io.File;
import java.util.List;
import java.util.Properties;

/**
 * A wrapper for the Banner NER, using default options
 * User: mihais
 * Date: 10/29/14
 */
public class BannerWrapper {
  Tokenizer tokenizer;

  CRFTagger tagger;

  PostProcessor postProcessor;

  public BannerWrapper() {
    try {
      Properties props = mkDefaultProps();
      BannerProperties properties = BannerProperties.load(props);
      tokenizer = properties.getTokenizer();
      tagger = CRFTagger.load(new File(props.getProperty("model")), properties.getLemmatiser(), properties.getPosTagger());
      postProcessor = properties.getPostProcessor();
    } catch(Exception e) {
      // e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public List<Mention> tag(String sentenceText) {
    String originalText = new String(sentenceText);
    Sentence sentence = new Sentence(sentenceText);
    tokenizer.tokenize(sentence);
    tagger.tag(sentence);
    if (postProcessor != null)
      postProcessor.postProcess(sentence);
    // make sure the text of the sentence did not change!
    //   if it did, it is impossible to align the mentions with the original text...
    assert(originalText.equals(sentence.getText()));
    return sentence.getMentions();
  }

  private static String BANNER_ENV = "BANNER_DATA";

  static Properties mkDefaultProps() {
    Properties props = new Properties();
    String bannerData = System.getenv(BANNER_ENV);
    if(bannerData == null) {
      throw new RuntimeException("ERROR: BannerWrapper requires the environment variable " + BANNER_ENV + "!");
    }

    props.setProperty("lemmatiserDataDirectory", bannerData + "/nlpdata/lemmatiser");
    props.setProperty("posTaggerDataDirectory", bannerData + "/nlpdata/tagger");
    props.setProperty("posTagger", "dragon.nlp.tool.HeppleTagger");
    props.setProperty("tokenizer", "banner.tokenization.SimpleTokenizer");
    props.setProperty("tagFormat", "IOB");
    props.setProperty("useNumericNormalization", "true");
    props.setProperty("useParenthesisPostProcessing", "true");
    props.setProperty("order", "2");
    props.setProperty("dictionary", bannerData + "/dict/single.txt");
    props.setProperty("regexFilename", bannerData + "/regex.txt");
    props.setProperty("useFeatureInduction", "false");
    props.setProperty("textDirection", "Forward");

    props.setProperty("model", bannerData + "/banner_model.dat");

    return props;
  }
}
