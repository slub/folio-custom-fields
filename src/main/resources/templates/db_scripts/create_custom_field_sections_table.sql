CREATE TABLE IF NOT EXISTS custom_field_sections(id UUID PRIMARY KEY, jsonb JSONB NOT NULL);

DROP TRIGGER IF EXISTS set_id_in_jsonb ON custom_field_sections CASCADE;
CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON custom_field_sections FOR EACH ROW EXECUTE PROCEDURE set_id_in_jsonb();

CREATE UNIQUE INDEX IF NOT EXISTS custom_field_sections_entity_type_name_idx
  ON custom_field_sections ((jsonb->>'entityType'), (lower(jsonb->>'name')));
