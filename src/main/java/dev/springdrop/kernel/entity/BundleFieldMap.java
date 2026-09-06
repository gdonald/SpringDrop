package dev.springdrop.kernel.entity;

import java.util.List;

/**
 * The fields a bundle carries. The Field API implements this from the field
 * instances attached to the bundle, and the CRUD service asks it which field
 * tables to read and write, so an entity of one bundle never touches another
 * bundle's fields.
 */
public interface BundleFieldMap {

    List<String> fieldNames(String entityTypeId, String bundle);
}
