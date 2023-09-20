package util;

import java.io.IOException;

/**
 * interface of protocol with one function processInput
 */
public interface Protocol {

  /**
   * process the input from stream and generate specific output.
   * @throws IOException io exception
   */
  void processInput() throws IOException;
}
