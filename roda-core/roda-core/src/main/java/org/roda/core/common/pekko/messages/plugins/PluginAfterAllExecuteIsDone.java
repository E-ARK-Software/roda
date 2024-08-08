package org.roda.core.common.pekko.messages.plugins;

import org.roda.core.plugins.Plugin;

import java.io.Serial;

/**
 * @author Miguel Guimarães <mguimaraes@keep.pt>
 */
public class PluginAfterAllExecuteIsDone extends PluginMethodIsDone {
  @Serial
  private static final long serialVersionUID = -5136014936634139026L;

  public PluginAfterAllExecuteIsDone(Plugin<?> plugin, boolean withError) {
    super(plugin, withError);
  }

  @Override
  public String toString() {
    return "PluginAfterAllExecuteIsDone [getPlugin()=" + getPlugin() + ", isWithError()=" + isWithError() + "]";
  }
}