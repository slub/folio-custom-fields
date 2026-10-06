package org.folio.repository;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.vertx.core.Future;

import org.folio.rest.jaxrs.model.CustomFieldSection;
import org.folio.rest.jaxrs.model.CustomFieldSectionCollection;
import org.folio.rest.persist.Conn;

public interface CustomFieldSectionsRepository {

  /**
   * Saves the custom field section. Generates the id if it is missing.
   * Returns the saved section.
   *
   * @param entity     - {@link CustomFieldSection} to save
   * @param connection - {@link Conn} to use
   */
  Future<CustomFieldSection> save(CustomFieldSection entity, @Nonnull Conn connection);

  /**
   * Fetches the custom field section with the given id.
   * If the section doesn't exist then returns an empty {@link Optional}.
   *
   * @param id       - id of the section
   * @param tenantId - tenant id
   */
  Future<Optional<CustomFieldSection>> findById(String id, String tenantId);

  /**
   * Fetches the custom field section with the given id and locks its row with {@code FOR SHARE}
   * until the transaction of the connection ends. Used when a custom field is assigned to the section:
   * a concurrent {@link #findByIdForUpdate} (section delete) waits until the field write is committed.
   *
   * @param id         - id of the section
   * @param tenantId   - tenant id
   * @param connection - {@link Conn} of the running transaction
   */
  Future<Optional<CustomFieldSection>> findByIdForShare(String id, String tenantId, @Nonnull Conn connection);

  /**
   * Fetches the custom field section with the given id and locks its row with {@code FOR UPDATE}
   * until the transaction of the connection ends. Used before the section is changed or deleted.
   *
   * @param id         - id of the section
   * @param tenantId   - tenant id
   * @param connection - {@link Conn} of the running transaction
   */
  Future<Optional<CustomFieldSection>> findByIdForUpdate(String id, String tenantId, @Nonnull Conn connection);

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
   * Fetches all custom field sections of the given entity type, sorted by order, and locks their rows with
   * {@code FOR UPDATE} until the transaction of the connection ends. Used whenever the order of the sections of
   * an entity type is computed or changed, so concurrent changes can't produce conflicting orders.
   *
   * @param entityType - entity type of the sections
   * @param tenantId   - tenant id
   * @param connection - {@link Conn} of the running transaction
   */
  Future<List<CustomFieldSection>> findByEntityTypeForUpdate(String entityType, String tenantId,
                                                             @Nonnull Conn connection);

  /**
   * Updates the custom field section. Returns false if no section with the entity's id exists.
   *
   * @param entity     - section to update
   * @param connection - {@link Conn} to use
   */
  Future<Boolean> update(CustomFieldSection entity, @Nonnull Conn connection);

  /**
   * Deletes the custom field section with the given id. Returns false if it doesn't exist.
   *
   * @param id         - id of the section
   * @param connection - {@link Conn} to use
   */
  Future<Boolean> delete(String id, @Nonnull Conn connection);
}
