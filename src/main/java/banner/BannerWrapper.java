package banner;

import banner.processing.ParenthesisPostProcessor;
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
      throw new RuntimeException(e);
    }
  }

  public List<Mention> tag(String sentenceText) {
    Sentence sentence = new Sentence(sentenceText);
    tokenizer.tokenize(sentence);
    tagger.tag(sentence);
    if (postProcessor != null)
      postProcessor.postProcess(sentence);
    return sentence.getMentions();
  }

  static Properties mkDefaultProps() {
    Properties props = new Properties();
    // TODO
    return props;
  }
}
