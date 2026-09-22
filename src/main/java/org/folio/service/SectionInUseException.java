package org.folio.service;

import java.util.List;

import lombok.Getter;

import org.folio.rest.jaxrs.model.CustomField;
import org.folio.rest.jaxrs.model.CustomFieldSection;

/**
 * Thrown when a custom field section can't be deleted, or its entityType can't be changed,
 * because custom fields are still assigned to it. The assigned fields must be moved to another
 * section or unassigned manually first; nothing is changed automatically.
 */
@Getter
public class SectionInUseException extends RuntimeException {

  public static final String CODE = "sectionInUse";

  private static final String DELETE_MESSAGE =
    "Section '%s' cannot be deleted: it is used by %d custom field(s). Move or unassign them first.";
  private static final String ENTITY_TYPE_CHANGE_MESSAGE =
    "Section '%s' cannot change entityType: it is used by %d custom field(s). Move or unassign them first.";

  private final transient CustomFieldSection section;
  private final transient List<CustomField> assignedFields;

  private SectionInUseException(String message, CustomFieldSection section, List<CustomField> assignedFields) {
    super(message);
    this.section = section;
    this.assignedFields = List.copyOf(assignedFields);
  }

  public static SectionInUseException forDelete(CustomFieldSection section, List<CustomField> assignedFields) {
    return new SectionInUseException(
      String.format(DELETE_MESSAGE, section.getName(), assignedFields.size()), section, assignedFields);
  }

  public static SectionInUseException forEntityTypeChange(CustomFieldSection section,
                                                          List<CustomField> assignedFields) {
    return new SectionInUseException(
      String.format(ENTITY_TYPE_CHANGE_MESSAGE, section.getName(), assignedFields.size()), section, assignedFields);
  }
}
