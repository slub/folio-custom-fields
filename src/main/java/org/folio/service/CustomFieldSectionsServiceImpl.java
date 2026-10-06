package org.folio.service;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import static org.folio.service.CqlSortUtils.withSortByOrder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
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

    return pgClient(tenantId).withTrans(conn -> appendToEntityType(section, tenantId, conn)
      .compose(v -> repository.save(section, conn)));
  }

  @Override
  public Future<CustomFieldSection> findById(String id, String tenantId) {
    log.debug("findById:: Attempts to get customFieldSection by [id: {}, tenantId: {}]", id, tenantId);
    return repository.findById(id, tenantId)
      .map(section -> section.orElseThrow(() -> ServiceExceptions.notFound(CustomFieldSection.class, id)));
  }

  @Override
  public Future<CustomFieldSectionCollection> findByQuery(String query, int offset, int limit, String tenantId) {
    return repository.findByQuery(withSortByOrder(query), offset, limit, tenantId);
  }

  @Override
  public Future<Void> update(String id, CustomFieldSection section, String tenantId) {
    log.debug("update:: Attempts to update customFieldSection by [id: {}, tenantId: {}, section: {}]",
      id, tenantId, section);
    section.setId(id);

    return pgClient(tenantId).withTrans(conn -> lockExisting(id, tenantId, conn)
      .compose(existing -> {
        if (Objects.equals(existing.getEntityType(), section.getEntityType())) {
          section.setOrder(existing.getOrder());
          return repository.update(section, conn)
            .compose(updated -> failIfNotFound(updated, id));
        }
        return failIfAssigned(existing, tenantId, conn, true)
          .compose(v -> appendToEntityType(section, tenantId, conn))
          .compose(v -> repository.update(section, conn))
          .compose(updated -> failIfNotFound(updated, id))
          .compose(v -> reorder(existing.getEntityType(), tenantId, conn));
      }));
  }

  @Override
  public Future<Void> delete(String id, String tenantId) {
    log.debug("delete:: Attempts to delete customFieldSection by [id: {}, tenantId: {}]", id, tenantId);

    return pgClient(tenantId).withTrans(conn -> lockExisting(id, tenantId, conn)
      .compose(existing -> failIfAssigned(existing, tenantId, conn, false)
        .compose(v -> repository.delete(id, conn))
        .compose(deleted -> failIfNotFound(deleted, id))
        .compose(v -> reorder(existing.getEntityType(), tenantId, conn))));
  }

  @Override
  public Future<Void> replaceAll(List<CustomFieldSection> sections, String entityType, String tenantId) {
    log.debug("replaceAll:: Attempts to replace all customFieldSections by [entityType: {}, tenantId: {}]",
      entityType, tenantId);

    setOrder(sections);
    setIdIfEmpty(sections);
    Map<String, CustomFieldSection> newSectionsMap = createMapById(sections);

    return pgClient(tenantId).withTrans(conn -> repository.findByEntityTypeForUpdate(entityType, tenantId, conn)
      .compose(existingSections -> {
        Map<String, CustomFieldSection> existingSectionsMap = createMapById(existingSections);

        Set<String> sectionsToRemove = Sets.difference(existingSectionsMap.keySet(), newSectionsMap.keySet());
        Set<String> sectionsToUpdate = Sets.intersection(existingSectionsMap.keySet(), newSectionsMap.keySet());
        Set<String> sectionsToInsert = Sets.difference(newSectionsMap.keySet(), existingSectionsMap.keySet());

        return executeForEach(sectionsToRemove,
            id -> failIfAssigned(existingSectionsMap.get(id), tenantId, conn, false)
              .compose(v -> repository.delete(id, conn)))
          .compose(v -> executeForEach(sectionsToUpdate, id -> repository.update(newSectionsMap.get(id), conn)))
          .compose(v -> executeForEach(sectionsToInsert, id -> repository.save(newSectionsMap.get(id), conn)));
      }));
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

  /**
   * Sets the order of the section after the last section of its entity type.
   * The sections of the entity type stay locked until the transaction ends.
   */
  private Future<Void> appendToEntityType(CustomFieldSection section, String tenantId, Conn conn) {
    return repository.findByEntityTypeForUpdate(section.getEntityType(), tenantId, conn)
      .map(sections -> {
        int maxOrder = sections.stream()
          .map(CustomFieldSection::getOrder)
          .filter(Objects::nonNull)
          .mapToInt(Integer::intValue)
          .max()
          .orElse(0);
        section.setOrder(maxOrder + 1);
        return null;
      });
  }

  /**
   * Renumbers the sections of the entity type to 1..n, keeping their relative order,
   * so no gap is left after a section is removed from the entity type.
   */
  private Future<Void> reorder(String entityType, String tenantId, Conn conn) {
    return repository.findByEntityTypeForUpdate(entityType, tenantId, conn)
      .compose(sections -> {
        List<CustomFieldSection> changed = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
          CustomFieldSection section = sections.get(i);
          if (!Objects.equals(section.getOrder(), i + 1)) {
            changed.add(section.withOrder(i + 1));
          }
        }
        return executeForEach(changed, section -> repository.update(section, conn));
      });
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

  private void setOrder(List<CustomFieldSection> sections) {
    for (int i = 0; i < sections.size(); i++) {
      sections.get(i).setOrder(i + 1);
    }
  }

  private void setIdIfEmpty(List<CustomFieldSection> sections) {
    for (CustomFieldSection section : sections) {
      if (StringUtils.isBlank(section.getId())) {
        section.setId(UUID.randomUUID().toString());
      }
    }
  }

  private Map<String, CustomFieldSection> createMapById(List<CustomFieldSection> sections) {
    return sections.stream()
      .collect(Collectors.toMap(CustomFieldSection::getId, Function.identity()));
  }

  private <T> Future<Void> executeForEach(Collection<T> collection, Function<T, Future<?>> action) {
    Future<?> resultFuture = succeededFuture();
    for (T item : collection) {
      resultFuture = resultFuture.compose(o -> action.apply(item));
    }
    return resultFuture.mapEmpty();
  }

  private PostgresClient pgClient(String tenantId) {
    return PostgresClient.getInstance(vertx, tenantId);
  }
}
