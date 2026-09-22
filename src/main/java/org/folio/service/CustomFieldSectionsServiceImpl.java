package org.folio.service;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.util.Objects;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.folio.repository.CustomFieldSectionsRepository;
import org.folio.repository.CustomFieldsRepository;
import org.folio.rest.jaxrs.model.CustomFieldSection;
import org.folio.rest.jaxrs.model.CustomFieldSectionCollection;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.service.exc.ServiceExceptions;

@Log4j2
@Component
public class CustomFieldSectionsServiceImpl implements CustomFieldSectionsService {

  @Autowired
  private CustomFieldSectionsRepository repository;
  @Autowired
  private CustomFieldsRepository customFieldsRepository;
  @Autowired
  private Vertx vertx;

  @Override
  public Future<CustomFieldSection> save(CustomFieldSection section, String tenantId) {
    log.debug("save:: Attempts to save customFieldSection by [tenantId: {}, section: {}]", tenantId, section);
    return repository.save(section, tenantId);
  }

  @Override
  public Future<CustomFieldSection> findById(String id, String tenantId) {
    log.debug("findById:: Attempts to get customFieldSection by [id: {}, tenantId: {}]", id, tenantId);
    return repository.findById(id, tenantId)
      .map(section -> section.orElseThrow(() -> ServiceExceptions.notFound(CustomFieldSection.class, id)));
  }

  @Override
  public Future<CustomFieldSectionCollection> findByQuery(String query, int offset, int limit, String tenantId) {
    return repository.findByQuery(query, offset, limit, tenantId);
  }

  @Override
  public Future<Void> update(String id, CustomFieldSection section, String tenantId) {
    log.debug("update:: Attempts to update customFieldSection by [id: {}, tenantId: {}, section: {}]",
      id, tenantId, section);
    section.setId(id);

    return pgClient(tenantId).withTrans(conn -> lockExisting(id, tenantId, conn)
      .compose(existing -> Objects.equals(existing.getEntityType(), section.getEntityType())
        ? succeededFuture()
        : failIfAssigned(existing, tenantId, conn, true))
      .compose(v -> repository.update(section, conn))
      .compose(updated -> failIfNotFound(updated, id)));
  }

  @Override
  public Future<Void> delete(String id, String tenantId) {
    log.debug("delete:: Attempts to delete customFieldSection by [id: {}, tenantId: {}]", id, tenantId);

    return pgClient(tenantId).withTrans(conn -> lockExisting(id, tenantId, conn)
      .compose(existing -> failIfAssigned(existing, tenantId, conn, false))
      .compose(v -> repository.delete(id, conn))
      .compose(deleted -> failIfNotFound(deleted, id)));
  }

  /**
   * Locks the section row with FOR UPDATE. A custom field write that assigns this section holds a
   * FOR SHARE lock on the same row, so the in-use check below always sees every committed assignment
   * and no field can be assigned while the section is being deleted.
   */
  private Future<CustomFieldSection> lockExisting(String id, String tenantId, Conn conn) {
    return repository.findByIdForUpdate(id, tenantId, conn)
      .map(section -> section.orElseThrow(() -> ServiceExceptions.notFound(CustomFieldSection.class, id)));
  }

  private Future<Void> failIfAssigned(CustomFieldSection section, String tenantId, Conn conn,
                                      boolean entityTypeChange) {
    return customFieldsRepository.findBySectionId(section.getId(), tenantId, conn)
      .compose(fields -> {
        if (fields.isEmpty()) {
          return succeededFuture();
        }
        log.warn("Custom field section [id: {}] is assigned to {} custom field(s)", section.getId(), fields.size());
        return failedFuture(entityTypeChange
          ? SectionInUseException.forEntityTypeChange(section, fields)
          : SectionInUseException.forDelete(section, fields));
      });
  }

  private Future<Void> failIfNotFound(boolean found, String id) {
    return found
      ? succeededFuture()
      : failedFuture(ServiceExceptions.notFound(CustomFieldSection.class, id));
  }

  private PostgresClient pgClient(String tenantId) {
    return PostgresClient.getInstance(vertx, tenantId);
  }
}
