package org.folio.repository;

import static org.folio.repository.CustomFieldsConstants.CUSTOM_FIELD_SECTIONS_TABLE;
import static org.folio.repository.CustomFieldsConstants.JSONB_COLUMN;
import static org.folio.repository.CustomFieldsConstants.SELECT_SECTION_BY_ID_FOR_SHARE;
import static org.folio.repository.CustomFieldsConstants.SELECT_SECTION_BY_ID_FOR_UPDATE;

import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.folio.db.CqlQuery;
import org.folio.db.RowSetUtils;
import org.folio.db.exc.translation.DBExceptionTranslator;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.rest.jaxrs.model.CustomFieldSection;
import org.folio.rest.jaxrs.model.CustomFieldSectionCollection;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.interfaces.Results;

@Log4j2
@Component
public class CustomFieldSectionsRepositoryImpl implements CustomFieldSectionsRepository {

  @Autowired
  private Vertx vertx;
  @Autowired
  private DBExceptionTranslator excTranslator;

  @Override
  public Future<CustomFieldSection> save(CustomFieldSection entity, String tenantId) {
    log.debug("Saving a custom field section with id: {}.", entity.getId());

    setIdIfMissing(entity);
    return pgClient(tenantId).save(CUSTOM_FIELD_SECTIONS_TABLE, entity.getId(), entity)
      .map(id -> {
        entity.setId(id);
        return entity;
      }).recover(excTranslator.translateOrPassBy());
  }

  @Override
  public Future<Optional<CustomFieldSection>> findById(String id, String tenantId) {
    log.debug("Getting a custom field section with id: {}.", id);
    return pgClient(tenantId).getById(CUSTOM_FIELD_SECTIONS_TABLE, id, CustomFieldSection.class)
      .map(Optional::ofNullable)
      .recover(excTranslator.translateOrPassBy());
  }

  @Override
  public Future<Optional<CustomFieldSection>> findByIdForShare(String id, String tenantId, @Nonnull Conn connection) {
    log.debug("Getting and share-locking a custom field section with id: {}.", id);
    return findLocked(SELECT_SECTION_BY_ID_FOR_SHARE, id, tenantId, connection);
  }

  @Override
  public Future<Optional<CustomFieldSection>> findByIdForUpdate(String id, String tenantId, @Nonnull Conn connection) {
    log.debug("Getting and update-locking a custom field section with id: {}.", id);
    return findLocked(SELECT_SECTION_BY_ID_FOR_UPDATE, id, tenantId, connection);
  }

  @Override
  public Future<CustomFieldSectionCollection> findByQuery(String query, int offset, int limit, String tenantId) {
    CqlQuery<CustomFieldSection> q =
      new CqlQuery<>(pgClient(tenantId), CUSTOM_FIELD_SECTIONS_TABLE, CustomFieldSection.class);
    log.debug("Getting custom field sections by query.");
    return q.get(query, offset, limit).map(this::toCollection)
      .recover(excTranslator.translateOrPassBy());
  }

  @Override
  public Future<Boolean> update(CustomFieldSection entity, @Nonnull Conn connection) {
    log.debug("Updating a custom field section with id: {}.", entity.getId());
    return connection.update(CUSTOM_FIELD_SECTIONS_TABLE, entity, entity.getId())
      .map(rowSet -> rowSet.rowCount() == 1)
      .recover(excTranslator.translateOrPassBy());
  }

  @Override
  public Future<Boolean> delete(String id, @Nonnull Conn connection) {
    log.debug("Deleting a custom field section with id: {}.", id);
    return connection.delete(CUSTOM_FIELD_SECTIONS_TABLE, id)
      .map(rowSet -> rowSet.rowCount() == 1)
      .recover(excTranslator.translateOrPassBy());
  }

  private Future<Optional<CustomFieldSection>> findLocked(String sqlTemplate, String id, String tenantId,
                                                          Conn connection) {
    String query = String.format(sqlTemplate, getTableName(tenantId));
    return connection.execute(query, Tuple.of(UUID.fromString(id)))
      .map(this::mapFirstSection)
      .recover(excTranslator.translateOrPassBy());
  }

  private Optional<CustomFieldSection> mapFirstSection(RowSet<Row> rowSet) {
    if (RowSetUtils.isEmpty(rowSet)) {
      return Optional.empty();
    }
    return Optional.of(RowSetUtils.mapFirstItem(rowSet,
      row -> ObjectMapperTool.readValue(row.getJsonObject(JSONB_COLUMN).encode(), CustomFieldSection.class)));
  }

  private void setIdIfMissing(CustomFieldSection section) {
    if (StringUtils.isBlank(section.getId())) {
      section.setId(UUID.randomUUID().toString());
    }
  }

  private CustomFieldSectionCollection toCollection(Results<CustomFieldSection> results) {
    return new CustomFieldSectionCollection()
      .withCustomFieldSections(results.getResults())
      .withTotalRecords(results.getResultInfo().getTotalRecords());
  }

  private String getTableName(String tenantId) {
    return PostgresClient.convertToPsqlStandard(tenantId) + "." + CUSTOM_FIELD_SECTIONS_TABLE;
  }

  private PostgresClient pgClient(String tenantId) {
    return PostgresClient.getInstance(vertx, tenantId);
  }
}
