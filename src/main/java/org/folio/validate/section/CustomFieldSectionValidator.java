package org.folio.validate.section;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.folio.rest.jaxrs.model.CustomFieldSection;

/**
 * Validates a custom field section definition.
 * Deliberately not a {@link org.folio.validate.definition.Validatable}: those are applied to custom fields.
 */
@Component
public class CustomFieldSectionValidator {

  @Value("${custom.fields.definition.name.length}")
  private int nameLengthLimit;

  /**
   * @param section - the custom field section
   * @throws IllegalArgumentException if validation fails
   */
  public void validate(CustomFieldSection section) {
    Validate.isTrue(StringUtils.isNotBlank(section.getName()), "The 'name' of the section must not be blank");
    Validate.isTrue(section.getName().length() <= nameLengthLimit,
      "The 'name' length cannot be more than %s", nameLengthLimit);
    Validate.isTrue(StringUtils.isNotBlank(section.getEntityType()),
      "The 'entityType' of the section must not be blank");
  }
}
