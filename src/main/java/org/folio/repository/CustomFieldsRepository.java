package org.folio.repository;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.vertx.core.Future;

import org.folio.rest.jaxrs.model.CustomField;
import org.folio.rest.jaxrs.model.CustomFieldCollection;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.SQLConnection;

public interface CustomFieldsRepository {

  /**
   * Saves the definition of the custom field.
   * Returns newly created custom field.
   *
   * @param entity   - current definition of the custom field {@link CustomField} object to save
   * @param tenantId - tenant id
   */
  Future<CustomField> save(CustomField entity, String tenantId);

  /**
   * Saves the definition of the custom field.
   * Returns newly created custom field.
   *
   * @param entity     - current definition of the custom field {@link CustomField} object to save
   * @param connection - {@link Conn} to use
   */
  Future<CustomField> save(CustomField entity, @Nonnull Conn connection);

  /**
   * Fetches a custom field definition with given id
   * If custom field with given id doesn't exist then returns an empty {@link Optional}.
   *
   * @param id       - id of custom field to get
   * @param tenantId - tenant id
   */
  Future<Optional<CustomField>> findById(String id, String tenantId);

  /**
   * Fetches the maximum custom field reference id by given custom field name.
   *
   * @param customFieldName - name of custom field
   * @param tenantId        - tenant id
   */
  Future<Integer> maxRefId(String customFieldName, String tenantId);

  Future<Integer> maxRefId(String customFieldName, String tenantId, @Nonnull Conn connection);

  /**
   * Fetches the maximum value of "order" attribute in all custom fields,
   * or 0 if there are no fields with "order" attribute.
   *
   * @param tenantId - tenant id
   */
  Future<Integer> maxOrder(String tenantId);


  /**
   * Fetches custom fields collection that match specified CQL query.
   *
   * @param query    - CQL query
   * @param offset   - offset
   * @param limit    - limit
   * @param tenantId - tenant id
   */
  Future<CustomFieldCollection> findByQuery(String query, int offset, int limit, String tenantId);

  /**
   * Fetches all custom field definitions assigned to the given section, sorted by order.
   *
   * @param sectionId  - id of the custom field section
   * @param tenantId   - tenant id
   * @param connection - {@link Conn} to use
   */
  Future<List<CustomField>> findBySectionId(String sectionId, String tenantId, @Nonnull Conn connection);

  /**
   * Updates custom field definition.
   *
   * @param entity   - entity to update
   * @param tenantId - tenant id
   */
  Future<Boolean> update(CustomField entity, String tenantId);

  /**
   * Updates custom field definition.
   *
   * @param entity     - entity to update
   * @param tenantId   - tenant id
   * @param connection - {@link Conn} to use
   */
  Future<Boolean> update(CustomField entity, @Nonnull Conn connection);

  /**
   * Deletes custom field with given id.
   *
   * @param id       - id of custom field to delete
   * @param tenantId - tenant id
   */
  Future<Boolean> delete(String id, String tenantId);

  /**
   * Deletes custom field with given id.
   *
   * @param id         - id of custom field to delete
   * @param connection - {@link SQLConnection} to use
   */
  Future<Boolean> delete(String id, @Nonnull Conn connection);
}
