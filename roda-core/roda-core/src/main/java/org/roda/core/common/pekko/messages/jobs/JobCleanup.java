package org.roda.core.common.pekko.messages.jobs;

import org.roda.core.common.pekko.messages.AbstractMessage;

import java.io.Serial;

/**
 * @author Miguel Guimarães <mguimaraes@keep.pt>
 */
public class JobCleanup extends AbstractMessage {
  @Serial
  private static final long serialVersionUID = -5175825019027462407L;

  public JobCleanup() {
    super();
  }

  @Override
  public String toString() {
    return "JobCleanup []";
  }
}