/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.common.pekko.messages.events;

import org.roda.core.common.pekko.messages.AbstractMessage;

import java.io.Serial;

/**
 * @author Miguel Guimarães <mguimaraes@keep.pt>
 */
public abstract class AbstractEventMessage extends AbstractMessage {
  @Serial
  private static final long serialVersionUID = -2517455273875624115L;

  private final String senderId;

  protected AbstractEventMessage(String senderId) {
    super();
    this.senderId = senderId;
  }

  public String getSenderId() {
    return senderId;
  }

  @Override
  public String toString() {
    return "AbstractEventMessage [senderId=" + senderId + "]";
  }
}
