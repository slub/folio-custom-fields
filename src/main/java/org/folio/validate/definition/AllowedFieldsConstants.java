package org.folio.validate.definition;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

final class AllowedFieldsConstants {

  static final Set<String> COMMON_ALLOWED_FIELDS = ImmutableSet.of(
    "id",
    "name",
    "refId",
    "type",
    "entityType",
    "visible",
    "required",
    "isRepeatable",
    "order",
    "helpText",
    "metadata",
    "displayInAccordion",
    "sectionId"
  );

  static final Set<String> CHECKBOX_ALLOWED_FIELDS = new ImmutableSet.Builder<String>()
    .addAll(COMMON_ALLOWED_FIELDS).add("checkboxField")
    .build();

  static final Set<String> SELECT_ALLOWED_FIELDS = new ImmutableSet.Builder<String>()
    .addAll(COMMON_ALLOWED_FIELDS).add("selectField")
    .build();

  static final Set<String> TEXTBOX_ALLOWED_FIELDS = new ImmutableSet.Builder<String>()
    .addAll(COMMON_ALLOWED_FIELDS).add("textField")
    .build();

  static final Set<String> LOOKUP_ALLOWED_FIELDS = new ImmutableSet.Builder<String>()
    .addAll(COMMON_ALLOWED_FIELDS).add("lookupField")
    .build();

  private AllowedFieldsConstants() {

  }

}

