package org.folio.service;

import java.util.List;

import io.vertx.core.Future;

import org.folio.rest.jaxrs.model.CustomFieldSection;
import org.folio.rest.jaxrs.model.CustomFieldSectionCollection;

public interface CustomFieldSectionsService {

  /**
   * Saves a new custom field section. The section is appended after the last section of its entity type:
   * its order is set to the highest order of the entity type + 1, a given order is ignored.
   *
   * @param section  - section to save
   * @param tenantId - tenant id
   */
  Future<CustomFieldSection> save(CustomFieldSection section, String tenantId);

  /**
   * Fetches the custom field section with the given id.
   * If it doesn't exist then returns a failed Future with NotFoundException as a cause.
   *
   * @param id       - id of the section
   * @param tenantId - tenant id
   */
  Future<CustomFieldSection> findById(String id, String tenantId);

  /**
   * Fetches the custom field sections that match the CQL query, sorted by order
   * (as second sort attribute, if the query has its own "sortby").
   *
   * @param query    - CQL query
   * @param offset   - offset
   * @param limit    - limit
   * @param tenantId - tenant id
   */
  Future<CustomFieldSectionCollection> findByQuery(String query, int offset, int limit, String tenantId);

  /**
   * Updates the custom field section with the given id. The order is kept, a given order is ignored.
   * If the entityType changes, the section is appended to the new entity type and the sections
   * of the old entity type are renumbered.
   * Fails with {@link SectionInUseException} if the entityType changes while custom fields are assigned to it.
   *
   * @param id       - id of the section
   * @param section  - new value
   * @param tenantId - tenant id
   */
  Future<Void> update(String id, CustomFieldSection section, String tenantId);

  /**
   * Deletes the custom field section with the given id.
   * Fails with {@link SectionInUseException} if custom fields are still assigned to it:
   * removing a section is a manual process, assigned fields are never changed automatically.
   * The remaining sections of the entity type are renumbered.
   *
   * @param id       - id of the section
   * @param tenantId - tenant id
   */
  Future<Void> delete(String id, String tenantId);

  /**
   * Replaces all custom field sections of the entity type with the given sections: the order of each section is
   * set to its position in the list (1..n), sections missing from the list are deleted, new ones are created.
   * Fails with {@link SectionInUseException} if a section to delete is still assigned to custom fields.
   *
   * @param sections   - all sections of the entity type, in display order
   * @param entityType - entity type of the sections
   * @param tenantId   - tenant id
   */
  Future<Void> replaceAll(List<CustomFieldSection> sections, String entityType, String tenantId);
}
