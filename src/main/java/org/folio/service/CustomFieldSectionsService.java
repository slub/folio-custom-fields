package org.folio.service;

import io.vertx.core.Future;

import org.folio.rest.jaxrs.model.CustomFieldSection;
import org.folio.rest.jaxrs.model.CustomFieldSectionCollection;

public interface CustomFieldSectionsService {

  /**
   * Saves a new custom field section.
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
   * Fetches the custom field sections that match the CQL query.
   *
   * @param query    - CQL query
   * @param offset   - offset
   * @param limit    - limit
   * @param tenantId - tenant id
   */
  Future<CustomFieldSectionCollection> findByQuery(String query, int offset, int limit, String tenantId);

  /**
   * Updates the custom field section with the given id.
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
   *
   * @param id       - id of the section
   * @param tenantId - tenant id
   */
  Future<Void> delete(String id, String tenantId);
}
