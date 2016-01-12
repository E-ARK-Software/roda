/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.wui.common;

import java.io.Serializable;
import java.util.Locale;
import java.util.MissingResourceException;

import org.apache.commons.lang3.StringUtils;
import org.roda.core.RodaCoreFactory;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.v2.FacetFieldResult;
import org.roda.core.data.v2.FacetValue;
import org.roda.core.data.v2.IndexedAIP;
import org.roda.core.data.v2.IndexResult;
import org.roda.core.data.v2.RODAMember;
import org.roda.wui.common.server.ServerTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class I18nUtility {
  private static final Logger LOGGER = LoggerFactory.getLogger(I18nUtility.class);

  /** Private empty constructor */
  private I18nUtility() {

  }

  public static <T extends Serializable> IndexResult<T> translate(IndexResult<T> input, Class<T> resultClass,
    String localeString) {
    Locale locale = ServerTools.parseLocale(localeString);
    return translate(input, resultClass, locale);
  }

  public static <T extends Serializable> IndexResult<T> translate(IndexResult<T> input, Class<T> resultClass,
    Locale locale) {
    IndexResult<T> output = input;
    if (output != null && output.getFacetResults() != null && output.getFacetResults().size() > 0) {
      for (FacetFieldResult ffr : output.getFacetResults()) {
        if (ffr != null && ffr.getValues() != null && ffr.getValues().size() > 0) {
          String field = ffr.getField();
          for (FacetValue fv : ffr.getValues()) {
            fv.setLabel(getFacetTranslation(field, fv.getValue(), locale, resultClass));
          }
        }
      }
    }
    return output;
  }

  private static <T extends Serializable> String getFacetTranslation(String facetField, String facetValue,
    Locale locale, Class<T> resultClass) {
    String bundleKey = RodaConstants.I18N_UI_FACETS_PREFIX + resultClass.getSimpleName() + "." + facetField
      + (facetValue != null && facetValue.trim().length() == 0 ? "" : "." + facetValue.trim());
    String ret;

    try {
      ret = RodaCoreFactory.getI18NMessages(locale).getTranslation(bundleKey);
    } catch (MissingResourceException e) {
      ret = facetValue;
      LOGGER.debug("Translation not found: " + bundleKey + " locale: " + locale);
    }

    return ret;
  }

}
