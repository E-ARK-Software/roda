/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.common.pekko.messages.events;

import org.roda.core.data.v2.user.Group;

import java.io.Serial;

/**
 * @author Miguel Guimarães <mguimaraes@keep.pt>
 */
public final class EventGroupCreated extends AbstractEventMessage {
  @Serial
  private static final long serialVersionUID = -51380983717488740L;

  private final Group group;

  public EventGroupCreated(Group group, String senderId) {
    super(senderId);
    this.group = group;
  }

  public Group getGroup() {
    return group;
  }

  @Override
  public String toString() {
    return "EventGroupCreated [group=" + group + ", getSenderId()=" + getSenderId() + "]";
  }
}
